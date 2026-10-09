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
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import org.junit.Test;

/**
 * The phase-2 bytes adapter constants (docs/api-surface.md): {@code WIRE_BYTES} and
 * {@code WIRE_BYTES_VALUE} produce the same wire bytes as their deprecated okio-valued twins
 * {@code BYTES} and {@code BYTES_VALUE}; only the Java value type differs.
 */
@SuppressWarnings("deprecation") // The okio twins are the comparison oracle by design.
public class WireBytesAdapterTest {
  @Test public void wireBytesEncodesLikeTheOkioBytes() throws IOException {
    assertArrayEquals(
        ProtoAdapter.BYTES.encode(okio.ByteString.encodeUtf8("hello")),
        ProtoAdapter.WIRE_BYTES.encode(Bytes.encodeUtf8("hello")));
    assertEquals(
        ProtoAdapter.BYTES.encodedSize(okio.ByteString.encodeUtf8("hello")),
        ProtoAdapter.WIRE_BYTES.encodedSize(Bytes.encodeUtf8("hello")));
  }

  @Test public void wireBytesDecodesItsOwnEncoding() throws IOException {
    // A scalar adapter's decode reads the length-prefixed value it meets after a field tag
    // inside a message, so the round-trip goes through encodeWithTag and a reader.
    Bytes value = Bytes.decodeHex("00010203ff");
    okio.Buffer buffer = new okio.Buffer();
    ProtoAdapter.WIRE_BYTES.encodeWithTag(new ProtoWriter(buffer), 1, value);

    ProtoReader reader = new ProtoReader(new okio.Buffer().write(buffer.readByteArray()));
    reader.beginMessage();
    assertEquals(1, reader.nextTag());
    Bytes decoded = ProtoAdapter.WIRE_BYTES.decode(reader);

    assertEquals(value, decoded);
    assertEquals(5, decoded.size());
  }

  @Test public void wireBytesIdentityIsEmpty() {
    assertEquals(Bytes.EMPTY, ProtoAdapter.WIRE_BYTES.getIdentity());
  }

  @Test public void wireBytesRedactIsUnsupported() {
    assertThrows(UnsupportedOperationException.class,
        () -> ProtoAdapter.WIRE_BYTES.redact(Bytes.EMPTY));
  }

  @Test public void wireBytesValueWrapsLikeTheOkioBytesValue() throws IOException {
    Bytes payload = Bytes.encodeUtf8("w");

    // A BytesValue field carries its payload in a nested tag-1 message: 0a 01 'w'.
    assertArrayEquals(new byte[] { 0x0a, 0x01, 'w' },
        ProtoAdapter.WIRE_BYTES_VALUE.encode(payload));
    assertEquals(3, ProtoAdapter.WIRE_BYTES_VALUE.encodedSize(payload));

    // Identity payloads encode to nothing, like every wrapper adapter.
    assertEquals(0, ProtoAdapter.WIRE_BYTES_VALUE.encodedSize(Bytes.EMPTY));

    Bytes decoded = ProtoAdapter.WIRE_BYTES_VALUE.decode(new byte[] { 0x0a, 0x01, 'w' });
    assertEquals(payload, decoded);

    // An empty wrapper decodes to the identity.
    assertEquals(Bytes.EMPTY, ProtoAdapter.WIRE_BYTES_VALUE.decode(new byte[0]));
  }

  @Test public void wireBytesEncodesInReverseToo() throws IOException {
    Bytes value = Bytes.encodeUtf8("rev");
    okio.Buffer forward = new okio.Buffer();
    ProtoAdapter.WIRE_BYTES.encodeWithTag(new ProtoWriter(forward), 7, value);

    ReverseProtoWriter reverseWriter = new ReverseProtoWriter();
    ProtoAdapter.WIRE_BYTES.encodeWithTag(reverseWriter, 7, value);
    okio.Buffer reverse = new okio.Buffer();
    reverseWriter.writeTo(reverse);

    assertArrayEquals(forward.readByteArray(), reverse.readByteArray());
  }
}
