/*
 * Copyright (C) 2022 Square, Inc.
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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import okio.BufferedSource;
import okio.ByteString;
import okio.Options;

/** Port of upstream jvmMain fileSystems.kt. */
public final class FileSystems {
  private static final Options UNICODE_BOMS = Options.of(
      ByteString.decodeHex("efbbbf"), // UTF-8
      ByteString.decodeHex("feff"), // UTF-16BE
      ByteString.decodeHex("fffe0000"), // UTF-32LE
      ByteString.decodeHex("fffe"), // UTF-16LE
      ByteString.decodeHex("0000feff") // UTF-32BE
  );

  public static Charset readBomAsCharset(BufferedSource source) throws java.io.IOException {
    return readBomAsCharset(source, StandardCharsets.UTF_8);
  }

  public static Charset readBomAsCharset(BufferedSource source, Charset defaultCharset)
      throws java.io.IOException {
    switch (source.select(UNICODE_BOMS)) {
      case 0: return StandardCharsets.UTF_8;
      case 1: return StandardCharsets.UTF_16BE;
      case 2: return Charset.forName("UTF-32LE");
      case 3: return StandardCharsets.UTF_16LE;
      case 4: return Charset.forName("UTF-32BE");
      case -1: return defaultCharset;
      default: throw new AssertionError();
    }
  }

  private FileSystems() {
    throw new AssertionError("no instances");
  }
}
