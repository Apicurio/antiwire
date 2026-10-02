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
package okio;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A virtual or physical collection of files. The API follows the subset of okio 3's FileSystem
 * (Apache 2.0, Square) that wire's schema loader and its tests exercise; the implementation is
 * original, on top of {@link java.nio.file}. Members okio has that wire never calls
 * (FileHandle, symlink creation, copy) are omitted and listed with justifications in
 * docs/loading-api-inventory.md, which also records the known divergences.
 */
public abstract class FileSystem implements Closeable {

  /** The current process's host file system. Closing it is a no-op. */
  public static final FileSystem SYSTEM = new NioFileSystem(FileSystems.getDefault(), null);

  // Protected (upstream okio keeps an internal constructor that same-package ForwardingFileSystem
  // can call): the WireCompiler dry-run file system subclasses through ForwardingFileSystem.
  protected FileSystem() {
  }

  /** Releases resources held by this file system. The default does nothing. */
  @Override public void close() throws IOException {
  }

  /** Returns {@code path} with {@code .} and {@code ..} resolved and symlinks followed. */
  public abstract Path canonicalize(Path path) throws IOException;

  /** Returns the metadata of {@code path}, or null if it cannot be found or stated. */
  public abstract FileMetadata metadataOrNull(Path path) throws IOException;

  /** Like {@link #metadataOrNull}, but throws when {@code path} does not exist. */
  public final FileMetadata metadata(Path path) throws IOException {
    FileMetadata metadata = metadataOrNull(path);
    if (metadata == null) throw new FileNotFoundException(path.toString());
    return metadata;
  }

  /** Returns true if {@code path} exists. */
  public final boolean exists(Path path) throws IOException {
    return metadataOrNull(path) != null;
  }

  /** Returns the paths in {@code dir}, in natural order; never null. */
  public abstract List<Path> list(Path dir) throws IOException;

  /** Like {@link #list}, but returns null when {@code dir} does not exist. */
  public abstract List<Path> listOrNull(Path dir) throws IOException;

  /**
   * Returns every path under {@code dir}, depth-first, parents before children, each directory
   * in natural order. Directories are included alongside their contents. With
   * {@code followSymlinks} a symlink to a directory is traversed; otherwise it is returned as
   * a leaf. okio's Kotlin Sequence becomes this eager snapshot in the port (recorded as a
   * mechanical adaptation for translated call sites).
   */
  public final List<Path> listRecursively(Path dir) throws IOException {
    return listRecursively(dir, false);
  }

  /** See {@link #listRecursively(Path)}. */
  public final List<Path> listRecursively(Path dir, boolean followSymlinks) throws IOException {
    List<Path> result = new ArrayList<>();
    listRecursivelyInto(dir, followSymlinks, result);
    return result;
  }

  private void listRecursivelyInto(Path dir, boolean followSymlinks, List<Path> out)
      throws IOException {
    List<Path> entries = list(dir);
    for (Path entry : entries) {
      out.add(entry);
      FileMetadata metadata = metadataOrNull(entry);
      boolean traverse = metadata != null
          && (metadata.isDirectory
              || (followSymlinks && metadata.symlinkTarget != null && entryIsDirectory(entry)));
      if (traverse) {
        listRecursivelyInto(entry, followSymlinks, out);
      }
    }
  }

  private boolean entryIsDirectory(Path entry) throws IOException {
    try {
      return Files.isDirectory(entry.toNio());
    } catch (UnsupportedOperationException e) {
      return false;
    }
  }

  /** Opens {@code file} for reading. */
  public abstract Source source(Path file) throws IOException;

  /** Opens {@code file} for writing, replacing it if it exists (unless {@code mustCreate}). */
  public abstract Sink sink(Path file, boolean mustCreate) throws IOException;

  /** Opens {@code file} for appending. */
  public abstract Sink appendingSink(Path file, boolean mustExist) throws IOException;

  /**
   * Creates {@code dir}, without creating parents. Fails if it already exists (unless not
   * {@code mustCreate}); a missing parent fails like okio's createDirectory.
   */
  public abstract void createDirectory(Path dir, boolean mustCreate) throws IOException;

  /** Creates {@code dir} and any missing parents. */
  public final void createDirectories(Path dir, boolean mustCreate) throws IOException {
    if (exists(dir)) {
      if (mustCreate) throw new FileAlreadyExistsException(dir.toString());
      return;
    }
    // Create the missing ancestor chain outermost-first with single-level creates.
    List<Path> missing = new ArrayList<>();
    for (Path p = dir; p != null && !exists(p); p = p.parent()) {
      missing.add(p);
    }
    for (int i = missing.size() - 1; i >= 0; i--) {
      createDirectory(missing.get(i), false);
    }
  }

