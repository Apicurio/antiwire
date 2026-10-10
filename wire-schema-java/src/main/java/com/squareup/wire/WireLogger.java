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

import com.squareup.wire.internal.Serializable;
import com.squareup.wire.schema.ProtoType;
import java.util.Set;
import okio.Path;

/**
 * Logger class used by {@link com.squareup.wire.schema.WireRun} and {@link
 * com.squareup.wire.schema.SchemaHandler}s to log information related to processing the protobuf
 * {@link com.squareup.wire.schema.Schema}.
 */
public interface WireLogger {
  /**
   * This is called when an artifact is handled by a {@link
   * com.squareup.wire.schema.SchemaHandler}.
   *
   * @param outputPath is the path where the artifact is written on disk.
   * @param qualifiedName is the file path when generating a {@code .proto} file, the type or
   *     service name prefixed with its package name when generating a {@code .java} or {@code .kt}
   *     file, and the type name when generating a {@code .swift} file.
   * @param targetName is used to identify the concerned target. For JavaTarget, the name will be
   *     "Java". For KotlinTarget, the name will be "Kotlin". For SwiftTarget, the name will be
   *     "Swift". For ProtoTarget, the name will be "Proto".
   */
  void artifactHandled(Path outputPath, String qualifiedName, String targetName);

  /**
   * This is called when an artifact has been passed down to a {@link
   * com.squareup.wire.schema.SchemaHandler} but has been skipped. This is useful for dry-runs.
   *
   * @param type is the unique identifier for the skipped type.
   * @param targetName is used to identify the concerned target. For JavaTarget, the name will be
   *     "Java". For KotlinTarget, the name will be "Kotlin". For SwiftTarget, the name will be
   *     "Swift". For ProtoTarget, the name will be "Proto".
   */
  void artifactSkipped(ProtoType type, String targetName);

  /**
   * This is called if some {@code root} values have not been used when Wire pruned the schema
   * model. Note that {@code root} should contain package names (suffixed with {@code .*}), type
   * names, and member names only. It should not contain file paths. Unused roots can happen if
   * the referenced type or service isn't part of any {@code .proto} files defined in either
   * sourcePath or protoPath, or if a broader root value is already defined.
   */
  void unusedRoots(Set<String> unusedRoots);

  /**
   * This is called if some {@code prune} values have not been used when Wire pruned the schema
   * model. Note that {@code prune} should contain package names (suffixed with {@code .*}), type
   * names, and member names only. It should not contain file paths. Unused prunes can happen if
   * the referenced type or service isn't part of any {@code .proto} files defined in either
   * sourcePath or protoPath, or if a broader prune value is already defined.
   */
  void unusedPrunes(Set<String> unusedPrunes);

  /**
   * This is called if some {@code includes} values have not been used by the target they were
   * defined in. Note that {@code includes} should contain package names (suffixed with {@code
   * .*}) and type names only. It should not contain member names, nor file paths. Unused includes
   * can happen if the referenced type or service isn't part of the parsed and pruned schema
   * model, or has already been consumed by another preceding target.
   */
  // TODO(Benoit) We could pass the target name or something which makes it identifiable.
  void unusedIncludesInTarget(Set<String> unusedIncludes);

  /**
   * This is called if some {@code excludes} values have not been used by the target they were
   * defined in. Note that {@code excludes} should contain package names (suffixed with {@code
   * .*}) and type names only. It should not contain member names, nor file paths. Unused excludes
   * can happen if the referenced type or service isn't part of the parsed and pruned schema
   * model, or has already been consumed by another preceding target.
   */
  // TODO(Benoit) We could pass the target name or something which makes it identifiable.
  void unusedExcludesInTarget(Set<String> unusedExcludes);

  /** Implementations of this interface must have a no-arguments public constructor. */
  interface Factory extends Serializable {
    WireLogger create();
  }

  WireLogger NONE = new WireLogger() {
    @Override public void artifactHandled(Path outputPath, String qualifiedName,
        String targetName) {
    }

    @Override public void artifactSkipped(ProtoType type, String targetName) {
    }

    @Override public void unusedRoots(Set<String> unusedRoots) {
    }

    @Override public void unusedPrunes(Set<String> unusedPrunes) {
    }

    @Override public void unusedIncludesInTarget(Set<String> unusedIncludes) {
    }

    @Override public void unusedExcludesInTarget(Set<String> unusedExcludes) {
    }
  };

  /** Mirror of the Kotlin companion object: lets Java callers write {@code WireLogger.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }

    public WireLogger getNONE() {
      return NONE;
    }
  }
}
