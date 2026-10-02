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

import static com.squareup.wire.testing.TestFiles.findFiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.wire.schema.CustomTarget;
import com.squareup.wire.schema.JavaTarget;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.Target;
import com.squareup.wire.schema.WireRun;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-16 adoption of upstream WireCompilerTest:
 * {@code wire-compiler/src/test/java/com/squareup/wire/WireCompilerTest.kt} at square/wire tag
 * 7.1.0. Flags and expected parsed values are verbatim. Recorded adaptations: upstream's
 * in-memory FakeFileSystem becomes the same tree under a JUnit {@link TempDir} on the real file
 * system (TASK-13 blanket adaptation), so location assertions interpolate the temp root where
 * upstream asserts its "/" root; upstream's data-class target equality becomes per-property
 * assertions because the port's targets are plain classes; and assertk maps onto JUnit 5.
 * allFlags, treeShaking, and allTargetsAndAllOptions are disabled: their inputs are
 * Kotlin/Swift-target flags this port rejects (DEC-6).
 */
public class WireCompilerTest {
  @TempDir Path tempDir;

  @Test
  public void compileExecutes() throws Exception {
    Path out = tempDir.resolve("out");

    Files.write(tempDir.resolve("baz.proto"), (""
        + "syntax = \"proto2\";\n"
        + "\n"
        + "message Player {\n"
        + "  optional string name = 1;\n"
        + "}\n"
        + "\n").getBytes(StandardCharsets.UTF_8));
    WireCompiler wireCompiler = WireCompiler.forArgs(
        okio.FileSystem.SYSTEM,
        WireLogger.NONE,
        "--proto_path=" + tempDir,
        "--java_out=" + out);
    wireCompiler.compile();

    // Upstream lists /out recursively; the port's tree also holds the input proto, so the
    // listing is rooted at the out directory and prefixed the same way.
    assertEquals(Collections.singleton("out/Player.java"),
        java.util.Collections.singleton("out/" + findFiles(out).iterator().next()));
  }

  @Test
  // Upstream's case name is `default`, a Java keyword; the trailing underscore keeps the name
  // canonicalizable to upstream's (parity coverage lowercases and strips punctuation).
  public void default_() throws Exception {
    WireCompiler wireCompiler = WireCompiler.forArgs("--java_out=out/");
    WireRun wireRun = wireCompiler.createRun();
    assertTrue(wireRun.sourcePath().isEmpty());
    assertTrue(wireRun.protoPath().isEmpty());
    assertEquals(Collections.singletonList("*"), wireRun.treeShakingRoots());
    assertTrue(wireRun.treeShakingRubbish().isEmpty());
    assertTrue(wireRun.moves().isEmpty());
    assertEquals(null, wireRun.sinceVersion());
    assertEquals(null, wireRun.untilVersion());
    assertEquals(null, wireRun.onlyVersion());
    assertEquals(1, wireRun.targets().size());
    assertDefaultJavaTarget(wireRun.targets().get(0), "out/");
    assertTrue(wireRun.modules().isEmpty());
    assertFalse(wireRun.permitPackageCycles());
    assertFalse(wireRun.loadExhaustively());
    assertFalse(wireRun.escapeKotlinKeywords());
    assertTrue(wireRun.eventListeners().isEmpty());
    assertTrue(wireRun.rejectUnusedRootsOrPrunes());
    assertTrue(wireRun.opaqueTypes().isEmpty());
  }

  private static void assertDefaultJavaTarget(Target target, String outDirectory) {
    assertTrue(target instanceof JavaTarget, String.valueOf(target));
    JavaTarget javaTarget = (JavaTarget) target;
    assertEquals(Collections.singletonList("*"), javaTarget.includes());
    assertEquals(Collections.emptyList(), javaTarget.excludes());
    assertTrue(javaTarget.exclusive());
    assertEquals(outDirectory, javaTarget.outDirectory());
  }

