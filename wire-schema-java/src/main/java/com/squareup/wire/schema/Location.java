/*
 * Copyright (C) 2015 Square, Inc.
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

import java.util.Objects;

/**
 * Locates a .proto file, or a position within a .proto file, on the file system. This includes a
 * base directory or a .jar file, and a path relative to that base.
 */
public final class Location {
  /** The base of this location; typically a directory or .jar file. */
  private final String base;

  public String getBase() {
    return base;
  }

  /** The path to this location relative to {@link #getBase()}. */
  private final String path;

  public String getPath() {
    return path;
  }

  /** The line number of this location, or -1 for no specific line number. */
  private final int line;

  public int getLine() {
    return line;
  }

  /** The column on the line of this location, or -1 for no specific column. */
  private final int column;

  public int getColumn() {
    return column;
  }

  public Location(String base, String path, int line, int column) {
    this.base = base;
    this.path = path;
    this.line = line;
    this.column = column;
  }

  public Location at(int line, int column) {
    return new Location(base, path, line, column);
  }

  /** Returns a copy of this location with an empty base. */
  public Location withoutBase() {
    return new Location("", path, line, column);
  }

  /** Returns a copy of this location including only its path. */
  public Location withPathOnly() {
    return new Location("", path, -1, -1);
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof Location)) return false;
    Location that = (Location) other;
    return base.equals(that.base) && path.equals(that.path)
        && line == that.line && column == that.column;
  }

  @Override public int hashCode() {
    int result = base.hashCode();
    result = 31 * result + path.hashCode();
    result = 31 * result + line;
    result = 31 * result + column;
    return result;
  }

  @Override public String toString() {
    StringBuilder builder = new StringBuilder();
    if (!base.isEmpty()) {
      builder.append(base);
      builder.append('/');
    }
    builder.append(path);
    if (line != -1) {
      builder.append(':');
      builder.append(line);
      if (column != -1) {
        builder.append(':');
        builder.append(column);
      }
    }
    return builder.toString();
  }

  public static Location get(String path) {
    return get("", path);
  }

  public static Location get(String base, String path) {
    String normalizedBase = base.replace('\\', '/').replaceAll("/+$", "");
    String normalizedPath;
    if (path.length() >= 3 && path.charAt(1) == ':' && path.charAt(2) == '\\') {
      // Preserve the Windows drive root backslash (e.g. "C:\") since okio's path parser
      // requires a backslash after the volume letter to recognize a Windows absolute path.
      normalizedPath = path.substring(0, 3)
          + path.substring(3).replace('\\', '/');
    } else {
      normalizedPath = path.replace('\\', '/');
    }
    return new Location(normalizedBase, normalizedPath, -1, -1);
  }

  /** Mirror of the Kotlin companion object: lets Java callers write {@code Location.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }

    public Location get(String path) {
      return Location.get(path);
    }

    public Location get(String base, String path) {
      return Location.get(base, path);
    }
  }
}
