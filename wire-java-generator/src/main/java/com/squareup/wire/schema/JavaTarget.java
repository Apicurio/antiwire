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

import com.squareup.wire.java.JavaSchemaHandler;
import java.util.List;

/** Generate {@code .java} sources. */
public final class JavaTarget extends Target {
  private final List<String> includes;
  private final List<String> excludes;

  private final boolean exclusive;

  private final String outDirectory;

  /** True for emitted types to implement {@code android.os.Parcelable}. */
  private final boolean android;

  /** True to enable the {@code androidx.annotation.Nullable} annotation where applicable. */
  private final boolean androidAnnotations;

  /**
   * True to emit code that uses reflection for reading, writing, and toString methods which are
   * normally implemented with generated code.
   */
  private final boolean compact;

  /** True to emit types for options declared on messages, fields, etc. */
  private final boolean emitDeclaredOptions;

  /** True to emit annotations for options applied on messages, fields, etc. */
  private final boolean emitAppliedOptions;

  /** If true, the constructor of all generated types will be non-public. */
  private final boolean buildersOnly;

  public JavaTarget(String outDirectory) {
    this(
        java.util.Collections.singletonList("*"),
        java.util.Collections.emptyList(),
        true /* exclusive */,
        outDirectory,
        false /* android */,
        false /* androidAnnotations */,
        false /* compact */,
        true /* emitDeclaredOptions */,
        true /* emitAppliedOptions */,
        false /* buildersOnly */);
  }

  public JavaTarget(
      List<String> includes,
      List<String> excludes,
      boolean exclusive,
      String outDirectory,
      boolean android,
      boolean androidAnnotations,
      boolean compact,
      boolean emitDeclaredOptions,
      boolean emitAppliedOptions,
      boolean buildersOnly) {
    this.includes = includes;
    this.excludes = excludes;
    this.exclusive = exclusive;
    this.outDirectory = outDirectory;
    this.android = android;
    this.androidAnnotations = androidAnnotations;
    this.compact = compact;
    this.emitDeclaredOptions = emitDeclaredOptions;
    this.emitAppliedOptions = emitAppliedOptions;
    this.buildersOnly = buildersOnly;
  }

  @Override public List<String> includes() {
    return includes;
  }

  @Override public List<String> excludes() {
    return excludes;
  }

  @Override public boolean exclusive() {
    return exclusive;
  }

  @Override public String outDirectory() {
    return outDirectory;
  }

  @Override public Target copyTarget(
      List<String> includes, List<String> excludes, boolean exclusive, String outDirectory) {
    return new JavaTarget(
        includes,
        excludes,
        exclusive,
        outDirectory,
        android,
        androidAnnotations,
        compact,
        emitDeclaredOptions,
        emitAppliedOptions,
        buildersOnly);
  }

  @Override public SchemaHandler newHandler() {
    return new JavaSchemaHandler(
        android,
        androidAnnotations,
        compact,
        emitDeclaredOptions,
        emitAppliedOptions,
        buildersOnly);
  }
}
