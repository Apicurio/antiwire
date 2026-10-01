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
package com.squareup.wire.internal;

import com.squareup.wire.KotlinConstructorBuilder;
import com.squareup.wire.Message;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.WireField;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Read, write, and describe a tag within a message. This class knows how to assign fields to a
 * builder object, and how to extract values from a message object.
 */
public final class FieldBinding<M extends Message<M, B>, B extends Message.Builder<M, B>>
    extends FieldOrOneOfBinding<M, B> {
  private static final Pattern IS_GETTER_FIELD_NAME_REGEX = Pattern.compile("^is[^a-z].*$");

  private interface BuilderSetter {
    void set(Object builder, Object value);
  }

  private interface BuilderGetter {
    Object get(Object builder);
  }

  private interface InstanceGetter {
    Object get(Object instance);
  }

  private final WireField.Label label;
  private final String name;
  private final String wireFieldJsonName;
  private final String declaredName;
  private final int tag;
  private final String keyAdapterString;
  private final String adapterString;
  private final boolean redacted;
  private final BuilderSetter builderSetter;
  private final BuilderGetter builderGetter;
  private final InstanceGetter instanceGetter;
  private final boolean writeIdentityValues;
  private final ClassLoader classLoader;
  private final Field messageField;

  public FieldBinding(WireField wireField, Class<M> messageType, Field messageField,
      Class<B> builderType, boolean writeIdentityValues, ClassLoader classLoader) {
    this.label = wireField.label();
    this.name = messageField.getName();
    this.wireFieldJsonName = wireField.jsonName();
    this.declaredName =
        wireField.declaredName().isEmpty() ? messageField.getName() : wireField.declaredName();
    this.tag = wireField.tag();
    this.keyAdapterString = wireField.keyAdapter();
    this.adapterString = wireField.adapter();
    this.redacted = wireField.redacted();
    this.messageField = messageField;
    this.writeIdentityValues = writeIdentityValues;
    this.classLoader = classLoader;
    this.builderSetter = getBuilderSetter(builderType, wireField, messageField, name);
    this.builderGetter = getBuilderGetter(builderType, wireField, name);
    this.instanceGetter = getInstanceGetter(messageType, messageField, name);
  }

  @Override public int tag() {
    return tag;
  }

  @Override public WireField.Label label() {
    return label;
  }

  @Override public String name() {
    return name;
  }

  @Override public String wireFieldJsonName() {
    return wireFieldJsonName;
  }

  @Override public String declaredName() {
    return declaredName;
  }

  @Override public boolean redacted() {
    return redacted;
  }

  @Override public boolean writeIdentityValues() {
    return writeIdentityValues;
  }

  @Override public ProtoAdapter<?> keyAdapter() {
    return ProtoAdapter.get(keyAdapterString, classLoader);
  }

  @Override public ProtoAdapter<?> singleAdapter() {
    return ProtoAdapter.get(adapterString, classLoader);
  }

  @Override public boolean isMap() {
    return !keyAdapterString.isEmpty();
  }

  @Override public boolean isMessage() {
    return Message.class.isAssignableFrom(box(singleAdapter().type));
  }

  private static Class<?> box(Class<?> type) {
    // KClass.javaObjectType semantics for the reflection-relevant cases.
    if (type == int.class) return Integer.class;
    if (type == long.class) return Long.class;
    if (type == short.class) return Short.class;
    if (type == byte.class) return Byte.class;
    if (type == boolean.class) return Boolean.class;
    if (type == float.class) return Float.class;
    if (type == double.class) return Double.class;
    return type;
  }

  private static BuilderSetter getBuilderSetter(Class<?> builderType, WireField wireField,
      Field messageField, String name) {
    if (KotlinConstructorBuilder.class.isAssignableFrom(builderType)) {
      return (builder, value) ->
          ((KotlinConstructorBuilder<?, ?>) builder).set(wireField, value);
    }
    if (wireField.label().isOneOf()) {
      Class<?> type = messageField.getType();
      Method method;
      try {
        method = builderType.getMethod(name, type);
      } catch (NoSuchMethodException e) {
        throw new AssertionError(
            "No builder method " + builderType.getName() + "." + name + "(" + type.getName()
                + ")");
      }
      return (builder, value) -> {
        try {
          method.invoke(builder, value);
        } catch (ReflectiveOperationException e) {
          throw new RuntimeException(e);
        }
      };
    }
    Field field;
    try {
      field = builderType.getField(name);
    } catch (NoSuchFieldException e) {
      throw new AssertionError("No builder field " + builderType.getName() + "." + name);
    }
    return (builder, value) -> {
      try {
        field.set(builder, value);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    };
  }

  private static BuilderGetter getBuilderGetter(Class<?> builderType, WireField wireField,
      String name) {
    if (KotlinConstructorBuilder.class.isAssignableFrom(builderType)) {
      return builder -> ((KotlinConstructorBuilder<?, ?>) builder).get(wireField);
    }
    Field field;
    try {
      field = builderType.getField(name);
    } catch (NoSuchFieldException e) {
      throw new AssertionError("No builder field " + builderType.getName() + "." + name);
    }
    return builder -> {
      try {
        return field.get(builder);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    };
  }

  private static InstanceGetter getInstanceGetter(Class<?> messageType, Field messageField,
      String name) {
    if (Modifier.isPrivate(messageField.getModifiers())) {
      // When the field name matches the isXxx convention the getter keeps the name verbatim;
      // otherwise it is getXxx.
      String getterName = IS_GETTER_FIELD_NAME_REGEX.matcher(name).matches()
          ? name
          : "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
      Method getter;
      try {
        getter = messageType.getMethod(getterName);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      return instance -> {
        try {
          return getter.invoke(instance);
        } catch (ReflectiveOperationException e) {
          throw new RuntimeException(e);
        }
      };
    }
    return instance -> {
      try {
        return messageField.get(instance);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    };
  }

  /** Accept a single value, independent of whether this value is single or repeated. */
  @SuppressWarnings("unchecked")
  @Override public void value(B builder, Object value) {
    if (label().isRepeated()) {
      Object list = getFromBuilder(builder);
      if (list instanceof List) {
        // Upstream branches on Kotlin MutableList versus read-only List (the copy-on-add
        // fallback); Java has no mutability query, so attempt in-place add and copy on refusal.
        try {
          ((List<Object>) list).add(value);
        } catch (UnsupportedOperationException e) {
          List<Object> mutable = new ArrayList<>((List<Object>) list);
          mutable.add(value);
          set(builder, mutable);
        }
      } else {
        throw new ClassCastException(
            "Expected a list type, got " + (list == null ? null : list.getClass()) + ".");
      }
    } else if (isMap()) {
      Object map = getFromBuilder(builder);
      if (map instanceof Map) {
        try {
          ((Map<Object, Object>) map).putAll((Map<?, ?>) value);
        } catch (UnsupportedOperationException e) {
          Map<Object, Object> mutable = new LinkedHashMap<>((Map<Object, Object>) map);
          mutable.putAll((Map<?, ?>) value);
          set(builder, mutable);
        }
      } else {
        throw new ClassCastException(
            "Expected a map type, got " + (map == null ? null : map.getClass()) + ".");
      }
    } else {
      set(builder, value);
    }
  }

  @Override public void set(B builder, Object value) {
    builderSetter.set(builder, value);
  }

  @Override public Object get(M message) {
    return instanceGetter.get(message);
  }

  @Override public Object getFromBuilder(B builder) {
    return builderGetter.get(builder);
  }
}