  /** Atomically renames {@code source} to {@code target}. */
  public abstract void atomicMove(Path source, Path target) throws IOException;

  /** Deletes {@code path}; throws if it is missing (unless not {@code mustExist}). */
  public abstract void delete(Path path, boolean mustExist) throws IOException;

  /**
   * Opens the ZIP archive at {@code zipFile} and returns a read-only file system over its
   * entries, rooted at {@code /}. The archive is read through THIS file system's
   * {@link #source}, so a resource file system can open zips from the classpath. The caller
   * owns the returned handle: closing it releases the archive and removes the temporary copy
   * the implementation materialized. Paths inside the returned system use the ZIP provider.
   */
  public FileSystem openZip(Path zipFile) throws IOException {
    java.nio.file.Path temp = Files.createTempFile("antiwire-openzip", ".zip");
    boolean success = false;
    try {
      try (Source in = source(zipFile);
          BufferedSink out = Okio.buffer(Okio.sink(Files.newOutputStream(temp,
              StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)))) {
        out.writeAll(in);
      }
      java.nio.file.FileSystem zipfs = FileSystems.newFileSystem(temp, (ClassLoader) null);
      success = true;
      final java.nio.file.Path tempFinal = temp;
      return new NioFileSystem(zipfs, () -> {
        try {
          zipfs.close();
        } finally {
          Files.deleteIfExists(tempFinal);
        }
      }); // the lambda targets CloseAction, which may throw IOException
    } finally {
      if (!success) Files.deleteIfExists(temp);
    }
  }

  /** A file system backed by {@code nioFileSystem}. */
  public static FileSystem asOkioFileSystem(java.nio.file.FileSystem nioFileSystem) {
    return new NioFileSystem(nioFileSystem, null);
  }

  /**
   * A read-only file system that reads resources visible to {@code classLoader}. Directories
   * are not enumerable across providers, so {@link #list} throws; reading a known resource
   * path works everywhere. This replaces the FakeFileSystem choreography classpath consumers
   * needed with okio's Kotlin artifacts.
   */
  public static FileSystem asResourceFileSystem(ClassLoader classLoader) {
    return new ResourceFileSystem(classLoader);
  }

  /** An action closing a provider, allowed to throw {@link IOException}. */
  interface CloseAction {
    void run() throws IOException;
  }

  static final class NioFileSystem extends FileSystem {
    private final java.nio.file.FileSystem nio;
    private final CloseAction onClose;

    NioFileSystem(java.nio.file.FileSystem nio, CloseAction onClose) {
      this.nio = nio;
      this.onClose = onClose;
    }

    /** Closes the wrapped provider unless it is the process default file system. */
    @Override public void close() throws IOException {
      if (onClose != null) {
        onClose.run();
      } else if (nio != FileSystems.getDefault()) {
        nio.close();
      }
    }

    /**
     * Uses the wrapped nio path directly when it belongs to this provider, and re-parses only
     * for cross-provider paths (a zip entry handed to SYSTEM, a host path handed to a zipfs).
     */
    private java.nio.file.Path nioPath(Path path) {
      java.nio.file.Path candidate = path.toNio();
      return candidate.getFileSystem() == nio ? candidate : nio.getPath(path.toString());
    }

    @Override public Path canonicalize(Path path) throws IOException {
      return Path.wrap(nioPath(path).toRealPath());
    }

    @Override public FileMetadata metadataOrNull(Path path) throws IOException {
      BasicFileAttributes attrs;
      try {
        // Follow nothing: a symlink must report as itself so symlinkTarget is populated.
        attrs = Files.readAttributes(nioPath(path), BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS);
      } catch (FileSystemException e) {
        // okio maps every stat failure, including access errors, to absent.
        return null;
      }
      Path symlinkTarget = attrs.isSymbolicLink()
          ? Path.wrap(Files.readSymbolicLink(nioPath(path))) : null;
      return new FileMetadata(
          attrs.isRegularFile(), attrs.isDirectory(), attrs.size(), symlinkTarget);
    }

    @Override public List<Path> list(Path dir) throws IOException {
      List<Path> list = listOrNull(dir);
      if (list == null) throw new FileNotFoundException(dir.toString());
      return list;
    }

