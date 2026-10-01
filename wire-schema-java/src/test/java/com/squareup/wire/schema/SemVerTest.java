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
package com.squareup.wire.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Ordering semantics for the since/until/only pruning versions. The empty-segment case pins the
 * code-review fix: Kotlin's {@code all(Char::isDigit)} is vacuously true for an empty segment,
 * so an empty segment compares as numeric zero, not as a non-numeric string.
 */
public class SemVerTest {
  @Test public void emptySegmentComparesAsNumericZero() {
    // Prerelease segments of a are ["", "5"]; "" vs "1" must compare -1, not +1.
    assertTrue(SemVer.toLowerCaseSemVer("1.0.0-.5")
        .compareTo(SemVer.toLowerCaseSemVer("1.0.0-1")) < 0);
    // Consecutive dots in the release section behave the same way.
    assertTrue(SemVer.toLowerCaseSemVer("1..0")
        .compareTo(SemVer.toLowerCaseSemVer("1.1.0")) < 0);
  }

  @Test public void basicOrdering() {
    assertTrue(SemVer.toLowerCaseSemVer("1.0").compareTo(SemVer.toLowerCaseSemVer("1.0.1")) < 0);
    assertTrue(SemVer.toLowerCaseSemVer("1.0.0-alpha")
        .compareTo(SemVer.toLowerCaseSemVer("1.0.0")) < 0);
    // Numeric, not lexicographic: 10 > 9.
    assertTrue(SemVer.toLowerCaseSemVer("1.10.0")
        .compareTo(SemVer.toLowerCaseSemVer("1.9.0")) > 0);
    assertTrue(SemVer.toLowerCaseSemVer("1.0.0-alpha.1")
        .compareTo(SemVer.toLowerCaseSemVer("1.0.0-alpha.2")) < 0);
    assertEquals(0, SemVer.toLowerCaseSemVer("1.0.0")
        .compareTo(SemVer.toLowerCaseSemVer("1.0.0+build.5")));
  }
}
