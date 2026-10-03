/*
 * Copyright (C) 2019 Square, Inc.
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
package com.squareup.wire.internal;

import com.squareup.wire.ProtoAdapter.EnumConstantNotFoundException;
import com.squareup.wire.FieldEncoding;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.ProtoReader;
import com.squareup.wire.ProtoWriter;
import com.squareup.wire.ReverseProtoWriter;
import com.squareup.wire.Syntax;
import com.squareup.wire.WireField;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RuntimeMessageAdapter<M, B> extends ProtoAdapter<M> {
  private static final String REDACTED = "██";

  /**
   * Built-in adapters for the well-known message types that map to platform types instead of
   * generated Message classes. Singular fields using these adapters merge duplicated
   * occurrences like any other message field. STRUCT_NULL is absent because
   * google.protobuf.NullValue is an enum, not a message.
   */
  @SuppressWarnings("deprecation") // Engine layer: the bytes wrapper adapter stays okio-typed (docs/api-surface.md).
  private static final Set<ProtoAdapter<?>> MESSAGE_BACKED_BUILT_IN_ADAPTERS = new HashSet<>(
      Arrays.asList(
          ProtoAdapter.DURATION,
          ProtoAdapter.INSTANT,
          ProtoAdapter.EMPTY,
          ProtoAdapter.FIELD_MASK,
          ProtoAdapter.STRUCT_MAP,
          ProtoAdapter.STRUCT_VALUE,
          ProtoAdapter.STRUCT_LIST,
          ProtoAdapter.DOUBLE_VALUE,
          ProtoAdapter.FLOAT_VALUE,
          ProtoAdapter.INT64_VALUE,
          ProtoAdapter.UINT64_VALUE,
          ProtoAdapter.INT32_VALUE,
          ProtoAdapter.UINT32_VALUE,
          ProtoAdapter.BOOL_VALUE,
          ProtoAdapter.STRING_VALUE,
          ProtoAdapter.BYTES_VALUE));

  private final MessageBinding<M, B> binding;
  private final boolean preservingProtoFieldNames;
  private final Class<?> messageType;
  public final Map<Integer, FieldOrOneOfBinding<M, B>> fields;

  /** Field bindings by index, consistent with jsonNames and jsonAlternateNames. */
  public final FieldOrOneOfBinding<M, B>[] fieldBindingsArray;
  public final List<String> jsonNames;
  public final List<String> jsonAlternateNames;

  @SuppressWarnings("unchecked")
  public RuntimeMessageAdapter(MessageBinding<M, B> binding, boolean preservingProtoFieldNames) {
    super(FieldEncoding.LENGTH_DELIMITED, binding.messageType(), binding.typeUrl(),
        binding.syntax(), null, null);
    this.binding = binding;
    this.preservingProtoFieldNames = preservingProtoFieldNames;
    this.messageType = binding.messageType();
    this.fields = binding.fields();
    this.fieldBindingsArray = binding.fields().values().toArray(
        new FieldOrOneOfBinding[0]);

    List<String> jsonNames = new ArrayList<>(fieldBindingsArray.length);
    for (FieldOrOneOfBinding<M, B> field : fieldBindingsArray) {
      jsonNames.add(jsonName(field));
    }
    this.jsonNames = jsonNames;

    List<String> jsonAlternateNames = new ArrayList<>(fieldBindingsArray.length);
    for (FieldOrOneOfBinding<M, B> field : fieldBindingsArray) {
      String alternate;
      if (!jsonName(field).equals(field.declaredName())) {
        alternate = field.declaredName();
      } else if (!jsonName(field).equals(field.name())) {
        alternate = field.name();
      } else {
        String camelCaseDeclaredName = Internal.camelCase(field.declaredName(), false);
        if (!jsonName(field).equals(camelCaseDeclaredName)
            // Do not shadow an existing jsonName.
            && !jsonNames.contains(camelCaseDeclaredName)) {
          alternate = camelCaseDeclaredName;
        } else {
          alternate = null;
        }
      }
      jsonAlternateNames.add(alternate);
    }
    this.jsonAlternateNames = jsonAlternateNames;
  }

  private String jsonName(FieldOrOneOfBinding<M, B> field) {
    return field.wireFieldJsonName().isEmpty() || preservingProtoFieldNames
        ? field.declaredName()
        : field.wireFieldJsonName();
  }

  public B newBuilder() {
    return binding.newBuilder();
  }

  @Override public int encodedSize(M value) {
    int cachedSerializedSize = binding.getCachedSerializedSize(value);
    if (cachedSerializedSize != 0) return cachedSerializedSize;

    int size = 0;
    for (FieldOrOneOfBinding<M, B> field : fields.values()) {
      Object fieldValue = field.get(value);
      if (fieldValue == null) continue;
      size += field.adapter().encodedSizeWithTag(field.tag(), fieldValue);
    }
    size += binding.unknownFields(value).size();

    binding.setCachedSerializedSize(value, size);
    return size;
  }

  @Override public void encode(ProtoWriter writer, M value) throws IOException {
    for (FieldOrOneOfBinding<M, B> field : fields.values()) {
      Object bound = field.get(value);
      if (bound == null) continue;
      field.adapter().encodeWithTag(writer, field.tag(), bound);
    }
    writer.writeBytes(binding.unknownFields(value));
  }

  @Override public void encode(ReverseProtoWriter writer, M value) throws IOException {
    writer.writeBytes(binding.unknownFields(value));
    for (int f = fieldBindingsArray.length - 1; f >= 0; f--) {
      FieldOrOneOfBinding<M, B> field = fieldBindingsArray[f];
      Object bound = field.get(value);
      if (bound == null) continue;
      field.adapter().encodeWithTag(writer, field.tag(), bound);
    }
  }

  @SuppressWarnings("unchecked")
  @Override public M redact(M value) {
    B builder = binding.newBuilder();
    for (FieldOrOneOfBinding<M, B> field : fields.values()) {
      if (field.redacted() && field.label() == WireField.Label.REQUIRED) {
        throw new UnsupportedOperationException(
            "Field '" + field.name() + "' in " + type + " is required and cannot be redacted.");
      }
      boolean isMessage = field.isMessage();
      if (field.redacted() || isMessage && !field.label().isRepeated()) {
        Object builderValue = field.getFromBuilder(builder);
        if (builderValue != null) {
          Object redactedValue = field.adapter().redact(builderValue);
          field.set(builder, redactedValue);
        }
      } else if (isMessage && field.label().isRepeated()) {
        List<Object> values = (List<Object>) field.getFromBuilder(builder);
        ProtoAdapter<Object> adapter = (ProtoAdapter<Object>) field.singleAdapter();
        field.set(builder, Internal.redactElements(values, adapter));
      }
    }
    binding.clearUnknownFields(builder);
    return binding.build(builder);
  }

  @Override public boolean equals(Object other) {
    return other instanceof RuntimeMessageAdapter
        && ((RuntimeMessageAdapter<?, ?>) other).messageType == messageType;
  }

  @Override public int hashCode() {
    return messageType.hashCode();
  }

  @Override public String toString(M value) {
    StringBuilder result = new StringBuilder();
    result.append(messageType.getSimpleName());
    result.append('{');
    boolean first = true;
    for (FieldOrOneOfBinding<M, B> field : fields.values()) {
      Object bound = field.get(value);
      if (bound == null) continue;
      if (!first) result.append(", ");
      first = false;
      result.append(field.name());
      result.append('=');
      result.append(field.redacted() ? REDACTED : bound);
    }
    result.append('}');
    return result.toString();
  }

  @SuppressWarnings("unchecked")
  @Override public M decode(ProtoReader reader) throws IOException {
    B builder = newBuilder();
    long token = reader.beginMessage();
    while (true) {
      int tag = reader.nextTag();
      if (tag == -1) break;
      FieldOrOneOfBinding<M, B> field = fields.get(tag);
      try {
        if (field != null) {
          if (field.isMap()) {
            Object value = field.adapter().decode(reader);
            field.value(builder, value);
          } else {
            ProtoAdapter<?> singleAdapter = field.singleAdapter();
            if ((field.isMessage() || MESSAGE_BACKED_BUILT_IN_ADAPTERS.contains(singleAdapter))
                && !field.label().isRepeated()) {
              ProtoAdapter<Object> adapter = (ProtoAdapter<Object>) singleAdapter;
              Object value =
                  Internal.decodeMessageOrMerge(adapter, reader, field.getFromBuilder(builder));
              field.set(builder, value);
            } else {
              Object value = singleAdapter.decode(reader);
              field.value(builder, value);
            }
          }
        } else {
          FieldEncoding fieldEncoding = reader.peekFieldEncoding();
          Object value = fieldEncoding.rawProtoAdapter().decode(reader);
          binding.addUnknownField(builder, tag, fieldEncoding, value);
        }
      } catch (EnumConstantNotFoundException e) {
        // An unknown Enum value was encountered, store it as an unknown field.
        binding.addUnknownField(builder, tag, FieldEncoding.VARINT, (long) e.value);
      }
    }
    reader.endMessageAndGetUnknownFields(token); // Ignore return value
    return binding.build(builder);
  }

  /** Invokes [encodeValue] for each field that should be written as JSON. */
  public interface FieldEncoder<A> {
    void encodeValue(String name, Object value, A adapter);
  }

  public <A> void writeAllFields(M message, List<A> jsonAdapters, A redactedFieldsAdapter,
      FieldEncoder<A> encodeValue) {
    List<String> redactedFields = null;
    for (int index = 0; index < fieldBindingsArray.length; index++) {
      FieldOrOneOfBinding<M, B> field = fieldBindingsArray[index];
      Object value = field.get(message);
      if (field.omitFromJson(syntax, value)) continue;
      if (field.redacted() && redactedFieldsAdapter != null && value != null) {
        // Initialize here to avoid a performance hit for non-redacted code.
        if (redactedFields == null) {
          redactedFields = new ArrayList<>();
        }
        redactedFields.add(jsonNames.get(index));
        continue;
      }
      encodeValue.encodeValue(jsonNames.get(index), value, jsonAdapters.get(index));
    }
    if (redactedFields != null && !redactedFields.isEmpty()) {
      encodeValue.encodeValue("__redacted_fields", redactedFields, redactedFieldsAdapter);
    }
  }
}
