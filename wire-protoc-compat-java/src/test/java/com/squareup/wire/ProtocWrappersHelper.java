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

import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;

/**
 * Translation of upstream wire-protoc-compatibility-tests
 * ProtocWrappersHelper.kt: Kotlin receiver extensions become static helpers. The receivers map
 * to primitives plus okio.ByteString for the bytes wrapper; see this module's
 * UPSTREAM-TEST-ADAPTATIONS.md.
 */
final class ProtocWrappersHelper {
  private ProtocWrappersHelper() {
  }

  static DoubleValue toDoubleValue(double value) {
    return DoubleValue.newBuilder().setValue(value).build();
  }

  static FloatValue toFloatValue(float value) {
    return FloatValue.newBuilder().setValue(value).build();
  }

  static Int64Value toInt64Value(long value) {
    return Int64Value.newBuilder().setValue(value).build();
  }

  static UInt64Value toUInt64Value(long value) {
    return UInt64Value.newBuilder().setValue(value).build();
  }

  static Int32Value toInt32Value(int value) {
    return Int32Value.newBuilder().setValue(value).build();
  }

  static UInt32Value toUInt32Value(int value) {
    return UInt32Value.newBuilder().setValue(value).build();
  }

  static BoolValue toBoolValue(boolean value) {
    return BoolValue.newBuilder().setValue(value).build();
  }

  static StringValue toStringValue(String value) {
    return StringValue.newBuilder().setValue(value).build();
  }

  static BytesValue toBytesValue(okio.ByteString value) {
    return BytesValue.newBuilder()
        .setValue(com.google.protobuf.ByteString.copyFrom(value.toByteArray()))
        .build();
  }
}
