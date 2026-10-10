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
package com.squareup.wire;

import com.squareup.wire.internal.Internal;
import java.lang.reflect.Method;

/** Converts values of an enum to and from integers using reflection. */
public final class RuntimeEnumAdapter<E extends WireEnum> extends EnumAdapter<E> {
  private final Class<E> javaType;
  private volatile Method fromValueMethod; // Lazy to avoid reflection during class loading.

  public RuntimeEnumAdapter(Class<E> javaType, Syntax syntax) {
    super(javaType, syntax, Internal.identityOrNull(javaType));
    this.javaType = javaType;
  }

  /** Obsolete; for Java classes generated before syntax was added. */
  public RuntimeEnumAdapter(Class<E> javaType) {
    this(javaType, Syntax.PROTO_2);
  }

  private Method getFromValueMethod() throws NoSuchMethodException {
    Method result = fromValueMethod;
    if (result == null) {
      synchronized (this) {
        result = fromValueMethod;
        if (result == null) {
          result = javaType.getMethod("fromValue", int.class);
          fromValueMethod = result;
        }
      }
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  @Override protected E fromValue(int value) {
    try {
      return (E) getFromValueMethod().invoke(null, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override public boolean equals(Object other) {
    return other instanceof RuntimeEnumAdapter && ((RuntimeEnumAdapter<?>) other).type == type;
  }

  @Override public int hashCode() {
    return type.hashCode();
  }

  public static <E extends WireEnum> RuntimeEnumAdapter<E> create(Class<E> enumType) {
    ProtoAdapter<?> defaultAdapter = ProtoAdapter.get(enumType);
    return new RuntimeEnumAdapter<>(enumType, defaultAdapter.getSyntax());
  }

  /** Mirror of the Kotlin companion object: lets Java callers write {@code RuntimeEnumAdapter.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }

    public <E extends WireEnum> RuntimeEnumAdapter<E> create(Class<E> enumType) {
      return RuntimeEnumAdapter.create(enumType);
    }
  }
}
