/*
 * Copyright (C) 2023 Square, Inc.
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

import java.io.IOException;
import okio.Buffer;
import okio.ForwardingFileSystem;
import okio.FileSystem;
import okio.Path;
import okio.Sink;

/**
 * This {@link FileSystem} reads from its {@linkplain #getDelegate() delegate} but its writing
 * operations do not produce anything.
 *
 * <p>Port note: upstream redirects writes into okio's in-memory FakeFileSystem; this port discards
 * them into a buffer, which preserves the observable behavior (nothing written reaches disk and
 * later reads never observe the write).
 */
public class DryRunFileSystem extends ForwardingFileSystem {
  public DryRunFileSystem(FileSystem delegate) {
    super(delegate);
  }

  @Override public Sink sink(Path file, boolean mustCreate) throws IOException {
    if (mustCreate && getDelegate().exists(file)) {
      throw new IOException("already exists: " + file);
    }
    return new Buffer();
  }

  @Override public Sink appendingSink(Path file, boolean mustExist) throws IOException {
    if (mustExist && !getDelegate().exists(file)) {
      throw new IOException("doesn't exist: " + file);
    }
    return new Buffer();
  }
}
