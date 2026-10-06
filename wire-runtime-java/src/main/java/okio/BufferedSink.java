/*
 * Translated from okio/src/commonMain/kotlin/okio/BufferedSink.kt and
 * okio/src/jvmMain/kotlin/okio/BufferedSink.kt at square/okio tag parent-3.18.2,
 * commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under the notices below.
 * Translation notes for the M0 subset: utf8Appendable() is excluded (no wire main or wire
 * test use at the pin); the retained members match the wire runtime's encoding surface
 * (writeByte/write/Le-variants/writeUtf8/writeAll/emit) and the generated-code contract.
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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

public interface BufferedSink extends Sink {

  /** This sink's internal buffer. */
  Buffer buffer();

  void write(Buffer source, long byteCount) throws IOException;

  BufferedSink write(ByteString byteString) throws IOException;

  BufferedSink write(ByteString byteString, int offset, int byteCount) throws IOException;

  BufferedSink writeUtf8(String string) throws IOException;

  BufferedSink writeUtf8(String string, int beginIndex, int endIndex) throws IOException;

  BufferedSink writeUtf8CodePoint(int codePoint) throws IOException;

  BufferedSink writeString(String string, Charset charset) throws IOException;

  BufferedSink write(byte[] source) throws IOException;

  BufferedSink write(byte[] source, int offset, int byteCount) throws IOException;

  long writeAll(Source source) throws IOException;

  BufferedSink write(Source source, long byteCount) throws IOException;

  BufferedSink writeByte(int b) throws IOException;

  BufferedSink writeShort(int s) throws IOException;

  BufferedSink writeShortLe(int s) throws IOException;

  BufferedSink writeInt(int i) throws IOException;

  BufferedSink writeIntLe(int i) throws IOException;

  BufferedSink writeLong(long v) throws IOException;

  BufferedSink writeLongLe(long v) throws IOException;

  BufferedSink writeDecimalLong(long v) throws IOException;

  BufferedSink writeHexadecimalUnsignedLong(long v) throws IOException;

  /**
   * Removes all bytes from the internal buffer and delivers them to the sink. This method blocks
   * until the bytes are actually written to the sink.
   */
  BufferedSink emitCompleteSegments() throws IOException;

  BufferedSink emit() throws IOException;

  void flush() throws IOException;

  Timeout timeout();

  OutputStream outputStream();
}
