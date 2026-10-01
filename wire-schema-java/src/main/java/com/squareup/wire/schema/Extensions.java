/*
 * Copyright (C) 2015 Square, Inc.
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
import com.squareup.wire.schema.internal.parser.ExtensionsElement;
import java.util.ArrayList;
import java.util.List;

/** An extension range declaration, like {@code extensions 101 to max;}. */
public final class Extensions {
  final Location location;
  final String documentation;
  final List<Object> values;

  Extensions(Location location, String documentation, List<Object> values) {
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

  /** Integers are tags; int[] pairs are inclusive ranges. */
  public List<Object> values() {
    return values;
  }

  void validate(Linker linker) {
    Linker scoped = linker.withContext(this);
    List<String> outOfRangeTags = new ArrayList<>();
    for (Object value : values) {
      if (value instanceof Integer) {
        if (!SchemaUtil.isValidTag((Integer) value)) {
          outOfRangeTags.add(String.valueOf(value));
        }
      } else if (value instanceof int[]) {
        int[] range = (int[]) value;
        if (!SchemaUtil.isValidTag(range[0]) || !SchemaUtil.isValidTag(range[1])) {
          outOfRangeTags.add(range[0] + " to " + range[1]);
        }
      } else {
        throw new AssertionError();
      }
    }
    if (!outOfRangeTags.isEmpty()) {
      scoped.errors.add("tags are out of range: " + String.join(", ", outOfRangeTags));
    }
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof Extensions)) return false;
    Extensions that = (Extensions) other;
    return location.equals(that.location)
        && documentation.equals(that.documentation)
        && Reserved.valuesEqual(values, that.values);
  }

  @Override public int hashCode() {
    int result = location.hashCode();
    result = 31 * result + documentation.hashCode();
    result = 31 * result + Reserved.valuesHashCode(values);
    return result;
  }

  @Override public String toString() {
    return "Extensions(location=" + location + ", documentation=" + documentation
        + ", values=" + values + ")";
  }

  public static Extensions fromElement(ExtensionsElement element) {
    return new Extensions(element.location, element.documentation, element.values);
  }

  public static ExtensionsElement toElement(Extensions extensions) {
    // Upstream ExtensionsElement defaults options to empty; the schema model drops them.
    return new ExtensionsElement(extensions.location, extensions.documentation,
        extensions.values, java.util.Collections.emptyList());
  }

  public static List<Extensions> fromElements(List<ExtensionsElement> elements) {
    List<Extensions> result = new ArrayList<>();
    for (ExtensionsElement element : elements) {
      result.add(fromElement(element));
    }
    return result;
  }

  public static List<ExtensionsElement> toElements(List<Extensions> extensionsList) {
    List<ExtensionsElement> result = new ArrayList<>();
    for (Extensions extensions : extensionsList) {
      result.add(toElement(extensions));
    }
    return result;
  }
}
