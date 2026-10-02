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

import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.util.List;
import java.util.Map;

/**
 * Translation of upstream wire-protoc-compatibility-tests ProtocStructHelper.kt: Kotlin
 * receiver extensions become static helpers; `Any?.toValue()` becomes
 * {@link #toValue(Object)}. The type dispatch keeps upstream's exact cases (Double, not
 * Number), so an Integer value still fails like upstream. See this module's
 * UPSTREAM-TEST-ADAPTATIONS.md.
 */
final class ProtocStructHelper {
  private ProtocStructHelper() {
  }

  static Struct toStruct(Map<String, ?> map) {
    Struct.Builder builder = Struct.newBuilder();
    for (Map.Entry<String, ?> entry : map.entrySet()) {
      builder.putFields(entry.getKey(), toValue(entry.getValue()));
    }
    return builder.build();
  }

  static Struct emptyStruct() {
    return Struct.newBuilder().build();
  }

  static ListValue toListValue(List<?> list) {
    ListValue.Builder builder = ListValue.newBuilder();
    for (Object any : list) {
      builder.addValues(toValue(any));
    }
    return builder.build();
  }

  static ListValue emptyListValue() {
    return ListValue.newBuilder().build();
  }

  static Value toValue(Object any) {
    if (any == null) {
      return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
    } else if (any instanceof Double) {
      return Value.newBuilder().setNumberValue((Double) any).build();
    } else if (any instanceof String) {
      return Value.newBuilder().setStringValue((String) any).build();
    } else if (any instanceof Boolean) {
      return Value.newBuilder().setBoolValue((Boolean) any).build();
    } else if (any instanceof Map) {
      return Value.newBuilder().setStructValue(toStruct((Map<String, ?>) any)).build();
    } else if (any instanceof List) {
      return Value.newBuilder().setListValue(toListValue((List<?>) any)).build();
    } else {
      throw new IllegalArgumentException("unexpected struct value: " + any);
    }
  }
}
