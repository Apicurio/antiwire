// This class is derived from the CodedInputByteBuffer class in Google's "Nano" Protocol Buffer
// implementation. The original copyright notice, list of conditions, and disclaimer for those
// classes is as follows:

// Protocol Buffers - Google's data interchange format
// Copyright 2013 Google Inc.  All rights reserved.
// http://code.google.com/p/protobuf/
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
// * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
// * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
package com.squareup.wire;

import com.squareup.wire.internal.ProtocolException;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import okio.Buffer;
import okio.ByteString;

/**
 * A [ProtoReader32] over a byte array. Upstream declares this Kotlin-internal; it is public here
 * per the translation conventions.
 */
public final class ByteArrayProtoReader32 implements ProtoReader32 {
  private final byte[] source;

  /** The current position in [source], starting at 0 and increasing monotonically. */
  private int pos;

  /** The absolute position of the end of the current message. */
  private int limit;

  /** The current number of levels of message nesting. */
  private int recursionDepth;

  /** How to interpret the next read call. */
  private int state = STATE_LENGTH_DELIMITED;

  /** The most recently read tag. Used to make packed values look like regular values. */
  private int tag = -1;

  /** Limit once we complete the current length-delimited value. */
  private int pushedLimit = -1;

  /** The encoding of the next value to be read. */
  private FieldEncoding nextFieldEncoding;

  /** Pooled buffers for unknown fields, indexed by [recursionDepth]. */
  private final List<Buffer> bufferStack = new ArrayList<>();

  /** Lazily-initialized. */
  private ProtoReader32AsProtoReader protoReader;

  public ByteArrayProtoReader32(byte[] source) {
    this(source, 0, source.length);
  }

  public ByteArrayProtoReader32(byte[] source, int pos, int limit) {
    if (!(pos >= 0 && pos <= source.length)) {
      throw new IllegalArgumentException(
          "pos=" + pos + " must be between 0 and source size " + source.length);
    }
    if (!(limit >= pos && limit <= source.length)) {
      throw new IllegalArgumentException(
          "limit=" + limit + " must be between pos=" + pos + " and source size " + source.length);
    }
    this.source = source;
    this.pos = pos;
    this.limit = limit;
  }

  @Override public ProtoReader asProtoReader() {
    if (protoReader == null) {
      protoReader = new ProtoReader32AsProtoReader(this);
    }
    return protoReader;
  }

  @Override public int beginMessage() throws IOException {
    if (state != STATE_LENGTH_DELIMITED) {
      throw new IllegalStateException("Unexpected call to beginMessage()");
    }
    if (++recursionDepth > RECURSION_LIMIT) {
      throw new IOException("Wire recursion limit exceeded");
    }
    // Allocate a buffer to store unknown fields encountered at this recursion level.
    if (recursionDepth > bufferStack.size()) bufferStack.add(new Buffer());
    // Give the pushed limit to the caller to hold. The value is returned in endMessage() where we
    // resume using it as our limit.
    int token = pushedLimit;
    pushedLimit = -1;
    state = STATE_TAG;
    return token;
  }

  @Override public ByteString endMessageAndGetUnknownFields(int token) throws IOException {
    if (state != STATE_TAG) {
      throw new IllegalStateException("Unexpected call to endMessage()");
    }
    if (!(--recursionDepth >= 0 && pushedLimit == -1)) {
      throw new IllegalStateException("No corresponding call to beginMessage()");
    }
    if (pos != limit && recursionDepth != 0) {
      throw new IOException("Expected to end at " + limit + " but was " + pos);
    }
    limit = token;
    Buffer unknownFieldsBuffer = bufferStack.get(recursionDepth);
    if (unknownFieldsBuffer.size() > 0L) {
      return unknownFieldsBuffer.readByteString();
    } else {
      return ByteString.EMPTY;
    }
  }

  @Override public int nextLengthDelimited() throws IOException {
    if (!(state == STATE_TAG || state == STATE_LENGTH_DELIMITED)) {
      throw new IllegalStateException("Unexpected call to nextDelimited()");
    }
    return internalNextLengthDelimited();
  }

  private int internalNextLengthDelimited() throws IOException {
    nextFieldEncoding = FieldEncoding.LENGTH_DELIMITED;
    state = STATE_LENGTH_DELIMITED;
    int length = internalReadVarint32();
    requireNonNegativeLength(length);
    if (pushedLimit != -1) throw new IllegalStateException();
    int newLimit = checkedLimit(length);
    // Push the current limit, and set a new limit to the length of this value.
    pushedLimit = limit;
    limit = newLimit;
    return length;
  }

