/*
 * Copyright (C) 2015 Square, Inc.
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
package com.squareup.wire.schema;

import com.squareup.wire.AnyMessage;
import com.squareup.wire.FieldEncoding;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.ProtoReader;
import com.squareup.wire.ProtoWriter;
import com.squareup.wire.ReverseProtoWriter;
import com.squareup.wire.Syntax;
import com.squareup.wire.WireField;
import com.squareup.wire.internal.FieldOrOneOfBinding;
import com.squareup.wire.internal.MessageBinding;
import com.squareup.wire.internal.RuntimeMessageAdapter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okio.ByteString;

/**
 * Creates type adapters to read and write protocol buffer data from a schema model. This doesn't
 * require an intermediate code gen step.
 */
final class SchemaProtoAdapterFactory {
  final Schema schema;
  private final boolean includeUnknown;

  private static final Map<ProtoType, ProtoAdapter<?>> SCALAR_AND_WELL_KNOWN_ADAPTERS =
      createScalarAndWellKnownAdapters();

  /** Bytes values cross this schema-driven surface as {@link com.squareup.wire.Bytes} (docs/api-surface.md, phase 2). */
  private static Map<ProtoType, ProtoAdapter<?>> createScalarAndWellKnownAdapters() {
    Map<ProtoType, ProtoAdapter<?>> adapters = new HashMap<>();
    adapters.put(ProtoType.BOOL, ProtoAdapter.BOOL);
    adapters.put(ProtoType.BYTES, ProtoAdapter.WIRE_BYTES);
    adapters.put(ProtoType.DOUBLE, ProtoAdapter.DOUBLE);
    adapters.put(ProtoType.FLOAT, ProtoAdapter.FLOAT);
    adapters.put(ProtoType.FIXED32, ProtoAdapter.FIXED32);
    adapters.put(ProtoType.FIXED64, ProtoAdapter.FIXED64);
    adapters.put(ProtoType.INT32, ProtoAdapter.INT32);
    adapters.put(ProtoType.INT64, ProtoAdapter.INT64);
    adapters.put(ProtoType.SFIXED32, ProtoAdapter.SFIXED32);
    adapters.put(ProtoType.SFIXED64, ProtoAdapter.SFIXED64);
    adapters.put(ProtoType.SINT32, ProtoAdapter.SINT32);
    adapters.put(ProtoType.SINT64, ProtoAdapter.SINT64);
    adapters.put(ProtoType.STRING, ProtoAdapter.STRING);
    adapters.put(ProtoType.UINT32, ProtoAdapter.UINT32);
    adapters.put(ProtoType.UINT64, ProtoAdapter.UINT64);
    adapters.put(ProtoType.ANY, AnyMessage.ADAPTER);
    adapters.put(ProtoType.DURATION, ProtoAdapter.DURATION);
    adapters.put(ProtoType.TIMESTAMP, ProtoAdapter.INSTANT);
    // TASK-26: Empty crosses this surface as the unit value, matching upstream's
    // ProtoAdapter<Unit>; the Void-typed EMPTY cannot represent a present field.
    adapters.put(ProtoType.EMPTY, ProtoAdapter.WIRE_EMPTY);
    adapters.put(ProtoType.FIELD_MASK, ProtoAdapter.FIELD_MASK);
    adapters.put(ProtoType.STRUCT_MAP, ProtoAdapter.STRUCT_MAP);
    adapters.put(ProtoType.STRUCT_VALUE, ProtoAdapter.STRUCT_VALUE);
    adapters.put(ProtoType.STRUCT_NULL, ProtoAdapter.STRUCT_NULL);
    adapters.put(ProtoType.STRUCT_LIST, ProtoAdapter.STRUCT_LIST);
    adapters.put(ProtoType.DOUBLE_VALUE, ProtoAdapter.DOUBLE_VALUE);
    adapters.put(ProtoType.FLOAT_VALUE, ProtoAdapter.FLOAT_VALUE);
    adapters.put(ProtoType.INT64_VALUE, ProtoAdapter.INT64_VALUE);
    adapters.put(ProtoType.UINT64_VALUE, ProtoAdapter.UINT64_VALUE);
    adapters.put(ProtoType.INT32_VALUE, ProtoAdapter.INT32_VALUE);
    adapters.put(ProtoType.UINT32_VALUE, ProtoAdapter.UINT32_VALUE);
    adapters.put(ProtoType.BOOL_VALUE, ProtoAdapter.BOOL_VALUE);
    adapters.put(ProtoType.STRING_VALUE, ProtoAdapter.STRING_VALUE);
    adapters.put(ProtoType.BYTES_VALUE, ProtoAdapter.WIRE_BYTES_VALUE);
    return adapters;
  }

  private final Map<ProtoType, ProtoAdapter<?>> adapterMap;

