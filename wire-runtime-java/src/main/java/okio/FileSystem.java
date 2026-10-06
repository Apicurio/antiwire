/*
 * Translated from okio/src/commonMain/kotlin/okio/FileSystem.kt and
 * okio/src/jvmMain/kotlin/okio/FileSystem.kt (with the common helpers of
 * okio/src/commonMain/kotlin/okio/internal/FileSystem.kt inlined) at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67.
 * Derivative work under the notices below. Translation notes for the M0 subset: the
 * Kotlin inline lambda forms read(file) { ... } / write(file) { ... } become
 * read(Path, ReaderFunction) and write(Path, boolean, WriterFunction) with functional
 * interfaces; deleteRecursively and the asOkioFileSystem NIO bridge are excluded
 * (deleteRecursively has no wire main use at the pin; the NIO bridge is the TASK-12
 * loading-boundary owner, docs/decisions.md OPEN-1). openZip lands here as the static
 * entry point the wire-schema Roots loader calls. listRecursively returns an eager
 * Iterable instead of a lazy Kotlin Sequence: the loader consumes it fully, so the
 * observable behavior is identical unless the tree mutates mid-iteration, which the
 * loader never does. The list/listOrNull pair collapses into one abstract
 * listOrNull(dir, throwOnFailure) so subclasses implement a single traversal.
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A provider of file system functionality.
 *
 * <p>This interface provides functionality for reading, writing, and manipulating files and
 * directories. The {@link #SYSTEM SYSTEM} implementation of this API is provided by the host
 * machine.
 */
public abstract class FileSystem implements java.io.Closeable {

  /**
   * The current machine's local file system. This is a {@code NioSystemFileSystem} on JVMs with
   * {@code java.nio.file.Files}, and a {@code JvmSystemFileSystem} otherwise.
   */
  public static final FileSystem SYSTEM = createSystemFileSystem();

  /**
   * The host machine's preferred directory for temporary files. The returned directory may or may
   * not exist.
   */
  public static final Path SYSTEM_TEMPORARY_DIRECTORY =
      Path.get(System.getProperty("java.io.tmpdir"));

  /**
   * A file system holding the files of a JAR ({@code .jar} or {@code .apk}) or ZIP ({@code .zip})
   * on the local file system.
   */
  public static FileSystem openZip(Path zipPath) throws IOException {
    return ZipFiles.openZip(zipPath, SYSTEM, null);
  }

  /**
   * A file system holding the files of a JAR or ZIP in {@code fileSystem}, filtered by
   * {@code predicate}. A null predicate keeps all entries.
   */
  static FileSystem openZip(Path zipPath, FileSystem fileSystem, ZipFiles.EntryPredicate predicate)
      throws IOException {
    return ZipFiles.openZip(zipPath, fileSystem, predicate);
  }

  /** Returns a file system exposing Java classpath resources. */
  public static FileSystem asResourceFileSystem(ClassLoader classLoader) throws IOException {
    return new ResourceFileSystem(classLoader, true);
  }

  private static FileSystem createSystemFileSystem() {
    try {
      Class.forName("java.nio.file.Files");
      return new NioSystemFileSystem();
    } catch (ClassNotFoundException e) {
      return new JvmSystemFileSystem();
    }
  }

  /**
   * Returns the path that {@code path} refers to. This is an identity transformation on
   * well-behaved file systems.
   */
  public abstract Path canonicalize(Path path) throws IOException;

  /**
   * Returns metadata of the file, directory, or object identified by {@code path}.
   *
   * @throws IOException if {@code path} does not exist or its metadata cannot be read.
   */
  public FileMetadata metadata(Path path) throws IOException {
    FileMetadata metadataOrNull = metadataOrNull(path);
    if (metadataOrNull == null) {
      throw new FileNotFoundException("no such file: " + path);
    }
    return metadataOrNull;
  }

  /**
   * Returns metadata of the file, directory, or object identified by {@code path}, or null if it
   * doesn't exist.
   */
  public abstract FileMetadata metadataOrNull(Path path) throws IOException;

  /**
   * Returns true if the file at {@code path} exists. This is a boolean-valued alternative to
   * {@link #metadataOrNull}.
   */
  public boolean exists(Path path) throws IOException {
    return metadataOrNull(path) != null;
  }

