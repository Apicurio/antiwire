/*
 * Copyright (C) 2020 Square, Inc.
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
package okio;

import java.io.IOException;
import java.util.List;

/**
 * A {@link FileSystem} that forwards calls to another {@link FileSystem}. Subclasses override the
 * operations they need to intercept.
 *
 * <p>Port note: this is a trimmed port of upstream okio's {@code ForwardingFileSystem} covering
 * only the operations the vendored file system declares. Upstream's {@code onPathParameter}
 * path-mapping hook is omitted because this port has no file system that needs path translation
 * (its only subclass, the compiler's dry-run file system, uses identical paths in both systems).
 */
public abstract class ForwardingFileSystem extends FileSystem {
  private final FileSystem delegate;

  protected ForwardingFileSystem(FileSystem delegate) {
    if (delegate == null) throw new IllegalArgumentException("delegate == null");
    this.delegate = delegate;
  }

  /** The file system that operations are forwarded to. */
  public final FileSystem getDelegate() {
    return delegate;
  }

  @Override public void close() throws IOException {
    delegate.close();
  }

  @Override public Path canonicalize(Path path) throws IOException {
    return delegate.canonicalize(path);
  }

  @Override public FileMetadata metadataOrNull(Path path) throws IOException {
    return delegate.metadataOrNull(path);
  }

  @Override public List<Path> list(Path dir) throws IOException {
    return delegate.list(dir);
  }

  @Override public List<Path> listOrNull(Path dir) throws IOException {
    return delegate.listOrNull(dir);
  }

  @Override public Source source(Path file) throws IOException {
    return delegate.source(file);
  }

  @Override public Sink sink(Path file, boolean mustCreate) throws IOException {
    return delegate.sink(file, mustCreate);
  }

  @Override public Sink appendingSink(Path file, boolean mustExist) throws IOException {
    return delegate.appendingSink(file, mustExist);
  }

  @Override public void createDirectory(Path dir, boolean mustCreate) throws IOException {
    delegate.createDirectory(dir, mustCreate);
  }

  @Override public void atomicMove(Path source, Path target) throws IOException {
    delegate.atomicMove(source, target);
  }

  @Override public void delete(Path path, boolean mustExist) throws IOException {
    delegate.delete(path, mustExist);
  }
}
