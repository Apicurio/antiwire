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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Upstream wire-schema jvmTest RootTest.kt translated to Java 11 JUnit 5 (TASK-13).
 *
 * <p>Recorded adaptations, applied uniformly below: upstream builds its file system in okio's
 * in-memory FakeFileSystem rooted at the working directory "/"; this test builds the same tree
 * under a JUnit {@link TempDir} on {@code okio.FileSystem.SYSTEM}, so asserted locations embed
 * the absolute temp-directory prefix where upstream's are relative (like
 * "sample/src/main/proto" and "lib/dinosaurs.zip"). Upstream calls the {@code Location.roots(fs)}
 * extension whose {@code baseToRoots} argument has a Kotlin default; the port's
 * {@link Root#roots(Location, okio.FileSystem, java.util.Map)} takes it explicitly, so each call
 * passes a fresh empty map. Roots backed by a ZIP own the file system that {@link Root#roots}
 * opened for it, and are closed so the temporary copies the vendored okio materializes are
 * released; upstream's FakeFileSystem needs no such close.
 */
public class RootTest {
  @TempDir Path tempDir;

  @Test public void standaloneFile() throws Exception {
    add("sample/src/main/proto/squareup/dinosaurs/dinosaur.proto", "/* dinosaur.proto */");

    Path base = tempDir.resolve("sample/src/main/proto");
    Location location = Location.get(base.toString(), "squareup/dinosaurs/dinosaur.proto");
    List<Root> roots =
        Root.roots(location, okio.FileSystem.SYSTEM, new LinkedHashMap<>());
    assertEquals(1, roots.size());

    // Standalone files resolve because we have a base directory.
    assertEquals(roots.get(0), roots.get(0).resolve("squareup/dinosaurs/dinosaur.proto"));
    assertNull(roots.get(0).resolve("sample/src/main/proto/squareup/dinosaurs/dinosaur.proto"));

    // But we can enumerate their contents.
    assertContainsOnly(locations(roots.get(0).allProtoFiles()), location);
  }

  @Test public void directory() throws Exception {
    add("sample/src/main/proto/squareup/dinosaurs/dinosaur.proto", "/* dinosaur.proto */");
    add("sample/src/main/proto/squareup/dinosaurs/geology.proto", "/* geology.proto */");

    Path sourceDir = tempDir.resolve("sample/src/main/proto");
    List<Root> roots =
        Root.roots(Location.get(sourceDir.toString()), okio.FileSystem.SYSTEM,
            new LinkedHashMap<>());
    assertEquals(1, roots.size());

    Root.ProtoFilePath resolved =
        roots.get(0).resolve("squareup/dinosaurs/dinosaur.proto");
    assertNotNull(resolved);
    assertEquals(Location.get(sourceDir.toString(), "squareup/dinosaurs/dinosaur.proto"),
        resolved.location);

    assertNull(roots.get(0).resolve("squareup/dinosaurs/unknown.proto"));

    assertContainsOnly(locations(roots.get(0).allProtoFiles()),
        Location.get(sourceDir.toString(), "squareup/dinosaurs/dinosaur.proto"),
        Location.get(sourceDir.toString(), "squareup/dinosaurs/geology.proto"));
  }

  @Test public void zip() throws Exception {
    Path zip = addZip("lib/dinosaurs.zip",
        "squareup/dinosaurs/dinosaur.proto", "/* dinosaur.proto */",
        "squareup/dinosaurs/geology.proto", "/* geology.proto */");

    Location sourceZip = Location.get(zip.toString());
    List<Root> roots =
        Root.roots(sourceZip, okio.FileSystem.SYSTEM, new LinkedHashMap<>());
    assertEquals(1, roots.size());
    try {
      Root.ProtoFilePath resolved =
          roots.get(0).resolve("squareup/dinosaurs/dinosaur.proto");
      assertNotNull(resolved);
      assertEquals(Location.get(zip.toString(), "squareup/dinosaurs/dinosaur.proto"),
          resolved.location);

      assertNull(roots.get(0).resolve("squareup/dinosaurs/unknown.proto"));

      assertContainsOnly(locations(roots.get(0).allProtoFiles()),
          Location.get(zip.toString(), "squareup/dinosaurs/dinosaur.proto"),
          Location.get(zip.toString(), "squareup/dinosaurs/geology.proto"));
    } finally {
      // TASK-13 adaptation: close the zip-backed root, which owns its file system.
      for (Root root : roots) {
        root.close();
      }
    }
  }

  @Test public void zipProtoFilesOnly() throws Exception {
    Path zip = addZip("lib/dinosaurs.zip",
        "squareup/dinosaurs/raptor.proto", "/* raptor.proto */",
        "squareup/dinosaurs/raptor.nba", "/* raptor.nba */");

    Location sourceZip = Location.get(zip.toString());
    List<Root> roots =
        Root.roots(sourceZip, okio.FileSystem.SYSTEM, new LinkedHashMap<>());
    assertEquals(1, roots.size());
    try {
      assertContainsOnly(locations(roots.get(0).allProtoFiles()),
          Location.get(zip.toString(), "squareup/dinosaurs/raptor.proto"));
    } finally {
      // TASK-13 adaptation: close the zip-backed root, which owns its file system.
      for (Root root : roots) {
        root.close();
      }
    }
  }

  @Test public void directoryProtoFilesOnly() throws Exception {
    add("sample/src/main/proto/squareup/dinosaurs/raptor.proto", "/* raptor.proto */");
    add("sample/src/main/proto/squareup/dinosaurs/raptor.nba", "/* raptor.nba */");

    Path sourceDir = tempDir.resolve("sample/src/main/proto");
    List<Root> roots =
        Root.roots(Location.get(sourceDir.toString()), okio.FileSystem.SYSTEM,
            new LinkedHashMap<>());
    assertEquals(1, roots.size());
    assertContainsOnly(locations(roots.get(0).allProtoFiles()),
        Location.get(sourceDir.toString(), "squareup/dinosaurs/raptor.proto"));
  }

  @Test public void standaloneFileMustBeProtoOrZip() throws Exception {
    Path file = add("sample/src/main/proto/squareup/dinosaurs/raptor.nba", "/* raptor.nba */");

    Location onlyFile = Location.get(file.toString());
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> Root.roots(onlyFile, okio.FileSystem.SYSTEM, new LinkedHashMap<>()));
    assertEquals("expected a directory, archive (.zip / .jar / etc.), or .proto: " + onlyFile,
        e.getMessage());
  }

  /** Writes {@code content} at {@code relativePath} under the temp directory, like fs.add. */
  private Path add(String relativePath, String content) throws IOException {
    Path file = tempDir.resolve(relativePath);
    if (file.getParent() != null) {
      Files.createDirectories(file.getParent());
    }
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    return file;
  }

  /**
   * Creates a real ZIP at {@code relativePath} holding {@code name}/{@code content} pairs, like
   * fs.addZip.
   */
  private Path addZip(String relativePath, String... nameContentPairs) throws IOException {
    if (nameContentPairs.length % 2 != 0) {
      throw new IllegalArgumentException("expected name/content pairs");
    }
    Path zip = tempDir.resolve(relativePath);
    if (zip.getParent() != null) {
      Files.createDirectories(zip.getParent());
    }
    try (OutputStream out = Files.newOutputStream(zip);
        ZipOutputStream zipOut = new ZipOutputStream(out)) {
      for (int i = 0; i < nameContentPairs.length; i += 2) {
        zipOut.putNextEntry(new ZipEntry(nameContentPairs[i]));
        zipOut.write(nameContentPairs[i + 1].getBytes(StandardCharsets.UTF_8));
        zipOut.closeEntry();
      }
    }
    return zip;
  }

  private static List<Location> locations(List<Root.ProtoFilePath> protoFilePaths) {
    List<Location> result = new ArrayList<>();
    for (Root.ProtoFilePath protoFilePath : protoFilePaths) {
      result.add(protoFilePath.location);
    }
    return result;
  }

  /** assertk's containsExactlyInAnyOrder / containsOnly, on JUnit 5. */
  @SafeVarargs private static <T> void assertContainsOnly(List<T> actual, T... expected) {
    List<T> remaining = new ArrayList<>(actual);
    for (T element : expected) {
      assertTrue(remaining.remove(element), "expected " + element + " in " + actual);
    }
    assertTrue(remaining.isEmpty(), "unexpected elements " + remaining);
  }
}
