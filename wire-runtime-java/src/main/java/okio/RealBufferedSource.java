/*
 * Translated from okio/src/jvmMain/kotlin/okio/RealBufferedSource.kt (with the common helpers of
 * okio/src/commonMain/kotlin/okio/internal/RealBufferedSource.kt inlined) at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67.
 * Derivative work under the notices below. Translation notes for the M0 subset: peek(),
 * the typed select overload, and readUtf8LineStrict() are excluded from this class
 * exactly as from the BufferedSource interface (no wire main or wire test use at the
 * pin); inputStream() and readUtf8CodePoint() are implemented (see below).
 * Package-private: upstream internal.
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

final class RealBufferedSource implements BufferedSource {

  final Source source;
  final Buffer bufferField = new Buffer();

  boolean closed;

  RealBufferedSource(Source source) {
    if (source == null) {
      throw new NullPointerException("source == null");
    }
    this.source = source;
  }

  @Override public Buffer buffer() {
    return bufferField;
  }

  @Override public long read(Buffer sink, long byteCount) throws IOException {
    if (byteCount < 0L) {
      throw new IllegalArgumentException("byteCount < 0: " + byteCount);
    }
    if (closed) {
      throw new IllegalStateException("closed");
    }
    if (bufferField.size == 0L) {
      if (byteCount == 0L) {
        return 0L;
      }
      long read = source.read(bufferField, (long) Segment.SIZE);
      if (read == -1L) {
        return -1L;
      }
    }
    long toRead = Math.min(byteCount, bufferField.size);
    return bufferField.read(sink, toRead);
  }

  @Override public boolean exhausted() throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    return bufferField.exhausted() && source.read(bufferField, (long) Segment.SIZE) == -1L;
  }

  @Override public void require(long byteCount) throws IOException {
    if (!request(byteCount)) {
      throw new EOFException();
    }
  }

  @Override public boolean request(long byteCount) throws IOException {
    if (byteCount < 0L) {
      throw new IllegalArgumentException("byteCount < 0: " + byteCount);
    }
    if (closed) {
      throw new IllegalStateException("closed");
    }
    while (bufferField.size < byteCount) {
      if (source.read(bufferField, (long) Segment.SIZE) == -1L) {
        return false;
      }
    }
    return true;
  }

  @Override public byte readByte() throws IOException {
    require(1L);
    return bufferField.readByte();
  }

  @Override public short readShort() throws IOException {
    require(2L);
    return bufferField.readShort();
  }

  @Override public short readShortLe() throws IOException {
    require(2L);
    return bufferField.readShortLe();
  }

  @Override public int readInt() throws IOException {
    require(4L);
    return bufferField.readInt();
  }

  @Override public int readIntLe() throws IOException {
    require(4L);
    return bufferField.readIntLe();
  }

  @Override public long readLong() throws IOException {
    require(8L);
    return bufferField.readLong();
  }

  @Override public long readLongLe() throws IOException {
    require(8L);
    return bufferField.readLongLe();
  }

  @Override public long readDecimalLong() throws IOException {
    require(1L);
    long pos = 0L;
    while (request(pos + 1)) {
      byte b = bufferField.getByte(pos);
      if ((b < '0' || b > '9') && (pos != 0L || b != '-')) {
        if (pos == 0L) {
          throw new NumberFormatException(
              "Expected a digit or '-' but was 0x" + Integer.toHexString(b & 0xff));
        }
        break;
      }
      pos++;
    }
    return bufferField.readDecimalLong();
  }

  @Override public long readHexadecimalUnsignedLong() throws IOException {
    require(1L);
    long pos = 0L;
    while (request(pos + 1)) {
      byte b = bufferField.getByte(pos);
      if ((b < '0' || b > '9')
          && (b < 'a' || b > 'f')
          && (b < 'A' || b > 'F')) {
        if (pos == 0L) {
          throw new NumberFormatException(
              "Expected leading [0-9a-fA-F] character but was 0x"
                  + Integer.toHexString(b & 0xff));
        }
        break;
      }
      pos++;
    }
    return bufferField.readHexadecimalUnsignedLong();
  }

  @Override public ByteString readByteString() throws IOException {
    bufferField.writeAll(source);
    return bufferField.readByteString();
  }

  @Override public ByteString readByteString(long byteCount) throws IOException {
    require(byteCount);
    return bufferField.readByteString(byteCount);
  }

  @Override public int select(Options options) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    while (true) {
      int index = bufferField.selectPrefix(options, true);
      switch (index) {
        case -1:
          return -1;
        case -2:
          if (source.read(bufferField, (long) Segment.SIZE) == -1L) {
            return -1;
          }
          break;
        default:
          int selectedSize = options.byteStrings[index].size();
          bufferField.skip((long) selectedSize);
          return index;
      }
    }
  }

  @Override public byte[] readByteArray() throws IOException {
    bufferField.writeAll(source);
    return bufferField.readByteArray();
  }

  @Override public byte[] readByteArray(long byteCount) throws IOException {
    require(byteCount);
    return bufferField.readByteArray(byteCount);
  }

  @Override public int read(byte[] sink) throws IOException {
    return read(sink, 0, sink.length);
  }

  @Override public void readFully(byte[] sink) throws IOException {
    try {
      require((long) sink.length);
    } catch (EOFException e) {
      // Attempt to read as much as we can before dying.
      int offset = 0;
      while (bufferField.size > 0L) {
        int read = bufferField.read(sink, offset, (int) bufferField.size);
        if (read == -1) {
          throw new AssertionError();
        }
        offset += read;
      }
      throw e;
    }
    bufferField.readFully(sink);
  }

  @Override public int read(byte[] sink, int offset, int byteCount) throws IOException {
    Util.checkOffsetAndCount(sink.length, offset, byteCount);

    if (bufferField.size == 0L) {
      if (byteCount == 0) {
        return 0;
      }
      long read = source.read(bufferField, (long) Segment.SIZE);
      if (read == -1L) {
        return -1;
      }
    }
    int toRead = (int) Math.min((long) byteCount, bufferField.size);
    return bufferField.read(sink, offset, toRead);
  }

  @Override public void readFully(Buffer sink, long byteCount) throws IOException {
    try {
      require(byteCount);
    } catch (EOFException e) {
      // Attempt to read as much as we can before dying.
      sink.writeAll(bufferField);
      throw e;
    }
    bufferField.readFully(sink, byteCount);
  }

  @Override public long readAll(Sink sink) throws IOException {
    long totalBytesWritten = 0L;
    while (source.read(bufferField, (long) Segment.SIZE) != -1L) {
      long emitByteCount = bufferField.completeSegmentByteCount();
      if (emitByteCount > 0L) {
        totalBytesWritten += emitByteCount;
        sink.write(bufferField, emitByteCount);
      }
    }
    if (bufferField.size > 0L) {
      totalBytesWritten += bufferField.size;
      sink.write(bufferField, bufferField.size);
    }
    return totalBytesWritten;
  }

  @Override public String readUtf8() throws IOException {
    bufferField.writeAll(source);
    return bufferField.readUtf8();
  }

  @Override public String readUtf8(long byteCount) throws IOException {
    require(byteCount);
    return bufferField.readUtf8(byteCount);
  }

  @Override public String readString(Charset charset) throws IOException {
    bufferField.writeAll(source);
    return bufferField.readString(charset);
  }

  @Override public String readString(long byteCount, Charset charset) throws IOException {
    require(byteCount);
    return bufferField.readString(byteCount, charset);
  }

  @Override public int readUtf8CodePoint() throws IOException {
    require(1L);
    int b0 = bufferField.getByte(0) & 0xff;
    if ((b0 & 0xe0) == 0xc0) {
      require(2L);
    } else if ((b0 & 0xf0) == 0xe0) {
      require(3L);
    } else if ((b0 & 0xf8) == 0xf0) {
      require(4L);
    }
    return bufferField.readUtf8CodePoint();
  }

  @Override public String readUtf8Line() throws IOException {
    long newline = indexOf((byte) '\n');
    if (newline == -1L) {
      if (bufferField.size != 0L) {
        return readUtf8(bufferField.size);
      }
      return null;
    }
    return bufferField.readUtf8Line(newline);
  }

  @Override public long indexOf(byte b) throws IOException {
    return indexOf(b, 0L, Long.MAX_VALUE);
  }

  @Override public long indexOf(byte b, long fromIndex) throws IOException {
    return indexOf(b, fromIndex, Long.MAX_VALUE);
  }

  @Override public long indexOf(byte b, long fromIndex, long toIndex) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    if (fromIndex < 0L || fromIndex > toIndex) {
      throw new IllegalArgumentException("fromIndex=" + fromIndex + " toIndex=" + toIndex);
    }
    while (fromIndex < toIndex) {
      // Scan the buffer. Stop on the first newline character or when we reach the size limit.
      long result = bufferField.indexOf(b, fromIndex, toIndex);
      if (result != -1L) {
        return result;
      }

      long size = bufferField.size;
      if (size >= toIndex || source.read(bufferField, (long) Segment.SIZE) == -1L) {
        return -1L;
      }

      fromIndex = Math.max(fromIndex, size);
    }
    return -1L;
  }

  @Override public long indexOf(ByteString bytes) throws IOException {
    return indexOf(bytes, 0L);
  }

  @Override public long indexOf(ByteString bytes, long fromIndex) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    while (true) {
      long result = bufferField.indexOfBytes(bytes, fromIndex, Long.MAX_VALUE, 0, bytes.size());
      if (result != -1L) {
        return result;
      }

      long size = bufferField.size;
      if (size - (long) (bytes.size() - 1) <= fromIndex
          || source.read(bufferField, (long) Segment.SIZE) == -1L) {
        return -1L;
      }

      fromIndex = Math.max(fromIndex, size - (long) (bytes.size() - 1));
    }
  }

  @Override public long indexOfElement(ByteString targetBytes) throws IOException {
    return indexOfElement(targetBytes, 0L);
  }

  @Override public long indexOfElement(ByteString targetBytes, long fromIndex) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    while (true) {
      long index = bufferField.indexOfElement(targetBytes, fromIndex);
      if (index != -1L) {
        return index;
      }

      long size = bufferField.size;
      if (size == Long.MAX_VALUE || source.read(bufferField, (long) Segment.SIZE) == -1L) {
        return -1L;
      }

      fromIndex = Math.max(fromIndex, size);
    }
  }

  @Override public boolean rangeEquals(long offset, ByteString bytes) throws IOException {
    return rangeEquals(offset, bytes, 0, bytes.size());
  }

  @Override public boolean rangeEquals(long offset, ByteString bytes, int bytesOffset,
      int byteCount) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    if (!request(offset + (long) byteCount)) {
      return false;
    }
    return bufferField.rangeEquals(offset, bytes, bytesOffset, byteCount);
  }

  @Override public void skip(long byteCount) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    while (byteCount > 0L) {
      if (bufferField.size == 0L && source.read(bufferField, (long) Segment.SIZE) == -1L) {
        throw new EOFException();
      }
      long toSkip = Math.min(byteCount, bufferField.size);
      bufferField.skip(toSkip);
      byteCount -= toSkip;
    }
  }

  @Override public Timeout timeout() {
    return source.timeout();
  }

  @Override public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    source.close();
  }

  @Override public java.io.InputStream inputStream() {
    return new java.io.InputStream() {
      @Override public int read() throws IOException {
        if (closed) {
          throw new IOException("closed");
        }
        if (bufferField.size == 0L) {
          long count = source.read(bufferField, (long) Segment.SIZE);
          if (count == -1L) {
            return -1;
          }
        }
        return bufferField.readByte() & 0xff;
      }

      @Override public int read(byte[] data, int offset, int byteCount) throws IOException {
        if (closed) {
          throw new IOException("closed");
        }
        Util.checkOffsetAndCount(data.length, offset, byteCount);

        if (bufferField.size == 0L) {
          long count = source.read(bufferField, (long) Segment.SIZE);
          if (count == -1L) {
            return -1;
          }
        }

        return bufferField.read(data, offset, byteCount);
      }

      @Override public int available() throws IOException {
        if (closed) {
          throw new IOException("closed");
        }
        return (int) Math.min(bufferField.size, Integer.MAX_VALUE);
      }

      @Override public void close() throws IOException {
        RealBufferedSource.this.close();
      }

      @Override public String toString() {
        return RealBufferedSource.this + ".inputStream()";
      }
    };
  }

  @Override public String toString() {
    return "buffer(" + source + ")";
  }
}
