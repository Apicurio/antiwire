/*
 * Copyright (C) 2023 Square, Inc.
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
package com.squareup.wire.java;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import com.squareup.wire.schema.Extend;
import com.squareup.wire.schema.Field;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.Profile;
import com.squareup.wire.schema.ProfileLoader;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.SchemaHandler;
import com.squareup.wire.schema.Service;
import com.squareup.wire.schema.Type;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import okio.BufferedSink;
import okio.Okio;
import okio.Path;

public class JavaSchemaHandler extends SchemaHandler {
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

  private JavaGenerator javaGenerator;

  public JavaSchemaHandler() {
    this(
        false /* android */,
        false /* androidAnnotations */,
        false /* compact */,
        true /* emitDeclaredOptions */,
        true /* emitAppliedOptions */,
        false /* buildersOnly */);
  }

  public JavaSchemaHandler(
      boolean android,
      boolean androidAnnotations,
      boolean compact,
      boolean emitDeclaredOptions,
      boolean emitAppliedOptions,
      boolean buildersOnly) {
    this.android = android;
    this.androidAnnotations = androidAnnotations;
    this.compact = compact;
    this.emitDeclaredOptions = emitDeclaredOptions;
    this.emitAppliedOptions = emitAppliedOptions;
    this.buildersOnly = buildersOnly;
  }

  @Override public void handle(Schema schema, Context context) {
    String profileName = android ? "android" : "java";
    ProfileLoader profileLoader = context.getProfileLoader();
    if (profileLoader == null) {
      throw new IllegalStateException("JavaSchemaHandler requires a profile loader");
    }
    Profile profile;
    try {
      profile = profileLoader.loadProfile(profileName, schema);
    } catch (IOException e) {
      // The port's SchemaHandler.handle carries no checked IOException (upstream Kotlin has
      // none), so the failure propagates unchecked like every other IO failure below.
      throw new RuntimeException("Error loading profile " + profileName, e);
    }
    javaGenerator = JavaGenerator.get(schema)
        .withProfile(profile)
        .withAndroid(android)
        .withAndroidAnnotations(androidAnnotations)
        .withCompact(compact)
        .withOptions(emitDeclaredOptions, emitAppliedOptions)
        .withBuildersOnly(buildersOnly);

    createOutDirectory(context);

    super.handle(schema, context);
  }

  @Override public Path handle(Type type, Context context) {
    if (JavaGenerator.builtInType(type.getType())) return null;

    TypeSpec typeSpec = javaGenerator.generateType(type);
    ClassName javaTypeName = javaGenerator.generatedTypeName(type);
    return write(javaTypeName, typeSpec, type.getType(), type.getLocation(), context);
  }

  @Override public List<Path> handle(Service service, Context context) {
    // Service handling isn't supporting in Java.
    return Collections.emptyList();
  }

  @Override public Path handle(Extend extend, Field field, Context context) {
    TypeSpec typeSpec = javaGenerator.generateOptionType(extend, field);
    if (typeSpec == null) return null;
    ClassName javaTypeName = javaGenerator.generatedTypeName(extend.member(field));
    return write(javaTypeName, typeSpec, field.getQualifiedName(), field.getLocation(), context);
  }

  private Path write(
      ClassName javaTypeName,
      TypeSpec typeSpec,
      Object source,
      Location location,
      Context context) {
    Path outDirectory = context.getOutDirectory();
    JavaFile javaFile = JavaFile.builder(javaTypeName.packageName(), typeSpec)
        .addFileComment("$L", CODE_GENERATED_BY_WIRE)
        .addFileComment("\nSource: $L in $L", source, location.withPathOnly())
        .build();
    Path filePath = outDirectory
        // Square JavaPoet exposes packageName as a public final field (the palantir fork used
        // an accessor method); same value either way.
        .div(javaFile.packageName.replace(".", "/"))
        .div(javaTypeName.simpleName() + ".java");
    checkPathInOutDirectory(filePath, outDirectory);

    context.getLogger().artifactHandled(
        outDirectory,
        javaFile.packageName + "." + javaFile.typeSpec.name,
        "Java");
    try {
      context.getFileSystem().createDirectories(filePath.parent(), false);
      // The vendored okio FileSystem has no write() extension; open a sink and write UTF-8.
      try (BufferedSink sink = Okio.buffer(context.getFileSystem().sink(filePath, false))) {
        sink.writeUtf8(javaFile.toString());
      }
    } catch (IOException e) {
      throw new RuntimeException(
          "Error emitting " + javaFile.packageName + "." + javaFile.typeSpec.name
              + " to " + outDirectory,
          e);
    }
    return filePath;
  }

  private static final String CODE_GENERATED_BY_WIRE =
      "Code generated by Wire protocol buffer compiler, do not edit.";
}
