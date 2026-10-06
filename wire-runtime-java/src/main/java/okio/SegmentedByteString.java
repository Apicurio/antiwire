/*
 * Translated from okio/src/jvmMain/kotlin/okio/SegmentedByteString.kt (with the common helpers
 * of okio/src/commonMain/kotlin/okio/internal/SegmentedByteString.kt inlined) at square/okio
 * tag parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67.
 * Derivative work under the notices below. Package-private: upstream declares the class
 * internal; it exists to implement efficient snapshots of buffers.
 *
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * An immutable byte string composed of segments of byte arrays. This class exists to implement
 * efficient snapshots of buffers. It is implemented as an array of segments, plus a directory in
 * two halves that describes how the segments compose this byte string.
 *
 * <p>The first half of the directory is the cumulative byte count covered by each segment. The
 * element at {@code directory[0]} contains the number of bytes held in {@code segments[0]}; the
 * element at {@code directory[1]} contains the number of bytes held in
 * {@code segments[0] + segments[1]}, and so on. The element at {@code directory[segments.length - 1]}
 * contains the total size of this byte string. The first half of the directory is always
 * monotonically increasing.
 *
 * <p>The second half of the directory is the offset in {@code segments} of the first content byte.
 * Bytes preceding this offset are unused, as are bytes beyond the segment's effective size.
 *
 * <p>Suppose we have a byte string, {@code [A, B, C, D, E, F, G, H, I, J, K, L, M]} that is stored
 * across three byte arrays: {@code [x, x, x, x, A, B, C, D, E, x, x, x]}, {@code [x, F, G]}, and
 * {@code [H, I, J, K, L, M, x, x, x, x, x, x]}. The three byte arrays would be stored in
 * {@code segments} in order. Since the arrays contribute 5, 2, and 6 elements respectively, the
 * directory starts with {@code [5, 7, 13} to hold the cumulative total at each position. Since the
 * offsets into the arrays are 4, 1, and 0 respectively, the directory ends with {@code 4, 1, 0]}.
 * Concatenating these two halves, the complete directory is {@code [5, 7, 13, 4, 1, 0]}.
 *
 * <p>This structure is chosen so that the segment holding a particular offset can be found by
 * binary search. We use one array rather than two for the directory as a micro-optimization.
 */
final class SegmentedByteString extends ByteString {

  private static final long serialVersionUID = 1L;

  final transient byte[][] segments;
  final transient int[] directory;

  SegmentedByteString(byte[][] segments, int[] directory) {
    super(EMPTY.data);
    this.segments = segments;
    this.directory = directory;
  }

  @Override public String string(Charset charset) {
    return toByteString().string(charset);
  }

  @Override public String base64(boolean includePadding) {
    return toByteString().base64(includePadding);
  }

  @Override public String hex() {
    return toByteString().hex();
  }

  @Override public ByteString toAsciiLowercase() {
    return toByteString().toAsciiLowercase();
  }

  @Override public ByteString toAsciiUppercase() {
    return toByteString().toAsciiUppercase();
  }

  @Override public ByteString substring(int beginIndex, int endIndex) {
    if (beginIndex < 0) {
      throw new IllegalArgumentException("beginIndex=" + beginIndex + " < 0");
    }
    if (endIndex > size()) {
      throw new IllegalArgumentException("endIndex=" + endIndex + " > length(" + size() + ")");
    }

    int subLen = endIndex - beginIndex;
    if (subLen < 0) {
      throw new IllegalArgumentException(
          "endIndex=" + endIndex + " < beginIndex=" + beginIndex);
    }

    if (beginIndex == 0 && endIndex == size()) {
      return this;
    }
    if (beginIndex == endIndex) {
      return EMPTY;
    }

    int beginSegment = segment(beginIndex); // First segment to include
    int endSegment = segment(endIndex - 1); // Last segment to include

    byte[][] newSegments = Arrays.copyOfRange(segments, beginSegment, endSegment + 1);
    int[] newDirectory = new int[newSegments.length * 2];
    int index = 0;
    for (int s = beginSegment; s <= endSegment; s++) {
      newDirectory[index] = Math.min(directory[s] - beginIndex, subLen);
      newDirectory[index++ + newSegments.length] = directory[s + segments.length];
    }

    // Set the new position of the first segment
    int segmentOffset = beginSegment == 0 ? 0 : directory[beginSegment - 1];
    newDirectory[newSegments.length] += beginIndex - segmentOffset;

    return new SegmentedByteString(newSegments, newDirectory);
  }

