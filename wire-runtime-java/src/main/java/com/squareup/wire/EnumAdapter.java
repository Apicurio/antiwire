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
import java.io.IOException;

/**
 * An abstract {@link ProtoAdapter} that converts values of an enum to and from integers. The
 * Kotlin KClass constructor parameter becomes {@link Class} in this port (ownership map API
 * mapping); the obsolete overloads generated classes used before syntax and identity existed
 * are kept.
 */
public abstract class EnumAdapter<E extends WireEnum> extends ProtoAdapter<E> {
  public EnumAdapter(Class<E> type, Syntax syntax, E identity) {
    super(FieldEncoding.VARINT, type, null, syntax, identity, null);
  }

  /** Obsolete; for Java classes generated before identity was added. */
  public EnumAdapter(Class<E> type, Syntax syntax) {
    this(type, syntax, Internal.getIdentityOrNull(type));
  }

  /** Obsolete; for Java classes generated before syntax was added. */
  public EnumAdapter(Class<E> type) {
    this(type, Syntax.PROTO_2, Internal.getIdentityOrNull(type));
  }

  @Override public int encodedSize(E value) {
    return ProtoWriter.varint32Size(value.getValue());
  }

  @Override public void encode(ProtoWriter writer, E value) throws IOException {
    writer.writeVarint32(value.getValue());
  }

  @Override public void encode(ReverseProtoWriter writer, E value) throws IOException {
    writer.writeVarint32(value.getValue());
  }

  @Override public E decode(ProtoReader reader) throws IOException {
    int value = reader.readVarint32();
    E result = fromValue(value);
    if (result == null) throw new EnumConstantNotFoundException(value, type);
    return result;
  }

  @Override public E redact(E value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Converts an integer to an enum. Returns null if there is no corresponding enum.
   */
  protected abstract E fromValue(int value);
}
