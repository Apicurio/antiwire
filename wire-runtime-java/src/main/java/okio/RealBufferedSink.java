/*
 * Translated from okio/src/jvmMain/kotlin/okio/RealBufferedSink.kt (with the common helpers of
 * okio/src/commonMain/kotlin/okio/internal/RealBufferedSink.kt inlined) at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67.
 * Derivative work under the notices below. Translation notes for the M0 subset: outputStream()
 * and utf8Appendable() are excluded (no wire main or wire test use at the pin).
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

final class RealBufferedSink implements BufferedSink {

  final Sink sink;
  final Buffer bufferField = new Buffer();

  boolean closed;

  RealBufferedSink(Sink sink) {
    if (sink == null) {
      throw new NullPointerException("sink == null");
    }
    this.sink = sink;
  }

  @Override public Buffer buffer() {
    return bufferField;
  }

  @Override public void write(Buffer source, long byteCount) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.write(source, byteCount);
    emitCompleteSegments();
  }

  @Override public BufferedSink write(ByteString byteString) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.write(byteString);
    return emitCompleteSegments();
  }

  @Override public BufferedSink write(ByteString byteString, int offset, int byteCount)
      throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.write(byteString, offset, byteCount);
    return emitCompleteSegments();
  }

  @Override public BufferedSink writeUtf8(String string) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.writeUtf8(string);
    return emitCompleteSegments();
  }

  @Override public BufferedSink writeUtf8(String string, int beginIndex, int endIndex)
      throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.writeUtf8(string, beginIndex, endIndex);
    return emitCompleteSegments();
  }

  @Override public BufferedSink writeUtf8CodePoint(int codePoint) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.writeUtf8CodePoint(codePoint);
    return emitCompleteSegments();
  }

  @Override public BufferedSink writeString(String string, Charset charset) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.writeString(string, charset);
    return emitCompleteSegments();
  }

  @Override public BufferedSink write(byte[] source) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.write(source);
    return emitCompleteSegments();
  }

  @Override public BufferedSink write(byte[] source, int offset, int byteCount)
      throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.write(source, offset, byteCount);
    return emitCompleteSegments();
  }

  @Override public long writeAll(Source source) throws IOException {
    if (source == null) {
      throw new IllegalArgumentException("source == null");
    }
    long totalBytesRead = 0L;
    while (true) {
      long readCount = source.read(bufferField, (long) Segment.SIZE);
      if (readCount == -1L) {
        break;
      }
      totalBytesRead += readCount;
      emitCompleteSegments();
    }
    return totalBytesRead;
  }

  @Override public BufferedSink write(Source source, long byteCount) throws IOException {
    if (source == null) {
      throw new IllegalArgumentException("source == null");
    }
    while (byteCount > 0L) {
      long read = source.read(bufferField, byteCount);
      if (read == -1L) {
        throw new EOFException();
      }
      byteCount -= read;
      emitCompleteSegments();
    }
    return this;
  }

  @Override public BufferedSink writeByte(int b) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.writeByte(b);
    return emitCompleteSegments();
  }

  @Override public BufferedSink writeShort(int s) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.writeShort(s);
    return emitCompleteSegments();
  }

  @Override public BufferedSink writeShortLe(int s) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.writeShortLe(s);
    return emitCompleteSegments();
  }

  @Override public BufferedSink writeInt(int i) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.writeInt(i);
    return emitCompleteSegments();
  }

  @Override public BufferedSink writeIntLe(int i) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.writeIntLe(i);
    return emitCompleteSegments();
  }

  @Override public BufferedSink writeLong(long v) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.writeLong(v);
    return emitCompleteSegments();
  }

  @Override public BufferedSink writeLongLe(long v) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.writeLongLe(v);
    return emitCompleteSegments();
  }

  @Override public BufferedSink writeDecimalLong(long v) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.writeDecimalLong(v);
    return emitCompleteSegments();
  }

  @Override public BufferedSink writeHexadecimalUnsignedLong(long v) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    bufferField.writeHexadecimalUnsignedLong(v);
    return emitCompleteSegments();
  }

  @Override public BufferedSink emitCompleteSegments() throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    long byteCount = bufferField.completeSegmentByteCount();
    if (byteCount > 0L) {
      sink.write(bufferField, byteCount);
    }
    return this;
  }

  @Override public BufferedSink emit() throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    long byteCount = bufferField.size;
    if (byteCount > 0L) {
      sink.write(bufferField, byteCount);
    }
    return this;
  }

  @Override public void flush() throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    if (bufferField.size > 0L) {
      sink.write(bufferField, bufferField.size);
    }
    sink.flush();
  }

  @Override public void close() throws IOException {
    if (closed) {
      return;
    }

    // Emit buffered data to the underlying sink. If this fails, we still need
    // to close the sink; otherwise we risk leaking resources.
    Throwable thrown = null;
    try {
      if (bufferField.size > 0) {
        sink.write(bufferField, bufferField.size);
      }
    } catch (Throwable e) {
      thrown = e;
    }

    try {
      sink.close();
    } catch (Throwable e) {
      if (thrown == null) {
        thrown = e;
      }
    }

    closed = true;

    if (thrown != null) {
      if (thrown instanceof IOException) {
        throw (IOException) thrown;
      }
      if (thrown instanceof RuntimeException) {
        throw (RuntimeException) thrown;
      }
      if (thrown instanceof Error) {
        throw (Error) thrown;
      }
      throw new AssertionError(thrown);
    }
  }

  @Override public Timeout timeout() {
    return sink.timeout();
  }

  @Override public java.io.OutputStream outputStream() {
    return new java.io.OutputStream() {
      @Override public void write(int b) throws IOException {
        if (closed) {
          throw new IOException("closed");
        }
        bufferField.writeByte(b);
        emitCompleteSegments();
      }

      @Override public void write(byte[] data, int offset, int byteCount) throws IOException {
        if (closed) {
          throw new IOException("closed");
        }
        bufferField.write(data, offset, byteCount);
        emitCompleteSegments();
      }

      @Override public void flush() throws IOException {
        // For backwards compatibility, a flush() on a closed stream is a no-op.
        if (!closed) {
          RealBufferedSink.this.flush();
        }
      }

      @Override public void close() throws IOException {
        RealBufferedSink.this.close();
      }

      @Override public String toString() {
        return RealBufferedSink.this + ".outputStream()";
      }
    };
  }

  @Override public String toString() {
    return "buffer(" + sink + ")";
  }
}
