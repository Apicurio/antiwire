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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-16 adoption of upstream OptionsLinkingTest:
 * {@code wire-compiler/src/test/java/com/squareup/wire/schema/OptionsLinkingTest.kt} at
 * square/wire tag 7.1.0. Inputs and expected option maps are verbatim; upstream's in-memory
 * FakeFileSystem becomes a JUnit {@link TempDir} tree and assertk maps onto JUnit 5.
 */
public class OptionsLinkingTest {
  @TempDir Path tempDir;

  private static final ProtoType fieldOptions = ProtoType.get("google.protobuf.FieldOptions");
  private static final ProtoMember formattingOptionsField =
      ProtoMember.get(fieldOptions, "formatting_options");

  private static final ProtoType formattingOptionsType = ProtoType.get("FormattingOptions");
  private static final ProtoMember languageField =
      ProtoMember.get(formattingOptionsType, "language");

  private static final ProtoType languageType = ProtoType.get("Language");
  private static final ProtoMember nameField = ProtoMember.get(languageType, "name");

  @Test
  public void extensionOnTheSourcePathIsApplied() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "import \"formatting_options.proto\";\n"
        + "message A {\n"
        + "  optional string s = 1 [(formatting_options).language = \"English\"];\n"
        + "}\n");
    add(tempDir, "source-path/formatting_options.proto", ""
        + "import \"google/protobuf/descriptor.proto\";\n"
        + "\n"
        + "message FormattingOptions {\n"
        + "  optional string language = 1;\n"
        + "}\n"
        + "\n"
        + "extend google.protobuf.FieldOptions {\n"
        + "  optional FormattingOptions formatting_options = 22001;\n"
        + "}\n");
    Schema schema = loadAndLinkSchema();

    MessageType typeA = (MessageType) schema.getType("A");
    assertEquals(
        optionMap(formattingOptionsField, optionMap(languageField, "English")),
        typeA.field("s").getOptions().getMap());
  }

  @Test
  public void extensionOnTheProtoPathIsApplied() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "import \"formatting_options.proto\";\n"
        + "message A {\n"
        + "  optional string s = 1 [(formatting_options).language = \"English\"];\n"
        + "}\n");
    add(tempDir, "proto-path/formatting_options.proto", ""
        + "import \"google/protobuf/descriptor.proto\";\n"
        + "\n"
        + "message FormattingOptions {\n"
        + "  optional string language = 1;\n"
        + "}\n"
        + "\n"
        + "extend google.protobuf.FieldOptions {\n"
        + "  optional FormattingOptions formatting_options = 22001;\n"
        + "}\n");
    Schema schema = loadAndLinkSchema();

    MessageType typeA = (MessageType) schema.getType("A");
    assertEquals(
        optionMap(formattingOptionsField, optionMap(languageField, "English")),
        typeA.field("s").getOptions().getMap());
  }

  @Test
  public void fieldsOfExtensions() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "import \"formatting_options.proto\";\n"
        + "message A {\n"
        + "  optional string s = 1 [(formatting_options).language.name = \"English\"];\n"
        + "  optional string t = 2 [(length).max = 80];\n"
        + "}\n");
    add(tempDir, "proto-path/formatting_options.proto", ""
        + "import \"google/protobuf/descriptor.proto\";\n"
        + "\n"
        + "message FormattingOptions {\n"
        + "  optional Language language = 1;\n"
        + "  optional StringCasing string_casing = 2;\n"
        + "}\n"
        + "\n"
        + "extend google.protobuf.FieldOptions {\n"
        + "  optional FormattingOptions formatting_options = 22001;\n"
        + "  optional Range length = 22002;\n"
        + "}\n"
        + "\n"
        + "message Language {\n"
        + "  optional string name = 1;\n"
        + "  optional string locale = 2;\n"
        + "}\n"
        + "\n"
        + "enum StringCasing {\n"
        + "  LOWER_CASE = 1;\n"
        + "  TITLE_CASE = 2;\n"
        + "  SENTENCE_CASE = 3;\n"
        + "}\n"
        + "\n"
        + "message Range {\n"
        + "  optional double min = 1;\n"
        + "  optional double max = 2;\n"
        + "}\n");
    Schema schema = loadAndLinkSchema();

    MessageType typeA = (MessageType) schema.getType("A");
    assertEquals(
        optionMap(formattingOptionsField,
            optionMap(languageField, optionMap(nameField, "English"))),
        typeA.field("s").getOptions().getMap());

    MessageType typeLanguage = (MessageType) schema.getType("Language");
    assertNotNull(typeLanguage.field("name"));

    MessageType typeRange = (MessageType) schema.getType("Range");
    assertNotNull(typeRange.field("max"));
  }

  @Test
  public void rejectsInvalidOptionScalarLiterals() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "import \"formatting_options.proto\";\n"
        + "\n"
        + "message A {\n"
        + "  option (message_options).enabled = \"false; static { } //\";\n"
        + "  optional string s = 1 [\n"
        + "    (formatting_options).max = \"80; static { } //\",\n"
        + "    (formatting_options).casing = \"LOWER_CASE; static { } //\"\n"
        + "  ];\n"
        + "}\n");
    add(tempDir, "source-path/formatting_options.proto", ""
        + "import \"google/protobuf/descriptor.proto\";\n"
        + "\n"
        + "message MessageOptions {\n"
        + "  optional bool enabled = 1;\n"
        + "}\n"
        + "\n"
        + "message FormattingOptions {\n"
        + "  optional int32 max = 1;\n"
        + "  optional StringCasing casing = 2;\n"
        + "  optional string documentation = 3;\n"
        + "}\n"
        + "\n"
        + "enum StringCasing {\n"
        + "  LOWER_CASE = 1;\n"
        + "}\n"
        + "\n"
        + "extend google.protobuf.MessageOptions {\n"
        + "  optional MessageOptions message_options = 22001;\n"
        + "}\n"
        + "\n"
        + "extend google.protobuf.FieldOptions {\n"
        + "  optional FormattingOptions formatting_options = 22002;\n"
        + "}\n");

    SchemaException e = assertThrows(SchemaException.class, this::loadAndLinkSchema);
    String message = e.getMessage();
    assertTrue(message.contains("invalid option value \"false; static { } //\" for bool"),
        message);
    assertTrue(message.contains("invalid option value \"80; static { } //\" for int32"),
        message);
    assertTrue(message.contains("invalid option value \"LOWER_CASE; static { } //\" for StringCasing"),
        message);
  }

  @Test
  public void extensionTypesInExternalFile() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "import \"extensions.proto\";\n"
        + "\n"
        + "message A {\n"
        + "  optional string s = 2 [(length).max = 80];\n"
        + "}\n");
    add(tempDir, "proto-path/extensions.proto", ""
        + "import \"google/protobuf/descriptor.proto\";\n"
        + "import \"range.proto\";\n"
        + "\n"
        + "extend google.protobuf.FieldOptions {\n"
        + "  optional Range length = 22002;\n"
        + "}\n");
    add(tempDir, "proto-path/range.proto", ""
        + "\n"
        + "message Range {\n"
        + "  optional double min = 1;\n"
        + "  optional double max = 2;\n"
        + "}\n");
    Schema schema = loadAndLinkSchema();

    MessageType typeRange = (MessageType) schema.getType("Range");
    assertNotNull(typeRange.field("max"));
  }

  /** Kotlin's mapOf for option maps, insertion-ordered like the ported OptionsTest helper. */
  private static Map<ProtoMember, Object> optionMap(ProtoMember key, Object value) {
    Map<ProtoMember, Object> result = new java.util.LinkedHashMap<>();
    result.put(key, value);
    return result;
  }

  private Schema loadAndLinkSchema() throws IOException {
    SchemaLoader loader = new SchemaLoader(okio.FileSystem.SYSTEM);
    List<Location> protoPath = Files.exists(tempDir.resolve("proto-path"))
        ? Collections.singletonList(Location.get(tempDir.resolve("proto-path").toString()))
        : Collections.emptyList();

    loader.initRoots(
        Collections.singletonList(Location.get(tempDir.resolve("source-path").toString())),
        protoPath);
    return loader.loadSchema();
  }
}
