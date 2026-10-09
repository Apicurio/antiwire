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
import static com.squareup.wire.testing.TestFiles.assertContainsExactlyInAnyOrderAsRelativePaths;
import static com.squareup.wire.testing.TestFiles.findFiles;
import static com.squareup.wire.testing.TestFiles.readUtf8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.wire.StringWireLogger;
import com.squareup.wire.WireLogger;
import com.squareup.wire.schema.internal.TypeMover;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okio.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-16 adoption of upstream WireRunTest:
 * {@code wire-compiler/src/test/java/com/squareup/wire/schema/WireRunTest.kt} at square/wire tag
 * 7.1.0. Inputs and expected values are verbatim. Recorded adaptations: upstream's in-memory
 * FakeFileSystem becomes the same tree under a JUnit {@link TempDir} on the real file system
 * (TASK-13 blanket adaptation), so paths inside exact messages interpolate the temp root where
 * upstream writes its "/" root; assertk maps onto JUnit 5; and upstream's KotlinTarget is
 * substituted with JavaTarget only in the cases whose assertions never mention Kotlin output
 * (myEventListenerSuccess, the three unusedTreeShaking* cases, skipDeclaredOptions, and
 * importNotFoundIncludesReferencingFile), keeping every asserted string untouched. Cases whose
 * inputs or expectations require the Kotlin generator or the Kotlin-specific ProtoReader32
 * emission are {@code @Disabled} naming DEC-6 with the reason recorded in this header and in
 * the TASK-16 ledger. The ProtoTarget cases (protoOnly,
 * protoTargetNeverEmitsGoogleProtobufDescriptor) run against the ported ProtoTarget since
 * TASK-16.2 with the same inputs and expectations as upstream.
 * javaPackageForJvmLanguages keeps only its
 * Java half (the Kotlin half of that mixed case is dropped, TASK-15 convention).
 */
public class WireRunTest {
  @TempDir java.nio.file.Path tempDir;

  StringWireLogger logger = new StringWireLogger();

  private Location location(String path) {
    return Location.get(tempDir.resolve(path).toString());
  }

  /**
   * Upstream's FakeFileSystem resolves relative out directories against its "/" root; the port's
   * real file system would resolve them against the process working directory, so every target's
   * out directory is rooted at the temp directory instead.
   */
  private String outDir(String dir) {
    return tempDir.resolve(dir).toString();
  }

  /** Upstream's {@code fs.findFiles(dir)}: files under {@code dir}, named {@code dir/...}. */
  private java.util.Set<String> filesUnder(String dir) throws IOException {
    java.nio.file.Path base = tempDir.resolve(dir);
    java.util.Set<String> result = new java.util.LinkedHashSet<>();
    if (!Files.exists(base)) return result;
    for (String file : findFiles(base)) {
      result.add(dir + "/" + file);
    }
    return result;
  }

  @Test
  public void javaOnly() throws IOException {
    writeBlueProto();
    writeRedProto();
    writeTriangleProto();

    WireRun wireRun = newWireRun(
        Collections.singletonList(location("colors/src/main/proto")),
        Collections.singletonList(location("polygons/src/main/proto")),
        new JavaTarget(outDir("generated/java")));
    wireRun.execute(okio.FileSystem.SYSTEM, logger);

    assertContainsExactlyInAnyOrderAsRelativePaths(filesUnder("generated"),
        "generated/java/squareup/colors/Blue.java",
        "generated/java/squareup/colors/Red.java");
    assertTrue(readUtf8("generated/java/squareup/colors/Blue.java")
        .contains("public final class Blue extends Message"));
    assertTrue(readUtf8("generated/java/squareup/colors/Red.java")
        .contains("public final class Red extends Message"));
  }

  @Test
  public void javaPackageOptionCannotEscapeOutDirectory() throws IOException {
    writeEscapingJavaPackageProto();

    WireRun wireRun = newWireRun(
        Collections.singletonList(location("colors/src/main/proto")),
        Collections.emptyList(),
        new JavaTarget(outDir("generated/java")));

    // A path separator cannot be part of a package name, so the option value is rejected before it
    // reaches the path check in SchemaHandler.checkPathInOutDirectory.
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> wireRun.execute(okio.FileSystem.SYSTEM, logger));
    assertNotNull(e.getMessage());
    assertTrue(e.getMessage().contains(
        "Refusing to use a package option value that cannot be a package name"), e.getMessage());
    assertTrue(e.getMessage().contains("character: '/'"), e.getMessage());

