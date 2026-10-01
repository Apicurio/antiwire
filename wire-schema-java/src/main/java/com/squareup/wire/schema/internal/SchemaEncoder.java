/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.wire.schema.internal;

import com.squareup.wire.FieldEncoding;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.ProtoReader;
import com.squareup.wire.ProtoWriter;
import com.squareup.wire.ReverseProtoWriter;
import com.squareup.wire.Syntax;
import com.squareup.wire.internal.Internal;
import com.squareup.wire.schema.EnclosingType;
import com.squareup.wire.schema.Extend;
import com.squareup.wire.schema.Extensions;
import com.squareup.wire.schema.EnumConstant;
import com.squareup.wire.schema.EnumType;
import com.squareup.wire.schema.Field;
import com.squareup.wire.schema.MessageType;
import com.squareup.wire.schema.OneOf;
import com.squareup.wire.schema.Options;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.ProtoMember;
import com.squareup.wire.schema.ProtoType;
import com.squareup.wire.schema.Rpc;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.Service;
import com.squareup.wire.schema.Type;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import okio.ByteString;

/**
 * This class encodes files from a Wire schema using the types in protobuf's
 * {@code descriptor.proto}. Unfortunately, the two models don't line up directly:
 *
 * <ul>
 *   <li>Wire keeps a heterogeneous list of messages and enums; {@code descriptor.proto} keeps
 *       each in its own list.
 *   <li>Descriptors don't have first class support for {@link Field.EncodeMode#OMIT_IDENTITY},
 *       which is the default in proto3. Instead these are synthesized with oneofs.
 *   <li>Descriptors don't support maps. Instead these are synthesized with entry classes.
 * </ul>
 *
 * <p>This file requires we manually keep tags and types in sync with {@code descriptor.proto}.
 */
public final class SchemaEncoder {
  private final Schema schema;

  private final ProtoAdapter<Object> fileOptionsProtoAdapter;
  private final ProtoAdapter<Object> messageOptionsProtoAdapter;
  private final ProtoAdapter<Object> fieldOptionsProtoAdapter;
  private final ProtoAdapter<Object> enumOptionsProtoAdapter;
  private final ProtoAdapter<Object> enumValueOptionsProtoAdapter;
  private final ProtoAdapter<Object> serviceOptionsProtoAdapter;
  private final ProtoAdapter<Object> rpcOptionsProtoAdapter;

  public SchemaEncoder(Schema schema) {
    this.schema = schema;
    this.fileOptionsProtoAdapter = schema.protoAdapter(Options.FILE_OPTIONS.toString(), false);
    this.messageOptionsProtoAdapter =
        schema.protoAdapter(Options.MESSAGE_OPTIONS.toString(), false);
    this.fieldOptionsProtoAdapter = schema.protoAdapter(Options.FIELD_OPTIONS.toString(), false);
    this.enumOptionsProtoAdapter = schema.protoAdapter(Options.ENUM_OPTIONS.toString(), false);
    this.enumValueOptionsProtoAdapter =
        schema.protoAdapter(Options.ENUM_VALUE_OPTIONS.toString(), false);
    this.serviceOptionsProtoAdapter =
        schema.protoAdapter(Options.SERVICE_OPTIONS.toString(), false);
    this.rpcOptionsProtoAdapter = schema.protoAdapter(Options.METHOD_OPTIONS.toString(), false);
  }

  public ByteString encode(ProtoFile protoFile) throws IOException {
    return fileEncoder.encodeByteString(protoFile);
  }

