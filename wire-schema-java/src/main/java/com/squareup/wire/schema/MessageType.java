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

import com.squareup.wire.Syntax;
import com.squareup.wire.schema.internal.parser.MessageElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MessageType extends Type {
  static final ProtoMember DEPRECATED =
      ProtoMember.get(Options.MESSAGE_OPTIONS, "deprecated");

  final ProtoType type;
  final Location location;
  final String documentation;
  final String name;
  final List<Field> declaredFields;

  /** Mutated by {@link #addExtensionFields(List)} during linking. */
  final List<Field> extensionFields;

  final List<OneOf> oneOfs;
  final List<Type> nestedTypes;
  final List<Extend> nestedExtendList;
  final List<Extensions> extensionsList;
  final List<Reserved> reserveds;
  final Options options;
  final Syntax syntax;

  private Object deprecated;

  MessageType(ProtoType type, Location location, String documentation, String name,
      List<Field> declaredFields, List<Field> extensionFields, List<OneOf> oneOfs,
      List<Type> nestedTypes, List<Extend> nestedExtendList, List<Extensions> extensionsList,
      List<Reserved> reserveds, Options options, Syntax syntax) {
    this.type = type;
    this.location = location;
    this.documentation = documentation;
    this.name = name;
    this.declaredFields = declaredFields;
    this.extensionFields = extensionFields;
    this.oneOfs = oneOfs;
    this.nestedTypes = nestedTypes;
    this.nestedExtendList = nestedExtendList;
    this.extensionsList = extensionsList;
    this.reserveds = reserveds;
    this.options = options;
    this.syntax = syntax;
  }

  /**
   * Returns a copy of this message type with the given property values, mirroring the Kotlin
   * data-class {@code copy}. Like upstream, the copy does not carry over the linked
   * {@code deprecated} option value.
   */
  public MessageType copy(ProtoType type, Location location, String documentation, String name,
      List<Field> declaredFields, List<Field> extensionFields, List<OneOf> oneOfs,
      List<Type> nestedTypes, List<Extend> nestedExtendList, List<Extensions> extensionsList,
      List<Reserved> reserveds, Options options, Syntax syntax) {
    return new MessageType(type, location, documentation, name, declaredFields, extensionFields,
        oneOfs, nestedTypes, nestedExtendList, extensionsList, reserveds, options, syntax);
  }

  @Override public ProtoType type() {
    return type;
  }

  @Override public Location location() {
    return location;
  }

  @Override public String documentation() {
    return documentation;
  }

  @Override public String name() {
    return name;
  }

  public List<Field> declaredFields() {
    return declaredFields;
  }

  public List<Field> extensionFields() {
    return extensionFields;
  }

  public List<OneOf> oneOfs() {
    return oneOfs;
  }

  @Override public List<Type> nestedTypes() {
    return nestedTypes;
  }

  @Override public List<Extend> nestedExtendList() {
    return nestedExtendList;
  }

  public List<Extensions> extensionsList() {
    return extensionsList;
  }

  public List<Reserved> reserveds() {
    return reserveds;
  }

  @Override public Options options() {
    return options;
  }

  @Override public Syntax syntax() {
    return syntax;
  }

  public boolean isDeprecated() {
    return "true".equals(deprecated);
  }

  public List<Field> fields() {
    List<Field> result = new ArrayList<>(declaredFields);
    result.addAll(extensionFields);
    return result;
  }

  public List<Field> requiredFields() {
    List<Field> result = new ArrayList<>();
    for (Field field : fieldsAndOneOfFields()) {
      if (field.isRequired()) result.add(field);
    }
    return result;
  }

  public List<Field> fieldsAndOneOfFields() {
    List<Field> result = new ArrayList<>(declaredFields);
    result.addAll(extensionFields);
    for (OneOf oneOf : oneOfs) {
      result.addAll(oneOf.fields());
    }
    return result;
  }

  /** Returns the field named {@code name}, or null if this type has no such field. */
  public Field field(String name) {
    for (Field field : declaredFields) {
      if (field.name().equals(name)) {
        return field;
      }
    }
    for (OneOf oneOf : oneOfs) {
      for (Field field : oneOf.fields()) {
        if (field.name().equals(name)) {
          return field;
        }
      }
    }
    return null;
  }

  /**
   * Returns the field with the qualified name {@code qualifiedName}, or null if this type has no
   * such field.
   */
  public Field extensionField(String qualifiedName) {
    for (Field field : extensionFields) {
      if (field.qualifiedName().equals(qualifiedName)) {
        return field;
      }
    }
    return null;
  }

  /** Returns the oneOf named {@code name}, or null if this type has no such oneOf. */
  public OneOf oneOf(String name) {
    for (OneOf oneOf : oneOfs) {
      if (oneOf.name().equals(name)) {
        return oneOf;
      }
    }
    return null;
  }

  /** Returns the field tagged {@code tag}, or null if this type has no such field. */
  public Field field(int tag) {
    for (Field field : declaredFields) {
      if (field.tag() == tag) {
        return field;
      }
    }
    for (Field field : extensionFields) {
      if (field.tag() == tag) {
        return field;
      }
    }
    return null;
  }

  public Map<String, Field> extensionFieldsMap() {
    // TODO(jwilson): simplify this to just resolve field values directly.
    Map<String, Field> extensionsForType = new HashMap<>();
    for (Field field : extensionFields) {
      extensionsForType.put(field.qualifiedName(), field);
    }
    return extensionsForType;
  }

  public void addExtensionFields(List<Field> fields) {
    extensionFields.addAll(fields);
  }

  @Override void linkMembers(Linker linker) {
    Linker scoped = linker.withContext(this);
    for (Field field : declaredFields) {
      field.link(scoped);
    }
    for (OneOf oneOf : oneOfs) {
      oneOf.link(scoped);
    }
  }

  @Override void linkOptions(Linker linker, SyntaxRules syntaxRules, boolean validate) {
    Linker scoped = linker.withContext(this);
    for (Type nestedType : nestedTypes) {
      nestedType.linkOptions(scoped, syntaxRules, validate);
    }
    for (Field field : declaredFields) {
      field.linkOptions(scoped, syntaxRules, validate);
    }
    for (OneOf oneOf : oneOfs) {
      oneOf.linkOptions(scoped, syntaxRules, validate);
    }
    options.link(scoped, location, validate);

    deprecated = options.get(DEPRECATED);
  }

  @Override void validate(Linker linker, SyntaxRules syntaxRules) {
    Linker scoped = linker.withContext(this);
    scoped.validateFields(fieldsAndOneOfFields(), reserveds, syntaxRules);
    scoped.validateEnumConstantNameUniqueness(nestedTypes);
    for (Field field : fieldsAndOneOfFields()) {
      field.validate(scoped, syntaxRules);
    }
    for (Type nestedType : nestedTypes) {
      nestedType.validate(scoped, syntaxRules);
    }
    for (Extensions extensions : extensionsList) {
      extensions.validate(scoped);
    }
  }

  @Override Type retainAll(Schema schema, MarkSet markSet) {
    List<Type> retainedNestedTypes = new ArrayList<>();
    for (Type nested : nestedTypes) {
      Type retained = nested.retainAll(schema, markSet);
      if (retained != null) retainedNestedTypes.add(retained);
    }
    List<Extend> retainedNestedExtends = new ArrayList<>();
    for (Extend nested : nestedExtendList) {
      Extend retained = nested.retainAll(schema, markSet);
      if (retained != null) retainedNestedExtends.add(retained);
    }
    if (!markSet.contains(type) && !Options.isGoogleProtobufOptionType(type)) {
      if (retainedNestedTypes.isEmpty() && retainedNestedExtends.isEmpty()) {
        // This type is not retained, and none of its nested types are retained, prune it.
        return null;
      }
      // This type is not retained but retained nested types, replace it with an enclosing type.
      return new EnclosingType(location, type, name, documentation, retainedNestedTypes,
          retainedNestedExtends, syntax);
    }

    List<OneOf> retainedOneOfs = new ArrayList<>();
    for (OneOf oneOf : oneOfs) {
      OneOf retained = oneOf.retainAll(schema, markSet, type);
      if (retained != null) retainedOneOfs.add(retained);
    }

    MessageType result = new MessageType(type, location, documentation, name,
        Field.retainAll(schema, markSet, type, declaredFields),
        Field.retainAll(schema, markSet, type, extensionFields),
        retainedOneOfs, retainedNestedTypes, retainedNestedExtends, extensionsList, reserveds,
        options.retainAll(schema, markSet), syntax);
    result.deprecated = deprecated;
    return result;
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

    if (!linkedTypes.contains(type)) {
      if (retainedNestedTypes.isEmpty() && retainedNestedExtends.isEmpty()) {
        // This type is not retained, and none of its nested types are retained, prune it.
        return null;
      }
      // This type is not retained but retained nested types, replace it with an enclosing type.
      return new EnclosingType(location, type, name, documentation, retainedNestedTypes,
          retainedNestedExtends, syntax);
    }

    // We're retaining this type. Retain its fields and oneofs.
    List<OneOf> retainedOneOfs = new ArrayList<>();
    for (OneOf oneOf : oneOfs) {
      OneOf retained = oneOf.retainLinked();
      if (retained != null) retainedOneOfs.add(retained);
    }

    return new MessageType(type, location, documentation, name,
        Field.retainLinked(declaredFields),
        Field.retainLinked(extensionFields),
        retainedOneOfs, retainedNestedTypes, retainedNestedExtends,
        Collections.emptyList(), Collections.emptyList(), options.retainLinked(), syntax);
  }

  MessageElement toElement() {
    return new MessageElement(location, name, documentation, Type.toElements(nestedTypes),
        options.elements(), Reserved.toElements(reserveds), Field.toElements(declaredFields),
        OneOf.toElements(oneOfs), Extensions.toElements(extensionsList),
        Collections.emptyList(), Extend.toElements(nestedExtendList));
  }

  static MessageType fromElement(List<String> namespaces, ProtoType protoType,
      MessageElement messageElement, Syntax syntax) {
    if (!messageElement.groups.isEmpty()) {
      throw new IllegalStateException(
          messageElement.groups.get(0).location + ": 'group' is not supported");
    }
    // Namespaces for all child elements include this message's name.
    List<String> childNamespaces;
    if (namespaces.isEmpty()) {
      // The first element must be the package name.
      childNamespaces = new ArrayList<>();
      childNamespaces.add("");
      childNamespaces.add(messageElement.name);
    } else {
      childNamespaces = new ArrayList<>(namespaces);
      childNamespaces.add(messageElement.name);
    }
    List<Type> nested = new ArrayList<>();
    for (com.squareup.wire.schema.internal.parser.TypeElement element
        : messageElement.nestedTypes) {
      nested.add(Type.get(childNamespaces, protoType.nestedType(element.name()), element, syntax));
    }
    List<Extend> nestedExtends = Extend.fromElements(childNamespaces,
        messageElement.extendDeclarations);

    return new MessageType(protoType, messageElement.location, messageElement.documentation,
        messageElement.name,
        Field.fromElements(childNamespaces, messageElement.fields, false, false),
        // Extension fields are populated during linking.
        new ArrayList<>(),
        OneOf.fromElements(childNamespaces, messageElement.oneOfs),
        nested, nestedExtends,
        Extensions.fromElements(messageElement.extensions),
        Reserved.fromElements(messageElement.reserveds),
        new Options(Options.MESSAGE_OPTIONS, messageElement.options),
        syntax);
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof MessageType)) return false;
    MessageType that = (MessageType) other;
    return type.equals(that.type)
        && location.equals(that.location)
        && documentation.equals(that.documentation)
        && name.equals(that.name)
        && declaredFields.equals(that.declaredFields)
        && extensionFields.equals(that.extensionFields)
        && oneOfs.equals(that.oneOfs)
        && nestedTypes.equals(that.nestedTypes)
        && nestedExtendList.equals(that.nestedExtendList)
        && extensionsList.equals(that.extensionsList)
        && reserveds.equals(that.reserveds)
        && Objects.equals(options, that.options)
        && syntax == that.syntax;
  }

  @Override public int hashCode() {
    int result = type.hashCode();
    result = 31 * result + location.hashCode();
    result = 31 * result + documentation.hashCode();
    result = 31 * result + name.hashCode();
    result = 31 * result + declaredFields.hashCode();
    result = 31 * result + extensionFields.hashCode();
    result = 31 * result + oneOfs.hashCode();
    result = 31 * result + nestedTypes.hashCode();
    result = 31 * result + nestedExtendList.hashCode();
    result = 31 * result + extensionsList.hashCode();
    result = 31 * result + reserveds.hashCode();
    result = 31 * result + Objects.hashCode(options);
    result = 31 * result + syntax.hashCode();
    return result;
  }

  @Override public String toString() {
    return "MessageType(type=" + type + ", location=" + location + ", documentation="
        + documentation + ", name=" + name + ", declaredFields=" + declaredFields
        + ", extensionFields=" + extensionFields + ", oneOfs=" + oneOfs + ", nestedTypes="
        + nestedTypes + ", nestedExtendList=" + nestedExtendList + ", extensionsList="
        + extensionsList + ", reserveds=" + reserveds + ", options=" + options + ", syntax="
        + syntax + ")";
  }
}
