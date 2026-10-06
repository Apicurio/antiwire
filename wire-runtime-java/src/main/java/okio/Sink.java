/*
 * Translated from okio/src/commonMain/kotlin/okio/Sink.kt at square/okio tag parent-3.18.2,
 * commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under the notices below.
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

import java.io.Closeable;
import java.io.IOException;

/**
 * Receives a stream of bytes. Use this interface to write data wherever it's needed: to the
 * network, storage, or a buffer in memory. Sinks may be layered to transform received data, such
 * as to compress, encrypt, throttle, or add protocol framing.
 */
public interface Sink extends Closeable {

  /** Removes {@code byteCount} bytes from {@code source} and appends them to this. */
  void write(Buffer source, long byteCount) throws IOException;

  /** Pushes all buffered bytes to their final destination. */
  void flush() throws IOException;

  /** Returns the timeout for this sink. */
  Timeout timeout();

  /**
   * Pushes all buffered bytes to their final destination and releases the resources held by this
   * sink. It is an error to write a closed sink. It is safe to close a sink more than once.
   */
  @Override void close() throws IOException;
}
