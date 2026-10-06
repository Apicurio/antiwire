/*
 * Translated from okio/src/jvmMain/kotlin/okio/Buffer.kt (with the common helpers of
 * okio/src/commonMain/kotlin/okio/internal/Buffer.kt inlined) at square/okio tag parent-3.18.2,
 * commit 9403a094ac98d5a6ba8bad4ec3301399796fad67.
 * Derivative work under the notices below.
 * Translation notes for the M0 subset (docs/decisions.md OPEN-1): excluded with scope
 * justification (no wire main or wire test use at wire tag 7.1.0): md5/sha1/sha256/sha512/
 * hmacSha*, the UnsafeCursor read/write-unsafe machinery, utf8Appendable(), the
 * read(ByteBuffer)/write(ByteBuffer) channel forms, and the
 * copyTo(OutputStream, offset, byteCount)/writeTo(OutputStream) bridges.
 * inputStream()/outputStream() are implemented (see below). The
 * readUtf8(byteCount) path decodes through String(bytes, UTF_8) exactly like the pinned
 * artifact's readString(byteCount, Charsets.UTF_8) JVM actual.
 *
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * A collection of bytes in memory, moving data from one buffer to the next is {@code
 * threadLocal.copyTo(threadLocal2, ...)}.
 *
 * <p><strong>Moving data from one buffer to another is fast.</strong> Instead of copying bytes
 * from one place in memory to another, this class just changes ownership of the underlying
 * segments.
 */
public final class Buffer implements BufferedSource, BufferedSink, Cloneable {

  private static final byte[] HEX_DIGIT_BYTES =
      ByteString.encodeUtf8("0123456789abcdef").internalArray();

  private static final int SEGMENTING_THRESHOLD = 4096;

  private static final long OVERFLOW_ZONE = Long.MIN_VALUE / 10L;
  private static final long OVERFLOW_DIGIT_START = Long.MIN_VALUE % 10L + 1;

  private static final long[] DIGIT_COUNT_TO_LARGEST_VALUE = {
      -1L, // Every value has more than 0 digits.
      9L, // For 1 digit (index 1), the largest value is 9.
      99L,
      999L,
      9999L,
      99999L,
      999999L,
      9999999L,
      99999999L,
      999999999L,
      9999999999L,
      99999999999L,
      999999999999L,
      9999999999999L,
      99999999999999L,
      999999999999999L,
      9999999999999999L,
      99999999999999999L,
      999999999999999999L, // For 18 digits (index 18), the largest value is 999999999999999999.
      Long.MAX_VALUE, // For 19 digits (index 19), the largest value is MAX_VALUE.
  };

  Segment head;

  long size;

  public long size() {
    return size;
  }

  @Override public Buffer buffer() {
    return this;
  }

  @Override public Buffer emitCompleteSegments() {
    return this; // Nowhere to emit to!
  }

  @Override public Buffer emit() {
    return this; // Nowhere to emit to!
  }

  @Override public boolean exhausted() {
    return size == 0L;
  }

  @Override public void require(long byteCount) throws EOFException {
    if (size < byteCount) {
      throw new EOFException();
    }
  }

  @Override public boolean request(long byteCount) {
    return size >= byteCount;
  }

  @Override public byte readByte() throws EOFException {
    if (size == 0L) {
      throw new EOFException();
    }

    Segment segment = head;
    int pos = segment.pos;
    int limit = segment.limit;

    byte[] data = segment.data;
    byte b = data[pos++];
    size -= 1L;

    if (pos == limit) {
      head = segment.pop();
      SegmentPool.recycle(segment);
    } else {
      segment.pos = pos;
    }

    return b;
  }

  /** Returns the byte at {@code pos}. Named {@code get} so Kotlin callers can use {@code buffer[pos]}. */
  public byte get(long pos) {
    Util.checkOffsetAndCount(size, pos, 1L);
    SeekPosition position = seek(pos);
    return position.segment.data[(int) ((long) position.segment.pos + pos - position.offset)];
  }

  /**
   * Returns the byte at {@code pos}. Upstream exposes this JVM name via JvmName on the operator
   * get; the port keeps both spellings for Java and Kotlin source compatibility.
   */
  public byte getByte(long pos) {
    return get(pos);
  }

  @Override public short readShort() throws EOFException {
    if (size < 2L) {
      throw new EOFException();
    }

    Segment segment = head;
    int pos = segment.pos;
    int limit = segment.limit;

    // If the short is split across multiple segments, delegate to readByte().
    if (limit - pos < 2) {
      short s = (short) (((readByte() & 0xff) << 8) | (readByte() & 0xff));
      return s;
    }

    byte[] data = segment.data;
    short s = (short) (((data[pos++] & 0xff) << 8) | (data[pos++] & 0xff));
    size -= 2L;

    if (pos == limit) {
      head = segment.pop();
      SegmentPool.recycle(segment);
    } else {
      segment.pos = pos;
    }

    return s;
  }

  @Override public int readInt() throws EOFException {
    if (size < 4L) {
      throw new EOFException();
    }

    Segment segment = head;
    int pos = segment.pos;
    int limit = segment.limit;

    // If the int is split across multiple segments, delegate to readByte().
    if (limit - pos < 4) {
      return (((readByte() & 0xff) << 24)
          | ((readByte() & 0xff) << 16)
          | ((readByte() & 0xff) << 8)
          | (readByte() & 0xff));
    }

    byte[] data = segment.data;
    int i = (((data[pos++] & 0xff) << 24)
        | ((data[pos++] & 0xff) << 16)
        | ((data[pos++] & 0xff) << 8)
        | (data[pos++] & 0xff));
    size -= 4L;

    if (pos == limit) {
      head = segment.pop();
      SegmentPool.recycle(segment);
    } else {
      segment.pos = pos;
    }

    return i;
  }

  @Override public long readLong() throws EOFException {
    if (size < 8L) {
      throw new EOFException();
    }

    Segment segment = head;
    int pos = segment.pos;
    int limit = segment.limit;

    // If the long is split across multiple segments, delegate to readInt().
    if (limit - pos < 8) {
      return (((readInt() & 0xffffffffL) << 32)
          | (readInt() & 0xffffffffL));
    }

    byte[] data = segment.data;
    long v = (((data[pos++] & 0xffL) << 56)
        | ((data[pos++] & 0xffL) << 48)
        | ((data[pos++] & 0xffL) << 40)
        | ((data[pos++] & 0xffL) << 32)
        | ((data[pos++] & 0xffL) << 24)
        | ((data[pos++] & 0xffL) << 16)
        | ((data[pos++] & 0xffL) << 8)
        | (data[pos++] & 0xffL));
    size -= 8L;

    if (pos == limit) {
      head = segment.pop();
      SegmentPool.recycle(segment);
    } else {
      segment.pos = pos;
    }

    return v;
  }

