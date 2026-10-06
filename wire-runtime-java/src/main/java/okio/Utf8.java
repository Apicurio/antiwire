/*
 * Translated from okio/src/commonMain/kotlin/okio/Utf8.kt (utf8Size) and the JVM actuals of
 * okio/src/commonMain/kotlin/okio/CommonPlatform.kt (toUtf8String, asUtf8ToByteArray, which
 * delegate to java.lang.String UTF-8 on the JVM) at square/okio tag parent-3.18.2,
 * commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under the notices below.
 * The byte-level UTF-8 decoder generator of the upstream file is out of the M0 subset: on the
 * JVM upstream itself uses String(ByteArray, UTF_8) for reads and getBytes(UTF_8) for writes
 * (okio/src/jvmMain/kotlin/okio/-JvmPlatform.kt lines 24-26), so the JDK does the work here
 * exactly as it does in the pinned artifact. This file is also the single home for the internal
 * Utf8.kt declarations REPLACEMENT_CODE_POINT and isIsoControl (Utf8.kt lines 118-122),
 * re-homed here from Buffer.java and ByteString.java to mirror the upstream placement.
 *
 * Copyright (C) 2017 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio;

public final class Utf8 {

  private Utf8() {
  }

  static String string(byte[] bytes) {
    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
  }

  static byte[] bytes(String s) {
    return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  static final int REPLACEMENT_CODE_POINT = 0xfffd;

  static boolean isIsoControl(int codePoint) {
    return (codePoint >= 0x00 && codePoint <= 0x1f) || (codePoint >= 0x7f && codePoint <= 0x9f);
  }

  /**
   * Returns the number of bytes used to encode the slice of {@code string} as UTF-8 when using
   * the form of UTF-8 that represents a lone surrogate with the replacement character, matching
   * upstream {@code String.utf8Size()}.
   */
  public static long utf8Size(String string) {
    return utf8Size(string, 0, string.length());
  }

  public static long utf8Size(String string, int beginIndex, int endIndex) {
    if (beginIndex < 0) {
      throw new IllegalArgumentException("beginIndex < 0: " + beginIndex);
    }
    if (endIndex < beginIndex) {
      throw new IllegalArgumentException(
          "endIndex < beginIndex: " + endIndex + " < " + beginIndex);
    }
    if (endIndex > string.length()) {
      throw new IllegalArgumentException(
          "endIndex > string.length: " + endIndex + " > " + string.length());
    }

    long result = 0L;
    int i = beginIndex;
    while (i < endIndex) {
      int c = string.charAt(i);

      if (c < 0x80) {
        // A 7-bit character with 1 byte.
        result++;
        i++;
      } else if (c < 0x800) {
        // An 11-bit character with 2 bytes.
        result += 2;
        i++;
      } else if (c < 0xd800 || c > 0xdfff) {
        // A 16-bit character with 3 bytes.
        result += 3;
        i++;
      } else {
        int low = i + 1 < endIndex ? string.charAt(i + 1) : 0;
        if (c > 0xdbff || low < 0xdc00 || low > 0xdfff) {
          // A malformed surrogate, which yields '?'.
          result++;
          i++;
        } else {
          // A 21-bit character with 4 bytes.
          result += 4;
          i += 2;
        }
      }
    }

    return result;
  }
}
