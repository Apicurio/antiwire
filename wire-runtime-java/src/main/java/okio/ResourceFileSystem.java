/*
 * Translated from okio/src/jvmMain/kotlin/okio/internal/ResourceFileSystem.kt at square/okio
 * tag parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under
 * the notices below. Package-private in okio rather than okio.internal: the class is
 * upstream internal, and the ZIP entry types it needs stay in the okio package.
 * Translation note: upstream's FileSystem.RESOURCES companion constant is folded into
 * FileSystem.asResourceFileSystem(ClassLoader), the form the wire-schema CoreLoader calls
 * (upstream: ClassLoader.asResourceFileSystem()).
 *
 * Copyright (C) 2021 Square, Inc.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A file system exposing Java classpath resources. It is equivalent to the files returned by
 * {@link ClassLoader#getResource} but supports extra features like metadataOrNull and list.
 *
 * <p>If {@code .jar} files overlap, this returns an arbitrary element. For overlapping
 * directories it unions their contents.
 *
 * <p>ResourceFileSystem excludes {@code .class} files.
 *
 * <p>This file system is read-only.
 */
final class ResourceFileSystem extends FileSystem {

  private static final Path ROOT = Path.get("/");

  private final ClassLoader classLoader;
  private final List<Root> roots;

  /** A file system to use, and the base directory of that file system to search from. */
  private static final class Root {
    final FileSystem fileSystem;
    final Path base;

    Root(FileSystem fileSystem, Path base) {
      this.fileSystem = fileSystem;
      this.base = base;
    }
  }

  ResourceFileSystem(ClassLoader classLoader, boolean indexEagerly) throws IOException {
    this(classLoader, indexEagerly, FileSystem.SYSTEM);
  }

  ResourceFileSystem(ClassLoader classLoader, boolean indexEagerly, FileSystem systemFileSystem)
      throws IOException {
    this.classLoader = classLoader;
    this.roots = toClasspathRoots(classLoader, systemFileSystem);
    if (indexEagerly) {
      roots.size();
    }
  }

  @Override public Path canonicalize(Path path) throws IOException {
    return canonicalizeInternal(path);
  }

  /** Don't throw FileNotFoundException if the path doesn't identify a file. */
  private Path canonicalizeInternal(Path path) {
    return ROOT.resolve(path, true);
  }

  @Override public List<Path> listOrNull(Path dir, boolean throwOnFailure) throws IOException {
    String relativePath = toRelativePath(dir);
    Set<Path> result = new LinkedHashSet<>();
    boolean foundAny = false;
    for (Root root : roots) {
      List<Path> baseResult = root.fileSystem.listOrNull(root.base.resolve(relativePath));
      if (baseResult != null) {
        result.addAll(keepPaths(baseResult, root.base));
        foundAny = true;
      }
    }
    if (!foundAny) {
      if (throwOnFailure) {
        throw new FileNotFoundException("file not found: " + dir);
      }
      return null;
    }
    return new ArrayList<>(result);
  }

  @Override public FileHandle openReadOnly(Path file) throws IOException {
    if (!keepPath(file)) {
      throw new FileNotFoundException("file not found: " + file);
    }
    String relativePath = toRelativePath(file);
    for (Root root : roots) {
      try {
        return root.fileSystem.openReadOnly(root.base.resolve(relativePath));
      } catch (FileNotFoundException e) {
        // Try the next root.
      }
    }
    throw new FileNotFoundException("file not found: " + file);
  }

  @Override public FileHandle openReadWrite(Path file, boolean mustCreate, boolean mustExist)
      throws IOException {
    throw new IOException("resources are not writable");
  }

  @Override public FileMetadata metadataOrNull(Path path) throws IOException {
    if (!keepPath(path)) {
      return null;
    }
    String relativePath = toRelativePath(path);
    for (Root root : roots) {
      FileMetadata metadata = root.fileSystem.metadataOrNull(root.base.resolve(relativePath));
      if (metadata != null) {
        return metadata;
      }
    }
    return null;
  }

  @Override public Source source(Path file) throws IOException {
    if (!keepPath(file)) {
      throw new FileNotFoundException("file not found: " + file);
    }
    // Make sure we have a path that doesn't start with '/'.
    Path relativePath = ROOT.resolve(file).relativeTo(ROOT);
    URL resource = classLoader.getResource(relativePath.toString());
    if (resource == null) {
      throw new FileNotFoundException("file not found: " + file);
    }
    java.net.URLConnection urlConnection = resource.openConnection();
    if (urlConnection instanceof JarURLConnection) {
      ((JarURLConnection) urlConnection).setUseCaches(false);
    }
    InputStream inputStream = urlConnection.getInputStream();
    return Okio.source(inputStream);
  }

  @Override public Sink sink(Path file, boolean mustCreate) throws IOException {
    throw new IOException(this + " is read-only");
  }

