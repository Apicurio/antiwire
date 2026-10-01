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

import com.squareup.wire.schema.internal.parser.OneOfElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OneOf {
  final String name;
  final String documentation;
  final List<Field> fields;
  final Location location;
  final Options options;

  OneOf(String name, String documentation, List<Field> fields, Location location,
      Options options) {
    this.name = name;
    this.documentation = documentation;
    this.fields = fields;
    this.location = location;
    this.options = options;
  }

  public String name() {
    return name;
  }

  public String documentation() {
    return documentation;
  }

  public List<Field> fields() {
    return fields;
  }

  public Location location() {
    return location;
  }

  public Options options() {
    return options;
  }

  void link(Linker linker) {
    for (Field field : fields) {
      field.link(linker);
    }
  }

  void linkOptions(Linker linker, SyntaxRules syntaxRules, boolean validate) {
    for (Field field : fields) {
      field.linkOptions(linker, syntaxRules, validate);
    }
    options.link(linker, location, validate);
  }

  OneOf retainAll(Schema schema, MarkSet markSet, ProtoType enclosingType) {
    List<Field> retainedFields = Field.retainAll(schema, markSet, enclosingType, fields);
    if (retainedFields.isEmpty()) return null;
    return new OneOf(name, documentation, retainedFields, location,
        options.retainAll(schema, markSet));
  }

  OneOf retainLinked() {
    List<Field> retainedFields = Field.retainLinked(fields);
    if (retainedFields.isEmpty()) return null;
    return new OneOf(name, documentation, retainedFields, location, options.retainLinked());
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof OneOf)) return false;
    OneOf that = (OneOf) other;
    return name.equals(that.name)
        && documentation.equals(that.documentation)
        && fields.equals(that.fields)
        && location.equals(that.location)
        && Objects.equals(options, that.options);
  }

  @Override public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + documentation.hashCode();
    result = 31 * result + fields.hashCode();
    result = 31 * result + location.hashCode();
    result = 31 * result + Objects.hashCode(options);
    return result;
  }

  @Override public String toString() {
    return "OneOf(name=" + name + ", documentation=" + documentation + ", fields=" + fields
        + ", location=" + location + ", options=" + options + ")";
  }

  public static List<OneOf> fromElements(List<String> namespaces,
      List<OneOfElement> elements) {
    List<OneOf> result = new ArrayList<>();
    for (OneOfElement element : elements) {
      if (!element.groups.isEmpty()) {
        throw new IllegalStateException(element.groups.get(0).location + ": 'group' is not supported");
      }
      result.add(new OneOf(element.name, element.documentation,
          Field.fromElements(namespaces, element.fields, false, true), element.location,
          new Options(Options.ONEOF_OPTIONS, element.options)));
    }
    return result;
  }

  public static List<OneOfElement> toElements(List<OneOf> oneOfs) {
    List<OneOfElement> result = new ArrayList<>();
    for (OneOf oneOf : oneOfs) {
      result.add(new OneOfElement(oneOf.name, oneOf.documentation,
          Field.toElements(oneOf.fields), new ArrayList<>(), oneOf.options.elements(),
          oneOf.location));
    }
    return result;
  }
}
