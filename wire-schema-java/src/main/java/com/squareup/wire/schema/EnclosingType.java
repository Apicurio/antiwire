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
package com.squareup.wire.schema;

import com.squareup.wire.Syntax;
import com.squareup.wire.schema.internal.parser.MessageElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** An empty type which only holds nested types. */
public final class EnclosingType extends Type {
  final Location location;
  final ProtoType type;
  final String name;
  final String documentation;
  final List<Type> nestedTypes;
  final List<Extend> nestedExtendList;
  final Syntax syntax;

  public EnclosingType(Location location, ProtoType type, String name, String documentation,
      List<Type> nestedTypes, List<Extend> nestedExtendList, Syntax syntax) {
    this.location = location;
    this.type = type;
    this.name = name;
    this.documentation = documentation;
    this.nestedTypes = nestedTypes;
    this.nestedExtendList = nestedExtendList;
    this.syntax = syntax;
  }

  /** Returns a copy of this enclosing type with the given property values, mirroring the Kotlin
   * data-class {@code copy}. */
  public EnclosingType copy(Location location, ProtoType type, String name, String documentation,
      List<Type> nestedTypes, List<Extend> nestedExtendList, Syntax syntax) {
    return new EnclosingType(location, type, name, documentation, nestedTypes, nestedExtendList,
        syntax);
  }

  @Override public Location location() {
    return location;
  }

  @Override public ProtoType type() {
    return type;
  }

  @Override public String name() {
    return name;
  }

  @Override public String documentation() {
    return documentation;
  }

  @Override public Options options() {
    return new Options(Options.MESSAGE_OPTIONS, Collections.emptyList());
  }

  @Override public List<Type> nestedTypes() {
    return nestedTypes;
  }

  @Override public List<Extend> nestedExtendList() {
    return nestedExtendList;
  }

  @Override public Syntax syntax() {
    return syntax;
  }

  @Override void linkMembers(Linker linker) {
  }

  @Override void linkOptions(Linker linker, SyntaxRules syntaxRules, boolean validate) {
    for (Type nested : nestedTypes) {
      nested.linkOptions(linker, syntaxRules, validate);
    }
  }

  @Override void validate(Linker linker, SyntaxRules syntaxRules) {
    for (Type nested : nestedTypes) {
      nested.validate(linker, syntaxRules);
    }
  }

  @Override Type retainAll(Schema schema, MarkSet markSet) {
    List<Type> retainedNestedTypes = retainAllTypes(schema, markSet);
    List<Extend> retainedNestedExtends = retainAllExtends(schema, markSet);
    if (retainedNestedTypes.isEmpty() && retainedNestedExtends.isEmpty()) return null;
    return new EnclosingType(location, type, name, documentation, retainedNestedTypes,
        retainedNestedExtends, syntax);
  }

  @Override Type retainLinked(Set<ProtoType> linkedTypes, Set<Field> linkedFields) {
    List<Type> retainedNestedTypes = new ArrayList<>();
    for (Type nested : nestedTypes) {
      Type retained = nested.retainLinked(linkedTypes, linkedFields);
      if (retained != null) retainedNestedTypes.add(retained);
    }
    List<Extend> retainedNestedExtends = new ArrayList<>();
    for (Extend nested : nestedExtendList) {
      Extend retained = nested.retainLinked(linkedFields);
      if (retained != null) retainedNestedExtends.add(retained);
    }
    if (retainedNestedTypes.isEmpty() && retainedNestedExtends.isEmpty()) return null;
    return new EnclosingType(location, type, name, documentation, retainedNestedTypes,
        retainedNestedExtends, syntax);
  }

  private List<Type> retainAllTypes(Schema schema, MarkSet markSet) {
    List<Type> result = new ArrayList<>();
    for (Type nested : nestedTypes) {
      Type retained = nested.retainAll(schema, markSet);
      if (retained != null) result.add(retained);
    }
    return result;
  }

  private List<Extend> retainAllExtends(Schema schema, MarkSet markSet) {
    List<Extend> result = new ArrayList<>();
    for (Extend nested : nestedExtendList) {
      Extend retained = nested.retainAll(schema, markSet);
      if (retained != null) result.add(retained);
    }
    return result;
  }

  MessageElement toElement() {
    return new MessageElement(location, type.simpleName(), "", Type.toElements(nestedTypes),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof EnclosingType)) return false;
    EnclosingType that = (EnclosingType) other;
    return location.equals(that.location)
        && type.equals(that.type)
        && name.equals(that.name)
        && documentation.equals(that.documentation)
        && nestedTypes.equals(that.nestedTypes)
        && nestedExtendList.equals(that.nestedExtendList)
        && syntax == that.syntax;
  }

  @Override public int hashCode() {
    int result = location.hashCode();
    result = 31 * result + type.hashCode();
    result = 31 * result + name.hashCode();
    result = 31 * result + documentation.hashCode();
    result = 31 * result + nestedTypes.hashCode();
    result = 31 * result + nestedExtendList.hashCode();
    result = 31 * result + syntax.hashCode();
    return result;
  }

  @Override public String toString() {
    return "EnclosingType(location=" + location + ", type=" + type + ", name=" + name
        + ", documentation=" + documentation + ", nestedTypes=" + nestedTypes
        + ", nestedExtendList=" + nestedExtendList + ", syntax=" + syntax + ")";
  }
}
