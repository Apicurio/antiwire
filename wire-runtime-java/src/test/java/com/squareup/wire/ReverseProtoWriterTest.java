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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import okio.Buffer;
import okio.ByteString;
import okio.Utf8;
import org.junit.jupiter.api.Test;

/**
 * Upstream commonTest translated (assertk to JUnit 5; Person/Task fixtures in TestMessages with
 * the same field shapes; kotlin apply/repeat to plain Java); see UPSTREAM-TEST-ADAPTATIONS.md.
 */
public class ReverseProtoWriterTest {
  private static final int SEGMENT_SIZE = 8192;

  private interface ReverseBlock {
    void write(ReverseProtoWriter writer) throws IOException;
  }

  private static Buffer reverseWrite(ReverseBlock block) throws IOException {
    ReverseProtoWriter writer = new ReverseProtoWriter();
    block.write(writer);
    Buffer result = new Buffer();
    writer.writeTo(result);
    return result;
  }

  private static void assertUtf8(String string, String expectedHex) throws IOException {
    Buffer buffer = reverseWrite(w -> w.writeString(string));
    assertEquals(expectedHex, buffer.readByteString().hex());
    assertEquals(expectedHex.length() / 2L, Utf8.size(string));
  }

  @Test public void utf8() throws IOException {
    // 0 byte strings.
    assertUtf8("", "");

    // 1 byte code points.
    assertUtf8("\u0000", "00");
    assertUtf8("A", "41");

    // 2 byte code points.
    assertUtf8("\u0080", "c280");
    assertUtf8("߿", "dfbf");

    // 3 byte code points.
    assertUtf8("ࠀ", "e0a080");
    assertUtf8("퟿", "ed9fbf");
    assertUtf8("", "ee8080");
    assertUtf8("￿", "efbfbf");

    // 4 byte code points, in Java as a high surrogate followed by low surrogate.
    assertUtf8("𐀀", "f0908080");

    // Malformed UTF-16.
    assertUtf8("\ud800", "3f"); // Dangling high surrogate.
    assertUtf8("\ud800A", "3f41"); // High surrogate followed by a 1 byte code point.
    assertUtf8("\ud800", "3fee8080"); // High surrogate followed by a 3 byte code point.
    assertUtf8("\ud800𐀀", "3ff0908080"); // High surrogate followed by surrogate pair.
    assertUtf8("\udc00A", "3f41"); // Unexpected low surrogate.
    assertUtf8("\udc00", "3f"); // Unexpected, dangling low surrogate.
  }

  @Test public void consistentWithRegularProtoWriterStrings() throws IOException {
    Buffer forwardBuffer = new Buffer();
    ProtoWriter forward = new ProtoWriter(forwardBuffer);
    forward.writeTag(3, FieldEncoding.LENGTH_DELIMITED);
    forward.writeVarint32(11);
    forward.writeString("hello world");

    Buffer reverseBuffer = new Buffer();
    ReverseProtoWriter reverse = new ReverseProtoWriter();
    int byteCountBefore = reverse.byteCount();
    reverse.writeString("hello world");
    reverse.writeVarint32(reverse.byteCount() - byteCountBefore);
    reverse.writeTag(3, FieldEncoding.LENGTH_DELIMITED);
    reverse.writeTo(reverseBuffer);

    assertEquals(forwardBuffer.readByteString(), reverseBuffer.readByteString());
  }

  @Test public void consistentWithRegularProtoWriterByteStrings() throws IOException {
    Buffer forwardBuffer = new Buffer();
    ProtoWriter forward = new ProtoWriter(forwardBuffer);
    forward.writeTag(3, FieldEncoding.LENGTH_DELIMITED);
    forward.writeVarint32(11);
    forward.writeBytes(ByteString.encodeUtf8("hello world"));

    Buffer reverseBuffer = new Buffer();
    ReverseProtoWriter reverse = new ReverseProtoWriter();
    int byteCountBefore = reverse.byteCount();
    reverse.writeBytes(ByteString.encodeUtf8("hello world"));
    reverse.writeVarint32(reverse.byteCount() - byteCountBefore);
    reverse.writeTag(3, FieldEncoding.LENGTH_DELIMITED);
    reverse.writeTo(reverseBuffer);

    assertEquals(forwardBuffer.readByteString(), reverseBuffer.readByteString());
  }