  SchemaProtoAdapterFactory(Schema schema, boolean includeUnknown) {
    this.schema = schema;
    this.includeUnknown = includeUnknown;
    this.adapterMap = new HashMap<>(SCALAR_AND_WELL_KNOWN_ADAPTERS);
  }

  @SuppressWarnings("unchecked")
  ProtoAdapter<Object> get(ProtoType protoType) {
    if (protoType.isMap) throw new UnsupportedOperationException("map types not supported");
    ProtoAdapter<?> result = adapterMap.get(protoType);
    if (result != null) {
      return (ProtoAdapter<Object>) result;
    }
    Type type = schema.getType(protoType);
    if (type == null) {
      throw new IllegalArgumentException("unknown type: " + protoType);
    }
    if (type instanceof EnumType) {
      EnumAdapter enumAdapter = new EnumAdapter((EnumType) type);
      adapterMap.put(protoType, enumAdapter);
      return (ProtoAdapter<Object>) enumAdapter;
    }
    if (type instanceof MessageType) {
      MessageType messageType = (MessageType) type;
      SchemaMessageBinding messageBinding = new SchemaMessageBinding(messageType.type.typeUrl(),
          messageType.syntax(), includeUnknown);
      // Put the adapter in the map early to mitigate the recursive calls to get() made below.
      DeferredAdapter<Map<String, Object>> deferredAdapter =
          new DeferredAdapter<>(messageBinding);
      adapterMap.put(protoType, deferredAdapter);
      for (Field field : messageType.fields()) {
        messageBinding.fields.put(field.tag(), new SchemaFieldOrOneOfBinding(field, null));
      }
      for (OneOf oneOf : messageType.oneOfs()) {
        for (Field field : oneOf.fields()) {
          messageBinding.fields.put(field.tag(), new SchemaFieldOrOneOfBinding(field, oneOf));
        }
      }
      RuntimeMessageAdapter<Map<String, Object>, Map<String, Object>> messageAdapter =
          new RuntimeMessageAdapter<>(messageBinding, false);
      deferredAdapter.delegate = messageAdapter;
      adapterMap.put(protoType, messageAdapter);
      return (ProtoAdapter<Object>) (ProtoAdapter<?>) messageAdapter;
    }
    throw new IllegalArgumentException("unexpected type: " + protoType);
  }

  /** We prevent cycles by linking against while we're still building the graph of adapters. */
  private static final class DeferredAdapter<T> extends ProtoAdapter<T> {
    ProtoAdapter<T> delegate;

    DeferredAdapter(SchemaMessageBinding binding) {
      super(FieldEncoding.LENGTH_DELIMITED, (Class) Map.class, binding.typeUrl(),
          binding.syntax());
    }

    @Override public T decode(ProtoReader reader) throws IOException {
      return delegate.decode(reader);
    }

    @Override public void encode(ProtoWriter writer, T value) throws IOException {
      delegate.encode(writer, value);
    }

    @Override public void encode(ReverseProtoWriter writer, T value) throws IOException {
      delegate.encode(writer, value);
    }

    @Override public int encodedSize(T value) {
      return delegate.encodedSize(value);
    }

    @Override public T redact(T value) {
      return delegate.redact(value);
    }
  }

  private final class EnumAdapter extends ProtoAdapter<Object> {
    private final EnumType enumType;

    EnumAdapter(EnumType enumType) {
      super(FieldEncoding.VARINT, Object.class, null, enumType.syntax());
      this.enumType = enumType;
    }

    @Override public int encodedSize(Object value) {
      if (value instanceof String) {
        return INT32.encodedSize(enumType.constant((String) value).tag());
      }
      if (value instanceof Integer) {
        return INT32.encodedSize((Integer) value);
      }
      throw new IllegalArgumentException("unexpected " + enumType.type() + ": " + value);
    }

    @Override public void encode(ProtoWriter writer, Object value) throws IOException {
      writeVarint32(writer, value);
    }

    @Override public void encode(ReverseProtoWriter writer, Object value) throws IOException {
      writeVarint32(writer, value);
    }

    private void writeVarint32(ProtoWriter writer, Object value) throws IOException {
      if (value instanceof String) {
        writer.writeVarint32(enumType.constant((String) value).tag());
      } else if (value instanceof Integer) {
        writer.writeVarint32((Integer) value);
      } else {
        throw new IllegalArgumentException("unexpected " + enumType.type() + ": " + value);
      }
    }

    private void writeVarint32(ReverseProtoWriter writer, Object value) throws IOException {
      if (value instanceof String) {
        writer.writeVarint32(enumType.constant((String) value).tag());
      } else if (value instanceof Integer) {
        writer.writeVarint32((Integer) value);
      } else {
        throw new IllegalArgumentException("unexpected " + enumType.type() + ": " + value);
      }
    }

