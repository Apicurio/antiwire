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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.wire.FieldMask;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.SchemaBuilder;
import com.squareup.wire.schema.internal.JvmLanguages;
import java.util.Arrays;
import java.util.Collections;
import okio.ByteString;
import okio.Path;
import org.junit.jupiter.api.Test;

/**
 * TASK-17 security regression corpus, schema-module items. One named case per inventory row in
 * docs/security-regression-inventory.md; each case fails when its defect is reintroduced.
 */
public class SchemaSecurityCorpusTest {

  // ---------------------------------------------------------------- literal validation (#3633)

  /**
   * Invalid scalar and enum literals in proto2 default values are rejected during schema linking.
   * The hostile values smuggle Java declarations through the default that generated code would
   * otherwise emit verbatim.
   */
  @Test public void issue3633_invalidDefaultValuesRejected() {
    SchemaBuilder builder = new SchemaBuilder();
    builder.add("defaults.proto", ""
        + "syntax = \"proto2\";\n"
        + "\n"
        + "message Defaults {\n"
        + "  optional bool bool_field = 1 [default = \"false; static { } //\"];\n"
        + "  optional float float_field = 2 [default = \"0.0f; init { } //\"];\n"
        + "  optional double double_field = 3 [default = \"0.0d; static { } //\"];\n"
        + "  optional Choice enum_field = 4 [default = \"ONE; static { } //\"];\n"
        + "\n"
        + "  enum Choice {\n"
        + "    ONE = 0;\n"
        + "  }\n"
        + "}\n");

    SchemaException expected = assertThrows(SchemaException.class, builder::build);
    String message = builder.normalizeLocations(expected.getMessage());
    assertTrue(message.contains("invalid default value \"false; static { } //\" for bool"),
        message);
    assertTrue(message.contains("invalid default value \"0.0f; init { } //\" for float"),
        message);
    assertTrue(message.contains("invalid default value \"0.0d; static { } //\" for double"),
        message);
    assertTrue(message.contains("invalid default value \"ONE; static { } //\" for Defaults.Choice"),
        message);
  }

