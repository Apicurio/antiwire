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

import java.nio.file.Paths;

/**
 * A path in a file system. The API follows okio 3's Path (Apache 2.0, Square), which wire's
 * schema loader navigates; the implementation wraps {@link java.nio.file.Path}, so separator,
 * root and volume-letter semantics come from the path's own provider. Paths created by
 * {@link FileSystem#openZip} wrap paths from the ZIP provider, not the default one.
 *
 * <p>Equality, hashing and ordering are string-based across providers, like okio's: a zip
 * entry path and a host path with the same text are equal and order identically, even though
 * their nio providers differ.
 */
public final class Path implements Comparable<Path> {
  private final java.nio.file.Path nioPath;

  Path(java.nio.file.Path nioPath) {
    this.nioPath = nioPath;
  }

  /** Returns a path in the default file system. */
  public static Path get(String path) {
    return new Path(Paths.get(path));
  }

  static Path wrap(java.nio.file.Path nioPath) {
    return nioPath == null ? null : new Path(nioPath);
  }

  java.nio.file.Path toNio() {
    return nioPath;
  }

  /** True if this path starts at a root, like {@code /} or {@code C:\}. */
  public boolean isAbsolute() {
    return nioPath.isAbsolute();
  }

  /** True if this path does not start at a root. */
  public boolean isRelative() {
    return !nioPath.isAbsolute();
  }

  /** True if this path has no root and no segments at all. */
  public boolean isEmpty() {
    return nioPath.getRoot() == null && nioPath.getNameCount() == 0;
  }

  /**
   * Returns the root of this path, like {@code /} on Unix or {@code C:\} on Windows, or null
   * if this path is relative.
   */
  public Path root() {
    return wrap(nioPath.getRoot());
  }

  /**
   * Returns the volume letter of this path, like {@code C} for {@code C:\}, or null when the
   * platform has no volume letters.
   */
  public Character volumeLetter() {
    java.nio.file.Path root = nioPath.getRoot();
    if (root == null) return null;
    String s = root.toString();
    if (s.length() == 3 && s.charAt(1) == ':'
        && (s.charAt(2) == '/' || s.charAt(2) == '\\')
        && Character.isLetter(s.charAt(0))) {
      return s.charAt(0);
    }
    return null;
  }

  /**
   * Returns a copy of this path whose segments are normalized: redundant {@code .} and
   * {@code ..} segments are resolved where possible.
   */
  public Path normalized() {
    return wrap(nioPath.normalize());
  }

  /**
   * Returns this path with {@code child} appended. If {@code child} is absolute it replaces
   * this path entirely, matching okio's {@code div} semantics that wire relies on when
   * sanitizing handler outputs.
   */
  public Path div(String child) {
    return wrap(nioPath.resolve(child));
  }

  /** See {@link #div(String)}. A child from another provider is parsed by this path's provider. */
  public Path div(Path child) {
    if (child.nioPath.getFileSystem() == nioPath.getFileSystem()) {
      return wrap(nioPath.resolve(child.nioPath));
    }
    return wrap(nioPath.resolve(child.toString()));
  }

  /** Java-idiomatic alias for {@link #div(String)}, kept for translated Kotlin call sites. */
  public Path resolve(String child) {
    return div(child);
  }

  /** Returns a relative path from this path to {@code other}. */
  public Path relativeTo(Path other) {
    return wrap(nioPath.relativize(other.nioPath));
  }

  /** Returns the parent of this path, or null when there is none. */
  public Path parent() {
    return wrap(nioPath.getParent());
  }

  @Override public int compareTo(Path other) {
    return toString().compareTo(other.toString());
  }

  @Override public boolean equals(Object other) {
    return other instanceof Path && other.toString().equals(toString());
  }

  @Override public int hashCode() {
    return toString().hashCode();
  }

  @Override public String toString() {
    return nioPath.toString();
  }
}