  /**
   * Returns the names of files and directories in {@code dir}. The returned list is sorted
   * naturally and is a copy that can be mutated freely.
   */
  public final List<Path> list(Path dir) throws IOException {
    return listOrNull(dir, true);
  }

  /**
   * Returns the names of the files and directories in {@code dir}. The returned list can be
   * mutated freely.
   *
   * @throws IOException if {@code dir} does not exist, or if it is not a directory.
   */
  public List<Path> listOrNull(Path dir) throws IOException {
    return listOrNull(dir, false);
  }

  public abstract List<Path> listOrNull(Path dir, boolean throwOnFailure) throws IOException;

  /**
   * Returns a sequence of all files in {@code dir} and its descendants. The returned
   * collection is computed eagerly (an Iterable rather than upstream's lazy Kotlin
   * Sequence; see the translation note in this class header), so any traversal failure,
   * including a symlink cycle, throws from this call itself rather than from iteration.
   *
   * @throws IOException if {@code dir} does not exist, or if it is not a directory.
   */
  public final Iterable<Path> listRecursively(Path dir) throws IOException {
    return listRecursively(dir, false);
  }

  public Iterable<Path> listRecursively(Path dir, boolean followSymlinks) throws IOException {
    final Deque<Path> stack = new ArrayDeque<>();
    stack.addLast(dir); // Seed the cycle detection with the starting directory, per upstream.
    final List<Path> result = new ArrayList<>();
    for (Path child : list(dir)) {
      collectRecursively(this, stack, child, followSymlinks, false, result);
    }
    return result;
  }

  private static void collectRecursively(
      FileSystem fileSystem,
      Deque<Path> stack,
      Path path,
      boolean followSymlinks,
      boolean postorder,
      List<Path> result) throws IOException {
    // For listRecursively, visit enclosing directory first.
    if (!postorder) {
      result.add(path);
    }

    List<Path> children = fileSystem.listOrNull(path);
    if (children == null) {
      children = new ArrayList<>();
    }
    if (!children.isEmpty()) {
      // Figure out if path is a symlink and detect symlink cycles.
      Path symlinkPath = path;
      int symlinkCount = 0;
      while (true) {
        if (followSymlinks && stack.contains(symlinkPath)) {
          throw new IOException("symlink cycle at " + path);
        }
        Path target = symlinkTargetOrNull(fileSystem, symlinkPath);
        if (target == null) {
          break;
        }
        symlinkPath = target;
        symlinkCount++;
      }

      // Recursively visit children.
      if (followSymlinks || symlinkCount == 0) {
        stack.addLast(symlinkPath);
        try {
          for (Path child : children) {
            collectRecursively(fileSystem, stack, child, followSymlinks, postorder, result);
          }
        } finally {
          stack.removeLast();
        }
      }
    }
  }

  /** Returns a resolved path to the symlink target, resolving it if necessary. */
  private static Path symlinkTargetOrNull(FileSystem fileSystem, Path path) throws IOException {
    Path target;
    try {
      target = fileSystem.metadata(path).symlinkTarget;
    } catch (IOException e) {
      return null;
    }
    if (target == null) {
      return null;
    }
    return path.getParent().resolve(target);
  }

  /**
   * Opens a file within this file system for reading. The returned handle must be closed after
   * use.
   */
  public abstract FileHandle openReadOnly(Path file) throws IOException;

  /**
   * Opens a file within this file system for reading and writing. The returned handle must be
   * closed after use.
   */
  public final FileHandle openReadWrite(Path file) throws IOException {
    return openReadWrite(file, false, false);
  }

  /**
   * Opens a file within this file system for reading and writing. The returned handle must be
   * closed after use.
   *
   * @param mustCreate true to throw an {@link IOException} instead of overwriting an existing
   *     file.
   * @param mustExist true to throw an {@link IOException} instead of creating a new file.
   */
  public abstract FileHandle openReadWrite(Path file, boolean mustCreate, boolean mustExist)
      throws IOException;

  /** Opens {@code file} for reading, calls {@code readerAction} with a buffered source, closes. */
  public <T> T read(Path file, ReaderFunction<T> readerAction) throws IOException {
    BufferedSource source = Okio.buffer(source(file));
    try {
      return readerAction.read(source);
    } finally {
      source.close();
    }
  }

