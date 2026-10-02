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

import static com.squareup.wire.testing.TestFiles.readUtf8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.wire.schema.SchemaException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-16 adoption of upstream WireCompilerErrorTest:
 * {@code wire-compiler/src/test/java/com/squareup/wire/WireCompilerErrorTest.kt} at square/wire
 * tag 7.1.0. Inputs and expected messages are verbatim. Recorded adaptation: upstream compiles
 * from an in-memory FakeFileSystem rooted at "/" so its messages cite "/source/test_1.proto";
 * this port writes the same tree under a JUnit {@link TempDir} and interpolates the absolute
 * source root into the expected messages (the TASK-13 blanket adaptation).
 */
public class WireCompilerErrorTest {
  @TempDir Path tempDir;

  private int nextFileIndex = 1;

  /**
   * Compile a .proto contained in a String and returns the contents of each output file, indexed
   * by class name.
   */
  private void compile(String... files) throws IOException, WireException {
    Path source = tempDir.resolve("source");
    Path target = tempDir.resolve("target");
    Files.createDirectories(source);
    Files.createDirectories(target);
    java.util.List<String> fileNames = new java.util.ArrayList<>();
    for (String fileContent : files) {
      String fileName = "test_" + nextFileIndex++ + ".proto";
      Files.write(source.resolve(fileName), fileContent.getBytes(StandardCharsets.UTF_8));
      fileNames.add(fileName);
    }

    java.util.List<String> args = new java.util.ArrayList<>();
    args.add("--proto_path=" + source);
    args.add("--java_out=" + target);
    args.addAll(fileNames);
    WireCompiler compiler = WireCompiler.forArgs(
        okio.FileSystem.SYSTEM,
        new StringWireLogger(),
        args.toArray(new String[0]));
    compiler.compile();
  }

  @Test
  public void testCorrect() throws Exception {
    compile(""
        + "package com.squareup.protos.test;\n"
        + "message Simple {\n"
        + "  optional int32 f = 1;\n"
        + "}\n");
    String generatedSource = readFile("target/com/squareup/protos/test/Simple.java");
    assertTrue(generatedSource.contains(
        "public final class Simple extends Message<Simple, Simple.Builder> {"));
  }

  @Test
  public void testZeroTag() {
    String source = tempDir.resolve("source").toString();
    SchemaException e = assertThrows(SchemaException.class, () -> compile(""
        + "package com.squareup.protos.test;\n"
        + "message Simple {\n"
        + "  optional int32 f = 0;\n"
        + "}\n"));
    assertEqualsMessage(""
        + "tag is out of range: 0\n"
        + "  for field f (" + source + "/test_1.proto:3:3)\n"
        + "  in message com.squareup.protos.test.Simple (" + source + "/test_1.proto:2:1)",
        e.getMessage());
  }

  @Test
  public void testDuplicateTag() {
    String source = tempDir.resolve("source").toString();
    SchemaException e = assertThrows(SchemaException.class, () -> compile(""
        + "package com.squareup.protos.test;\n"
        + "message Simple {\n"
        + "  optional int32 f = 1;\n"
        + "  optional int32 g = 1;\n"
        + "}\n"));
    assertEqualsMessage(""
        + "multiple fields share tag 1:\n"
        + "  1. f (" + source + "/test_1.proto:3:3)\n"
        + "  2. g (" + source + "/test_1.proto:4:3)\n"
        + "  for message com.squareup.protos.test.Simple (" + source + "/test_1.proto:2:1)",
        e.getMessage());
  }

  @Test
  public void testEnumNamespaceType() {
    String source = tempDir.resolve("source").toString();
    SchemaException e = assertThrows(SchemaException.class, () -> compile(""
        + "package com.squareup.protos.test;\n"
        + "message Foo {\n"
        + "  enum Bar {\n"
        + "    QUIX = 0;\n"
        + "    FOO = 1;\n"
        + "  }\n"
        + "\n"
        + "  enum Bar2 {\n"
        + "    BAZ = 0;\n"
        + "    QUIX = 1;\n"
        + "  }\n"
        + "}\n"));
    assertEqualsMessage(""
        + "multiple enums share constant QUIX:\n"
        + "  1. com.squareup.protos.test.Foo.Bar.QUIX (" + source + "/test_1.proto:4:5)\n"
        + "  2. com.squareup.protos.test.Foo.Bar2.QUIX (" + source + "/test_1.proto:10:5)\n"
        + "  for message com.squareup.protos.test.Foo (" + source + "/test_1.proto:2:1)",
        e.getMessage());
  }

  @Test
  public void testEnumNamespaceTypeSplitAcrossTwoFiles() {
    String source = tempDir.resolve("source").toString();
    SchemaException e = assertThrows(SchemaException.class, () -> compile(""
        + "package com.squareup.protos.test;\n"
        + "\n"
        + "enum Bar {\n"
        + "  QUIX = 0;\n"
        + "  FOO = 1;\n"
        + "}\n",
        ""
        + "package com.squareup.protos.test;\n"
        + "\n"
        + "enum Bar2 {\n"
        + "  BAZ = 0;\n"
        + "  QUIX = 1;\n"
        + "}\n"));
    assertEqualsMessage(""
        + "multiple enums share constant QUIX:\n"
        + "  1. com.squareup.protos.test.Bar.QUIX (" + source + "/test_1.proto:4:3)\n"
        + "  2. com.squareup.protos.test.Bar2.QUIX (" + source + "/test_2.proto:5:3)\n"
        + "  for file " + source + "/test_1.proto",
        e.getMessage());
  }

  @Test
  public void testEnumNamespaceFile() {
    String source = tempDir.resolve("source").toString();
    SchemaException e = assertThrows(SchemaException.class, () -> compile(""
        + "package com.squareup.protos.test;\n"
        + "\n"
        + "enum Bar {\n"
        + "  QUIX = 0;\n"
        + "  FOO = 1;\n"
        + "}\n"
        + "\n"
        + "enum Bar2 {\n"
        + "  BAZ = 0;\n"
        + "  QUIX = 1;\n"
        + "}\n"));
    assertEqualsMessage(""
        + "multiple enums share constant QUIX:\n"
        + "  1. com.squareup.protos.test.Bar.QUIX (" + source + "/test_1.proto:4:3)\n"
        + "  2. com.squareup.protos.test.Bar2.QUIX (" + source + "/test_1.proto:10:3)\n"
        + "  for file " + source + "/test_1.proto",
        e.getMessage());
  }

  @Test
  public void testNoPackageNameIsLegal() throws Exception {
    compile("message Simple { optional int32 f = 1; }");
    // Output should not have a 'package' declaration.
    assertFalse(readFile("target/Simple.java").contains("package"));
  }

  private static void assertEqualsMessage(String expected, String actual) {
    assertTrue(expected.equals(actual), "expected:<" + expected + "> but was:<" + actual + ">");
  }

  private String readFile(String path) throws IOException {
    return readUtf8(tempDir.resolve(path));
  }
}