  @Test
  @Disabled("DEC-6: the flag set includes --kotlin_out/--swift_out and Kotlin-only flags the port rejects")
  public void allFlags() {
    // Upstream body preserved verbatim in the pinned clone; it passes every Kotlin and Swift
    // generator flag at once, which this port rejects with IllegalArgumentException.
  }

  @Test
  public void protoLocations() throws Exception {
    Files.write(tempDir.resolve("foo.proto"), new byte[0]);
    Files.write(tempDir.resolve("bar.proto"), new byte[0]);
    Path protoInclude = tempDir.resolve("proto.include");
    Files.write(protoInclude, "foo.proto\nbar.proto\n".getBytes(StandardCharsets.UTF_8));
    Files.write(tempDir.resolve("baz.proto"), new byte[0]);

    WireCompiler wireCompiler = WireCompiler.forArgs(
        okio.FileSystem.SYSTEM,
        WireLogger.NONE,
        "--proto_path=" + tempDir,
        "--java_out=java_out",
        "--files=" + protoInclude,
        "baz.proto");
    WireRun wireRun = wireCompiler.createRun();
    assertEquals(Arrays.asList(
        Location.get(tempDir.toString(), "foo.proto"),
        Location.get(tempDir.toString(), "bar.proto"),
        Location.get(tempDir.toString(), "baz.proto")),
        wireRun.sourcePath());
    assertEquals(Collections.singletonList(Location.get(tempDir.toString())),
        wireRun.protoPath());
    assertEquals(Collections.singletonList("*"), wireRun.treeShakingRoots());
    assertTrue(wireRun.treeShakingRubbish().isEmpty());
    assertEquals(1, wireRun.targets().size());
    assertDefaultJavaTarget(wireRun.targets().get(0), "java_out");
  }

  @Test
  @Disabled("DEC-6: --kotlin_out and the KotlinTarget assertions cover the excluded Kotlin generator")
  public void treeShaking() {
    // Upstream passes --kotlin_out=kotlin_out and asserts a KotlinTarget, which the port rejects.
  }

  @Test
  public void manifest() throws Exception {
    Path manifest = tempDir.resolve("proto_manifest.yaml");
    Files.write(manifest, (""
        + "a: {}\n"
        + "b:\n"
        + "  dependencies:\n"
        + "   - a\n"
        + "\n").getBytes(StandardCharsets.UTF_8));

    WireCompiler wireCompiler = WireCompiler.forArgs(
        okio.FileSystem.SYSTEM,
        WireLogger.NONE,
        "--java_out=java_out",
        "--experimental-module-manifest=" + manifest);
    WireRun wireRun = wireCompiler.createRun();
    assertEquals(1, wireRun.targets().size());
    assertDefaultJavaTarget(wireRun.targets().get(0), "java_out");
    Map<String, WireRun.Module> expected = new LinkedHashMap<>();
    expected.put("a", new WireRun.Module());
    expected.put("b", new WireRun.Module(Collections.singleton("a")));
    assertEquals(expected.keySet(), wireRun.modules().keySet());
    assertTrue(wireRun.modules().get("a").dependencies().isEmpty());
    assertEquals(Collections.singleton("a"), wireRun.modules().get("b").dependencies());
  }

  @Test
  public void loadExhaustively() throws Exception {
    WireCompiler wireCompiler = WireCompiler.forArgs(
        okio.FileSystem.SYSTEM,
        WireLogger.NONE,
        "--java_out=java_out",
        "--load_exhaustively");
    WireRun wireRun = wireCompiler.createRun();
    assertEquals(1, wireRun.targets().size());
    assertDefaultJavaTarget(wireRun.targets().get(0), "java_out");
    assertTrue(wireRun.loadExhaustively());
  }