  /**
   * The same validation covers option values linked against extension-declared option types; a
   * crafted option value cannot smuggle a literal into generated code either.
   */
  @Test public void issue3633_invalidOptionValuesRejected() {
    SchemaBuilder builder = new SchemaBuilder();
    builder.add("a.proto", ""
        + "import \"formatting_options.proto\";\n"
        + "\n"
        + "message A {\n"
        + "  option (message_options).enabled = \"false; static { } //\";\n"
        + "  optional string s = 1 [\n"
        + "    (formatting_options).max = \"80; static { } //\",\n"
        + "    (formatting_options).casing = \"LOWER_CASE; static { } //\"\n"
        + "  ];\n"
        + "}\n");
    builder.addProtoPath("formatting_options.proto", ""
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

    SchemaException expected = assertThrows(SchemaException.class, builder::build);
    String message = builder.normalizeLocations(expected.getMessage());
    assertTrue(message.contains("invalid option value \"false; static { } //\" for bool"),
        message);
    assertTrue(message.contains("invalid option value \"80; static { } //\" for int32"),
        message);
    assertTrue(
        message.contains("invalid option value \"LOWER_CASE; static { } //\" for StringCasing"),
        message);
  }

  // ------------------------------------------------- output path containment (#3657)

  /**
   * A generated path that resolves outside the configured output directory must be refused. A
   * package option that is an absolute path replaces the whole path when joined, so the file would
   * land outside the build output.
   */
  @Test public void issue3657_absolutePathCannotEscapeOutDirectory() {
    SchemaHandlerTest.TestSchemaHandler schemaHandler = new SchemaHandlerTest.TestSchemaHandler();

    assertThrows(IllegalArgumentException.class,
        () -> schemaHandler.checkGeneratedPath(
            Path.get("/etc/cron.d/Message.java"), Path.get("build/generated")));
  }

  /** A package option traversing upward with {@code ..} must be refused the same way. */
  @Test public void issue3657_dotDotTraversalCannotEscapeOutDirectory() {
    SchemaHandlerTest.TestSchemaHandler schemaHandler = new SchemaHandlerTest.TestSchemaHandler();

    assertThrows(IllegalArgumentException.class,
        () -> schemaHandler.checkGeneratedPath(
            Path.get("build/generated/../../evil/Message.java"), Path.get("build/generated")));
  }

  /** A path inside the output directory still passes; the check must not over-reject. */
  @Test public void issue3657_pathInsideOutDirectoryIsAccepted() {
    SchemaHandlerTest.TestSchemaHandler schemaHandler = new SchemaHandlerTest.TestSchemaHandler();

    schemaHandler.checkGeneratedPath(
        Path.get("build/generated/com/example/Message.java"), Path.get("build/generated"));
  }

  // ------------------------------------------------- package option validation (#3718)

  /**
   * A {@code java_package} value carrying a character that ends or escapes a declaration must be
   * rejected with an error naming the option, because Wire emits the value into generated code as
   * written.
   */
  @Test public void issue3718_javaPackageOptionValueRejected() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto2\";\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "option java_package = \"com.example; static { } //\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string name = 1;\n"
            + "}\n")
        .build();

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JvmLanguages.javaPackage(schema.protoFile("message.proto")));
    String message = e.getMessage();
    assertTrue(message.contains("Refusing to use a package option value"), message);
    assertTrue(message.contains("option:    java_package"), message);
  }

  /** {@code wire.wire_package} values are validated by the same rule. */
  @Test public void issue3718_wirePackageOptionValueRejected() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto2\";\n"
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "option (wire.wire_package) = \"com/example\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string name = 1;\n"
            + "}\n")
        .build();

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JvmLanguages.javaPackage(schema.protoFile("message.proto")));
    assertTrue(e.getMessage().contains("option:    wire.wire_package"), e.getMessage());
  }

  /** A well-formed package option is returned unchanged; the check must not over-reject. */
  @Test public void issue3718_validJavaPackageAccepted() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto2\";\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "option java_package = \"com.example.generated\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string name = 1;\n"
            + "}\n")
        .build();

    assertEquals("com.example.generated",
        JvmLanguages.javaPackage(schema.protoFile("message.proto")));
  }

  // ------------------------------------------------- duplicate singular merge (#3652, #3656)

  /**
   * The dynamic schema adapter merges duplicate occurrences of a singular FieldMask field per the
   * protobuf specification instead of keeping the last occurrence.
   */
  @Test public void issue3652_schemaAdapterMergesDuplicateSingularFieldMask() throws Exception {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto3\";\n"
            + "import \"google/protobuf/field_mask.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  google.protobuf.FieldMask field_mask_field = 1;\n"
            + "}\n")
        .build();

    ProtoAdapter<Object> adapter = schema.protoAdapter("Message", true);
    // Field 1 twice: FieldMask { paths: ["a"] } then FieldMask { paths: ["b"] }.
    ByteString encoded = ByteString.decodeHex("0a030a01610a030a0162");

    assertEquals(Collections.singletonMap("field_mask_field", new FieldMask(Arrays.asList("a", "b"))),
        adapter.decode(encoded));
  }

  /**
   * In a oneof, repeated occurrences of the same message member merge, and a later occurrence of a
   * different member clears the earlier one, leaving exactly one member set.
   */
  @Test public void issue3656_schemaAdapterOneofLastMemberWinsAndSameMemberMerges() throws Exception {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto3\";\n"
            + "import \"google/protobuf/field_mask.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  oneof choice {\n"
            + "    google.protobuf.FieldMask field_mask_field = 1;\n"
            + "    string name = 2;\n"
            + "  }\n"
            + "}\n")
        .build();

    ProtoAdapter<Object> adapter = schema.protoAdapter("Message", true);

    // Same member twice: merge.
    assertEquals(Collections.singletonMap("field_mask_field", new FieldMask(Arrays.asList("a", "b"))),
        adapter.decode(ByteString.decodeHex("0a030a01610a030a0162")));
    // name first, then the mask: the later oneof member wins and merges.
    assertEquals(Collections.singletonMap("field_mask_field", new FieldMask(Arrays.asList("b"))),
        adapter.decode(ByteString.decodeHex("0a030a01611201780a030a0162")));
  }
}
