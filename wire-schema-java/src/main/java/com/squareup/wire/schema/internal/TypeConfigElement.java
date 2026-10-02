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
package com.squareup.wire.schema.internal;

import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.OptionElement;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Configures how Wire will generate code for a specific type. This configuration belongs in a
 * {@code build.wire} file that is in the same directory as the configured type.
 */
public final class TypeConfigElement {
  public final Location location;
  public final String type;
  public final String documentation;
  public final List<OptionElement> with;
  public final String target;
  public final String adapter;

  public TypeConfigElement(Location location, String type, String documentation,
      List<OptionElement> with, String target, String adapter) {
    this.location = location;
    this.type = type;
    this.documentation = documentation;
    this.with = with;
    this.target = target;
    this.adapter = adapter;
  }

  public TypeConfigElement(Location location) {
    this(location, null, "", Collections.emptyList(), null, null);
  }

  public TypeConfigElement(Location location, String type) {
    this(location, type, "", Collections.emptyList(), null, null);
  }

  public TypeConfigElement(Location location, String type, String documentation) {
    this(location, type, documentation, Collections.emptyList(), null, null);
  }

  public TypeConfigElement(Location location, String type, String documentation,
      List<OptionElement> with) {
    this(location, type, documentation, with, null, null);
  }

  public TypeConfigElement(Location location, String type, String documentation,
      List<OptionElement> with, String target) {
    this(location, type, documentation, with, target, null);
  }

  public String toSchema() {
    StringBuilder builder = new StringBuilder();
    SchemaUtil.appendDocumentation(builder, documentation);
    builder.append("type ").append(type).append(" {\n");
    for (OptionElement option : with) {
      SchemaUtil.appendIndented(builder, "with " + option.toSchema() + ";\n");
    }
    builder.append("  target ").append(target).append(" using ").append(adapter).append(";\n");
    builder.append("}\n");
    return builder.toString();
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof TypeConfigElement)) return false;
    TypeConfigElement that = (TypeConfigElement) other;
    return Objects.equals(location, that.location)
        && Objects.equals(type, that.type)
        && Objects.equals(documentation, that.documentation)
        && Objects.equals(with, that.with)
        && Objects.equals(target, that.target)
        && Objects.equals(adapter, that.adapter);
  }

  @Override public int hashCode() {
    int result = Objects.hashCode(location);
    result = 31 * result + Objects.hashCode(type);
    result = 31 * result + Objects.hashCode(documentation);
    result = 31 * result + Objects.hashCode(with);
    result = 31 * result + Objects.hashCode(target);
    result = 31 * result + Objects.hashCode(adapter);
    return result;
  }

  @Override public String toString() {
    return "TypeConfigElement(location=" + location + ", type=" + type + ", documentation="
        + documentation + ", with=" + with + ", target=" + target + ", adapter=" + adapter + ")";
  }
}