  @Override public Sink appendingSink(Path file, boolean mustExist) throws IOException {
    throw new IOException(this + " is read-only");
  }

  @Override public void createDirectory(Path dir, boolean mustCreate) throws IOException {
    throw new IOException(this + " is read-only");
  }

  @Override public void atomicMove(Path source, Path target) throws IOException {
    throw new IOException(this + " is read-only");
  }

  @Override public void delete(Path path, boolean mustExist) throws IOException {
    throw new IOException(this + " is read-only");
  }

  @Override public void createSymlink(Path source, Path target) throws IOException {
    throw new IOException(this + " is read-only");
  }

  private String toRelativePath(Path path) {
    Path canonicalThis = canonicalizeInternal(path);
    return canonicalThis.relativeTo(ROOT).toString();
  }

  private static List<Path> keepPaths(List<Path> paths, Path base) {
    List<Path> result = new ArrayList<>();
    for (Path path : paths) {
      if (keepPath(path)) {
        result.add(removeBase(path, base));
      }
    }
    return result;
  }

  /**
   * Returns a search path of classpath roots. Each element contains a file system to use, and
   * the base directory of that file system to search from.
   */
  private static List<Root> toClasspathRoots(ClassLoader classLoader,
      FileSystem systemFileSystem) throws IOException {
    // We'd like to build this upon an API like ClassLoader.getURLs() but unfortunately that
    // API exists only on URLClassLoader (and that isn't the default class loader implementation).
    //
    // The closest we have is `ClassLoader.getResources("")`. It returns all classpath roots that
    // are directories but none that are .jar files. To mitigate that we also search for all
    // `META-INF/MANIFEST.MF` files, hastily assuming that every .jar file will have such an
    // entry.
    //
    // Classpath entries that aren't directories and don't have a META-INF/MANIFEST.MF file will
    // not be visible in this file system.
    List<Root> result = new ArrayList<>();
    result.addAll(getResourcesAsRoots(classLoader, "", systemFileSystem));
    result.addAll(getResourcesAsJarRoots(
        classLoader, "META-INF/MANIFEST.MF", systemFileSystem));
    return result;
  }

  private static List<Root> getResourcesAsRoots(
      ClassLoader classLoader, String name, FileSystem systemFileSystem) throws IOException {
    Enumeration<URL> resources = classLoader.getResources(name);
    List<Root> result = new ArrayList<>();
    while (resources.hasMoreElements()) {
      Root root = toFileRoot(resources.nextElement(), systemFileSystem);
      if (root != null) {
        result.add(root);
      }
    }
    return result;
  }

  private static List<Root> getResourcesAsJarRoots(
      ClassLoader classLoader, String name, FileSystem systemFileSystem) throws IOException {
    Enumeration<URL> resources = classLoader.getResources(name);
    List<Root> result = new ArrayList<>();
    while (resources.hasMoreElements()) {
      Root root = toJarRoot(resources.nextElement(), systemFileSystem);
      if (root != null) {
        result.add(root);
      }
    }
    return result;
  }

  private static Root toFileRoot(URL url, FileSystem systemFileSystem) {
    if (!url.getProtocol().equals("file")) {
      return null; // Ignore unexpected URLs.
    }
    try {
      return new Root(systemFileSystem, Path.get(new File(url.toURI())));
    } catch (java.net.URISyntaxException e) {
      return null; // Ignore unexpected URLs.
    }
  }

  private static Root toJarRoot(URL url, FileSystem systemFileSystem) throws IOException {
    String urlString = url.toString();
    if (!urlString.startsWith("jar:file:")) {
      return null; // Ignore unexpected URLs.
    }

    // Given a URL like `jar:file:/tmp/foo.jar!/META-INF/MANIFEST.MF`, get the path to the archive
    // file, like `/tmp/foo.jar`.
    int suffixStart = urlString.lastIndexOf('!');
    if (suffixStart == -1) {
      return null;
    }
    try {
      File file = new File(URI.create(urlString.substring("jar:".length(), suffixStart)));
      Path path = Path.get(file);
      FileSystem zip = FileSystem.openZip(path, systemFileSystem,
          entry -> keepPath(entry.canonicalPath));
      return new Root(zip, ROOT);
    } catch (java.lang.IllegalArgumentException e) {
      return null; // Ignore unexpected URLs.
    }
  }

  private static Path removeBase(Path path, Path base) {
    String prefix = base.toString();
    String pathString = path.toString();
    String stripped = pathString.startsWith(prefix)
        ? pathString.substring(prefix.length())
        : pathString;
    return ROOT.resolve(stripped.replace('\\', '/'));
  }

  private static boolean keepPath(Path path) {
    String name = path.getName();
    String lowerCase = name.toLowerCase(java.util.Locale.US);
    return !lowerCase.endsWith(".class");
  }

  @Override public String toString() {
    return "ResourceFileSystem";
  }
}
