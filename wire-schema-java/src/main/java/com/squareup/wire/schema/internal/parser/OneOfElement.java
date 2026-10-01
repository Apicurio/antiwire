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

public final class OneOfElement {
  public final String name;
  public final String documentation;
  public final List<FieldElement> fields;
  public final List<GroupElement> groups;
  public final List<OptionElement> options;
  public final Location location;

  public OneOfElement(String name, String documentation, List<FieldElement> fields,
      List<GroupElement> groups, List<OptionElement> options, Location location) {
    this.name = name;
    this.documentation = documentation;
    this.fields = fields;
    this.groups = groups;
    this.options = options;
    this.location = location;
  }

  public String toSchema() {
    StringBuilder builder = new StringBuilder();
    SchemaUtil.appendDocumentation(builder, documentation);
    builder.append("oneof ").append(name).append(" {");

    if (!options.isEmpty()) {
      builder.append('\n');
      for (OptionElement option : options) {
        SchemaUtil.appendIndented(builder, option.toSchemaDeclaration());
      }
    }
    if (!fields.isEmpty()) {
      builder.append('\n');
      for (FieldElement field : fields) {
        SchemaUtil.appendIndented(builder, field.toSchema());
      }
    }
    if (!groups.isEmpty()) {
      builder.append('\n');
      for (GroupElement group : groups) {
        SchemaUtil.appendIndented(builder, group.toSchema());
      }
    }
    builder.append("}\n");
    return builder.toString();
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof OneOfElement)) return false;
    OneOfElement that = (OneOfElement) other;
    return true
      && java.util.Objects.equals(this.name, that.name)
      && java.util.Objects.equals(this.documentation, that.documentation)
      && java.util.Objects.equals(this.fields, that.fields)
      && java.util.Objects.equals(this.groups, that.groups)
      && java.util.Objects.equals(this.options, that.options)
      && java.util.Objects.equals(this.location, that.location);
  }

  @Override public int hashCode() {
    int result = java.util.Objects.hashCode(name);
    result = 31 * result + java.util.Objects.hashCode(documentation);result = 31 * result + java.util.Objects.hashCode(fields);result = 31 * result + java.util.Objects.hashCode(groups);result = 31 * result + java.util.Objects.hashCode(options);result = 31 * result + java.util.Objects.hashCode(location);    return result;
  }
}
