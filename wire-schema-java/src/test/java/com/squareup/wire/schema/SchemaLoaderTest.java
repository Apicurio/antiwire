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

import com.squareup.wire.schema.internal.CommonSchemaLoader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import okio.ByteString;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Upstream wire-schema jvmTest SchemaLoaderTest.kt translated to Java 11 JUnit 5 (TASK-13).
 *
 * <p>Recorded adaptations, applied uniformly below: upstream builds its file system in okio's
 * in-memory FakeFileSystem rooted at the working directory "/"; this test builds the same tree
 * under a JUnit {@link TempDir} on {@code okio.FileSystem.SYSTEM}, so asserted locations and
 * error messages embed the absolute temp-directory prefix where upstream's are relative (like
 * "colors/src/main/proto" and "lib/curves.zip"). Like upstream, this drives CommonSchemaLoader
 * directly rather than the SchemaLoader wrapper: the port keeps that class public in
 * {@code com.squareup.wire.schema.internal} and TASK-13 widened
 * {@code loadSourcePathFiles()} and {@code reportLoadingErrors()} from package-private to
 * public for exactly these callers, because reporting the same loads through
 * {@code loadSchema()} instead would attach the linker's "for file ..." context to the asserted
 * messages. Loaders that open ZIP file systems are closed so the temporary copies the vendored
 * okio materializes are released.
 */
// TODO(Benoit) Move this class to commonTest, and test `SchemaLoader` instead of `CommonSchemaLoader`.
public class SchemaLoaderTest {
  @TempDir Path tempDir;

  @Test public void happyPath() throws Exception {
    // Dependency graph:
    //   - blue
    //     - circle
    //       - triangle
    //   - red
    //     - oval
    //     - triangle
    // Note that the protoPath element octagon.proto is not imported!
    add("colors/src/main/proto/squareup/colors/blue.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "import \"squareup/curves/circle.proto\";\n"
        + "message Blue {\n"
        + "}\n");
    add("colors/src/main/proto/squareup/colors/red.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "import \"squareup/curves/oval.proto\";\n"
        + "import \"squareup/polygons/triangle.proto\";\n"
        + "message Red {\n"
        + "}\n");
    add("polygons/src/main/proto/squareup/polygons/octagon.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.polygons;\n"
        + "message Octagon {\n"
        + "}\n");
    add("polygons/src/main/proto/squareup/polygons/triangle.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.polygons;\n"
        + "message Triangle {\n"
        + "}\n");
    addZip("lib/curves.zip",
        "squareup/curves/circle.proto", ""
            + "syntax = \"proto2\";\n"
            + "package squareup.curves;\n"
            + "import \"squareup/polygons/triangle.proto\";\n"
            + "message Circle {\n"
            + "}\n",
        "squareup/curves/oval.proto", ""
            + "syntax = \"proto2\";\n"
            + "package squareup.curves;\n"
            + "message Oval {\n"
            + "}\n");

    Path sourceDir = tempDir.resolve("colors/src/main/proto");
    Path polygonsDir = tempDir.resolve("polygons/src/main/proto");
    Path curvesZip = tempDir.resolve("lib/curves.zip");

    CommonSchemaLoader loader = new CommonSchemaLoader(okio.FileSystem.SYSTEM);
    try {
      loader.initRoots(
          Collections.singletonList(Location.get(sourceDir.toString())),
          Arrays.asList(
              Location.get(polygonsDir.toString()),
              Location.get(curvesZip.toString())));
      List<ProtoFile> sourcePathFiles = loader.loadSourcePathFiles();
      assertEquals(Arrays.asList(
          Location.get(sourceDir.toString(), "squareup/colors/blue.proto"),
          Location.get(sourceDir.toString(), "squareup/colors/red.proto")),
          locations(sourcePathFiles));
      assertEquals(Location.get("google/protobuf/descriptor.proto"),
          loader.load("google/protobuf/descriptor.proto").location());
      assertEquals(Location.get(curvesZip.toString(), "squareup/curves/circle.proto"),
          loader.load("squareup/curves/circle.proto").location());
      assertEquals(Location.get(curvesZip.toString(), "squareup/curves/oval.proto"),
          loader.load("squareup/curves/oval.proto").location());
      assertEquals(Location.get(polygonsDir.toString(), "squareup/polygons/triangle.proto"),
          loader.load("squareup/polygons/triangle.proto").location());
      loader.reportLoadingErrors();
    } finally {
      loader.close();
    }
  }

