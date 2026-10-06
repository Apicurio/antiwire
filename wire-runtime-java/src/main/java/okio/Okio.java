/*
 * Translated from okio/src/commonMain/kotlin/okio/Okio.kt (Source.buffer, Sink.buffer,
 * blackholeSink, Source.limit) and okio/src/jvmMain/kotlin/okio/JvmOkio.kt
 * (InputStream.source, OutputStream.sink) at square/okio tag parent-3.18.2,
 * commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under the notices
 * below. Translation notes for the M0 subset: the Socket, File, nio-file, cipher, hashing,
 * and zlib entry points of JvmOkio are out of the M0 subset except InflaterSource, which
 * the ZIP loader needs; the Kotlin extension receiver forms (stream.source(),
 * source.buffer()) become statics (Okio.source(stream), Okio.buffer(source)), the
 * source-compatibility mapping recorded in docs/decisions.md OPEN-1.
 *
 * Copyright (C) 2019 Square, Inc.
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
import java.io.InputStream;
import java.io.OutputStream;

public final class Okio {

  private Okio() {
  }

  /** Returns a new source that buffers {@code source}, reading a small chunk at a time. */
  public static BufferedSource buffer(Source source) {
    return new RealBufferedSource(source);
  }

  /** Returns a new sink that buffers {@code sink}, writing a small chunk at a time. */
  public static BufferedSink buffer(Sink sink) {
    return new RealBufferedSink(sink);
  }

  public static Source source(InputStream in) {
    return new InputStreamSource(in, new Timeout());
  }

  public static Sink sink(OutputStream out) {
    return new OutputStreamSink(out, new Timeout());
  }

  /**
   * Returns a source that limits the length of {@code source} to {@code byteCount}. If
   * {@code throwIfSourceIsLonger} is true, an attempt to read past that limit throws
   * rather than ending the stream.
   */
  static Source limit(Source source, long byteCount, boolean throwIfSourceIsLonger) {
    return new LimitSource(source, byteCount, throwIfSourceIsLonger);
  }

  private static final class OutputStreamSink implements Sink {
    private final OutputStream out;
    private final Timeout timeout;

    OutputStreamSink(OutputStream out, Timeout timeout) {
      if (out == null) {
        throw new NullPointerException("out == null");
      }
      if (timeout == null) {
        throw new NullPointerException("timeout == null");
      }
      this.out = out;
      this.timeout = timeout;
    }

    @Override public void write(Buffer source, long byteCount) throws IOException {
      Util.checkOffsetAndCount(source.size, 0, byteCount);
      long remaining = byteCount;
      while (remaining > 0) {
        timeout.throwIfReached();
        Segment head = source.head;
        int toCopy = (int) Math.min(remaining, head.limit - head.pos);
        out.write(head.data, head.pos, toCopy);

        head.pos += toCopy;
        remaining -= (long) toCopy;
        source.size -= (long) toCopy;

        if (head.pos == head.limit) {
          source.head = head.pop();
          SegmentPool.recycle(head);
        }
      }
    }

    @Override public void flush() throws IOException {
      out.flush();
    }

    @Override public void close() throws IOException {
      out.close();
    }

    @Override public Timeout timeout() {
      return timeout;
    }

    @Override public String toString() {
      return "sink(" + out + ")";
    }
  }

  private static final class InputStreamSource implements Source {
    private final InputStream input;
    private final Timeout timeout;

    InputStreamSource(InputStream input, Timeout timeout) {
      if (input == null) {
        throw new NullPointerException("input == null");
      }
      if (timeout == null) {
        throw new NullPointerException("timeout == null");
      }
      this.input = input;
      this.timeout = timeout;
    }

    @Override public long read(Buffer sink, long byteCount) throws IOException {
      if (byteCount == 0L) {
        return 0L;
      }
      if (byteCount < 0L) {
        throw new IllegalArgumentException("byteCount < 0: " + byteCount);
      }
      try {
        timeout.throwIfReached();
        Segment tail = sink.writableSegment(1);
        int maxToCopy = (int) Math.min(byteCount, Segment.SIZE - tail.limit);
        int bytesRead = input.read(tail.data, tail.limit, maxToCopy);
        if (bytesRead == -1) {
          if (tail.pos == tail.limit) {
            // We allocated a tail segment, but didn't end up needing it. Recycle!
            sink.head = tail.pop();
            SegmentPool.recycle(tail);
          }
          return -1;
        }
        tail.limit += bytesRead;
        sink.size += (long) bytesRead;
        return (long) bytesRead;
      } catch (AssertionError e) {
        if (e.getCause() != null && e.getCause() instanceof IOException) {
          throw (IOException) e.getCause();
        }
        throw e;
      }
    }

    @Override public void close() throws IOException {
      input.close();
    }

    @Override public Timeout timeout() {
      return timeout;
    }

    @Override public String toString() {
      return "source(" + input + ")";
    }
  }

  private static final class LimitSource implements Source {
    private final Source source;
    private final long byteCount;
    private final boolean throwIfSourceIsLonger;
    private long bytesReceived;

    LimitSource(Source source, long byteCount, boolean throwIfSourceIsLonger) {
      if (source == null) {
        throw new NullPointerException("source == null");
      }
      if (byteCount < 0L) {
        throw new IllegalArgumentException("byteCount < 0: " + byteCount);
      }
      this.source = source;
      this.byteCount = byteCount;
      this.throwIfSourceIsLonger = throwIfSourceIsLonger;
    }

    @Override public long read(Buffer sink, long byteCount) throws IOException {
      long remainingByteCount = this.byteCount - bytesReceived;
      long toRead;
      if (remainingByteCount < 0) {
        throw new IOException("expected " + this.byteCount + " bytes but got " + bytesReceived);
      } else if (throwIfSourceIsLonger) {
        // Attempt to read an extra byte, so we can detect if too many bytes are returned.
        toRead = Math.min(byteCount, remainingByteCount + 1);
      } else if (remainingByteCount == 0L) {
        return -1L; // Already read exactly the promised size.
      } else {
        toRead = Math.min(byteCount, remainingByteCount);
      }

      long result = source.read(sink, toRead);

      if (result == -1L) {
        if (remainingByteCount == 0L) {
          return -1L;
        }
        throw new EOFException("expected " + this.byteCount + " bytes but got " + bytesReceived);
      }

      bytesReceived += result;

      long beyondLimitByteCount = bytesReceived - this.byteCount;
      if (beyondLimitByteCount > 0) {
        // If we received bytes beyond the limit, don't return them to the caller.
        sink.truncateToSize(sink.size - beyondLimitByteCount);
        throw new IOException("expected " + this.byteCount + " bytes but got " + bytesReceived);
      }

      return result;
    }

    @Override public void close() throws IOException {
      source.close();
    }

    @Override public Timeout timeout() {
      return source.timeout();
    }

    @Override public String toString() {
      return "limit(" + byteCount + ", " + source + ")";
    }
  }
}
