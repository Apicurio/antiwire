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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import okio.ForwardingFileSystem;
import okio.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-16 adoption of upstream LinkerTest:
 * {@code wire-compiler/src/test/java/com/squareup/wire/schema/LinkerTest.kt} at square/wire tag
 * 7.1.0. Inputs, expected schemas, and messages are verbatim; upstream's in-memory FakeFileSystem
 * becomes a JUnit {@link TempDir} tree (location assertions interpolate the temp root, the
 * TASK-13 blanket adaptation) and assertk maps onto JUnit 5. For
 * schemaIsDeterministicEvenIfProtoPathOrderIsNot, upstream reverses {@code listRecursively} on a
 * ForwardingFileSystem; the vendored okio makes {@code listRecursively} final, so the port
 * reverses {@code list} instead, which produces the recursion's per-directory reversal.
 */
public class LinkerTest {
  @TempDir java.nio.file.Path tempDir;

  private Location location(String path) {
    return Location.get(tempDir.resolve(path).toString());
  }

  @Test
  public void usedProtoPathFileIncludedInSchema() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "import \"b.proto\";\n"
        + "message A {\n"
        + "  optional B b = 1;\n"
        + "}\n");
    add(tempDir, "proto-path/b.proto", ""
        + "message B {\n"
        + "}\n");
    Schema schema = loadAndLinkSchema();

    List<Location> locations = new ArrayList<>();
    for (ProtoFile protoFile : schema.getProtoFiles()) {
      locations.add(protoFile.getLocation());
    }
    assertEquals(Arrays.asList(
        Location.get(tempDir.resolve("source-path").toString(), "a.proto"),
        Location.get(tempDir.resolve("proto-path").toString(), "b.proto"),
        Location.get("google/protobuf/descriptor.proto"),
        Location.get("wire/extensions.proto")),
        locations);
  }

  @Test public void opaqueMessageDeclaredField() throws IOException {
    add(tempDir, "source-path/cafe/cafe.proto", ""
        + "syntax = \"proto2\";\n"
        + "\n"
        + "package cafe;\n"
        + "\n"
        + "message CafeDrink {\n"
        + "  optional int32 size_ounces = 1;\n"
        + "  repeated EspressoShot shots = 2;\n"
        + "}\n"
        + "\n"
        + "message EspressoShot {\n"
        + "  optional Roast roast = 1;\n"
        + "  optional bool decaf = 2;\n"
        + "}\n"
        + "\n"
        + "enum Roast {\n"
        + "  MEDIUM = 1;\n"
        + "  DARK = 2;\n"
        + "}\n");
    Schema schema = loadAndLinkSchema(ProtoType.get("cafe.EspressoShot"));
    assertEquals(ProtoType.BYTES,
        ((MessageType) schema.getType("cafe.CafeDrink")).field("shots").getType());
    assertEquals(""
        + "// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: cafe/cafe.proto\n"
        + "\n"
        + "syntax = \"proto2\";\n"
        + "\n"
        + "package cafe;\n"
        + "\n"
        + "message CafeDrink {\n"
        + "  optional int32 size_ounces = 1;\n"
        + "\n"
        + "  repeated bytes shots = 2;\n"
        + "}\n"
        + "\n"
        + "message EspressoShot {\n"
        + "  optional Roast roast = 1;\n"
        + "\n"
        + "  optional bool decaf = 2;\n"
        + "}\n"
        + "\n"
        + "enum Roast {\n"
        + "  MEDIUM = 1;\n"
        + "  DARK = 2;\n"
        + "}\n",
        schema.protoFile("cafe/cafe.proto").toSchema());
  }

  @Test public void enumsCannotBeOpaqued() throws IOException {
    add(tempDir, "source-path/cafe/cafe.proto", ""
        + "syntax = \"proto2\";\n"
        + "\n"
        + "package cafe;\n"
        + "\n"
        + "message CafeDrink {\n"
        + "  optional int32 size_ounces = 1;\n"
        + "  repeated EspressoShot shots = 2;\n"
        + "}\n"
        + "\n"
        + "message EspressoShot {\n"
        + "  optional Roast roast = 1;\n"
        + "  optional bool decaf = 2;\n"
        + "}\n"
        + "\n"
        + "enum Roast {\n"
        + "  MEDIUM = 1;\n"
        + "  DARK = 2;\n"
        + "}\n");
    SchemaException e = assertThrows(SchemaException.class,
        () -> loadAndLinkSchema(ProtoType.get("cafe.Roast")));
    assertTrue(e.getMessage().contains(""
        + "Enums like cafe.Roast cannot be opaqued\n"
        + "  for field roast (" + location("source-path") + "/cafe/cafe.proto:11:3)\n"
        + "  in message cafe.EspressoShot (" + location("source-path") + "/cafe/cafe.proto:10:1)"),
        e.getMessage());
  }

  @Test public void escapedSequenceInStringLiterals() throws IOException {
    add(tempDir, "source-path/cafe/cafe.proto", ""
        + "syntax = \"proto2\";\n"
        + "\n"
        + "package cafe;\n"
        + "\n"
        + "option php_namespace = \"\\\"\\\\\\a\\b\\f\\n\\r\\t\\v\";\n"
        + "\n");
    Schema schema = loadAndLinkSchema();
    assertEquals(""
        + "// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: cafe/cafe.proto\n"
        + "\n"
        + "syntax = \"proto2\";\n"
        + "\n"
        + "package cafe;\n"
        + "\n"
        + "option php_namespace = \"\\\"\\\\\\a\\b\\f\\n\\r\\t\\v\";\n",
        schema.protoFile("cafe/cafe.proto").toSchema());
  }

  @Test public void opaqueExtensionField() throws IOException {
    add(tempDir, "source-path/cafe/cafe.proto", ""
        + "syntax = \"proto2\";\n"
        + "\n"
        + "package cafe;\n"
        + "\n"
        + "message CafeDrink {\n"
        + "  optional int32 size_ounces = 1;\n"
        + "}\n"
        + "\n"
        + "extend CafeDrink {\n"
        + "  repeated EspressoShot shots = 2;\n"
        + "}\n"
        + "\n"
        + "message EspressoShot {\n"
        + "  optional Roast roast = 1;\n"
        + "  optional bool decaf = 2;\n"
        + "}\n"
        + "\n"
        + "enum Roast {\n"
        + "  MEDIUM = 1;\n"
        + "  DARK = 2;\n"
        + "}\n");
    Schema schema = loadAndLinkSchema(ProtoType.get("cafe.EspressoShot"));
    assertEquals(ProtoType.BYTES,
        ((MessageType) schema.getType("cafe.CafeDrink")).extensionField("cafe.shots").getType());
    assertEquals(""
        + "// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: cafe/cafe.proto\n"
        + "\n"
        + "syntax = \"proto2\";\n"
        + "\n"
        + "package cafe;\n"
        + "\n"
        + "message CafeDrink {\n"
        + "  optional int32 size_ounces = 1;\n"
        + "}\n"
        + "\n"
        + "message EspressoShot {\n"
        + "  optional Roast roast = 1;\n"
        + "\n"
        + "  optional bool decaf = 2;\n"
        + "}\n"
        + "\n"
        + "enum Roast {\n"
        + "  MEDIUM = 1;\n"
        + "  DARK = 2;\n"
        + "}\n"
        + "\n"
        + "extend CafeDrink {\n"
        + "  repeated bytes shots = 2;\n"
        + "}\n",
        schema.protoFile("cafe/cafe.proto").toSchema());
  }

  @Test public void opaqueMultipleFields() throws IOException {
    add(tempDir, "source-path/cafe/cafe.proto", ""
        + "syntax = \"proto2\";\n"
        + "\n"
        + "package cafe;\n"
        + "\n"
        + "message CafeDrink {\n"
        + "  optional int32 size_ounces = 1;\n"
        + "  repeated EspressoShot shots = 2;\n"
        + "}\n"
        + "\n"
        + "message EspressoShot {\n"
        + "  optional Roast roast = 1;\n"
        + "  optional bool decaf = 2;\n"
        + "}\n"
        + "\n"
        + "message Roast {\n"
        + "  optional int32 id = 1;\n"
        + "  optional string name = 2;\n"
        + "}\n");
    Schema schema = loadAndLinkSchema(ProtoType.get("cafe.EspressoShot"), ProtoType.get("cafe.Roast"));
    assertEquals(ProtoType.BYTES,
        ((MessageType) schema.getType("cafe.CafeDrink")).field("shots").getType());
    assertEquals(ProtoType.BYTES,
        ((MessageType) schema.getType("cafe.EspressoShot")).field("roast").getType());
    assertEquals(""
        + "// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: cafe/cafe.proto\n"
        + "\n"
        + "syntax = \"proto2\";\n"
        + "\n"
        + "package cafe;\n"
        + "\n"
        + "message CafeDrink {\n"
        + "  optional int32 size_ounces = 1;\n"
        + "\n"
        + "  repeated bytes shots = 2;\n"
        + "}\n"
        + "\n"
        + "message EspressoShot {\n"
        + "  optional bytes roast = 1;\n"
        + "\n"
        + "  optional bool decaf = 2;\n"
        + "}\n"
        + "\n"
        + "message Roast {\n"
        + "  optional int32 id = 1;\n"
        + "\n"
        + "  optional string name = 2;\n"
        + "}\n",
        schema.protoFile("cafe/cafe.proto").toSchema());
  }

  @Test public void opaqueScalarTypeThrows() throws IOException {
    add(tempDir, "source-path/cafe/cafe.proto", ""
        + "syntax = \"proto2\";\n"
        + "\n"
        + "package cafe;\n"
        + "\n"
        + "message CafeDrink {\n"
        + "  optional int32 size_ounces = 1;\n"
        + "}\n");

    SchemaException e = assertThrows(SchemaException.class,
        () -> loadAndLinkSchema(ProtoType.INT32));
    assertTrue(e.getMessage().contains(""
        + "Scalar types like int32 cannot be opaqued\n"
        + "  for field size_ounces (" + location("source-path") + "/cafe/cafe.proto:6:3)\n"
        + "  in message cafe.CafeDrink (" + location("source-path") + "/cafe/cafe.proto:5:1)"),
        e.getMessage());
  }

  @Test
  public void unusedProtoPathFileExcludedFromSchema() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "import \"b.proto\";\n"
        + "message A {\n"
        + "}\n");
    add(tempDir, "proto-path/b.proto", ""
        + "message B {\n"
        + "}\n");
    Schema schema = loadAndLinkSchema();

    List<Location> locations = new ArrayList<>();
    for (ProtoFile protoFile : schema.getProtoFiles()) {
      locations.add(protoFile.getLocation());
    }
    assertEquals(Arrays.asList(
        Location.get(tempDir.resolve("source-path").toString(), "a.proto"),
        Location.get("google/protobuf/descriptor.proto"),
        Location.get("wire/extensions.proto")),
        locations);
  }

  @Test
  public void onlyProtoPathTypesAreIncludedInSchema() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "import \"b.proto\";\n"
        + "message A {\n"
        + "  optional B b = 1;\n"
        + "}\n");
    add(tempDir, "proto-path/b.proto", ""
        + "message B {\n"
        + "}\n"
        + "message C {\n"
        + "}\n");
    Schema schema = loadAndLinkSchema();

    assertNotNull(schema.getType("B"));
    assertNull(schema.getType("C"));
  }

  @Test
  public void protoPathMembersAreIncludedInSchemaIfTheyAreUsedInOptions() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "import \"formatting_options.proto\";\n"
        + "message A {\n"
        + "  optional string s = 1 [formatting_options.language.name = \"English\"];\n"
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
        + "}\n");
    Schema schema = loadAndLinkSchema();

    assertNotNull(schema.getType("FormattingOptions"));
    assertNotNull(schema.getType("Language"));
    assertNotNull(schema.getType("StringCasing"));

    assertNotNull(schema.getField("FormattingOptions", "language"));
    assertNotNull(schema.getField("FormattingOptions", "string_casing"));
    assertNotNull(schema.getField("Language", "name"));

    ProtoType fieldOptionsType = ProtoType.get("google.protobuf", "FieldOptions");
    assertNotNull(schema.getField(fieldOptionsType, "formatting_options"));
  }

  @Test
  public void protoPathMembersAreNotIncludedInSchemaIfTheyAreNotUsedInOptions() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "import \"b.proto\";\n"
        + "message A {\n"
        + "  optional B b = 1;\n"
        + "}\n");
    add(tempDir, "proto-path/b.proto", ""
        + "message B {\n"
        + "  optional C c = 1;\n"
        + "}\n"
        + "message C {\n"
        + "}\n");
    Schema schema = loadAndLinkSchema();

    assertNotNull(schema.getType("B"));
    assertNull(schema.getType("C"));
    assertNull(schema.getField("B", "c"));
  }

  @Test
  public void javaPackageIsSetOnProtoPathFiles() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "import \"b.proto\";\n"
        + "message A {\n"
        + "  optional B b = 1;\n"
        + "}\n");
    add(tempDir, "proto-path/b.proto", ""
        + "option java_package = \"com.squareup.b\";\n"
        + "message B {\n"
        + "}\n");
    Schema schema = loadAndLinkSchema();

    assertEquals("com.squareup.b", schema.protoFile("b.proto").javaPackage());
  }

  @Test
  public void descriptorProtoIsLinked() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "import \"google/protobuf/descriptor.proto\";\n"
        + "\n"
        + "enum Roshambo {\n"
        + "  ROCK = 1 [deprecated = true];\n"
        + "  SCISSORS = 2;\n"
        + "  PAPER = 3;\n"
        + "}\n");
    Schema schema = loadAndLinkSchema();

    Field enumValueDeprecated = schema.getField(Options.ENUM_VALUE_OPTIONS, "deprecated");
    assertNotNull(enumValueDeprecated.getEncodeMode());
  }

  @Test
  public void schemaIsDeterministicEvenIfProtoPathOrderIsNot() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "message A {\n"
        + "}\n");
    add(tempDir, "source-path/b.proto", ""
        + "import \"a.proto\";\n"
        + "extend A {\n"
        + "  optional string b = 1;\n"
        + "}\n");
    add(tempDir, "source-path/c.proto", ""
        + "import \"a.proto\";\n"
        + "extend A {\n"
        + "  optional string c = 2;\n"
        + "}\n");

    Schema schemaSorted = loadAndLinkSchema();
    List<String> sortedNames = new ArrayList<>();
    for (Field field : ((MessageType) schemaSorted.getType("A")).getExtensionFields()) {
      sortedNames.add(field.getName());
    }
    assertEquals(Arrays.asList("b", "c"), sortedNames);

    Schema schemaReversed = loadAndLinkSchemaReverseSort();
    List<String> reversedNames = new ArrayList<>();
    for (Field field : ((MessageType) schemaReversed.getType("A")).getExtensionFields()) {
      reversedNames.add(field.getName());
    }
    assertEquals(Arrays.asList("b", "c"), reversedNames);
  }

  private Schema loadAndLinkSchema(ProtoType... opaqueTypes) throws IOException {
    return loadAndLinkSchema(false, opaqueTypes);
  }

  private Schema loadAndLinkSchemaReverseSort(ProtoType... opaqueTypes) throws IOException {
    return loadAndLinkSchema(true, opaqueTypes);
  }

  private Schema loadAndLinkSchema(boolean reverseSort, ProtoType... opaqueTypes)
      throws IOException {
    okio.FileSystem schemaLoaderFileSystem = reverseSort
        ? new ForwardingFileSystem(okio.FileSystem.SYSTEM) {
          // Upstream reverses listRecursively; the vendored okio keeps it final, so this
          // reverses each directory listing the recursion consumes instead.
          @Override public List<Path> list(Path dir) throws IOException {
            List<Path> list = new ArrayList<>(super.list(dir));
            Collections.reverse(list);
            return list;
          }
        }
        : okio.FileSystem.SYSTEM;

    // Upstream's FakeFileSystem fixture pre-creates the proto-path directory; the loader
    // rejects roots that do not exist, so the same directory is materialized here.
    Files.createDirectories(tempDir.resolve("proto-path"));
    SchemaLoader loader = new SchemaLoader(schemaLoaderFileSystem);
    loader.setOpaqueTypes(Arrays.asList(opaqueTypes));
    loader.initRoots(
        Collections.singletonList(Location.get(tempDir.resolve("source-path").toString())),
        Collections.singletonList(Location.get(tempDir.resolve("proto-path").toString())));
    return loader.loadSchema();
  }
}
