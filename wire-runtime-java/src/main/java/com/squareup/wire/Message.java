/*
 * Copyright (C) 2013 Square, Inc.
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
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.io.Serializable;
import okio.Buffer;
import okio.BufferedSink;
import okio.ByteString;

/** A protocol buffer message. */
public abstract class Message<M extends Message<M, B>, B extends Message.Builder<M, B>>
    implements Serializable {
  private static final long serialVersionUID = 0L;

  /** The {@link ProtoAdapter} for encoding and decoding messages of this type. */
  final transient ProtoAdapter<M> adapter;

  /** The {@link ProtoAdapter} for encoding and decoding messages of this type. */
  public ProtoAdapter<M> adapter() {
    return adapter;
  }

  private transient ByteString unknownFieldsMemoized;

  /** If not {@code 0} then the serialized size of this message. Internal cache; do not mutate. */
  transient int cachedSerializedSize;

  /**
   * Internal serialized-size cache, read and written by the reflection machinery. Upstream
   * reaches it through Kotlin internal visibility (bytecode public there); calling this as a
   * consumer corrupts every later encodedSize result. Do not use.
   */
  public int getCachedSerializedSizeForReflection() {
    return cachedSerializedSize;
  }

  /** Internal; see {@link #getCachedSerializedSizeForReflection()}. Do not use. */
  public void setCachedSerializedSizeForReflection(int size) {
    cachedSerializedSize = size;
  }

  /** If non-zero, the hash code of this message. Accessed by generated code. */
  protected transient int hashCode = 0;

  /**
   * @deprecated Engine/compat layer: the okio-typed constructor. okio remains internal to the
   *     port (docs/api-surface.md); generated code and the reflection machinery keep calling
   *     it, and its behavior is unchanged.
   */
  @Deprecated protected Message(ProtoAdapter<M> adapter, ByteString unknownFields) {
    this.adapter = adapter;
    this.unknownFieldsMemoized = unknownFields;
  }

  /**
   * Returns a byte string containing the proto encoding of this message's unknown fields.
   * Returns an empty byte string if this message has no unknown fields.
   *
   * @deprecated Engine/compat layer: the okio-typed form. okio remains internal to the port
   *     (docs/api-surface.md); prefer {@link #unknownFieldsBytes()}.
   */
  @Deprecated public ByteString unknownFields() {
    // Some naughty libraries construct Messages by reflection which causes this non-null field
    // to have a null value. We defend against this with an otherwise-redundant null check.
    ByteString fields = unknownFieldsMemoized;
    return fields != null ? fields : ByteString.EMPTY;
  }

  /**
   * Returns a {@link Bytes} containing the proto encoding of this message's unknown fields.
   * Returns an empty {@code Bytes} if this message has no unknown fields. Copies out of the
   * internal okio memo once per call.
   */
  public Bytes unknownFieldsBytes() {
    return Bytes.fromByteString(unknownFields());
  }

  /** Returns a new builder initialized with the data in this message. */
  public abstract B newBuilder();

  /** Returns this message with any unknown fields removed. */
  public M withoutUnknownFields() {
    return newBuilder().clearUnknownFields().build();
  }

  @Override public String toString() {
    return adapter.toString((M) this);
  }

  @SuppressWarnings("unchecked")
  protected Object writeReplace() throws ObjectStreamException {
    try {
      return new MessageSerializedForm(encode(), (Class<M>) getClass());
    } catch (java.io.IOException e) {
      // Kotlin propagates IOException here unchecked; the closest Java form for a failing
      // serialization substitute is InvalidObjectException.
      throw new java.io.InvalidObjectException(e.getMessage());
    }
  }

  /**
   * Encode this message and write it to {@code sink}.
   *
   * @deprecated Engine/compat layer: the okio-typed form. okio remains internal to the port
   *     (docs/api-surface.md); prefer {@link #encode(OutputStream)}.
   */
  @Deprecated public void encode(BufferedSink sink) throws IOException {
    adapter.encode(sink, (M) this);
  }

  /** Encode this message as a {@code byte[]}. */
  public byte[] encode() throws IOException {
    return adapter.encode((M) this);
  }

  /**
   * Encode this message as a {@link ByteString}.
   *
   * @deprecated Engine/compat layer: the okio-typed form. okio remains internal to the port
   *     (docs/api-surface.md); prefer {@link #encodeToBytes()} or {@link #encode()}.
   */
  @Deprecated public ByteString encodeByteString() throws IOException {
    return adapter.encodeByteString((M) this);
  }

  /** Encode this message as a {@link Bytes}. */
  public Bytes encodeToBytes() throws IOException {
    return adapter.encodeToBytes((M) this);
  }

  /** Encode this message and write it to {@code stream}. */
  public void encode(OutputStream stream) throws IOException {
    adapter.encode(stream, (M) this);
  }

  /**
   * Superclass for protocol buffer message builders.
   */
  public abstract static class Builder<M extends Message<M, B>, B extends Builder<M, B>> {
    transient ByteString unknownFieldsByteString = ByteString.EMPTY;
    transient Buffer unknownFieldsBuffer;
    transient ProtoWriter unknownFieldsWriter;

    protected Builder() {
    }

    /**
     * @deprecated Engine/compat layer: the okio-typed form. okio remains internal to the port
     *     (docs/api-surface.md); prefer {@link #addUnknownFields(Bytes)}.
     */
    @Deprecated public B addUnknownFields(ByteString unknownFields) {
      if (unknownFields.size() > 0) {
        prepareForNewUnknownFields();
        try {
          unknownFieldsWriter.writeBytes(unknownFields);
        } catch (IOException e) {
          // In-memory buffer writes cannot fail; Kotlin surfaces nothing here.
          throw new java.io.UncheckedIOException(e);
        }
      }
      return (B) this;
    }

    /** Adds {@code unknownFields} to the unknown fields this builder will build. */
    public B addUnknownFields(Bytes unknownFields) {
      return addUnknownFields(unknownFields.toByteString());
    }

    public B addUnknownField(int tag, FieldEncoding fieldEncoding, Object value) {
      prepareForNewUnknownFields();
      @SuppressWarnings("unchecked")
      ProtoAdapter<Object> protoAdapter = (ProtoAdapter<Object>) fieldEncoding.rawProtoAdapter();
      try {
        protoAdapter.encodeWithTag(unknownFieldsWriter, tag, value);
      } catch (IOException e) {
        throw new java.io.UncheckedIOException(e);
      }
      return (B) this;
    }

    public B clearUnknownFields() {
      unknownFieldsByteString = ByteString.EMPTY;
      if (unknownFieldsBuffer != null) {
        unknownFieldsBuffer.clear();
        unknownFieldsBuffer = null;
      }
      unknownFieldsWriter = null;
      return (B) this;
    }

    /**
     * Returns a byte string with this message's unknown fields. Returns an empty byte string
     * if this message has no unknown fields.
     *
     * @deprecated Engine/compat layer: the okio-typed form. okio remains internal to the port
     *     (docs/api-surface.md); prefer {@link #buildUnknownFieldsBytes()}.
     */
    @Deprecated public ByteString buildUnknownFields() {
      if (unknownFieldsBuffer != null) {
        // Reads and caches the unknown fields from the buffer.
        unknownFieldsByteString = unknownFieldsBuffer.readByteString();
        unknownFieldsBuffer = null;
        unknownFieldsWriter = null;
      }
      return unknownFieldsByteString;
    }

    /**
     * Returns a {@link Bytes} with this message's unknown fields. Returns an empty
     * {@code Bytes} if this message has no unknown fields.
     */
    public Bytes buildUnknownFieldsBytes() {
      return Bytes.fromByteString(buildUnknownFields());
    }

    /** Returns an immutable {@link Message} based on the fields that set in this builder. */
    public abstract M build();

    private void prepareForNewUnknownFields() {
      if (unknownFieldsBuffer == null) {
        unknownFieldsBuffer = new Buffer();
        unknownFieldsWriter = new ProtoWriter(unknownFieldsBuffer);
        try {
          // Writes the cached unknown fields to the buffer.
          unknownFieldsWriter.writeBytes(unknownFieldsByteString);
        } catch (IOException e) {
          throw new java.io.UncheckedIOException(e);
        }
        unknownFieldsByteString = ByteString.EMPTY;
      }
    }
  }
}
