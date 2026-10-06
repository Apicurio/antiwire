/*
 * Translated from okio/src/jvmMain/kotlin/okio/Path.kt (with the common helpers of
 * okio/src/commonMain/kotlin/okio/internal/Path.kt inlined) at square/okio tag parent-3.18.2,
 * commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under the notices below.
 * Translation notes: Kotlin's operator div and the "path".toPath() extension become
 * Path.resolve overloads and Path.get; toFile()/toNioPath() are kept. The constructor is
 * package-private, the source-level analog of upstream's internal constructor.
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

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * A fully-qualified path to a file or directory in a particular {@link FileSystem}. This class
 * handles path components and separators.
 */
public final class Path implements Comparable<Path> {

  private static final ByteString SLASH = ByteString.encodeUtf8("/");
  private static final ByteString BACKSLASH = ByteString.encodeUtf8("\\");
  private static final ByteString ANY_SLASH = ByteString.encodeUtf8("/\\");
  private static final ByteString DOT = ByteString.encodeUtf8(".");
  private static final ByteString DOT_DOT = ByteString.encodeUtf8("..");

  final ByteString bytes;

  Path(ByteString bytes) {
    if (bytes == null) {
      throw new NullPointerException("bytes == null");
    }
    this.bytes = bytes;
  }

  /**
   * Either {@code /} (on UNIX-like systems including Android, iOS, and Linux) or {@code \} (on
   * Windows systems).
   */
  public static final String DIRECTORY_SEPARATOR = File.separator;

  /** Returns the {@link Path} representation for {@code path}. */
  public static Path get(String path) {
    return get(path, false);
  }

  /**
   * Returns the {@link Path} representation for {@code path}. Set {@code normalize} to true to
   * eagerly consume {@code ..} segments in your path.
   */
  public static Path get(String path, boolean normalize) {
    return get(new Buffer().writeUtf8(path), normalize);
  }

  /** Returns the {@link Path} for {@code file}. */
  public static Path get(File file) {
    return get(file.toString(), false);
  }

  /** Returns the {@link Path} for {@code path}. */
  public static Path get(java.nio.file.Path path) {
    return get(path.toString(), false);
  }

  /**
   * Consumes the buffer and returns it as a path. All reads hit the in-memory buffer only, so
   * the declared IOExceptions of the Buffer API are unreachable here.
   */
  static Path get(Buffer buffer, boolean normalize) {
    try {
      return getUnchecked(buffer, normalize);
    } catch (java.io.IOException e) {
      throw new AssertionError(e); // Unreachable: the buffer has no underlying source.
    }
  }

  private static Path getUnchecked(Buffer buffer, boolean normalize)
      throws java.io.IOException {
    ByteString slash = null;
    Buffer result = new Buffer();

    // Consume the absolute path prefix, like `/`, `\\`, `C:`, or `C:\` and write the
    // canonicalized prefix to result.
    int leadingSlashCount = 0;
    while (buffer.rangeEquals(0L, SLASH) || buffer.rangeEquals(0L, BACKSLASH)) {
      byte b = buffer.readByte();
      if (slash == null) {
        slash = byteToSlash(b);
      }
      leadingSlashCount++;
    }
    boolean windowsUncPath = leadingSlashCount >= 2 && slash == BACKSLASH;
    if (windowsUncPath) {
      // This is a Windows UNC path, like \\server\directory\file.txt.
      result.write(slash);
      result.write(slash);
    } else if (leadingSlashCount > 0) {
      // This is platform-dependent:
      //  * On UNIX: an absolute path like /home
      //  * On Windows: this is relative to the current volume, like \Windows.
      result.write(slash);
    } else {
      // This path doesn't start with any slash. We must initialize the slash character to use.
      long limit = buffer.indexOfElement(ANY_SLASH);
      if (slash == null) {
        slash = limit == -1L
            ? stringToSlash(DIRECTORY_SEPARATOR)
            : byteToSlash(buffer.getByte(limit));
      }
      if (startsWithVolumeLetterAndColon(buffer, slash)) {
        if (limit == 2L) {
          result.write(buffer, 3L); // Absolute on a named volume, like `C:\`.
        } else {
          result.write(buffer, 2L); // Relative to the named volume, like `C:`.
        }
      }
    }

    boolean absolute = result.size > 0;

    List<ByteString> canonicalParts = new ArrayList<>();
    while (!buffer.exhausted()) {
      long limit = buffer.indexOfElement(ANY_SLASH);

      ByteString part;
      if (limit == -1L) {
        part = buffer.readByteString();
      } else {
        part = buffer.readByteString(limit);
        buffer.readByte();
      }

      if (part.equals(DOT_DOT)) {
        if (absolute && canonicalParts.isEmpty()) {
          // Silently consume `..`.
        } else if (!normalize
            || !absolute
            && (canonicalParts.isEmpty() || canonicalParts.get(canonicalParts.size() - 1)
            .equals(DOT_DOT))) {
          canonicalParts.add(part); // '..' doesn't pop '..' for relative paths.
        } else if (windowsUncPath && canonicalParts.size() == 1) {
          // `..` doesn't pop UNC hostnames.
        } else {
          canonicalParts.remove(canonicalParts.size() - 1);
        }
      } else if (!part.equals(DOT) && !part.equals(ByteString.EMPTY)) {
        canonicalParts.add(part);
      }
    }

    for (int i = 0; i < canonicalParts.size(); i++) {
      if (i > 0) {
        result.write(slash);
      }
      result.write(canonicalParts.get(i));
    }
    if (result.size == 0L) {
      result.write(DOT);
    }

    return new Path(result.readByteString());
  }

