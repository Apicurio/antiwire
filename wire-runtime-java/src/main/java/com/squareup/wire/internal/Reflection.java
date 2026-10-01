/*
 * Copyright (C) 2019 Square, Inc.
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

import com.squareup.wire.FieldEncoding;
import com.squareup.wire.KotlinConstructorBuilder;
import com.squareup.wire.Message;
import com.squareup.wire.OneOf;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.Syntax;
import com.squareup.wire.WireField;
import com.squareup.wire.WireOneofField;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Upstream declares these as Kotlin file functions on the Internal facade; they become static
 * methods on this class (a Java-only shape; recorded in the ownership map).
 */
public final class Reflection {
  private Reflection() {
  }

  public static <M extends Message<M, B>, B extends Message.Builder<M, B>>
      RuntimeMessageAdapter<M, B> createRuntimeMessageAdapter(
          Class<M> messageType, String typeUrl, Syntax syntax) {
    return createRuntimeMessageAdapter(messageType, typeUrl, syntax,
        messageType.getClassLoader(), false, false);
  }

  public static <M extends Message<M, B>, B extends Message.Builder<M, B>>
      RuntimeMessageAdapter<M, B> createRuntimeMessageAdapter(
          Class<M> messageType, String typeUrl, Syntax syntax, ClassLoader classLoader) {
    return createRuntimeMessageAdapter(messageType, typeUrl, syntax, classLoader, false, false);
  }

  @SuppressWarnings({"unchecked", "deprecation"})
  public static <M extends Message<M, B>, B extends Message.Builder<M, B>>
      RuntimeMessageAdapter<M, B> createRuntimeMessageAdapter(
          Class<M> messageType, String typeUrl, Syntax syntax, ClassLoader classLoader,
          boolean writeIdentityValues, boolean preservingProtoFieldNames) {
    Class<B> builderType = getBuilderType(messageType);
    java.util.function.Supplier<B> newBuilderInstance;
    if (builderType.isAssignableFrom(KotlinConstructorBuilder.class)) {
      newBuilderInstance = () -> (B) new KotlinConstructorBuilder<>(messageType);
    } else {
      newBuilderInstance = () -> {
        try {
          return builderType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
          throw new RuntimeException(e);
        }
      };
    }

    Map<Integer, FieldOrOneOfBinding<M, B>> fields = new LinkedHashMap<>();

    // Create tag bindings for fields annotated with '@WireField'.
    for (Field messageField : messageType.getDeclaredFields()) {
      WireField wireField = messageField.getAnnotation(WireField.class);
      if (wireField != null) {
        fields.put(wireField.tag(), new FieldBinding<>(
            wireField, messageType, messageField, builderType, writeIdentityValues,
            classLoader));
      } else if (messageField.getType() == OneOf.class) {
        for (OneOf.Key<?> key : getKeys(messageField)) {
          fields.put(key.tag, new OneOfBinding<>(messageField, builderType, key,
              writeIdentityValues));
        }
      } else {
        for (WireOneofFieldAndClass pair : getSealedOneOfAnnotations(messageField)) {
          fields.put(pair.annotation.tag(), new SealedOneOfBinding<>(
              messageField, builderType, pair.annotation, pair.nestedClass, classLoader));
        }
      }
    }

    return new RuntimeMessageAdapter<>(
        new RuntimeMessageBinding<>(
            messageType, builderType, newBuilderInstance,
            Collections.unmodifiableMap(fields), typeUrl, syntax),
        preservingProtoFieldNames);
  }

  public static <M extends Message<M, B>, B extends Message.Builder<M, B>>
      RuntimeMessageAdapter<M, B> createRuntimeMessageAdapter(
          Class<M> messageType, boolean writeIdentityValues, boolean preservingProtoFieldNames,
          ClassLoader classLoader) {
    ProtoAdapter<?> defaultAdapter = ProtoAdapter.get(messageType);
    return createRuntimeMessageAdapter(
        messageType, defaultAdapter.typeUrl, defaultAdapter.syntax, classLoader,
        writeIdentityValues, preservingProtoFieldNames);
  }

  @SuppressWarnings("unchecked")
  private static <M extends Message<M, B>, B extends Message.Builder<M, B>>
      Set<OneOf.Key<?>> getKeys(Field messageField) {
    Class<?> messageClass = messageField.getDeclaringClass();
    try {
      Field keysField = messageClass.getDeclaredField(
          Internal.boxedOneOfKeysFieldName(messageField.getName()));
      keysField.setAccessible(true);
      return (Set<OneOf.Key<?>>) keysField.get(null);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static final class WireOneofFieldAndClass {
    final WireOneofField annotation;
    final Class<?> nestedClass;

    WireOneofFieldAndClass(WireOneofField annotation, Class<?> nestedClass) {
      this.annotation = annotation;
      this.nestedClass = nestedClass;
    }
  }

  private static List<WireOneofFieldAndClass> getSealedOneOfAnnotations(Field messageField) {
    Class<?> sealedClass = messageField.getType();
    List<WireOneofFieldAndClass> result = new ArrayList<>();
    for (Class<?> nestedClass : sealedClass.getDeclaredClasses()) {
      WireOneofField annotation = nestedClass.getAnnotation(WireOneofField.class);
      if (annotation != null) {
        result.add(new WireOneofFieldAndClass(annotation, nestedClass));
      }
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static <M extends Message<M, B>, B extends Message.Builder<M, B>> Class<B>
      getBuilderType(Class<M> messageType) {
    try {
      return (Class<B>) Class.forName(messageType.getName() + "$Builder", false,
          messageType.getClassLoader());
    } catch (ClassNotFoundException e) {
      return (Class<B>) (Class<?>) KotlinConstructorBuilder.class;
    }
  }

  private static final class RuntimeMessageBinding<M extends Message<M, B>,
      B extends Message.Builder<M, B>> implements MessageBinding<M, B> {
    final Class<M> messageType;
    final Class<B> builderType;
    final java.util.function.Supplier<B> createBuilder;
    final Map<Integer, FieldOrOneOfBinding<M, B>> fields;
    final String typeUrl;
    final Syntax syntax;

    RuntimeMessageBinding(Class<M> messageType, Class<B> builderType,
        java.util.function.Supplier<B> createBuilder,
        Map<Integer, FieldOrOneOfBinding<M, B>> fields, String typeUrl, Syntax syntax) {
      this.messageType = messageType;
      this.builderType = builderType;
      this.createBuilder = createBuilder;
      this.fields = fields;
      this.typeUrl = typeUrl;
      this.syntax = syntax;
    }

    @Override public Class<?> messageType() {
      return messageType;
    }

    @Override public Map<Integer, FieldOrOneOfBinding<M, B>> fields() {
      return fields;
    }

    @Override public String typeUrl() {
      return typeUrl;
    }

    @Override public Syntax syntax() {
      return syntax;
    }

    @Override public okio.ByteString unknownFields(M message) {
      return message.unknownFields();
    }

    @Override public int getCachedSerializedSize(M message) {
      return message.cachedSerializedSize;
    }

    @Override public void setCachedSerializedSize(M message, int size) {
      message.cachedSerializedSize = size;
    }

    @Override public B newBuilder() {
      return createBuilder.get();
    }

    @Override public M build(B builder) {
      return builder.build();
    }

    @Override public void addUnknownField(B builder, int tag, FieldEncoding fieldEncoding,
        Object value) {
      builder.addUnknownField(tag, fieldEncoding, value);
    }

    @Override public void clearUnknownFields(B builder) {
      builder.clearUnknownFields();
    }
  }
}