  private final Encoder<ProtoFile> fileEncoder = new Encoder<ProtoFile>() {
    @Override public void encode(ReverseProtoWriter writer, ProtoFile value) throws IOException {
      if (value.syntax() != Syntax.PROTO_2) {
        ProtoAdapter.STRING.encodeWithTag(writer, 12,
            value.syntax() != null ? value.syntax().toString() : null);
      }

      // SourceCodeInfo.ADAPTER.encodeWithTag(writer, 9, value.source_code_info)
      fileOptionsProtoAdapter.encodeWithTag(writer, 8, toJsonOptions(value.options()));

      // TODO(jwilson): can extension fields be maps?
      List<Extend> reversed = new ArrayList<>(value.extendList());
      Collections.reverse(reversed);
      for (Extend extend : reversed) {
        List<EncodedField> encodedFields = new ArrayList<>();
        for (Field field : extend.fields()) {
          encodedFields.add(new EncodedField(value.syntax(), field, null, dotName(extend.type()),
              null));
        }
        fieldEncoder.asRepeated().encodeWithTag(writer, 7, encodedFields);
      }

      serviceEncoder.asRepeated().encodeWithTag(writer, 6, value.services());
      enumEncoder.asRepeated().encodeWithTag(writer, 5, typesOf(value.types(), EnumType.class));
      messageEncoder.asRepeated().encodeWithTag(writer, 4, typesOf(value.types(), MessageType.class));
      enclosingEncoder.asRepeated().encodeWithTag(writer, 4, typesOf(value.types(), EnclosingType.class));
      // INT32.asRepeated().encodeWithTag(writer, 11, value.weak_dependency)
      List<String> allImports = new ArrayList<>(value.imports());
      allImports.addAll(value.publicImports());
      List<Integer> publicImportIndexes = new ArrayList<>();
      for (int i = 0; i < allImports.size(); i++) {
        if (value.publicImports().contains(allImports.get(i))) {
          publicImportIndexes.add(i);
        }
      }
      ProtoAdapter.INT32.asRepeated().encodeWithTag(writer, 10, publicImportIndexes);
      ProtoAdapter.STRING.asRepeated().encodeWithTag(writer, 3, allImports);
      ProtoAdapter.STRING.encodeWithTag(writer, 2, value.packageName());
      ProtoAdapter.STRING.encodeWithTag(writer, 1, value.location().path);
    }
  };

  private final Encoder<MessageType> messageEncoder = new Encoder<MessageType>() {
    @Override public void encode(ReverseProtoWriter writer, MessageType value) throws IOException {
      Syntax syntax = schema.protoFile(value.type()).syntax();

      Map<Field, SyntheticMapEntry> syntheticMaps =
          collectSyntheticMapEntries(value.type().toString(), value.declaredFields());

      List<EncodedOneOf> encodedOneOfs = new ArrayList<>();

      // Collect the true oneofs.
      for (OneOf oneOf : value.oneOfs()) {
        List<EncodedField> oneOfFields = new ArrayList<>();
        for (Field field : oneOf.fields()) {
          oneOfFields.add(new EncodedField(syntax, field, null, null, encodedOneOfs.size()));
        }
        encodedOneOfs.add(new EncodedOneOf(oneOf.name(), oneOfFields));
      }

      // Collect encoded fields, synthesizing map types and oneofs.
      List<EncodedField> encodedFields = new ArrayList<>();
      for (Field field : value.declaredFields()) {
        SyntheticMapEntry syntheticMap = syntheticMaps.get(field);
        ProtoType type = syntheticMap != null ? syntheticMap.fieldType : field.type();
        EncodedField encodedField = new EncodedField(syntax, field, type, null, null);
        if (encodedField.isProto3Optional()) {
          encodedField = encodedField.withOneOfIndex(encodedOneOfs.size());
          encodedOneOfs.add(new EncodedOneOf("_" + field.name(), Collections.emptyList()));
        }
        encodedFields.add(encodedField);
      }

      // STRING.asRepeated().encodeWithTag(writer, 10, value.reserved_name)
      // ReservedRange.ADAPTER.asRepeated().encodeWithTag(writer, 9, value.reserved_range)

      messageOptionsProtoAdapter.encodeWithTag(writer, 7, toJsonOptions(value.options()));

      // Real and synthetic oneofs.
      oneOfEncoder.asRepeated().encodeWithTag(writer, 8, encodedOneOfs);

      List<Object> extensionRanges = new ArrayList<>();
      for (Extensions extensions : value.extensionsList()) {
        extensionRanges.addAll(extensions.values());
      }
      extensionRangeEncoder.asRepeated().encodeWithTag(writer, 5, extensionRanges);

      // Real and synthetic nested types.
      syntheticMapEntryEncoder.asRepeated()
          .encodeWithTag(writer, 3, new ArrayList<>(syntheticMaps.values()));
      encodeNestedTypes(writer, value.nestedTypes());

      // FieldDescriptorProto.ADAPTER.asRepeated().encodeWithTag(writer, 6, value.extension)

      List<EncodedField> fieldsAndOneOfFields = new ArrayList<>(encodedFields);
      for (EncodedOneOf encodedOneOf : encodedOneOfs) {
        fieldsAndOneOfFields.addAll(encodedOneOf.fields);
      }
      fieldsAndOneOfFields.sort(Comparator.<EncodedField>comparingInt(
              encodedField -> encodedField.field.location().line)
          .thenComparingInt(encodedField -> encodedField.field.location().column));
      fieldEncoder.asRepeated().encodeWithTag(writer, 2, fieldsAndOneOfFields);

      ProtoAdapter.STRING.encodeWithTag(writer, 1, value.type().simpleName());
    }
  };

