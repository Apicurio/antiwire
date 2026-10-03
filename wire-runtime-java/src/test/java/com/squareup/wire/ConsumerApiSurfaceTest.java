/*
 * Copyright (C) 2026 the antiwire authors
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
package com.squareup.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The phase-1 contract of docs/api-surface.md, made mechanical. The pattern follows
 * wire-schema-java's JdkSchemaLoaderValidationTest.
 *
 * <p>Test 1: every public or protected constructor, method, field, and implemented interface
 * of the consumer classes {@link ProtoAdapter}, {@link Message} (including
 * {@link Message.Builder}), {@link AnyMessage}, and {@link FieldEncoding} (consumer-reachable
 * through {@code addUnknownField}), including generic type arguments and array components, is
 * free of {@code okio.*} types unless it is part of the documented deprecated bridge layer.
 * Protected members count: generated code extends these classes.
 *
 * <p>Test 2 pins that layer as an exact set: the consumer-reachable members that expose okio
 * types are exactly the members the pinned-upstream fixtures compile against, and nothing
 * else. The set fails closed: a new okio-typed member fails here even when deprecated, and
 * removing a fixture-facing member fails the fixture modules' own compilation.
 */
public class ConsumerApiSurfaceTest {
  /**
   * The documented engine/compat bridge (docs/api-surface.md), rendered as
   * owner#name(parameter,type,names); constructors render as {@code <init>}.
   */
  private static final Set<String> DOCUMENTED_OKIO_BRIDGE_MEMBERS = new TreeSet<>(Set.of(
      "com.squareup.wire.AnyMessage#<init>(java.lang.String,okio.ByteString)",
      "com.squareup.wire.AnyMessage#copy(java.lang.String,okio.ByteString)",
      "com.squareup.wire.AnyMessage#value",
      "com.squareup.wire.Message#<init>(com.squareup.wire.ProtoAdapter,okio.ByteString)",
      "com.squareup.wire.Message#encode(okio.BufferedSink)",
      "com.squareup.wire.Message#encodeByteString()",
      "com.squareup.wire.Message#unknownFields()",
      "com.squareup.wire.Message$Builder#addUnknownFields(okio.ByteString)",
      "com.squareup.wire.Message$Builder#buildUnknownFields()",
      "com.squareup.wire.ProtoAdapter#BYTES",
      "com.squareup.wire.ProtoAdapter#BYTES_VALUE",
      "com.squareup.wire.ProtoAdapter#decode(okio.BufferedSource)",
      "com.squareup.wire.ProtoAdapter#decode(okio.ByteString)",
      "com.squareup.wire.ProtoAdapter#encode(okio.BufferedSink,java.lang.Object)",
      "com.squareup.wire.ProtoAdapter#encodeByteString(java.lang.Object)"));

  private static final List<Class<?>> CONSUMER_CLASSES = consumerClasses();

  private static List<Class<?>> consumerClasses() {
    List<Class<?>> classes = new ArrayList<>();
    for (Class<?> root :
        new Class<?>[] { ProtoAdapter.class, Message.class, AnyMessage.class,
            FieldEncoding.class }) {
      classes.add(root);
      for (Class<?> nested : root.getDeclaredClasses()) {
        if (Modifier.isPublic(nested.getModifiers())) classes.add(nested);
      }
    }
    return classes;
  }

  @Test public void noOkioTypesInPublicSignatures() {
    List<String> offenders = new ArrayList<>();
    for (Class<?> clazz : CONSUMER_CLASSES) {
      for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
        if (!isConsumerReachable(constructor.getModifiers())) continue;
        if (isDeprecated(constructor)) continue;
        if (exposesOkio(constructor)) offenders.add(render(constructor));
      }
      for (Method method : clazz.getDeclaredMethods()) {
        if (!isConsumerReachable(method.getModifiers())) continue;
        if (method.isSynthetic() || isDeprecated(method)) continue;
        if (exposesOkio(method)) offenders.add(render(method));
      }
      for (Field field : clazz.getDeclaredFields()) {
        if (!isConsumerReachable(field.getModifiers())) continue;
        if (isDeprecated(field)) continue;
        if (usesOkioType(field.getGenericType())) offenders.add(render(field));
      }
      for (Type implemented : clazz.getGenericInterfaces()) {
        if (usesOkioType(implemented)) {
          offenders.add(clazz + " implements okio-parameterized type " + implemented);
        }
      }
    }
    assertTrue(offenders.isEmpty(),
        "okio types leaked into the non-deprecated consumer API: " + offenders);
  }

  @Test public void okioBridgeSurfaceIsExactlyTheDocumentedSet() {
    Set<String> actual = new TreeSet<>();
    for (Class<?> clazz : CONSUMER_CLASSES) {
      for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
        if (!isConsumerReachable(constructor.getModifiers())) continue;
        if (exposesOkio(constructor)) actual.add(render(constructor));
      }
      for (Method method : clazz.getDeclaredMethods()) {
        if (!isConsumerReachable(method.getModifiers())) continue;
        if (method.isSynthetic()) continue;
        if (exposesOkio(method)) actual.add(render(method));
      }
      for (Field field : clazz.getDeclaredFields()) {
        if (!isConsumerReachable(field.getModifiers())) continue;
        if (usesOkioType(field.getGenericType())) actual.add(render(field));
      }
    }
    assertEquals(DOCUMENTED_OKIO_BRIDGE_MEMBERS, actual,
        "the okio bridge surface changed: members only in ACTUAL are new okio exposure "
            + "(extend the bridge only with a docs/api-surface.md update), members only in "
            + "EXPECTED were removed (the pinned-upstream fixtures compile against them)");
  }

  /** Public or protected: the surface consumers and generated subclasses reach. */
  private static boolean isConsumerReachable(int modifiers) {
    return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
  }

  private static boolean exposesOkio(Executable executable) {
    if (executable instanceof Method && usesOkioType(((Method) executable).getGenericReturnType())) {
      return true;
    }
    for (Type type : executable.getGenericParameterTypes()) {
      if (usesOkioType(type)) return true;
    }
    return false;
  }

  private static boolean usesOkioType(Type type) {
    if (type instanceof Class) return isOkio((Class<?>) type);
    if (type instanceof ParameterizedType) {
      for (Type argument : ((ParameterizedType) type).getActualTypeArguments()) {
        if (usesOkioType(argument)) return true;
      }
      return false;
    }
    if (type instanceof GenericArrayType) {
      return usesOkioType(((GenericArrayType) type).getGenericComponentType());
    }
    return false;
  }

  private static String render(Executable executable) {
    StringBuilder result = new StringBuilder(executable.getDeclaringClass().getName())
        .append('#')
        .append(executable instanceof Constructor ? "<init>" : ((Method) executable).getName())
        .append('(');
    Class<?>[] parameters = executable.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) result.append(',');
      result.append(parameters[i].getName());
    }
    return result.append(')').toString();
  }

  private static String render(Field field) {
    return field.getDeclaringClass().getName() + '#' + field.getName();
  }

  private static boolean isDeprecated(java.lang.reflect.AnnotatedElement member) {
    return member.isAnnotationPresent(Deprecated.class);
  }

  private static boolean isOkio(Class<?> type) {
    return type.getName().startsWith("okio.");
  }
}
