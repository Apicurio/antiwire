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
package com.squareup.wire;

import com.squareup.wire.schema.ProtoType;
import java.util.Set;
import okio.Path;

/**
 * Test logger that records events into a string, translated from
 * {@code wire-compiler/src/test/java/com/squareup/wire/StringWireLogger.kt} at square/wire tag
 * 7.1.0 (TASK-16). Kotlin string templates become concatenation; the log format is unchanged.
 */
public class StringWireLogger implements WireLogger {
  boolean quiet = false;
  private final StringBuilder buffer = new StringBuilder();

  public String log() {
    return buffer.toString();
  }

  @Override public synchronized void artifactHandled(
      Path outputPath,
      String qualifiedName,
      String targetName) {
    buffer.append(outputPath + " " + qualifiedName + " (target=" + targetName + ")\n");
  }

  @Override public void artifactSkipped(ProtoType type, String targetName) {
    buffer.append("Skipped " + type + " (target=" + targetName + ")\n");
  }

  @Override public synchronized void unusedRoots(Set<String> unusedRoots) {
    if (quiet) return;

    buffer.append(
        "Unused element in treeShakingRoots:\n"
            + "  " + String.join("\n  ", unusedRoots));
  }

  @Override public synchronized void unusedPrunes(Set<String> unusedPrunes) {
    if (quiet) return;

    buffer.append(
        "Unused element in treeShakingRubbish:\n"
            + "  " + String.join("\n  ", unusedPrunes));
  }

  @Override public synchronized void unusedIncludesInTarget(Set<String> unusedIncludes) {
    if (quiet) return;

    buffer.append(
        "Unused includes in targets:\n"
            + "  " + String.join("\n  ", unusedIncludes));
  }

  @Override public synchronized void unusedExcludesInTarget(Set<String> unusedExcludes) {
    if (quiet) return;

    buffer.append(
        "Unused excludes in targets:\n"
            + "  " + String.join("\n  ", unusedExcludes));
  }
}
