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

import java.io.IOException;
import okio.ByteString;

/**
 * Reads and decodes protocol message fields using an {@code int} as a cursor.
 *
 * <p>This is an alternative to {@link ProtoReader}, which uses {@code long} as a cursor. It
 * originates as an optimization for Kotlin/JS, where {@code long} cursors are prohibitively
 * expensive. It doesn't subclass {@link ProtoReader} because {@code beginMessage},
 * {@code endMessageAndGetUnknownFields}, and {@code nextFieldMinLengthInBytes} must operate on
 * the correct cursor type.
 */
public interface ProtoReader32 {
  /** Returns a [ProtoReader] that reads the same data as this using a different type. */
  ProtoReader asProtoReader();

  /**
   * Begin a nested message. A call to this method will restrict the reader so that [nextTag]
   * returns -1 when the message is complete. An accompanying call to
   * [endMessageAndGetUnknownFields] must then occur with the opaque token returned from this
   * method.
   */
  int beginMessage() throws IOException;

  /**
   * End a length-delimited nested message. Calls to this method must be symmetric with calls to
   * [beginMessage].
   *
   * @param token value returned from the corresponding call to [beginMessage].
   */
  ByteString endMessageAndGetUnknownFields(int token) throws IOException;

  /** Reads and returns the length of the next message in a length-delimited stream. */
  int nextLengthDelimited() throws IOException;

  /**
   * Reads and returns the next tag of the message, or -1 if there are no further tags. Use
   * [peekFieldEncoding] after calling this method to query its encoding. This silently skips
   * groups.
   */
  int nextTag() throws IOException;

  /** Returns the encoding of the next field value. [nextTag] must be called before this method. */
  FieldEncoding peekFieldEncoding();

  /**
   * Skips the current field's value. This is only safe to call immediately following a call to
   * [nextTag].
   */
  void skip() throws IOException;

  /**
   * Reads a {@code bytes} field value from the stream. The length is read from the stream prior
   * to the actual data.
   */
  ByteString readBytes() throws IOException;

  /**
   * Prepares to read a value and returns true if the read should proceed. If there's nothing to
   * read (because a packed value has length 0), this will clear the reader state.
   */
  boolean beforePossiblyPackedScalar() throws IOException;

  /** Reads a {@code string} field value from the stream. */
  String readString() throws IOException;

  /** Reads a raw varint from the stream. If larger than 32 bits, discard the upper bits. */
  int readVarint32() throws IOException;

  /** Reads a raw varint up to 64 bits in length from the stream. */
  long readVarint64() throws IOException;

  /** Reads a 32-bit little-endian integer from the stream. */
  int readFixed32() throws IOException;

  /** Reads a 64-bit little-endian integer from the stream. */
  long readFixed64() throws IOException;

  /**
   * Read an unknown field and store temporarily. Once the entire message is read, call
   * [endMessageAndGetUnknownFields] to retrieve unknown fields.
   */
  void readUnknownField(int tag) throws IOException;

  /**
   * Store an already read field temporarily. Once the entire message is read, call
   * [endMessageAndGetUnknownFields] to retrieve unknown fields.
   */
  void addUnknownField(int tag, FieldEncoding fieldEncoding, Object value) throws IOException;

  /**
   * Returns the min length of the next field in bytes. Some encodings have a fixed length,
   * while others have a variable length. LENGTH_DELIMITED fields have a known variable length,
   * while VARINT fields could be as small as a single byte.
   */
  int nextFieldMinLengthInBytes() throws IOException;
}
