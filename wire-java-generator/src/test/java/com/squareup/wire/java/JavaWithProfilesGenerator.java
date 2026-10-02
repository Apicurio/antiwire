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
package com.squareup.wire.java;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.Profile;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.Type;
import com.squareup.wire.schema.internal.ProfileParser;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper class to run Java code generation, translated from
 * {@code wire-java-generator/src/test/java/com/squareup/wire/java/JavaWithProfilesGenerator.kt}
 * at square/wire tag 7.1.0 (TASK-16). Kotlin's {@code @JvmOverloads} defaults become explicit
 * overloads; behavior is unchanged.
 */
final class JavaWithProfilesGenerator {
  private final Schema schema;
  private final Map<String, Profile> profiles = new LinkedHashMap<>();

  JavaWithProfilesGenerator(Schema schema) {
    this.schema = schema;
  }

  /**
   * Load a {@link Profile} on the file system.
   *
   * @param name The qualified name of the file. This can contain slashes.
   * @param profileFile The content of the file.
   */
  JavaWithProfilesGenerator withProfile(String name, String profileFile) {
    if (!name.endsWith(".wire")) {
      throw new IllegalArgumentException(
          "unexpected file extension for " + name + ". Profile files should use the '.wire' extension");
    }

    Profile profile = new Profile(
        Collections.singletonList(new ProfileParser(Location.get(""), profileFile).read()));
    Profile existingEntry = profiles.putIfAbsent(name, profile);
    if (existingEntry != null) {
      throw new IllegalStateException("A profile was already set: " + name);
    }

    return this;
  }

  private Profile profile(String profileName) {
    return profileName == null ? new Profile() : profiles.get(profileName + ".wire");
  }

  String generateJava(String typeName) throws IOException {
    return generateJava(typeName, null, false, false, false);
  }

  String generateJava(String typeName, String profileName) throws IOException {
    return generateJava(typeName, profileName, false, false, false);
  }

  String generateJava(String typeName, String profileName, boolean buildersOnly)
      throws IOException {
    return generateJava(typeName, profileName, buildersOnly, false, false);
  }

  String generateJava(String typeName, String profileName, boolean buildersOnly,
      boolean emitDeclaredOptions) throws IOException {
    return generateJava(typeName, profileName, buildersOnly, emitDeclaredOptions, false);
  }

  String generateJava(
      String typeName,
      String profileName,
      boolean buildersOnly,
      boolean emitDeclaredOptions,
      boolean emitAppliedOptions) throws IOException {
    JavaGenerator javaGenerator = JavaGenerator.get(schema)
        .withProfile(profile(profileName))
        .withBuildersOnly(buildersOnly)
        .withOptions(emitDeclaredOptions, emitAppliedOptions);
    Type type = schema.getType(typeName);
    TypeSpec typeSpec = javaGenerator.generateType(type);
    String packageName = javaGenerator.generatedTypeName(type).packageName();
    JavaFile javaFile = JavaFile.builder(packageName, typeSpec).build();
    return javaFile.toString();
  }
}