  @Override public int nextTag() throws IOException {
    if (state == STATE_PACKED_TAG) {
      state = STATE_LENGTH_DELIMITED;
      return tag;
    } else if (state != STATE_TAG) {
      throw new IllegalStateException("Unexpected call to nextTag()");
    }

    while (pos < limit) {
      int tagAndFieldEncoding = internalReadVarint32();
      if (tagAndFieldEncoding == 0) {
        throw new ProtocolException(
            "Unexpected tag 0. Reader position: " + pos + ". Last read tag: " + tag + ".");
      }

      tag = tagAndFieldEncoding >>> ProtoReader.TAG_FIELD_ENCODING_BITS;
      int groupOrFieldEncoding = tagAndFieldEncoding & ProtoReader.FIELD_ENCODING_MASK;
      switch (groupOrFieldEncoding) {
        case ProtoReader.STATE_START_GROUP:
          skipGroup(tag);
          continue;

        case ProtoReader.STATE_END_GROUP:
          throw new ProtocolException(
              "Unexpected end group. Reader position: " + pos + ". Last read tag: " + tag + ".");

        case ProtoReader.STATE_LENGTH_DELIMITED:
          internalNextLengthDelimited();
          return tag;

        case ProtoReader.STATE_VARINT:
          nextFieldEncoding = FieldEncoding.VARINT;
          state = STATE_VARINT;
          return tag;

        case ProtoReader.STATE_FIXED64:
          nextFieldEncoding = FieldEncoding.FIXED64;
          state = STATE_FIXED64;
          return tag;

        case ProtoReader.STATE_FIXED32:
          nextFieldEncoding = FieldEncoding.FIXED32;
          state = STATE_FIXED32;
          return tag;

        default:
          throw new ProtocolException("Unexpected field encoding: " + groupOrFieldEncoding
              + ". Reader position: " + pos + ". Last read tag: " + tag + ".");
      }
    }
    return -1;
  }

  @Override public FieldEncoding peekFieldEncoding() {
    return nextFieldEncoding;
  }

  @Override public void skip() throws IOException {
    switch (state) {
      case STATE_LENGTH_DELIMITED: {
        int byteCount = beforeLengthDelimitedScalar();
        skip(byteCount);
        break;
      }
      case STATE_VARINT:
        readVarint64();
        break;
      case STATE_FIXED64:
        readFixed64();
        break;
      case STATE_FIXED32:
        readFixed32();
        break;
      default:
        throw new IllegalStateException("Unexpected call to skip()");
    }
  }

  /** Skips a section of the input delimited by START_GROUP/END_GROUP type markers. */
  private void skipGroup(int expectedEndTag) throws IOException {
    while (pos < limit) {
      int tagAndFieldEncoding = internalReadVarint32();
      if (tagAndFieldEncoding == 0) {
        throw new ProtocolException(
            "Unexpected tag 0. Reader position: " + pos + ". Last read tag: " + tag + ".");
      }
      int tag = tagAndFieldEncoding >>> ProtoReader.TAG_FIELD_ENCODING_BITS;
      int groupOrFieldEncoding = tagAndFieldEncoding & ProtoReader.FIELD_ENCODING_MASK;
      switch (groupOrFieldEncoding) {
        case ProtoReader.STATE_START_GROUP:
          recursionDepth++;
          try {
            if (recursionDepth > RECURSION_LIMIT) {
              throw new IOException("Wire recursion limit exceeded");
            }
            // Nested group.
            skipGroup(tag);
          } finally {
            recursionDepth--;
          }
          break;

        case ProtoReader.STATE_END_GROUP:
          if (tag == expectedEndTag) return; // Success!
          throw new ProtocolException(
              "Unexpected end group. Reader position: " + pos + ". Last read tag: " + tag + ".");

        case ProtoReader.STATE_LENGTH_DELIMITED: {
          int length = internalReadVarint32();
          requireNonNegativeLength(length, tag);
          skip(length);
          break;
        }

        case ProtoReader.STATE_VARINT:
          state = STATE_VARINT;
          readVarint64();
          break;

        case ProtoReader.STATE_FIXED64:
          state = STATE_FIXED64;
          readFixed64();
          break;

        case ProtoReader.STATE_FIXED32:
          state = STATE_FIXED32;
          readFixed32();
          break;

        default:
          throw new ProtocolException("Unexpected field encoding: " + groupOrFieldEncoding
              + ". Reader position: " + pos + ". Last read tag: " + tag + ".");
      }
    }
    throw new EOFException();
  }

  @Override public ByteString readBytes() throws IOException {
    int byteCount = beforeLengthDelimitedScalar();
    return readByteString(byteCount);
  }

