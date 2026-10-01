/*
 * Copyright (C) 2020 Square, Inc.
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
import com.squareup.wire.WireOneofField;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/** Upstream Kotlin-internal; public here per the translation conventions. */
public final class SealedOneOfBinding<M extends Message<M, B>, B extends Message.Builder<M, B>>
    extends FieldOrOneOfBinding<M, B> {
  private final Field messageField;
  private final WireOneofField annotation;
  private final Class<?> subclassType;
  private final ClassLoader classLoader;

  // When there is no explicit Builder (javaInterop = false), KotlinConstructorBuilder is used.
  // It has no message-specific fields, so sealed oneof values live in its side map instead.
  private final boolean isKotlinConstructorBuilder;
  private final Field builderField;
  private volatile Field valueFieldMemoized;

  public SealedOneOfBinding(Field messageField, Class<B> builderType, WireOneofField annotation,
      Class<?> subclassType, ClassLoader classLoader) {
    this.messageField = messageField;
    this.annotation = annotation;
    this.subclassType = subclassType;
    this.classLoader = classLoader;
    messageField.setAccessible(true);
    this.isKotlinConstructorBuilder = builderType == (Class<?>) KotlinConstructorBuilder.class;
    if (isKotlinConstructorBuilder) {
      this.builderField = null;
    } else {
      try {
        this.builderField = builderType.getDeclaredField(messageField.getName());
        this.builderField.setAccessible(true);
      } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override public int tag() {
    return annotation.tag();
  }

  /** Sealed oneof fields are always optional; equivalent to OPTIONAL. */
  @Override public WireField.Label label() {
    return WireField.Label.OPTIONAL;
  }

  @Override public boolean redacted() {
    return annotation.redacted();
  }

  @Override public String wireFieldJsonName() {
    return annotation.jsonName();
  }

  @Override public String name() {
    return annotation.declaredName();
  }

  @Override public String declaredName() {
    return annotation.declaredName();
  }

  @Override public boolean isMap() {
    return false;
  }

  @Override public boolean isMessage() {
    return Message.class.isAssignableFrom(singleAdapter().type);
  }

  @Override public ProtoAdapter<?> keyAdapter() {
    throw new IllegalStateException("not a map");
  }

  @Override public ProtoAdapter<?> singleAdapter() {
    return ProtoAdapter.get(annotation.adapter(), classLoader);
  }

  @Override public boolean writeIdentityValues() {
    return false;
  }

  private Field valueField() {
    Field result = valueFieldMemoized;
    if (result == null) {
      try {
        result = subclassType.getDeclaredField("value");
        result.setAccessible(true);
      } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
      valueFieldMemoized = result;
    }
    return result;
  }

  @Override public Object get(M message) {
    Object sealed;
    try {
      sealed = messageField.get(message);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    if (sealed == null || !subclassType.isInstance(sealed)) return null;
    try {
      return valueField().get(sealed);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override public Object getFromBuilder(B builder) {
    Object sealed;
    if (isKotlinConstructorBuilder) {
      sealed = ((KotlinConstructorBuilder<M, B>) builder).getSealedOneof(messageField.getName());
    } else {
      try {
        sealed = builderField.get(builder);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    if (sealed == null || !subclassType.isInstance(sealed)) return null;
    try {
      return valueField().get(sealed);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override public void set(B builder, Object value) {
    if (value == null) return;
    Object sealed;
    try {
      Constructor<?> ctor = null;
      for (Constructor<?> candidate : subclassType.getDeclaredConstructors()) {
        if (candidate.getParameterCount() == 1) {
          ctor = candidate;
          break;
        }
      }
      if (ctor == null) throw new NoSuchMethodException("single-arg constructor");
      ctor.setAccessible(true);
      sealed = ctor.newInstance(value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    if (isKotlinConstructorBuilder) {
      ((KotlinConstructorBuilder<M, B>) builder).setSealedOneof(messageField.getName(), sealed);
    } else {
      try {
        builderField.set(builder, sealed);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override public void value(B builder, Object value) {
    set(builder, value);
  }
}
