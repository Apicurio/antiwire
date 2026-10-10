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
package com.squareup.wire.internal;

/** Java name of upstream's {@code MathMethods.kt} file facade (TASK-33.2). */
public final class MathMethodsKt {
  public static final long NANOS_PER_SECOND = MathMethods.NANOS_PER_SECOND;

  private MathMethodsKt() {
  }

  public static long addExactLong(long x, long y) {
    return MathMethods.addExactLong(x, y);
  }

  public static long floorDivLong(long dividend, long divisor) {
    return MathMethods.floorDivLong(dividend, divisor);
  }

  public static long floorModLong(long dividend, long divisor) {
    return MathMethods.floorModLong(dividend, divisor);
  }
}