  private static ByteString stringToSlash(String s) {
    switch (s) {
      case "/":
        return SLASH;
      case "\\":
        return BACKSLASH;
      default:
        throw new IllegalArgumentException("not a directory separator: " + s);
    }
  }

  private static ByteString byteToSlash(byte b) {
    if (b == '/') {
      return SLASH;
    }
    if (b == '\\') {
      return BACKSLASH;
    }
    throw new IllegalArgumentException("not a directory separator: " + (char) b);
  }

  private static boolean startsWithVolumeLetterAndColon(Buffer buffer, ByteString slash) {
    if (!slash.equals(BACKSLASH)) {
      return false;
    }
    if (buffer.size < 2) {
      return false;
    }
    if (buffer.getByte(1) != ':') {
      return false;
    }
    char b = (char) buffer.getByte(0);
    return b >= 'a' && b <= 'z' || b >= 'A' && b <= 'Z';
  }

  /**
   * This is the root path if this is an absolute path, or null if it is a relative path. UNIX paths
   * have a single root, {@code /}. Each volume on Windows is its own root, like {@code C:\} and
   * {@code D:\}. The path to the current volume {@code \} is its own root. Windows UNC paths like
   * {@code \\server} are also roots.
   */
  public Path getRoot() {
    int rootLength = rootLength();
    if (rootLength == -1) {
      return null;
    }
    return new Path(bytes.substring(0, rootLength));
  }

  /**
   * The components of this path that are usually delimited by slashes. If the root is not null it
   * precedes these segments. If this path is a root its segments list is empty.
   */
  public List<String> getSegments() {
    List<String> result = new ArrayList<>();
    for (ByteString segment : getSegmentsBytes()) {
      result.add(segment.utf8());
    }
    return result;
  }

  public List<ByteString> getSegmentsBytes() {
    List<ByteString> result = new ArrayList<>();
    int segmentStart = rootLength();

    // segmentStart should always follow a `\`, but for UNC paths it doesn't.
    if (segmentStart == -1) {
      segmentStart = 0;
    } else if (segmentStart < bytes.size() && bytes.getByte(segmentStart) == '\\') {
      segmentStart++;
    }

    for (int i = segmentStart; i < bytes.size(); i++) {
      if (bytes.getByte(i) == '/' || bytes.getByte(i) == '\\') {
        result.add(bytes.substring(segmentStart, i));
        segmentStart = i + 1;
      }
    }

    if (segmentStart < bytes.size()) {
      result.add(bytes.substring(segmentStart, bytes.size()));
    }

    return result;
  }

