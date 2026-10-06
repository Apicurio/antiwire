/*
 * Translated from okio/src/zlibMain/kotlin/okio/ZipFileSystem.kt at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under
 * the notices below. Package-private: upstream internal; read-only exactly like
 * upstream (sink, appendingSink, createDirectory, atomicMove, delete, createSymlink
 * all throw IOException).
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

/**
 * Read only access to a <a href="https://pkware.cachefly.net/webdocs/APPNOTE/APPNOTE_6.2.0.txt">zip
 * file</a> and common
 * <a href="https://opensource.apple.com/source/zip/zip-6/unzip/unzip/proginfo/extra.fld">extra
 * fields</a>.
 */
final class ZipFileSystem extends FileSystem {

  private static final Path ROOT = Path.get("/");

  private final Path zipPath;
  private final FileSystem fileSystem;
  private final Map<Path, ZipEntry> entries;
  private final String comment;

  ZipFileSystem(Path zipPath, FileSystem fileSystem, Map<Path, ZipEntry> entries, String comment) {
    this.zipPath = zipPath;
    this.fileSystem = fileSystem;
    this.entries = entries;
    this.comment = comment;
  }

  public String getComment() {
    return comment;
  }

  @Override public Path canonicalize(Path path) throws IOException {
    Path canonical = canonicalizeInternal(path);
    if (!entries.containsKey(canonical)) {
      throw new FileNotFoundException(path.toString());
    }
    return canonical;
  }

  /** Don't throw FileNotFoundException if the path doesn't identify a file. */
  private Path canonicalizeInternal(Path path) {
    return ROOT.resolve(path, true);
  }

  @Override public FileMetadata metadataOrNull(Path path) throws IOException {
    Path canonicalPath = canonicalizeInternal(path);
    ZipEntry centralDirectoryEntry = entries.get(canonicalPath);
    if (centralDirectoryEntry == null) {
      return null;
    }

    ZipEntry fullEntry = centralDirectoryEntry;
    if (centralDirectoryEntry.offset != -1L) {
      try (FileHandle fileHandle = fileSystem.openReadOnly(zipPath)) {
        try (BufferedSource source = Okio.buffer(fileHandle.source(centralDirectoryEntry.offset))) {
          fullEntry = ZipFiles.readLocalHeader(source, centralDirectoryEntry);
        }
      }
    }

    return new FileMetadata(
        !fullEntry.isDirectory,
        fullEntry.isDirectory,
        null,
        fullEntry.isDirectory ? null : fullEntry.size,
        fullEntry.createdAtMillis(),
        fullEntry.lastModifiedAtMillis(),
        fullEntry.lastAccessedAtMillis());
  }

  @Override public FileHandle openReadOnly(Path file) throws IOException {
    throw new UnsupportedOperationException("not implemented yet!");
  }

  @Override public FileHandle openReadWrite(Path file, boolean mustCreate, boolean mustExist)
      throws IOException {
    throw new IOException("zip entries are not writable");
  }

  @Override public List<Path> listOrNull(Path dir, boolean throwOnFailure) throws IOException {
    Path canonicalDir = canonicalizeInternal(dir);
    ZipEntry entry = entries.get(canonicalDir);
    if (entry == null) {
      if (throwOnFailure) {
        throw new IOException("not a directory: " + dir);
      }
      return null;
    }
    return new ArrayList<>(entry.children);
  }

  @Override public Source source(Path file) throws IOException {
    Path canonicalPath = canonicalizeInternal(file);
    ZipEntry entry = entries.get(canonicalPath);
    if (entry == null) {
      throw new FileNotFoundException("no such file: " + file);
    }
    BufferedSource source;
    FileHandle fileHandle = fileSystem.openReadOnly(zipPath);
    try {
      source = Okio.buffer(fileHandle.source(entry.offset));
    } finally {
      fileHandle.close();
    }
    ZipFiles.skipLocalHeader(source);

    if (entry.compressionMethod == ZipFiles.COMPRESSION_METHOD_STORED) {
      return Okio.limit(source, entry.size, false);
    } else {
      InflaterSource inflaterSource = new InflaterSource(
          Okio.limit(source, entry.compressedSize, false),
          new Inflater(true));
      return Okio.limit(inflaterSource, entry.size, true);
    }
  }

  @Override public Sink sink(Path file, boolean mustCreate) throws IOException {
    throw new IOException("zip file systems are read-only");
  }

  @Override public Sink appendingSink(Path file, boolean mustExist) throws IOException {
    throw new IOException("zip file systems are read-only");
  }

  @Override public void createDirectory(Path dir, boolean mustCreate) throws IOException {
    throw new IOException("zip file systems are read-only");
  }

  @Override public void atomicMove(Path source, Path target) throws IOException {
    throw new IOException("zip file systems are read-only");
  }

  @Override public void delete(Path path, boolean mustExist) throws IOException {
    throw new IOException("zip file systems are read-only");
  }

  @Override public void createSymlink(Path source, Path target) throws IOException {
    throw new IOException("zip file systems are read-only");
  }
}
