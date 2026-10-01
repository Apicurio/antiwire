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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MessageElement implements TypeElement {
  public final Location location;
  public final String name;
  public final String documentation;
  public final List<TypeElement> nestedTypes;
  public final List<OptionElement> options;
  public final List<ReservedElement> reserveds;
  public final List<FieldElement> fields;
  public final List<OneOfElement> oneOfs;
  public final List<ExtensionsElement> extensions;
  public final List<GroupElement> groups;
  public final List<ExtendElement> extendDeclarations;

  public MessageElement(Location location, String name, String documentation,
      List<TypeElement> nestedTypes, List<OptionElement> options,
      List<ReservedElement> reserveds, List<FieldElement> fields, List<OneOfElement> oneOfs,
      List<ExtensionsElement> extensions, List<GroupElement> groups,
      List<ExtendElement> extendDeclarations) {
    this.location = location;
    this.name = name;
    this.documentation = documentation;
    this.nestedTypes = nestedTypes;
    this.options = options;
    this.reserveds = reserveds;
    this.fields = fields;
    this.oneOfs = oneOfs;
    this.extensions = extensions;
    this.groups = groups;
    this.extendDeclarations = extendDeclarations;
  }

  @Override public Location location() {
    return location;
  }

  @Override public String name() {
    return name;
  }

  @Override public String documentation() {
    return documentation;
  }

  @Override public List<OptionElement> options() {
    return options;
  }

  @Override public List<TypeElement> nestedTypes() {
    return nestedTypes;
  }

  @Override public String toSchema() {
    StringBuilder builder = new StringBuilder();
    SchemaUtil.appendDocumentation(builder, documentation);
    builder.append("message ").append(name).append(" {");

    if (!reserveds.isEmpty()) {
      builder.append('\n');
      for (ReservedElement reserved : reserveds) {
        SchemaUtil.appendIndented(builder, reserved.toSchema());
      }
    }
    if (!options.isEmpty()) {
      builder.append('\n');
      for (OptionElement option : options) {
        SchemaUtil.appendIndented(builder, option.toSchemaDeclaration());
      }
    }

    List<FieldElement> allFieldsSorted = new ArrayList<>(fields);
    for (OneOfElement oneOf : oneOfs) {
      allFieldsSorted.addAll(oneOf.fields);
    }
    allFieldsSorted.sort(Comparator.comparingInt(FieldElement::getLine)
        .thenComparingInt(FieldElement::getColumn));

    Set<OneOfElement> addedOneOfs = new HashSet<>();

    if (!allFieldsSorted.isEmpty()) {
      for (FieldElement field : allFieldsSorted) {
        OneOfElement oneOf = getOneOfForField(field);
        if (oneOf != null && addedOneOfs.contains(oneOf)) {
          continue;
        }
        builder.append('\n');
        if (oneOf != null) {
          addedOneOfs.add(oneOf);
          SchemaUtil.appendIndented(builder, oneOf.toSchema());
        } else {
          SchemaUtil.appendIndented(builder, field.toSchema());
        }
      }
    }

    if (!groups.isEmpty()) {
      for (GroupElement group : groups) {
        builder.append('\n');
        SchemaUtil.appendIndented(builder, group.toSchema());
      }
    }
    if (!extendDeclarations.isEmpty()) {
      for (ExtendElement extendDeclaration : extendDeclarations) {
        builder.append('\n');
        builder.append(extendDeclaration.toSchema());
      }
    }
    if (!extensions.isEmpty()) {
      builder.append('\n');
      for (ExtensionsElement extension : extensions) {
        SchemaUtil.appendIndented(builder, extension.toSchema());
      }
    }
    if (!nestedTypes.isEmpty()) {
      for (TypeElement type : nestedTypes) {
        builder.append('\n');
        SchemaUtil.appendIndented(builder, type.toSchema());
      }
    }
    builder.append("}\n");
    return builder.toString();
  }

  private OneOfElement getOneOfForField(FieldElement field) {
    for (OneOfElement oneOf : oneOfs) {
      if (oneOf.fields.contains(field)) {
        return oneOf;
      }
    }
    return null;
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof MessageElement)) return false;
    MessageElement that = (MessageElement) other;
    return true
      && java.util.Objects.equals(this.location, that.location)
      && java.util.Objects.equals(this.name, that.name)
      && java.util.Objects.equals(this.documentation, that.documentation)
      && java.util.Objects.equals(this.nestedTypes, that.nestedTypes)
      && java.util.Objects.equals(this.options, that.options)
      && java.util.Objects.equals(this.reserveds, that.reserveds)
      && java.util.Objects.equals(this.fields, that.fields)
      && java.util.Objects.equals(this.oneOfs, that.oneOfs)
      && java.util.Objects.equals(this.extensions, that.extensions)
      && java.util.Objects.equals(this.groups, that.groups)
      && java.util.Objects.equals(this.extendDeclarations, that.extendDeclarations);
  }

  @Override public int hashCode() {
    int result = java.util.Objects.hashCode(location);
    result = 31 * result + java.util.Objects.hashCode(name);result = 31 * result + java.util.Objects.hashCode(documentation);result = 31 * result + java.util.Objects.hashCode(nestedTypes);result = 31 * result + java.util.Objects.hashCode(options);result = 31 * result + java.util.Objects.hashCode(reserveds);result = 31 * result + java.util.Objects.hashCode(fields);result = 31 * result + java.util.Objects.hashCode(oneOfs);result = 31 * result + java.util.Objects.hashCode(extensions);result = 31 * result + java.util.Objects.hashCode(groups);result = 31 * result + java.util.Objects.hashCode(extendDeclarations);    return result;
  }
}
