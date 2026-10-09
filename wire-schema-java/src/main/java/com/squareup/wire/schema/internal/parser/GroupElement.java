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

import com.squareup.wire.schema.Field.Label;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.SchemaUtil;
import java.util.List;

public final class GroupElement {
  private final Label label;

  public Label getLabel() {
    return label;
  }

  private final Location location;

  public Location getLocation() {
    return location;
  }

  private final String name;

  public String getName() {
    return name;
  }

  private final int tag;

  public int getTag() {
    return tag;
  }

  private final String documentation;

  public String getDocumentation() {
    return documentation;
  }

  private final List<FieldElement> fields;

  public List<FieldElement> getFields() {
    return fields;
  }

  public GroupElement(Label label, Location location, String name, int tag,
      String documentation, List<FieldElement> fields) {
    this.label = label;
    this.location = location;
    this.name = name;
    this.tag = tag;
    this.documentation = documentation;
    this.fields = fields;
  }

  public String toSchema() {
    StringBuilder builder = new StringBuilder();
    SchemaUtil.appendDocumentation(builder, documentation);
    if (label != null) {
      builder.append(SchemaUtil.toEnglishLowerCase(label.name())).append(' ');
    }
    builder.append("group ").append(name).append(" = ").append(tag).append(" {");
    if (!fields.isEmpty()) {
      builder.append('\n');
      for (FieldElement field : fields) {
        SchemaUtil.appendIndented(builder, field.toSchema());
      }
    }
    builder.append("}\n");
    return builder.toString();
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof GroupElement)) return false;
    GroupElement that = (GroupElement) other;
    return true
      && java.util.Objects.equals(this.label, that.label)
      && java.util.Objects.equals(this.location, that.location)
      && java.util.Objects.equals(this.name, that.name)
      && this.tag == that.tag
      && java.util.Objects.equals(this.documentation, that.documentation)
      && java.util.Objects.equals(this.fields, that.fields);
  }

  @Override public int hashCode() {
    int result = java.util.Objects.hashCode(label);
    result = 31 * result + java.util.Objects.hashCode(location);result = 31 * result + java.util.Objects.hashCode(name);result = 31 * result + tag;result = 31 * result + java.util.Objects.hashCode(documentation);result = 31 * result + java.util.Objects.hashCode(fields);    return result;
  }
}