    // Nothing was written outside the configured output directory.
    assertFalse(java.nio.file.Path.of("/tmp/wire-escape/EscapeMe.java").toFile().exists());
  }

  @Test
  public void javaPackageWithSemicolonIsRejected() {
    assertPackageOptionRejected(
        "option java_package = \"com.squareup.colors;more\";",
        "java_package",
        "com.squareup.colors;more",
        "';'");
  }

  @Test
  public void javaPackageWithOpeningBraceIsRejected() {
    assertPackageOptionRejected(
        "option java_package = \"com.squareup.colors{more\";",
        "java_package",
        "com.squareup.colors{more",
        "'{'");
  }

  @Test
  public void javaPackageWithClosingBraceIsRejected() {
    assertPackageOptionRejected(
        "option java_package = \"com.squareup.colors}more\";",
        "java_package",
        "com.squareup.colors}more",
        "'}'");
  }

  @Test
  public void javaPackageWithOpeningParenthesisIsRejected() {
    assertPackageOptionRejected(
        "option java_package = \"com.squareup.colors(more\";",
        "java_package",
        "com.squareup.colors(more",
        "'('");
  }

  @Test
  public void javaPackageWithClosingParenthesisIsRejected() {
    assertPackageOptionRejected(
        "option java_package = \"com.squareup.colors)more\";",
        "java_package",
        "com.squareup.colors)more",
        "')'");
  }

  @Test
  public void javaPackageWithSlashIsRejected() {
    assertPackageOptionRejected(
        "option java_package = \"com.squareup.colors/more\";",
        "java_package",
        "com.squareup.colors/more",
        "'/'");
  }

  @Test
  public void javaPackageWithAsteriskIsRejected() {
    assertPackageOptionRejected(
        "option java_package = \"com.squareup.colors*more\";",
        "java_package",
        "com.squareup.colors*more",
        "'*'");
  }

  @Test
  public void javaPackageWithQuoteIsRejected() {
    assertPackageOptionRejected(
        "option java_package = \"com.squareup.colors\\\"more\";",
        "java_package",
        "com.squareup.colors\"more",
        "'\"'");
  }

  /** javac decodes Unicode escapes before it reads tokens, so a backslash reaches every other one. */
  @Test
  public void javaPackageWithBackslashIsRejected() {
    assertPackageOptionRejected(
        "option java_package = \"com.squareup.colors\\\\u003bmore\";",
        "java_package",
        "com.squareup.colors\\u003bmore",
        "'\\'");
  }

  @Test
  public void javaPackageWithSpaceIsRejected() {
    assertPackageOptionRejected(
        "option java_package = \"com.squareup.colors more\";",
        "java_package",
        "com.squareup.colors more",
        "' '");
  }

  @Test
  public void javaPackageWithNewlineIsRejected() {
    assertPackageOptionRejected(
        "option java_package = \"com.squareup.colors\\nmore\";",
        "java_package",
        "com.squareup.colors\\u000amore",
        "'\\u000a'");
  }

  /**
   * An alert character is a control character, and it is not whitespace. The message must print it
   * as an escape, so that the diagnostic stays readable and stays on one line.
   */
  @Test
  public void javaPackageWithControlCharacterIsRejected() {
    assertPackageOptionRejected(
        "option java_package = \"com.squareup.colors\\amore\";",
        "java_package",
        "com.squareup.colors\\u0007more",
        "'\\u0007'");
  }

  @Test
  public void wirePackageWithSemicolonIsRejected() {
    assertPackageOptionRejected(
        "import \"wire/extensions.proto\";\n"
            + "option (wire.wire_package) = \"com.squareup.colors;more\";",
        "wire.wire_package",
        "com.squareup.colors;more",
        "';'");
  }

  /** A Kotlin keyword segment is a legal Kotlin package, so Wire must keep accepting it. */
  @Test
  @Disabled("DEC-6: asserts KotlinTarget output; the Kotlin generator is not ported")
  public void javaPackageWithUnusualButHarmlessSegmentsIsAccepted() {
    // Upstream generates generated/kt/com/squareup/enum/_private/Orange.kt through KotlinTarget.
  }

  /** Wire reads the package options of every file in the schema, so this value is checked too. */
  @Test
  public void javaPackageOnProtoPathFileIsRejected() throws IOException {
    writeBlueProto();
    add(tempDir, "polygons/src/main/proto/squareup/polygons/triangle.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.polygons;\n"
        + "option java_package = \"com.squareup.polygons;more\";\n"
        + "message Triangle {\n"
        + "  repeated double angles = 1;\n"
        + "}\n");

    WireRun wireRun = newWireRun(
        Collections.singletonList(location("colors/src/main/proto")),
        Collections.singletonList(location("polygons/src/main/proto")),
        new JavaTarget(outDir("generated/java")));

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> wireRun.execute(okio.FileSystem.SYSTEM, logger));
    assertNotNull(e.getMessage());
    assertTrue(e.getMessage().contains(
        "Refusing to use a package option value that cannot be a package name"), e.getMessage());
    assertTrue(e.getMessage().contains("option:    java_package"), e.getMessage());
    assertTrue(e.getMessage().contains("character: ';'"), e.getMessage());

    assertFalse(Files.exists(tempDir.resolve("generated/java/squareup/colors/Blue.java")));
  }

  private void assertPackageOptionRejected(
      String option, String optionName, String value, String character) {
    try {
      add(tempDir, "colors/src/main/proto/squareup/colors/orange.proto", ""
          + "syntax = \"proto2\";\n"
          + "package squareup.colors;\n"
          + option + "\n"
          + "/** This is a warm color. */\n"
          + "message Orange {\n"
          + "  optional string circle = 1;\n"
          + "}\n");
    } catch (IOException e) {
      throw new AssertionError(e);
    }

    WireRun wireRun = newWireRun(
        Collections.singletonList(location("colors/src/main/proto")),
        Collections.emptyList(),
        new JavaTarget(outDir("generated/java")));

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> wireRun.execute(okio.FileSystem.SYSTEM, logger));
    assertNotNull(e.getMessage());
    assertTrue(e.getMessage().contains(
        "Refusing to use a package option value that cannot be a package name"), e.getMessage());
    assertTrue(e.getMessage().contains("option:    " + optionName), e.getMessage());
    assertTrue(e.getMessage().contains("value:     " + value), e.getMessage());
    assertTrue(e.getMessage().contains("character: " + character), e.getMessage());
    assertTrue(e.getMessage().contains(
        "file:      " + location("colors/src/main/proto") + "/squareup/colors/orange.proto"),
        e.getMessage());
  }

  @Test
  @Disabled("DEC-6: KotlinTarget generation; the Kotlin generator is not ported")
  public void ktOnly() {
    // Upstream asserts generated/kt/squareup/colors/{Blue,Red}.kt through KotlinTarget.
  }

  @Test
  @Disabled("DEC-6: KotlinTarget service generation; the Kotlin generator is not ported")
  public void ktOnlyWithService() {
  }

  @Test
  @Disabled("DEC-6: KotlinTarget BLOCKING RpcCallStyle; the Kotlin generator is not ported")
  public void ktOnlyWithBlockingService() {
  }

  @Test
  @Disabled("DEC-6: KotlinTarget single-method services; the Kotlin generator is not ported")
  public void ktOnlyWithServiceAsSingleMethod() {
  }

  @Test
  public void protoOnly() throws IOException {
    writeBlueProto();
    writeRedProto();
    writeTriangleProto();

    WireRun wireRun = newWireRun(
        Collections.singletonList(location("colors/src/main/proto")),
        Collections.singletonList(location("polygons/src/main/proto")),
        new ProtoTarget(outDir("generated/proto")));
    wireRun.execute(okio.FileSystem.SYSTEM, logger);

    assertContainsExactlyInAnyOrderAsRelativePaths(filesUnder("generated"),
        "generated/proto/squareup/colors/blue.proto",
        "generated/proto/squareup/colors/red.proto");
    assertTrue(readUtf8("generated/proto/squareup/colors/blue.proto")
        .contains("message Blue {"));
    assertTrue(readUtf8("generated/proto/squareup/colors/red.proto")
        .contains("message Red {"));
  }

  @Test
  public void protoTargetNeverEmitsGoogleProtobufDescriptor() throws IOException {
    writeSquareProto();
    writeMinimalGoogleProtobufProtos();
    writeMinimalWireProtos();

    WireRun wireRun = newWireRun(
        Arrays.asList(
            location("polygons/src/main/proto"),
            location("google/src/main/proto"),
            location("wire/src/main/proto")),
        Collections.emptyList(),
        new ProtoTarget(outDir("generated/proto")));
    wireRun.execute(okio.FileSystem.SYSTEM, logger);

    // We're happy if google.protobuf.descriptor isn't here.
    assertContainsExactlyInAnyOrderAsRelativePaths(filesUnder("generated"),
        "generated/proto/squareup/polygons/square.proto");
  }

  @Test
  @Disabled("DEC-6: KotlinTarget generation; the Kotlin generator is not ported")
  public void ktThenJava() {
  }

  @Test
  @Disabled("DEC-6: KotlinTarget ByteString field assertions; the Kotlin generator is not ported")
  public void opaqueBeforeGeneratingKtThenJava() {
  }

  @Test
  public void noSuchClassEventListener() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> EventListeners.newEventListenerFactory("foo").create());
    assertEquals("Couldn't find EventListenerClass 'foo'", e.getMessage());
  }

  @Test
  public void noPublicConstructorEventListener() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> EventListeners.newEventListenerFactory("java.lang.Void").create());
    assertEquals("No public constructor on java.lang.Void", e.getMessage());
  }

  @Test
  public void classDoesNotImplementEventListenerInterface() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> EventListeners.newEventListenerFactory("java.lang.Object").create());
    assertEquals("java.lang.Object does not implement EventListener.Factory", e.getMessage());
  }

  @Test
  public void myEventListenerSuccess() throws IOException {
    MyEventListener listenerA = new MyEventListener();
    MyEventListener listenerB = new MyEventListener();
    List<MyEventListener> listeners = Arrays.asList(listenerA, listenerB);

    writeBlueProto();
    writeRedProto();
    writeTriangleProto();
    WireRun wireRun = newWireRun(
        Collections.singletonList(location("colors/src/main/proto")),
        Collections.singletonList(location("polygons/src/main/proto")),
        Collections.singletonList("not.existing"),
        Collections.emptyList(),
        Arrays.asList(
            // Upstream uses KotlinTarget here; the Java target drives the same listener sequence.
            new JavaTarget(Collections.singletonList("squareup.colors.Blue"),
                Collections.emptyList(), true, outDir("generated/kt"), false, false, false, true, true,
                false),
            new JavaTarget(outDir("generated/kt"))),
        listeners,
        false);
    wireRun.execute(okio.FileSystem.SYSTEM, logger);

    for (MyEventListener listener : listeners) {
      assertEquals("runStart", listener.takeLog());
      assertEquals("loadSchemaStart", listener.takeLog());
      assertEquals("loadSchemaSuccess", listener.takeLog());
      assertEquals("treeShakeStart", listener.takeLog());
      assertEquals("treeShakeEnd", listener.takeLog());
      assertEquals("moveTypesStart", listener.takeLog());
      assertEquals("moveTypesEnd", listener.takeLog());
      assertEquals("schemaHandlersStart", listener.takeLog());
      assertEquals("schemaHandlerStart", listener.takeLog());
      assertEquals("schemaHandlerEnd", listener.takeLog());
      assertEquals("schemaHandlerStart", listener.takeLog());
      assertEquals("schemaHandlerEnd", listener.takeLog());
      assertEquals("schemaHandlersEnd", listener.takeLog());
      assertEquals("runSuccess", listener.takeLog());
      listener.assertAllLogsAreConsumed();
    }
  }

  static class MyEventListener extends EventListener {
    private final Deque<String> logs = new ArrayDeque<>();

    public String takeLog() {
      return logs.removeFirst();
    }

    public void assertAllLogsAreConsumed() {
      if (!logs.isEmpty()) {
        throw new AssertionError("Unconsumed logs: " + String.join(", ", logs));
      }
    }

    @Override public void runStart(WireRun wireRun) {
      logs.add("runStart");
    }

    @Override public void runSuccess(WireRun wireRun) {
      logs.add("runSuccess");
    }

    @Override public void runFailed(List<String> errors) {
      logs.add("runFailed");
    }

    @Override public void loadSchemaStart() {
      logs.add("loadSchemaStart");
    }

    @Override public void loadSchemaSuccess(Schema schema) {
      logs.add("loadSchemaSuccess");
    }

    @Override public void treeShakeStart(Schema schema, PruningRules pruningRules) {
      logs.add("treeShakeStart");
    }

    @Override public void treeShakeEnd(Schema refactoredSchema, PruningRules pruningRules) {
      logs.add("treeShakeEnd");
    }

    @Override public void moveTypesStart(Schema schema, List<TypeMover.Move> moves) {
      logs.add("moveTypesStart");
    }

    @Override public void moveTypesEnd(Schema refactoredSchema, List<TypeMover.Move> moves) {
      logs.add("moveTypesEnd");
    }

    @Override public void schemaHandlersStart() {
      logs.add("schemaHandlersStart");
    }

    @Override public void schemaHandlersEnd() {
      logs.add("schemaHandlersEnd");
    }

    @Override public void schemaHandlerStart(SchemaHandler schemaHandler,
        EmittingRules emittingRules) {
      logs.add("schemaHandlerStart");
    }

    @Override public void schemaHandlerEnd(SchemaHandler schemaHandler,
        EmittingRules emittingRules) {
      logs.add("schemaHandlerEnd");
    }

    public static class Factory implements EventListener.Factory {
      @Override public EventListener create() {
        return new MyEventListener();
      }
    }
  }

  @Test
  @Disabled("DEC-6: KotlinTarget generation; the Kotlin generator is not ported")
  public void javaThenKt() {
  }

  @Test
  @Disabled("DEC-6: expectations mix .kt and .java files via KotlinTarget")
  public void excludesTypeName() {
  }

  @Test
  @Disabled("DEC-6: expectations mix .kt and .java files via KotlinTarget")
  public void excludesWildcard() {
  }

  @Test
  @Disabled("DEC-6: asserts a generated .kt file through KotlinTarget")
  public void treeShakingRoots() {
  }

  @Test
  public void unusedTreeShakingRoots() throws IOException {
    writeBlueProto();
    writeRedProto();
    writeTriangleProto();

    WireRun wireRun = newWireRun(
        Collections.singletonList(location("colors/src/main/proto")),
        Collections.singletonList(location("polygons/src/main/proto")),
        Arrays.asList("squareup.colors.Blue", "squareup.colors.Purple",
            "squareup.colors.Color#name"),
        Collections.emptyList(),
        // Upstream uses KotlinTarget; the assertion is the thrown message only.
        Collections.singletonList(new JavaTarget(outDir("generated/kt"))),
        Collections.emptyList(),
        true);
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> wireRun.execute(okio.FileSystem.SYSTEM, logger));
    assertEquals("Unused element(s) in roots:\n"
            + "  squareup.colors.Purple\n"
            + "  squareup.colors.Color#name",
        e.getMessage());
  }

  @Test
  public void unusedTreeShakingPrunes() throws IOException {
    writeBlueProto();
    writeRedProto();
    writeTriangleProto();

    WireRun wireRun = newWireRun(
        Collections.singletonList(location("colors/src/main/proto")),
        Collections.singletonList(location("polygons/src/main/proto")),
        Collections.singletonList("squareup.colors.Blue"),
        Arrays.asList("squareup.colors.Purple", "squareup.colors.Color#name"),
        Collections.singletonList(new JavaTarget(outDir("generated/kt"))),
        Collections.emptyList(),
        true);
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> wireRun.execute(okio.FileSystem.SYSTEM, logger));
    assertEquals("Unused element(s) in prunes:\n"
            + "  squareup.colors.Purple\n"
            + "  squareup.colors.Color#name",
        e.getMessage());
  }

  @Test
  public void unusedTreeShakingRootsAndPrunes() throws IOException {
    writeBlueProto();
    writeRedProto();
    writeTriangleProto();

    WireRun wireRun = newWireRun(
        Collections.singletonList(location("colors/src/main/proto")),
        Collections.singletonList(location("polygons/src/main/proto")),
        Arrays.asList("squareup.colors.Blue", "squareup.colors.Green"),
        Arrays.asList("squareup.colors.Purple", "squareup.colors.Color#name"),
        Collections.singletonList(new JavaTarget(outDir("generated/kt"))),
        Collections.emptyList(),
        true);
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> wireRun.execute(okio.FileSystem.SYSTEM, logger));
    assertEquals("Unused element(s) in roots:\n"
            + "  squareup.colors.Green\n"
            + "Unused element(s) in prunes:\n"
            + "  squareup.colors.Purple\n"
            + "  squareup.colors.Color#name",
        e.getMessage());
  }

  /** Confirm we can disable the {@code rejectUnusedRootsOrPrunes} check. */
  @Test
  @Disabled("DEC-6: asserts a generated .kt file through KotlinTarget")
  public void permitUnusedRootsOrPrunes() {
  }

  @Test
  @Disabled("DEC-6: asserts a generated .kt file through KotlinTarget")
  public void treeShakingRubbish() {
  }

  @Test
  public void javaPackageForJvmLanguages() throws IOException {
    writeSquareProto();
    writeRhombusProto();

    WireRun wireRun = newWireRun(
        Collections.singletonList(location("polygons/src/main/proto")),
        Collections.emptyList(),
        // Upstream pairs this JavaTarget with a KotlinTarget; the Kotlin half of the mixed case
        // is dropped with the excluded generator (DEC-6), the Java assertions are verbatim.
        new JavaTarget(Collections.singletonList("squareup.polygons.Square"),
            Collections.emptyList(), true, outDir("generated/java"), false, false, false, true, true,
            false));
    wireRun.execute(okio.FileSystem.SYSTEM, logger);

    assertTrue(filesUnder("generated").contains("generated/java/com/squareup/polygons/Square.java"));
  }

  @Test
  @Disabled("DEC-6: the case's subject is the same type emitted by two generators; only one is ported")
  public void nonExclusiveTypeEmittedTwice() {
  }

  @Test
  @Disabled("DEC-6: asserts generated .kt files through KotlinTarget")
  public void proto3ReadAlways() {
  }

  /**
   * If Wire loaded (and therefore validated) members of dependencies this would fail with a
   * {@link SchemaException}. But we no longer do this to make Wire both faster and to eliminate the
   * need to place all transitive dependencies in the proto path.
   */
  @Test
  public void onlyDirectDependenciesOfSourcePathRequired() throws IOException {
    writeBlueProto();
    add(tempDir, "polygons/src/main/proto/squareup/polygons/triangle.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.polygons;\n"
        + "message Triangle {\n"
        + "  repeated squareup.geometry.Angle angles = 2; // No such type!\n"
        + "}\n");

    WireRun wireRun = newWireRun(
        Collections.singletonList(location("colors/src/main/proto")),
        Collections.singletonList(location("polygons/src/main/proto")),
        new JavaTarget(outDir("generated/java")));
    wireRun.execute(okio.FileSystem.SYSTEM, logger);

    assertContainsExactlyInAnyOrderAsRelativePaths(filesUnder("generated"),
        "generated/java/squareup/colors/Blue.java");
    assertTrue(readUtf8("generated/java/squareup/colors/Blue.java")
        .contains("public final class Blue extends Message"));
  }

  @Test
  public void optionsOnlyValidatedForPathFiles() throws IOException {
    writeBlueProto();
    add(tempDir, "polygons/src/main/proto/squareup/polygons/triangle.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.polygons;\n"
        + "option (unicorn) = true; // No such option!\n"
        + "message Triangle {\n"
        + "}\n");
    WireRun wireRun = newWireRun(
        Collections.singletonList(location("colors/src/main/proto")),
        Collections.singletonList(location("polygons/src/main/proto")),
        new JavaTarget(outDir("generated/java")));
    wireRun.execute(okio.FileSystem.SYSTEM, logger);
    assertContainsExactlyInAnyOrderAsRelativePaths(filesUnder("generated"),
        "generated/java/squareup/colors/Blue.java");
    assertTrue(readUtf8("generated/java/squareup/colors/Blue.java")
        .contains("public final class Blue extends Message"));
  }

  @Test
  public void customOnly() throws IOException {
    writeBlueProto();
    writeRedProto();
    writeTriangleProto();

    WireRun wireRun = newWireRun(
        Collections.singletonList(location("colors/src/main/proto")),
        Collections.singletonList(location("polygons/src/main/proto")),
        new CustomTarget(outDir("generated/markdown"), new MarkdownHandlerFactory()));
    wireRun.execute(okio.FileSystem.SYSTEM, logger);

    assertContainsExactlyInAnyOrderAsRelativePaths(filesUnder("generated"),
        "generated/markdown/squareup/colors/Blue.md",
        "generated/markdown/squareup/colors/Red.md");
    assertEquals(""
        + "# Blue\n"
        + "\n"
        + "This is the color of the sky.\n",
        readUtf8("generated/markdown/squareup/colors/Blue.md"));
    assertEquals(""
        + "# Red\n"
        + "\n"
        + "This is the color of the sky when the sky is lava.\n",
        readUtf8("generated/markdown/squareup/colors/Red.md"));
  }

  static class NullSchemaHandler extends SchemaHandler {
    @Override public void handle(Schema schema, Context context) {
    }

    @Override public Path handle(Type type, Context context) {
      return null;
    }

    @Override public List<Path> handle(Service service, Context context) {
      return Collections.emptyList();
    }

    @Override public Path handle(Extend extend, Field field, Context context) {
      return null;
    }

    static class Factory implements SchemaHandler.Factory {
      @Override public SchemaHandler create(
          List<String> includes,
          List<String> excludes,
          boolean exclusive,
          String outDirectory,
          Map<String, String> options) {
        return new NullSchemaHandler();
      }
    }
  }

  /**
   * We had a bug where custom handlers that don't need {@link
   * SchemaHandler.Context#emittingRules()}, would trigger an annoying warning like this:
   *
   * <pre>{@code
   * Unused includes in targets:
   *   *
   * }</pre>
   *
   * The '*' here is the default includes rule, which isn't really the user's fault.
   */
  @Test
  public void noUnusedIncludesWarningOnStar() throws IOException {
    writeTriangleProto();

    WireRun wireRun = newWireRun(
        Collections.singletonList(location("polygons/src/main/proto")),
        Collections.emptyList(),
        new CustomTarget(outDir("generated/out"), new NullSchemaHandler.Factory()));
    wireRun.execute(okio.FileSystem.SYSTEM, logger);

    assertEquals("", logger.log());
  }

  @Test
  public void loadExhaustively() throws IOException {
    writeBlueProto();
    writeRedProto();
    writeTriangleProto();

    final Schema[] handledSchema = new Schema[1];

    class CustomSchemaHandler extends SchemaHandler {
      @Override public void handle(Schema schema, Context context) {
        handledSchema[0] = schema;
        super.handle(schema, context);
      }

      @Override public Path handle(Type type, Context context) {
        return null;
      }

      @Override public List<Path> handle(Service service, Context context) {
        return Collections.emptyList();
      }

      @Override public Path handle(Extend extend, Field field, Context context) {
        return null;
      }
    }

    WireRun wireRun = new WireRun(
        Collections.singletonList(location("colors/src/main/proto")),
        Collections.singletonList(location("polygons/src/main/proto")),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        null,
        Collections.singletonList(
            new CustomTarget(outDir("generated/out"), new SchemaHandler.Factory() {
              @Override public SchemaHandler create(
                  List<String> includes,
                  List<String> excludes,
                  boolean exclusive,
                  String outDirectory,
                  Map<String, String> options) {
                return new CustomSchemaHandler();
              }
            })),
        Collections.emptyMap(),
        false,
        true,
        false,
        Collections.emptyList(),
        true,
        Collections.emptyList());
    wireRun.execute(okio.FileSystem.SYSTEM, logger);

    // These fields would not be present by default, but are linked when using `loadExhaustively`.
    MessageType triangle = (MessageType) handledSchema[0].getType("squareup.polygons.Triangle");
    assertNotNull(triangle);
    assertFalse(triangle.fields().isEmpty());
  }

  @Test
  public void noSuchClass() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> callCustomHandler(CustomTarget.newSchemaHandler("foo")));
    assertEquals("Couldn't find SchemaHandlerClass 'foo'", e.getMessage());
  }

  @Test
  public void noPublicConstructor() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> callCustomHandler(CustomTarget.newSchemaHandler("java.lang.Void")));
    assertEquals("No public constructor on java.lang.Void", e.getMessage());
  }

  @Test
  public void classDoesNotImplementCustomHandlerInterface() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> callCustomHandler(CustomTarget.newSchemaHandler("java.lang.Object")));
    assertEquals("java.lang.Object does not implement SchemaHandler.Factory", e.getMessage());
  }

  public static class ErrorReportingCustomHandler implements SchemaHandler.Factory {
    @Override public SchemaHandler create(
        List<String> includes,
        List<String> excludes,
        boolean exclusive,
        String outDirectory,
        Map<String, String> options) {
      return new SchemaHandler() {
        @Override public Path handle(Type type, Context context) {
          ErrorCollector errorCollector = context.getErrorCollector();
          if (type.getLocation().getPath().contains("descriptor.proto")) return null; // Don't report errors on built-in stuff.
          if (type instanceof MessageType) {
            for (Field field : ((MessageType) type).fields()) {
              if (field.getName().startsWith("a")) {
                errorCollector.at(field).add("field starts with 'a'");
              }
            }
          }
          return null;
        }

        @Override public List<Path> handle(Service service, Context context) {
          return Collections.emptyList();
        }

        @Override public Path handle(Extend extend, Field field, Context context) {
          return null;
        }
      };
    }
  }

  @Test
  public void errorReportingCustomHandler() {
    SchemaHandler.Factory customHandler = CustomTarget.newSchemaHandler(
        "com.squareup.wire.schema.WireRunTest$ErrorReportingCustomHandler");

    SchemaException e = assertThrows(SchemaException.class,
        () -> callCustomHandler(customHandler));
    assertEquals(""
            + "field starts with 'a'\n"
            + "  for field angles (" + location("polygons/src/main/proto")
            + "/squareup/polygons/triangle.proto:4:3)",
        e.getMessage());
  }

  private void callCustomHandler(SchemaHandler.Factory schemaHandlerFactory) throws IOException {
    writeTriangleProto();
    SchemaLoader schemaLoader = new SchemaLoader(okio.FileSystem.SYSTEM);
    schemaLoader.initRoots(
        Collections.singletonList(location("polygons/src/main/proto")),
        Collections.emptyList());
    Schema schema = schemaLoader.loadSchema();
    ErrorCollector errorCollector = new ErrorCollector();
    schemaHandlerFactory
        .create(
            Collections.emptyList(),
            Collections.emptyList(),
            true,
            "",
            Collections.emptyMap())
        .handle(
            schema,
            new SchemaHandler.Context(
                okio.FileSystem.SYSTEM,
                okio.Path.get(tempDir.resolve("out").toString()),
                WireLogger.NONE,
                errorCollector,
                new EmittingRules.Builder().build(),
                null,
                new ClaimedPaths(),
                null,
                null,
                null,
                schema));

    errorCollector.throwIfNonEmpty();
  }

  private void writeRedProto() throws IOException {
    add(tempDir, "colors/src/main/proto/squareup/colors/red.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "/** This is the color of the sky when the sky is lava. */\n"
        + "message Red {\n"
        + "  optional string oval = 1;\n"
        + "}\n");
  }

  @Test
  @Disabled("DEC-6: asserts a generated .kt file through KotlinTarget")
  public void includeSubTypes() {
  }

  @Test
  @Disabled("DEC-6: asserts a generated .kt file through KotlinTarget")
  public void includeSubTypesWithPruning() {
  }

  @Test
  public void partitionAcrossFiles() throws IOException {
    add(tempDir, "protos/one.proto", ""
        + "syntax = \"proto2\";\n"
        + "message A {}\n"
        + "message B {}\n"
        + "\n");
    Map<String, WireRun.Module> modules = new LinkedHashMap<>();
    modules.put("a", new WireRun.Module(Collections.emptySet(),
        new PruningRules.Builder().prune("B").build()));
    modules.put("b", new WireRun.Module(Collections.singleton("a")));
    WireRun wireRun = new WireRun(
        Collections.singletonList(location("protos")),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        null,
        Collections.singletonList(new JavaTarget(outDir("gen"))),
        modules,
        false,
        false,
        false,
        Collections.emptyList(),
        true,
        Collections.emptyList());
    wireRun.execute(okio.FileSystem.SYSTEM, logger);

    assertContainsExactlyInAnyOrderAsRelativePaths(
        filesUnder("gen/a"), "gen/a/A.java");
    assertContainsExactlyInAnyOrderAsRelativePaths(
        filesUnder("gen/b"), "gen/b/B.java");
  }

  @Test
  public void partitionWithOptionsIsNotLinkedTwice() throws IOException {
    // This test exercises a bug where stub replacement would cause options to get linked twice
    // which would then fail as a duplicate.

    add(tempDir, "protos/one.proto", ""
        + "syntax = \"proto2\";\n"
        + "package example;\n"
        + "\n"
        + "import 'google/protobuf/descriptor.proto';\n"
        + "\n"
        + "extend google.protobuf.MessageOptions {\n"
        + "  optional string type = 12000 [(maps_to) = 'test'];\n"
        + "}\n"
        + "extend google.protobuf.FieldOptions {\n"
        + "  optional string maps_to = 123301;\n"
        + "}\n"
        + "\n"
        + "message A {\n"
        + "}\n"
        + "message B {\n"
        + "}\n"
        + "\n");
    Map<String, WireRun.Module> modules = new LinkedHashMap<>();
    modules.put("a", new WireRun.Module(Collections.emptySet(),
        new PruningRules.Builder().prune("example.B").build()));
    modules.put("b", new WireRun.Module(Collections.singleton("a")));
    WireRun wireRun = new WireRun(
        Collections.singletonList(location("protos")),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        null,
        Collections.singletonList(new JavaTarget(outDir("gen"))),
        modules,
        false,
        false,
        false,
        Collections.emptyList(),
        true,
        Collections.emptyList());
    wireRun.execute(okio.FileSystem.SYSTEM, logger);

    // TODO(jwilson): fix modules to treat extension fields as first-class objects.
    assertContainsExactlyInAnyOrderAsRelativePaths(filesUnder("gen/a"),
        "gen/a/example/A.java",
        "gen/a/example/MapsToOption.java",
        "gen/a/example/TypeOption.java");
    assertContainsExactlyInAnyOrderAsRelativePaths(filesUnder("gen/b"),
        "gen/b/example/B.java",
        "gen/b/example/MapsToOption.java",
        "gen/b/example/TypeOption.java");
  }

  @Test public void crashWhenTypeGenerationConflicts() throws IOException {
    add(tempDir, "protos/one/au.proto", ""
        + "package one;\n"
        + "option java_package = \"same.package\";\n"
        + "message Owner {}\n"
        + "\n");
    add(tempDir, "protos/two/jp.proto", ""
        + "package two;\n"
        + "option java_package = \"same.package\";\n"
        + "message Owner {}\n"
        + "\n");
    WireRun wireRun = newWireRun(
        Collections.singletonList(location("protos")),
        Collections.emptyList(),
        new JavaTarget(outDir("generated/java")));

    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> wireRun.execute(okio.FileSystem.SYSTEM, logger));
    assertEquals(""
            + "Same file " + outDir("generated/java") + "/same/package/Owner.java"
            + " is getting generated by different messages:\n"
            + "  Owner at " + location("protos") + "/one/au.proto:3:1\n"
            + "  Owner at " + location("protos") + "/two/jp.proto:3:1",
        e.getMessage());
  }

  @Test public void optionNameIsPreciseToAvoidConflict_java() throws IOException {
    add(tempDir, "protos/zero/zero.proto", ""
        + "package zero;\n"
        + "option java_package = \"same.package\";\n"
        + "import \"google/protobuf/descriptor.proto\";\n"
        + "extend google.protobuf.FieldOptions {\n"
        + "  optional string documentation_url = 60001;\n"
        + "}\n"
        + "extend google.protobuf.MessageOptions {\n"
        + "  optional string documentation_url = 60002;\n"
        + "}\n"
        + "\n");
    WireRun wireRunAllOptions = new WireRun(
        Collections.singletonList(location("protos")),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        null,
        Collections.singletonList(new JavaTarget(
            Collections.singletonList("*"),
            Collections.emptyList(),
            true,
            outDir("generated/java"),
            false,
            false,
            false,
            true /* emitDeclaredOptions */,
            true /* emitAppliedOptions */,
            false)),
        Collections.emptyMap(),
        false,
        false,
        false,
        Collections.emptyList(),
        true,
        Collections.emptyList());
    wireRunAllOptions.execute(okio.FileSystem.SYSTEM, logger);
    assertContainsExactlyInAnyOrderAsRelativePaths(filesUnder("generated"),
        "generated/java/same/package/DocumentationUrlMessageOption.java",
        "generated/java/same/package/DocumentationUrlOption.java");
  }

  @Test
  @Disabled("DEC-6: KotlinTarget generation; the Kotlin generator is not ported")
  public void optionNameIsPreciseToAvoidConflict_kotlin() {
  }

  @Test
  @Disabled("DEC-6: asserts Kotlin-generated service clients; the Kotlin generator is not ported")
  public void javaDoesNotClaimServices() {
  }

  @Test
  @Disabled("DEC-6: the conflict needs Kotlin service generation; only one generator is ported")
  public void crashWhenServiceGenerationConflicts() {
  }

  @Test public void crashOnDependencyCycle() {
    Map<String, WireRun.Module> modules = new LinkedHashMap<>();
    modules.put("one", new WireRun.Module(Collections.singleton("two")));
    modules.put("two", new WireRun.Module(Collections.singleton("three")));
    modules.put("three", new WireRun.Module(Collections.singleton("one")));
    WireRun wireRun = new WireRun(
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        null,
        Collections.emptyList(),
        modules,
        false,
        false,
        false,
        Collections.emptyList(),
        true,
        Collections.emptyList());
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> wireRun.execute(okio.FileSystem.SYSTEM, WireLogger.NONE));
    assertEquals(""
        + "ERROR: Modules contain dependency cycle(s):\n"
        + " - [one, two, three]\n",
        e.getMessage());
  }

  @Test public void crashOnPackageCycle() throws IOException {
    add(tempDir, "source-path/people/employee.proto", ""
        + "syntax = \"proto2\";\n"
        + "import \"locations/office.proto\";\n"
        + "import \"locations/residence.proto\";\n"
        + "package people;\n"
        + "message Employee {\n"
        + "  optional locations.Office office = 1;\n"
        + "  optional locations.Residence residence = 2;\n"
        + "}\n");
    add(tempDir, "source-path/locations/office.proto", ""
        + "syntax = \"proto2\";\n"
        + "import \"people/office_manager.proto\";\n"
        + "package locations;\n"
        + "message Office {\n"
        + "  optional people.OfficeManager office_manager = 1;\n"
        + "}\n");
    add(tempDir, "source-path/locations/residence.proto", ""
        + "syntax = \"proto2\";\n"
        + "package locations;\n"
        + "message Residence {\n"
        + "}\n");
    add(tempDir, "source-path/people/office_manager.proto", ""
        + "syntax = \"proto2\";\n"
        + "package people;\n"
        + "message OfficeManager {\n"
        + "}\n");

    WireRun wireRun = newWireRun(
        Collections.singletonList(location("source-path")),
        Collections.emptyList(),
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        Collections.<Target>emptyList(),
        Collections.<EventListener>emptyList(),
        true);

    SchemaException e = assertThrows(SchemaException.class,
        () -> wireRun.execute(okio.FileSystem.SYSTEM, logger));
    assertEquals(""
            + "packages form a cycle:\n"
            + "  locations imports people\n"
            + "    locations/office.proto:\n"
            + "      import \"people/office_manager.proto\";\n"
            + "  people imports locations\n"
            + "    people/employee.proto:\n"
            + "      import \"locations/office.proto\";\n"
            + "      import \"locations/residence.proto\";",
        e.getMessage());
  }

  @Test
  @Disabled("DEC-6: expectations list .java and .kt files from paired Java and Kotlin targets")
  public void emitDeclaredOptions() {
  }

  @Test
  public void skipDeclaredOptions() throws IOException {
    writeDocumentationProto();
    WireRun wireRun = new WireRun(
        Collections.singletonList(location("docs/src/main/proto")),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        null,
        // Upstream pairs a JavaTarget and a KotlinTarget, both with emitDeclaredOptions = false;
        // both are JavaTargets here (the assertion, an empty output set, is verbatim).
        Arrays.asList(
            new JavaTarget(Collections.singletonList("*"), Collections.emptyList(), false,
                outDir("generated/java"), false, false, false, false, false, false),
            new JavaTarget(Collections.singletonList("*"), Collections.emptyList(), false,
                "generated/kt", false, false, false, false, false, false)),
        Collections.emptyMap(),
        false,
        false,
        false,
        Collections.emptyList(),
        true,
        Collections.emptyList());
    wireRun.execute(okio.FileSystem.SYSTEM, logger);
    assertContainsExactlyInAnyOrderAsRelativePaths(filesUnder("generated"));
  }

  @Test
  @Disabled("DEC-6: expectations list .java and .kt files from paired Java and Kotlin targets")
  public void emitAppliedOptions() {
  }

  @Test
  @Disabled("DEC-6: expectations list .java and .kt files from paired Java and Kotlin targets")
  public void skipAppliedOptions() {
  }

  @Test
  public void importNotFoundIncludesReferencingFile() throws IOException {
    writeBlueProto();
    writeSquareProto();

    WireRun wireRun = newWireRun(
        Collections.singletonList(location("colors/src/main/proto")),
        Collections.singletonList(location("polygons/src/main/proto")),
        // Upstream uses KotlinTarget; the assertion is the thrown message only.
        new JavaTarget(outDir("generated/kt")));

    SchemaException e = assertThrows(SchemaException.class,
        () -> wireRun.execute(okio.FileSystem.SYSTEM, logger));
    assertEquals(""
            + "unable to find squareup/polygons/triangle.proto\n"
            + "  searching 1 proto paths:\n"
            + "    " + location("polygons/src/main/proto") + "\n"
            + "  for file " + location("colors/src/main/proto")
            + "/squareup/colors/blue.proto\n"
            + "unable to resolve squareup.polygons.Triangle\n"
            + "  for field triangle (" + location("colors/src/main/proto")
            + "/squareup/colors/blue.proto:7:3)\n"
            + "  in message squareup.colors.Blue (" + location("colors/src/main/proto")
            + "/squareup/colors/blue.proto:5:1)",
        e.getMessage());
  }

  /** We had a bug where extension fields names needed to be globally unique. */
  @Test
  @Disabled("DEC-6: asserts generated .kt files through KotlinTarget")
  public void conflictingExtends() {
  }

  @Test
  @Disabled("DEC-6: emitProtoReader32 is a Kotlin-generator-only emission mode")
  public void emitProtoReader32() {
  }

  @Test
  @Disabled("DEC-6: ProtoReader32 emission is a Kotlin-generator-only feature")
  public void skipProtoReader32() {
  }

  private void writeOrangeProto() throws IOException {
    add(tempDir, "colors/src/main/proto/squareup/colors/orange.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "import \"squareup/polygons/triangle.proto\";\n"
        + "message Orange {\n"
        + "  optional string circle = 1;\n"
        + "  optional squareup.polygons.Triangle.Type triangle = 2;\n"
        + "}\n");
  }

  private void writeColorsRouteProto() throws IOException {
    add(tempDir, "routes/src/main/proto/squareup/routes/route.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.routes;\n"
        + "import \"squareup/colors/blue.proto\";\n"
        + "import \"squareup/colors/red.proto\";\n"
        + "service Route {\n"
        + "  rpc GetUpdatedRed(squareup.colors.Red) returns (squareup.colors.Red) {}\n"
        + "  rpc GetUpdatedBlue(squareup.colors.Blue) returns (squareup.colors.Blue) {}\n"
        + "}\n");
  }

  private void writeBlueProto() throws IOException {
    add(tempDir, "colors/src/main/proto/squareup/colors/blue.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "import \"squareup/polygons/triangle.proto\";\n"
        + "/** This is the color of the sky. */\n"
        + "message Blue {\n"
        + "  optional string circle = 1;\n"
        + "  optional squareup.polygons.Triangle triangle = 2;\n"
        + "}\n");
  }

  private void writeEscapingJavaPackageProto() throws IOException {
    add(tempDir, "colors/src/main/proto/squareup/colors/escape.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.colors;\n"
        + "option java_package = \"/tmp/wire-escape\";\n"
        + "message EscapeMe {\n"
        + "  optional string circle = 1;\n"
        + "}\n");
  }

  private void writeTriangleProto() throws IOException {
    add(tempDir, "polygons/src/main/proto/squareup/polygons/triangle.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.polygons;\n"
        + "message Triangle {\n"
        + "  repeated double angles = 1;\n"
        + "  enum Type {\n"
        + "    EQUILATERAL = 1;\n"
        + "    ISOSCELES = 2;\n"
        + "    RIGHTANGLED = 3;\n"
        + "  }\n"
        + "}\n");
  }

  private void writeSquareProto() throws IOException {
    add(tempDir, "polygons/src/main/proto/squareup/polygons/square.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.polygons;\n"
        + "option java_package = \"com.squareup.polygons\";\n"
        + "message Square {\n"
        + "  optional double length = 1;\n"
        + "}\n");
  }

  private void writeRhombusProto() throws IOException {
    add(tempDir, "polygons/src/main/proto/squareup/polygons/rhombus.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.polygons;\n"
        + "option java_package = \"com.squareup.polygons\";\n"
        + "message Rhombus {\n"
        + "  optional double length = 1;\n"
        + "  optional double acute_angle = 2;\n"
        + "}\n");
  }

  private void writeDocumentationProto() throws IOException {
    add(tempDir, "docs/src/main/proto/squareup/options/documentation.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.options;\n"
        + "import \"google/protobuf/descriptor.proto\";\n"
        + "\n"
        + "extend google.protobuf.MessageOptions {\n"
        + "  optional string documentation_url = 22200;\n"
        + "}\n");
  }

  private void writeOctagonProto() throws IOException {
    add(tempDir, "polygons/src/main/proto/squareup/polygons/octagon.proto", ""
        + "syntax = \"proto2\";\n"
        + "package squareup.polygons;\n"
        + "import \"squareup/options/documentation.proto\";\n"
        + "\n"
        + "message Octagon {\n"
        + "  option (options.documentation_url) = \"https://en.wikipedia.org/wiki/Octagon\";\n"
        + "  optional bool stop = 1;\n"
        + "}\n");
  }

  private void writeMinimalGoogleProtobufProtos() throws IOException {
    add(tempDir, "google/src/main/proto/google/protobuf/descriptor.proto", ""
        + "syntax = \"proto2\";\n"
        + "package google.protobuf;\n"
        + "message descriptor {}\n");
    add(tempDir, "google/src/main/proto/google/protobuf/any.proto", ""
        + "syntax = \"proto2\";\n"
        + "package google.protobuf;\n"
        + "message Any {}\n");
    add(tempDir, "google/src/main/proto/google/protobuf/duration.proto", ""
        + "syntax = \"proto2\";\n"
        + "package google.protobuf;\n"
        + "message Duration {}\n");
    add(tempDir, "google/src/main/proto/google/protobuf/empty.proto", ""
        + "syntax = \"proto2\";\n"
        + "package google.protobuf;\n"
        + "message Empty {}\n");
    add(tempDir, "google/src/main/proto/google/protobuf/struct.proto", ""
        + "syntax = \"proto2\";\n"
        + "package google.protobuf;\n"
        + "message Struct {}\n");
    add(tempDir, "google/src/main/proto/google/protobuf/timestamp.proto", ""
        + "syntax = \"proto2\";\n"
        + "package google.protobuf;\n"
        + "message Timestamp {}\n");
    add(tempDir, "google/src/main/proto/google/protobuf/wrappers.proto", ""
        + "syntax = \"proto2\";\n"
        + "package google.protobuf;\n"
        + "message Wrappers {}\n");
  }

  private void writeMinimalWireProtos() throws IOException {
    add(tempDir, "wire/src/main/proto/wire/extensions.proto", ""
        + "syntax = \"proto2\";\n"
        + "package google.protobuf;\n"
        + "message extensions {}\n");
  }

  @Test public void noSuchClassLogger() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> WireLoggers.newLoggerFactory("foo").create());
    assertEquals("Couldn't find LoggerClass 'foo'", e.getMessage());
  }

  @Test public void noPublicConstructorLogger() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> WireLoggers.newLoggerFactory("java.lang.Void").create());
    assertEquals("No public constructor on java.lang.Void", e.getMessage());
  }

  @Test public void classDoesNotImplementWireLoggerInterface() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> WireLoggers.newLoggerFactory("java.lang.Object").create());
    assertEquals("java.lang.Object does not implement WireLogger.Factory", e.getMessage());
  }

  @Test
  @Disabled("DEC-6: asserts KotlinTarget artifactHandled logs (target=Kotlin)")
  public void customLogger() {
  }

  private String readUtf8(String path) throws IOException {
    return com.squareup.wire.testing.TestFiles.readUtf8(tempDir.resolve(path));
  }

  private WireRun newWireRun(List<Location> sourcePath, List<Location> protoPath, Target target) {
    return newWireRun(sourcePath, protoPath, Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(target), Collections.emptyList(), true);
  }

  private WireRun newWireRun(
      List<Location> sourcePath,
      List<Location> protoPath,
      List<String> treeShakingRoots,
      List<String> treeShakingRubbish,
      List<Target> targets,
      List<? extends EventListener> eventListeners,
      boolean rejectUnusedRootsOrPrunes) {
    return new WireRun(
        sourcePath,
        protoPath,
        treeShakingRoots,
        treeShakingRubbish,
        Collections.emptyList(),
        null,
        null,
        null,
        targets,
        Collections.emptyMap(),
        false,
        false,
        false,
        new ArrayList<EventListener>(eventListeners),
        rejectUnusedRootsOrPrunes,
        Collections.emptyList());
  }
}
