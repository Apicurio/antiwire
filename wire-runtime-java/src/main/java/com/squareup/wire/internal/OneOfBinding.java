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

import com.squareup.wire.Message;
import com.squareup.wire.OneOf;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.WireField;
import java.lang.reflect.Field;

/** Upstream Kotlin-internal; public here per the translation conventions. */
public final class OneOfBinding<M extends Message<M, B>, B extends Message.Builder<M, B>>
    extends FieldOrOneOfBinding<M, B> {
  private final Field messageField;
  private final Field builderField;
  private final OneOf.Key<?> key;
  private final boolean writeIdentityValues;

  public OneOfBinding(Field messageField, Class<B> builderType, OneOf.Key<?> key,
      boolean writeIdentityValues) {
    this.messageField = messageField;
    this.key = key;
    this.writeIdentityValues = writeIdentityValues;
    try {
      this.builderField = builderType.getDeclaredField(messageField.getName());
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  @Override public int tag() {
    return key.tag;
  }

  @Override public WireField.Label label() {
    return WireField.Label.OPTIONAL;
  }

  @Override public boolean redacted() {
    return key.redacted;
  }

  @Override public String wireFieldJsonName() {
    return key.jsonName;
  }

  @Override public String name() {
    return key.declaredName;
  }

  @Override public String declaredName() {
    return key.declaredName;
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

  @SuppressWarnings("unchecked")
  @Override public ProtoAdapter<?> singleAdapter() {
    return (ProtoAdapter<Object>) key.adapter;
  }

  @Override public boolean writeIdentityValues() {
    return writeIdentityValues;
  }

  @Override public void value(B builder, Object value) {
    set(builder, value);
  }

  @SuppressWarnings("unchecked")
  @Override public void set(B builder, Object value) {
    try {
      builderField.set(builder, new OneOf<>((OneOf.Key<Object>) key, value));
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override public Object get(M message) {
    OneOf<?, ?> oneOfOrNull;
    try {
      oneOfOrNull = (OneOf<?, ?>) messageField.get(message);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    return oneOfOrNull != null ? oneOfOrNull.getOrNull((OneOf.Key) key) : null;
  }

  @Override public Object getFromBuilder(B builder) {
    OneOf<?, ?> oneOfOrNull;
    try {
      oneOfOrNull = (OneOf<?, ?>) builderField.get(builder);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    return oneOfOrNull != null ? oneOfOrNull.getOrNull((OneOf.Key) key) : null;
  }
}
