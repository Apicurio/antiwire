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
// Methods from https://r8.googlesource.com/r8/+/master/src/test/java/com/android/tools/r8/ir/desugar/backports/MathMethods.java
package com.squareup.wire.internal;

/** Upstream internal (R8-derived) math backports used by generated code. */
public final class MathMethods {
  private MathMethods() {
  }

  public static long addExactLong(long x, long y) {
    long result = x + y;
    if (((x ^ y) < 0L) || ((x ^ result) >= 0L)) {
      return result;
    }
    throw new ArithmeticException();
  }

  public static long floorDivLong(long dividend, long divisor) {
    long div = dividend / divisor;
    long rem = dividend - divisor * div;
    if (rem == 0L) {
      return div;
    }
    // Normal Java division rounds towards 0; deal with the cases where that is wrong.
    long signum = 1L | ((dividend ^ divisor) >> (Long.SIZE - 1));
    return signum < 0L ? div - 1L : div;
  }

  public static long floorModLong(long dividend, long divisor) {
    long rem = dividend % divisor;
    if (rem == 0L) {
      return 0L;
    }
    // Normal Java remainder tracks the sign of the dividend; fix mismatched signs.
    long signum = 1L | ((dividend ^ divisor) >> (Long.SIZE - 1));
    return signum > 0L ? rem : rem + divisor;
  }

  public static final long NANOS_PER_SECOND = 1000_000_000L;
}
