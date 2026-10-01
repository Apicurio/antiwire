/*
 * Copyright (c) 2016, the R8 project authors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of Google Inc. nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
// Methods from https://r8.googlesource.com/r8/+/master/src/test/java/com/android/tools/r8/ir/desugar/backports/MathMethods.java https://r8.googlesource.com/r8/+/master/src/test/java/com/android/tools/r8/ir/desugar/backports/MathMethods.java
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
