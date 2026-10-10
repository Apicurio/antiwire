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
package com.squareup.wire;

import java.io.IOException;
import okio.ByteString;

/**
 * Wire implementation of the {@code google.protobuf.Any} type: wraps an arbitrary protobuf
 * message, with {@link #typeUrl} naming the wrapped type and {@link #value} holding its
 * serialized form. Use {@link #pack} and {@link #unpack} to wrap and unwrap.
 */
public final class AnyMessage extends Message<AnyMessage, AnyMessage.NoBuilder> {
  /** Stands in for upstream's Nothing builder parameter; never instantiated. */
  static abstract class NoBuilder extends Builder<AnyMessage, NoBuilder> {
    protected NoBuilder() {
      throw new AssertionError("AnyMessage has no builder");
    }
  }

  public static final ProtoAdapter<AnyMessage> ADAPTER = new AnyMessageAdapter();

  private final String typeUrl;

  public String getTypeUrl() {
    return typeUrl;
  }

  /**
   * The serialized form of the wrapped value.
   *
   * @deprecated Engine/compat layer: the okio-typed field. okio remains internal to the port
   *     (docs/api-surface.md); prefer {@link #valueBytes()}.
   */
  private final ByteString value;

  /**
   * The serialized form of the wrapped value.
   *
   * @deprecated Engine/compat layer: the okio-typed accessor. okio remains internal to the port
   *     (docs/api-surface.md); prefer {@link #valueBytes()}.
   */
  @Deprecated public ByteString getValue() {
    return value;
  }

  /**
   * @deprecated Engine/compat layer: the okio-typed constructor. okio remains internal to the
   *     port (docs/api-surface.md); prefer {@link #AnyMessage(String, Bytes)}.
   */
  @Deprecated public AnyMessage(String typeUrl, ByteString value) {
    super(ADAPTER, ByteString.EMPTY); // Deprecated bridge: Message's okio ctor is the engine form.
    this.typeUrl = typeUrl;
    this.value = value;
  }

  /**
   * Builds an Any from the wire-owned bytes form. Unlike the deprecated okio constructor,
   * which defers a null {@code value} failure to first use, a null {@code value} fails here
   * at construction.
   */
  @SuppressWarnings("deprecation") // Message's okio ctor is the engine form (docs/api-surface.md).
  public AnyMessage(String typeUrl, Bytes value) {
    super(ADAPTER, ByteString.EMPTY);
    this.typeUrl = typeUrl;
    this.value = value.toByteString();
  }

  /** Returns the serialized form of the wrapped value as a {@link Bytes}. */
  public Bytes valueBytes() {
    return Bytes.fromByteString(value);
  }

  public <T> T unpack(ProtoAdapter<T> adapter) throws IOException {
    if (!typeUrl.equals(adapter.getTypeUrl())) {
      throw new IllegalStateException("type mismatch: " + typeUrl + " != " + adapter.getTypeUrl());
    }
    return adapter.decode(value.toByteArray());
  }

  public <T> T unpackOrNull(ProtoAdapter<T> adapter) throws IOException {
    return typeUrl.equals(adapter.getTypeUrl()) ? adapter.decode(value.toByteArray()) : null;
  }

  /** Packs a generated {@link Message} using its built-in adapter. */
  @SuppressWarnings("deprecation") // Engine layer: one-copy okio encode feeds the okio field.
  public static AnyMessage pack(Message<?, ?> message) throws IOException {
    ProtoAdapter<Object> adapter = (ProtoAdapter<Object>) message.adapter();
    if (adapter.getTypeUrl() == null) {
      throw new IllegalStateException(
          "recompile " + adapter.type.getName() + " to use it with AnyMessage");
    }
    return new AnyMessage(adapter.getTypeUrl(), adapter.encodeByteString((Object) message));
  }

