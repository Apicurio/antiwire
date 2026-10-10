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
import com.squareup.wire.schema.internal.parser.EnumElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.TypeElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Type {
  public abstract Location getLocation();

  public abstract ProtoType getType();

  public abstract String getName();

  public abstract String getDocumentation();

  public abstract Options getOptions();

  public abstract List<Type> getNestedTypes();

  public abstract List<Extend> getNestedExtendList();

  public abstract Syntax getSyntax();

  public abstract void linkMembers(Linker linker);

  public abstract void linkOptions(Linker linker, SyntaxRules syntaxRules, boolean validate);

  public abstract void validate(Linker linker, SyntaxRules syntaxRules);

  public abstract Type retainAll(Schema schema, MarkSet markSet);

  /**
   * Returns a copy of this containing only the types in {@code linkedTypes} and extensions in
   * {@code linkedFields}, or null if that set is empty. This will return an {@link EnclosingType}
   * if it is itself not linked, but its nested types are linked.
   *
   * <p>The returned type is a shadow of its former self. It is useful for linking against, but
   * lacks most of the members of the original type.
   */
  public abstract Type retainLinked(java.util.Set<ProtoType> linkedTypes,
      java.util.Set<Field> linkedFields);

  /** Returns all types and subtypes which are linked to the type. */
  public List<Type> typesAndNestedTypes() {
    List<Type> typesAndNestedTypes = new ArrayList<>();
    typesAndNestedTypes.add(this);
    for (Type type : getNestedTypes()) {
      typesAndNestedTypes.addAll(type.typesAndNestedTypes());
    }
    return typesAndNestedTypes;
  }

  public static Type get(List<String> namespaces, ProtoType protoType, TypeElement type,
      Syntax syntax) {
    if (type instanceof EnumElement) {
      return EnumType.fromElement(protoType, (EnumElement) type, syntax);
    }
    if (type instanceof MessageElement) {
      return MessageType.fromElement(namespaces, protoType, (MessageElement) type, syntax);
    }
    throw new IllegalArgumentException("unexpected type: " + type);
  }

  public static List<Type> fromElements(String packageName, List<TypeElement> elements,
      Syntax syntax) {
    List<Type> result = new ArrayList<>();
    for (TypeElement element : elements) {
      ProtoType protoType = ProtoType.get(packageName, element.getName());
      List<String> namespaces = packageName == null
          ? Collections.emptyList()
          : Collections.singletonList(packageName);
      result.add(get(namespaces, protoType, element, syntax));
    }
    return result;
  }

  private static TypeElement toElement(Type type) {
    if (type instanceof EnumType) return ((EnumType) type).toElement();
    if (type instanceof MessageType) return ((MessageType) type).toElement();
    if (type instanceof EnclosingType) return ((EnclosingType) type).toElement();
    throw new AssertionError();
  }

  public static List<TypeElement> toElements(List<Type> types) {
    List<TypeElement> result = new ArrayList<>();
    for (Type type : types) {
      result.add(toElement(type));
    }
    return result;
  }

  /** Mirror of the Kotlin companion object: lets Java callers write {@code Type.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }

    public List<Type> fromElements(String packageName, List<TypeElement> elements, Syntax syntax) {
      return Type.fromElements(packageName, elements, syntax);
    }

    public Type get(List<String> namespaces, ProtoType protoType, TypeElement type, Syntax syntax) {
      return Type.get(namespaces, protoType, type, syntax);
    }

    public List<TypeElement> toElements(List<Type> types) {
      return Type.toElements(types);
    }
  }
}