  @Override public short readShortLe() throws EOFException {
    short s = readShort();
    int i = s & 0xffff;
    int reversed = ((i & 0xff00) >>> 8) | ((i & 0x00ff) << 8);
    return (short) reversed;
  }

  @Override public int readIntLe() throws EOFException {
    return Integer.reverseBytes(readInt());
  }

  @Override public long readLongLe() throws EOFException {
    return Long.reverseBytes(readLong());
  }

  @Override public long readDecimalLong() throws java.io.IOException {
    if (size == 0L) {
      throw new EOFException();
    }

    // This value is always built negatively in order to accommodate Long.MIN_VALUE.
    long value = 0L;
    int seen = 0;
    boolean negative = false;
    boolean done = false;

    long overflowDigit = OVERFLOW_DIGIT_START;

    do {
      Segment segment = head;

      byte[] data = segment.data;
      int pos = segment.pos;
      int limit = segment.limit;

      while (pos < limit) {
        byte b = data[pos];
        if (b >= '0' && b <= '9') {
          int digit = '0' - b;

          // Detect when the digit would cause an overflow.
          if (value < OVERFLOW_ZONE || value == OVERFLOW_ZONE && digit < overflowDigit) {
            Buffer buffer = new Buffer().writeDecimalLong(value).writeByte(b);
            if (!negative) {
              buffer.readByte(); // Skip negative sign.
            }
            throw new NumberFormatException("Number too large: " + buffer.readUtf8());
          }
          value *= 10L;
          value += digit;
        } else if (b == '-' && seen == 0) {
          negative = true;
          overflowDigit -= 1;
        } else {
          // Set a flag to stop iteration. We still need to run through segment updating below.
          done = true;
          break;
        }
        pos++;
        seen++;
      }

      if (pos == limit) {
        head = segment.pop();
        SegmentPool.recycle(segment);
      } else {
        segment.pos = pos;
      }
    } while (!done && head != null);

    size -= (long) seen;

    int minimumSeen = negative ? 2 : 1;
    if (seen < minimumSeen) {
      if (size == 0L) {
        throw new EOFException();
      }
      String expected = negative ? "Expected a digit" : "Expected a digit or '-'";
      throw new NumberFormatException(
          expected + " but was 0x" + Integer.toHexString(getByte(0) & 0xff));
    }

    return negative ? value : -value;
  }

  @Override public long readHexadecimalUnsignedLong() throws java.io.IOException {
    if (size == 0L) {
      throw new EOFException();
    }

    long value = 0L;
    int seen = 0;
    boolean done = false;

    do {
      Segment segment = head;

      byte[] data = segment.data;
      int pos = segment.pos;
      int limit = segment.limit;

      while (pos < limit) {
        int digit;

        byte b = data[pos];
        if (b >= '0' && b <= '9') {
          digit = b - '0';
        } else if (b >= 'a' && b <= 'f') {
          digit = b - 'a' + 10;
        } else if (b >= 'A' && b <= 'F') {
          digit = b - 'A' + 10; // We never write uppercase, but we support reading it.
        } else {
          if (seen == 0) {
            throw new NumberFormatException(
                "Expected leading [0-9a-fA-F] character but was 0x"
                    + Integer.toHexString(b & 0xff));
          }
          // Set a flag to stop iteration. We still need to run through segment updating below.
          done = true;
          break;
        }

        // Detect when the shift will overflow.
        if ((value & -0x1000000000000000L) != 0L) {
          Buffer buffer = new Buffer().writeHexadecimalUnsignedLong(value).writeByte(b);
          throw new NumberFormatException("Number too large: " + buffer.readUtf8());
        }

        value <<= 4;
        value |= digit;
        pos++;
        seen++;
      }

      if (pos == limit) {
        head = segment.pop();
        SegmentPool.recycle(segment);
      } else {
        segment.pos = pos;
      }
    } while (!done && head != null);

    size -= (long) seen;
    return value;
  }

  @Override public ByteString readByteString() throws EOFException {
    return readByteString(size);
  }

  @Override public ByteString readByteString(long byteCount) throws EOFException {
    if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("byteCount: " + byteCount);
    }
    if (size < byteCount) {
      throw new EOFException();
    }

