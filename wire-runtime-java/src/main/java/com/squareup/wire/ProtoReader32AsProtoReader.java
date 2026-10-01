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

import java.io.IOException;
import okio.Buffer;
import okio.ByteString;

/**
 * Adapts a [ProtoReader32] as a [ProtoReader] so that [ProtoAdapter] implementations that don't
 * have generated code for [ProtoReader32] forms can still be used with that type.
 *
 * <p>This code is fragile because [ProtoReader] was not designed to be subclassed this way. In
 * particular none of the state variables in the supertype [ProtoReader] are used in any way.
 * Upstream declares this Kotlin-internal; it is public here per the translation conventions.
 */
public final class ProtoReader32AsProtoReader extends ProtoReader {
  public final ProtoReader32 delegate;

  public ProtoReader32AsProtoReader(ProtoReader32 delegate) {
    super(new Buffer());
    this.delegate = delegate;
  }

  @Override public long beginMessage() throws IOException {
    return delegate.beginMessage();
  }

  @Override public ByteString endMessageAndGetUnknownFields(long token) throws IOException {
    return delegate.endMessageAndGetUnknownFields((int) token);
  }

  @Override public int nextLengthDelimited() throws IOException {
    return delegate.nextLengthDelimited();
  }

  @Override public int nextTag() throws IOException {
    return delegate.nextTag();
  }

  @Override public FieldEncoding peekFieldEncoding() {
    return delegate.peekFieldEncoding();
  }

  @Override public void skip() throws IOException {
    delegate.skip();
  }

  @Override public ByteString readBytes() throws IOException {
    return delegate.readBytes();
  }

  @Override public boolean beforePossiblyPackedScalar() throws IOException {
    return delegate.beforePossiblyPackedScalar();
  }

  @Override public String readString() throws IOException {
    return delegate.readString();
  }

  @Override public int readVarint32() throws IOException {
    return delegate.readVarint32();
  }

  @Override public long readVarint64() throws IOException {
    return delegate.readVarint64();
  }

  @Override public int readFixed32() throws IOException {
    return delegate.readFixed32();
  }

  @Override public long readFixed64() throws IOException {
    return delegate.readFixed64();
  }

  @Override public void readUnknownField(int tag) throws IOException {
    delegate.readUnknownField(tag);
  }

  @Override public void addUnknownField(int tag, FieldEncoding fieldEncoding, Object value)
      throws IOException {
    delegate.addUnknownField(tag, fieldEncoding, value);
  }

  @Override public long nextFieldMinLengthInBytes() throws java.io.EOFException {
    try {
      return delegate.nextFieldMinLengthInBytes();
    } catch (IOException e) {
      // The interface declares the broader IOException; the supertype narrows to EOFException,
      // which is the only failure remainingInLimit can raise here.
      if (e instanceof java.io.EOFException) throw (java.io.EOFException) e;
      throw new java.io.UncheckedIOException(e);
    }
  }
}