  private void encodeNestedTypes(ReverseProtoWriter writer, List<Type> types) throws IOException {
    enumEncoder.asRepeated().encodeWithTag(writer, 4, typesOf(types, EnumType.class));
    messageEncoder.asRepeated().encodeWithTag(writer, 3, typesOf(types, MessageType.class));
    enclosingEncoder.asRepeated().encodeWithTag(writer, 3, typesOf(types, EnclosingType.class));
  }

  private final Encoder<EnclosingType> enclosingEncoder = new Encoder<EnclosingType>() {
    @Override public void encode(ReverseProtoWriter writer, EnclosingType value) throws IOException {
      messageOptionsProtoAdapter.encodeWithTag(writer, 7, toJsonOptions(value.options()));
      encodeNestedTypes(writer, value.nestedTypes());
      ProtoAdapter.STRING.encodeWithTag(writer, 1, value.type().simpleName());
    }
  };

  /**
   * Create synthetic map entry types for {@code fields}. These replace the fields' natural types
   * and will be emitted as children of the fields' declaring message.
   */
  private Map<Field, SyntheticMapEntry> collectSyntheticMapEntries(String enclosingTypeOrPackage,
      List<Field> fields) {
    Map<Field, SyntheticMapEntry> result = new LinkedHashMap<>();
    for (Field field : fields) {
      ProtoType fieldType = field.type();
      if (fieldType.isMap) {
        String name = Internal.camelCase(field.name(), true) + "Entry";
        result.put(field, new SyntheticMapEntry(enclosingTypeOrPackage, name,
            fieldType.keyType, fieldType.valueType));
      }
    }
    return result;
  }

  private final class SyntheticMapEntry {
    final ProtoType fieldType;
    final String name;
    final ProtoType keyType;
    final ProtoType valueType;

    SyntheticMapEntry(String enclosingTypeOrPackage, String name, ProtoType keyType,
        ProtoType valueType) {
      this.name = name;
      this.keyType = keyType;
      this.valueType = valueType;
      this.fieldType = ProtoType.get(enclosingTypeOrPackage, name);
    }
  }

  private final Encoder<SyntheticMapEntry> syntheticMapEntryEncoder =
      new Encoder<SyntheticMapEntry>() {
        final Encoder<SyntheticMapEntry> keyFieldEncoder = new Encoder<SyntheticMapEntry>() {
          @Override public void encode(ReverseProtoWriter writer, SyntheticMapEntry value)
              throws IOException {
            ProtoAdapter.STRING.encodeWithTag(writer, 10, "key");
            if (!value.keyType.isScalar) {
              ProtoAdapter.STRING.encodeWithTag(writer, 6, dotName(value.keyType));
            }
            ProtoAdapter.INT32.encodeWithTag(writer, 5, typeTag(value.keyType));
            ProtoAdapter.INT32.encodeWithTag(writer, 4, 1); // 1 = Field.Label.OPTIONAL
            ProtoAdapter.INT32.encodeWithTag(writer, 3, 1);
            ProtoAdapter.STRING.encodeWithTag(writer, 1, "key");
          }
        };

        final Encoder<SyntheticMapEntry> valueFieldEncoder = new Encoder<SyntheticMapEntry>() {
          @Override public void encode(ReverseProtoWriter writer, SyntheticMapEntry value)
              throws IOException {
            ProtoAdapter.STRING.encodeWithTag(writer, 10, "value");
            if (!value.valueType.isScalar) {
              ProtoAdapter.STRING.encodeWithTag(writer, 6, dotName(value.valueType));
            }
            ProtoAdapter.INT32.encodeWithTag(writer, 5, typeTag(value.valueType));
            ProtoAdapter.INT32.encodeWithTag(writer, 4, 1); // 1 = Field.Label.OPTIONAL
            ProtoAdapter.INT32.encodeWithTag(writer, 3, 2);
            ProtoAdapter.STRING.encodeWithTag(writer, 1, "value");
          }
        };

        @Override public void encode(ReverseProtoWriter writer, SyntheticMapEntry value)
            throws IOException {
          Map<String, Object> mapEntry = new LinkedHashMap<>();
          mapEntry.put("map_entry", Boolean.TRUE);
          messageOptionsProtoAdapter.encodeWithTag(writer, 7, mapEntry);
          valueFieldEncoder.encodeWithTag(writer, 2, value);
          keyFieldEncoder.encodeWithTag(writer, 2, value);
          ProtoAdapter.STRING.encodeWithTag(writer, 1, value.name);
        }
      };

