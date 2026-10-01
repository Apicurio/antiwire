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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import okio.Buffer;
import okio.ByteString;
import java.io.EOFException;
import org.junit.jupiter.api.Test;

/**
 * Upstream commonTest translated (assertk to JUnit 5; Person fixture in TestMessages); see
 * UPSTREAM-TEST-ADAPTATIONS.md.
 */
public class ProtoReaderTest {
  @Test public void packedExposedAsRepeated() throws IOException {
    ByteString packedEncoded = ByteString.decodeHex("d20504d904bd05");
    ProtoReader reader = new ProtoReader(new Buffer().write(packedEncoded));
    long token = reader.beginMessage();
    assertEquals(90, reader.nextTag());
    assertEquals(601, ProtoAdapter.INT32.decode(reader));
    assertEquals(90, reader.nextTag());
    assertEquals(701, ProtoAdapter.INT32.decode(reader));
    assertEquals(-1, reader.nextTag());
    reader.endMessageAndGetUnknownFields(token);
  }

  @Test public void lengthDelimited() throws IOException {
    ByteString encoded = ByteString.decodeHex(
        "02" // varint32 length = 2
            + "0802" // 1: int32 = 2
            + "06" // varint32 length = 6
            + "08ffffffff07" // 1: int32 = 2,147,483,647
    );
    ProtoReader reader = new ProtoReader(new Buffer().write(encoded));

    assertEquals(2, reader.nextLengthDelimited());

    long firstToken = reader.beginMessage();
    assertEquals(1, reader.nextTag());
    assertEquals(2, ProtoAdapter.INT32.decode(reader));
    assertEquals(-1, reader.nextTag());
    reader.endMessageAndGetUnknownFields(firstToken);

    assertEquals(6, reader.nextLengthDelimited());

    long secondToken = reader.beginMessage();
    assertEquals(1, reader.nextTag());
    assertEquals(Integer.MAX_VALUE, ProtoAdapter.INT32.decode(reader));
    assertEquals(-1, reader.nextTag());
    reader.endMessageAndGetUnknownFields(secondToken);
  }

  /** We had a bug where we weren't enforcing recursion limits for groups. */
  @Test public void testSkipGroupNested() {
    byte[] data = new byte[50000];
    for (int i = 0; i < data.length; i++) {
      data[i] = (i % 2 == 0) ? (byte) 0xa3 : 0x01;
    }

    IOException e = assertThrows(IOException.class,
        () -> TestMessages.Person.ADAPTER.decode(new Buffer().write(data)));
    assertEquals("Wire recursion limit exceeded", e.getMessage());
  }

  /** We had a bug where negative lengths in skipped groups crashed with runtime exceptions. */
  @Test public void testSkipGroupRejectsNegativeLength() {
    ByteString data = ByteString.decodeHex("9b060a80ffffff0f9c06");

    IOException e = assertThrows(IOException.class,
        () -> TestMessages.Person.ADAPTER.decode(new Buffer().write(data)));
    assertEquals("Negative length: -128. Reader position: 8. Last read tag: 1.", e.getMessage());
  }

  @Test public void fixed32CannotReadPastLengthDelimitedLimit() throws IOException {
    ByteString encoded = ByteString.decodeHex(
        "02" // varint32 length = 2
            + "0d" // 1: fixed32
            + "05000000" // enough bytes in the source, but not in the current message
    );
    ProtoReader reader = new ProtoReader(new Buffer().write(encoded));

    assertEquals(2, reader.nextLengthDelimited());
    reader.beginMessage();
    assertEquals(1, reader.nextTag());
    assertThrows(EOFException.class, reader::readFixed32);
  }
}
