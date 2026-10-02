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
package com.squareup.wire.schema.internal;

import com.squareup.wire.schema.internal.parser.OptionElement;
import java.util.List;

/** The schema-formatting helpers upstream declares on the Internal facade. */
public final class SchemaUtil {
  private SchemaUtil() {
  }

  public static final int MIN_TAG_VALUE = 1;
  public static final int MAX_TAG_VALUE = (1 << 29) - 1; // 536,870,911

  private static final int RESERVED_TAG_VALUE_START = 19000;
  private static final int RESERVED_TAG_VALUE_END = 19999;

  /** True if the supplied value is in the valid tag range and not reserved. */
  public static boolean isValidTag(int value) {
    return (value >= MIN_TAG_VALUE && value < RESERVED_TAG_VALUE_START)
        || (value > RESERVED_TAG_VALUE_END && value <= MAX_TAG_VALUE);
  }

  public static void appendDocumentation(StringBuilder builder, String documentation) {
    if (documentation.isEmpty()) {
      return;
    }
    String[] lines = documentation.split("\n", -1);
    int count = lines.length;
    if (count > 1 && lines[count - 1].isEmpty()) {
      count--;
    }
    for (int i = 0; i < count; i++) {
      builder.append("// ").append(lines[i]).append('\n');
    }
  }

  public static void appendOptions(StringBuilder builder, List<OptionElement> options) {
    int count = options.size();
    if (count == 1) {
      builder.append('[').append(options.get(0).toSchema()).append(']');
      return;
    }
    builder.append("[\n");
    for (int i = 0; i < count; i++) {
      String endl = i < count - 1 ? "," : "";
      appendIndented(builder, options.get(i).toSchema() + endl);
    }
    builder.append(']');
  }

  public static void appendIndented(StringBuilder builder, String value) {
    String[] lines = value.split("\n", -1);
    int count = lines.length;
    if (count > 1 && lines[count - 1].isEmpty()) {
      count--;
    }
    for (int i = 0; i < count; i++) {
      builder.append("  ").append(lines[i]).append('\n');
    }
  }

  /** Upstream's UtilJVM actual: lowercase(Locale.US), which is Unicode-aware. */
  public static String toEnglishLowerCase(String value) {
    return value.toLowerCase(java.util.Locale.US);
  }

  /** Values mix Integers and int[] ranges; compare ranges structurally like IntRange. */
  public static boolean valuesEqual(java.util.List<Object> a, java.util.List<Object> b) {
    if (a.size() != b.size()) return false;
    java.util.Iterator<Object> bi = b.iterator();
    for (Object value : a) {
      Object other = bi.next();
      if (value instanceof int[]) {
        if (!(other instanceof int[]) || !java.util.Arrays.equals((int[]) value, (int[]) other)) {
          return false;
        }
      } else if (!value.equals(other)) {
        return false;
      }
    }
    return true;
  }

  /** Values mix Integers and int[] ranges; hash ranges structurally like IntRange. */
  public static int valuesHashCode(java.util.List<Object> values) {
    int result = 1;
    for (Object value : values) {
      result = 31 * result
          + (value instanceof int[] ? java.util.Arrays.hashCode((int[]) value) : value.hashCode());
    }
    return result;
  }
}
