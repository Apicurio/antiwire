/*
 * Translated from okio/src/commonMain/kotlin/okio/Base64.kt at square/okio tag parent-3.18.2,
 * commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under the notices below.
 * Package-private: upstream hides this file facade from Java with a dashed JvmName.
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package okio;

/** @author Alexander Y. Kleymenov */
final class Base64 {

  static final byte[] BASE64_MAP =
      ByteString.encodeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")
          .internalArray();

  static final byte[] BASE64_URL_SAFE_MAP =
      ByteString.encodeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_")
          .internalArray();

  private Base64() {
  }

  static byte[] decodeBase64ToArray(String s) {
    // Ignore trailing '=' padding and whitespace from the input.
    int limit = s.length();
    while (limit > 0) {
      char c = s.charAt(limit - 1);
      if (c != '=' && c != '\n' && c != '\r' && c != ' ' && c != '\t') {
        break;
      }
      limit--;
    }

    // If the input includes whitespace, this output array will be longer than necessary.
    byte[] out = new byte[(int) (limit * 6L / 8L)];
    int outCount = 0;
    int inCount = 0;

    int word = 0;
    for (int pos = 0; pos < limit; pos++) {
      char c = s.charAt(pos);

      int bits;
      if (c >= 'A' && c <= 'Z') {
        // char ASCII value
        //  A    65    0
        //  Z    90    25 (ASCII - 65)
        bits = c - 65;
      } else if (c >= 'a' && c <= 'z') {
        // char ASCII value
        //  a    97    26
        //  z    122   51 (ASCII - 71)
        bits = c - 71;
      } else if (c >= '0' && c <= '9') {
        // char ASCII value
        //  0    48    52
        //  9    57    61 (ASCII + 4)
        bits = c + 4;
      } else if (c == '+' || c == '-') {
        bits = 62;
      } else if (c == '/' || c == '_') {
        bits = 63;
      } else if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
        continue;
      } else {
        return null;
      }

      // Append this char's 6 bits to the word.
      word = (word << 6) | bits;

      // For every 4 chars of input, we accumulate 24 bits of output. Emit 3 bytes.
      inCount++;
      if (inCount % 4 == 0) {
        out[outCount++] = (byte) (word >> 16);
        out[outCount++] = (byte) (word >> 8);
        out[outCount++] = (byte) word;
      }
    }

    int lastWordChars = inCount % 4;
    switch (lastWordChars) {
      case 1:
        // We read 1 char followed by "===". But 6 bits is a truncated byte! Fail.
        return null;
      case 2:
        // We read 2 chars followed by "==". Emit 1 byte with 8 of those 12 bits.
        word <<= 12;
        out[outCount++] = (byte) (word >> 16);
        break;
      case 3:
        // We read 3 chars, followed by "=". Emit 2 bytes for 16 of those 18 bits.
        word <<= 6;
        out[outCount++] = (byte) (word >> 16);
        out[outCount++] = (byte) (word >> 8);
        break;
      default:
        break;
    }

    // If we sized our out array perfectly, we're done.
    if (outCount == out.length) {
      return out;
    }

    // Copy the decoded bytes to a new, right-sized array.
    byte[] result = new byte[outCount];
    System.arraycopy(out, 0, result, 0, outCount);
    return result;
  }

  static String encodeBase64(byte[] data, boolean includePadding, byte[] map) {
    int length = includePadding
        ? (data.length + 2) / 3 * 4
        : (data.length * 4 + 2) / 3;
    byte[] out = new byte[length];
    int index = 0;
    int end = data.length - data.length % 3;
    int i = 0;
    while (i < end) {
      int b0 = data[i++];
      int b1 = data[i++];
      int b2 = data[i++];
      out[index++] = map[(b0 & 0xff) >> 2];
      out[index++] = map[((b0 & 0x03) << 4) | ((b1 & 0xff) >> 4)];
      out[index++] = map[((b1 & 0x0f) << 2) | ((b2 & 0xff) >> 6)];
      out[index++] = map[(b2 & 0x3f)];
    }
    switch (data.length - end) {
      case 1:
        int b0 = data[i];
        out[index++] = map[(b0 & 0xff) >> 2];
        out[index++] = map[(b0 & 0x03) << 4];
        if (includePadding) {
          out[index++] = '=';
          out[index] = '=';
        }
        break;
      case 2:
        int c0 = data[i++];
        int c1 = data[i];
        out[index++] = map[(c0 & 0xff) >> 2];
        out[index++] = map[((c0 & 0x03) << 4) | ((c1 & 0xff) >> 4)];
        out[index++] = map[(c1 & 0x0f) << 2];
        if (includePadding) {
          out[index] = '=';
        }
        break;
      default:
        break;
    }
    return Utf8.string(out);
  }
}
