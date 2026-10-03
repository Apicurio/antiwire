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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.squareup.wire.internal.Reflection;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Phase 2 of the no-okio public API (docs/api-surface.md): the reflection machinery drives a
 * hand-written sample of what the port's generator now emits. A message with a {@link Bytes}
 * field whose {@code @WireField} adapter string names {@code ProtoAdapter#WIRE_BYTES}, built
 * through the canonical {@code Message(ProtoAdapter, Bytes)} constructor and
 * {@code buildUnknownFieldsBytes()}, must decode, encode, and carry unknown fields exactly as
 * the okio-typed fixtures always have: the wire bytes do not change, only the Java value type.
 */
public class WireBytesReflectionTest {

  /** The generated shape the port's JavaGenerator emits for a bytes field (phase 2). */
  public static class BlobMessage extends Message<BlobMessage, BlobMessage.Builder> {
    @WireField(
        tag = 1,
        adapter = "com.squareup.wire.ProtoAdapter#WIRE_BYTES"
    )
    public final Bytes blob;

    @WireField(
        tag = 2,
        adapter = "com.squareup.wire.ProtoAdapter#WIRE_BYTES",
        label = WireField.Label.REPEATED
    )
    public final List<Bytes> chunks;

    public BlobMessage(Bytes blob, List<Bytes> chunks, Bytes unknownFields) {
      super(ADAPTER, unknownFields);
      this.blob = blob;
      this.chunks = chunks;
    }

    @Override public Builder newBuilder() {
      Builder builder = new Builder();
      builder.blob = blob;
      builder.chunks.addAll(chunks);
      builder.addUnknownFields(unknownFieldsBytes());
      return builder;
    }

    @SuppressWarnings("unchecked")
    public static final ProtoAdapter<BlobMessage> ADAPTER =
        (ProtoAdapter<BlobMessage>) (ProtoAdapter<?>) Reflection.createRuntimeMessageAdapter(
            BlobMessage.class, null, Syntax.PROTO_2);

    public static final class Builder extends Message.Builder<BlobMessage, Builder> {
      public Bytes blob;
      public List<Bytes> chunks = new ArrayList<>();

      public Builder blob(Bytes value) {
        this.blob = value;
        return this;
      }

      public Builder chunks(Bytes... values) {
        this.chunks.addAll(Arrays.asList(values));
        return this;
      }

      @Override public BlobMessage build() {
        if (blob == null) throw new IllegalStateException("blob == null");
        return new BlobMessage(blob, chunks, buildUnknownFieldsBytes());
      }
    }
  }

  @Test public void adapterStringResolvesTheWireBytesConstant() {
    assertEquals(ProtoAdapter.WIRE_BYTES, ProtoAdapter.get("com.squareup.wire.ProtoAdapter#WIRE_BYTES"));
    assertEquals(ProtoAdapter.WIRE_BYTES_VALUE,
        ProtoAdapter.get("com.squareup.wire.ProtoAdapter#WIRE_BYTES_VALUE"));
  }

  @Test public void roundTripThroughTheReflectionPath() throws IOException {
    BlobMessage message = new BlobMessage.Builder()
        .blob(Bytes.encodeUtf8("bytes"))
        .chunks(Bytes.encodeUtf8("a"), Bytes.encodeUtf8("b"))
        .build();

    byte[] encoded = BlobMessage.ADAPTER.encode(message);
    assertEquals(encoded.length, BlobMessage.ADAPTER.encodedSize(message));

    // The same wire bytes an okio-typed message of this shape has always produced: tag 1,
    // length 5, "bytes", then tag 2 twice for the repeated field.
    byte[] expected = {
        0x0a, 0x05, 'b', 'y', 't', 'e', 's',
        0x12, 0x01, 'a',
        0x12, 0x01, 'b',
    };
    assertArrayEquals(expected, encoded);

    BlobMessage decoded = BlobMessage.ADAPTER.decode(encoded);
    assertEquals(message.blob, decoded.blob);
    assertEquals(message.chunks, decoded.chunks);
    assertEquals(Bytes.EMPTY, decoded.unknownFieldsBytes());
    assertArrayEquals(encoded, BlobMessage.ADAPTER.encode(decoded));
  }

  @Test public void unknownFieldsSurviveTheReflectionPath() throws IOException {
    // Tag 1 carries the known blob; tag 15 is an unknown length-delimited field, which the
    // reflection machinery decodes and re-encodes through FieldEncoding.rawProtoAdapter():
    // LENGTH_DELIMITED resolves to WIRE_BYTES, so Bytes values flow on both sides of that
    // round-trip (the phase-1 value-typed leak, closed).
    byte[] encoded = {
        0x0a, 0x02, 'o', 'k',
        0x7a, 0x03, 0x01, 0x02, 0x03,
    };

    BlobMessage decoded = BlobMessage.ADAPTER.decode(encoded);
    assertEquals(Bytes.encodeUtf8("ok"), decoded.blob);
    // The unknown field's encoding is tag 15, length 3, three payload bytes: five bytes.
    assertEquals(5, decoded.unknownFieldsBytes().size());

    byte[] reencoded = BlobMessage.ADAPTER.encode(decoded);
    assertArrayEquals(encoded, reencoded);

    BlobMessage rebuilt = decoded.newBuilder().build();
    assertArrayEquals(encoded, BlobMessage.ADAPTER.encode(rebuilt));
  }
}
