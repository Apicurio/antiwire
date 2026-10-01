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

import java.io.EOFException;
import java.io.IOException;
import okio.ByteString;
import org.junit.jupiter.api.Test;

/**
 * Upstream commonTest translated (assertk to JUnit 5; Person fixture in TestMessages; the
 * ProtoReader32 factory becomes ByteArrayProtoReader32); see UPSTREAM-TEST-ADAPTATIONS.md.
 */
public class ProtoReader32Test {
  @Test public void packedExposedAsRepeated() throws IOException {
    ByteString packedEncoded = ByteString.decodeHex("d20504d904bd05");
    ProtoReader32 reader = new ByteArrayProtoReader32(packedEncoded.toByteArray());
    int token = reader.beginMessage();
    assertEquals(90, reader.nextTag());
    assertEquals(601, ProtoAdapter.INT32.decode(reader));
    assertEquals(90, reader.nextTag());
    assertEquals(701, ProtoAdapter.INT32.decode(reader));
    assertEquals(-1, reader.nextTag());
    reader.endMessageAndGetUnknownFields(token);
  }

  @Test public void lengthDelimited() throws IOException {
    byte[] encoded = ByteString.decodeHex(
        "02" // varint32 length = 2
            + "0802" // 1: int32 = 2
            + "06" // varint32 length = 6
            + "08ffffffff07" // 1: int32 = 2,147,483,647
    ).toByteArray();
    ProtoReader32 reader = new ByteArrayProtoReader32(encoded);

    assertEquals(2, reader.nextLengthDelimited());

    int firstToken = reader.beginMessage();
    assertEquals(1, reader.nextTag());
    assertEquals(2, ProtoAdapter.INT32.decode(reader));
    assertEquals(-1, reader.nextTag());
    reader.endMessageAndGetUnknownFields(firstToken);

    assertEquals(6, reader.nextLengthDelimited());

    int secondToken = reader.beginMessage();
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
        () -> TestMessages.Person.ADAPTER.decode(data));
    assertEquals("Wire recursion limit exceeded", e.getMessage());
  }

  /** We had a bug where negative lengths in skipped groups crashed with runtime exceptions. */
  @Test public void testSkipGroupRejectsNegativeLength() {
    byte[] data = ByteString.decodeHex("9b060a80ffffff0f9c06").toByteArray();

    IOException e = assertThrows(IOException.class,
        () -> TestMessages.Person.ADAPTER.decode(data));
    assertEquals("Negative length: -128. Reader position: 8. Last read tag: 1.", e.getMessage());
  }

  /** We had a bug where positive lengths near Int.MAX_VALUE overflowed cursor math. */
  @Test public void lengthDelimitedRejectsPositiveLengthOverflow() {
    byte[] knownString = ByteString.decodeHex("0affffffff07").toByteArray();
    byte[] unknownBytes = ByteString.decodeHex("1affffffff07").toByteArray();
    byte[] skippedGroup = ByteString.decodeHex("0b0affffffff070c").toByteArray();

    assertThrows(IOException.class, () -> TestMessages.Person.ADAPTER.decode(knownString));
    assertThrows(IOException.class, () -> TestMessages.Person.ADAPTER.decode(unknownBytes));
    assertThrows(IOException.class, () -> TestMessages.Person.ADAPTER.decode(skippedGroup));
  }

  @Test public void fixed32CannotReadPastLengthDelimitedLimit() throws IOException {
    byte[] encoded = ByteString.decodeHex(
        "02" // varint32 length = 2
            + "0d" // 1: fixed32
            + "05000000" // enough bytes in the source, but not in the current message
    ).toByteArray();
    ProtoReader32 reader = new ByteArrayProtoReader32(encoded);

    assertEquals(2, reader.nextLengthDelimited());
    reader.beginMessage();
    assertEquals(1, reader.nextTag());
    assertThrows(EOFException.class, reader::readFixed32);
  }
}