  @Test public void noSourcesFound() throws Exception {
    CommonSchemaLoader loader = new CommonSchemaLoader(okio.FileSystem.SYSTEM);
    SchemaException e = assertThrows(SchemaException.class, () -> {
      // TASK-13 adaptation: Kotlin's default protoPath argument becomes an explicit empty list.
      loader.initRoots(Collections.emptyList(), Collections.emptyList());
      loader.loadSourcePathFiles();
    });
    assertEquals("no sources", e.getMessage());
  }

  @Test public void packageDoesNotMatchFileSystemIsOkayWithBaseDirectory() throws Exception {
    add("colors/src/main/proto/squareup/shapes/blue.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "message Blue {\n"
        + "}\n");

    Path sourceDir = tempDir.resolve("colors/src/main/proto");
    CommonSchemaLoader loader = new CommonSchemaLoader(okio.FileSystem.SYSTEM);
    loader.initRoots(
        Collections.singletonList(
            Location.get(sourceDir.toString(), "squareup/shapes/blue.proto")),
        Collections.emptyList());
    List<ProtoFile> sourcePathFiles = loader.loadSourcePathFiles();
    assertEquals(Collections.singletonList("squareup/shapes/blue.proto"),
        paths(sourcePathFiles));
  }

  @Test public void packageDoesNotMatchFileSystemFailsWithoutBaseDirectory() throws Exception {
    Path file = add("colors/src/main/proto/squareup/shapes/blue.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "message Blue {\n"
        + "}\n");

    CommonSchemaLoader loader = new CommonSchemaLoader(okio.FileSystem.SYSTEM);
    SchemaException e = assertThrows(SchemaException.class, () -> {
      loader.initRoots(
          Collections.singletonList(Location.get(file.toString())),
          Collections.emptyList());
      loader.loadSourcePathFiles();
    });
    // TASK-13 adaptation: upstream's FakeFileSystem path "colors/src/main/proto/..." becomes the
    // absolute @TempDir path here; the message shape is upstream's.
    assertEquals("expected " + Location.get(file.toString()).path
            + " to have a path ending with squareup/colors/blue.proto",
        e.getMessage());
  }

  @Test public void protoPathSpecifiedWithBaseAndFile() throws Exception {
    add("colors/src/main/proto/squareup/colors/blue.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "import \"squareup/curves/circle.proto\";\n"
        + "message Blue {\n"
        + "}\n");
    add("curves/src/main/proto/squareup/curves/circle.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.curves;\n"
        + "message Circle {\n"
        + "}\n");

    Path sourceDir = tempDir.resolve("colors/src/main/proto");
    Path protoDir = tempDir.resolve("curves/src/main/proto");
    CommonSchemaLoader loader = new CommonSchemaLoader(okio.FileSystem.SYSTEM);
    loader.initRoots(
        Collections.singletonList(Location.get(sourceDir.toString())),
        Collections.singletonList(
            Location.get(protoDir.toString(), "squareup/curves/circle.proto")));
    List<ProtoFile> sourcePathFiles = loader.loadSourcePathFiles();
    assertContainsExactlyInAnyOrder(paths(sourcePathFiles), "squareup/colors/blue.proto");
    assertEquals(Location.get("google/protobuf/descriptor.proto"),
        loader.load("google/protobuf/descriptor.proto").location());
    assertEquals(Location.get(protoDir.toString(), "squareup/curves/circle.proto"),
        loader.load("squareup/curves/circle.proto").location());
  }

  @Test public void emptyPackagedProtoMessage() throws Exception {
    add("address.proto", ""
        + "syntax = \"proto3\";\n"
        + "option java_package =\"address\";\n"
        + "\n"
        + "message Address {\n"
        + "  string street = 1;\n"
        + "  int32 zip = 2;\n"
        + "  string city = 3;\n"
        + "}\n");
    add("customer.proto", ""
        + "syntax = \"proto3\";\n"
        + "option java_package =\"customer\";\n"
        + "\n"
        + "import \"address.proto\";\n"
        + "\n"
        + "message Customer {\n"
        + "  string name = 1;\n"
        + "  Address address = 3;\n"
        + "}\n");

    CommonSchemaLoader loader = new CommonSchemaLoader(okio.FileSystem.SYSTEM);
    loader.initRoots(
        Collections.singletonList(Location.get(tempDir.toString())),
        Collections.singletonList(Location.get(tempDir.toString())));
    Schema schema = loader.loadSchema();
    assertTrue(schema.getType(ProtoType.get("Address")) instanceof MessageType);
    assertTrue(schema.getType(ProtoType.get("Customer")) instanceof MessageType);
  }

  @Test public void bomAwareLoading() throws Exception {
    add("colors/squareup/colors/red.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "message Red {\n"
        + "}\n",
        StandardCharsets.UTF_8, ByteString.decodeHex("efbbbf").toByteArray());
    add("colors/squareup/colors/orange.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "message Orange {\n"
        + "}\n",
        StandardCharsets.UTF_16BE, ByteString.decodeHex("feff").toByteArray());
    add("colors/squareup/colors/yellow.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "message Yellow {\n"
        + "}\n",
        StandardCharsets.UTF_16LE, ByteString.decodeHex("fffe").toByteArray());
    add("colors/squareup/colors/green.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "message Green {\n"
        + "}\n",
        Charset.forName("UTF-32BE"), ByteString.decodeHex("0000feff").toByteArray());
    add("colors/squareup/colors/blue.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "message Blue {\n"
        + "}\n",
        Charset.forName("UTF-32LE"), ByteString.decodeHex("fffe0000").toByteArray());

    Path sourceDir = tempDir.resolve("colors");
    CommonSchemaLoader loader = new CommonSchemaLoader(okio.FileSystem.SYSTEM);
    loader.initRoots(
        Collections.singletonList(Location.get(sourceDir.toString())),
        Collections.emptyList());
    List<ProtoFile> sourcePathFiles = loader.loadSourcePathFiles();
    assertContainsExactlyInAnyOrder(paths(sourcePathFiles),
        "squareup/colors/red.proto",
        "squareup/colors/orange.proto",
        "squareup/colors/yellow.proto",
        "squareup/colors/green.proto",
        "squareup/colors/blue.proto");
  }

  @Test public void symlinkDirectory() throws Exception {
    assumeSymlinksSupported();

    add("secret/proto/squareup/colors/blue.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "message Blue {\n"
        + "}\n");
    Files.createDirectories(tempDir.resolve("colors/src/main"));
    // TASK-13 adaptation: the symlink target is absolute. Upstream's relative
    // "../../../secret/proto" target works inside FakeFileSystem; the port resolves the link
    // target itself, which a relative target would resolve against the process directory.
    Files.createSymbolicLink(
        tempDir.resolve("colors/src/main/proto"), tempDir.resolve("secret/proto"));

    Path sourceDir = tempDir.resolve("colors/src/main/proto");
    CommonSchemaLoader loader = new CommonSchemaLoader(okio.FileSystem.SYSTEM);
    loader.initRoots(
        Collections.singletonList(Location.get(sourceDir.toString())),
        Collections.emptyList());
    List<ProtoFile> sourcePathFiles = loader.loadSourcePathFiles();
    assertEquals(Collections.singletonList(
        Location.get(sourceDir.toString(), "squareup/colors/blue.proto")),
        locations(sourcePathFiles));
  }

  @Test public void nameCollisions() throws Exception {
    String content = ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "message Blue {}";

    add("colors/squareup/colors/a.proto", content);

    Path colors = tempDir.resolve("colors");
    Path nestedColors = tempDir.resolve("colors/squareup");
    CommonSchemaLoader loader = new CommonSchemaLoader(okio.FileSystem.SYSTEM);
    SchemaException e = assertThrows(SchemaException.class, () -> {
      loader.initRoots(
          Arrays.asList(Location.get(colors.toString()), Location.get(nestedColors.toString())),
          Collections.emptyList());
      loader.loadSchema();
    });
    // TASK-13 adaptation: upstream's FakeFileSystem bases "colors" and "colors/squareup" become
    // the absolute @TempDir bases here; the message shape is upstream's.
    assertEquals(""
        + "same type 'squareup.colors.Blue' from the same file loaded from different paths:\n"
        + "  1. base:" + colors + ", path:squareup/colors/a.proto:3:1\n"
        + "  2. base:" + nestedColors + ", path:colors/a.proto:3:1",
        e.getMessage());
  }

  @Test public void symlinkFile() throws Exception {
    assumeSymlinksSupported();

    Path target = add("secret/proto/squareup/colors/blue.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "message Blue {\n"
        + "}\n");
    Files.createDirectories(tempDir.resolve("colors/src/main/proto/squareup/colors"));
    // TASK-13 adaptation: absolute symlink target; see symlinkDirectory.
    Files.createSymbolicLink(
        tempDir.resolve("colors/src/main/proto/squareup/colors/blue.proto"), target);

    Path sourceDir = tempDir.resolve("colors/src/main/proto");
    CommonSchemaLoader loader = new CommonSchemaLoader(okio.FileSystem.SYSTEM);
    loader.initRoots(
        Collections.singletonList(Location.get(sourceDir.toString())),
        Collections.emptyList());
    List<ProtoFile> sourcePathFiles = loader.loadSourcePathFiles();
    assertContainsExactlyInAnyOrder(locations(sourcePathFiles),
        Location.get(sourceDir.toString(), "squareup/colors/blue.proto"));
  }

  @Test public void importNotFound() throws Exception {
    add("colors/src/main/proto/squareup/colors/blue.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "import \"squareup/curves/circle.proto\";\n"
        + "import \"squareup/polygons/rectangle.proto\";\n"
        + "message Blue {\n"
        + "}\n");
    add("polygons/src/main/proto/squareup/polygons/triangle.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.polygons;\n"
        + "message Triangle {\n"
        + "}\n");
    addZip("lib/curves.zip",
        "squareup/curves/oval.proto", ""
            + "syntax = \"proto2\";\n"
            + "package squareup.curves;\n"
            + "message Oval {\n"
            + "}\n");

    Path sourceDir = tempDir.resolve("colors/src/main/proto");
    Path polygonsDir = tempDir.resolve("polygons/src/main/proto");
    Path curvesZip = tempDir.resolve("lib/curves.zip");

    CommonSchemaLoader loader = new CommonSchemaLoader(okio.FileSystem.SYSTEM);
    try {
      SchemaException e = assertThrows(SchemaException.class, () -> {
        loader.initRoots(
            Collections.singletonList(Location.get(sourceDir.toString())),
            Arrays.asList(
                Location.get(polygonsDir.toString()),
                Location.get(curvesZip.toString())));
        loader.loadSourcePathFiles();
        loader.load("squareup/curves/circle.proto");
        loader.load("squareup/polygons/rectangle.proto");
        loader.reportLoadingErrors();
      });
      // TASK-13 adaptation: upstream's FakeFileSystem proto paths become the absolute @TempDir
      // paths here; the message shape is upstream's.
      assertEquals(""
          + "unable to find squareup/curves/circle.proto\n"
          + "  searching 2 proto paths:\n"
          + "    " + polygonsDir + "\n"
          + "    " + curvesZip + "\n"
          + "unable to find squareup/polygons/rectangle.proto\n"
          + "  searching 2 proto paths:\n"
          + "    " + polygonsDir + "\n"
          + "    " + curvesZip,
          e.getMessage());
    } finally {
      loader.close();
    }
  }

  @Test public void ambiguousImport() throws Exception {
    add("colors/src/main/proto/squareup/colors/blue.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "import \"squareup/curves/circle.proto\";\n"
        + "message Blue {\n"
        + "}\n");
    add("polygons/src/main/proto/squareup/curves/circle.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.curves;\n"
        + "message Circle {\n"
        + "}\n");
    addZip("lib/curves.zip",
        "squareup/curves/circle.proto", ""
            + "syntax = \"proto2\";\n"
            + "package squareup.curves;\n"
            + "message Circle {\n"
            + "}\n");

    Path sourceDir = tempDir.resolve("colors/src/main/proto");
    Path polygonsDir = tempDir.resolve("polygons/src/main/proto");
    Path curvesZip = tempDir.resolve("lib/curves.zip");

    CommonSchemaLoader loader = new CommonSchemaLoader(okio.FileSystem.SYSTEM);
    try {
      SchemaException e = assertThrows(SchemaException.class, () -> {
        loader.initRoots(
            Collections.singletonList(Location.get(sourceDir.toString())),
            Arrays.asList(
                Location.get(polygonsDir.toString()),
                Location.get(curvesZip.toString())));
        loader.loadSourcePathFiles();
        loader.load("squareup/curves/circle.proto");
        loader.reportLoadingErrors();
      });
      // TASK-13 adaptation: upstream's FakeFileSystem proto paths become the absolute @TempDir
      // paths here; the message shape is upstream's.
      assertEquals(""
          + "squareup/curves/circle.proto is ambiguous:\n"
          + "  " + curvesZip + "/squareup/curves/circle.proto\n"
          + "  " + polygonsDir + "/squareup/curves/circle.proto",
          e.getMessage());
    } finally {
      loader.close();
    }
  }

  // TASK-13 adaptation: deferred. locationsToCheck() belongs to the profile layer the port
  // defers to TASK-16 (see CommonSchemaLoader's class comment), so there is no API to call yet.
  // Upstream's case and expected values are preserved below for TASK-16 to revive verbatim.
  @Disabled("TASK-16: the profile layer (locationsToCheck) is not ported yet")
  @Test public void locationsToCheck() {
    // val newSchemaLoader = CommonSchemaLoader(fs)
    // val result = newSchemaLoader.locationsToCheck(
    //   "java",
    //   listOf(
    //     Location.get("shared-protos.jar", "squareup/cash/money/Money.proto"),
    //     Location.get("src/main/proto", "squareup/cash/Service.proto"),
    //     Location.get("src/main/proto", "squareup/cash/cashtags/Cashtag.proto"),
    //     Location.get("src/main/proto", "squareup/cash/payments/Payment.proto"),
    //   ),
    // )
    // assertThat(result).containsExactlyInAnyOrder(
    //   Location.get("shared-protos.jar", "java.wire"),
    //   Location.get("shared-protos.jar", "squareup/cash/java.wire"),
    //   Location.get("shared-protos.jar", "squareup/cash/money/java.wire"),
    //   Location.get("shared-protos.jar", "squareup/java.wire"),
    //   Location.get("src/main/proto", "java.wire"),
    //   Location.get("src/main/proto", "squareup/cash/cashtags/java.wire"),
    //   Location.get("src/main/proto", "squareup/cash/java.wire"),
    //   Location.get("src/main/proto", "squareup/cash/payments/java.wire"),
    //   Location.get("src/main/proto", "squareup/java.wire"),
    // )
  }

  // TASK-13 adaptation: deferred with locationsToCheck(); see above.
  @Disabled("TASK-16: the profile layer (locationsToCheck) is not ported yet")
  @Test public void pathsToAttempt() {
    // val newSchemaLoader = CommonSchemaLoader(fs)
    // val result = newSchemaLoader.locationsToCheck(
    //   "android",
    //   listOf(
    //     Location.get("/a/b", "c/d/e.proto"),
    //   ),
    // )
    // assertThat(result).containsExactlyInAnyOrder(
    //   Location.get("/a/b", "c/d/android.wire"),
    //   Location.get("/a/b", "c/android.wire"),
    //   Location.get("/a/b", "android.wire"),
    // )
  }

  // TASK-13 adaptation: deferred with locationsToCheck(); see above.
  @Disabled("TASK-16: the profile layer (locationsToCheck) is not ported yet")
  @Test public void pathsToAttemptMultipleRoots() {
    // val newSchemaLoader = CommonSchemaLoader(fs)
    // val result = newSchemaLoader.locationsToCheck(
    //   "android",
    //   listOf(
    //     Location.get("/a/b", "c/d/e.proto"),
    //     Location.get("/a/b", "c/f/g/h.proto"),
    //     Location.get("/i/j.zip", "k/l/m.proto"),
    //     Location.get("/i/j.zip", "k/l/m/n.proto"),
    //   ),
    // )
    // assertThat(result).containsExactlyInAnyOrder(
    //   Location.get("/a/b", "c/d/android.wire"),
    //   Location.get("/a/b", "c/android.wire"),
    //   Location.get("/a/b", "android.wire"),
    //   Location.get("/a/b", "c/f/g/android.wire"),
    //   Location.get("/a/b", "c/f/android.wire"),
    //   Location.get("/i/j.zip", "k/l/android.wire"),
    //   Location.get("/i/j.zip", "k/android.wire"),
    //   Location.get("/i/j.zip", "android.wire"),
    //   Location.get("/i/j.zip", "k/l/m/android.wire"),
    // )
  }

  @Test public void exhaustiveLoad() throws Exception {
    add("colors/src/main/proto/squareup/colors/red.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "import \"squareup/colors/orange.proto\";\n"
        + "message Red {\n"
        + "  optional Orange orange = 1;\n"
        + "}\n");
    add("colors/src/main/proto/squareup/colors/orange.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "import \"squareup/colors/yellow.proto\";\n"
        + "message Orange {\n"
        + "  optional Yellow yellow = 1;\n"
        + "}\n");
    add("colors/src/main/proto/squareup/colors/yellow.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "// import \"squareup/colors/green.proto\";\n"
        + "message Yellow {\n"
        + "  // optional Green green = 1;\n"
        + "}\n");

    Path sourceDir = tempDir.resolve("colors/src/main/proto");
    CommonSchemaLoader loader = new CommonSchemaLoader(okio.FileSystem.SYSTEM);
    loader.setLoadExhaustively(true);
    loader.initRoots(
        Collections.singletonList(
            Location.get(sourceDir.toString(), "squareup/colors/red.proto")),
        Collections.singletonList(Location.get(sourceDir.toString())));
    Schema schema = loader.loadSchema();

    MessageType redMessage = (MessageType) schema.getType("squareup.colors.Red");
    assertNotNull(redMessage);
    Field orangeField = redMessage.field("orange");
    assertNotNull(orangeField);
    assertEquals(ProtoType.get("squareup.colors.Orange"), orangeField.type());

    MessageType orangeMessage = (MessageType) schema.getType("squareup.colors.Orange");
    assertNotNull(orangeMessage);
    Field yellowField = orangeMessage.field("yellow");
    assertNotNull(yellowField);
    assertEquals(ProtoType.get("squareup.colors.Yellow"), yellowField.type());

    MessageType yellowMessage = (MessageType) schema.getType("squareup.colors.Yellow");
    assertNotNull(yellowMessage);
    assertTrue(yellowMessage.fields().isEmpty());
  }

  /** Writes {@code content} at {@code relativePath} under the temp directory, like fs.add. */
  private Path add(String relativePath, String content) throws IOException {
    return add(relativePath, content, StandardCharsets.UTF_8, new byte[0]);
  }

  /**
   * Writes {@code content} encoded with {@code charset} prefixed by the byte-order mark
   * {@code bom}, like fs.add(content, charset, bom).
   */
  private Path add(String relativePath, String content, Charset charset, byte[] bom)
      throws IOException {
    Path file = tempDir.resolve(relativePath);
    if (file.getParent() != null) {
      Files.createDirectories(file.getParent());
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    bytes.write(bom);
    bytes.write(content.getBytes(charset));
    Files.write(file, bytes.toByteArray());
    return file;
  }

  /** Creates a real ZIP at {@code relativePath} holding name/content pairs, like fs.addZip. */
  private Path addZip(String relativePath, String... nameContentPairs) throws IOException {
    return com.squareup.wire.testing.TestFiles.addZip(tempDir, relativePath, nameContentPairs);
  }

  /**
   * TASK-13 adaptation: upstream skips the symlink tests when FakeFileSystem disallows symlinks
   * (Windows); the port runs on the real file system, so probe for symlink support instead.
   */
  private void assumeSymlinksSupported() throws IOException {
    Path probe = tempDir.resolve("symlink-probe");
    try {
      Files.createSymbolicLink(probe, tempDir);
    } catch (IOException | UnsupportedOperationException | SecurityException e) {
      Assumptions.assumeTrue(false, "symlinks are not supported: " + e);
    } finally {
      Files.deleteIfExists(probe);
    }
  }

  private static List<Location> locations(List<ProtoFile> protoFiles) {
    List<Location> result = new ArrayList<>();
    for (ProtoFile protoFile : protoFiles) {
      result.add(protoFile.location());
    }
    return result;
  }

  private static List<String> paths(List<ProtoFile> protoFiles) {
    List<String> result = new ArrayList<>();
    for (ProtoFile protoFile : protoFiles) {
      result.add(protoFile.location().path);
    }
    return result;
  }

  /** assertk's containsExactlyInAnyOrder / containsOnly, on JUnit 5. */
  @SafeVarargs private static <T> void assertContainsExactlyInAnyOrder(
      List<T> actual, T... expected) {
    com.squareup.wire.testing.TestFiles.assertContainsExactlyInAnyOrder(
        java.util.Arrays.asList(expected), actual);
  }
}
