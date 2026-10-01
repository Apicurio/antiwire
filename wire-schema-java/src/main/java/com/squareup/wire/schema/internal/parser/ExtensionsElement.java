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
package com.squareup.wire.schema.internal.parser;

import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.SchemaUtil;
import java.util.List;

public final class ExtensionsElement {
  public final Location location;
  public final String documentation;
  /** An Integer or Integer range (int[]{first, last}) tag. */
  public final List<Object> values;
  public final List<OptionElement> options;

  public ExtensionsElement(Location location, String documentation, List<Object> values,
      List<OptionElement> options) {
    this.location = location;
    this.documentation = documentation;
    this.values = values;
    this.options = options;
  }

  public String toSchema() {
    StringBuilder builder = new StringBuilder();
    SchemaUtil.appendDocumentation(builder, documentation);
    builder.append("extensions ");

    for (int index = 0; index < values.size(); index++) {
      if (index > 0) builder.append(", ");

      Object value = values.get(index);
      if (value instanceof Integer) {
        builder.append(value);
      } else if (value instanceof int[]) {
        int[] range = (int[]) value;
        builder.append(range[0]).append(" to ");
        if (range[1] < SchemaUtil.MAX_TAG_VALUE) {
          builder.append(range[1]);
        } else {
          builder.append("max");
        }
      } else {
        throw new AssertionError();
      }
    }

    if (!options.isEmpty()) {
      builder.append(" ");
      SchemaUtil.appendOptions(builder, options);
    }

    builder.append(";\n");
    return builder.toString();
  }
}
