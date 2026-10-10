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
import java.io.UncheckedIOException;
import okio.Buffer;
import okio.BufferedSink;
import okio.ByteString;

/**
 * Encodes protocol buffer message fields from back-to-front for efficiency. Callers should write
 * data in the opposite order that the data will be read.
 *
 * <p>One significant benefit of writing messages in reverse order is that length prefixes can be
 * computed in constant time. Get the length of a message by subtracting the {@link #getByteCount()}
 * before writing it from {@link #getByteCount()} after writing it.
 */
public class ReverseProtoWriter {
  /*
   * Okio's Buffer doesn't want to receive reverse-order writes, so we need some hacks!
   *
   * We keep two buffers:
   *  * head: a single-segment buffer where we're currently writing new data to
   *  * tail: N-segments of already written data
   *
   * To write 'head' from back to front we use Okio's UnsafeCursor, which offers raw access to the
   * byte array inside the buffer. We can write wherever we want to in this array, so we write it
   * from back to front.
   *
   * When we fill up the head we move all of its data to the front of the tail.
   */

  private static final byte[] EMPTY_ARRAY = new byte[0];

  /** A block of forward writes performed against a scratch writer. */
  public interface ForwardBlock {
    void write(ProtoWriter forwardWriter) throws IOException;
  }

  private Buffer tail = new Buffer();
  private Buffer head = new Buffer();

  // Directly access the only segment inside the head buffer.
  private final Buffer.UnsafeCursor cursor = new Buffer.UnsafeCursor();
  private byte[] array = EMPTY_ARRAY;
  private int arrayLimit;

  // Cached and reused for all forward-encoded messages inside a reverse-encoded message.
  // Upstream initializes these lazily with no thread safety; plain fields are equivalent.
  private final Buffer forwardBuffer = new Buffer();
  private final ProtoWriter forwardWriter = new ProtoWriter(forwardBuffer);

  /** The total number of bytes emitted thus far. */
  public int getByteCount() {
    return (int) tail.size() + (array.length - arrayLimit);
  }

  public void writeTo(BufferedSink sink) throws IOException {
    emitCurrentSegment();
    sink.writeAll(tail);
  }

  private void require(int minByteCount) {
    if (arrayLimit >= minByteCount) return;
    emitCurrentSegment();
    head.readAndWriteUnsafe(cursor);
    cursor.expandBuffer(minByteCount);
    if (!(cursor.offset == 0L && cursor.end == cursor.data.length)) {
      throw new IllegalStateException();
    }
    array = cursor.data;
    arrayLimit = cursor.end;
  }

  /** Make the current segment a prefix of {@code tail}. */
  private void emitCurrentSegment() {
    if (array == EMPTY_ARRAY) return; // No current segment.
    cursor.close();

    try {
      // Advance the cursor to the first byte of data.
      head.skip(arrayLimit);

      // Move 'head' data to the front of 'tail'. We first move 'tail' to the end of head, then swap.
      head.writeAll(tail);
    } catch (IOException e) {
      // Both buffers are in memory, so this cannot happen; upstream's Kotlin has no checked
      // exceptions here either.
      throw new UncheckedIOException(e);
    }
    Buffer swap = tail;
    tail = head;
    head = swap;

    // Use EMPTY_ARRAY as a sentinel until we start a new segment.
    array = EMPTY_ARRAY;
    arrayLimit = 0;
  }

  /**
   * When a forward-writable message needs to be written while we're writing in reverse, write
   * that message forwards then copy its bytes into this.
   */
  public void writeForward(ForwardBlock block) throws IOException {
    block.write(forwardWriter);
    writeBytes(forwardBuffer.readByteString());
  }

  public void writeBytes(ByteString value) {
    int valueLimit = value.size();
    while (valueLimit != 0) {
      require(1);
      int copyByteCount = Math.min(arrayLimit, valueLimit);
      arrayLimit -= copyByteCount;
      int valuePos = valueLimit - copyByteCount;
      value.copyInto(valuePos, array, arrayLimit, copyByteCount);
      valueLimit = valuePos;
    }
  }

  /** Writes {@code value} without copying: the tail of the payload is written first. */
  public void writeBytes(Bytes value) {
    byte[] data = value.internalBytes();
    int valueLimit = data.length;
    while (valueLimit != 0) {
      require(1);
      int copyByteCount = Math.min(arrayLimit, valueLimit);
      arrayLimit -= copyByteCount;
      int valuePos = valueLimit - copyByteCount;
      System.arraycopy(data, valuePos, array, arrayLimit, copyByteCount);
      valueLimit = valuePos;
    }
  }