  /** Return the length of the prefix of this that is the root path, or -1 if it has no root. */
  private int rootLength() {
    if (bytes.size() == 0) {
      return -1;
    }
    if (bytes.getByte(0) == '/') {
      return 1;
    }

    if (bytes.getByte(0) == '\\') {
      if (bytes.size() > 2 && bytes.getByte(1) == '\\') {
        // Look for a root like `\\localhost`.
        int uncRootEnd = bytes.indexOf(BACKSLASH, 2);
        if (uncRootEnd == -1) {
          uncRootEnd = bytes.size();
        }
        return uncRootEnd;
      }

      // We found a root like `\`.
      return 1;
    }

    // Look for a root like `C:\`.
    if (bytes.size() > 2 && bytes.getByte(1) == ':' && bytes.getByte(2) == '\\') {
      char c = (char) bytes.getByte(0);
      if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z')) {
        return -1;
      }
      return 3;
    }

    return -1;
  }

  /** This is true if the path is absolute. */
  public boolean isAbsolute() {
    return rootLength() != -1;
  }

  /** This is true if the path is relative. */
  public boolean isRelative() {
    return rootLength() == -1;
  }

  /**
   * This is the volume letter like "C" on Windows paths that starts with a volume letter. For
   * example, on the path "C:\Windows" this returns "C". This property is null if this is not a
   * Windows path, or if it doesn't have a volume letter.
   */
  public Character getVolumeLetter() {
    if (bytes.indexOf(SLASH) != -1) {
      return null;
    }
    if (bytes.size() < 2) {
      return null;
    }
    if (bytes.getByte(1) != ':') {
      return null;
    }
    char c = (char) bytes.getByte(0);
    if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z')) {
      return null;
    }
    return c;
  }

  public ByteString getNameBytes() {
    int lastSlash = indexOfLastSlash();
    if (lastSlash != -1) {
      return bytes.substring(lastSlash + 1);
    }
    if (getVolumeLetter() != null && bytes.size() == 2) {
      return ByteString.EMPTY; // "C:" has no name.
    }
    return bytes;
  }

  public String getName() {
    return getNameBytes().utf8();
  }

  private int indexOfLastSlash() {
    int lastSlash = bytes.lastIndexOf(SLASH);
    if (lastSlash != -1) {
      return lastSlash;
    }
    return bytes.lastIndexOf(BACKSLASH);
  }

  /**
   * Returns the path immediately enclosing this path. This returns null if this has no parent.
   */
  public Path getParent() {
    if (bytes.equals(DOT) || bytes.equals(SLASH) || bytes.equals(BACKSLASH)
        || lastSegmentIsDotDot()) {
      return null; // Terminal path.
    }

    int lastSlash = indexOfLastSlash();
    if (lastSlash == 2 && getVolumeLetter() != null) {
      if (bytes.size() == 3) {
        return null; // "C:\" has no parent.
      }
      return new Path(bytes.substring(0, 3)); // Keep the trailing '\' in C:\.
    }
    if (lastSlash == 1 && bytes.startsWith(BACKSLASH)) {
      return null; // "\\server" is a UNC path with no parent.
    }
    if (lastSlash == -1 && getVolumeLetter() != null) {
      if (bytes.size() == 2) {
        return null; // "C:" has no parent.
      }
      return new Path(bytes.substring(0, 2)); // C: is volume-relative.
    }
    if (lastSlash == -1) {
      return new Path(DOT); // Parent is the current working directory.
    }
    if (lastSlash == 0) {
      return new Path(bytes.substring(0, 1)); // Parent is the filesystem root '/'.
    }
    return new Path(bytes.substring(0, lastSlash));
  }

  private boolean lastSegmentIsDotDot() {
    if (bytes.endsWith(DOT_DOT)) {
      if (bytes.size() == 2) {
        return true; // ".." is the whole string.
      }
      if (bytes.rangeEquals(bytes.size() - 3, SLASH, 0, 1)) {
        return true; // Ends with "/..".
      }
      if (bytes.rangeEquals(bytes.size() - 3, BACKSLASH, 0, 1)) {
        return true; // Ends with "\..".
      }
    }
    return false;
  }

  /** Returns true if this is a root path like {@code /} on UNIX or {@code C:\} on Windows. */
  public boolean isRoot() {
    return rootLength() == bytes.size();
  }

  /** Returns a path that resolves {@code child} relative to this path. */
  public Path resolve(String child) {
    return resolve(child, false);
  }

  public Path resolve(String child, boolean normalize) {
    return resolve(new Buffer().writeUtf8(child), normalize);
  }

  public Path resolve(ByteString child) {
    return resolve(child, false);
  }

  public Path resolve(ByteString child, boolean normalize) {
    return resolve(new Buffer().write(child), normalize);
  }

  private Path resolve(Buffer child, boolean normalize) {
    return resolve(get(child, false), normalize);
  }

  public Path resolve(Path child) {
    return resolve(child, false);
  }

  public Path resolve(Path child, boolean normalize) {
    if (child.isAbsolute() || child.getVolumeLetter() != null) {
      return child;
    }

    ByteString slash = slash();
    if (slash == null) {
      slash = child.slash();
    }
    if (slash == null) {
      slash = stringToSlash(DIRECTORY_SEPARATOR);
    }

    Buffer buffer = new Buffer();
    buffer.write(bytes);
    if (buffer.size > 0) {
      buffer.write(slash);
    }
    buffer.write(child.bytes);
    return get(buffer, normalize);
  }

  /** The slash used by this path, or null if the path has no slashes. */
  private ByteString slash() {
    if (bytes.indexOf(SLASH) != -1) {
      return SLASH;
    }
    if (bytes.indexOf(BACKSLASH) != -1) {
      return BACKSLASH;
    }
    return null;
  }

  /**
   * Returns this path relative to {@code other}. This effectively inverts the resolve operator.
   */
  public Path relativeTo(Path other) {
    // Upstream's require(root == other.root) is null-safe structural equality (Kotlin ==).
    if (!java.util.Objects.equals(getRoot(), other.getRoot())) {
      throw new IllegalArgumentException(
          "Paths of different roots cannot be relative to each other: " + this + " and " + other);
    }

    List<ByteString> thisSegments = getSegmentsBytes();
    List<ByteString> otherSegments = other.getSegmentsBytes();

    // We look at the path both have in common.
    int firstNewSegmentIndex = 0;
    int minSegmentsSize = Math.min(thisSegments.size(), otherSegments.size());
    while (firstNewSegmentIndex < minSegmentsSize
        && thisSegments.get(firstNewSegmentIndex).equals(otherSegments.get(firstNewSegmentIndex))) {
      firstNewSegmentIndex++;
    }

    if (firstNewSegmentIndex == minSegmentsSize && bytes.size() == other.bytes.size()) {
      // `this` and `other` are the same path.
      return Path.get(".");
    }

    for (int i = firstNewSegmentIndex; i < otherSegments.size(); i++) {
      if (otherSegments.get(i).equals(DOT_DOT)) {
        throw new IllegalArgumentException(
            "Impossible relative path to resolve: " + this + " and " + other);
      }
    }

    if (other.bytes.equals(DOT)) {
      // Anything relative to "." is itself!
      return this;
    }

    Buffer buffer = new Buffer();
    ByteString slash = other.slash();
    if (slash == null) {
      slash = slash();
    }
    if (slash == null) {
      slash = stringToSlash(DIRECTORY_SEPARATOR);
    }
    for (int i = firstNewSegmentIndex; i < otherSegments.size(); i++) {
      buffer.write(DOT_DOT);
      buffer.write(slash);
    }
    for (int i = firstNewSegmentIndex; i < thisSegments.size(); i++) {
      buffer.write(thisSegments.get(i));
      buffer.write(slash);
    }
    return get(buffer, false);
  }

  /** Returns the normalized version of this path. */
  public Path normalized() {
    return Path.get(toString(), true);
  }

  public File toFile() {
    return new File(toString());
  }

  // Can only be invoked on platforms that have java.nio.file.
  public java.nio.file.Path toNioPath() {
    return Paths.get(toString());
  }

  @Override public int compareTo(Path other) {
    return bytes.compareTo(other.bytes);
  }

  @Override public boolean equals(Object other) {
    return other instanceof Path && ((Path) other).bytes.equals(bytes);
  }

  @Override public int hashCode() {
    return bytes.hashCode();
  }

  @Override public String toString() {
    return bytes.utf8();
  }
}