    @Override public List<Path> listOrNull(Path dir) throws IOException {
      try (java.util.stream.Stream<java.nio.file.Path> stream = Files.list(nioPath(dir))) {
        List<Path> result = new ArrayList<>();
        try {
          stream.forEach(entry -> result.add(Path.wrap(entry)));
        } catch (DirectoryIteratorException e) {
          throw e.getCause() instanceof IOException
              ? (IOException) e.getCause()
              : new IOException(e.getCause());
        }
        result.sort(Comparator.naturalOrder());
        return result;
      } catch (NoSuchFileException | NotDirectoryException e) {
        return null;
      }
    }

    @Override public Source source(Path file) throws IOException {
      return Okio.source(nioPath(file));
    }

    @Override public Sink sink(Path file, boolean mustCreate) throws IOException {
      return newSink(file, mustCreate
          ? new StandardOpenOption[] {StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE}
          : new StandardOpenOption[] {
              StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
              StandardOpenOption.WRITE});
    }

    @Override public Sink appendingSink(Path file, boolean mustExist) throws IOException {
      if (mustExist && metadataOrNull(file) == null) {
        throw new FileNotFoundException(file.toString());
      }
      return newSink(file, new StandardOpenOption[] {
          StandardOpenOption.CREATE, StandardOpenOption.APPEND});
    }

    private Sink newSink(Path file, StandardOpenOption... options) throws IOException {
      return Okio.sink(nioPath(file), options);
    }

    @Override public void createDirectory(Path dir, boolean mustCreate) throws IOException {
      if (mustCreate && Files.exists(nioPath(dir))) {
        throw new FileAlreadyExistsException(dir.toString());
      }
      try {
        Files.createDirectory(nioPath(dir));
      } catch (FileAlreadyExistsException e) {
        if (mustCreate) throw e;
      }
    }

    @Override public void atomicMove(Path source, Path target) throws IOException {
      Files.move(nioPath(source), nioPath(target),
          java.nio.file.StandardCopyOption.ATOMIC_MOVE,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    @Override public void delete(Path path, boolean mustExist) throws IOException {
      if (mustExist) {
        Files.delete(nioPath(path));
      } else {
        Files.deleteIfExists(nioPath(path));
      }
    }
  }

  static final class ResourceFileSystem extends FileSystem {
    private final ClassLoader classLoader;

    ResourceFileSystem(ClassLoader classLoader) {
      this.classLoader = classLoader;
    }

    private static String resourceName(Path path) {
      String name = path.toString();
      while (name.startsWith("/")) name = name.substring(1);
      return name;
    }

    private static UnsupportedOperationException readOnly() {
      return new UnsupportedOperationException("ResourceFileSystem is read-only");
    }

    @Override public Path canonicalize(Path path) throws IOException {
      if (exists(path)) return path;
      throw new FileNotFoundException(path.toString());
    }

    @Override public FileMetadata metadataOrNull(Path path) {
      String name = resourceName(path);
      if (name.isEmpty()) {
        // The classpath root is always a directory, even when the provider reports no URL
        // for the empty resource name (jars-only classpaths like `java -jar` return null
        // there); without this, a root Location cannot become a DirectoryRoot.
        return new FileMetadata(false, true, null, null);
      }
      URL url = classLoader.getResource(name);
      if (url == null) return null;
      // Classpath providers report directories as URLs ending in a slash; sizes are not
      // probed (one connection per call) because the loader never reads byteSize here.
      boolean directory = url.toString().endsWith("/");
      return new FileMetadata(!directory, directory, null, null);
    }

    @Override public List<Path> list(Path dir) throws IOException {
      throw new UnsupportedOperationException(
          "ResourceFileSystem cannot enumerate " + dir + ": classpath providers are not "
              + "listable; read known resource paths instead");
    }

    @Override public List<Path> listOrNull(Path dir) throws IOException {
      // Keep the absent-means-null contract; enumeration of what does exist stays loud.
      if (metadataOrNull(dir) == null) return null;
      return list(dir);
    }

    @Override public Source source(Path file) throws IOException {
      java.io.InputStream in = classLoader.getResourceAsStream(resourceName(file));
      if (in == null) throw new FileNotFoundException(file.toString());
      return Okio.source(in);
    }

    @Override public Sink sink(Path file, boolean mustCreate) throws IOException {
      throw readOnly();
    }

    @Override public Sink appendingSink(Path file, boolean mustExist) throws IOException {
      throw readOnly();
    }

    @Override public void createDirectory(Path dir, boolean mustCreate) throws IOException {
      throw readOnly();
    }

    @Override public void atomicMove(Path source, Path target) throws IOException {
      throw readOnly();
    }

    @Override public void delete(Path path, boolean mustExist) throws IOException {
      throw readOnly();
    }
  }
}
