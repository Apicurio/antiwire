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
import java.util.Collections;
import java.util.List;

public final class EnumElement implements TypeElement {
  private final Location location;
  private final String name;
  private final String documentation;
  private final List<OptionElement> options;
  private final List<EnumConstantElement> constants;

  public List<EnumConstantElement> getConstants() {
    return constants;
  }

  private final List<ReservedElement> reserveds;

  public List<ReservedElement> getReserveds() {
    return reserveds;
  }

  public EnumElement(Location location, String name, String documentation,
      List<OptionElement> options, List<EnumConstantElement> constants,
      List<ReservedElement> reserveds) {
    this.location = location;
    this.name = name;
    this.documentation = documentation;
    this.options = options;
    this.constants = constants;
    this.reserveds = reserveds;
  }

  // Enums do not allow nested type declarations.
  @Override public List<TypeElement> getNestedTypes() {
    return Collections.emptyList();
  }

  @Override public Location getLocation() {
    return location;
  }

  @Override public String getName() {
    return name;
  }

  @Override public String getDocumentation() {
    return documentation;
  }

  @Override public List<OptionElement> getOptions() {
    return options;
  }

  @Override public String toSchema() {
    StringBuilder builder = new StringBuilder();
    SchemaUtil.appendDocumentation(builder, documentation);
    builder.append("enum ").append(name).append(" {");

    if (!reserveds.isEmpty()) {
      builder.append('\n');
      for (ReservedElement reserved : reserveds) {
        SchemaUtil.appendIndented(builder, reserved.toSchema());
      }
    }
    if (reserveds.isEmpty() && (!options.isEmpty() || !constants.isEmpty())) {
      builder.append('\n');
    }

    if (!options.isEmpty()) {
      for (OptionElement option : options) {
        SchemaUtil.appendIndented(builder, option.toSchemaDeclaration());
      }
    }
    if (!constants.isEmpty()) {
      for (EnumConstantElement constant : constants) {
        SchemaUtil.appendIndented(builder, constant.toSchema());
      }
    }
    builder.append("}\n");
    return builder.toString();
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof EnumElement)) return false;
    EnumElement that = (EnumElement) other;
    return true
      && java.util.Objects.equals(this.location, that.location)
      && java.util.Objects.equals(this.name, that.name)
      && java.util.Objects.equals(this.documentation, that.documentation)
      && java.util.Objects.equals(this.options, that.options)
      && java.util.Objects.equals(this.constants, that.constants)
      && java.util.Objects.equals(this.reserveds, that.reserveds);
  }

  @Override public int hashCode() {
    int result = java.util.Objects.hashCode(location);
    result = 31 * result + java.util.Objects.hashCode(name);result = 31 * result + java.util.Objects.hashCode(documentation);result = 31 * result + java.util.Objects.hashCode(options);result = 31 * result + java.util.Objects.hashCode(constants);result = 31 * result + java.util.Objects.hashCode(reserveds);    return result;
  }
}
