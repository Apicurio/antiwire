/*
 * Copyright (C) 2019 Square, Inc.
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

import com.squareup.wire.schema.internal.SchemaUtil;
import java.util.Objects;

/**
 * A version string as specified by semver.org. This is used to order versions for sorting since
 * and until.
 *
 * <p>If there is a {@code +} in the version string it delimits the build metadata. The plus
 * character and the section that follows are unused for version comparisons.
 *
 * <p>The rest of the string is split into two sections:
 *
 * <ul>
 *   <li>Release (preceding the first dash). Typically like "1.0.0".
 *   <li>Pre-release (optional, following the first dash). Typically like "alpha", "snapshot",
 *       or "rc1".
 * </ul>
 *
 * <p>Each section has one or more segments separated by dots and dashes. Within a segment
 * comparisons are either numeric (if both segments are strictly digits), lexicographic (if both
 * contain non-digit characters) or numbers-first (if only one segment is strictly digits).
 *
 * <p>This class requires lowercase versions to defend against potentially surprising behavior
 * in the semver.org spec ("1.0-alpha" comes after "1.0-BETA" due to case-sensitive sorting).
 */
final class SemVer implements Comparable<SemVer> {
  /** Characters that signal the end of the release section. */
  private static final char[] RELEASE_TERMINATORS = { '+', '-' };

  /** Characters that signal the end of the pre-release section. */
  private static final char[] PRERELEASE_TERMINATORS = { '+' };

  /** Characters that separate segments within a section. */
  private static final char[] SEPARATORS = { '.', '-' };

  final String version;

  private SemVer(String version) {
    if (!SchemaUtil.toEnglishLowerCase(version).equals(version)) {
      throw new IllegalArgumentException("version must be lowercase: " + version);
    }
    this.version = version;
  }

  @Override public String toString() {
    return version;
  }

  @Override public int compareTo(SemVer other) {
    String a = this.version;
    String b = other.version;
    int aPos = 0;
    int bPos = 0;

    char[][] terminatorSets = { RELEASE_TERMINATORS, PRERELEASE_TERMINATORS };
    for (char[] terminators : terminatorSets) {
      int aSize = find(a, terminators, aPos, a.length());
      int bSize = find(b, terminators, bPos, b.length());

      // A version that lacks a pre-release section comes after a version that has one.
      if (terminators == PRERELEASE_TERMINATORS) {
        boolean aNoPrerelease = aPos == aSize;
        boolean bNoPrerelease = bPos == bSize;
        if (aNoPrerelease && !bNoPrerelease) return 1;
        if (bNoPrerelease && !aNoPrerelease) return -1;
      }

      while (aPos < aSize && bPos < bSize) {
        int aLimit = find(a, SEPARATORS, aPos, aSize);
        int bLimit = find(b, SEPARATORS, bPos, bSize);

        int result = compareSegment(
            a.substring(aPos, aLimit),
            b.substring(bPos, bLimit));

        if (result != 0) return result;

        // The next position starts just past the delimiter.
        aPos = aLimit < aSize ? aLimit + 1 : aSize;
        bPos = bLimit < bSize ? bLimit + 1 : bSize;
      }

      // If there are more segments in this section, it's later.
      if (aPos < aSize) return 1;
      if (bPos < bSize) return -1;
    }

    return 0;
  }

  private int compareSegment(String a, String b) {
    // Note: an empty segment is vacuously all digits, like Kotlin's all(Char::isDigit).
    boolean aAllDigits = allDigits(a);
    boolean bAllDigits = allDigits(b);
    if (aAllDigits && bAllDigits) return compareNumber(a, b);
    if (aAllDigits) return -1;
    if (bAllDigits) return 1;
    return a.compareTo(b);
  }

  private static boolean allDigits(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (!Character.isDigit(value.charAt(i))) return false;
    }
    return true;
  }

  /** Compare two strings as non-negative integral values without decoding. */
  private int compareNumber(String a, String b) {
    int aStart = indexOfNonZero(a);
    int bStart = indexOfNonZero(b);
    if (aStart == -1 && bStart == -1) return 0; // a and b are 0
    if (aStart == -1) return -1; // a is 0, b is not
    if (bStart == -1) return 1; // b is 0, a is not
    int aLength = a.length() - aStart;
    int bLength = b.length() - bStart;
    if (aLength < bLength) return -1; // a is shorter than b, thus smaller
    if (aLength > bLength) return 1; // b is shorter than a, thus smaller
    return a.substring(aStart).compareTo(b.substring(bStart)); // compare lexicographically
  }

  private static int indexOfNonZero(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) != '0') return i;
    }
    return -1;
  }

  /**
   * Returns the index of a char in {@code chars} in {@code [startIndex..endIndex)}. Returns
   * {@code endIndex} if the value is not found.
   */
  private static int find(String string, char[] chars, int startIndex, int endIndex) {
    for (int i = Math.max(startIndex, 0); i < endIndex && i < string.length(); i++) {
      for (char c : chars) {
        if (string.charAt(i) == c) return i;
      }
    }
    return endIndex;
  }

  static SemVer toLowerCaseSemVer(String version) {
    return new SemVer(SchemaUtil.toEnglishLowerCase(version));
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof SemVer)) return false;
    return version.equals(((SemVer) other).version);
  }

  @Override public int hashCode() {
    return Objects.hashCode(version);
  }
}
