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

import static com.squareup.wire.testing.TestFiles.add;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.wire.SchemaBuilder;
import com.squareup.wire.StringWireLogger;
import com.squareup.wire.schema.JavaTarget;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.ProtoTarget;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.SchemaHandler;
import com.squareup.wire.schema.WireRun;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import okio.FileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-17 security regression corpus, generator-module items. One named case per inventory row in
 * docs/security-regression-inventory.md; each case fails when its defect is reintroduced: the
 * hostile input goes through the real generator or WireRun, and a regression changes the emitted
 * output or exception.
 */
public class JavaGeneratorSecurityCorpusTest {

  /**
   * Proto documentation carrying comment delimiters must not be able to close the generated
   * Javadoc and inject source: {@code *} and {@code /} pairs are HTML-escaped both ways
   * ({@code &#42;/} and {@code /&#42;}).
   */
  @Test public void issue3600_commentDelimitersEscapedInGeneratedJavadoc() throws IOException {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message Message {\n"
            + "  // */ class Cheeky { } /*\n"
            + "  optional string owner = 1;\n"
            + "}\n")
        .build();

    String javaOutput = new JavaWithProfilesGenerator(schema).generateJava("Message");

    assertTrue(javaOutput.contains("&#42;/ class Cheeky { } /&#42;"), javaOutput);
    assertFalse(javaOutput.contains("*/ class Cheeky"), javaOutput);
  }

  /**
   * Java decodes Unicode escapes before lexing, including inside comments, so a backslash in proto
   * documentation must be HTML-escaped ({@code &#92;}); otherwise {@code \\u002a\\u002f} hides a
   * comment termination from the reader.
   */
  @Test public void issue3622_unicodeEscapesEscapedInGeneratedJavadoc() throws IOException {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message Message {\n"
            + "  // \\u002a\\u002f class Cheeky { } \\u002f\\u002a\n"
            + "  optional string owner = 1;\n"
            + "}\n")
        .build();

    String javaOutput = new JavaWithProfilesGenerator(schema).generateJava("Message");

    assertTrue(javaOutput.contains("&#92;u002a&#92;u002f class Cheeky { } &#92;u002f&#92;u002a"),
        javaOutput);
    assertFalse(javaOutput.contains("\\u002a\\u002f class Cheeky"), javaOutput);
  }

  /**
   * Generated Java code merges repeated occurrences of the same oneof message member by emitting
   * {@code Internal.decodeMessageOrMerge} for it (pre-#3656 the oneof member was assigned with a
   * plain decode, so the last occurrence replaced the earlier ones).
   */
  @Test public void issue3656_oneofMessageMemberEmitsDecodeMessageOrMerge() throws IOException {
    Schema schema = new SchemaBuilder()
        .add("common.proto", ""
            + "syntax = \"proto2\";\n"
            + "import \"google/protobuf/field_mask.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  oneof choice {\n"
            + "    google.protobuf.FieldMask oneof_mask = 1;\n"
            + "    string name = 2;\n"
            + "  }\n"
            + "}\n")
        .build();

    String code = new JavaWithProfilesGenerator(schema).generateJava("Message");

    assertTrue(code.contains(
        "builder.oneof_mask(Internal.decodeMessageOrMerge(ProtoAdapter.FIELD_MASK, reader, builder.oneof_mask))"),
        code);
  }

  @TempDir java.nio.file.Path tempDir;

  /**
   * End-to-end pin of the #3657 call site: a {@code java_package} of {@code ..} survives the
   * package-option validation (dots are legal package characters) and reaches {@code
   * JavaSchemaHandler}'s path computation, which must refuse it through {@code
   * SchemaHandler.checkPathInOutDirectory} instead of writing outside the output directory. No
   * ported upstream test reaches this call (the upstream escape case is rejected earlier by the
   * package validation), so this case is port-only.
   */
  @Test public void issue3657_javaTargetRefusesEscapingPackageEndToEnd() throws IOException {
    add(tempDir, "src/main/proto/message.proto", ""
        + "syntax = \"proto2\";\n"
        + "\n"
        + "option java_package = \"..\";\n"
        + "\n"
        + "message EscapeMe {\n"
        + "  optional string circle = 1;\n"
        + "}\n");
    StringWireLogger logger = new StringWireLogger();
    WireRun wireRun = new WireRun(
        Collections.singletonList(Location.get(tempDir.resolve("src/main/proto").toString())),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        null,
        Collections.singletonList(new JavaTarget(tempDir.resolve("generated/java").toString())),
        Map.of(),
        false,
        false,
        false,
        List.of(),
        true,
        Collections.emptyList());

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> wireRun.execute(FileSystem.SYSTEM, logger));
    assertTrue(e.getMessage().contains("Refusing to write a generated file outside the output directory"),
        e.getMessage());
  }

  /**
   * Port-only hardening (TASK-16.2.1): ProtoTarget, like every other emitting handler, refuses a
   * recorded location path that traverses out of the output directory; upstream applies no check
   * here, so this is a deliberate divergence. An in-directory path is still written.
   */
  @Test public void protoTargetRefusesEscapingLocationPath() throws IOException {
    java.nio.file.Path out = tempDir.resolve("generated/proto");
    Schema base = new SchemaBuilder()
        .add("escape.proto", "syntax = \"proto2\";\nmessage Escape {\n  optional string a = 1;\n}\n")
        .build();
    ProtoFile pf = base.protoFile("escape.proto");
    Schema escapeSchema = new Schema(Collections.singletonList(pf.copy(
        Location.get("../escape.proto"), pf.getImports(), pf.getPublicImports(), pf.getWeakImports(),
        pf.getPackageName(), pf.getTypes(), pf.getServices(), pf.getExtendList(), pf.getOptions(),
        pf.getSyntax())));
    SchemaHandler handler = new ProtoTarget(out.toString()).newHandler();
    SchemaHandler.Context context = contextFor(out, escapeSchema);

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> handler.handle(escapeSchema, context));
    assertTrue(e.getMessage().contains("Refusing to write a generated file outside the output directory"),
        e.getMessage());
    assertFalse(Files.exists(tempDir.resolve("generated/escape.proto")));

    Schema okSchema = new SchemaBuilder()
        .add("pkg/ok.proto", "syntax = \"proto2\";\nmessage Ok {\n  optional string a = 1;\n}\n")
        .build();
    SchemaHandler.Context okContext = contextFor(out, okSchema);
    handler.handle(okSchema, okContext);
    assertTrue(Files.exists(out.resolve("pkg/ok.proto")));
  }

  private static SchemaHandler.Context contextFor(java.nio.file.Path out, Schema schema) {
    return new SchemaHandler.Context(
        FileSystem.SYSTEM, okio.Path.get(out.toString()), new StringWireLogger(), schema);
  }
}
