/*
 * Copyright (C) 2013 Square, Inc.
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

class ConsoleWireLogger implements WireLogger {
  boolean quiet = false;

  @Override public void unusedRoots(Set<String> unusedRoots) {
    if (quiet) return;

    System.out.println(
        "Unused element in treeShakingRoots:\n  " + String.join("\n  ", unusedRoots) + "\n");
  }

  @Override public void unusedPrunes(Set<String> unusedPrunes) {
    if (quiet) return;

    System.out.println(
        "Unused element in treeShakingRubbish:\n  " + String.join("\n  ", unusedPrunes) + "\n");
  }

  @Override public void unusedIncludesInTarget(Set<String> unusedIncludes) {
    if (quiet) return;

    System.out.println(
        "Unused includes in targets:\n  " + String.join("\n  ", unusedIncludes) + "\n");
  }

  @Override public void unusedExcludesInTarget(Set<String> unusedExcludes) {
    if (quiet) return;

    System.out.println(
        "Unused excludes in targets:\n  " + String.join("\n  ", unusedExcludes) + "\n");
  }

  @Override public void artifactHandled(Path outputPath, String qualifiedName, String targetName) {
    if (quiet) return;

    System.out.println("Writing " + qualifiedName + " to " + outputPath + " (target=" + targetName
        + ")");
  }

  @Override public void artifactSkipped(ProtoType type, String targetName) {
    if (quiet) return;

    System.out.println("Skipping " + type + " (target=" + targetName + ")");
  }
}
