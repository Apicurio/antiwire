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

  public final String typeUrl;
  public final ByteString value;

  public AnyMessage(String typeUrl, ByteString value) {
    super(ADAPTER, ByteString.EMPTY);
    this.typeUrl = typeUrl;
    this.value = value;
  }

  public <T> T unpack(ProtoAdapter<T> adapter) throws IOException {
    if (!typeUrl.equals(adapter.typeUrl)) {
      throw new IllegalStateException("type mismatch: " + typeUrl + " != " + adapter.typeUrl);
    }
    return adapter.decode(value);
  }

  public <T> T unpackOrNull(ProtoAdapter<T> adapter) throws IOException {
    return typeUrl.equals(adapter.typeUrl) ? adapter.decode(value) : null;
  }

  /**
   * Packs a generated {@link Message} using its built-in adapter. Requires the ADAPTER field
   * lookup owned by TASK-7's reflection registry; the explicit-adapter overload serves callers
   * meanwhile.
   */
  public static AnyMessage pack(Message<?, ?> message) throws IOException {
    throw new UnsupportedOperationException(
        "pack(Message) requires the reflection registry owned by TASK-7; use "
            + "pack(ProtoAdapter, T) meanwhile");
  }

  /**
   * Packs {@code value} using an explicit {@code adapter}, for types that aren't Message
   * subtypes, such as well-known types like FieldMask.
   */
  public static <T> AnyMessage pack(ProtoAdapter<T> adapter, T value) throws IOException {
    if (adapter.typeUrl == null) {
      throw new IllegalStateException(
          "cannot pack " + (adapter.type == null ? "value" : adapter.type.getName())
              + ": the adapter has no type URL");
    }
    return new AnyMessage(adapter.typeUrl, adapter.encodeByteString(value));
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

  public AnyMessage copy(String typeUrl, ByteString value) {
    return new AnyMessage(typeUrl, value);
  }

  public AnyMessage copy() {
    return copy(typeUrl, value);
  }

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
}