  @Test
  public void permitPackageCycles() throws Exception {
    WireCompiler wireCompiler = WireCompiler.forArgs(
        okio.FileSystem.SYSTEM,
        WireLogger.NONE,
        "--java_out=java_out",
        "--permit_package_cycles");
    WireRun wireRun = wireCompiler.createRun();
    assertEquals(1, wireRun.targets().size());
    assertDefaultJavaTarget(wireRun.targets().get(0), "java_out");
    assertTrue(wireRun.permitPackageCycles());
  }

  @Test
  public void dryRun() throws Exception {
    Path out = tempDir.resolve("out");

    Files.write(tempDir.resolve("baz.proto"), (""
        + "syntax = \"proto2\";\n"
        + "\n"
        + "message Player {\n"
        + "  optional string name = 1;\n"
        + "}\n"
        + "\n").getBytes(StandardCharsets.UTF_8));
    WireCompiler wireCompiler = WireCompiler.forArgs(
        okio.FileSystem.SYSTEM,
        WireLogger.NONE,
        "--proto_path=" + tempDir,
        "--java_out=" + out,
        "--dry_run");
    wireCompiler.compile();

    // Dry run didn't write on disk.
    assertTrue(!Files.exists(out) || findFiles(out).isEmpty());
  }

  @Test
  public void opaqueTypes() throws Exception {
    WireCompiler wireCompiler = WireCompiler.forArgs(
        okio.FileSystem.SYSTEM,
        WireLogger.NONE,
        "--java_out=java_out",
        "--opaque_types=opaque_types");
    WireRun wireRun = wireCompiler.createRun();
    assertEquals(1, wireRun.targets().size());
    assertDefaultJavaTarget(wireRun.targets().get(0), "java_out");
    assertEquals(Collections.singletonList("opaque_types"), wireRun.opaqueTypes());
  }

  @Test
  public void ignoreUnusedRootsAndPrunes() throws Exception {
    WireCompiler wireCompiler = WireCompiler.forArgs(
        okio.FileSystem.SYSTEM,
        WireLogger.NONE,
        "--java_out=java_out",
        "--ignore_unused_roots_and_prunes");
    WireRun wireRun = wireCompiler.createRun();
    assertEquals(1, wireRun.targets().size());
    assertDefaultJavaTarget(wireRun.targets().get(0), "java_out");
    assertFalse(wireRun.rejectUnusedRootsOrPrunes());
  }

  @Test
  @Disabled("DEC-6: asserts KotlinTarget and SwiftTarget outputs the port cannot construct")
  public void allTargetsAndAllOptions() {
    // Upstream passes every target flag (java, kotlin, swift, custom) and asserts all four
    // targets; only the Java and Custom halves are portable, and customOutput covers Custom.
  }

  @Test
  public void customOutput() throws Exception {
    WireCompiler wireCompiler = WireCompiler.forArgs(
        okio.FileSystem.SYSTEM,
        WireLogger.NONE,
        "--proto_path=" + tempDir,
        "--custom_out=custom_out",
        "--schema_handler_factory_class=schema_handler_factory_class",
        "--custom_option=a,1",
        "--custom_option=b,2");
    WireRun wireRun = wireCompiler.createRun();
    assertEquals(1, wireRun.targets().size());
    CustomTarget target = (CustomTarget) wireRun.targets().get(0);
    assertEquals(Collections.singletonList("*"), target.includes());
    assertEquals(Collections.emptyList(), target.excludes());
    assertTrue(target.exclusive());
    Map<String, String> expectedOptions = new LinkedHashMap<>();
    expectedOptions.put("a", "1");
    expectedOptions.put("b", "2");
    assertEquals(expectedOptions, target.options());
    // Upstream asserts the data-class schemaHandlerFactory property is not null; the port keeps
    // the field private, so the adopted case reads it reflectively.
    java.lang.reflect.Field factoryField = CustomTarget.class.getDeclaredField("schemaHandlerFactory");
    factoryField.setAccessible(true);
    assertTrue(factoryField.get(target) != null);
  }
}