  /**
   * Upstream overrides the single internal lookup that backs both {@code get} and
   * {@code getByte} (internal/SegmentedByteString.kt commonInternalGet); overriding
   * {@link #get} here routes both spellings through the segmented lookup.
   */
  @Override public byte get(int pos) {
    Util.checkOffsetAndCount(directory[segments.length - 1], pos, 1);
    int segment = segment(pos);
    int segmentOffset = segment == 0 ? 0 : directory[segment - 1];
    int segmentPos = directory[segment + segments.length];
    return segments[segment][pos - segmentOffset + segmentPos];
  }

  @Override public int size() {
    return directory[segments.length - 1];
  }

  @Override public byte[] toByteArray() {
    byte[] result = new byte[size()];
    int resultPos = 0;
    for (int s = 0, pos = 0; s < segments.length; s++) {
      int segmentPos = directory[segments.length + s];
      int nextSegmentOffset = directory[s];

      int byteCount = nextSegmentOffset - pos;
      System.arraycopy(segments[s], segmentPos, result, resultPos, byteCount);
      resultPos += byteCount;
      pos = nextSegmentOffset;
    }
    return result;
  }

  @Override public ByteBuffer asByteBuffer() {
    return ByteBuffer.wrap(toByteArray()).asReadOnlyBuffer();
  }

  @Override public void write(OutputStream out) throws IOException {
    int segmentCount = segments.length;
    int pos = 0;
    for (int s = 0; s < segmentCount; s++) {
      int segmentPos = directory[segmentCount + s];
      int nextSegmentOffset = directory[s];
      out.write(segments[s], segmentPos, nextSegmentOffset - pos);
      pos = nextSegmentOffset;
    }
  }

  @Override void write(Buffer buffer, int offset, int byteCount) {
    forEachSegment(offset, offset + byteCount, (data, segmentOffset, count) -> {
      Segment segment = new Segment(data, segmentOffset, segmentOffset + count, true, false);
      if (buffer.head == null) {
        segment.prev = segment;
        segment.next = segment.prev;
        buffer.head = segment.next;
      } else {
        buffer.head.prev.push(segment);
      }
    });
    buffer.size += byteCount;
  }

  @Override public boolean rangeEquals(int offset, ByteString other, int otherOffset,
      int byteCount) {
    if (offset < 0 || offset > size() - byteCount) {
      return false;
    }
    // Go segment-by-segment through this, passing arrays to other's rangeEquals().
    int[] holder = new int[] {otherOffset};
    boolean[] result = new boolean[] {true};
    forEachSegment(offset, offset + byteCount, (data, segmentOffset, count) -> {
      if (result[0] && !other.rangeEquals(holder[0], data, segmentOffset, count)) {
        result[0] = false;
        return;
      }
      holder[0] += count;
    });
    return result[0];
  }

  @Override public boolean rangeEquals(int offset, byte[] other, int otherOffset, int byteCount) {
    if (offset < 0 || offset > size() - byteCount
        || otherOffset < 0 || otherOffset > other.length - byteCount) {
      return false;
    }
    // Go segment-by-segment through this, comparing ranges of arrays.
    int[] holder = new int[] {otherOffset};
    boolean[] result = new boolean[] {true};
    forEachSegment(offset, offset + byteCount, (data, segmentOffset, count) -> {
      if (result[0] && !Util.arrayRangeEquals(data, segmentOffset, other, holder[0], count)) {
        result[0] = false;
        return;
      }
      holder[0] += count;
    });
    return result[0];
  }