  /** Supplements the schema Field with overrides. */
  private static final class EncodedField {
    final Syntax syntax;
    final Field field;
    final ProtoType type;
    final String extendee;
    final Integer oneOfIndex;

    EncodedField(Syntax syntax, Field field, ProtoType type, String extendee,
        Integer oneOfIndex) {
      this.syntax = syntax;
      this.field = field;
      this.type = type;
      this.extendee = extendee;
      this.oneOfIndex = oneOfIndex;
    }

    EncodedField withOneOfIndex(int newOneOfIndex) {
      return new EncodedField(syntax, field, type, extendee, newOneOfIndex);
    }

    boolean isProto3Optional() {
      return syntax == Syntax.PROTO_3 && field.label() == Field.Label.OPTIONAL;
    }
  }

  private final Encoder<EncodedField> fieldEncoder = new Encoder<EncodedField>() {
    @Override public void encode(ReverseProtoWriter writer, EncodedField value)
        throws IOException {
      ProtoAdapter.INT32.encodeWithTag(writer, 9, value.oneOfIndex);
      if (value.isProto3Optional()) {
        ProtoAdapter.BOOL.encodeWithTag(writer, 17, Boolean.TRUE);
      }
      fieldOptionsProtoAdapter.encodeWithTag(writer, 8, toJsonOptions(value.field.options()));
      if (value.syntax == Syntax.PROTO_2
          && !Objects.equals(value.field.jsonName(), value.field.name())) {
        ProtoAdapter.STRING.encodeWithTag(writer, 10, value.field.jsonName());
      }
      ProtoAdapter.STRING.encodeWithTag(writer, 7, value.field.defaultValue());
      ProtoAdapter.STRING.encodeWithTag(writer, 2, value.extendee);
      if (!value.type.isScalar) {
        ProtoAdapter.STRING.encodeWithTag(writer, 6, dotName(value.type));
      }
      ProtoAdapter.INT32.encodeWithTag(writer, 5, typeTag(value.field.type()));
      ProtoAdapter.INT32.encodeWithTag(writer, 4, labelTag(value.field));
      ProtoAdapter.INT32.encodeWithTag(writer, 3, value.field.tag());
      ProtoAdapter.STRING.encodeWithTag(writer, 1, value.field.name());
    }
  };

  private static int labelTag(Field field) {
    Field.EncodeMode encodeMode = field.encodeMode();
    if (encodeMode == Field.EncodeMode.NULL_IF_ABSENT
        || encodeMode == Field.EncodeMode.OMIT_IDENTITY) {
      return 1;
    }
    if (encodeMode == Field.EncodeMode.REQUIRED) {
      return 2;
    }
    if (encodeMode == Field.EncodeMode.REPEATED
        || encodeMode == Field.EncodeMode.PACKED
        || encodeMode == Field.EncodeMode.MAP) {
      return 3;
    }
    throw new IllegalArgumentException("unexpected encodeMode: " + encodeMode);
  }

  private static String dotName(ProtoType type) {
    return "." + type;
  }

  private int typeTag(ProtoType type) {
    if (type.equals(ProtoType.DOUBLE)) return 1;
    if (type.equals(ProtoType.FLOAT)) return 2;
    if (type.equals(ProtoType.INT64)) return 3;
    if (type.equals(ProtoType.UINT64)) return 4;
    if (type.equals(ProtoType.INT32)) return 5;
    if (type.equals(ProtoType.FIXED64)) return 6;
    if (type.equals(ProtoType.FIXED32)) return 7;
    if (type.equals(ProtoType.BOOL)) return 8;
    if (type.equals(ProtoType.STRING)) return 9;
    if (schema.getType(type) instanceof MessageType) return 11;
    if (type.equals(ProtoType.BYTES)) return 12;
    if (type.equals(ProtoType.UINT32)) return 13;
    if (schema.getType(type) instanceof EnumType) return 14;
    if (type.equals(ProtoType.SFIXED32)) return 15;
    if (type.equals(ProtoType.SFIXED64)) return 16;
    if (type.equals(ProtoType.SINT32)) return 17;
    if (type.equals(ProtoType.SINT64)) return 18;
    if (type.isMap) return 11; // Maps are encoded as messages.
    throw new IllegalArgumentException("unexpected type: " + type);
  }

