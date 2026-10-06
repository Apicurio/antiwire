/*
 * Translated from okio/src/commonMain/kotlin/okio/FileHandle.kt at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under
 * the notices below. Translation notes for the M0 subset: position()/reposition(),
 * sink(), and appendingSink() are excluded (no wire main, wire test, or observed
 * Apicurio call-site use at the pins; the Apicurio census exercises openReadWrite,
 * write(fileOffset, byte[], offset, byteCount), and close, which are retained).
 *
 * Copyright (C) 2021 Square, Inc.
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

import java.io.IOException;

/**
 * A handle to a file.
 *
 * <p>This class is not thread safe: the close-check lock guards close bookkeeping only. Calls
 * must be guarded externally, like the synchronized implementations do.
 */
public abstract class FileHandle implements java.io.Closeable {

  protected final boolean readWrite;

  /** This lock is held while a close check runs. */
  private final Object lock = new Object();

  private boolean closed;

  private int openStreamCount;

  protected FileHandle(boolean readWrite) {
    this.readWrite = readWrite;
  }

  /**
   * Reads at least 1, and up to {@code byteCount} bytes from this starting at {@code fileOffset}
   * and stores them in {@code array.slice(arrayOffset, arrayOffset + byteCount)}. Returns the
   * number of bytes read, or -1 if {@code fileOffset} equals {@link #size()}.
   */
  public int read(long fileOffset, byte[] array, int arrayOffset, int byteCount)
      throws IOException {
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("closed");
      }
    }
    return protectedRead(fileOffset, array, arrayOffset, byteCount);
  }

  /**
   * Reads at least 1, and up to {@code byteCount} bytes from this starting at {@code fileOffset}
   * and appends them to {@code sink}. Returns the number of bytes read, or -1 if
   * {@code fileOffset} equals {@link #size()}.
   */
  public long read(long fileOffset, Buffer sink, long byteCount) throws IOException {
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("closed");
      }
    }
    return readNoCloseCheck(fileOffset, sink, byteCount);
  }

  /** Returns the total number of bytes in the file. This will change if the file size changes. */
  public long size() throws IOException {
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("closed");
      }
    }
    return protectedSize();
  }

  /**
   * Changes the number of bytes in this file to {@code size}. This will remove bytes from the end
   * if the new size is smaller. It will add 0 bytes to the end if it is larger.
   */
  public void resize(long size) throws IOException {
    if (!readWrite) {
      throw new IllegalStateException("file handle is read-only");
    }
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("closed");
      }
    }
    protectedResize(size);
  }

  /** Reads {@code byteCount} bytes from {@code array} and writes them to this at {@code fileOffset}. */
  public void write(long fileOffset, byte[] array, int arrayOffset, int byteCount)
      throws IOException {
    if (!readWrite) {
      throw new IllegalStateException("file handle is read-only");
    }
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("closed");
      }
    }
    protectedWrite(fileOffset, array, arrayOffset, byteCount);
  }

  /** Removes {@code byteCount} bytes from {@code source} and writes them to this at {@code fileOffset}. */
  public void write(long fileOffset, Buffer source, long byteCount) throws IOException {
    if (!readWrite) {
      throw new IllegalStateException("file handle is read-only");
    }
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("closed");
      }
    }
    writeNoCloseCheck(fileOffset, source, byteCount);
  }

  /** Pushes all buffered bytes to their final destination. */
  public void flush() throws IOException {
    if (!readWrite) {
      throw new IllegalStateException("file handle is read-only");
    }
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("closed");
      }
    }
    protectedFlush();
  }

  /**
   * Returns a source that reads from this starting at {@code fileOffset}. The returned source
   * must be closed when it is no longer needed.
   */
  public Source source(long fileOffset) throws IOException {
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("closed");
      }
      openStreamCount++;
    }
    return new FileHandleSource(this, fileOffset);
  }

  /** Returns a source that reads from this from the beginning. */
  public Source source() throws IOException {
    return source(0L);
  }

  /**
   * Closes this file handle and releases the resources held by this file handle. It is an error to
   * call a member of an open file handle.
   */
  public void close() throws IOException {
    synchronized (lock) {
      if (closed) {
        return;
      }
      closed = true;
      if (openStreamCount != 0) {
        return;
      }
    }
    protectedClose();
  }

  protected abstract int protectedRead(
      long fileOffset, byte[] array, int arrayOffset, int byteCount) throws IOException;

  protected abstract void protectedWrite(
      long fileOffset, byte[] array, int arrayOffset, int byteCount) throws IOException;

  protected abstract void protectedFlush() throws IOException;

  protected abstract void protectedResize(long size) throws IOException;

  protected abstract long protectedSize() throws IOException;

  protected abstract void protectedClose() throws IOException;

  private long readNoCloseCheck(long fileOffset, Buffer sink, long byteCount) throws IOException {
    if (byteCount < 0L) {
      throw new IllegalArgumentException("byteCount < 0: " + byteCount);
    }

    long currentOffset = fileOffset;
    long targetOffset = fileOffset + byteCount;

    while (currentOffset < targetOffset) {
      Segment tail = sink.writableSegment(1);
      int readByteCount = protectedRead(
          currentOffset,
          tail.data,
          tail.limit,
          (int) Math.min(targetOffset - currentOffset, Segment.SIZE - tail.limit));

      if (readByteCount == -1) {
        if (tail.pos == tail.limit) {
          // We allocated a tail segment, but didn't end up needing it. Recycle!
          sink.head = tail.pop();
          SegmentPool.recycle(tail);
        }
        if (fileOffset == currentOffset) {
          return -1L; // We wanted bytes but didn't return any.
        }
        break;
      }

      tail.limit += readByteCount;
      currentOffset += readByteCount;
      sink.size += readByteCount;
    }

    return currentOffset - fileOffset;
  }

  private void writeNoCloseCheck(long fileOffset, Buffer source, long byteCount)
      throws IOException {
    Util.checkOffsetAndCount(source.size, 0L, byteCount);

    long currentOffset = fileOffset;
    long targetOffset = fileOffset + byteCount;

    while (currentOffset < targetOffset) {
      Segment head = source.head;
      int toCopy = (int) Math.min(targetOffset - currentOffset, head.limit - head.pos);
      protectedWrite(currentOffset, head.data, head.pos, toCopy);

      head.pos += toCopy;
      currentOffset += toCopy;
      source.size -= toCopy;

      if (head.pos == head.limit) {
        source.head = head.pop();
        SegmentPool.recycle(head);
      }
    }
  }

  private static final class FileHandleSource implements Source {
    final FileHandle fileHandle;
    long position;
    boolean closed;

    FileHandleSource(FileHandle fileHandle, long position) {
      this.fileHandle = fileHandle;
      this.position = position;
    }

    @Override public long read(Buffer sink, long byteCount) throws IOException {
      if (closed) {
        throw new IllegalStateException("closed");
      }
      long result = fileHandle.readNoCloseCheck(position, sink, byteCount);
      if (result != -1L) {
        position += result;
      }
      return result;
    }

    @Override public Timeout timeout() {
      return Timeout.NONE;
    }

    @Override public void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;
      synchronized (fileHandle.lock) {
        fileHandle.openStreamCount--;
        if (fileHandle.openStreamCount != 0 || !fileHandle.closed) {
          return;
        }
      }
      fileHandle.protectedClose();
    }
  }
}
