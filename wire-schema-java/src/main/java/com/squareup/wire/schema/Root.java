/*
 * Copyright (C) 2018 Square, Inc.
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
package com.squareup.wire.schema;

import com.squareup.wire.schema.internal.FileSystems;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okio.BufferedSource;
import okio.FileMetadata;
import okio.FileSystem;
import okio.Okio;
import okio.Path;

/**
 * A location on the file system that proto files are loaded from: a single file, a directory, or
 * the contents of a .zip/.jar archive.
*
 * <p>Merges upstream's commonMain Root.kt sealed class with the JVM actual in Roots.kt.
 */
public abstract class Root {
  /** Returns the location's base, or null if this root has none. */
  public abstract String base();

  /** Returns all proto files within this root. */
  public abstract List<ProtoFilePath> allProtoFiles() throws IOException;

  /** Returns the proto file if it's in this root, or null if it isn't. */
  public abstract ProtoFilePath resolve(String importPath) throws IOException;

  /**
   * Returns this location's roots.
   *
   * @param baseToRoots cached roots to avoid opening the same .zip multiple times.
   */
  public static List<Root> roots(Location location, FileSystem fs,
      Map<String, List<Root>> baseToRoots) throws IOException {
    if (CoreLoader.isWireRuntimeProto(location)) {
      // Handle descriptor.proto, etc. by returning a placeholder path.
      return Collections.singletonList(
          new ProtoFilePath(location, fs, Path.get(location.path)));
    }
    if (!location.base.isEmpty()) {
      List<Root> roots = baseToRoots.get(location.base);
      if (roots == null) {
        roots = roots(Location.get(location.base), fs, new LinkedHashMap<>());
        baseToRoots.put(location.base, roots);
      }
      for (Root root : roots) {
        ProtoFilePath resolved = root.resolve(location.path);
        if (resolved != null) return Collections.singletonList(resolved);
      }
      throw new IllegalArgumentException("unable to resolve " + location);
    }
    List<Root> cached = baseToRoots.get(location.path);
    if (cached != null) return cached;
    List<Root> result = pathRoots(Path.get(location.path), fs, location);
    baseToRoots.put(location.path, result);
    return result;
  }

  private static List<Root> pathRoots(Path path, FileSystem fileSystem, Location location)
      throws IOException {
    FileMetadata metadata = fileSystem.metadataOrNull(path);
    Path realPath = metadata != null && metadata.symlinkTarget != null
        ? metadata.symlinkTarget
        : path;
    metadata = fileSystem.metadataOrNull(realPath);
    if (metadata != null && Boolean.TRUE.equals(metadata.isDirectory)) {
      if (!location.base.isEmpty()) {
        throw new IllegalStateException("Check failed");
      }
      return Collections.singletonList(
          new DirectoryRoot(location.path, fileSystem, realPath));
    }

    if (realPath.toString().endsWith(".proto")) {
      return Collections.singletonList(new ProtoFilePath(location, fileSystem, realPath));
    }

    // Handle a .zip or .jar file by adding all .proto files within.
    try {
      if (!location.base.isEmpty()) {
        throw new IllegalStateException("Check failed");
      }
      FileSystem sourceFs = fileSystem.openZip(realPath);
      return Collections.singletonList(
          new DirectoryRoot(location.path, sourceFs, Path.get("/")));
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "expected a directory, archive (.zip / .jar / etc.), or .proto: " + realPath, e);
    }
  }

  /**
   * A logical location (the base location and path to the file), plus the physical path to load.
   * These will be different if the file is loaded from a .zip archive.
   */
  public static final class ProtoFilePath extends Root {
    public final Location location;
    final FileSystem fileSystem;
    final Path path;

    ProtoFilePath(Location location, FileSystem fileSystem, Path path) {
      this.location = location;
      this.fileSystem = fileSystem;
      this.path = path;
    }

    @Override public String base() {
      return null;
    }

    @Override public List<ProtoFilePath> allProtoFiles() {
      return Collections.singletonList(this);
    }

    @Override public ProtoFilePath resolve(String importPath) {
      if (importPath.equals(location.path)) return this;
      return null;
    }

    @Override public String toString() {
      return location.toString();
    }

    public ProtoFile parse() throws IOException {
      try (BufferedSource source = Okio.buffer(fileSystem.source(path))) {
        Charset charset = FileSystems.readBomAsCharset(source);
        String data = source.readString(charset);
        ProtoFileElement element = ProtoParser.parse(location, data);
        return ProtoFile.get(element);
      } catch (IOException e) {
        throw new IOException("Failed to load " + path, e);
      }
    }
  }

  public static final class DirectoryRoot extends Root {
    /** The location of either a directory or .zip file. */
    public final String base;

    final FileSystem fileSystem;

    /** The root to search. If this is a .zip file this is within its internal file system. */
    final Path rootDirectory;

    DirectoryRoot(String base, FileSystem fileSystem, Path rootDirectory) {
      this.base = base;
      this.fileSystem = fileSystem;
      this.rootDirectory = rootDirectory;
    }

    @Override public String base() {
      return base;
    }

    @Override public List<ProtoFilePath> allProtoFiles() throws IOException {
      List<ProtoFilePath> result = new ArrayList<>();
      for (Path descendant : fileSystem.listRecursively(rootDirectory)) {
        if (!descendant.toString().endsWith(".proto")) continue;
        Location location = Location.get(base,
            descendant.relativeTo(rootDirectory).toString());
        result.add(new ProtoFilePath(location, fileSystem, descendant));
      }
      return result;
    }

    @Override public ProtoFilePath resolve(String importPath) throws IOException {
      Path resolved = rootDirectory.div(importPath);
      if (!fileSystem.exists(resolved)) return null;
      return new ProtoFilePath(
          Location.get(base, Path.get(importPath).toString()),
          fileSystem,
          resolved);
    }

    @Override public String toString() {
      return base;
    }
  }

}