  private static final class EncodedOneOf {
    final String name;
    final List<EncodedField> fields;

    EncodedOneOf(String name, List<EncodedField> fields) {
      this.name = name;
      this.fields = fields;
    }
  }

  private final Encoder<EncodedOneOf> oneOfEncoder = new Encoder<EncodedOneOf>() {
    @Override public void encode(ReverseProtoWriter writer, EncodedOneOf value)
        throws IOException {
      // OneofOptions.ADAPTER.encodeWithTag(writer, 2, value.options)
      ProtoAdapter.STRING.encodeWithTag(writer, 1, value.name);
    }
  };

  private final Encoder<EnumType> enumEncoder = new Encoder<EnumType>() {
    @Override public void encode(ReverseProtoWriter writer, EnumType value) throws IOException {
      // STRING.asRepeated().encodeWithTag(writer, 5, value.reserved_name)
      // EnumReservedRange.ADAPTER.asRepeated().encodeWithTag(writer, 4, value.reserved_range)
      enumOptionsProtoAdapter.encodeWithTag(writer, 3, toJsonOptions(value.options()));
      enumConstantEncoder.asRepeated().encodeWithTag(writer, 2, value.constants());
      ProtoAdapter.STRING.encodeWithTag(writer, 1, value.name());
    }
  };

  private final Encoder<Object> extensionRangeEncoder = new Encoder<Object>() {
    @Override public void encode(ReverseProtoWriter writer, Object value) throws IOException {
      if (value instanceof Integer) {
        ProtoAdapter.INT32.encodeWithTag(writer, 2, (Integer) value + 1); // Exclusive.
        ProtoAdapter.INT32.encodeWithTag(writer, 1, (Integer) value); // Inclusive.
      } else if (value instanceof int[]) {
        int[] range = (int[]) value;
        ProtoAdapter.INT32.encodeWithTag(writer, 2, range[1] + 1); // Exclusive.
        ProtoAdapter.INT32.encodeWithTag(writer, 1, range[0]); // Inclusive.
      } else {
        throw new IllegalArgumentException("unexpected extension range: " + value);
      }
    }
  };

  private final Encoder<EnumConstant> enumConstantEncoder = new Encoder<EnumConstant>() {
    @Override public void encode(ReverseProtoWriter writer, EnumConstant value) throws IOException {
      enumValueOptionsProtoAdapter.encodeWithTag(writer, 3, toJsonOptions(value.options()));
      ProtoAdapter.INT32.encodeWithTag(writer, 2, value.tag());
      ProtoAdapter.STRING.encodeWithTag(writer, 1, value.name());
    }
  };

  private final Encoder<Service> serviceEncoder = new Encoder<Service>() {
    @Override public void encode(ReverseProtoWriter writer, Service value) throws IOException {
      serviceOptionsProtoAdapter.encodeWithTag(writer, 3, toJsonOptions(value.options()));
      rpcEncoder.asRepeated().encodeWithTag(writer, 2, value.rpcs());
      ProtoAdapter.STRING.encodeWithTag(writer, 1, value.name());
    }
  };

  private final Encoder<Rpc> rpcEncoder = new Encoder<Rpc>() {
    @Override public void encode(ReverseProtoWriter writer, Rpc value) throws IOException {
      if (value.responseStreaming()) {
        ProtoAdapter.BOOL.encodeWithTag(writer, 6, Boolean.TRUE);
      }
      if (value.requestStreaming()) {
        ProtoAdapter.BOOL.encodeWithTag(writer, 5, Boolean.TRUE);
      }
      rpcOptionsProtoAdapter.encodeWithTag(writer, 4, toJsonOptions(value.options()));
      ProtoAdapter.STRING.encodeWithTag(writer, 3, dotName(value.responseType()));
      ProtoAdapter.STRING.encodeWithTag(writer, 2, dotName(value.requestType()));
      ProtoAdapter.STRING.encodeWithTag(writer, 1, value.name());
    }
  };

