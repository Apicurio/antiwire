/*
 * Translated from okio/src/jvmMain/kotlin/okio/JvmSystemFileSystem.kt at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under
 * the notices below. Package-private: upstream internal open class.
 *
 * Copyright (C) 2020 Square, Inc.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class JvmSystemFileSystem extends FileSystem {

  @Override public Path canonicalize(Path path) throws IOException {
    java.io.File canonicalFile = path.toFile().getCanonicalFile();
    if (!canonicalFile.exists()) {
      throw new FileNotFoundException("no such file");
    }
    return Path.get(canonicalFile);
  }

  @Override public FileMetadata metadataOrNull(Path path) throws IOException {
    java.io.File file = path.toFile();
    boolean isRegularFile = file.isFile();
    boolean isDirectory = file.isDirectory();
    long lastModifiedAtMillis = file.lastModified();
    long size = file.length();

    if (!isRegularFile
        && !isDirectory
        && lastModifiedAtMillis == 0L
        && size == 0L
        && !file.exists()) {
      return null;
    }

    return new FileMetadata(
        isRegularFile,
        isDirectory,
        null,
        size,
        null,
        lastModifiedAtMillis == 0L ? null : lastModifiedAtMillis,
        null);
  }

  @Override public List<Path> listOrNull(Path dir, boolean throwOnFailure) throws IOException {
    java.io.File file = dir.toFile();
    String[] entries = file.list();
    if (entries == null) {
      if (throwOnFailure) {
        if (!file.exists()) {
          throw new FileNotFoundException("no such file: " + dir);
        }
        throw new IOException("failed to list " + dir);
      } else {
        return null;
      }
    }
    List<Path> result = new ArrayList<>();
    for (String entry : entries) {
      result.add(dir.resolve(entry));
    }
    Collections.sort(result);
    return result;
  }

  @Override public FileHandle openReadOnly(Path file) throws IOException {
    return new JvmFileHandle(false, new RandomAccessFile(file.toFile(), "r"));
  }

  @Override public FileHandle openReadWrite(Path file, boolean mustCreate, boolean mustExist)
      throws IOException {
    if (mustCreate && mustExist) {
      throw new IllegalArgumentException(
          "Cannot require mustCreate and mustExist at the same time.");
    }
    if (mustCreate) {
      requireCreate(file);
    }
    if (mustExist) {
      requireExist(file);
    }
    return new JvmFileHandle(true, new RandomAccessFile(file.toFile(), "rw"));
  }

  @Override public Source source(Path file) throws IOException {
    InputStream inputStream = new java.io.FileInputStream(file.toFile());
    return Okio.source(inputStream);
  }

  @Override public Sink sink(Path file, boolean mustCreate) throws IOException {
    if (mustCreate) {
      requireCreate(file);
    }
    OutputStream out = new java.io.FileOutputStream(file.toFile(), false);
    return Okio.sink(out);
  }

  @Override public Sink appendingSink(Path file, boolean mustExist) throws IOException {
    if (mustExist) {
      requireExist(file);
    }
    OutputStream out = new java.io.FileOutputStream(file.toFile(), true);
    return Okio.sink(out);
  }

  @Override public void createDirectory(Path dir, boolean mustCreate) throws IOException {
    if (!dir.toFile().mkdir()) {
      FileMetadata metadata = metadataOrNull(dir);
      boolean alreadyExist = metadata != null && Boolean.TRUE.equals(metadata.isDirectory);
      if (alreadyExist) {
        if (mustCreate) {
          throw new IOException(dir + " already exists.");
        } else {
          return;
        }
      }
      throw new IOException("failed to create directory: " + dir);
    }
  }

  @Override public void atomicMove(Path source, Path target) throws IOException {
    // Note that on Windows, this will fail if [target] already exists.
    boolean renamed = source.toFile().renameTo(target.toFile());
    if (!renamed) {
      throw new IOException("failed to move " + source + " to " + target);
    }
  }

  @Override public void delete(Path path, boolean mustExist) throws IOException {
    if (Thread.interrupted()) {
      Thread.currentThread().interrupt(); // Retain interrupted status.
      throw new InterruptedIOException("interrupted");
    }
    java.io.File file = path.toFile();
    boolean deleted = file.delete();
    if (!deleted) {
      if (file.exists()) {
        throw new IOException("failed to delete " + path);
      }
      if (mustExist) {
        throw new FileNotFoundException("no such file: " + path);
      }
    }
  }

  @Override public void createSymlink(Path source, Path target) throws IOException {
    throw new IOException("unsupported");
  }

  @Override public String toString() {
    return "JvmSystemFileSystem";
  }

  // We have to implement existence verification non-atomically on the JVM because there's no API
  // to do so.
  private void requireExist(Path path) throws IOException {
    if (!exists(path)) {
      throw new IOException(path + " doesn't exist.");
    }
  }

  private void requireCreate(Path path) throws IOException {
    if (exists(path)) {
      throw new IOException(path + " already exists.");
    }
  }
}
