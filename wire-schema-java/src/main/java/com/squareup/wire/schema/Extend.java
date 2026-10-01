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

import com.squareup.wire.schema.internal.parser.ExtendElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Extend {
  final Location location;
  final String documentation;
  final String name;
  final List<Field> fields;

  // Null until this extend is linked.
  private ProtoType type;

  Extend(Location location, String documentation, String name, List<Field> fields) {
    this.location = location;
    this.documentation = documentation;
    this.name = name;
    this.fields = fields;
  }

  public Location location() {
    return location;
  }

  public String documentation() {
    return documentation;
  }

  public String name() {
    return name;
  }

  public List<Field> fields() {
    return fields;
  }

  /** Null until this extend is linked. */
  public ProtoType type() {
    return type;
  }

  public ProtoMember member(Field field) {
    return ProtoMember.get(type, field);
  }

  void link(Linker linker) {
    Linker scoped = linker.withContext(this);
    type = scoped.resolveMessageType(name);
    Type resolved = scoped.get(type);
    if (resolved != null) {
      ((MessageType) resolved).addExtensionFields(fields);
    }
    for (Field field : fields) {
      field.link(scoped);
    }
  }

  void linkOptions(Linker linker, SyntaxRules syntaxRules, boolean validate) {
    Linker scoped = linker.withContext(this);
    for (Field field : fields) {
      field.linkOptions(scoped, syntaxRules, validate);
    }
  }

  void validate(Linker linker, SyntaxRules syntaxRules) {
    Linker scoped = linker.withContext(this);
    scoped.validateImportForType(location, type);

    syntaxRules.validateExtension(ProtoType.get(name), scoped.errors);
  }

  Extend retainAll(Schema schema, MarkSet markSet) {
    List<Field> retainedFields = Field.retainAll(schema, markSet, type, fields);
    if (retainedFields.isEmpty()) return null;
    Extend result = new Extend(location, documentation, name, retainedFields);
    result.type = type;
    return result;
  }

  Extend retainLinked(java.util.Set<Field> linkedFields) {
    List<Field> retainedFields = new ArrayList<>();
    for (Field field : fields) {
      if (linkedFields.contains(field)) retainedFields.add(field);
    }
    if (retainedFields.isEmpty()) return null;
    Extend result = new Extend(location, documentation, name, retainedFields);
    result.type = type;
    return result;
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof Extend)) return false;
    Extend that = (Extend) other;
    return location.equals(that.location)
        && documentation.equals(that.documentation)
        && name.equals(that.name)
        && fields.equals(that.fields);
  }

  @Override public int hashCode() {
    int result = location.hashCode();
    result = 31 * result + documentation.hashCode();
    result = 31 * result + name.hashCode();
    result = 31 * result + fields.hashCode();
    return result;
  }

  @Override public String toString() {
    return "Extend(location=" + location + ", documentation=" + documentation + ", name=" + name
        + ", fields=" + fields + ")";
  }

  public static List<Extend> fromElements(List<String> namespaces,
      List<ExtendElement> extendElements) {
    List<Extend> result = new ArrayList<>();
    for (ExtendElement element : extendElements) {
      result.add(new Extend(element.location, element.documentation, element.name,
          Field.fromElements(namespaces, element.fields, true, false)));
    }
    return result;
  }

  public static List<ExtendElement> toElements(List<Extend> extendList) {
    List<ExtendElement> result = new ArrayList<>();
    for (Extend extend : extendList) {
      result.add(new ExtendElement(extend.location, extend.name, extend.documentation,
          Field.toElements(extend.fields)));
    }
    return result;
  }
}