  public void writeString(String value) {
    // This is derived from Okio's Buffer.writeUtf8(), modified to write back-to-front. Like that
    // function, malformed UTF-16 surrogates are encoded as '?' in UTF-8.
    int i = value.length() - 1;
    while (i >= 0) {
      int c = value.charAt(i--);

      if (c < 0x80) {
        require(1);
        int localArrayLimit = arrayLimit;
        byte[] localArray = array;

        // Emit a 7-bit character with 1 byte.
        localArray[--localArrayLimit] = (byte) c; // 0xxxxxxx

        // Fast-path contiguous runs of ASCII characters. This is ugly, but yields a ~4x
        // performance improvement over independent calls to writeByte().
        int runLimit = Math.max(-1, i - localArrayLimit);
        while (i > runLimit) {
          int d = value.charAt(i);
          if (d >= 0x80) break;
          i--;
          localArray[--localArrayLimit] = (byte) d; // 0xxxxxxx
        }

        arrayLimit = localArrayLimit;
      } else if (c < 0x800) {
        // Emit a 11-bit character with 2 bytes.
        require(2);
        array[--arrayLimit] = (byte) ((c & 0x3f) | 0x80); // 10xxxxxx
        array[--arrayLimit] = (byte) ((c >> 6) | 0xc0); // 110xxxxx
      } else if (c < 0xd800 || c > 0xdfff) {
        // Emit a 16-bit character with 3 bytes.
        require(3);
        array[--arrayLimit] = (byte) ((c & 0x3f) | 0x80); // 10xxxxxx
        array[--arrayLimit] = (byte) (((c >> 6) & 0x3f) | 0x80); // 10xxxxxx
        array[--arrayLimit] = (byte) ((c >> 12) | 0xe0); // 1110xxxx
      } else {
        // c is a surrogate. Make sure it is a low surrogate & that its predecessor is a high
        // surrogate. If not, the UTF-16 is invalid, in which case we emit a replacement
        // character.
        int high = (i >= 0) ? value.charAt(i) : Integer.MAX_VALUE;
        if (high > 0xdbff || !(c >= 0xdc00 && c <= 0xdfff)) {
          require(1);
          array[--arrayLimit] = (byte) '?';
        } else {
          i--;
          // UTF-16 high surrogate: 110110xxxxxxxxxx (10 bits)
          // UTF-16 low surrogate:  110111yyyyyyyyyy (10 bits)
          // Unicode code point:    00010000000000000000 + xxxxxxxxxxyyyyyyyyyy (21 bits)
          int codePoint = 0x010000 + (((high & 0x03ff) << 10) | (c & 0x03ff));

          // Emit a 21-bit character with 4 bytes.
          require(4);
          array[--arrayLimit] = (byte) ((codePoint & 0x3f) | 0x80); // 10yyyyyy
          array[--arrayLimit] = (byte) (((codePoint >> 6) & 0x3f) | 0x80); // 10xxyyyy
          array[--arrayLimit] = (byte) (((codePoint >> 12) & 0x3f) | 0x80); // 10xxxxxx
          array[--arrayLimit] = (byte) ((codePoint >> 18) | 0xf0); // 11110xxx
        }
      }
    }
  }

  /** Encode and write a tag. */
  public void writeTag(int fieldNumber, FieldEncoding fieldEncoding) {
    writeVarint32(ProtoWriter.makeTag(fieldNumber, fieldEncoding));
  }

  /** Write an {@code int32} field to the stream. */
  public void writeSignedVarint32(int value) {
    if (value >= 0) {
      writeVarint32(value);
    } else {
      // Must sign-extend.
      writeVarint64(value);
    }
  }

  /**
   * Encode and write a varint. {@code value} is treated as unsigned, so it won't be
   * sign-extended if negative.
   */
  public void writeVarint32(int value) {
    int varint32Size = ProtoWriter.varint32Size(value);
    require(varint32Size);
    arrayLimit -= varint32Size;
    int offset = arrayLimit;

    while ((value & ~0x7f) != 0) {
      array[offset++] = (byte) ((value & 0x7f) | 0x80);
      value >>>= 7;
    }
    array[offset] = (byte) value;
  }

  /** Encode and write a varint. */
  public void writeVarint64(long value) {
    int varint64Size = ProtoWriter.varint64Size(value);
    require(varint64Size);
    arrayLimit -= varint64Size;
    int offset = arrayLimit;

    while ((value & ~0x7fL) != 0L) {
      array[offset++] = (byte) (((int) value & 0x7f) | 0x80);
      value >>>= 7;
    }
    array[offset] = (byte) value;
  }

  /** Write a little-endian 32-bit integer. */
  public void writeFixed32(int value) {
    require(4);
    arrayLimit -= 4;
    int offset = arrayLimit;
    array[offset++] = (byte) (value & 0xff);
    array[offset++] = (byte) ((value >>> 8) & 0xff);
    array[offset++] = (byte) ((value >>> 16) & 0xff);
    array[offset] = (byte) ((value >>> 24) & 0xff);
  }

  /** Write a little-endian 64-bit integer. */
  public void writeFixed64(long value) {
    require(8);
    arrayLimit -= 8;
    int offset = arrayLimit;
    array[offset++] = (byte) (value & 0xffL);
    array[offset++] = (byte) ((value >>> 8) & 0xffL);
    array[offset++] = (byte) ((value >>> 16) & 0xffL);
    array[offset++] = (byte) ((value >>> 24) & 0xffL);
    array[offset++] = (byte) ((value >>> 32) & 0xffL);
    array[offset++] = (byte) ((value >>> 40) & 0xffL);
    array[offset++] = (byte) ((value >>> 48) & 0xffL);
    array[offset] = (byte) ((value >>> 56) & 0xffL);
  }
}
