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

import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.ProtoType;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.SchemaLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a schema out of written {@code .proto} files. Java port of upstream's wire-schema-tests
 * SchemaBuilder; where upstream writes into an in-memory FakeFileSystem, this port writes into a
 * per-builder temp directory deleted on JVM exit (the recorded TASK-13 adaptation).
 */
public final class SchemaBuilder {
  private static final List<Path> TEMP_DIRS = new ArrayList<>();

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(SchemaBuilder::deleteTempDirs));
  }

  private final Path sourcePath;
  private final Path protoPath;
  private final List<ProtoType> opaqueTypes = new ArrayList<>();

  public SchemaBuilder() {
    try {
      Path parent = Files.createTempDirectory("antiwire-schema-builder");
      TEMP_DIRS.add(parent);
      this.sourcePath = Files.createDirectories(parent.resolve("sourcePath"));
      this.protoPath = Files.createDirectories(parent.resolve("protoPath"));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Add a file to be loaded into the schema.
   *
   * @param name The qualified name of the file.
   * @param protoFile The content of the file.
   */
  public SchemaBuilder add(String name, String protoFile) {
    return add(name, protoFile, sourcePath);
  }

  /**
   * Add a file to be loaded into the schema.
   *
   * @param name The qualified name of the file.
   * @param protoFile The content of the file.
   * @param base The directory on which {@code name} is based.
   */
  public SchemaBuilder add(String name, String protoFile, Path base) {
    if (!name.endsWith(".proto")) {
      throw new IllegalArgumentException(
          "unexpected file extension for " + name + ". Proto files should use the '.proto' extension");
    }

    try {
      Path resolvedPath = base.resolve(name);
      Path parent = resolvedPath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.write(resolvedPath, protoFile.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    return this;
  }

  /** Add a file to be linked against, but not used to generate artifacts. */
  public SchemaBuilder addProtoPath(String name, String protoFile) {
    return add(name, protoFile, protoPath);
  }

  /** See SchemaLoader.opaqueTypes. */
  public SchemaBuilder addOpaqueTypes(ProtoType... opaqueTypes) {
    this.opaqueTypes.addAll(java.util.Arrays.asList(opaqueTypes));
    return this;
  }

  public Schema build() {
    SchemaLoader schemaLoader = new SchemaLoader(okio.FileSystem.SYSTEM);
    schemaLoader.setOpaqueTypes(new ArrayList<>(opaqueTypes));
    try {
      schemaLoader.initRoots(
          java.util.Collections.singletonList(Location.get(sourcePath.toString())),
          java.util.Collections.singletonList(Location.get(protoPath.toString())));
      return schemaLoader.loadSchema();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Rewrites this builder's absolute source and proto path roots in {@code message} to the
   * {@code /sourcePath} and {@code /protoPath} prefixes that upstream's in-memory FakeFileSystem
   * produces, so adapted tests can assert upstream's exact error strings (the TASK-13 adaptation
   * for this builder's real-filesystem roots).
   */
  public String normalizeLocations(String message) {
    return message
        .replace(Location.get(sourcePath.toString()).path, "/sourcePath")
        .replace(Location.get(protoPath.toString()).path, "/protoPath");
  }

  private static void deleteTempDirs() {
    for (Path dir : TEMP_DIRS) {
      deleteRecursively(dir);
    }
  }

  private static void deleteRecursively(Path dir) {
    try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
      walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
      });
    } catch (IOException ignored) {
    }
  }
}
