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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Round trips through the JDK-typed canonical members added in phase 1 (docs/api-surface.md):
 * {@code encodeToBytes}/{@code decode(Bytes)} on adapters, the {@code Bytes} forms of
 * AnyMessage, and the unknown-fields builder chain, all against the engine's byte-array
 * forms so the bridge conversions are proven, not just typed.
 */
public class BytesBridgeTest {
  @Test public void adapterEncodeToBytesAndDecodeBytesRoundTrip() throws IOException {
    TestMessages.Person person = new TestMessages.Person("Grace Hopper", 1906);
    Bytes encoded = TestMessages.Person.ADAPTER.encodeToBytes(person);
    assertArrayEquals(TestMessages.Person.ADAPTER.encode(person), encoded.toByteArray());
    assertEquals(person, TestMessages.Person.ADAPTER.decode(encoded));
  }

  @Test public void anyMessageBytesFormsMatchTheEngineForms() throws IOException {
    AnyMessage packed = AnyMessage.pack(TestMessages.Person.ADAPTER,
        new TestMessages.Person("Grace Hopper", 1906));
    AnyMessage fromBytes = new AnyMessage(packed.typeUrl, packed.valueBytes());
    assertEquals(packed, fromBytes);
    assertEquals(packed.valueBytes(), fromBytes.valueBytes());
    assertEquals(fromBytes, fromBytes.copy(fromBytes.typeUrl, fromBytes.valueBytes()));
    assertEquals(packed.unpack(TestMessages.Person.ADAPTER),
        fromBytes.unpack(TestMessages.Person.ADAPTER));
  }

  @Test public void messageEncodeToBytesMatchesByteArrayEncode() throws IOException {
    AnyMessage any = new AnyMessage("type.googleapis.com/Person", Bytes.encodeUtf8("payload"));
    assertArrayEquals(any.encode(), any.encodeToBytes().toByteArray());
    assertEquals(Bytes.EMPTY, any.unknownFieldsBytes());
  }

  @Test public void unknownFieldsBuilderChainCarriesBytes() {
    EmptyMessage message = new EmptyMessage.Builder()
        .addUnknownFields(Bytes.decodeHex("0802"))
        .build();
    assertEquals(Bytes.decodeHex("0802"), message.unknownFieldsBytes());
  }

  /** Minimal concrete Message so the unknown-fields builder chain has a real build(). */
  private static final class EmptyMessage
      extends Message<EmptyMessage, EmptyMessage.Builder> {
    static final ProtoAdapter<EmptyMessage> ADAPTER = new ProtoAdapter<EmptyMessage>(
        FieldEncoding.LENGTH_DELIMITED, EmptyMessage.class, null, Syntax.PROTO_2, null, null) {
      @Override public EmptyMessage redact(EmptyMessage value) {
        throw new UnsupportedOperationException();
      }

      @Override public int encodedSize(EmptyMessage value) {
        return 0;
      }

      @Override public void encode(ProtoWriter writer, EmptyMessage value) {
      }

      @Override public EmptyMessage decode(ProtoReader reader) throws IOException {
        long token = reader.beginMessage();
        int tag;
        while ((tag = reader.nextTag()) != -1) {
          reader.readUnknownField(tag);
        }
        reader.endMessageAndGetUnknownFields(token);
        return new EmptyMessage.Builder().build();
      }
    };

    @SuppressWarnings("deprecation") // The okio ctor is the engine form (docs/api-surface.md).
    EmptyMessage(okio.ByteString unknownFields) {
      super(ADAPTER, unknownFields);
    }

    @Override public Builder newBuilder() {
      return new Builder();
    }

    static final class Builder extends Message.Builder<EmptyMessage, Builder> {
      @SuppressWarnings("deprecation") // The okio ctor is the engine form (docs/api-surface.md).
      @Override public EmptyMessage build() {
        return new EmptyMessage(buildUnknownFields());
      }
    }
  }
}
