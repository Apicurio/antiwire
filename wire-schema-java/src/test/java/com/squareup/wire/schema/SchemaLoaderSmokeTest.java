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
package com.squareup.wire.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-12 smoke corpus for the source loader: directory source paths, proto-path separation,
 * ZIP archives, classpath runtime protos, and loading errors. Full upstream SchemaLoaderTest
 * adoption is TASK-13.
 */
public class SchemaLoaderSmokeTest {
  @TempDir Path tempDir;

  private Path write(Path base, String relativePath, String content) throws IOException {
    Path file = base.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    return file;
  }

  @Test public void loadSchemaFromDirectory() throws Exception {
    Path sourcePath = Files.createDirectories(tempDir.resolve("source"));
    write(sourcePath, "squareup/protos/a.proto", ""
        + "syntax = \"proto3\";\n"
        + "package squareup.protos;\n"
        + "option java_package = \"com.squareup.protos\";\n"
        + "import \"squareup/protos/b.proto\";\n"
        + "message A { B b = 1; }\n");
    write(sourcePath, "squareup/protos/b.proto", ""
        + "syntax = \"proto3\";\n"
        + "package squareup.protos;\n"
        + "message B { string s = 1; }\n");

    SchemaLoader loader = new SchemaLoader(okio.FileSystem.SYSTEM);
    loader.initRoots(
        Collections.singletonList(Location.get(sourcePath.toString())));
    Schema schema = loader.loadSchema();

    assertNotNull(schema.getType("squareup.protos.A"));
    assertNotNull(schema.getType("squareup.protos.B"));
    assertEquals("com.squareup.protos", schema.protoFile("squareup/protos/a.proto").javaPackage());
    assertEquals(2, loader.sourcePathFiles().size());
  }

  @Test public void protoPathIsLinkedButNotASource() throws Exception {
    Path sourcePath = Files.createDirectories(tempDir.resolve("source"));
    Path protoPath = Files.createDirectories(tempDir.resolve("proto"));
    write(sourcePath, "a.proto", ""
        + "syntax = \"proto3\";\n"
        + "import \"dep.proto\";\n"
        + "message A { Dep dep = 1; }\n");
    write(protoPath, "dep.proto", ""
        + "syntax = \"proto3\";\n"
        + "message Dep { string s = 1; }\n");

    SchemaLoader loader = new SchemaLoader(okio.FileSystem.SYSTEM);
    loader.initRoots(
        Collections.singletonList(Location.get(sourcePath.toString())),
        Collections.singletonList(Location.get(protoPath.toString())));
    Schema schema = loader.loadSchema();

    assertNotNull(schema.getType("A"));
    assertNotNull(schema.getType("Dep"));
    assertEquals(1, loader.sourcePathFiles().size());
    assertEquals("a.proto", loader.sourcePathFiles().get(0).location().path);
  }

  @Test public void loadSchemaFromZip() throws Exception {
    Path zip = tempDir.resolve("protos.zip");
    try (OutputStream out = Files.newOutputStream(zip);
        ZipOutputStream zipOut = new ZipOutputStream(out)) {
      zipOut.putNextEntry(new ZipEntry("squareup/protos/zipped.proto"));
      zipOut.write((""
          + "syntax = \"proto3\";\n"
          + "package squareup.protos;\n"
          + "message Zipped { string s = 1; }\n").getBytes(StandardCharsets.UTF_8));
      zipOut.closeEntry();
    }

    SchemaLoader loader = new SchemaLoader(okio.FileSystem.SYSTEM);
    loader.initRoots(Collections.singletonList(Location.get(zip.toString())));
    Schema schema = loader.loadSchema();

    assertNotNull(schema.getType("squareup.protos.Zipped"));
    assertEquals(1, loader.sourcePathFiles().size());
    assertEquals("squareup/protos/zipped.proto",
        loader.sourcePathFiles().get(0).location().path);
  }

  @Test public void noSourcesFails() throws Exception {
    Path sourcePath = Files.createDirectories(tempDir.resolve("empty"));
    SchemaLoader loader = new SchemaLoader(okio.FileSystem.SYSTEM);
    loader.initRoots(Collections.singletonList(Location.get(sourcePath.toString())));
    SchemaException e = assertThrows(SchemaException.class, loader::loadSchema);
    assertTrue(e.getMessage().contains("no sources"), e.getMessage());
  }

  @Test public void ambiguousPathFails() throws Exception {
    Path sourcePath = Files.createDirectories(tempDir.resolve("source"));
    Path first = Files.createDirectories(tempDir.resolve("first"));
    Path second = Files.createDirectories(tempDir.resolve("second"));
    write(sourcePath, "a.proto", ""
        + "syntax = \"proto3\";\n"
        + "import \"dep.proto\";\n"
        + "message A { Dep dep = 1; }\n");
    write(first, "dep.proto", "syntax = \"proto3\";\nmessage Dep { string s = 1; }\n");
    write(second, "dep.proto", "syntax = \"proto3\";\nmessage Dep { string s = 1; }\n");

    SchemaLoader loader = new SchemaLoader(okio.FileSystem.SYSTEM);
    loader.initRoots(
        Collections.singletonList(Location.get(sourcePath.toString())),
        Arrays.asList(Location.get(first.toString()), Location.get(second.toString())));
    SchemaException e = assertThrows(SchemaException.class, loader::loadSchema);
    assertTrue(e.getMessage().contains("dep.proto is ambiguous"), e.getMessage());
  }

  @Test public void singleFileLocationMustMatchItsImportPath() throws Exception {
    Path sourcePath = Files.createDirectories(tempDir.resolve("source"));
    // Package says squareup/protos/, but the file sits at the root of the source path.
    Path file = write(sourcePath, "misplaced.proto", ""
        + "syntax = \"proto3\";\n"
        + "package squareup.protos;\n"
        + "message Misplaced { string s = 1; }\n");

    SchemaLoader loader = new SchemaLoader(okio.FileSystem.SYSTEM);
    loader.initRoots(Collections.singletonList(Location.get(file.toString())));
    SchemaException e = assertThrows(SchemaException.class, loader::loadSchema);
    assertTrue(e.getMessage().contains("to have a path ending with squareup/protos/misplaced.proto"),
        e.getMessage());
  }

  @Test public void loadExhaustivelyIncludesTransitiveFiles() throws Exception {
    Path sourcePath = Files.createDirectories(tempDir.resolve("source"));
    write(sourcePath, "a.proto", ""
        + "syntax = \"proto3\";\n"
        + "import \"b.proto\";\n"
        + "message A { B b = 1; }\n");
    write(sourcePath, "b.proto", ""
        + "syntax = \"proto3\";\n"
        + "import \"c.proto\";\n"
        + "message B { C c = 1; }\n");
    write(sourcePath, "c.proto", ""
        + "syntax = \"proto3\";\n"
        + "message C { string s = 1; }\n");

    SchemaLoader loader = new SchemaLoader(okio.FileSystem.SYSTEM);
    loader.setLoadExhaustively(true);
    loader.initRoots(Collections.singletonList(Location.get(sourcePath.toString())));
    Schema schema = loader.loadSchema();

    // c.proto is only reachable through b.proto's import, but loadExhaustively keeps it.
    assertNotNull(schema.getType("C"));
    List<ProtoFile> sourceFiles = loader.sourcePathFiles();
    assertEquals(3, sourceFiles.size());
  }
}
