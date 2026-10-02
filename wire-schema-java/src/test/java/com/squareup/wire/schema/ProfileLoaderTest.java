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

import static com.squareup.wire.testing.TestFiles.add;
import static com.squareup.wire.testing.TestFiles.addZip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-16 adoption of upstream ProfileLoaderTest:
 * {@code wire-schema/src/jvmTest/kotlin/com/squareup/wire/schema/ProfileLoaderTest.kt} at
 * square/wire tag 7.1.0. Inputs, profiles, and expected error messages are verbatim. Recorded
 * adaptations: upstream's in-memory FakeFileSystem becomes the same tree under a JUnit
 * {@link TempDir} on the real file system (the TASK-13 blanket adaptation), so the exact
 * {@code missingImport} message interpolates the absolute temp root where upstream writes
 * {@code source-path}; and the port's poet-free {@link Profile} returns target names as strings,
 * so {@code ClassName.OBJECT} and {@code ClassName.get(String::class.java)} assert as
 * {@code java.lang.Object} and {@code java.lang.String}.
 */
public class ProfileLoaderTest {
  @TempDir Path tempDir;

  @Test
  public void test() throws IOException {
    add(tempDir, "source-path/a/b/message1.proto", ""
        + "package a.b;\n"
        + "message Message1 {\n"
        + "}\n");
    add(tempDir, "source-path/a/b/c/message2.proto", ""
        + "package a.b.c;\n"
        + "message Message2 {\n"
        + "}\n");
    add(tempDir, "source-path/android.wire", ""
        + "syntax = \"wire2\";\n"
        + "import \"a/b/message1.proto\";\n"
        + "type a.b.Message1 {\n"
        + "  target java.lang.Object using com.example.Message1#OBJECT_ADAPTER;\n"
        + "}\n");
    add(tempDir, "source-path/a/b/c/android.wire", ""
        + "syntax = \"wire2\";\n"
        + "import \"a/b/c/message2.proto\";\n"
        + "package a.b.c;\n"
        + "type a.b.c.Message2 {\n"
        + "  target java.lang.String using com.example.Message2#STRING_ADAPTER;\n"
        + "}\n");

    Profile profile = loadAndLinkProfile("android");

    ProtoType message1 = ProtoType.get("a.b.Message1");
    assertEquals("java.lang.Object", profile.javaTarget(message1));
    assertEquals(AdapterConstant.get("com.example.Message1#OBJECT_ADAPTER"),
        profile.getAdapter(message1));

    ProtoType message2 = ProtoType.get("a.b.c.Message2");
    assertEquals("java.lang.String", profile.javaTarget(message2));
    assertEquals(AdapterConstant.get("com.example.Message2#STRING_ADAPTER"),
        profile.getAdapter(message2));
  }

  @Test
  public void profileInZip() throws IOException {
    Path protosZip = addZip(tempDir, "source/protos.zip",
        "a/b/message.proto", ""
            + "package a.b;\n"
            + "message Message {}\n",
        "a/b/android.wire", ""
            + "syntax = \"wire2\";\n"
            + "package a.b;\n"
            + "import \"a/b/message.proto\";\n"
            + "type a.b.Message {\n"
            + "  target java.lang.Object using com.example.Message#ADAPTER;\n"
            + "}\n");

    Profile profile = loadAndLinkProfile(
        "android",
        Collections.singletonList(Location.get(protosZip.toString())));

    ProtoType message = ProtoType.get("a.b.Message");
    assertEquals("java.lang.Object", profile.javaTarget(message));
    assertEquals(AdapterConstant.get("com.example.Message#ADAPTER"),
        profile.getAdapter(message));
  }

  @Test
  public void unknownType() throws IOException {
    add(tempDir, "source-path/a/b/message.proto", ""
        + "package a.b;\n"
        + "message Message {\n"
        + "}\n");
    add(tempDir, "source-path/a/b/android.wire", ""
        + "syntax = \"wire2\";\n"
        + "type a.b.Message2 {\n"
        + "  target java.lang.Object using com.example.Message#OBJECT_ADAPTER;\n"
        + "}\n");
    Profile profile = loadAndLinkProfile("android");
    ProtoType message = ProtoType.get("a.b.Message");
    assertNull(profile.javaTarget(message));
  }

  @Test
  public void missingImport() throws IOException {
    add(tempDir, "source-path/a/b/message.proto", ""
        + "package a.b;\n"
        + "message Message {\n"
        + "}\n");
    add(tempDir, "source-path/a/b/android.wire", ""
        + "syntax = \"wire2\";\n"
        + "type a.b.Message {\n"
        + "  target java.lang.Object using com.example.Message#OBJECT_ADAPTER;\n"
        + "}\n");
    String sourcePath = tempDir.resolve("source-path").toString();
    SchemaException e = assertThrows(SchemaException.class, () -> loadAndLinkProfile("android"));
    assertEquals("a/b/android.wire needs to import a/b/message.proto"
            + " (" + sourcePath + "/a/b/android.wire:2:1)",
        e.getMessage());
  }

  private Profile loadAndLinkProfile(String name) throws IOException {
    return loadAndLinkProfile(
        name,
        Collections.singletonList(Location.get(tempDir.resolve("source-path").toString())));
  }

  private Profile loadAndLinkProfile(String name, List<Location> sourcePath) throws IOException {
    SchemaLoader loader = new SchemaLoader(okio.FileSystem.SYSTEM);
    loader.initRoots(sourcePath, Collections.emptyList());
    Schema schema = loader.loadSchema();
    return loader.loadProfile(name, schema);
  }
}
