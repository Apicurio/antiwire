/*
 * Translated from okio/src/jvmMain/kotlin/okio/InflaterSource.kt at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under
 * the notices below. Needed by the ZIP loader for deflated entries.
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
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A source that uses <a href="http://tools.ietf.org/html/rfc1951">DEFLATE</a> to decompress data
 * compressed by <a href="http://tools.ietf.org/html/rfc1951">DEFALTE</a>.
 */
public final class InflaterSource implements Source {

  private final BufferedSource source;
  private final Inflater inflater;

  /**
   * When we call Inflater.setInput(), the inflater keeps our byte array until it needs input again
   * (or until {@code inflate()} returns a short count). We track how many bytes the inflater is
   * currently holding so we can reclaim that memory as necessary.
   */
  private int bufferBytesHeldByInflater;
  private boolean closed;

  public InflaterSource(Source source, Inflater inflater) {
    this(Okio.buffer(source), inflater);
  }

  InflaterSource(BufferedSource source, Inflater inflater) {
    if (source == null) {
      throw new NullPointerException("source == null");
    }
    if (inflater == null) {
      throw new NullPointerException("inflater == null");
    }
    this.source = source;
    this.inflater = inflater;
  }

  @Override public long read(Buffer sink, long byteCount) throws IOException {
    while (true) {
      long bytesInflated = readOrInflate(sink, byteCount);
      if (bytesInflated > 0) {
        return bytesInflated;
      }
      if (inflater.finished() || inflater.needsDictionary()) {
        return -1L;
      }
      if (source.exhausted()) {
        throw new EOFException("source exhausted prematurely");
      }
    }
  }

  public long readOrInflate(Buffer sink, long byteCount) throws IOException {
    if (byteCount < 0L) {
      throw new IllegalArgumentException("byteCount < 0: " + byteCount);
    }
    if (closed) {
      throw new IllegalStateException("closed");
    }
    if (byteCount == 0L) {
      return 0L;
    }

    try {
      // Prepare the destination that we'll write into.
      Segment tail = sink.writableSegment(1);
      int toRead = (int) Math.min(byteCount, Segment.SIZE - tail.limit);

      // Prepare the source that we'll read from.
      refill();

      // Decompress the inflater's compressed data into the sink.
      int bytesInflated = inflater.inflate(tail.data, tail.limit, toRead);

      // Release consumed bytes from the source.
      releaseBytesAfterInflate();

      // Track produced bytes in the destination.
      if (bytesInflated > 0) {
        tail.limit += bytesInflated;
        sink.size += (long) bytesInflated;
        return (long) bytesInflated;
      }

      // We allocated a tail segment, but didn't end up needing it. Recycle!
      if (tail.pos == tail.limit) {
        sink.head = tail.pop();
        SegmentPool.recycle(tail);
      }

      return 0L;
    } catch (DataFormatException e) {
      throw new IOException(e);
    }
  }

  /** Refills the inflater with compressed data if it requires input. */
  public boolean refill() throws IOException {
    if (!inflater.needsInput()) {
      return false; // Only refill if the inflater has unprocessed input.
    }

    // If there are no further bytes in the source, we cannot refill.
    if (source.exhausted()) {
      return true; // The caller should inflate any remaining bytes.
    }

    // Assign buffer bytes to the inflater.
    Segment head = source.buffer().head;
    bufferBytesHeldByInflater = head.limit - head.pos;
    inflater.setInput(head.data, head.pos, bufferBytesHeldByInflater);
    return false; // The caller should not inflate.
  }

  /** When the inflater has processed compressed data, remove it from the buffer. */
  private void releaseBytesAfterInflate() throws IOException {
    if (bufferBytesHeldByInflater == 0) {
      return;
    }
    int toRelease = bufferBytesHeldByInflater - inflater.getRemaining();
    bufferBytesHeldByInflater -= toRelease;
    source.skip((long) toRelease);
  }

  @Override public Timeout timeout() {
    return source.timeout();
  }

  @Override public void close() throws IOException {
    if (closed) {
      return;
    }
    inflater.end();
    closed = true;
    source.close();
  }
}