  private static String repeat(char c, int count) {
    StringBuilder sb = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      sb.append(c);
    }
    return sb.toString();
  }

  @Test public void multipleStringWritesSpanSegments() throws IOException {
    Buffer buffer = reverseWrite(w -> {
      w.writeString(repeat('a', SEGMENT_SIZE - 1));
      w.writeString(repeat('b', 2));
    });
    assertEquals(repeat('b', 2) + repeat('a', SEGMENT_SIZE - 1), buffer.readUtf8());
  }

  @Test public void writeStringExactlySegmentSize() throws IOException {
    Buffer buffer = reverseWrite(w -> w.writeString(repeat('a', SEGMENT_SIZE)));
    assertEquals(repeat('a', SEGMENT_SIZE), buffer.readUtf8());
  }

  @Test public void writeStringLargerThanSegmentSize() throws IOException {
    Buffer buffer = reverseWrite(w -> w.writeString(repeat('a', SEGMENT_SIZE + 1)));
    assertEquals(repeat('a', SEGMENT_SIZE + 1), buffer.readUtf8());
  }

  @Test public void writeStringSpanningMultipleSegments() throws IOException {
    Buffer buffer = reverseWrite(w -> w.writeString(repeat('a', SEGMENT_SIZE + SEGMENT_SIZE + 1)));
    assertEquals(repeat('a', SEGMENT_SIZE + SEGMENT_SIZE + 1), buffer.readUtf8());
  }

  @Test public void multipleByteStringWritesSpanSegments() throws IOException {
    Buffer buffer = reverseWrite(w -> {
      w.writeBytes(ByteString.encodeUtf8(repeat('a', SEGMENT_SIZE - 1)));
      w.writeBytes(ByteString.encodeUtf8(repeat('b', 2)));
    });
    assertEquals(repeat('b', 2) + repeat('a', SEGMENT_SIZE - 1), buffer.readUtf8());
  }

  @Test public void writeByteStringExactlySegmentSize() throws IOException {
    Buffer buffer = reverseWrite(
        w -> w.writeBytes(ByteString.encodeUtf8(repeat('a', SEGMENT_SIZE))));
    assertEquals(repeat('a', SEGMENT_SIZE), buffer.readUtf8());
  }

  @Test public void writeByteStringLargerThanSegmentSize() throws IOException {
    Buffer buffer = reverseWrite(
        w -> w.writeBytes(ByteString.encodeUtf8(repeat('a', SEGMENT_SIZE + 1))));
    assertEquals(repeat('a', SEGMENT_SIZE + 1), buffer.readUtf8());
  }

  @Test public void writeByteStringSpanningMultipleSegments() throws IOException {
    Buffer buffer = reverseWrite(
        w -> w.writeBytes(ByteString.encodeUtf8(repeat('a', SEGMENT_SIZE + SEGMENT_SIZE + 1))));
    assertEquals(repeat('a', SEGMENT_SIZE + SEGMENT_SIZE + 1), buffer.readUtf8());
  }

  @Test public void reverseEncodedMessageEmbedsForwardEncodedMessage() throws IOException {
    TestMessages.Person alanGrant = new TestMessages.Person("Alan Grant", 1950);
    TestMessages.Task digUpDinosaurs = new TestMessages.Task("dig up dinosaurs", alanGrant);

    ByteString alanGrantEncoded = TestMessages.Person.ADAPTER.encodeByteString(alanGrant);
    assertEquals(alanGrant, TestMessages.Person.ADAPTER.decode(alanGrantEncoded));

    ByteString digUpDinosaursEncoded =
        TestMessages.Task.ADAPTER.encodeByteString(digUpDinosaurs);
    assertEquals(digUpDinosaurs, TestMessages.Task.ADAPTER.decode(digUpDinosaursEncoded));
  }

  @Test public void mapEncodingPreservesOrder() throws IOException {
    Map<String, Integer> expectedMap = new LinkedHashMap<>();
    expectedMap.put("red", 0xff0000);
    expectedMap.put("green", 0x00ff00);
    expectedMap.put("blue", 0x0000ff);

    ProtoAdapter<Map<String, Integer>> mapAdapter =
        ProtoAdapter.newMapAdapter(ProtoAdapter.STRING, ProtoAdapter.INT32);
    ReverseProtoWriter writer = new ReverseProtoWriter();
    mapAdapter.encodeWithTag(writer, 1, expectedMap);
    Buffer buffer = new Buffer();
    writer.writeTo(buffer);
    ProtoReader protoReader = new ProtoReader(buffer);
    Map<String, Integer> decodedMap = new LinkedHashMap<>();
    long token = protoReader.beginMessage();
    int tag;
    while ((tag = protoReader.nextTag()) != -1) {
      if (tag == 1) {
        decodedMap.putAll(mapAdapter.decode(protoReader));
      } else {
        protoReader.readUnknownField(tag);
      }
    }
    protoReader.endMessageAndGetUnknownFields(token);
    assertEquals(expectedMap, decodedMap);
    assertEquals(new java.util.ArrayList<>(expectedMap.keySet()),
        new java.util.ArrayList<>(decodedMap.keySet()));
  }
}