  @Override public Bytes readBytesAsBytes() throws IOException {
    int byteCount = beforeLengthDelimitedScalar();
    int newPos = checkedLimit(byteCount);
    if (pos == 0 && newPos == source.length) {
      // The value IS the array: adopt it instead of copying (TASK-28). The array is assigned
      // once at construction and never mutated, so aliasing it in Bytes is safe; pos still
      // advances, so a later read correctly hits EOF.
      Bytes result = Bytes.takeOwnership(source);
      pos = newPos;
      return result;
    }
    Bytes result = Bytes.takeOwnership(java.util.Arrays.copyOfRange(source, pos, newPos));
    pos = newPos;
    return result;
  }

  @Override public boolean beforePossiblyPackedScalar() throws IOException {
    switch (state) {
      case STATE_LENGTH_DELIMITED:
        if (pos < limit) {
          // It's packed and there's a value.
          return true;
        } else {
          // It's packed and there aren't any values.
          limit = pushedLimit;
          pushedLimit = -1;
          state = STATE_TAG;
          return false;
        }

      case STATE_VARINT:
      case STATE_FIXED64:
      case STATE_FIXED32:
        return true; // Not packed.

      default:
        throw new ProtocolException("unexpected state: " + state + ". Reader position: " + pos
            + ". Last read tag: " + tag + ".");
    }
  }

  @Override public String readString() throws IOException {
    int byteCount = beforeLengthDelimitedScalar();
    return readUtf8(byteCount);
  }

  @Override public int readVarint32() throws IOException {
    if (state != STATE_VARINT && state != STATE_LENGTH_DELIMITED) {
      throw new ProtocolException("Expected VARINT or LENGTH_DELIMITED but was " + state
          + ". Reader position: " + pos + ". Last read tag: " + tag + ".");
    }
    int result = internalReadVarint32();
    afterPackableScalar(STATE_VARINT);
    return result;
  }

  private int internalReadVarint32() throws IOException {
    int tmp = readByte();
    if (tmp >= 0) {
      return tmp;
    }
    int result = tmp & 0x7f;
    tmp = readByte();
    if (tmp >= 0) {
      result |= tmp << 7;
    } else {
      result |= (tmp & 0x7f) << 7;
      tmp = readByte();
      if (tmp >= 0) {
        result |= tmp << 14;
      } else {
        result |= (tmp & 0x7f) << 14;
        tmp = readByte();
        if (tmp >= 0) {
          result |= tmp << 21;
        } else {
          result |= (tmp & 0x7f) << 21;
          tmp = readByte();
          result |= tmp << 28;
          if (tmp < 0) {
            // Discard upper 32 bits.
            for (int i = 0; i < 5; i++) {
              if (readByte() >= 0) {
                return result;
              }
            }
            throw new ProtocolException(
                "Malformed VARINT. Reader position: " + pos + ". Last read tag: " + tag + ".");
          }
        }
      }
    }
    return result;
  }

  @Override public long readVarint64() throws IOException {
    if (state != STATE_VARINT && state != STATE_LENGTH_DELIMITED) {
      throw new ProtocolException("Expected VARINT or LENGTH_DELIMITED but was " + state
          + ". Reader position: " + pos + ". Last read tag: " + tag + ".");
    }
    int shift = 0;
    long result = 0;
    while (shift < 64) {
      int b = readByte();
      result |= (long) (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        afterPackableScalar(STATE_VARINT);
        return result;
      }
      shift += 7;
    }
    throw new ProtocolException("WireInput encountered a malformed varint. Reader position: "
        + pos + ". Last read tag: " + tag + ".");
  }

  @Override public int readFixed32() throws IOException {
    if (state != STATE_FIXED32 && state != STATE_LENGTH_DELIMITED) {
      throw new ProtocolException("Expected FIXED32 or LENGTH_DELIMITED but was " + state
          + ". Reader position: " + pos + ". Last read tag: " + tag + ".");
    }
    int result = readIntLe();
    afterPackableScalar(STATE_FIXED32);
    return result;
  }

  @Override public long readFixed64() throws IOException {
    if (state != STATE_FIXED64 && state != STATE_LENGTH_DELIMITED) {
      throw new ProtocolException("Expected FIXED64 or LENGTH_DELIMITED but was " + state
          + ". Reader position: " + pos + ". Last read tag: " + tag + ".");
    }
    long result = readLongLe();
    afterPackableScalar(STATE_FIXED64);
    return result;
  }

  private void afterPackableScalar(int fieldEncoding) throws IOException {
    if (state == fieldEncoding) {
      state = STATE_TAG;
    } else {
      if (pos > limit) {
        throw new IOException("Expected to end at " + limit + " but was " + pos);
      } else if (pos == limit) {
        // We've completed a sequence of packed values. Pop the limit.
        limit = pushedLimit;
        pushedLimit = -1;
        state = STATE_TAG;
      } else {
        state = STATE_PACKED_TAG;
      }
    }
  }

