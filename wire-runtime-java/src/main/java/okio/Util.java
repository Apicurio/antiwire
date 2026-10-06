/*
 * Translated from okio/src/commonMain/kotlin/okio/Util.kt at square/okio tag parent-3.18.2,
 * commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under the notices below.
 * Only the helpers the retained subset needs are translated; the hashing and rotating
 * helpers of the upstream file are out of the M0 subset (no wire main or wire test use at
 * the pin, docs/decisions.md OPEN-1).
 *
 * Copyright (C) 2018 Square, Inc.
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

final class Util {

  private Util() {
  }

  static void checkOffsetAndCount(long size, long offset, long byteCount) {
    if ((offset | byteCount) < 0 || offset > size || size - offset < byteCount) {
      throw new ArrayIndexOutOfBoundsException(
          "size=" + size + " offset=" + offset + " byteCount=" + byteCount);
    }
  }

  static boolean arrayRangeEquals(
      byte[] a, int aOffset, byte[] b, int bOffset, int byteCount) {
    for (int i = 0; i < byteCount; i++) {
      if (a[i + aOffset] != b[i + bOffset]) {
        return false;
      }
    }
    return true;
  }
}
