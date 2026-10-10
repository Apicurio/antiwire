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

/**
 * Rethrows a checked exception without declaring it, which is what upstream's Kotlin does: Kotlin
 * has no checked exceptions, so a function without {@code @Throws} still throws the original
 * {@code IOException} at runtime (TASK-33.3).
 *
 * <p>Used by the internal helpers that generated and runtime code call from inside methods that
 * upstream declares with {@code @Throws(IOException::class)} (for example {@code decode}). There the
 * original exception must stay observable: wrapping it would change what a caller of the declared
 * {@code decode} catches (upstream's {@code ParseTest} pins "Wire recursion limit exceeded" as an
 * {@code IOException}). Top-level convenience methods that upstream declares without {@code
 * @Throws} wrap in {@link java.io.UncheckedIOException} instead.
 */
public final class Rethrow {
  private Rethrow() {
  }

  /** Throws {@code t} as is; the return type lets callers write {@code throw Rethrow.unchecked(e)}. */
  public static RuntimeException unchecked(Throwable t) {
    throw Rethrow.<RuntimeException>sneaky(t);
  }

  @SuppressWarnings("unchecked")
  private static <T extends Throwable> T sneaky(Throwable t) throws T {
    throw (T) t;
  }
}
