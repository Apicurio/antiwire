/*
 * Copyright (C) 2020 Square, Inc.
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
package com.squareup.wire;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A set of symbolic field paths.
 *
 * <p>Field masks are used to specify a subset of fields on a target message. Each path uses
 * proto field names, separated by dots for nested fields.
 */
public final class FieldMask {
  private final PathChunks pathChunks;

  /** Upstream's private primary constructor, reached by every public one. */
  private FieldMask(PathChunks pathChunks) {
    this.pathChunks = pathChunks;
  }

  public FieldMask() {
    this((PathChunks) null);
  }

  public FieldMask(List<String> paths) {
    this(paths != null && !paths.isEmpty()
        ? new PathChunks(null, new ArrayList<>(paths)) : null);
  }

  /** Lazily-flattened paths; merging encoded occurrences appends chunks in constant time. */
  private volatile List<String> pathsMemoized;

  public List<String> paths() {
    List<String> result = pathsMemoized;
    if (result == null) {
      synchronized (this) {
        result = pathsMemoized;
        if (result == null) {
          if (pathChunks == null) {
            result = Collections.emptyList();
          } else {
            List<List<String>> chunks = new ArrayList<>();
            for (PathChunks chunk = pathChunks; chunk != null; chunk = chunk.previous) {
              chunks.add(chunk.paths);
            }
            List<String> flattened = new ArrayList<>(pathChunks.size);
            for (int i = chunks.size() - 1; i >= 0; i--) {
              flattened.addAll(chunks.get(i));
            }
            result = flattened;
          }
          pathsMemoized = result;
        }
      }
    }
    return result;
  }

  /** Upstream internal; public here per the translation conventions (internal is public in bytecode). */
  public FieldMask append(List<String> paths) {
    if (paths.isEmpty()) return this;
    return new FieldMask(
        new PathChunks(pathChunks, new ArrayList<>(paths), size(pathChunks) + paths.size()));
  }

  private static int size(PathChunks chunks) {
    return chunks == null ? 0 : chunks.size;
  }

  public FieldMask copy(List<String> paths) {
    return new FieldMask(paths);
  }

  public FieldMask copy() {
    return copy(paths());
  }

  @Override public boolean equals(Object other) {
    if (other == this) return true;
    return other instanceof FieldMask && paths().equals(((FieldMask) other).paths());
  }

  @Override public int hashCode() {
    return paths().hashCode();
  }

  @Override public String toString() {
    return "FieldMask{paths=" + paths() + "}";
  }

  private static final class PathChunks {
    final PathChunks previous;
    final List<String> paths;
    final int size;

    PathChunks(PathChunks previous, List<String> paths, int size) {
      this.previous = previous;
      this.paths = paths;
      this.size = size;
    }

    PathChunks(PathChunks previous, List<String> paths) {
      this(previous, paths, paths.size());
    }
  }
}