  /** The Java analog of upstream's filterIsInstance calls. */
  private static <T extends Type> List<T> typesOf(List<Type> types, Class<T> kind) {
    List<T> result = new ArrayList<>();
    for (Type type : types) {
      if (kind.isInstance(type)) result.add(kind.cast(type));
    }
    return result;
  }

  /**
   * Encodes a synthetic map type.
   */
  private abstract static class Encoder<T> extends ProtoAdapter<T> {
    Encoder() {
      super(FieldEncoding.LENGTH_DELIMITED, null, null, Syntax.PROTO_2);
    }

    @Override public T redact(T value) {
      return value;
    }

    @Override public int encodedSize(T value) {
      throw new UnsupportedOperationException();
    }

    @Override public void encode(ProtoWriter writer, T value) {
      throw new UnsupportedOperationException();
    }

    @Override public T decode(ProtoReader reader) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Converts this options instance to a JSON-style value that the runtime adapter can process.
   *
   * <p>TODO: offer an alternative to SchemaProtoAdapterFactory that uses ProtoMember or tag keys
   * so we don't need a clumsy conversion through JSON.
   */
  private Object toJsonOptions(Options options) {
    Map<ProtoMember, Object> optionsMap = options.map();
    if (optionsMap.isEmpty()) return null;

    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<ProtoMember, Object> entry : optionsMap.entrySet()) {
      Field field = schema.getField(entry.getKey());
      if (field == null) {
        throw new IllegalArgumentException("unexpected options field: " + entry.getKey());
      }
      result.put(field.name(), toJson(field, entry.getValue()));
    }

    return result;
  }

  private Object toJson(Field field, Object value) {
    if (field.isRepeated()) {
      List<Object> result = new ArrayList<>();
      for (Object element : (List<?>) value) {
        result.add(toJsonSingle(field.type(), element));
      }
      return result;
    }
    return toJsonSingle(field.type(), value);
  }

  /**
   * Convert {@code value} to a untyped value that preserves its binary encoding. This converts
   * strings to the right-sized primitive type. This converts unsigned values to the signed value
   * that has the same binary encoding.
   */
  private Object toJsonSingle(ProtoType type, Object value) {
    if (type.equals(ProtoType.BOOL)) return Boolean.parseBoolean((String) value);
    if (type.equals(ProtoType.BYTES)) {
      return ByteString.encodeUtf8((String) value);
    }
    if (type.equals(ProtoType.DOUBLE)) return Double.parseDouble((String) value);
    if (type.equals(ProtoType.FIXED32)) return parseUnsignedInt((String) value);
    if (type.equals(ProtoType.FIXED64)) return parseUnsignedLong((String) value);
    if (type.equals(ProtoType.FLOAT)) return Float.parseFloat((String) value);
    if (type.equals(ProtoType.INT32)) return Integer.parseInt((String) value);
    if (type.equals(ProtoType.INT64)) return Long.parseLong((String) value);
    if (type.equals(ProtoType.SFIXED32)) return Integer.parseInt((String) value);
    if (type.equals(ProtoType.SFIXED64)) return Long.parseLong((String) value);
    if (type.equals(ProtoType.SINT32)) return Integer.parseInt((String) value);
    if (type.equals(ProtoType.SINT64)) return Long.parseLong((String) value);
    if (type.equals(ProtoType.STRING)) return value;
    if (type.equals(ProtoType.UINT32)) return parseUnsignedInt((String) value);
    if (type.equals(ProtoType.UINT64)) return parseUnsignedLong((String) value);
    if (schema.getType(type) instanceof MessageType) {
      return toJsonMap((Map<ProtoMember, Object>) value);
    }
    if (schema.getType(type) instanceof EnumType) return value;
    throw new IllegalArgumentException("not implemented: " + type);
  }

  private Map<String, Object> toJsonMap(Map<ProtoMember, Object> map) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<ProtoMember, Object> entry : map.entrySet()) {
      Field field = schema.getField(entry.getKey());
      if (field == null) continue; // TODO: warn about this??
      result.put(entry.getKey().simpleName(), toJson(field, entry.getValue()));
    }
    return result;
  }

  /** Kotlin's String.toUInt().toInt(): unsigned parse, then the same-bits signed int. */
  private static int parseUnsignedInt(String value) {
    return Integer.parseUnsignedInt(value);
  }

  /** Kotlin's String.toULong().toLong(): unsigned parse, then the same-bits signed long. */
  private static long parseUnsignedLong(String value) {
    return Long.parseUnsignedLong(value);
  }
}