    if (byteCount >= SEGMENTING_THRESHOLD) {
      ByteString result = snapshot((int) byteCount);
      skip(byteCount);
      return result;
    } else {
      return new ByteString(readByteArray(byteCount));
    }
  }

  @Override public int select(Options options) throws java.io.IOException {
    int index = selectPrefix(options, false);
    if (index == -1) {
      return -1;
    }

    // If the prefix match actually matched a full byte string, consume it and return it.
    int selectedSize = options.byteStrings[index].size();
    skip((long) selectedSize);
    return index;
  }

  @Override public void readFully(Buffer sink, long byteCount) throws EOFException {
    if (size < byteCount) {
      sink.write(this, size); // Exhaust ourselves.
      throw new EOFException();
    }
    sink.write(this, byteCount);
  }

  @Override public long readAll(Sink sink) throws IOException {
    long byteCount = size;
    if (byteCount > 0L) {
      sink.write(this, byteCount);
    }
    return byteCount;
  }

  @Override public String readUtf8() throws EOFException {
    return readString(size, java.nio.charset.StandardCharsets.UTF_8);
  }

  @Override public String readUtf8(long byteCount) throws EOFException {
    return readString(byteCount, java.nio.charset.StandardCharsets.UTF_8);
  }

  @Override public String readString(Charset charset) throws EOFException {
    return readString(size, charset);
  }

  @Override public String readString(long byteCount, Charset charset) throws EOFException {
    if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("byteCount: " + byteCount);
    }
    if (size < byteCount) {
      throw new EOFException();
    }
    if (byteCount == 0L) {
      return "";
    }

    Segment s = head;
    if (s.pos + byteCount > s.limit) {
      // If the string spans multiple segments, delegate to readBytes().
      return new String(readByteArray(byteCount), charset);
    }

    String result = new String(s.data, s.pos, (int) byteCount, charset);
    s.pos += (int) byteCount;
    size -= byteCount;

    if (s.pos == s.limit) {
      head = s.pop();
      SegmentPool.recycle(s);
    }

    return result;
  }

  @Override public String readUtf8Line() throws IOException {
    long newline = indexOf((byte) '\n');

    if (newline != -1L) {
      return readUtf8Line(newline);
    }
    if (size != 0L) {
      return readUtf8(size);
    }
    return null;
  }

  String readUtf8Line(long newline) throws EOFException {
    if (newline > 0 && getByte(newline - 1) == '\r') {
      // Read everything until '\r\n', then skip the '\r\n'.
      String result = readUtf8(newline - 1L);
      skip(2L);
      return result;
    } else {
      // Read everything until '\n', then skip the '\n'.
      String result = readUtf8(newline);
      skip(1L);
      return result;
    }
  }

  @Override public int readUtf8CodePoint() throws EOFException {
    if (size == 0L) {
      throw new EOFException();
    }

    byte b0 = getByte(0);
    int codePoint;
    int byteCount;
    int min;

    if ((b0 & 0x80) == 0) {
      // 0xxxxxxx.
      codePoint = b0 & 0x7f;
      byteCount = 1; // 7 bits (ASCII).
      min = 0x0;
    } else if ((b0 & 0xe0) == 0xc0) {
      // 0x110xxxxx
      codePoint = b0 & 0x1f;
      byteCount = 2; // 11 bits (5 + 6).
      min = 0x80;
    } else if ((b0 & 0xf0) == 0xe0) {
      // 0x1110xxxx
      codePoint = b0 & 0x0f;
      byteCount = 3; // 16 bits (4 + 6 + 6).
      min = 0x800;
    } else if ((b0 & 0xf8) == 0xf0) {
      // 0x11110xxx
      codePoint = b0 & 0x07;
      byteCount = 4; // 21 bits (3 + 6 + 6 + 6).
      min = 0x10000;
    } else {
      // We expected the first byte of a code point but got something else.
      skip(1L);
      return Utf8.REPLACEMENT_CODE_POINT;
    }

    if (size < byteCount) {
      throw new EOFException(
          "size < " + byteCount + ": " + size
              + " (to read code point prefixed 0x" + Integer.toHexString(b0 & 0xff) + ")");
    }

    // Read the continuation bytes. If we encounter a non-continuation byte, the sequence consumed
    // thus far is truncated and is decoded as the replacement character. That non-continuation byte
    // is left in the stream for processing by the next call to readUtf8CodePoint().
    for (int i = 1; i < byteCount; i++) {
      byte b = getByte((long) i);
      if ((b & 0xc0) == 0x80) {
        // 0x10xxxxxx
        codePoint <<= 6;
        codePoint |= b & 0x3f;
      } else {
        skip((long) i);
        return Utf8.REPLACEMENT_CODE_POINT;
      }
    }

    skip((long) byteCount);

    if (codePoint > 0x10ffff) {
      return Utf8.REPLACEMENT_CODE_POINT; // Reject code points larger than the Unicode maximum.
    }
    if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
      return Utf8.REPLACEMENT_CODE_POINT; // Reject partial surrogates.
    }
    if (codePoint < min) {
      return Utf8.REPLACEMENT_CODE_POINT; // Reject overlong code points.
    }
    return codePoint;
  }

  @Override public byte[] readByteArray() throws EOFException {
    return readByteArray(size);
  }

  @Override public byte[] readByteArray(long byteCount) throws EOFException {
    if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("byteCount: " + byteCount);
    }
    if (size < byteCount) {
      throw new EOFException();
    }

    byte[] result = new byte[(int) byteCount];
    readFully(result);
    return result;
  }

  @Override public int read(byte[] sink) throws IOException {
    return read(sink, 0, sink.length);
  }

  @Override public void readFully(byte[] sink) throws EOFException {
    int offset = 0;
    while (offset < sink.length) {
      int read = read(sink, offset, sink.length - offset);
      if (read == -1) {
        throw new EOFException();
      }
      offset += read;
    }
  }

  @Override public int read(byte[] sink, int offset, int byteCount) {
    Util.checkOffsetAndCount(sink.length, offset, byteCount);

    Segment s = head;
    if (s == null) {
      return -1;
    }
    int toCopy = Math.min(byteCount, s.limit - s.pos);
    System.arraycopy(s.data, s.pos, sink, offset, toCopy);

    s.pos += toCopy;
    size -= (long) toCopy;

    if (s.pos == s.limit) {
      head = s.pop();
      SegmentPool.recycle(s);
    }

    return toCopy;
  }

  public void clear() throws EOFException {
    skip(size);
  }

  /** Discards all bytes after {@code newByteCount}, keeping the first {@code newByteCount}. */
  void truncateToSize(long newByteCount) throws IOException {
    Buffer scratch = new Buffer();
    scratch.writeAll(this);
    write(scratch, newByteCount);
    scratch.clear();
  }

  @Override public void skip(long byteCount) throws EOFException {
    while (byteCount > 0) {
      Segment head = this.head;
      if (head == null) {
        throw new EOFException();
      }

      int toSkip = (int) Math.min(byteCount, head.limit - head.pos);
      size -= (long) toSkip;
      byteCount -= (long) toSkip;
      head.pos += toSkip;

      if (head.pos == head.limit) {
        this.head = head.pop();
        SegmentPool.recycle(head);
      }
    }
  }

  @Override public Buffer write(ByteString byteString) {
    return write(byteString.internalArray(), 0, byteString.size());
  }

  @Override public Buffer write(ByteString byteString, int offset, int byteCount) {
    return write(byteString.internalArray(), offset, byteCount);
  }

  @Override public Buffer writeUtf8(String string) {
    return writeUtf8(string, 0, string.length());
  }

  @Override public Buffer writeUtf8(String string, int beginIndex, int endIndex) {
    if (beginIndex < 0) {
      throw new IllegalArgumentException("beginIndex < 0: " + beginIndex);
    }
    if (endIndex < beginIndex) {
      throw new IllegalArgumentException("endIndex < beginIndex: " + endIndex + " < " + beginIndex);
    }
    if (endIndex > string.length()) {
      throw new IllegalArgumentException(
          "endIndex > string.length: " + endIndex + " > " + string.length());
    }

    // Transcode a UTF-16 Java String to UTF-8 bytes.
    int i = beginIndex;
    while (i < endIndex) {
      int c = string.charAt(i);

      if (c < 0x80) {
        Segment tail = writableSegment(1);
        byte[] data = tail.data;
        int segmentOffset = tail.limit - i;
        int runLimit = Math.min(endIndex, Segment.SIZE - segmentOffset);

        // Emit a 7-bit character with 1 byte.
        data[segmentOffset + i++] = (byte) c; // 0xxxxxxx

        // Fast-path contiguous runs of ASCII characters. This is ugly, but yields a ~4x
        // performance improvement over independent calls to writeByte().
        while (i < runLimit) {
          c = string.charAt(i);
          if (c >= 0x80) {
            break;
          }
          data[segmentOffset + i++] = (byte) c; // 0xxxxxxx
        }

        int runSize = i + segmentOffset - tail.limit; // Equivalent to i - (previous i).
        tail.limit += runSize;
        size += (long) runSize;
      } else if (c < 0x800) {
        // Emit a 11-bit character with 2 bytes.
        Segment tail = writableSegment(2);
        tail.data[tail.limit] = (byte) ((c >> 6) | 0xc0); // 110xxxxx
        tail.data[tail.limit + 1] = (byte) ((c & 0x3f) | 0x80); // 10xxxxxx
        tail.limit += 2;
        size += 2L;
        i++;
      } else if (Character.isHighSurrogate((char) c)) {
        // c is a high surrogate. Make sure its successor is a low surrogate. If not, the UTF-16 is
        // invalid, in which case we emit a replacement character.
        int low = i + 1 < endIndex ? string.charAt(i + 1) : 0;
        if (!Character.isLowSurrogate((char) low)) {
          writeByte('?');
          i++;
        } else {
          int codePoint = Character.toCodePoint((char) c, (char) low);

          // Emit a 21-bit character with 4 bytes.
          Segment tail = writableSegment(4);
          tail.data[tail.limit] = (byte) ((codePoint >> 18) | 0xf0); // 11110xxx
          tail.data[tail.limit + 1] = (byte) (((codePoint >> 12) & 0x3f) | 0x80); // 10xxxxxx
          tail.data[tail.limit + 2] = (byte) (((codePoint >> 6) & 0x3f) | 0x80); // 10xxyyyy
          tail.data[tail.limit + 3] = (byte) ((codePoint & 0x3f) | 0x80); // 10yyyyyy
          tail.limit += 4;
          size += 4L;
          i += 2;
        }
      } else if (Character.isLowSurrogate((char) c)) {
        // Low surrogate should have followed a high surrogate. Emit a replacement character.
        writeByte('?');
        i++;
      } else {
        // Emit a 16-bit character with 3 bytes.
        Segment tail = writableSegment(3);
        tail.data[tail.limit] = (byte) ((c >> 12) | 0xe0); // 1110xxxx
        tail.data[tail.limit + 1] = (byte) (((c >> 6) & 0x3f) | 0x80); // 10xxxxxx
        tail.data[tail.limit + 2] = (byte) ((c & 0x3f) | 0x80); // 10xxxxxx
        tail.limit += 3;
        size += 3L;
        i++;
      }
    }

    return this;
  }

  @Override public Buffer writeUtf8CodePoint(int codePoint) {
    if (codePoint < 0x80) {
      // Emit a 7-bit code point with 1 byte.
      writeByte(codePoint);
    } else if (codePoint < 0x800) {
      // Emit a 11-bit code point with 2 bytes.
      Segment tail = writableSegment(2);
      tail.data[tail.limit] = (byte) ((codePoint >> 6) | 0xc0); // 110xxxxx
      tail.data[tail.limit + 1] = (byte) ((codePoint & 0x3f) | 0x80); // 10xxxxxx
      tail.limit += 2;
      size += 2L;
    } else if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
      // Emit a replacement character for a partial surrogate.
      writeByte('?');
    } else if (codePoint < 0x10000) {
      // Emit a 16-bit code point with 3 bytes.
      Segment tail = writableSegment(3);
      tail.data[tail.limit] = (byte) ((codePoint >> 12) | 0xe0); // 1110xxxx
      tail.data[tail.limit + 1] = (byte) (((codePoint >> 6) & 0x3f) | 0x80); // 10xxxxxx
      tail.data[tail.limit + 2] = (byte) ((codePoint & 0x3f) | 0x80); // 10xxxxxx
      tail.limit += 3;
      size += 3L;
    } else if (codePoint <= 0x10ffff) {
      // Emit a 21-bit code point with 4 bytes.
      Segment tail = writableSegment(4);
      tail.data[tail.limit] = (byte) ((codePoint >> 18) | 0xf0); // 11110xxx
      tail.data[tail.limit + 1] = (byte) (((codePoint >> 12) & 0x3f) | 0x80); // 10xxxxxx
      tail.data[tail.limit + 2] = (byte) (((codePoint >> 6) & 0x3f) | 0x80); // 10xxyyyy
      tail.data[tail.limit + 3] = (byte) ((codePoint & 0x3f) | 0x80); // 10yyyyyy
      tail.limit += 4;
      size += 4L;
    } else {
      throw new IllegalArgumentException("Unexpected code point: 0x" + Integer.toHexString(codePoint));
    }

    return this;
  }

  @Override public Buffer writeString(String string, Charset charset) {
    return writeString(string, 0, string.length(), charset);
  }

  public Buffer writeString(String string, int beginIndex, int endIndex, Charset charset) {
    if (beginIndex < 0) {
      throw new IllegalArgumentException("beginIndex < 0: " + beginIndex);
    }
    if (endIndex < beginIndex) {
      throw new IllegalArgumentException("endIndex < beginIndex: " + endIndex + " < " + beginIndex);
    }
    if (endIndex > string.length()) {
      throw new IllegalArgumentException(
          "endIndex > string.length: " + endIndex + " > " + string.length());
    }
    if (charset.equals(java.nio.charset.StandardCharsets.UTF_8)) {
      return writeUtf8(string, beginIndex, endIndex);
    }
    byte[] data = string.substring(beginIndex, endIndex).getBytes(charset);
    return write(data, 0, data.length);
  }

  @Override public Buffer write(byte[] source) {
    return write(source, 0, source.length);
  }

  @Override public Buffer write(byte[] source, int offset, int byteCount) {
    Util.checkOffsetAndCount(source.length, offset, byteCount);

    int limit = offset + byteCount;
    while (offset < limit) {
      Segment tail = writableSegment(1);

      int toCopy = Math.min(limit - offset, Segment.SIZE - tail.limit);
      System.arraycopy(source, offset, tail.data, tail.limit, toCopy);

      offset += toCopy;
      tail.limit += toCopy;
    }

    size += (long) byteCount;
    return this;
  }

  @Override public long writeAll(Source source) throws IOException {
    long totalBytesRead = 0L;
    while (true) {
      long readCount = source.read(this, (long) Segment.SIZE);
      if (readCount == -1L) {
        break;
      }
      totalBytesRead += readCount;
    }
    return totalBytesRead;
  }

  @Override public Buffer write(Source source, long byteCount) throws IOException {
    while (byteCount > 0L) {
      long read = source.read(this, byteCount);
      if (read == -1L) {
        throw new EOFException();
      }
      byteCount -= read;
    }
    return this;
  }

  @Override public Buffer writeByte(int b) {
    Segment tail = writableSegment(1);
    tail.data[tail.limit++] = (byte) b;
    size += 1L;
    return this;
  }

  @Override public Buffer writeShort(int s) {
    Segment tail = writableSegment(2);
    byte[] data = tail.data;
    int limit = tail.limit;
    data[limit++] = (byte) ((s >>> 8) & 0xff);
    data[limit++] = (byte) (s & 0xff);
    tail.limit = limit;
    size += 2L;
    return this;
  }

  @Override public Buffer writeShortLe(int s) {
    int i = ((short) s) & 0xffff;
    int reversed = ((i & 0xff00) >>> 8) | ((i & 0x00ff) << 8);
    return writeShort(reversed);
  }

  @Override public Buffer writeInt(int i) {
    Segment tail = writableSegment(4);
    byte[] data = tail.data;
    int limit = tail.limit;
    data[limit++] = (byte) ((i >>> 24) & 0xff);
    data[limit++] = (byte) ((i >>> 16) & 0xff);
    data[limit++] = (byte) ((i >>> 8) & 0xff);
    data[limit++] = (byte) (i & 0xff);
    tail.limit = limit;
    size += 4L;
    return this;
  }

  @Override public Buffer writeIntLe(int i) {
    return writeInt(Integer.reverseBytes(i));
  }

  @Override public Buffer writeLong(long v) {
    Segment tail = writableSegment(8);
    byte[] data = tail.data;
    int limit = tail.limit;
    data[limit++] = (byte) ((v >>> 56) & 0xffL);
    data[limit++] = (byte) ((v >>> 48) & 0xffL);
    data[limit++] = (byte) ((v >>> 40) & 0xffL);
    data[limit++] = (byte) ((v >>> 32) & 0xffL);
    data[limit++] = (byte) ((v >>> 24) & 0xffL);
    data[limit++] = (byte) ((v >>> 16) & 0xffL);
    data[limit++] = (byte) ((v >>> 8) & 0xffL);
    data[limit++] = (byte) (v & 0xffL);
    tail.limit = limit;
    size += 8L;
    return this;
  }

  @Override public Buffer writeLongLe(long v) {
    return writeLong(Long.reverseBytes(v));
  }

  @Override public Buffer writeDecimalLong(long v) {
    if (v == 0L) {
      // Both a shortcut and required since the following code can't handle zero.
      return writeByte('0');
    }

    boolean negative = false;
    if (v < 0L) {
      v = -v;
      if (v < 0L) { // Only true for Long.MIN_VALUE.
        return writeUtf8("-9223372036854775808");
      }
      negative = true;
    }

    int width = countDigitsIn(v);
    if (negative) {
      ++width;
    }

    Segment tail = writableSegment(width);
    byte[] data = tail.data;
    int pos = tail.limit + width; // We write backwards from right to left.
    while (v != 0L) {
      int digit = (int) (v % 10L);
      data[--pos] = HEX_DIGIT_BYTES[digit];
      v /= 10L;
    }
    if (negative) {
      data[--pos] = '-';
    }

    tail.limit += width;
    this.size += (long) width;
    return this;
  }

  private static int countDigitsIn(long v) {
    int guess = ((64 - Long.numberOfLeadingZeros(v)) * 10) >>> 5;
    return guess + (v > DIGIT_COUNT_TO_LARGEST_VALUE[guess] ? 1 : 0);
  }

  @Override public Buffer writeHexadecimalUnsignedLong(long v) {
    if (v == 0L) {
      // Both a shortcut and required since the following code can't handle zero.
      return writeByte('0');
    }

    // Mask every bit below the most significant bit to a 1
    // https://aggregate.org/MAGIC/#Most%20Significant%201%20Bit
    long x = v;
    x |= x >>> 1;
    x |= x >>> 2;
    x |= x >>> 4;
    x |= x >>> 8;
    x |= x >>> 16;
    x |= x >>> 32;

    // Count the number of 1s
    // https://aggregate.org/MAGIC/#Population%20Count%20(Ones%20Count)
    x -= (x >>> 1) & 0x5555555555555555L;
    x = (x >>> 2 & 0x3333333333333333L) + (x & 0x3333333333333333L);
    x = (x >>> 4) + x & 0x0f0f0f0f0f0f0f0fL;
    x += x >>> 8;
    x += x >>> 16;
    x = (x & 0x3f) + ((x >>> 32) & 0x3f);

    // Round up to the nearest full byte
    int width = (int) ((x + 3) / 4);

    Segment tail = writableSegment(width);
    byte[] data = tail.data;
    int pos = tail.limit + width - 1;
    int start = tail.limit;
    while (pos >= start) {
      data[pos] = HEX_DIGIT_BYTES[(int) (v & 0xF)];
      v >>>= 4;
      pos--;
    }
    tail.limit += width;
    size += (long) width;
    return this;
  }

  Segment writableSegment(int minimumCapacity) {
    if (minimumCapacity < 1 || minimumCapacity > Segment.SIZE) {
      throw new IllegalArgumentException("unexpected capacity");
    }

    if (head == null) {
      Segment result = SegmentPool.take(); // Acquire a first segment.
      head = result;
      result.prev = result;
      result.next = result;
      return result;
    }

    Segment tail = head.prev;
    if (tail.limit + minimumCapacity > Segment.SIZE || !tail.owner) {
      tail = tail.push(SegmentPool.take()); // Append a new empty segment to fill up.
    }
    return tail;
  }

  @Override public void write(Buffer source, long byteCount) {
    // Move bytes from the head of the source buffer to the tail of this buffer
    // while balancing two conflicting goals: don't waste CPU and don't waste
    // memory.
    //
    // Don't waste CPU (ie. don't copy data around).
    //
    // Copying large amounts of data is expensive. Instead, we prefer to
    // reassign entire segments from one buffer to the other.
    //
    // Don't waste memory.
    //
    // As an invariant, adjacent pairs of segments in a buffer should be at
    // least 50% full, except for the head segment and the tail segment.
    //
    // The head segment cannot maintain the invariant because the application is
    // consuming bytes from this segment, decreasing its level.
    //
    // The tail segment cannot maintain the invariant because the application is
    // producing bytes, which may require new nearly-empty tail segments to be
    // appended.
    //
    // Moving segments between buffers
    //
    // When writing one buffer to another, we prefer to reassign entire segments
    // over copying bytes into their most compact form. Suppose we have a buffer
    // with these segment levels [91%, 61%]. If we append a buffer with a
    // single [72%] segment, that yields [91%, 61%, 72%]. No bytes are copied.
    //
    // Or suppose we have a buffer with these segment levels: [100%, 2%], and we
    // want to append it to a buffer with these segment levels [99%, 3%]. This
    // operation will yield the following segments: [100%, 2%, 99%, 3%]. That
    // is, we do not spend time copying bytes around to achieve more efficient
    // memory use like [100%, 100%, 4%].
    //
    // When combining buffers, we will compact adjacent buffers when their
    // combined level doesn't exceed 100%. For example, when we start with
    // [100%, 40%] and append [30%, 80%], the result is [100%, 70%, 80%].
    //
    // Splitting segments
    //
    // Occasionally we write only part of a source buffer to a sink buffer. For
    // example, given a sink [51%, 91%], we may want to write the first 30% of
    // a source [92%, 82%] to it. To simplify, we first transform the source to
    // an equivalent buffer [30%, 62%, 82%] and then move the head segment,
    // yielding sink [51%, 91%, 30%] and source [62%, 82%].

    if (source == this) {
      throw new IllegalArgumentException("source == this");
    }
    Util.checkOffsetAndCount(source.size, 0, byteCount);

    while (byteCount > 0L) {
      // Is a prefix of the source's head segment all that we need to move?
      if (byteCount < source.head.limit - source.head.pos) {
        Segment tail = head != null ? head.prev : null;
        if (tail != null && tail.owner
            && byteCount + tail.limit - (tail.shared ? 0 : tail.pos) <= Segment.SIZE) {
          // Our existing segments are sufficient. Move bytes from source's head to our tail.
          source.head.writeTo(tail, (int) byteCount);
          source.size -= byteCount;
          size += byteCount;
          return;
        } else {
          // We're going to need another segment. Split the source's head
          // segment in two, then move the first of those two to this buffer.
          source.head = source.head.split((int) byteCount);
        }
      }

      // Remove the source's head segment and append it to our tail.
      Segment segmentToMove = source.head;
      long movedByteCount = segmentToMove.limit - segmentToMove.pos;
      source.head = segmentToMove.pop();
      if (head == null) {
        head = segmentToMove;
        segmentToMove.prev = segmentToMove;
        segmentToMove.next = segmentToMove.prev;
      } else {
        Segment tail = head.prev;
        tail = tail.push(segmentToMove);
        tail.compact();
      }
      source.size -= movedByteCount;
      size += movedByteCount;
      byteCount -= movedByteCount;
    }
  }

  @Override public long read(Buffer sink, long byteCount) {
    if (byteCount < 0L) {
      throw new IllegalArgumentException("byteCount < 0: " + byteCount);
    }
    if (size == 0L) {
      return -1L;
    }
    if (byteCount > size) {
      byteCount = size;
    }
    sink.write(this, byteCount);
    return byteCount;
  }

  @Override public long indexOf(byte b) throws IOException {
    return indexOf(b, 0L, Long.MAX_VALUE);
  }

  @Override public long indexOf(byte b, long fromIndex) throws IOException {
    return indexOf(b, fromIndex, Long.MAX_VALUE);
  }

  @Override public long indexOf(byte b, long fromIndex, long toIndex) {
    if (fromIndex < 0 || fromIndex > toIndex) {
      throw new IllegalArgumentException("size=" + size + " fromIndex=" + fromIndex
          + " toIndex=" + toIndex);
    }

    if (toIndex > size) {
      toIndex = size;
    }
    if (fromIndex == toIndex) {
      return -1L;
    }

    SeekPosition seekPosition = seek(fromIndex);
    Segment s = seekPosition.segment;
    if (s == null) {
      return -1L;
    }
    long offset = seekPosition.offset;

    // Scan through the segments, searching for b.
    while (offset < toIndex) {
      byte[] data = s.data;
      int limit = (int) Math.min(s.limit, s.pos + toIndex - offset);
      int pos = (int) (s.pos + fromIndex - offset);
      while (pos < limit) {
        if (data[pos] == b) {
          return pos - s.pos + offset;
        }
        pos++;
      }

      // Not in this segment. Try the next one.
      offset += s.limit - s.pos;
      fromIndex = offset;
      s = s.next;
    }

    return -1L;
  }

  @Override public long indexOf(ByteString bytes) throws IOException {
    return indexOf(bytes, 0L);
  }

  @Override public long indexOf(ByteString bytes, long fromIndex) throws IOException {
    return indexOfBytes(bytes, fromIndex, Long.MAX_VALUE, 0, bytes.size());
  }

  long indexOfBytes(ByteString bytes, long fromIndex, long toIndex, int bytesOffset,
      int byteCount) {
    Util.checkOffsetAndCount(bytes.size(), bytesOffset, byteCount);
    if (byteCount <= 0) {
      throw new IllegalArgumentException("byteCount == 0");
    }
    if (fromIndex < 0) {
      throw new IllegalArgumentException("fromIndex < 0: " + fromIndex);
    }
    if (fromIndex > toIndex) {
      throw new IllegalArgumentException("fromIndex > toIndex: " + fromIndex + " > " + toIndex);
    }

    if (toIndex > size) {
      toIndex = size;
    }
    if (fromIndex == toIndex) {
      return -1L;
    }

    SeekPosition seekPosition = seek(fromIndex);
    Segment s = seekPosition.segment;
    if (s == null) {
      return -1L;
    }
    long offset = seekPosition.offset;

    // Scan through the segments, searching for the lead byte. Each time that is found, delegate
    // to rangeEquals() to check for a complete match.
    byte[] targetByteArray = bytes.internalArray();
    byte b0 = targetByteArray[bytesOffset];
    long resultLimit = Math.min(toIndex, size - byteCount + 1L);
    while (offset < resultLimit) {
      // Scan through the current segment.
      byte[] data = s.data;
      int segmentLimit = (int) Math.min(s.limit, s.pos + resultLimit - offset);
      for (int pos = (int) (s.pos + fromIndex - offset); pos < segmentLimit; pos++) {
        if (data[pos] == b0
            && rangeEquals(s, pos + 1, targetByteArray, bytesOffset + 1, byteCount)) {
          return pos - s.pos + offset;
        }
      }

      // Not in this segment. Try the next one.
      offset += s.limit - s.pos;
      fromIndex = offset;
      s = s.next;
    }

    return -1L;
  }

  private static boolean rangeEquals(
      Segment segment, int segmentPos, byte[] bytes, int bytesOffset, int bytesLimit) {
    int segmentLimit = segment.limit;
    byte[] data = segment.data;

    int i = bytesOffset;
    while (i < bytesLimit) {
      if (segmentPos == segmentLimit) {
        segment = segment.next;
        data = segment.data;
        segmentPos = segment.pos;
        segmentLimit = segment.limit;
      }

      if (data[segmentPos] != bytes[i]) {
        return false;
      }

      segmentPos++;
      i++;
    }

    return true;
  }

  @Override public long indexOfElement(ByteString targetBytes) throws IOException {
    return indexOfElement(targetBytes, 0L);
  }

  @Override public long indexOfElement(ByteString targetBytes, long fromIndex) {
    if (fromIndex < 0L) {
      throw new IllegalArgumentException("fromIndex < 0: " + fromIndex);
    }

    SeekPosition seekPosition = seek(fromIndex);
    Segment s = seekPosition.segment;
    if (s == null) {
      return -1L;
    }
    long offset = seekPosition.offset;

    // Special case searching for one of two bytes. This is a common case for tools like Moshi,
    // which search for pairs of chars like `\r` and `\n` or {@code `"` and `\`. The impact of
    // this optimization is a ~5x speedup for this case without a substantial cost to other cases.
    byte[] targetByteArray = targetBytes.internalArray();
    if (targetByteArray.length == 2) {
      // Scan through the segments, searching for either of the two bytes.
      byte b0 = targetByteArray[0];
      byte b1 = targetByteArray[1];
      while (offset < size) {
        byte[] data = s.data;
        int pos = (int) (s.pos + fromIndex - offset);
        int limit = s.limit;
        while (pos < limit) {
          int b = data[pos];
          if (b == b0 || b == b1) {
            return pos - s.pos + offset;
          }
          pos++;
        }

        // Not in this segment. Try the next one.
        offset += s.limit - s.pos;
        fromIndex = offset;
        s = s.next;
      }
    } else {
      // Scan through the segments, searching for a byte that's also in the array.
      while (offset < size) {
        byte[] data = s.data;
        int pos = (int) (s.pos + fromIndex - offset);
        int limit = s.limit;
        while (pos < limit) {
          int b = data[pos];
          for (byte t : targetByteArray) {
            if (b == t) {
              return pos - s.pos + offset;
            }
          }
          pos++;
        }

        // Not in this segment. Try the next one.
        offset += s.limit - s.pos;
        fromIndex = offset;
        s = s.next;
      }
    }

    return -1L;
  }

  @Override public boolean rangeEquals(long offset, ByteString bytes) throws IOException {
    return rangeEquals(offset, bytes, 0, bytes.size());
  }

  @Override public boolean rangeEquals(long offset, ByteString bytes, int bytesOffset,
      int byteCount) {
    if (byteCount < 0) {
      return false;
    }
    if (offset < 0 || offset + byteCount > size) {
      return false;
    }
    if (bytesOffset < 0 || bytesOffset + byteCount > bytes.size()) {
      return false;
    }
    if (byteCount == 0) {
      return true;
    }

    return indexOfBytes(bytes, offset, offset + 1, bytesOffset, byteCount) != -1L;
  }

  @Override public void flush() {
  }

  @Override public java.io.OutputStream outputStream() {
    return new java.io.OutputStream() {
      @Override public void write(int b) {
        writeByte(b);
      }

      @Override public void write(byte[] data, int offset, int byteCount) {
        Buffer.this.write(data, offset, byteCount);
      }

      @Override public void flush() {
      }

      @Override public void close() {
      }

      @Override public String toString() {
        return Buffer.this + ".outputStream()";
      }
    };
  }

  @Override public java.io.InputStream inputStream() {
    return new java.io.InputStream() {
      @Override public int read() {
        if (size > 0L) {
          try {
            return readByte() & 0xff;
          } catch (EOFException e) {
            throw new AssertionError(e); // Unreachable: we hold a segment.
          }
        }
        return -1;
      }

      @Override public int read(byte[] sink, int offset, int byteCount) {
        return Buffer.this.read(sink, offset, byteCount);
      }

      @Override public int available() {
        return (int) Math.min(size, Integer.MAX_VALUE);
      }

      @Override public void close() {
      }

      @Override public String toString() {
        return Buffer.this + ".inputStream()";
      }
    };
  }

  @Override public void close() {
  }

  @Override public Timeout timeout() {
    return Timeout.NONE;
  }

  /** Returns the segment and offset within that segment corresponding to {@code fromIndex}. */
  private SeekPosition seek(long fromIndex) {
    Segment s = head;
    if (s == null) {
      return new SeekPosition(null, -1L);
    }

    if (size - fromIndex < fromIndex) {
      // We're scanning in the back half of this buffer. Find the segment starting at the back.
      long offset = size;
      while (offset > fromIndex) {
        s = s.prev;
        offset -= s.limit - s.pos;
      }
      return new SeekPosition(s, offset);
    } else {
      // We're scanning in the front half of this buffer. Find the segment starting at the front.
      long offset = 0L;
      while (true) {
        long nextOffset = offset + (s.limit - s.pos);
        if (nextOffset > fromIndex) {
          break;
        }
        s = s.next;
        offset = nextOffset;
      }
      return new SeekPosition(s, offset);
    }
  }

  private static final class SeekPosition {
    final Segment segment;
    final long offset;

    SeekPosition(Segment segment, long offset) {
      this.segment = segment;
      this.offset = offset;
    }
  }

  public long completeSegmentByteCount() {
    long result = size;
    if (result == 0L) {
      return 0L;
    }

    // Omit the tail if it's still writable.
    Segment tail = head.prev;
    if (tail.limit < Segment.SIZE && tail.owner) {
      result -= tail.limit - tail.pos;
    }

    return result;
  }

  /** Returns the number of bytes in segments that are fully filled and are not read-only. */
  public Buffer copyTo(Buffer out, long offset, long byteCount) {
    Util.checkOffsetAndCount(size, offset, byteCount);
    if (byteCount == 0L) {
      return this;
    }

    out.size += byteCount;

    // Skip segments that we aren't copying from.
    Segment s = head;
    while (offset >= s.limit - s.pos) {
      offset -= s.limit - s.pos;
      s = s.next;
    }

    // Copy one segment at a time.
    while (byteCount > 0L) {
      Segment copy = s.sharedCopy();
      copy.pos += (int) offset;
      copy.limit = Math.min(copy.pos + (int) byteCount, copy.limit);
      if (out.head == null) {
        copy.prev = copy;
        copy.next = copy.prev;
        out.head = copy.next;
      } else {
        out.head.prev.push(copy);
      }
      byteCount -= copy.limit - copy.pos;
      offset = 0L;
      s = s.next;
    }

    return this;
  }

  public Buffer copyTo(Buffer out, long offset) {
    return copyTo(out, offset, size - offset);
  }

  @Override public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Buffer)) {
      return false;
    }
    Buffer that = (Buffer) other;
    if (size != that.size) {
      return false;
    }
    if (size == 0L) {
      return true; // Both buffers are empty.
    }

    Segment sa = this.head;
    Segment sb = that.head;
    int posA = sa.pos;
    int posB = sb.pos;

    long pos = 0L;
    long count;
    while (pos < size) {
      count = Math.min(sa.limit - posA, sb.limit - posB);

      for (long i = 0L; i < count; i++) {
        if (sa.data[posA++] != sb.data[posB++]) {
          return false;
        }
      }

      if (posA == sa.limit) {
        sa = sa.next;
        posA = sa.pos;
      }

      if (posB == sb.limit) {
        sb = sb.next;
        posB = sb.pos;
      }
      pos += count;
    }

    return true;
  }

  @Override public int hashCode() {
    Segment s = head;
    if (s == null) {
      return 0;
    }
    int result = 1;
    do {
      int pos = s.pos;
      int limit = s.limit;
      while (pos < limit) {
        result = 31 * result + s.data[pos];
        pos++;
      }
      s = s.next;
    } while (s != head);
    return result;
  }

  /**
   * Returns a human-readable string that describes the contents of this buffer. Typically this
   * is a string like {@code [text=Hello]} or {@code [hex=0000ffff]}.
   */
  @Override public String toString() {
    return snapshot().toString();
  }

  /** Returns a deep copy of this buffer. */
  public Buffer copy() {
    Buffer result = new Buffer();
    if (size == 0L) {
      return result;
    }

    Segment headCopy = head.sharedCopy();

    result.head = headCopy;
    headCopy.prev = result.head;
    headCopy.next = headCopy.prev;

    Segment s = head.next;
    while (s != head) {
      headCopy.prev.push(s.sharedCopy());
      s = s.next;
    }

    result.size = size;
    return result;
  }

  /** Returns a deep copy of this buffer. Same as {@link #copy()}. */
  @Override public Buffer clone() {
    return copy();
  }

  /** Returns an immutable copy of this buffer as a byte string. */
  public ByteString snapshot() {
    if (size > Integer.MAX_VALUE) {
      throw new IllegalStateException("size > Int.MAX_VALUE: " + size);
    }
    return snapshot((int) size);
  }

  /** Returns an immutable copy of the first {@code byteCount} bytes of this buffer as a byte string. */
  public ByteString snapshot(int byteCount) {
    if (byteCount == 0) {
      return ByteString.EMPTY;
    }
    Util.checkOffsetAndCount(size, 0, (long) byteCount);

    // Walk through the buffer to count how many segments we'll need.
    int offset = 0;
    int segmentCount = 0;
    Segment s = head;
    while (offset < byteCount) {
      if (s.limit == s.pos) {
        throw new AssertionError("s.limit == s.pos"); // Empty segment. This should not happen!
      }
      offset += s.limit - s.pos;
      segmentCount++;
      s = s.next;
    }

    // Walk through the buffer again to assign segments and build the directory.
    byte[][] segments = new byte[segmentCount][];
    int[] directory = new int[segmentCount * 2];
    offset = 0;
    segmentCount = 0;
    s = head;
    while (offset < byteCount) {
      segments[segmentCount] = s.data;
      offset += s.limit - s.pos;
      // Despite sharing more bytes, only report having up to byteCount.
      directory[segmentCount] = Math.min(offset, byteCount);
      directory[segmentCount + segments.length] = s.pos;
      s.shared = true;
      segmentCount++;
      s = s.next;
    }
    return new SegmentedByteString(segments, directory);
  }

  /**
   * Returns the index of a value in {@code options} that is a prefix of this buffer. Returns -1 if
   * no value is found. This method does two simultaneous iterations: it iterates the trie and it
   * iterates this buffer. It returns when it reaches a result in the trie, when it mismatches in
   * the trie, and when the buffer is exhausted.
   */
  int selectPrefix(Options options, boolean selectTruncated) {
    Segment head = this.head;
    if (head == null) {
      return selectTruncated ? -2 : -1;
    }

    Segment s = head;
    byte[] data = head.data;
    int pos = head.pos;
    int limit = head.limit;

    int[] trie = options.trie;
    int triePos = 0;

    int prefixIndex = -1;

    navigateTrie:
    while (true) {
      int scanOrSelect = trie[triePos++];

      int possiblePrefixIndex = trie[triePos++];
      if (possiblePrefixIndex != -1) {
        prefixIndex = possiblePrefixIndex;
      }

      int nextStep;

      if (s == null) {
        break navigateTrie;
      } else if (scanOrSelect < 0) {
        // Scan: take multiple bytes from the buffer and the trie, looking for any mismatch.
        int scanByteCount = -1 * scanOrSelect;
        int trieLimit = triePos + scanByteCount;
        while (true) {
          int byte_ = data[pos++] & 0xff;
          if (byte_ != trie[triePos++]) {
            return prefixIndex; // Fail 'cause we found a mismatch.
          }
          boolean scanComplete = triePos == trieLimit;

          // Advance to the next buffer segment if this one is exhausted.
          if (pos == limit) {
            s = s.next;
            pos = s.pos;
            data = s.data;
            limit = s.limit;
            if (s == head) {
              if (!scanComplete) {
                break navigateTrie; // We were exhausted before the scan completed.
              }
              s = null; // We were exhausted at the end of the scan.
            }
          }

          if (scanComplete) {
            nextStep = trie[triePos];
            break;
          }
        }
      } else {
        // Select: take one byte from the buffer and find a match in the trie.
        int selectChoiceCount = scanOrSelect;
        int byte_ = data[pos++] & 0xff;
        int selectLimit = triePos + selectChoiceCount;
        while (true) {
          if (triePos == selectLimit) {
            return prefixIndex; // Fail 'cause we didn't find a match.
          }

          if (byte_ == trie[triePos]) {
            nextStep = trie[triePos + selectChoiceCount];
            break;
          }

          triePos++;
        }

        // Advance to the next buffer segment if this one is exhausted.
        if (pos == limit) {
          s = s.next;
          pos = s.pos;
          data = s.data;
          limit = s.limit;
          if (s == head) {
            s = null; // No more segments! The next trie node will be our last.
          }
        }
      }

      if (nextStep >= 0) {
        return nextStep; // Found a matching option.
      }
      triePos = -nextStep; // Found another node to continue the search.
    }

    // We break out of the loop above when we've exhausted the buffer without exhausting the trie.
    if (selectTruncated) {
      return -2; // The buffer is a prefix of at least one option.
    }
    return prefixIndex; // Return any matches we encountered while searching for a deeper match.
  }
}