  @Override public void copyInto(int offset, byte[] target, int targetOffset, int byteCount) {
    Util.checkOffsetAndCount(size(), offset, byteCount);
    Util.checkOffsetAndCount(target.length, targetOffset, byteCount);
    // Go segment-by-segment through this, copying ranges of arrays.
    int[] holder = new int[] {targetOffset};
    forEachSegment(offset, offset + byteCount, (data, segmentOffset, count) -> {
      System.arraycopy(data, segmentOffset, target, holder[0], count);
      holder[0] += count;
    });
  }

  @Override public int indexOf(byte[] other, int fromIndex) {
    return toByteString().indexOf(other, fromIndex);
  }

  @Override public int lastIndexOf(byte[] other, int fromIndex) {
    return toByteString().lastIndexOf(other, fromIndex);
  }

  /** Returns a copy as a non-segmented byte string. */
  private ByteString toByteString() {
    return new ByteString(toByteArray());
  }

  @Override byte[] internalArray() {
    return toByteArray();
  }

  @Override public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (other instanceof ByteString) {
      ByteString that = (ByteString) other;
      return that.size() == size() && rangeEquals(0, that, 0, size());
    }
    return false;
  }

  @Override public int hashCode() {
    int result = hashCode;
    if (result != 0) {
      return result;
    }

    // Equivalent to Arrays.hashCode(toByteArray()).
    result = 1;
    for (int s = 0, pos = 0; s < segments.length; s++) {
      int segmentPos = directory[segments.length + s];
      int nextSegmentOffset = directory[s];
      int i = segmentPos;
      int limit = segmentPos + (nextSegmentOffset - pos);
      while (i < limit) {
        result = 31 * result + segments[s][i];
        i++;
      }
      pos = nextSegmentOffset;
    }
    hashCode = result;
    return result;
  }

  @Override public String toString() {
    return toByteString().toString();
  }

  private Object writeReplace() {
    return toByteString();
  }

  private void readObject(ObjectInputStream in)
      throws ClassNotFoundException, InvalidObjectException {
    throw new InvalidObjectException("SegmentedByteString is always serialized as ByteString");
  }

  private void writeObject(ObjectOutputStream out) {
    throw new AssertionError("never reached; writeReplace replaces this instance");
  }

  /** Returns the index of the segment that contains the byte at {@code pos}. */
  private int segment(int pos) {
    // Search for (pos + 1) instead of (pos) because the directory holds sizes, not indexes.
    int i = binarySearch(directory, pos + 1, 0, segments.length);
    return i >= 0 ? i : ~i; // If i is negative, bitflip to get the insert position.
  }

  private static int binarySearch(int[] array, int value, int fromIndex, int toIndex) {
    int left = fromIndex;
    int right = toIndex - 1;

    while (left <= right) {
      int mid = (left + right) >>> 1; // protect from overflow
      int midVal = array[mid];

      if (midVal < value) {
        left = mid + 1;
      } else if (midVal > value) {
        right = mid - 1;
      } else {
        return mid;
      }
    }

    // no exact match, return negative of where it should match
    return -left - 1;
  }

  /**
   * Processes the segments between {@code beginIndex} and {@code endIndex}, invoking
   * {@code action} with the ByteArray and range of the valid data.
   */
  private void forEachSegment(int beginIndex, int endIndex, SegmentAction action) {
    int s = segment(beginIndex);
    int pos = beginIndex;
    while (pos < endIndex) {
      int segmentOffset = s == 0 ? 0 : directory[s - 1];
      int segmentSize = directory[s] - segmentOffset;
      int segmentPos = directory[segments.length + s];

      int byteCount = Math.min(endIndex, segmentOffset + segmentSize) - pos;
      int offset = segmentPos + (pos - segmentOffset);
      action.accept(segments[s], offset, byteCount);
      pos += byteCount;
      s++;
    }
  }

  private interface SegmentAction {
    void accept(byte[] data, int offset, int byteCount);
  }
}