  /** Opens {@code file} for writing, calls {@code writerAction} with a buffered sink, closes. */
  public <T> T write(Path file, WriterFunction<T> writerAction) throws IOException {
    return write(file, false, writerAction);
  }

  public <T> T write(Path file, boolean mustCreate, WriterFunction<T> writerAction)
      throws IOException {
    BufferedSink sink = Okio.buffer(sink(file, mustCreate));
    try {
      return writerAction.write(sink);
    } finally {
      sink.close();
    }
  }

  /**
   * Returns a source that reads bytes from {@code file}. The returned source must be closed when
   * it is no longer needed.
   */
  public abstract Source source(Path file) throws IOException;

  /**
   * Returns a sink that writes bytes to {@code file} from beginning to end. If {@code file} already
   * exists it will be replaced with the new data.
   *
   * @param mustCreate true to throw an {@link IOException} instead of overwriting an existing
   *     file.
   */
  public final Sink sink(Path file) throws IOException {
    return sink(file, false);
  }

  public abstract Sink sink(Path file, boolean mustCreate) throws IOException;

  /**
   * Returns a sink that appends bytes to the end of {@code file}, creating it if it doesn't
   * already exist.
   *
   * @param mustExist true to throw an {@link IOException} instead of creating a new file.
   */
  public final Sink appendingSink(Path file) throws IOException {
    return appendingSink(file, false);
  }

  public abstract Sink appendingSink(Path file, boolean mustExist) throws IOException;

  /**
   * Creates a directory at {@code dir}.
   *
   * @param mustCreate true to throw an {@link IOException} if the directory already exists.
   */
  public final void createDirectory(Path dir) throws IOException {
    createDirectory(dir, false);
  }

  public abstract void createDirectory(Path dir, boolean mustCreate) throws IOException;

  /**
   * Creates a directory at {@code dir}, including any necessary but nonexistent parent
   * directories.
   *
   * @param mustCreate true to throw an {@link IOException} if the directory already exists.
   */
  public final void createDirectories(Path dir) throws IOException {
    createDirectories(dir, false);
  }

  public void createDirectories(Path dir, boolean mustCreate) throws IOException {
    // Compute the sequence of directories to create.
    Deque<Path> directories = new ArrayDeque<>();
    Path path = dir;
    while (path != null && !exists(path)) {
      directories.addFirst(path);
      path = path.getParent();
    }

    if (mustCreate && directories.isEmpty()) {
      throw new IOException(dir + " already exists.");
    }

    // Create them.
    for (Path toCreate : directories) {
      // We know we are creating new directories by now so we don't have to pass down `mustCreate`.
      createDirectory(toCreate);
    }
  }

  /**
   * Renames {@code source} to {@code target}. If {@code target} already exists, it will be
   * replaced by {@code source}.
   */
  public abstract void atomicMove(Path source, Path target) throws IOException;

  /**
   * Copies all bytes from {@code source} to {@code target}, overwriting the contents of
   * {@code target} if it already exists.
   */
  public void copy(Path source, Path target) throws IOException {
    Source bytesIn = source(source);
    try {
      BufferedSink bytesOut = Okio.buffer(sink(target));
      try {
        bytesOut.writeAll(bytesIn);
      } finally {
        bytesOut.close();
      }
    } finally {
      bytesIn.close();
    }
  }

  /**
   * Deletes the file or directory at {@code path}.
   *
   * @param mustExist true to throw an {@link IOException} if {@code path} does not exist.
   */
  public final void delete(Path path) throws IOException {
    delete(path, false);
  }

  public abstract void delete(Path path, boolean mustExist) throws IOException;

  /**
   * Creates a symbolic link at {@code source} whose target is {@code target}.
   */
  public abstract void createSymlink(Path source, Path target) throws IOException;

  @Override public void close() throws IOException {
  }

  /** Reader callback for {@link #read(Path, ReaderFunction)}. */
  public interface ReaderFunction<T> {
    T read(BufferedSource source) throws IOException;
  }

  /** Writer callback for {@link #write(Path, WriterFunction)}. */
  public interface WriterFunction<T> {
    T write(BufferedSink sink) throws IOException;
  }
}
