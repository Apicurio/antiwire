/*
 * Copyright (C) 2026 the antiwire authors
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

/**
 * Java name of upstream's {@code ProtoReader32.kt} file facade: the factory for the 32-bit reader
 * over a byte array (TASK-33.2). The {@code okio.ByteString} overload and the {@code forEachTag}
 * extension are excluded by DEC-14 and DEC-4.
 */
public final class ProtoReader32Kt {
  private ProtoReader32Kt() {
  }

  public static ProtoReader32 ProtoReader32(byte[] source, int pos, int limit) {
    return new ByteArrayProtoReader32(source, pos, limit);
  }
}
