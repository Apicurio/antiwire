/*
 * Copyright (C) 2016 Square, Inc.
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

import com.squareup.wire.schema.internal.parser.ReservedElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/** A reserved tag range or name list declared on a message or enum. */
public final class Reserved {
  final Location location;
  final String documentation;
  final List<Object> values;

  Reserved(Location location, String documentation, List<Object> values) {
    this.location = location;
    this.documentation = documentation;
    this.values = values;
  }

  public Location location() {
    return location;
  }

  public String documentation() {
    return documentation;
  }

  /** Integers are tags; int[] pairs are inclusive ranges; strings are names. */
  public List<Object> values() {
    return values;
  }

  public boolean matchesTag(int tag) {
    for (Object value : values) {
      if (value instanceof Integer && tag == (Integer) value) return true;
      if (value instanceof int[] && tag >= ((int[]) value)[0] && tag <= ((int[]) value)[1]) {
        return true;
      }
    }
    return false;
  }

  public boolean matchesName(String name) {
    for (Object value : values) {
      if (value instanceof String && name.equals(value)) return true;
    }
    return false;
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof Reserved)) return false;
    Reserved that = (Reserved) other;
    return location.equals(that.location)
        && documentation.equals(that.documentation)
        && valuesEqual(values, that.values);
  }

  @Override public int hashCode() {
    int result = location.hashCode();
    result = 31 * result + documentation.hashCode();
    result = 31 * result + valuesHashCode(values);
    return result;
  }

  /** Ranges are int[] pairs; compared structurally like upstream's IntRange via SchemaUtil. */
  static boolean valuesEqual(List<Object> a, List<Object> b) {
    return com.squareup.wire.schema.internal.SchemaUtil.valuesEqual(a, b);
  }

  /** Ranges are int[] pairs; hashed structurally like upstream's IntRange via SchemaUtil. */
  static int valuesHashCode(List<Object> values) {
    return com.squareup.wire.schema.internal.SchemaUtil.valuesHashCode(values);
  }

  @Override public String toString() {
    return "Reserved(location=" + location + ", documentation=" + documentation
        + ", values=" + values + ")";
  }

  public static Reserved fromElement(ReservedElement element) {
    return new Reserved(element.location, element.documentation, element.values);
  }

  public static ReservedElement toElement(Reserved reserved) {
    return new ReservedElement(reserved.location, reserved.documentation, reserved.values);
  }

  public static List<Reserved> fromElements(List<ReservedElement> elements) {
    List<Reserved> result = new ArrayList<>();
    for (ReservedElement element : elements) {
      result.add(fromElement(element));
    }
    return result;
  }

  public static List<ReservedElement> toElements(List<Reserved> reserveds) {
    List<ReservedElement> result = new ArrayList<>();
    for (Reserved reserved : reserveds) {
      result.add(toElement(reserved));
    }
    return result;
  }
}