    @Override public Object decode(ProtoReader reader) throws IOException {
      int value = UINT32.decode(reader);
      EnumConstant constant = enumType.constant(value);
      return constant != null ? constant.name() : value;
    }

    @Override public Object redact(Object value) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class SchemaMessageBinding
      implements MessageBinding<Map<String, Object>, Map<String, Object>> {
    private final String typeUrl;
    private final Syntax syntax;
    private final boolean includeUnknown;

    final Map<Integer, FieldOrOneOfBinding<Map<String, Object>, Map<String, Object>>> fields =
        new LinkedHashMap<>();

    SchemaMessageBinding(String typeUrl, Syntax syntax, boolean includeUnknown) {
      this.typeUrl = typeUrl;
      this.syntax = syntax;
      this.includeUnknown = includeUnknown;
    }

    @Override public Class<?> messageType() {
      return Map.class;
    }

    @Override
    public Map<Integer, FieldOrOneOfBinding<Map<String, Object>, Map<String, Object>>> fields() {
      return fields;
    }

    @Override public String typeUrl() {
      return typeUrl;
    }

    @Override public Syntax syntax() {
      return syntax;
    }

    @Override public ByteString unknownFields(Map<String, Object> message) {
      return ByteString.EMPTY;
    }

    @Override public int getCachedSerializedSize(Map<String, Object> message) {
      return 0;
    }

    @Override public void setCachedSerializedSize(Map<String, Object> message, int size) {
    }

    @Override public Map<String, Object> newBuilder() {
      return new LinkedHashMap<>();
    }

    @Override public Map<String, Object> build(Map<String, Object> builder) {
      return new LinkedHashMap<>(builder);
    }

    @Override public void addUnknownField(Map<String, Object> builder, int tag,
        FieldEncoding fieldEncoding, Object value) {
      if (!includeUnknown || value == null) return;
      String name = String.valueOf(tag);

      List<Object> values = (List<Object>) builder.get(name);
      if (values == null) {
        values = new ArrayList<>();
        builder.put(name, values);
      }
      values.add(value);
    }

    @Override public void clearUnknownFields(Map<String, Object> builder) {
    }
  }

  private final class SchemaFieldOrOneOfBinding
      extends FieldOrOneOfBinding<Map<String, Object>, Map<String, Object>> {
    private final Field field;
    private final OneOf oneOf;

    SchemaFieldOrOneOfBinding(Field field, OneOf oneOf) {
      this.field = field;
      this.oneOf = oneOf;
    }

    @Override public int tag() {
      return field.tag();
    }

    @Override public WireField.Label label() {
      if (oneOf != null) return WireField.Label.ONE_OF;
      switch (field.encodeMode()) {
        case OMIT_IDENTITY:
          return WireField.Label.OMIT_IDENTITY;
        case NULL_IF_ABSENT:
          return WireField.Label.OPTIONAL;
        case MAP:
        case PACKED:
        case REPEATED:
          return WireField.Label.REPEATED;
        case REQUIRED:
          return WireField.Label.REQUIRED;
        default:
          throw new AssertionError();
      }
    }

    @Override public boolean redacted() {
      return field.isRedacted();
    }

    @Override public boolean isMap() {
      return field.type().isMap;
    }

    @Override public boolean isMessage() {
      return schema.getType(field.type()) instanceof MessageType;
    }

    @Override public String name() {
      return field.name();
    }

    @Override public String declaredName() {
      return field.name();
    }

    @Override public String wireFieldJsonName() {
      return field.jsonName();
    }

    @Override public boolean writeIdentityValues() {
      return false;
    }

    @Override public ProtoAdapter<?> keyAdapter() {
      return SchemaProtoAdapterFactory.this.get(field.type().keyType);
    }

    @Override public ProtoAdapter<?> singleAdapter() {
      return SchemaProtoAdapterFactory.this.get(field.type());
    }

    @SuppressWarnings("unchecked")
    @Override public void value(Map<String, Object> builder, Object value) {
      if (isMap()) {
        Map<String, Object> map = (Map<String, Object>) builder.get(field.name());
        if (map == null) {
          map = new LinkedHashMap<>();
          builder.put(field.name(), map);
        }
        map.putAll((Map<String, Object>) value);
      } else if (field.isRepeated()) {
        List<Object> list = (List<Object>) builder.get(field.name());
        if (list == null) {
          list = new ArrayList<>();
          builder.put(field.name(), list);
        }
        list.add(value);
      } else {
        set(builder, value);
      }
    }

    @Override public void set(Map<String, Object> builder, Object value) {
      if (oneOf != null) {
        for (Field member : oneOf.fields()) {
          builder.remove(member.name());
        }
      }
      builder.put(field.name(), value);
    }

    @Override public Object get(Map<String, Object> message) {
      return message.get(field.name());
    }

    @Override public Object getFromBuilder(Map<String, Object> builder) {
      return builder.get(field.name());
    }
  }
}