  private int beforeLengthDelimitedScalar() throws IOException {
    if (state != STATE_LENGTH_DELIMITED) {
      throw new ProtocolException("Expected LENGTH_DELIMITED but was " + state
          + ". Reader position: " + pos + ". Last read tag: " + tag + ".");
    }
    int byteCount = remainingInLimit();
    state = STATE_TAG;
    // We've completed a length-delimited scalar. Pop the limit.
    limit = pushedLimit;
    pushedLimit = -1;
    return byteCount;
  }

  @Override public void readUnknownField(int tag) throws IOException {
    FieldEncoding fieldEncoding = peekFieldEncoding();
    ProtoAdapter<?> protoAdapter = fieldEncoding.rawProtoAdapter();
    Object value = protoAdapter.decode(this);
    addUnknownField(tag, fieldEncoding, value);
  }

  @Override public void addUnknownField(int tag, FieldEncoding fieldEncoding, Object value)
      throws IOException {
    ProtoWriter unknownFieldsWriter = new ProtoWriter(bufferStack.get(recursionDepth - 1));
    @SuppressWarnings("unchecked")
    ProtoAdapter<Object> protoAdapter = (ProtoAdapter<Object>) fieldEncoding.rawProtoAdapter();
    protoAdapter.encodeWithTag(unknownFieldsWriter, tag, value);
  }

  @Override public int nextFieldMinLengthInBytes() throws IOException {
    if (nextFieldEncoding == null) {
      throw new IllegalStateException("nextFieldEncoding is not set");
    }
    switch (nextFieldEncoding) {
      case LENGTH_DELIMITED:
        return remainingInLimit();
      case FIXED32:
        return 4;
      case FIXED64:
        return 8;
      case VARINT:
        return 1;
      default:
        throw new AssertionError();
    }
  }

  private void skip(int byteCount) throws IOException {
    pos = checkedLimit(byteCount);
  }

  private ByteString readByteString(int byteCount) throws IOException {
    int newPos = checkedLimit(byteCount);
    ByteString result = ByteString.of(source, pos, byteCount);
    pos = newPos;
    return result;
  }

  private String readUtf8(int byteCount) throws IOException {
    int newPos = checkedLimit(byteCount);
    String result = new String(source, pos, byteCount, StandardCharsets.UTF_8);
    pos = newPos;
    return result;
  }

  private int readByte() throws IOException {
    checkedLimit(1);
    return source[pos++];
  }

  private int readIntLe() throws IOException {
    checkedLimit(4);

    int result = (source[pos++] & 0xff)
        | ((source[pos++] & 0xff) << 8)
        | ((source[pos++] & 0xff) << 16)
        | ((source[pos++] & 0xff) << 24);

    return result;
  }

  private long readLongLe() throws IOException {
    checkedLimit(8);

    long result = (source[pos++] & 0xffL)
        | ((source[pos++] & 0xffL) << 8)
        | ((source[pos++] & 0xffL) << 16)
        | ((source[pos++] & 0xffL) << 24)
        | ((source[pos++] & 0xffL) << 32)
        | ((source[pos++] & 0xffL) << 40)
        | ((source[pos++] & 0xffL) << 48)
        | ((source[pos++] & 0xffL) << 56);

    return result;
  }

  private void requireNonNegativeLength(int length) throws ProtocolException {
    requireNonNegativeLength(length, tag);
  }

  private void requireNonNegativeLength(int length, int lastReadTag) throws ProtocolException {
    if (length < 0) {
      throw new ProtocolException("Negative length: " + length + ". Reader position: " + pos
          + ". Last read tag: " + lastReadTag + ".");
    }
  }

  private int checkedLimit(int byteCount) throws EOFException {
    if (byteCount < 0 || byteCount > remainingInLimit()) throw new EOFException();
    return pos + byteCount;
  }

  private int remainingInLimit() throws EOFException {
    if (pos > limit) throw new EOFException();
    return limit - pos;
  }

  private static final int RECURSION_LIMIT = ProtoReader.RECURSION_LIMIT;
  private static final int STATE_VARINT = ProtoReader.STATE_VARINT;
  private static final int STATE_FIXED64 = ProtoReader.STATE_FIXED64;
  private static final int STATE_FIXED32 = ProtoReader.STATE_FIXED32;
  private static final int STATE_LENGTH_DELIMITED = ProtoReader.STATE_LENGTH_DELIMITED;
  private static final int STATE_TAG = ProtoReader.STATE_TAG;
  private static final int STATE_PACKED_TAG = ProtoReader.STATE_PACKED_TAG;
}
