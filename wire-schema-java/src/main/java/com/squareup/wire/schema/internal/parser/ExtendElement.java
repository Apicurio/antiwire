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

public final class ExtendElement {
  public final Location location;
  public final String name;
  public final String documentation;
  public final List<FieldElement> fields;

  public ExtendElement(Location location, String name, String documentation,
      List<FieldElement> fields) {
    this.location = location;
    this.name = name;
    this.documentation = documentation;
    this.fields = fields;
  }

  public String toSchema() {
    StringBuilder builder = new StringBuilder();
    SchemaUtil.appendDocumentation(builder, documentation);
    builder.append("extend ").append(name).append(" {");

    if (!fields.isEmpty()) {
      builder.append('\n');
      for (FieldElement field : fields) {
        SchemaUtil.appendIndented(builder, field.toSchema());
      }
    }

    builder.append("}\n");
    return builder.toString();
  }
}
