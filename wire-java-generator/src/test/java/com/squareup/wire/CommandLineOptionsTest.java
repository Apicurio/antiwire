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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.wire.schema.WireRun;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-16 adoption of upstream CommandLineOptionsTest:
 * {@code wire-compiler/src/test/java/com/squareup/wire/CommandLineOptionsTest.kt} at square/wire
 * tag 7.1.0. Flags and expected parsed values are verbatim; assertk maps onto JUnit 5, and
 * upstream's package-visible {@code WireCompiler} properties are the port's package-private
 * fields of the same names. kotlinEnumMode is disabled: it exercises the excluded Kotlin
 * generator's flags (DEC-6).
 */
public class CommandLineOptionsTest {
  @TempDir File tempDir;

  @Test
  public void unknownArgumentFails() throws Exception {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> parseArgs("--do-work"));
    assertEquals("Unknown argument '--do-work'.", e.getMessage());
  }

  @Test
  public void protoPaths() throws Exception {
    WireCompiler compiler = parseArgs("--java_out=.");
    assertTrue(compiler.getProtoPaths().isEmpty());

    compiler = parseArgs("--java_out=.", "--proto_path=foo/bar");
    assertEquals(1, compiler.getProtoPaths().size());
    assertTrue(compiler.getProtoPaths().contains("foo/bar"));

    compiler = parseArgs(
        "--java_out=.",
        "--proto_path=foo/bar",
        "--proto_path=one/two",
        "--proto_path=three/four");
    assertEquals(Arrays.asList("foo/bar", "one/two", "three/four"), compiler.getProtoPaths());
  }

  @Test
  public void javaOut() throws Exception {
    assertThrows(WireException.class, () -> WireCompiler.forArgs());

    WireCompiler compiler = parseArgs("--java_out=baz/qux");
    assertEquals("baz/qux", compiler.getJavaOut());
  }

  @Test
  public void sourceFileNames() throws Exception {
    WireCompiler compiler = parseArgs("--java_out=.");
    assertTrue(compiler.getSourceFileNames().isEmpty());

    compiler = parseArgs("--java_out=.", "baz", "qux");
    assertEquals(Arrays.asList("baz", "qux"), compiler.getSourceFileNames());
  }

  @Test
  public void sourceFileNamesFromInclude() throws Exception {
    File tmpFile = File.createTempFile("proto", ".include", tempDir);
    try {
      PrintWriter out = new PrintWriter(new FileOutputStream(tmpFile));
      out.println("foo");
      out.println("bar");
      out.close();

      WireCompiler compiler = parseArgs("--java_out=.", "--files=" + tmpFile.getAbsolutePath());
      assertEquals(Arrays.asList("foo", "bar"), compiler.getSourceFileNames());

      // Test both --files and bare filenames together
      compiler = parseArgs("--java_out=.", "--files=" + tmpFile.getAbsolutePath(), "baz");
      assertEquals(Arrays.asList("foo", "bar", "baz"), compiler.getSourceFileNames());
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void roots() throws Exception {
    WireCompiler compiler = parseArgs("--java_out=.");
    assertEquals(Arrays.asList("*"), compiler.getTreeShakingRoots());
    assertTrue(compiler.getTreeShakingRubbish().isEmpty());

    compiler = parseArgs("--java_out=.", "--includes=com.example.Foo");
    assertEquals(Arrays.asList("com.example.Foo"), compiler.getTreeShakingRoots());

    compiler = parseArgs("--java_out=.", "--includes=com.example.Foo,com.example.Bar");
    assertEquals(Arrays.asList("com.example.Foo", "com.example.Bar"), compiler.getTreeShakingRoots());

    compiler = parseArgs("--java_out=.", "--ignore_unused_roots_and_prunes");
    assertFalse(compiler.getRejectUnusedRootsOrPrunes());
  }

  @Test
  public void customOptions() throws Exception {
    WireCompiler compiler = parseArgs(
        "--custom_out=src/custom/out",
        "--schema_handler_factory_class=com.squareup.wire.MyCustomHandlerFactory",
        "--custom_option=key1,one",
        "--custom_option=key2,value1,value2",
        "--custom_option=key3,three",
        "--custom_option=key1,override");
    assertEquals("src/custom/out", compiler.getCustomOut());
    assertEquals("com.squareup.wire.MyCustomHandlerFactory", compiler.getSchemaHandlerFactoryClass());
    Map<String, String> expected = new LinkedHashMap<>();
    expected.put("key1", "override");
    expected.put("key2", "value1,value2");
    expected.put("key3", "three");
    assertEquals(expected, compiler.getCustomOptions());
  }

  @Test
  public void manifestModules() throws Exception {
    File tmpFile = File.createTempFile("proto", ".yaml", tempDir);
    java.nio.file.Files.write(tmpFile.toPath(), (""
        + "a: {}\n"
        + "b:\n"
        + "  dependencies:\n"
        + "   - a\n"
        + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));

    WireCompiler compiler =
        parseArgs("--java_out=.", "--experimental-module-manifest=" + tmpFile.getAbsolutePath());

    Map<String, WireRun.Module> expected = new LinkedHashMap<>();
    expected.put("a", new WireRun.Module());
    expected.put("b", new WireRun.Module(java.util.Collections.singleton("a")));
    assertEquals(expected.keySet(), compiler.getModules().keySet());
    assertTrue(compiler.getModules().get("a").getDependencies().isEmpty());
    assertNullModulePruningRules(compiler.getModules().get("a"));
    assertEquals(java.util.Collections.singleton("a"), compiler.getModules().get("b").getDependencies());
  }

  private static void assertNullModulePruningRules(WireRun.Module module) {
    assertTrue(module.getPruningRules() == null, "expected default pruning rules to be null");
  }

  @Test
  @Disabled("DEC-6: --kotlin_out and --kotlin_enum_mode belong to the excluded Kotlin generator")
  public void kotlinEnumMode() {
    // Upstream body preserved verbatim; the port's WireCompiler rejects Kotlin flags outright.
    //
    // var compiler = parseArgs("--kotlin_out=.")
    // assertThat(compiler.kotlinEnumMode).isEqualTo(EnumMode.ENUM_CLASS) // Default is ENUM_CLASS
    //
    // compiler = parseArgs("--kotlin_out=.", "--kotlin_enum_mode=enum_class")
    // assertThat(compiler.kotlinEnumMode).isEqualTo(EnumMode.ENUM_CLASS)
    //
    // compiler = parseArgs("--kotlin_out=.", "--kotlin_enum_mode=sealed_class")
    // assertThat(compiler.kotlinEnumMode).isEqualTo(EnumMode.SEALED_CLASS)
    //
    // // Args should be case-insensitive
    // compiler = parseArgs("--kotlin_out=.", "--kotlin_enum_mode=ENUM_CLASS")
    // assertThat(compiler.kotlinEnumMode).isEqualTo(EnumMode.ENUM_CLASS)
    //
    // compiler = parseArgs("--kotlin_out=.", "--kotlin_enum_mode=SEALED_CLASS")
    // assertThat(compiler.kotlinEnumMode).isEqualTo(EnumMode.SEALED_CLASS)
    //
    // // Invalid values should throw an exception pointing to the EnumMode class and the invalid value
    // assertFailure {
    //   parseArgs("--kotlin_out=.", "--kotlin_enum_mode=invalid")
    // }.isInstanceOf<IllegalArgumentException>()
    //   .hasMessage("No enum constant com.squareup.wire.kotlin.EnumMode.INVALID")
  }

  private WireCompiler parseArgs(String... args) throws WireException {
    return WireCompiler.forArgs(args);
  }
}
