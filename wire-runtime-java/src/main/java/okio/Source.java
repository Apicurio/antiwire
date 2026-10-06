/*
 * Translated from okio/src/commonMain/kotlin/okio/Source.kt at square/okio tag parent-3.18.2,
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
 * Supplies a stream of bytes. Use this interface to read data from wherever it's located: from the
 * network, storage, or a buffer in memory. Sources may be layered to transform supplied data, such
 * as to decompress, decrypt, or remove protocol framing.
 */
public interface Source extends Closeable {

  /**
   * Removes at least 1, and up to {@code byteCount} bytes from this and appends them to
   * {@code sink}. Returns the number of bytes read, or -1 if this source is exhausted.
   */
  long read(Buffer sink, long byteCount) throws IOException;

  /** Returns the timeout for this source. */
  Timeout timeout();

  /**
   * Closes this source and releases the resources held by this source. It is an error to read a
   * closed source. It is safe to close a source more than once.
   */
  @Override void close() throws IOException;
}