  /**
   * Packs {@code value} using an explicit {@code adapter}, for types that aren't Message
   * subtypes, such as well-known types like FieldMask.
   */
  @SuppressWarnings("deprecation") // Engine layer: one-copy okio encode feeds the okio field.
  public static <T> AnyMessage pack(ProtoAdapter<T> adapter, T value) throws IOException {
    if (adapter.getTypeUrl() == null) {
      throw new IllegalStateException(
          "cannot pack " + (adapter.type == null ? "value" : adapter.type.getName())
              + ": the adapter has no type URL");
    }
    return new AnyMessage(adapter.getTypeUrl(), adapter.encodeByteString(value));
  }

  @Override public NoBuilder newBuilder() {
    throw new AssertionError();
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof AnyMessage)) return false;
    AnyMessage that = (AnyMessage) other;
    return typeUrl.equals(that.typeUrl) && value.equals(that.value);
  }

  @Override public int hashCode() {
    int result = super.hashCode;
    if (result == 0) {
      result = result * 37 + typeUrl.hashCode();
      result = result * 37 + value.hashCode();
      super.hashCode = result;
    }
    return result;
  }

  @Override public String toString() {
    return "Any{type_url=" + typeUrl + ", value=" + value + "}";
  }

  /**
   * @deprecated Engine/compat layer: the okio-typed form. okio remains internal to the port
   *     (docs/api-surface.md); prefer {@link #copy(String, Bytes)}.
   */
  @Deprecated public AnyMessage copy(String typeUrl, ByteString value) {
    return new AnyMessage(typeUrl, value);
  }

  public AnyMessage copy(String typeUrl, Bytes value) {
    return new AnyMessage(typeUrl, value);
  }

  public AnyMessage copy() {
    return copy(typeUrl, value);
  }

  @SuppressWarnings("deprecation") // Engine layer: the Any payload encodes through ProtoAdapter.BYTES.
  private static final class AnyMessageAdapter extends ProtoAdapter<AnyMessage> {
    AnyMessageAdapter() {
      super(FieldEncoding.LENGTH_DELIMITED, AnyMessage.class,
          "type.googleapis.com/google.protobuf.Any", Syntax.PROTO_3, null, null);
    }

    @Override public int encodedSize(AnyMessage value) {
      return STRING.encodedSizeWithTag(1, value.typeUrl)
          + BYTES.encodedSizeWithTag(2, value.value);
    }

    @Override public void encode(ProtoWriter writer, AnyMessage value) throws IOException {
      STRING.encodeWithTag(writer, 1, value.typeUrl);
      BYTES.encodeWithTag(writer, 2, value.value);
    }

    @Override public void encode(ReverseProtoWriter writer, AnyMessage value) throws IOException {
      BYTES.encodeWithTag(writer, 2, value.value);
      STRING.encodeWithTag(writer, 1, value.typeUrl);
    }

    @Override public AnyMessage decode(ProtoReader reader) throws IOException {
      String typeUrl = "";
      ByteString value = ByteString.EMPTY;
      long token = reader.beginMessage();
      int tag;
      while ((tag = reader.nextTag()) != -1) {
        switch (tag) {
          case 1:
            typeUrl = STRING.decode(reader);
            break;
          case 2:
            value = BYTES.decode(reader);
            break;
          default:
            reader.readUnknownField(tag);
        }
      }
      reader.endMessageAndGetUnknownFields(token);
      return new AnyMessage(typeUrl, value);
    }

    // TODO: this is a hazard.
    @Override public AnyMessage redact(AnyMessage value) {
      return new AnyMessage("square.github.io/wire/redacted", ByteString.EMPTY);
    }
  }

  /** Kotlin companion mirror: Java may write {@code AnyMessage.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }

    public AnyMessage pack(Message<?, ?> message) throws IOException {
      return AnyMessage.pack(message);
    }

    public <T> AnyMessage pack(ProtoAdapter<T> adapter, T value) throws IOException {
      return AnyMessage.pack(adapter, value);
    }
  }
}
