/*
 * Translated from okio/src/commonMain/kotlin/okio/BufferedSource.kt and
 * okio/src/jvmMain/kotlin/okio/BufferedSource.kt at square/okio tag parent-3.18.2,
 * commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under the notices below.
 * Translation notes for the M0 subset: peek(), the readDecimalLong/readHexadecimalUnsignedLong
 * surface (they are on Buffer and RealBufferedSource; the interface keeps them), the typed
 * select overload, utf8Appendable, and the long-form indexOf/toIndex overloads retained below
 * match the members the wire runtime, the wire-schema loader (select for BOM detection), and
 * the adapted behavior tests use. peek() and PeekSource are excluded (no wire main or wire
 * test use at the pin); the typed select overload is excluded (no wire use).
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
import java.nio.charset.Charset;

public interface BufferedSource extends Source {

  /** This source's internal buffer. */
  Buffer buffer();

  boolean exhausted() throws IOException;

  /** @throws EOFException if insufficient bytes are available. */
  void require(long byteCount) throws IOException;

  boolean request(long byteCount) throws IOException;

  byte readByte() throws IOException;

  short readShort() throws IOException;

  short readShortLe() throws IOException;

  int readInt() throws IOException;

  int readIntLe() throws IOException;

  long readLong() throws IOException;

  long readLongLe() throws IOException;

  long readDecimalLong() throws IOException;

  long readHexadecimalUnsignedLong() throws IOException;

  /** Reads a code point from a UTF-8 stream. Returns the replacement character on invalid input. */
  int readUtf8CodePoint() throws IOException;

  ByteString readByteString() throws IOException;

  /** @throws EOFException if insufficient bytes are available. */
  ByteString readByteString(long byteCount) throws IOException;

  /**
   * Removes all bytes buffered and returns them as a byte string. Equivalent to
   * {@code readByteString(buffer().size())}.
   */
  byte[] readByteArray() throws IOException;

  /** @throws EOFException if insufficient bytes are available. */
  byte[] readByteArray(long byteCount) throws IOException;

  int read(byte[] sink) throws IOException;

  void readFully(byte[] sink) throws IOException;

  int read(byte[] sink, int offset, int byteCount) throws IOException;

  void readFully(Buffer sink, long byteCount) throws IOException;

  long readAll(Sink sink) throws IOException;

  /** Removes all bytes from this and returns them as a string decoded as UTF-8. */
  String readUtf8() throws IOException;

  String readUtf8(long byteCount) throws IOException;

  String readString(Charset charset) throws IOException;

  String readString(long byteCount, Charset charset) throws IOException;

  String readUtf8Line() throws IOException;

  /**
   * Returns the index of {@code b} in this at or beyond {@code fromIndex}, or -1 if this buffer
   * does not contain {@code b} in that range.
   */
  long indexOf(byte b) throws IOException;

  long indexOf(byte b, long fromIndex) throws IOException;

  long indexOf(byte b, long fromIndex, long toIndex) throws IOException;

  long indexOf(ByteString bytes) throws IOException;

  long indexOf(ByteString bytes, long fromIndex) throws IOException;

  long indexOfElement(ByteString targetBytes) throws IOException;

  long indexOfElement(ByteString targetBytes, long fromIndex) throws IOException;

  boolean rangeEquals(long offset, ByteString bytes) throws IOException;

  boolean rangeEquals(long offset, ByteString bytes, int bytesOffset, int byteCount)
      throws IOException;

  /**
   * Returns the index of a value in {@code options} that is a prefix of this buffer. Returns -1 if
   * no value is found.
   */
  int select(Options options) throws IOException;

  void skip(long byteCount) throws IOException;

  Timeout timeout();

  InputStream inputStream();
}
