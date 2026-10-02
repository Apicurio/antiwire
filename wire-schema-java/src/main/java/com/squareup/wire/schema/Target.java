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

import com.squareup.wire.internal.Serializable;
import java.util.List;

public abstract class Target implements Serializable {
  /**
   * Proto types to include generated sources for. Types listed here will be generated for this
   * target and not for subsequent targets in the task.
   *
   * <p>This list should contain package names (suffixed with {@code .*}) and type names only. It
   * should not contain member names.
   */
  public abstract List<String> includes();

  /**
   * Proto types to excluded generated sources for. Types listed here will not be generated for
   * this target.
   *
   * <p>This list should contain package names (suffixed with {@code .*}) and type names only. It
   * should not contain member names.
   */
  public abstract List<String> excludes();

  /**
   * True if types emitted for this target should not also be emitted for other targets. Use this
   * to cause multiple outputs to be emitted for the same input type.
   */
  public abstract boolean exclusive();

  /**
   * Directory where this target will write its output.
   *
   * <p>In Gradle, when this class is serialized, this is relative to the project to improve build
   * cacheability. Callers must use {@link #copyTarget} to resolve it to real path prior to use.
   */
  public abstract String outDirectory();

  /**
   * Returns a new Target object that is a copy of this one, but with the given fields updated.
   */
  // TODO(Benoit) We're only ever copying outDirectory, maybe we can remove other fields and rename
  //  the method?
  public abstract Target copyTarget(
      List<String> includes,
      List<String> excludes,
      boolean exclusive,
      String outDirectory
  );

  public Target copyTarget(List<String> includes, List<String> excludes, boolean exclusive) {
    return copyTarget(includes, excludes, exclusive, outDirectory());
  }

  public Target copyTarget(List<String> includes, List<String> excludes) {
    return copyTarget(includes, excludes, exclusive());
  }

  public Target copyTarget(List<String> includes) {
    return copyTarget(includes, excludes());
  }

  public abstract SchemaHandler newHandler();
}
