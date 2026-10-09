/*
 * Copyright (C) 2020 Square, Inc.
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
package com.squareup.wire;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okio.ByteString;

/**
 * A builder for generated Kotlin messages that have no explicit Builder (javaInterop = false):
 * values collect in side maps and the message is reconstructed through its primary
 * constructor, ordered by schemaIndex. Upstream declares this Kotlin-internal; public here per
 * the translation conventions.
 */
public class KotlinConstructorBuilder<M extends Message<M, B>, B extends Message.Builder<M, B>>
    extends Message.Builder<M, B> {
  private final Class<M> messageType;
  private final Map<Integer, WireFieldAndValue> fieldValueMap = new LinkedHashMap<>();
  private final Map<Integer, RepeatedFieldValue> repeatedFieldValueMap = new LinkedHashMap<>();
  private final Map<Integer, MapFieldValue> mapFieldKeyValueMap = new LinkedHashMap<>();
  private final Map<String, Object> sealedOneofValues = new LinkedHashMap<>();

  private static final class WireFieldAndValue {
    final WireField field;
    final Object value;

    WireFieldAndValue(WireField field, Object value) {
      this.field = field;
      this.value = value;
    }
  }

  private static final class RepeatedFieldValue {
    final WireField field;
    final List<?> value;

    RepeatedFieldValue(WireField field, List<?> value) {
      this.field = field;
      this.value = value;
    }
  }

  private static final class MapFieldValue {
    final WireField field;
    final Map<?, ?> value;

    MapFieldValue(WireField field, Map<?, ?> value) {
      this.field = field;
      this.value = value;
    }
  }

  public KotlinConstructorBuilder(Class<M> messageType) {
    this.messageType = messageType;
  }

  private static boolean isMap(WireField field) {
    return !field.keyAdapter().isEmpty();
  }

  public void set(WireField field, Object value) {
    if (isMap(field)) {
      mapFieldKeyValueMap.put(field.tag(), new MapFieldValue(field, (Map<?, ?>) value));
    } else if (field.label().isRepeated()) {
      repeatedFieldValueMap.put(field.tag(), new RepeatedFieldValue(field, (List<?>) value));
    } else {
      fieldValueMap.put(field.tag(), new WireFieldAndValue(field, value));
      if (value != null && field.label().isOneOf()) {
        clobberOtherIsOneOfs(field);
      }
    }
  }

  public void setSealedOneof(String fieldName, Object value) {
    sealedOneofValues.put(fieldName, value);
  }

  public Object getSealedOneof(String fieldName) {
    return sealedOneofValues.get(fieldName);
  }

  private void clobberOtherIsOneOfs(WireField field) {
    fieldValueMap.values().removeIf(
        entry -> entry.field.oneofName().equals(field.oneofName())
            && entry.field.tag() != field.tag());
  }

  public Object get(WireField field) {
    if (isMap(field)) {
      MapFieldValue entry = mapFieldKeyValueMap.get(field.tag());
      return entry != null ? entry.value : java.util.Collections.emptyMap();
    }
    if (field.label().isRepeated()) {
      RepeatedFieldValue entry = repeatedFieldValueMap.get(field.tag());
      return entry != null ? entry.value : java.util.Collections.emptyList();
    }
    WireFieldAndValue entry = fieldValueMap.get(field.tag());
    Object value = entry != null ? entry.value : null;
    // Proto3 singular fields have non-nullable types with default parameters; pass the identity
    // value to please the constructor.
    if (value == null && field.label() == WireField.Label.OMIT_IDENTITY) {
      return ProtoAdapter.get(field.adapter()).getIdentity();
    }
    return value;
  }

  @SuppressWarnings({ "unchecked", "deprecation" }) // Engine layer: generated ctors take okio unknown fields (docs/api-surface.md).
  @Override public M build() {
    List<ConstructorParam> params = new ArrayList<>();
    for (java.lang.reflect.Field field : messageType.getDeclaredFields()) {
      WireField wireField = field.getAnnotation(WireField.class);
      if (wireField != null) {
        final WireField wf = wireField;
        params.add(new ConstructorParam(field.getType(), wireField.schemaIndex()) {
          @Override public Object value() {
            return get(wf);
          }
        });
        continue;
      }
      WireSealedOneof sealedOneof = field.getAnnotation(WireSealedOneof.class);
      if (sealedOneof != null) {
        final String name = field.getName();
        params.add(new ConstructorParam(field.getType(), sealedOneof.schemaIndex()) {
          @Override public Object value() {
            return sealedOneofValues.get(name);
          }
        });
      }
    }
    params.sort(java.util.Comparator.comparingInt(p -> p.schemaIndex));

    Class<?>[] parameterTypes = new Class<?>[params.size() + 1];
    Object[] args = new Object[params.size() + 1];
    for (int i = 0; i < params.size(); i++) {
      parameterTypes[i] = params.get(i).type;
      args[i] = params.get(i).value();
    }
    parameterTypes[params.size()] = ByteString.class; // unknown_fields
    args[params.size()] = buildUnknownFields();

    try {
      Constructor<M> constructor = messageType.getDeclaredConstructor(parameterTypes);
      constructor.setAccessible(true);
      return constructor.newInstance(args);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private abstract static class ConstructorParam {
    final Class<?> type;
    final int schemaIndex;

    ConstructorParam(Class<?> type, int schemaIndex) {
      this.type = type;
      this.schemaIndex = schemaIndex;
    }

    abstract Object value();
  }
}
