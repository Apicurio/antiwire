/*
 * Copyright (C) 2026 the antiwire authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.squareup.wire;

import java.io.Closeable;
import java.io.IOException;

/** A source that reads messages of type {@code T}. Mirrors upstream's {@code MessageSource}. */
public interface MessageSource<T> extends Closeable {
  /** Returns the next message, or null when the source is exhausted. */
  T read() throws IOException;
}
