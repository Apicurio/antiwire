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
import java.util.ArrayList;
import java.util.List;

/**
 * A path in a file system. The API follows okio 3's Path (Apache 2.0, Square), which wire's
 * schema loader navigates; the implementation wraps {@link java.nio.file.Path}. Paths created
 * by {@link FileSystem#openZip} wrap paths from the ZIP provider, not the default one.
 *
 * <p>Equality, hashing and ordering are string-based across providers, like okio's: a zip
 * entry path and a host path with the same text are equal and order identically, even though
 * their nio providers differ.
 *
 * <p>{@link #root()}, {@link #volumeLetter()}, {@link #segments()}, and {@link #isAbsolute()}
 * are computed lexically from the path string exactly like okio 3.18.2
 * (okio/src/commonMain/kotlin/okio/internal/Path.kt, rootLength and commonSegmentsBytes,
 * verified against that tag's source on 2026-10-02), not through the nio provider: okio
 * recognizes {@code \\server} UNC roots, drive letters, and {@code \} as a separator on every
 * platform, and {@code SchemaHandler.checkPathInOutDirectory} depends on that cross-platform
 * parsing (upstream SchemaHandlerTest's UNC cases). Empty and {@code .} segments are dropped,
 * matching what okio's canonicalizing construction guarantees for every path it can build.
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

  /** True if this path starts at a root, like {@code /}, {@code C:\}, or {@code \\server}. */
  public boolean isAbsolute() {
    return rootLength() != -1;
  }

  /** True if this path does not start at a root. */
  public boolean isRelative() {
    return rootLength() == -1;
  }

  /** True if this path has no root and no segments at all. */
  public boolean isEmpty() {
    return nioPath.getRoot() == null && nioPath.getNameCount() == 0;
  }

  /**
   * Returns the root of this path, like {@code /} on Unix, {@code C:\} on Windows, or
   * {@code \\server} for a UNC path, or null if this path is relative. Lexical, like okio.
   */
  public Path root() {
    int rootLength = rootLength();
    return rootLength == -1 ? null : wrap(Paths.get(toString().substring(0, rootLength)));
  }

  /**
   * Returns the volume letter of this path, like {@code C} for {@code C:\} or {@code C:foo},
   * or null when there is none. Lexical, like okio: null whenever the path contains a
   * {@code /} anywhere.
   */
  public Character volumeLetter() {
    String path = toString();
    if (path.indexOf('/') != -1) return null;
    if (path.length() < 2) return null;
    if (path.charAt(1) != ':') return null;
    char c = path.charAt(0);
    if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z')) return null;
    return c;
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

  /**
   * Returns this path relative to {@code base}, like okio 3: {@code /a/b/c.txt}.relativeTo({@code
   * /a/b}) is {@code c.txt}, equal paths yield {@code .}, a sibling needs {@code ..} hops, and a
   * base with an unresolvable {@code ..} throws. Verified against okio-jvm 3.18.2 behavior on
   * 2026-10-02. The computation is purely lexical over the path strings, like okio and like this
   * class's equals and compareTo: nio providers never participate, so a zip entry and a host path
   * relativize no matter which provider built them. Both '/' and the platform separator count as
   * segment boundaries because this class's strings come from nio paths.
   */
  public Path relativeTo(Path base) {
    String thisString = toString();
    String baseString = base.toString();
    if (thisString.equals(baseString)) return Path.get(".");

    if (nioPath.isAbsolute() != base.nioPath.isAbsolute()) {
      throw new IllegalArgumentException(
          "Impossible relative path to resolve: " + thisString + " and " + baseString);
    }

    String[] thisSegments = segmentsOf(thisString);
    String[] baseSegments = segmentsOf(baseString);

    int common = 0;
    while (common < thisSegments.length && common < baseSegments.length
        && thisSegments[common].equals(baseSegments[common])) {
      common++;
    }

    StringBuilder result = new StringBuilder();
    for (int i = common; i < baseSegments.length; i++) {
      if (baseSegments[i].equals("..")) {
        // Like okio: a '..' in the base past the common prefix cannot be resolved lexically.
        throw new IllegalArgumentException(
            "Impossible relative path to resolve: " + thisString + " and " + baseString);
      }
      if (result.length() > 0) result.append('/');
      result.append("..");
    }
    for (int i = common; i < thisSegments.length; i++) {
      if (result.length() > 0) result.append('/');
      result.append(thisSegments[i]);
    }

    return Path.get(result.length() == 0 ? "." : result.toString());
  }

  private static String[] segmentsOf(String path) {
    List<String> segments = new ArrayList<>();
    int start = 0;
    for (int i = 0; i <= path.length(); i++) {
      char c = i < path.length() ? path.charAt(i) : '/';
      if (c == '/' || c == java.io.File.separatorChar) {
        String segment = path.substring(start, i);
        if (!segment.isEmpty() && !segment.equals(".")) segments.add(segment);
        start = i + 1;
      }
    }
    return segments.toArray(new String[0]);
  }

  /**
   * Returns the non-empty, non-{@code .} segments of this path, like {@code ["a", "b", "c"]} for
   * {@code /a/b/c} and {@code ["generated", "Message.java"]} for {@code \\trusted\generated\Message.java};
   * the root is not a segment. Lexical, like okio: the split runs over the path string past the
   * root, with both {@code /} and {@code \} counting as boundaries on every platform.
   */
  public List<String> segments() {
    String path = toString();
    List<String> segments = new ArrayList<>();
    int segmentStart = rootLength();
    // segmentStart should always follow a `\`, but for UNC paths it doesn't.
    if (segmentStart == -1) {
      segmentStart = 0;
    } else if (segmentStart < path.length() && path.charAt(segmentStart) == '\\') {
      segmentStart++;
    }
    for (int i = segmentStart; i < path.length(); i++) {
      char c = path.charAt(i);
      if (c == '/' || c == '\\') {
        addSegment(segments, path, segmentStart, i);
        segmentStart = i + 1;
      }
    }
    if (segmentStart < path.length()) {
      addSegment(segments, path, segmentStart, path.length());
    }
    return java.util.Collections.unmodifiableList(segments);
  }

  private static void addSegment(List<String> segments, String path, int start, int end) {
    String segment = path.substring(start, end);
    if (!segment.isEmpty() && !segment.equals(".")) segments.add(segment);
  }

  /**
   * Returns the length of the prefix of this path that is the root, or -1 if it has no root.
   * Lexical, like okio's rootLength: {@code /} and single {@code \} roots are one character,
   * {@code \\server} UNC roots run through the server name, and {@code X:\} drive roots are
   * three characters.
   */
  private int rootLength() {
    String path = toString();
    if (path.isEmpty()) return -1;
    if (path.charAt(0) == '/') return 1;

    if (path.charAt(0) == '\\') {
      if (path.length() > 2 && path.charAt(1) == '\\') {
        // Look for a root like `\\localhost`.
        int uncRootEnd = path.indexOf('\\', 2);
        if (uncRootEnd == -1) uncRootEnd = path.length();
        return uncRootEnd;
      }
      // We found a root like `\`.
      return 1;
    }

    // Look for a root like `C:\`.
    if (path.length() > 2 && path.charAt(1) == ':' && path.charAt(2) == '\\') {
      char c = path.charAt(0);
      if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') return 3;
    }

    return -1;
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
