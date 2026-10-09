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

import static com.squareup.wire.schema.Field.Label.OPTIONAL;
import static com.squareup.wire.schema.Field.Label.REPEATED;
import static com.squareup.wire.schema.Field.Label.REQUIRED;
import static com.squareup.wire.schema.internal.SchemaUtil.MAX_TAG_VALUE;
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.BOOLEAN;
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.ENUM;
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.LIST;
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.MAP;
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.NUMBER;
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.OPTION;
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.STRING;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.wire.schema.Field.Label;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.EnumConstantElement;
import com.squareup.wire.schema.internal.parser.EnumElement;
import com.squareup.wire.schema.internal.parser.ExtendElement;
import com.squareup.wire.schema.internal.parser.ExtensionsElement;
import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.GroupElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.OneOfElement;
import com.squareup.wire.schema.internal.parser.OptionElement;
import com.squareup.wire.schema.internal.parser.OptionElement.OptionPrimitive;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import com.squareup.wire.schema.internal.parser.ReservedElement;
import com.squareup.wire.schema.internal.parser.RpcElement;
import com.squareup.wire.schema.internal.parser.ServiceElement;
import com.squareup.wire.schema.internal.parser.TypeElement;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * TASK-13 full adoption of upstream
 * {@code wire-schema/src/commonTest/kotlin/com/squareup/wire/schema/internal/parser/ProtoParserTest.kt}
 * at square/wire tag 7.1.0. Every upstream case keeps its name, inputs, expected values, and
 * expected error messages, including exact {@code toSchema}-independent element-model equality.
 * assertk maps onto JUnit 5: {@code isEqualTo} becomes {@code assertEquals(expected, actual)},
 * {@code hasMessage} becomes an exact message assertion, {@code messageContains} and boolean
 * {@code assertThat} become {@code assertTrue}, and {@code assertFails}/{@code assertFailsWith}
 * plus {@code fail()} become {@code assertThrows} (IllegalStateException for the parser's
 * {@code error()}/{@code expect()} paths). Upstream named-argument element constructors become
 * positional calls through the factories at the bottom of this class; per conventions document
 * rule R1 every omitted argument is supplied with the upstream default value from the pinned
 * declarations. Range-bearing cases use {@link #assertFilesEqual}, explained at that method.
 */
public class ProtoParserFullTest {
  private final Location location = Location.get("file.proto");

  private static final List<OptionElement> NO_OPTIONS = Collections.emptyList();
  private static final List<TypeElement> NO_TYPES = Collections.emptyList();
  private static final List<FieldElement> NO_FIELDS = Collections.emptyList();
  private static final List<OneOfElement> NO_ONEOFS = Collections.emptyList();
  private static final List<ExtensionsElement> NO_EXTENSIONS = Collections.emptyList();
  private static final List<GroupElement> NO_GROUPS = Collections.emptyList();
  private static final List<ExtendElement> NO_EXTENDS = Collections.emptyList();
  private static final List<ReservedElement> NO_RESERVEDS = Collections.emptyList();
  private static final List<EnumConstantElement> NO_CONSTANTS = Collections.emptyList();
  private static final List<RpcElement> NO_RPCS = Collections.emptyList();
  private static final List<ServiceElement> NO_SERVICES = Collections.emptyList();
  private static final List<String> NO_STRINGS = Collections.emptyList();

  @Test public void typeParsing() {
    String proto = ""
        + "message Types {\n"
        + "  required any f1 = 1;\n"
        + "  required bool f2 = 2;\n"
        + "  required bytes f3 = 3;\n"
        + "  required double f4 = 4;\n"
        + "  required float f5 = 5;\n"
        + "  required fixed32 f6 = 6;\n"
        + "  required fixed64 f7 = 7;\n"
        + "  required int32 f8 = 8;\n"
        + "  required int64 f9 = 9;\n"
        + "  required sfixed32 f10 = 10;\n"
        + "  required sfixed64 f11 = 11;\n"
        + "  required sint32 f12 = 12;\n"
        + "  required sint64 f13 = 13;\n"
        + "  required string f14 = 14;\n"
        + "  required uint32 f15 = 15;\n"
        + "  required uint64 f16 = 16;\n"
        + "  map<string, bool> f17 = 17;\n"
        + "  map<arbitrary, nested.nested> f18 = 18;\n"
        + "  required arbitrary f19 = 19;\n"
        + "  required nested.nested f20 = 20;\n"
        + "}\n";

    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "Types", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Arrays.asList(
                field(location.at(2, 3), REQUIRED, "any", "f1", 1),
                field(location.at(3, 3), REQUIRED, "bool", "f2", 2),
                field(location.at(4, 3), REQUIRED, "bytes", "f3", 3),
                field(location.at(5, 3), REQUIRED, "double", "f4", 4),
                field(location.at(6, 3), REQUIRED, "float", "f5", 5),
                field(location.at(7, 3), REQUIRED, "fixed32", "f6", 6),
                field(location.at(8, 3), REQUIRED, "fixed64", "f7", 7),
                field(location.at(9, 3), REQUIRED, "int32", "f8", 8),
                field(location.at(10, 3), REQUIRED, "int64", "f9", 9),
                field(location.at(11, 3), REQUIRED, "sfixed32", "f10", 10),
                field(location.at(12, 3), REQUIRED, "sfixed64", "f11", 11),
                field(location.at(13, 3), REQUIRED, "sint32", "f12", 12),
                field(location.at(14, 3), REQUIRED, "sint64", "f13", 13),
                field(location.at(15, 3), REQUIRED, "string", "f14", 14),
                field(location.at(16, 3), REQUIRED, "uint32", "f15", 15),
                field(location.at(17, 3), REQUIRED, "uint64", "f16", 16),
                field(location.at(18, 3), null, "map<string, bool>", "f17", 17),
                field(location.at(19, 3), null, "map<arbitrary, nested.nested>", "f18", 18),
                field(location.at(20, 3), REQUIRED, "arbitrary", "f19", 19),
                field(location.at(21, 3), REQUIRED, "nested.nested", "f20", 20)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));

    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void mapWithLabelThrows() {
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, "message Hey { required map<string, string> a = 1; }"));
    assertEquals("Syntax error in file.proto:1:15: 'map' type cannot have label", e.getMessage());

    e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, "message Hey { optional map<string, string> a = 1; }"));
    assertEquals("Syntax error in file.proto:1:15: 'map' type cannot have label", e.getMessage());

    e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, "message Hey { repeated map<string, string> a = 1; }"));
    assertEquals("Syntax error in file.proto:1:15: 'map' type cannot have label", e.getMessage());
  }

  /** It looks like an option, but 'default' is special. It's not defined as an option. */
  @Test public void defaultFieldOptionIsSpecial() {
    String proto = ""
        + "message Message {\n"
        + "  required string a = 1 [default = \"b\", faulted = \"c\"];\n"
        + "}\n";

    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "Message", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(2, 3), REQUIRED, "string", "a", "b", null, 1, "",
                    Collections.singletonList(OptionElement.create("faulted", STRING, "c")))),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  /** It looks like an option, but 'json_name' is special. It's not defined as an option. */
  @Test public void jsonNameOptionIsSpecial() {
    String proto = ""
        + "message Message {\n"
        + "  required string a = 1 [json_name = \"b\", faulted = \"c\"];\n"
        + "}\n";

    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "Message", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(2, 3), REQUIRED, "string", "a", null, "b", 1, "",
                    Collections.singletonList(OptionElement.create("faulted", STRING, "c")))),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void singleLineComment() {
    String proto = ""
        + "// Test all the things!\n"
        + "message Test {}\n";
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    TypeElement type = parsed.getTypes().get(0);
    assertEquals("Test all the things!", type.getDocumentation());
  }

  @Test public void multipleSingleLineComments() {
    String proto = ""
        + "// Test all\n"
        + "// the things!\n"
        + "message Test {}\n";
    String expected = ""
        + "Test all\n"
        + "the things!";

    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    TypeElement type = parsed.getTypes().get(0);
    assertEquals(expected, type.getDocumentation());
  }

  @Test public void singleLineJavadocComment() {
    String proto = ""
        + "/** Test */\n"
        + "message Test {}\n";
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    TypeElement type = parsed.getTypes().get(0);
    assertEquals("Test", type.getDocumentation());
  }

  @Test public void multilineJavadocComment() {
    String proto = ""
        + "/**\n"
        + " * Test\n"
        + " *\n"
        + " * Foo\n"
        + " */\n"
        + "message Test {}\n";
    String expected = ""
        + "Test\n"
        + "\n"
        + "Foo";
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    TypeElement type = parsed.getTypes().get(0);
    assertEquals(expected, type.getDocumentation());
  }

  @Test public void multipleSingleLineCommentsWithLeadingWhitespace() {
    String proto = ""
        + "// Test\n"
        + "//   All\n"
        + "//     The\n"
        + "//       Things!\n"
        + "message Test {}\n";
    String expected = ""
        + "Test\n"
        + "  All\n"
        + "    The\n"
        + "      Things!";
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    TypeElement type = parsed.getTypes().get(0);
    assertEquals(expected, type.getDocumentation());
  }

  @Test public void multilineJavadocCommentWithLeadingWhitespace() {
    String proto = ""
        + "/**\n"
        + " * Test\n"
        + " *   All\n"
        + " *     The\n"
        + " *       Things!\n"
        + " */\n"
        + "message Test {}\n";
    String expected = ""
        + "Test\n"
        + "  All\n"
        + "    The\n"
        + "      Things!";
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    TypeElement type = parsed.getTypes().get(0);
    assertEquals(expected, type.getDocumentation());
  }

  @Test public void multilineJavadocCommentWithoutLeadingAsterisks() {
    // We do not honor leading whitespace when the comment lacks leading asterisks.
    String proto = ""
        + "/**\n"
        + " Test\n"
        + "   All\n"
        + "     The\n"
        + "       Things!\n"
        + " */\n"
        + "message Test {}\n";
    String expected = ""
        + "Test\n"
        + "All\n"
        + "The\n"
        + "Things!";
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    TypeElement type = parsed.getTypes().get(0);
    assertEquals(expected, type.getDocumentation());
  }

  @Test public void messageFieldTrailingCommentWithCarriageReturn() {
    // Trailing message field comment.
    String proto = (""
        + "message Test {\n"
        + "  optional string name = 1; // Test all the things!\n"
        + "}\n").replace("\n", "\r\n");
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    MessageElement message = (MessageElement) parsed.getTypes().get(0);
    FieldElement field = message.getFields().get(0);
    assertEquals("Test all the things!", field.getDocumentation());
  }

  @Test public void messageFieldLeadingAndTrailingCommentAreCombined() {
    String proto = ""
        + "message Test {\n"
        + "  // Test all...\n"
        + "  optional string name = 1; // ...the things!\n"
        + "}\n";
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    MessageElement message = (MessageElement) parsed.getTypes().get(0);
    FieldElement field = message.getFields().get(0);
    assertEquals("Test all...\n...the things!", field.getDocumentation());
  }

  @Test public void trailingCommentNotAssignedToFollowingField() {
    String proto = ""
        + "message Test {\n"
        + "  optional string first_name = 1; // Testing!\n"
        + "  optional string last_name = 2;\n"
        + "}\n";
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    MessageElement message = (MessageElement) parsed.getTypes().get(0);
    FieldElement field1 = message.getFields().get(0);
    assertEquals("Testing!", field1.getDocumentation());
    FieldElement field2 = message.getFields().get(1);
    assertEquals("", field2.getDocumentation());
  }

  @Test public void enumValueTrailingComment() {
    String proto = ""
        + "enum Test {\n"
        + "  FOO = 1; // Test all the things!\n"
        + "}\n";
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    EnumElement enumElement = (EnumElement) parsed.getTypes().get(0);
    EnumConstantElement value = enumElement.getConstants().get(0);
    assertEquals("Test all the things!", value.getDocumentation());
  }

  @Test public void trailingSinglelineComment() {
    String proto = ""
        + "enum Test {\n"
        + "  FOO = 1; /* Test all the things!  */\n"
        + "  BAR = 2;/*Test all the things!*/\n"
        + "}\n";
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    EnumElement enumElement = (EnumElement) parsed.getTypes().get(0);
    EnumConstantElement foo = enumElement.getConstants().get(0);
    assertEquals("Test all the things!", foo.getDocumentation());
    EnumConstantElement bar = enumElement.getConstants().get(1);
    assertEquals("Test all the things!", bar.getDocumentation());
  }

  @Test public void trailingMultilineComment() {
    String proto = ""
        + "enum Test {\n"
        + "  FOO = 1; /* Test all the\n"
        + "things! */\n"
        + "}\n";
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    EnumElement enumElement = (EnumElement) parsed.getTypes().get(0);
    EnumConstantElement value = enumElement.getConstants().get(0);
    assertEquals("Test all the\nthings!", value.getDocumentation());
  }

  @Test public void trailingMultilineCommentMustBeLastOnLineThrows() {
    String proto = ""
        + "enum Test {\n"
        + "  FOO = 1; /* Test all the things! */ BAR = 2;\n"
        + "}\n";
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, proto));
    assertEquals("Syntax error in file.proto:2:40: no syntax may follow trailing comment",
        e.getMessage());
  }

  @Test public void fieldCannotStartWithDigits() {
    String proto = ""
        + "message Test {\n"
        + "  optional string 3DS = 1;\n"
        + "}\n";
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, proto));
    assertEquals(
        "Syntax error in file.proto:2:18: field and constant names cannot start with a digit",
        e.getMessage());
  }

  @Test public void errorOnMultilinedDefinedTypes() {
    String proto = ""
        + "message Test {\n"
        + "  optional a.b.LookalikeExpansionLevelEnum\n"
        + "      .LookalikeExpansionLevel 3DS = 1;\n"
        + "}\n";
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, proto));
    assertEquals(
        "Syntax error in file.proto:3:31: field and constant names cannot start with a digit",
        e.getMessage());
  }

  @Test public void constantCannotStartWithDigits() {
    String proto = ""
        + "enum Test {\n"
        + "  3DS = 1;\n"
        + "}\n";
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, proto));
    assertEquals(
        "Syntax error in file.proto:2:2: field and constant names cannot start with a digit",
        e.getMessage());
  }

  @Test public void invalidTrailingComment() {
    String proto = ""
        + "enum Test {\n"
        + "  FOO = 1; /\n"
        + "}\n";
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, proto));
    assertEquals("Syntax error in file.proto:2:13: expected '//' or '/*'", e.getMessage());
  }

  @Test public void enumValueLeadingAndTrailingCommentsAreCombined() {
    String proto = ""
        + "enum Test {\n"
        + "  // Test all...\n"
        + "  FOO = 1; // ...the things!\n"
        + "}\n";
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    EnumElement enumElement = (EnumElement) parsed.getTypes().get(0);
    EnumConstantElement value = enumElement.getConstants().get(0);
    assertEquals("Test all...\n...the things!", value.getDocumentation());
  }

  @Test public void trailingCommentNotCombinedWhenEmpty() {
    // Can't use raw strings here; otherwise, the formatter removes the trailing whitespace on line 3.
    String proto = "enum Test {\n"
        + "  // Test all...\n"
        + "  FOO = 1; //       \n"
        + "}";
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    EnumElement enumElement = (EnumElement) parsed.getTypes().get(0);
    EnumConstantElement value = enumElement.getConstants().get(0);
    assertEquals("Test all...", value.getDocumentation());
  }

  @Test public void syntaxNotRequired() {
    String proto = "message Foo {}";
    ProtoFileElement parsed = ProtoParser.parse(location, proto);
    assertNull(parsed.getSyntax());
  }

  @Test public void syntaxSpecified() {
    String proto = ""
        + "syntax = \"proto3\";\n"
        + "message Foo {}\n";
    ProtoFileElement expected = protoFile(Syntax.PROTO_3,
        Collections.singletonList(message(location.at(2, 1), "Foo")));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void invalidSyntaxValueThrows() {
    String proto = ""
        + "syntax = \"proto4\";\n"
        + "message Foo {}\n";
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, proto));
    assertEquals("Syntax error in file.proto:1:1: unexpected syntax: proto4", e.getMessage());
  }

  @Test public void syntaxNotFirstDeclarationThrows() {
    String proto = ""
        + "message Foo {}\n"
        + "syntax = \"proto3\";\n";
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, proto));
    assertEquals(
        "Syntax error in file.proto:2:1: 'syntax' element must be the first declaration in a file",
        e.getMessage());
  }

  @Test public void syntaxMayFollowCommentsAndEmptyLines() {
    String proto = ""
        + "/* comment 1 */\n"
        + "// comment 2\n"
        + "\n"
        + "syntax = \"proto3\";\n"
        + "message Foo {}\n";
    ProtoFileElement expected = protoFile(Syntax.PROTO_3,
        Collections.singletonList(message(location.at(5, 1), "Foo")));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void proto3MessageFieldsDoNotRequireLabels() {
    String proto = ""
        + "syntax = \"proto3\";\n"
        + "message Message {\n"
        + "  string a = 1;\n"
        + "  int32 b = 2;\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Syntax.PROTO_3, Collections.singletonList(
        message(location.at(2, 1), "Message", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Arrays.asList(
                field(location.at(3, 3), null, "string", "a", 1),
                field(location.at(4, 3), null, "int32", "b", 2)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void proto3ExtensionFieldsDoNotRequireLabels() {
    String proto = ""
        + "syntax = \"proto3\";\n"
        + "message Message {\n"
        + "}\n"
        + "extend Message {\n"
        + "  string a = 1;\n"
        + "  int32 b = 2;\n"
        + "}\n";
    ProtoFileElement expected = protoFile(null, Syntax.PROTO_3, NO_STRINGS, NO_STRINGS,
        NO_STRINGS,
        Collections.singletonList(message(location.at(2, 1), "Message")),
        NO_SERVICES,
        Collections.singletonList(extendElement(location.at(4, 1), "Message", "",
            Arrays.asList(
                field(location.at(5, 3), null, "string", "a", 1),
                field(location.at(6, 3), null, "int32", "b", 2)))),
        NO_OPTIONS);
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void proto3MessageFieldsAllowOptional() {
    String proto = ""
        + "syntax = \"proto3\";\n"
        + "message Message {\n"
        + "  optional string a = 1;\n"
        + "}\n";

    ProtoFileElement expected = protoFile(Syntax.PROTO_3, Collections.singletonList(
        message(location.at(2, 1), "Message", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(field(location.at(3, 3), OPTIONAL, "string", "a", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void proto3MessageFieldsForbidRequired() {
    String proto = ""
        + "syntax = \"proto3\";\n"
        + "message Message {\n"
        + "  required string a = 1;\n"
        + "}\n";
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, proto));
    assertEquals(
        "Syntax error in file.proto:3:3: 'required' label forbidden in proto3 field declarations",
        e.getMessage());
  }

  @Test public void proto3ExtensionFieldsAllowOptional() {
    String proto = ""
        + "syntax = \"proto3\";\n"
        + "message Message {\n"
        + "}\n"
        + "extend Message {\n"
        + "  optional string a = 1;\n"
        + "}\n";
    ProtoFileElement expected = protoFile(null, Syntax.PROTO_3, NO_STRINGS, NO_STRINGS,
        NO_STRINGS,
        Collections.singletonList(message(location.at(2, 1), "Message")),
        NO_SERVICES,
        Collections.singletonList(extendElement(location.at(4, 1), "Message", "",
            Collections.singletonList(
                field(location.at(5, 3), OPTIONAL, "string", "a", 1)))),
        NO_OPTIONS);
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void proto3ExtensionFieldsForbidsRequired() {
    String proto = ""
        + "syntax = \"proto3\";\n"
        + "message Message {\n"
        + "}\n"
        + "extend Message {\n"
        + "  required string a = 1;\n"
        + "}\n";
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, proto));
    assertEquals(
        "Syntax error in file.proto:5:3: 'required' label forbidden in proto3 field declarations",
        e.getMessage());
  }

  @Test public void proto3MessageFieldsPermitRepeated() {
    String proto = ""
        + "syntax = \"proto3\";\n"
        + "message Message {\n"
        + "  repeated string a = 1;\n"
        + "}\n";

    ProtoFileElement expected = protoFile(Syntax.PROTO_3, Collections.singletonList(
        message(location.at(2, 1), "Message", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(field(location.at(3, 3), REPEATED, "string", "a", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void proto3ExtensionFieldsPermitRepeated() {
    String proto = ""
        + "syntax = \"proto3\";\n"
        + "message Message {\n"
        + "}\n"
        + "extend Message {\n"
        + "  repeated string a = 1;\n"
        + "}\n";
    ProtoFileElement expected = protoFile(null, Syntax.PROTO_3, NO_STRINGS, NO_STRINGS,
        NO_STRINGS,
        Collections.singletonList(message(location.at(2, 1), "Message")),
        NO_SERVICES,
        Collections.singletonList(extendElement(location.at(4, 1), "Message", "",
            Collections.singletonList(
                field(location.at(5, 3), REPEATED, "string", "a", 1)))),
        NO_OPTIONS);
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void parseMessageAndFields() {
    String proto = ""
        + "message SearchRequest {\n"
        + "  required string query = 1;\n"
        + "  optional int32 page_number = 2;\n"
        + "  optional int32 result_per_page = 3;\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "SearchRequest", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Arrays.asList(
                field(location.at(2, 3), REQUIRED, "string", "query", 1),
                field(location.at(3, 3), OPTIONAL, "int32", "page_number", 2),
                field(location.at(4, 3), OPTIONAL, "int32", "result_per_page", 3)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void group() {
    String proto = ""
        + "message SearchResponse {\n"
        + "  repeated group Result = 1 {\n"
        + "    required string url = 2;\n"
        + "    optional string title = 3;\n"
        + "    repeated string snippets = 4;\n"
        + "  }\n"
        + "}\n";
    MessageElement message = message(location.at(1, 1), "SearchResponse", "", NO_TYPES,
        NO_OPTIONS, NO_RESERVEDS, NO_FIELDS, NO_ONEOFS, NO_EXTENSIONS,
        Collections.singletonList(
            groupElement(REPEATED, location.at(2, 3), "Result", 1, "",
                Arrays.asList(
                    field(location.at(3, 5), REQUIRED, "string", "url", 2),
                    field(location.at(4, 5), OPTIONAL, "string", "title", 3),
                    field(location.at(5, 5), REPEATED, "string", "snippets", 4)))),
        NO_EXTENDS);
    ProtoFileElement expected = protoFile(Collections.singletonList(message));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void parseMessageAndOneOf() {
    String proto = ""
        + "message SearchRequest {\n"
        + "  required string query = 1;\n"
        + "  oneof page_info {\n"
        + "    int32 page_number = 2;\n"
        + "    int32 result_per_page = 3;\n"
        + "  }\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "SearchRequest", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(field(location.at(2, 3), REQUIRED, "string", "query", 1)),
            Collections.singletonList(
                oneOfElement("page_info", "",
                    Arrays.asList(
                        field(location.at(4, 5), null, "int32", "page_number", 2),
                        field(location.at(5, 5), null, "int32", "result_per_page", 3)),
                    NO_GROUPS, NO_OPTIONS, location.at(3, 3))),
            NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void parseMessageAndOneOfWithGroup() {
    String proto = ""
        + "message SearchRequest {\n"
        + "  required string query = 1;\n"
        + "  oneof page_info {\n"
        + "    int32 page_number = 2;\n"
        + "    group Stuff = 3 {\n"
        + "      optional int32 result_per_page = 4;\n"
        + "      optional int32 page_count = 5;\n"
        + "    }\n"
        + "  }\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "SearchRequest", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(field(location.at(2, 3), REQUIRED, "string", "query", 1)),
            Collections.singletonList(
                oneOfElement("page_info", "",
                    Collections.singletonList(
                        field(location.at(4, 5), null, "int32", "page_number", 2)),
                    Collections.singletonList(
                        groupElement(null, location.at(5, 5), "Stuff", 3, "",
                            Arrays.asList(
                                field(location.at(6, 7), OPTIONAL, "int32",
                                    "result_per_page", 4),
                                field(location.at(7, 7), OPTIONAL, "int32", "page_count", 5)))),
                    NO_OPTIONS, location.at(3, 3))),
            NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void parseEnum() {
    String proto = ""
        + "/**\n"
        + " * What's on my waffles.\n"
        + " * Also works on pancakes.\n"
        + " */\n"
        + "enum Topping {\n"
        + "  FRUIT = 1;\n"
        + "  /** Yummy, yummy cream. */\n"
        + "  CREAM = 2;\n"
        + "\n"
        + "  // Quebec Maple syrup\n"
        + "  SYRUP = 3;\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        enumElement(location.at(5, 1), "Topping", "What's on my waffles.\nAlso works on pancakes.",
            NO_OPTIONS,
            Arrays.asList(
                constant(location.at(6, 3), "FRUIT", 1),
                constant(location.at(8, 3), "CREAM", 2, "Yummy, yummy cream.", NO_OPTIONS),
                constant(location.at(11, 3), "SYRUP", 3, "Quebec Maple syrup", NO_OPTIONS)),
            NO_RESERVEDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void parseEnumWithOptions() {
    String proto = ""
        + "/**\n"
        + " * What's on my waffles.\n"
        + " * Also works on pancakes.\n"
        + " */\n"
        + "enum Topping {\n"
        + "  option(max_choices) = 2;\n"
        + "\n"
        + "  FRUIT = 1[(healthy) = true];\n"
        + "  /** Yummy, yummy cream. */\n"
        + "  CREAM = 2;\n"
        + "\n"
        + "  // Quebec Maple syrup\n"
        + "  SYRUP = 3;\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        enumElement(location.at(5, 1), "Topping", "What's on my waffles.\nAlso works on pancakes.",
            Collections.singletonList(OptionElement.create("max_choices", NUMBER, "2", true)),
            Arrays.asList(
                constant(location.at(8, 3), "FRUIT", 1, "",
                    Collections.singletonList(
                        OptionElement.create("healthy", BOOLEAN, "true", true))),
                constant(location.at(10, 3), "CREAM", 2, "Yummy, yummy cream.", NO_OPTIONS),
                constant(location.at(13, 3), "SYRUP", 3, "Quebec Maple syrup", NO_OPTIONS)),
            NO_RESERVEDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void packageDeclaration() {
    String proto = ""
        + "package google.protobuf;\n"
        + "option java_package = \"com.google.protobuf\";\n"
        + "\n"
        + "// The protocol compiler can output a FileDescriptorSet containing the .proto\n"
        + "// files it parses.\n"
        + "message FileDescriptorSet {\n"
        + "}\n";
    ProtoFileElement expected = protoFile("google.protobuf", null, NO_STRINGS, NO_STRINGS,
        NO_STRINGS,
        Collections.singletonList(
            message(location.at(6, 1), "FileDescriptorSet",
                "The protocol compiler can output a FileDescriptorSet containing the .proto\n"
                    + "files it parses.",
                NO_TYPES, NO_OPTIONS, NO_RESERVEDS, NO_FIELDS, NO_ONEOFS, NO_EXTENSIONS,
                NO_GROUPS, NO_EXTENDS)),
        NO_SERVICES, NO_EXTENDS,
        Collections.singletonList(
            OptionElement.create("java_package", STRING, "com.google.protobuf")));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  // TASK-13 adaptation: upstream asserts whole-file equality. Reserved and extension ranges
  // are Kotlin IntRanges upstream (structural equality) but int[] in the port, whose equals
  // is identity, so files containing ranges cannot satisfy whole-element assertEquals;
  // assertFilesEqual below asserts everything through element equality except those values.
  @Test public void nestingInMessage() {
    String proto = ""
        + "message FieldOptions {\n"
        + "  optional CType ctype = 1[old_default = STRING, deprecated = true];\n"
        + "  enum CType {\n"
        + "    STRING = 0[(opt_a) = 1, (opt_b) = 2];\n"
        + "  };\n"
        + "  // Clients can define custom options in extensions of this message. See above.\n"
        + "  extensions 500;\n"
        + "  extensions 1000 to max;\n"
        + "}\n";
    EnumElement enumElement = enumElement(location.at(3, 3), "CType", "", NO_OPTIONS,
        Collections.singletonList(
            constant(location.at(4, 5), "STRING", 0, "",
                Arrays.asList(
                    OptionElement.create("opt_a", NUMBER, "1", true),
                    OptionElement.create("opt_b", NUMBER, "2", true)))),
        NO_RESERVEDS);
    FieldElement field = field(location.at(2, 3), OPTIONAL, "CType", "ctype", null, null, 1, "",
        Arrays.asList(
            OptionElement.create("old_default", ENUM, "STRING"),
            OptionElement.create("deprecated", BOOLEAN, "true")));
    assertEquals(
        Arrays.asList(
            OptionElement.create("old_default", ENUM, "STRING"),
            OptionElement.create("deprecated", BOOLEAN, "true")),
        field.getOptions());

    MessageElement messageElement = message(location.at(1, 1), "FieldOptions", "",
        Collections.singletonList(enumElement), NO_OPTIONS, NO_RESERVEDS,
        Collections.singletonList(field), NO_ONEOFS,
        Arrays.asList(
            extensionsElement(location.at(7, 3),
                "Clients can define custom options in extensions of this message. See above.",
                Collections.singletonList(500), NO_OPTIONS),
            extensionsElement(location.at(8, 3), "",
                Collections.singletonList(new int[] {1000, MAX_TAG_VALUE}), NO_OPTIONS)),
        NO_GROUPS, NO_EXTENDS);
    ProtoFileElement expected = protoFile(Collections.singletonList(messageElement));
    assertFilesEqual(expected, ProtoParser.parse(location, proto));
  }

  // TASK-13 adaptation: ranges in values lists; see nestingInMessage.
  @Test public void multiRangesExtensions() {
    String proto = ""
        + "message MeGustaExtensions {\n"
        + "  extensions 1, 5 to 200, 500, 1000 to max;\n"
        + "}\n";
    MessageElement messageElement = message(location.at(1, 1), "MeGustaExtensions", "",
        NO_TYPES, NO_OPTIONS, NO_RESERVEDS, NO_FIELDS, NO_ONEOFS,
        Collections.singletonList(
            extensionsElement(location.at(2, 3), "",
                Arrays.<Object>asList(1, new int[] {5, 200}, 500,
                    new int[] {1000, MAX_TAG_VALUE}),
                NO_OPTIONS)),
        NO_GROUPS, NO_EXTENDS);
    ProtoFileElement expected = protoFile(Collections.singletonList(messageElement));
    assertFilesEqual(expected, ProtoParser.parse(location, proto));
  }

  @Test public void optionParentheses() {
    String proto = ""
        + "message Chickens {\n"
        + "  optional bool koka_ko_koka_ko = 1[old_default = true];\n"
        + "  optional bool coodle_doodle_do = 2[(delay) = 100, old_default = false];\n"
        + "  optional bool coo_coo_ca_cha = 3[old_default = true, (delay) = 200];\n"
        + "  optional bool cha_chee_cha = 4;\n"
        + "}\n";

    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "Chickens", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Arrays.asList(
                field(location.at(2, 3), OPTIONAL, "bool", "koka_ko_koka_ko", 1,
                    Collections.singletonList(
                        OptionElement.create("old_default", BOOLEAN, "true"))),
                field(location.at(3, 3), OPTIONAL, "bool", "coodle_doodle_do", 2,
                    Arrays.asList(
                        OptionElement.create("delay", NUMBER, "100", true),
                        OptionElement.create("old_default", BOOLEAN, "false"))),
                field(location.at(4, 3), OPTIONAL, "bool", "coo_coo_ca_cha", 3,
                    Arrays.asList(
                        OptionElement.create("old_default", BOOLEAN, "true"),
                        OptionElement.create("delay", NUMBER, "200", true))),
                field(location.at(5, 3), OPTIONAL, "bool", "cha_chee_cha", 4)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void imports() {
    String proto = "import \"src/test/resources/unittest_import.proto\";\n";
    ProtoFileElement expected = protoFile(null, null,
        Collections.singletonList("src/test/resources/unittest_import.proto"),
        NO_STRINGS, NO_STRINGS, NO_TYPES, NO_SERVICES, NO_EXTENDS, NO_OPTIONS);
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void publicImports() {
    String proto = "import public \"src/test/resources/unittest_import.proto\";\n";
    ProtoFileElement expected = protoFile(null, null, NO_STRINGS,
        Collections.singletonList("src/test/resources/unittest_import.proto"),
        NO_STRINGS, NO_TYPES, NO_SERVICES, NO_EXTENDS, NO_OPTIONS);
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void unquotedImportThrows() {
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, "import src/test/resources/unittest_import.proto;\n"));
    assertEquals("Syntax error in file.proto:1:1: expected quoted string", e.getMessage());
  }

  @Test public void unquotedWeakImportThrows() {
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location,
            "import weak src/test/resources/unittest_import.proto;\n"));
    assertEquals("Syntax error in file.proto:1:14: expected quoted string", e.getMessage());
  }

  @Test public void extend() {
    String proto = ""
        + "// Extends Foo\n"
        + "extend Foo {\n"
        + "  optional int32 bar = 126;\n"
        + "}\n";
    ProtoFileElement expected = protoFile(null, null, NO_STRINGS, NO_STRINGS, NO_STRINGS,
        NO_TYPES, NO_SERVICES,
        Collections.singletonList(
            extendElement(location.at(2, 1), "Foo", "Extends Foo",
                Collections.singletonList(
                    field(location.at(3, 3), OPTIONAL, "int32", "bar", 126)))),
        NO_OPTIONS);
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void extendInMessage() {
    String proto = ""
        + "message Bar {\n"
        + "  extend Foo {\n"
        + "    optional Bar bar = 126;\n"
        + "  }\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "Bar", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS, NO_FIELDS,
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS,
            Collections.singletonList(
                extendElement(location.at(2, 3), "Foo", "",
                    Collections.singletonList(
                        field(location.at(3, 5), OPTIONAL, "Bar", "bar", 126)))))));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void extendInMessageWithPackage() {
    String proto = ""
        + "package kit.kat;\n"
        + "\n"
        + "message Bar {\n"
        + "  extend Foo {\n"
        + "    optional Bar bar = 126;\n"
        + "  }\n"
        + "}\n";
    ProtoFileElement expected = protoFile("kit.kat", null, NO_STRINGS, NO_STRINGS, NO_STRINGS,
        Collections.singletonList(
            message(location.at(3, 1), "Bar", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS, NO_FIELDS,
                NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS,
                Collections.singletonList(
                    extendElement(location.at(4, 3), "Foo", "",
                        Collections.singletonList(
                            field(location.at(5, 5), OPTIONAL, "Bar", "bar", 126)))))),
        NO_SERVICES, NO_EXTENDS, NO_OPTIONS);
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void fqcnExtendInMessage() {
    String proto = ""
        + "message Bar {\n"
        + "  extend example.Foo {\n"
        + "    optional Bar bar = 126;\n"
        + "  }\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "Bar", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS, NO_FIELDS,
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS,
            Collections.singletonList(
                extendElement(location.at(2, 3), "example.Foo", "",
                    Collections.singletonList(
                        field(location.at(3, 5), OPTIONAL, "Bar", "bar", 126)))))));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void fqcnExtendInMessageWithPackage() {
    String proto = ""
        + "package kit.kat;\n"
        + "\n"
        + "message Bar {\n"
        + "  extend example.Foo {\n"
        + "    optional Bar bar = 126;\n"
        + "  }\n"
        + "}\n";
    ProtoFileElement expected = protoFile("kit.kat", null, NO_STRINGS, NO_STRINGS, NO_STRINGS,
        Collections.singletonList(
            message(location.at(3, 1), "Bar", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS, NO_FIELDS,
                NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS,
                Collections.singletonList(
                    extendElement(location.at(4, 3), "example.Foo", "",
                        Collections.singletonList(
                            field(location.at(5, 5), OPTIONAL, "Bar", "bar", 126)))))),
        NO_SERVICES, NO_EXTENDS, NO_OPTIONS);
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void defaultFieldWithParen() {
    String proto = ""
        + "message Foo {\n"
        + "  optional string claim_token = 2[(squareup.redacted) = true];\n"
        + "}\n";
    FieldElement field = field(location.at(2, 3), OPTIONAL, "string", "claim_token", null, null,
        2, "",
        Collections.singletonList(
            OptionElement.create("squareup.redacted", BOOLEAN, "true", true)));
    assertEquals(
        Collections.singletonList(
            OptionElement.create("squareup.redacted", BOOLEAN, "true", true)),
        field.getOptions());

    MessageElement messageElement = message(location.at(1, 1), "Foo", "", NO_TYPES, NO_OPTIONS,
        NO_RESERVEDS, Collections.singletonList(field), NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS,
        NO_EXTENDS);
    ProtoFileElement expected = protoFile(Collections.singletonList(messageElement));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  // Parse \a, \b, \f, \n, \r, \t, \v, \[0-7]{1-3}, and \[xX]{0-9a-fA-F]{1,2}
  @Test public void defaultFieldWithStringEscapes() {
    String proto = ""
        + "message Foo {\n"
        + "  optional string name = 1 [\n"
        + "    x = \"\\a\\b\\f\\n\\r\\t\\v\\1f\\01\\001\\11\\011\\111\\xe\\Xe\\xE\\xE\\x41\\X41\"\n"
        + "  ];\n"
        + "}\n";
    FieldElement field = field(location.at(2, 3), OPTIONAL, "string", "name", null, null, 1, "",
        Collections.singletonList(
            OptionElement.create("x", STRING,
                "\u0007\b\u000C\n\r\t\u000b\u0001f\u0001\u0001\u0009\u0009I\u000e\u000e\u000e"
                    + "\u000eAA")));
    assertEquals(
        Collections.singletonList(
            OptionElement.create("x", STRING,
                "\u0007\b\u000C\n\r\t\u000b\u0001f\u0001\u0001\u0009\u0009I\u000e\u000e\u000e"
                    + "\u000eAA")),
        field.getOptions());

    MessageElement messageElement = message(location.at(1, 1), "Foo", "", NO_TYPES, NO_OPTIONS,
        NO_RESERVEDS, Collections.singletonList(field), NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS,
        NO_EXTENDS);
    ProtoFileElement expected = protoFile(Collections.singletonList(messageElement));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void stringWithSingleQuotes() {
    String proto = ""
        + "message Foo {\n"
        + "  optional string name = 1[default = 'single\\\"quotes'];\n"
        + "}\n";

    FieldElement field = field(location.at(2, 3), OPTIONAL, "string", "name",
        "single\"quotes", null, 1, "", NO_OPTIONS);
    MessageElement messageElement = message(location.at(1, 1), "Foo", "", NO_TYPES, NO_OPTIONS,
        NO_RESERVEDS, Collections.singletonList(field), NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS,
        NO_EXTENDS);
    ProtoFileElement expected = protoFile(Collections.singletonList(messageElement));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void adjacentStringsConcatenated() {
    String proto = ""
        + "message Foo {\n"
        + "  optional string name = 1 [\n"
        + "    default = \"concat \"\n"
        + "              'these '\n"
        + "              \"please\"\n"
        + "  ];\n"
        + "}\n";

    FieldElement field = field(location.at(2, 3), OPTIONAL, "string", "name",
        "concat these please", null, 1, "", NO_OPTIONS);
    MessageElement messageElement = message(location.at(1, 1), "Foo", "", NO_TYPES, NO_OPTIONS,
        NO_RESERVEDS, Collections.singletonList(field), NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS,
        NO_EXTENDS);
    ProtoFileElement expected = protoFile(Collections.singletonList(messageElement));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void invalidHexStringEscape() {
    String proto = ""
        + "message Foo {\n"
        + "  optional string name = 1 [default = \"\\xW\"];\n"
        + "}\n";
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, proto));
    assertTrue(e.getMessage().contains("expected a digit after \\x or \\X"));
  }

  @Test public void service() {
    String proto = ""
        + "service SearchService {\n"
        + "  option (default_timeout) = 30;\n"
        + "\n"
        + "  rpc Search (SearchRequest) returns (SearchResponse);\n"
        + "  rpc Purchase (PurchaseRequest) returns (PurchaseResponse) {\n"
        + "    option (squareup.sake.timeout) = 15;\n"
        + "    option (squareup.a.b) = {\n"
        + "      value: [\n"
        + "        FOO,\n"
        + "        BAR\n"
        + "      ]\n"
        + "    };\n"
        + "  }\n"
        + "}\n";
    Map<String, Object> squareupABValue = new LinkedHashMap<>();
    squareupABValue.put("value", Arrays.asList(
        new OptionPrimitive(ENUM, "FOO"),
        new OptionPrimitive(ENUM, "BAR")));
    ProtoFileElement expected = protoFile(null, null, NO_STRINGS, NO_STRINGS, NO_STRINGS,
        NO_TYPES,
        Collections.singletonList(
            serviceElement(location.at(1, 1), "SearchService", "",
                Arrays.asList(
                    rpcElement(location.at(4, 3), "Search", "", "SearchRequest",
                        "SearchResponse", false, false, NO_OPTIONS),
                    rpcElement(location.at(5, 3), "Purchase", "", "PurchaseRequest",
                        "PurchaseResponse", false, false,
                        Arrays.asList(
                            OptionElement.create("squareup.sake.timeout", NUMBER, "15", true),
                            OptionElement.create("squareup.a.b", MAP, squareupABValue, true)))),
                Collections.singletonList(
                    OptionElement.create("default_timeout", NUMBER, "30", true)))),
        NO_EXTENDS, NO_OPTIONS);
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void streamingService() {
    String proto = ""
        + "service RouteGuide {\n"
        + "  rpc GetFeature (Point) returns (Feature) {}\n"
        + "  rpc ListFeatures (Rectangle) returns (stream Feature) {}\n"
        + "  rpc RecordRoute (stream Point) returns (RouteSummary) {}\n"
        + "  rpc RouteChat (stream RouteNote) returns (stream RouteNote) {}\n"
        + "}\n";
    ProtoFileElement expected = protoFile(null, null, NO_STRINGS, NO_STRINGS, NO_STRINGS,
        NO_TYPES,
        Collections.singletonList(
            serviceElement(location.at(1, 1), "RouteGuide", "",
                Arrays.asList(
                    rpcElement(location.at(2, 3), "GetFeature", "", "Point", "Feature",
                        false, false, NO_OPTIONS),
                    rpcElement(location.at(3, 3), "ListFeatures", "", "Rectangle", "Feature",
                        false, true, NO_OPTIONS),
                    rpcElement(location.at(4, 3), "RecordRoute", "", "Point", "RouteSummary",
                        true, false, NO_OPTIONS),
                    rpcElement(location.at(5, 3), "RouteChat", "", "RouteNote", "RouteNote",
                        true, true, NO_OPTIONS)),
                NO_OPTIONS)),
        NO_EXTENDS, NO_OPTIONS);
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void hexTag() {
    String proto = ""
        + "message HexTag {\n"
        + "  required string hex = 0x10;\n"
        + "  required string uppercase_x_hex = 0X11;\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "HexTag", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Arrays.asList(
                field(location.at(2, 3), REQUIRED, "string", "hex", 16),
                field(location.at(3, 3), REQUIRED, "string", "uppercase_x_hex", 17)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void structuredOption() {
    String proto = ""
        + "message ExoticOptions {\n"
        + "  option (squareup.one) = {name: \"Name\", class_name:\"ClassName\"};\n"
        + "  option (squareup.two.a) = {[squareup.options.type]: EXOTIC};\n"
        + "  option (squareup.two.b) = {names: [\"Foo\", \"Bar\"]};\n"
        + "  option (squareup.three) = {x: {y: 1 y: 2 } }; // NOTE: Omitted optional comma\n"
        + "  option (squareup.four) = {x: {y: {z: 1 }, y: {z: 2 }}};\n"
        + "}\n";

    Map<String, Object> optionOneMap = new LinkedHashMap<>();
    optionOneMap.put("name", "Name");
    optionOneMap.put("class_name", "ClassName");
    Map<String, Object> optionTwoAMap = new LinkedHashMap<>();
    optionTwoAMap.put("[squareup.options.type]", new OptionPrimitive(ENUM, "EXOTIC"));
    Map<String, Object> optionTwoBMap = new LinkedHashMap<>();
    optionTwoBMap.put("names", Arrays.asList("Foo", "Bar"));
    Map<String, Object> optionThreeMap = new LinkedHashMap<>();
    Map<String, Object> optionThreeNestedMap = new LinkedHashMap<>();
    optionThreeNestedMap.put("y", Arrays.asList(
        new OptionPrimitive(NUMBER, "1"),
        new OptionPrimitive(NUMBER, "2")));
    optionThreeMap.put("x", optionThreeNestedMap);

    Map<String, Object> optionFourMap = new LinkedHashMap<>();
    Map<String, Object> optionFourMap1 = new LinkedHashMap<>();
    Map<String, Object> optionFourMap2A = new LinkedHashMap<>();
    optionFourMap2A.put("z", new OptionPrimitive(NUMBER, "1"));
    Map<String, Object> optionFourMap2B = new LinkedHashMap<>();
    optionFourMap2B.put("z", new OptionPrimitive(NUMBER, "2"));
    optionFourMap1.put("y", Arrays.asList(optionFourMap2A, optionFourMap2B));
    optionFourMap.put("x", optionFourMap1);

    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "ExoticOptions", "", NO_TYPES,
            Arrays.asList(
                OptionElement.create("squareup.one", MAP, optionOneMap, true),
                OptionElement.create("squareup.two.a", MAP, optionTwoAMap, true),
                OptionElement.create("squareup.two.b", MAP, optionTwoBMap, true),
                OptionElement.create("squareup.three", MAP, optionThreeMap, true),
                OptionElement.create("squareup.four", MAP, optionFourMap, true)),
            NO_RESERVEDS, NO_FIELDS, NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void optionsWithNestedMapsAndTrailingCommas() {
    String proto = ""
        + "message StructuredOption {\n"
        + "    optional field.type has_options = 3 [\n"
        + "            (option_map) = {\n"
        + "                nested_map: {key:\"value\", key2:[\"value2a\",\"value2b\"]},\n"
        + "             },\n"
        + "            (option_string) = [\"string1\",\"string2\"]\n"
        + "    ];\n"
        + "}\n";
    Map<String, Object> nestedMap = new LinkedHashMap<>();
    nestedMap.put("key", "value");
    nestedMap.put("key2", Arrays.asList("value2a", "value2b"));
    Map<String, Object> optionMapValue = new LinkedHashMap<>();
    optionMapValue.put("nested_map", nestedMap);
    FieldElement field = field(location.at(2, 5), OPTIONAL, "field.type", "has_options", null,
        null, 3, "",
        Arrays.asList(
            OptionElement.create("option_map", MAP, optionMapValue, true),
            OptionElement.create("option_string", LIST,
                Arrays.asList("string1", "string2"), true)));
    assertEquals(
        Arrays.asList(
            OptionElement.create("option_map", MAP, optionMapValue, true),
            OptionElement.create("option_string", LIST,
                Arrays.asList("string1", "string2"), true)),
        field.getOptions());

    MessageElement expected = message(location.at(1, 1), "StructuredOption", "", NO_TYPES,
        NO_OPTIONS, NO_RESERVEDS, Collections.singletonList(field), NO_ONEOFS, NO_EXTENSIONS,
        NO_GROUPS, NO_EXTENDS);
    ProtoFileElement protoFile = protoFile(Collections.singletonList(expected));
    assertEquals(protoFile, ProtoParser.parse(location, proto));
  }

  @Test public void optionNumericalBounds() {
    String proto = ""
        + "message Test {\n"
        + "  optional int32 default_int32 = 401 [x = 2147483647];\n"
        + "  optional uint32 default_uint32 = 402 [x = 4294967295];\n"
        + "  optional sint32 default_sint32 = 403 [x = -2147483648];\n"
        + "  optional fixed32 default_fixed32 = 404 [x = 4294967295];\n"
        + "  optional sfixed32 default_sfixed32 = 405 [x = -2147483648];\n"
        + "  optional int64 default_int64 = 406 [x = 9223372036854775807];\n"
        + "  optional uint64 default_uint64 = 407 [x = 18446744073709551615];\n"
        + "  optional sint64 default_sint64 = 408 [x = -9223372036854775808];\n"
        + "  optional fixed64 default_fixed64 = 409 [x = 18446744073709551615];\n"
        + "  optional sfixed64 default_sfixed64 = 410 [x = -9223372036854775808];\n"
        + "  optional bool default_bool = 411 [x = true];\n"
        + "  optional float default_float = 412 [x = 123.456e7];\n"
        + "  optional double default_double = 413 [x = 123.456e78];\n"
        + "  optional string default_string = 414 [x = \"\u00e7ok\\a\\b\\f\\n\\r\\t\\v\\1\\01"
        + "\\001\\17\\017\\176\\x1\\x01\\x11\\X1\\X01\\X11g\u00fczel\" ];\n"
        + "  optional bytes default_bytes = 415 [x = \"\u00e7ok\\a\\b\\f\\n\\r\\t\\v\\1\\01"
        + "\\001\\17\\017\\176\\x1\\x01\\x11\\X1\\X01\\X11g\u00fczel\" ];\n"
        + "  optional NestedEnum default_nested_enum = 416 [x = A ];\n"
        + "}\n";
    String escapes =
        "\u00e7ok\u0007\b\u000C\n\r\t\u000b\u0001\u0001\u0001\u000f\u000f~\u0001\u0001\u0011"
            + "\u0001\u0001\u0011g\u00fczel";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "Test", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Arrays.asList(
                field(location.at(2, 3), OPTIONAL, "int32", "default_int32", null, null, 401, "",
                    Collections.singletonList(
                        OptionElement.create("x", NUMBER, "2147483647"))),
                field(location.at(3, 3), OPTIONAL, "uint32", "default_uint32", null, null, 402,
                    "", Collections.singletonList(
                        OptionElement.create("x", NUMBER, "4294967295"))),
                field(location.at(4, 3), OPTIONAL, "sint32", "default_sint32", null, null, 403,
                    "", Collections.singletonList(
                        OptionElement.create("x", NUMBER, "-2147483648"))),
                field(location.at(5, 3), OPTIONAL, "fixed32", "default_fixed32", null, null, 404,
                    "", Collections.singletonList(
                        OptionElement.create("x", NUMBER, "4294967295"))),
                field(location.at(6, 3), OPTIONAL, "sfixed32", "default_sfixed32", null, null,
                    405, "", Collections.singletonList(
                        OptionElement.create("x", NUMBER, "-2147483648"))),
                field(location.at(7, 3), OPTIONAL, "int64", "default_int64", null, null, 406, "",
                    Collections.singletonList(
                        OptionElement.create("x", NUMBER, "9223372036854775807"))),
                field(location.at(8, 3), OPTIONAL, "uint64", "default_uint64", null, null, 407,
                    "", Collections.singletonList(
                        OptionElement.create("x", NUMBER, "18446744073709551615"))),
                field(location.at(9, 3), OPTIONAL, "sint64", "default_sint64", null, null, 408,
                    "", Collections.singletonList(
                        OptionElement.create("x", NUMBER, "-9223372036854775808"))),
                field(location.at(10, 3), OPTIONAL, "fixed64", "default_fixed64", null, null,
                    409, "", Collections.singletonList(
                        OptionElement.create("x", NUMBER, "18446744073709551615"))),
                field(location.at(11, 3), OPTIONAL, "sfixed64", "default_sfixed64", null, null,
                    410, "", Collections.singletonList(
                        OptionElement.create("x", NUMBER, "-9223372036854775808"))),
                field(location.at(12, 3), OPTIONAL, "bool", "default_bool", null, null, 411, "",
                    Collections.singletonList(
                        OptionElement.create("x", BOOLEAN, "true"))),
                field(location.at(13, 3), OPTIONAL, "float", "default_float", null, null, 412,
                    "", Collections.singletonList(
                        OptionElement.create("x", NUMBER, "123.456e7"))),
                field(location.at(14, 3), OPTIONAL, "double", "default_double", null, null, 413,
                    "", Collections.singletonList(
                        OptionElement.create("x", NUMBER, "123.456e78"))),
                field(location.at(15, 3), OPTIONAL, "string", "default_string", null, null, 414,
                    "", Collections.singletonList(
                        OptionElement.create("x", STRING, escapes))),
                field(location.at(17, 3), OPTIONAL, "bytes", "default_bytes", null, null, 415,
                    "", Collections.singletonList(
                        OptionElement.create("x", STRING, escapes))),
                field(location.at(19, 3), OPTIONAL, "NestedEnum", "default_nested_enum", null,
                    null, 416, "", Collections.singletonList(
                        OptionElement.create("x", ENUM, "A")))),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void extensionWithNestedMessage() {
    String proto = ""
        + "message Foo {\n"
        + "  optional int32 bar = 1[\n"
        + "      (validation.range).min = 1,\n"
        + "      (validation.range).max = 100,\n"
        + "      old_default = 20\n"
        + "  ];\n"
        + "}\n";
    FieldElement field = field(location.at(2, 3), OPTIONAL, "int32", "bar", null, null, 1, "",
        Arrays.asList(
            OptionElement.create("validation.range", OPTION,
                OptionElement.create("min", NUMBER, "1"), true),
            OptionElement.create("validation.range", OPTION,
                OptionElement.create("max", NUMBER, "100"), true),
            OptionElement.create("old_default", NUMBER, "20")));
    assertEquals(
        Arrays.asList(
            OptionElement.create("validation.range", OPTION,
                OptionElement.create("min", NUMBER, "1"), true),
            OptionElement.create("validation.range", OPTION,
                OptionElement.create("max", NUMBER, "100"), true),
            OptionElement.create("old_default", NUMBER, "20")),
        field.getOptions());

    MessageElement expected = message(location.at(1, 1), "Foo", "", NO_TYPES, NO_OPTIONS,
        NO_RESERVEDS, Collections.singletonList(field), NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS,
        NO_EXTENDS);
    ProtoFileElement protoFile = protoFile(Collections.singletonList(expected));
    assertEquals(protoFile, ProtoParser.parse(location, proto));
  }

  // TASK-13 adaptation: ranges in values lists; see nestingInMessage.
  @Test public void reservedMessage() {
    String proto = ""
        + "message Foo {\n"
        + "  reserved 10, 12 to 14, 23 to max, 'foo', \"bar\";\n"
        + "}\n";
    MessageElement message = message(location.at(1, 1), "Foo", "", NO_TYPES, NO_OPTIONS,
        Collections.singletonList(
            reservedElement(location.at(2, 3), "",
                Arrays.<Object>asList(10, new int[] {12, 14}, new int[] {23, MAX_TAG_VALUE},
                    "foo", "bar"))),
        NO_FIELDS, NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS);
    ProtoFileElement expected = protoFile(Collections.singletonList(message));
    assertFilesEqual(expected, ProtoParser.parse(location, proto));
  }

  // Reserved are not supported yet for enums, this test asserts that we don't crash parsing them.
  // See https://github.com/square/wire/issues/797
  // TASK-13 adaptation: ranges in values lists; see nestingInMessage.
  @Test public void reservedEnum() {
    String proto = ""
        + "enum Foo {\n"
        + "  reserved 10, 12 to 14, 23 to max, 'FOO', \"BAR\";\n"
        + "  reserved 3;\n"
        + "}\n";
    EnumElement message = enumElement(location.at(1, 1), "Foo", "", NO_OPTIONS, NO_CONSTANTS,
        Arrays.asList(
            reservedElement(location.at(2, 3), "",
                Arrays.<Object>asList(10, new int[] {12, 14}, new int[] {23, MAX_TAG_VALUE},
                    "FOO", "BAR")),
            reservedElement(location.at(3, 3), "",
                Collections.singletonList(3))));
    ProtoFileElement expected = protoFile(Collections.singletonList(message));
    assertFilesEqual(expected, ProtoParser.parse(location, proto));
  }

  @Test public void reservedWithComments() {
    String proto = ""
        + "message Foo {\n"
        + "  optional string a = 1; // This is A.\n"
        + "  reserved 2; // This is reserved.\n"
        + "  optional string c = 3; // This is C.\n"
        + "}\n";

    MessageElement message = message(location.at(1, 1), "Foo", "", NO_TYPES, NO_OPTIONS,
        Collections.singletonList(
            reservedElement(location.at(3, 3), "This is reserved.",
                Collections.singletonList(2))),
        Arrays.asList(
            field(location.at(2, 3), OPTIONAL, "string", "a", 1, "This is A."),
            field(location.at(4, 3), OPTIONAL, "string", "c", 3, "This is C.")),
        NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS);
    ProtoFileElement expected = protoFile(Collections.singletonList(message));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void noWhitespace() {
    String proto = "message C {optional A.B ab = 1;}";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "C", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(field(location.at(1, 12), OPTIONAL, "A.B", "ab", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void deepOptionAssignments() {
    String proto = ""
        + "message Foo {\n"
        + "  optional string a = 1 [(wire.my_field_option).baz.value = \"a\"];\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "Foo", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(2, 3), OPTIONAL, "string", "a", null, null, 1, "",
                    Collections.singletonList(
                        new OptionElement("wire.my_field_option", OPTION,
                            new OptionElement("baz", OPTION,
                                new OptionElement("value", STRING, "a", false), false),
                            true)))),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  // https://github.com/square/wire/issues/3672
  @Test public void deepOptionAssignmentWithParenthesizedExtensionAfterFieldPathComponent() {
    String proto = ""
        + "message Foo {\n"
        + "  optional string a = 1 [(foo.field).string.(foo.datetime) = true];\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "Foo", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(2, 3), OPTIONAL, "string", "a", null, null, 1, "",
                    Collections.singletonList(
                        new OptionElement("foo.field", OPTION,
                            new OptionElement("string", OPTION,
                                new OptionElement("foo.datetime", BOOLEAN, "true", true),
                                false),
                            true)))),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  // https://github.com/square/wire/issues/3672
  @Test public void deepOptionAssignmentDotsAreSeparators() {
    // The dots between the components of an option name are separators, so whitespace and comments
    // around them are insignificant, as they are between any other two tokens.
    List<String> optionNames = Arrays.asList(
        "(foo.field).string.(foo.datetime)",
        "(foo.field).string. (foo.datetime)",
        "(foo.field).string . (foo.datetime)",
        "(foo.field) . string . (foo.datetime)",
        "(foo.field).string./* comment */(foo.datetime)",
        "(foo.field).string.\n      (foo.datetime)");
    ProtoFileElement canonical = parseOptionOnFieldA(optionNames.get(0));
    for (String optionName : optionNames) {
      assertEquals(canonical, parseOptionOnFieldA(optionName), optionName);
    }
  }

  /** Parses a message whose only field carries {@code optionName} set to {@code true}. */
  private ProtoFileElement parseOptionOnFieldA(String optionName) {
    return ProtoParser.parse(location,
        "message Foo {\n  optional string a = 1 [" + optionName + " = true];\n}\n");
  }

  @Test public void protoKeywordAsEnumConstants() {
    // Note: this is consistent with protoc.
    String proto = ""
        + "enum Foo {\n"
        + "  syntax = 0;\n"
        + "  import = 1;\n"
        + "  package = 2;\n"
        + "  // option = 3;\n"
        + "  // reserved = 4;\n"
        + "  message = 5;\n"
        + "  enum = 6;\n"
        + "  service = 7;\n"
        + "  extend = 8;\n"
        + "  rpc = 9;\n"
        + "  oneof = 10;\n"
        + "  extensions = 11;\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        enumElement(location.at(1, 1), "Foo", "", NO_OPTIONS,
            Arrays.asList(
                constant(location.at(2, 3), "syntax", 0),
                constant(location.at(3, 3), "import", 1),
                constant(location.at(4, 3), "package", 2),
                constant(location.at(7, 3), "message", 5, "option = 3;\nreserved = 4;",
                    NO_OPTIONS),
                constant(location.at(8, 3), "enum", 6),
                constant(location.at(9, 3), "service", 7),
                constant(location.at(10, 3), "extend", 8),
                constant(location.at(11, 3), "rpc", 9),
                constant(location.at(12, 3), "oneof", 10),
                constant(location.at(13, 3), "extensions", 11)),
            NO_RESERVEDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void protoKeywordAsMessageNameAndFieldProto2() {
    // Note: this is consistent with protoc.
    String proto = ""
        + "message syntax {\n"
        + "  optional syntax syntax = 1;\n"
        + "}\n"
        + "message import {\n"
        + "  optional import import = 1;\n"
        + "}\n"
        + "message package {\n"
        + "  optional package package = 1;\n"
        + "}\n"
        + "message option {\n"
        + "  optional option option = 1;\n"
        + "}\n"
        + "message reserved {\n"
        + "  optional reserved reserved = 1;\n"
        + "}\n"
        + "message message {\n"
        + "  optional message message = 1;\n"
        + "}\n"
        + "message enum {\n"
        + "  optional enum enum = 1;\n"
        + "}\n"
        + "message service {\n"
        + "  optional service service = 1;\n"
        + "}\n"
        + "message extend {\n"
        + "  optional extend extend = 1;\n"
        + "}\n"
        + "message rpc {\n"
        + "  optional rpc rpc = 1;\n"
        + "}\n"
        + "message oneof {\n"
        + "  optional oneof oneof = 1;\n"
        + "}\n"
        + "message extensions {\n"
        + "  optional extensions extensions = 1;\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Arrays.asList(
        message(location.at(1, 1), "syntax", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(2, 3), OPTIONAL, "syntax", "syntax", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(4, 1), "import", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(5, 3), OPTIONAL, "import", "import", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(7, 1), "package", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(8, 3), OPTIONAL, "package", "package", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(10, 1), "option", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(11, 3), OPTIONAL, "option", "option", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(13, 1), "reserved", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(14, 3), OPTIONAL, "reserved", "reserved", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(16, 1), "message", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(17, 3), OPTIONAL, "message", "message", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(19, 1), "enum", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(20, 3), OPTIONAL, "enum", "enum", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(22, 1), "service", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(23, 3), OPTIONAL, "service", "service", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(25, 1), "extend", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(26, 3), OPTIONAL, "extend", "extend", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(28, 1), "rpc", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(29, 3), OPTIONAL, "rpc", "rpc", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(31, 1), "oneof", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(32, 3), OPTIONAL, "oneof", "oneof", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(34, 1), "extensions", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(35, 3), OPTIONAL, "extensions", "extensions", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void protoKeywordAsMessageNameAndFieldProto3() {
    // Note: this is consistent with protoc.
    String proto = ""
        + "syntax = \"proto3\";\n"
        + "message syntax {\n"
        + "  syntax syntax = 1;\n"
        + "}\n"
        + "message import {\n"
        + "  import import = 1;\n"
        + "}\n"
        + "message package {\n"
        + "  package package = 1;\n"
        + "}\n"
        + "message option {\n"
        + "  option option = 1;\n"
        + "}\n"
        + "message reserved {\n"
        + "  // reserved reserved = 1;\n"
        + "}\n"
        + "message message {\n"
        + "  // message message = 1;\n"
        + "}\n"
        + "message enum {\n"
        + "  // enum enum = 1;\n"
        + "}\n"
        + "message service {\n"
        + "  service service = 1;\n"
        + "}\n"
        + "message extend {\n"
        + "  // extend extend = 1;\n"
        + "}\n"
        + "message rpc {\n"
        + "  rpc rpc = 1;\n"
        + "}\n"
        + "message oneof {\n"
        + "  // oneof oneof = 1;\n"
        + "}\n"
        + "message extensions {\n"
        + "  // extensions extensions = 1;\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Syntax.PROTO_3, Arrays.<TypeElement>asList(
        message(location.at(2, 1), "syntax", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(3, 3), null, "syntax", "syntax", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(5, 1), "import", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(6, 3), null, "import", "import", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(8, 1), "package", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(9, 3), null, "package", "package", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(11, 1), "option", "", NO_TYPES,
            Collections.singletonList(OptionElement.create("option", NUMBER, "1")),
            NO_RESERVEDS, NO_FIELDS, NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(14, 1), "reserved"),
        message(location.at(17, 1), "message"),
        message(location.at(20, 1), "enum"),
        message(location.at(23, 1), "service", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(24, 3), null, "service", "service", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(26, 1), "extend"),
        message(location.at(29, 1), "rpc", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(30, 3), null, "rpc", "rpc", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS),
        message(location.at(32, 1), "oneof"),
        message(location.at(35, 1), "extensions")));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  // TASK-13 adaptation: ranges in values lists; see nestingInMessage.
  @Test public void extensionRangeOptions() {
    String proto = ""
        + "syntax = \"proto2\";\n"
        + "message MyMessage {\n"
        + "  extensions 1000 to 9994 [\n"
        + "    declaration = {\n"
        + "      full_name: \".pb.cpp\",\n"
        + "      type: \".pb.CppFeatures\"\n"
        + "    },\n"
        + "    declaration = {\n"
        + "      full_name: \".pb.java\",\n"
        + "      type: \".pb.JavaFeatures\"\n"
        + "    },\n"
        + "    declaration = { full_name: \".pb.go\", type: \".pb.GoFeatures\" },\n"
        + "    declaration = {\n"
        + "      full_name: \".pb.proto1\",\n"
        + "      type: \".pb.Proto1Features\"\n"
        + "    }\n"
        + "  ];\n"
        + "  extensions 1, 2 to 5, 99 [(a) = \"b\"];\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Syntax.PROTO_2, Collections.singletonList(
        message(location.at(2, 1), "MyMessage", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            NO_FIELDS, NO_ONEOFS,
            Arrays.asList(
                extensionsElement(location.at(3, 3), "",
                    Collections.singletonList(new int[] {1000, 9994}),
                    Arrays.asList(
                        OptionElement.create("declaration", MAP,
                            declarationMap(".pb.cpp", ".pb.CppFeatures")),
                        OptionElement.create("declaration", MAP,
                            declarationMap(".pb.java", ".pb.JavaFeatures")),
                        OptionElement.create("declaration", MAP,
                            declarationMap(".pb.go", ".pb.GoFeatures")),
                        OptionElement.create("declaration", MAP,
                            declarationMap(".pb.proto1", ".pb.Proto1Features")))),
                extensionsElement(location.at(18, 3), "",
                    Arrays.<Object>asList(1, new int[] {2, 5}, 99),
                    Collections.singletonList(
                        OptionElement.create("a", STRING, "b", true)))),
            NO_GROUPS, NO_EXTENDS)));
    assertFilesEqual(expected, ProtoParser.parse(location, proto));
  }

  private static Map<String, Object> declarationMap(String fullName, String type) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("full_name", fullName);
    map.put("type", type);
    return map;
  }

  @Test public void protoKeywordAsServiceNameAndRpc() {
    // Note: this is consistent with protoc.
    String proto = ""
        + "service syntax {\n"
        + "  rpc syntax (google.protobuf.StringValue) returns (google.protobuf.StringValue);\n"
        + "}\n"
        + "service import {\n"
        + "  rpc import (google.protobuf.StringValue) returns (google.protobuf.StringValue);\n"
        + "}\n"
        + "service package {\n"
        + "  rpc package (google.protobuf.StringValue) returns (google.protobuf.StringValue);\n"
        + "}\n"
        + "service option {\n"
        + "  rpc option (google.protobuf.StringValue) returns (google.protobuf.StringValue);\n"
        + "}\n"
        + "service reserved {\n"
        + "  rpc reserved (google.protobuf.StringValue) returns (google.protobuf.StringValue);\n"
        + "}\n"
        + "service message {\n"
        + "  rpc message (google.protobuf.StringValue) returns (google.protobuf.StringValue);\n"
        + "}\n"
        + "service enum {\n"
        + "  rpc enum (google.protobuf.StringValue) returns (google.protobuf.StringValue);\n"
        + "}\n"
        + "service service {\n"
        + "  rpc service (google.protobuf.StringValue) returns (google.protobuf.StringValue);\n"
        + "}\n"
        + "service extend {\n"
        + "  rpc extend (google.protobuf.StringValue) returns (google.protobuf.StringValue);\n"
        + "}\n"
        + "service rpc {\n"
        + "  rpc rpc (google.protobuf.StringValue) returns (google.protobuf.StringValue);\n"
        + "}\n"
        + "service oneof {\n"
        + "  rpc oneof (google.protobuf.StringValue) returns (google.protobuf.StringValue);\n"
        + "}\n"
        + "service extensions {\n"
        + "  rpc extensions (google.protobuf.StringValue) returns"
        + " (google.protobuf.StringValue);\n"
        + "}\n";
    ProtoFileElement expected = protoFile(null, null, NO_STRINGS, NO_STRINGS, NO_STRINGS,
        NO_TYPES,
        Arrays.asList(
            keywordService("syntax", location.at(1, 1), location.at(2, 3)),
            keywordService("import", location.at(4, 1), location.at(5, 3)),
            keywordService("package", location.at(7, 1), location.at(8, 3)),
            keywordService("option", location.at(10, 1), location.at(11, 3)),
            keywordService("reserved", location.at(13, 1), location.at(14, 3)),
            keywordService("message", location.at(16, 1), location.at(17, 3)),
            keywordService("enum", location.at(19, 1), location.at(20, 3)),
            keywordService("service", location.at(22, 1), location.at(23, 3)),
            keywordService("extend", location.at(25, 1), location.at(26, 3)),
            keywordService("rpc", location.at(28, 1), location.at(29, 3)),
            keywordService("oneof", location.at(31, 1), location.at(32, 3)),
            keywordService("extensions", location.at(34, 1), location.at(35, 3))),
        NO_EXTENDS, NO_OPTIONS);
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  private static ServiceElement keywordService(String name, Location serviceLocation,
      Location rpcLocation) {
    return serviceElement(serviceLocation, name, "",
        Collections.singletonList(
            rpcElement(rpcLocation, name, "", "google.protobuf.StringValue",
                "google.protobuf.StringValue", false, false, NO_OPTIONS)),
        NO_OPTIONS);
  }

  @Test public void parsingTrailingComments() {
    String proto = ""
        + "enum ImageState {\n"
        + "    IMAGE_STATE_UNSPECIFIED = 0;\n"
        + "    IMAGE_STATE_READONLY = 1;     /* unlocked */\n"
        + "    IMAGE_STATE_MUSTLOCK = 2;     /* must be locked */\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        enumElement(location.at(1, 1), "ImageState", "", NO_OPTIONS,
            Arrays.asList(
                constant(location.at(2, 5), "IMAGE_STATE_UNSPECIFIED", 0, "", NO_OPTIONS),
                constant(location.at(3, 5), "IMAGE_STATE_READONLY", 1, "unlocked", NO_OPTIONS),
                constant(location.at(4, 5), "IMAGE_STATE_MUSTLOCK", 2, "must be locked",
                    NO_OPTIONS)),
            NO_RESERVEDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void parsingTrailingCommentsWithCarriageReturn() {
    String proto = (""
        + "enum ImageState {\n"
        + "    IMAGE_STATE_UNSPECIFIED = 0;\n"
        + "    IMAGE_STATE_READONLY = 1;     /* unlocked */\n"
        + "    IMAGE_STATE_MUSTLOCK = 2;     /* must be locked */\n"
        + "}\n").replace("\n", "\r\n");
    ProtoFileElement expected = protoFile(Collections.singletonList(
        enumElement(location.at(1, 1), "ImageState", "", NO_OPTIONS,
            Arrays.asList(
                constant(location.at(2, 5), "IMAGE_STATE_UNSPECIFIED", 0, "", NO_OPTIONS),
                constant(location.at(3, 5), "IMAGE_STATE_READONLY", 1, "unlocked", NO_OPTIONS),
                constant(location.at(4, 5), "IMAGE_STATE_MUSTLOCK", 2, "must be locked",
                    NO_OPTIONS)),
            NO_RESERVEDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void forbidMultipleSyntaxDefinitions() {
    String proto = ""
        + "  syntax = \"proto2\";\n"
        + "  syntax = \"proto2\";\n";
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, proto));
    assertTrue(e.getMessage().contains(
        "Syntax error in file.proto:2:3: too many syntax definitions"));
  }

  @Test public void weDoNotSupportEdition() {
    String proto = ""
        + "  edition = \"2023\";\n";
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ProtoParser.parse(location, proto));
    assertTrue(e.getMessage().contains(
        "Syntax error in file.proto:1:10: edition is not currently supported"));
  }

  @Test public void oneOfOptions() {
    String proto = ""
        + "message SearchRequest {\n"
        + "  required string query = 1;\n"
        + "  oneof page_info {\n"
        + "    option (my_option) = true;\n"
        + "    int32 page_number = 2;\n"
        + "    int32 result_per_page = 3;\n"
        + "  }\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Collections.singletonList(
        message(location.at(1, 1), "SearchRequest", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(field(location.at(2, 3), REQUIRED, "string", "query", 1)),
            Collections.singletonList(
                oneOfElement("page_info", "",
                    Arrays.asList(
                        field(location.at(5, 5), null, "int32", "page_number", 2),
                        field(location.at(6, 5), null, "int32", "result_per_page", 3)),
                    NO_GROUPS,
                    Collections.singletonList(
                        OptionElement.create("my_option", BOOLEAN, "true", true)),
                    location.at(3, 3))),
            NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void semiColonAsOptionsDelimiters() {
    String proto = ""
        + "service MyService {\n"
        + "    option (custom_rule) = {\n"
        + "        my_string: \"abc\"; my_int: 3;\n"
        + "        my_list: [\"a\", \"b\", \"c\"];\n"
        + "    };\n"
        + "}\n";
    Map<String, Object> customRuleValue = new LinkedHashMap<>();
    customRuleValue.put("my_string", "abc");
    customRuleValue.put("my_int", new OptionPrimitive(NUMBER, "3"));
    customRuleValue.put("my_list", Arrays.asList("a", "b", "c"));
    ProtoFileElement expected = protoFile(null, null, NO_STRINGS, NO_STRINGS, NO_STRINGS,
        NO_TYPES,
        Collections.singletonList(
            serviceElement(location.at(1, 1), "MyService", "", NO_RPCS,
                Collections.singletonList(
                    new OptionElement("custom_rule", MAP, customRuleValue, true)))),
        NO_EXTENDS, NO_OPTIONS);
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void fieldTypeWrappedAcrossLines() {
    String proto = ""
        + "syntax = \"proto3\";\n"
        + "message MyRequest {\n"
        + "  message Body {\n"
        + "    string required_field = 1;\n"
        + "    repeated squareup.geology.deep.sedimentary\n"
        + "        .Mite wrapped_field = 2;\n"
        + "    optional string other_field = 3;\n"
        + "  }\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Syntax.PROTO_3, Collections.singletonList(
        message(location.at(2, 1), "MyRequest", "",
            Collections.singletonList(
                message(location.at(3, 3), "Body", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
                    Arrays.asList(
                        field(location.at(4, 5), null, "string", "required_field", 1),
                        field(location.at(5, 5), REPEATED,
                            "squareup.geology.deep.sedimentary.Mite", "wrapped_field", 2),
                        field(location.at(7, 5), OPTIONAL, "string", "other_field", 3)),
                    NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)),
            NO_OPTIONS, NO_RESERVEDS, NO_FIELDS, NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS,
            NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  @Test public void fieldTypeWithNestedTypeWrappedAcrossLines() {
    String proto = ""
        + "syntax = \"proto3\";\n"
        + "message Modules {\n"
        + "  optional squareup.geology.Calcium\n"
        + "      .ite.ite.Ite category = 1;\n"
        + "}\n";
    ProtoFileElement expected = protoFile(Syntax.PROTO_3, Collections.singletonList(
        message(location.at(2, 1), "Modules", "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS,
            Collections.singletonList(
                field(location.at(3, 3), OPTIONAL, "squareup.geology.Calcium.ite.ite.Ite",
                    "category", 1)),
            NO_ONEOFS, NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS)));
    assertEquals(expected, ProtoParser.parse(location, proto));
  }

  /**
   * TASK-13 adaptation of upstream whole-file equality for range-bearing models. Upstream
   * ranges are Kotlin IntRanges, which compare structurally in element equality; the port
   * represents them as {@code int[]}, whose {@code equals} is identity, so whole-element
   * {@code assertEquals} cannot succeed for files containing reserved or extension ranges.
   * This helper asserts every property through element equality except the values lists of
   * reserved and extensions elements, whose ranges are compared structurally instead.
   */
  private static void assertFilesEqual(ProtoFileElement expected, ProtoFileElement actual) {
    assertEquals(expected.getLocation(), actual.getLocation());
    assertEquals(expected.getPackageName(), actual.getPackageName());
    assertEquals(expected.getSyntax(), actual.getSyntax());
    assertEquals(expected.getImports(), actual.getImports());
    assertEquals(expected.getPublicImports(), actual.getPublicImports());
    assertEquals(expected.getWeakImports(), actual.getWeakImports());
    assertEquals(expected.getServices(), actual.getServices());
    assertEquals(expected.getExtendDeclarations(), actual.getExtendDeclarations());
    assertEquals(expected.getOptions(), actual.getOptions());
    assertEquals(expected.getTypes().size(), actual.getTypes().size());
    for (int i = 0; i < expected.getTypes().size(); i++) {
      TypeElement expectedType = expected.getTypes().get(i);
      TypeElement actualType = actual.getTypes().get(i);
      assertEquals(expectedType.getLocation(), actualType.getLocation());
      assertEquals(expectedType.getName(), actualType.getName());
      assertEquals(expectedType.getDocumentation(), actualType.getDocumentation());
      assertEquals(expectedType.getOptions(), actualType.getOptions());
      if (expectedType instanceof MessageElement) {
        MessageElement expectedMessage = (MessageElement) expectedType;
        MessageElement actualMessage = (MessageElement) actualType;
        assertEquals(expectedMessage.nestedTypes, actualMessage.nestedTypes);
        assertEquals(expectedMessage.getFields(), actualMessage.getFields());
        assertEquals(expectedMessage.getOneOfs(), actualMessage.getOneOfs());
        assertEquals(expectedMessage.getGroups(), actualMessage.getGroups());
        assertEquals(expectedMessage.getExtendDeclarations(), actualMessage.getExtendDeclarations());
        assertReservedsEqual(expectedMessage.getReserveds(), actualMessage.getReserveds());
        assertExtensionsEqual(expectedMessage.getExtensions(), actualMessage.getExtensions());
      } else if (expectedType instanceof EnumElement) {
        EnumElement expectedEnum = (EnumElement) expectedType;
        EnumElement actualEnum = (EnumElement) actualType;
        assertEquals(expectedEnum.getConstants(), actualEnum.getConstants());
        assertReservedsEqual(expectedEnum.getReserveds(), actualEnum.getReserveds());
      } else {
        throw new AssertionError("unexpected type element " + expectedType.getClass());
      }
    }
  }

  private static void assertReservedsEqual(List<ReservedElement> expected,
      List<ReservedElement> actual) {
    assertEquals(expected.size(), actual.size());
    for (int i = 0; i < expected.size(); i++) {
      ReservedElement expectedReserved = expected.get(i);
      ReservedElement actualReserved = actual.get(i);
      assertEquals(expectedReserved.getLocation(), actualReserved.getLocation());
      assertEquals(expectedReserved.getDocumentation(), actualReserved.getDocumentation());
      assertValuesEqual(expectedReserved.getValues(), actualReserved.getValues());
    }
  }

  private static void assertExtensionsEqual(List<ExtensionsElement> expected,
      List<ExtensionsElement> actual) {
    assertEquals(expected.size(), actual.size());
    for (int i = 0; i < expected.size(); i++) {
      ExtensionsElement expectedExtension = expected.get(i);
      ExtensionsElement actualExtension = actual.get(i);
      assertEquals(expectedExtension.getLocation(), actualExtension.getLocation());
      assertEquals(expectedExtension.getDocumentation(), actualExtension.getDocumentation());
      assertEquals(expectedExtension.getOptions(), actualExtension.getOptions());
      assertValuesEqual(expectedExtension.getValues(), actualExtension.getValues());
    }
  }

  private static void assertValuesEqual(List<Object> expected, List<Object> actual) {
    assertEquals(expected.size(), actual.size());
    for (int i = 0; i < expected.size(); i++) {
      Object expectedValue = expected.get(i);
      Object actualValue = actual.get(i);
      if (expectedValue instanceof int[]) {
        assertTrue(actualValue instanceof int[], "expected a range at index " + i);
        assertArrayEquals((int[]) expectedValue, (int[]) actualValue);
      } else {
        assertEquals(expectedValue, actualValue);
      }
    }
  }

  private ProtoFileElement protoFile(List<TypeElement> types) {
    return new ProtoFileElement(location, null, null, NO_STRINGS, NO_STRINGS, NO_STRINGS, types,
        NO_SERVICES, NO_EXTENDS, NO_OPTIONS);
  }

  private ProtoFileElement protoFile(Syntax syntax, List<TypeElement> types) {
    return new ProtoFileElement(location, null, syntax, NO_STRINGS, NO_STRINGS, NO_STRINGS,
        types, NO_SERVICES, NO_EXTENDS, NO_OPTIONS);
  }

  private ProtoFileElement protoFile(String packageName, Syntax syntax, List<String> imports,
      List<String> publicImports, List<String> weakImports, List<TypeElement> types,
      List<ServiceElement> services, List<ExtendElement> extendDeclarations,
      List<OptionElement> options) {
    return new ProtoFileElement(location, packageName, syntax, imports, publicImports,
        weakImports, types, services, extendDeclarations, options);
  }

  private static FieldElement field(Location location, Label label, String type, String name,
      String defaultValue, String jsonName, int tag, String documentation,
      List<OptionElement> options) {
    return new FieldElement(location, label, type, name, defaultValue, jsonName, tag,
        documentation, options);
  }

  private static FieldElement field(Location location, Label label, String type, String name,
      int tag) {
    return field(location, label, type, name, null, null, tag, "", NO_OPTIONS);
  }

  private static FieldElement field(Location location, Label label, String type, String name,
      int tag, List<OptionElement> options) {
    return field(location, label, type, name, null, null, tag, "", options);
  }

  private static FieldElement field(Location location, Label label, String type, String name,
      int tag, String documentation) {
    return field(location, label, type, name, null, null, tag, documentation, NO_OPTIONS);
  }

  private static MessageElement message(Location location, String name) {
    return message(location, name, "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS, NO_FIELDS, NO_ONEOFS,
        NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS);
  }

  private static MessageElement message(Location location, String name,
      List<FieldElement> fields) {
    return message(location, name, "", NO_TYPES, NO_OPTIONS, NO_RESERVEDS, fields, NO_ONEOFS,
        NO_EXTENSIONS, NO_GROUPS, NO_EXTENDS);
  }

  private static MessageElement message(Location location, String name, String documentation,
      List<TypeElement> nestedTypes, List<OptionElement> options,
      List<ReservedElement> reserveds, List<FieldElement> fields, List<OneOfElement> oneOfs,
      List<ExtensionsElement> extensions, List<GroupElement> groups,
      List<ExtendElement> extendDeclarations) {
    return new MessageElement(location, name, documentation, nestedTypes, options, reserveds,
        fields, oneOfs, extensions, groups, extendDeclarations);
  }

  private static EnumElement enumElement(Location location, String name,
      List<EnumConstantElement> constants) {
    return enumElement(location, name, "", NO_OPTIONS, constants, NO_RESERVEDS);
  }

  private static EnumElement enumElement(Location location, String name, String documentation,
      List<OptionElement> options, List<EnumConstantElement> constants,
      List<ReservedElement> reserveds) {
    return new EnumElement(location, name, documentation, options, constants, reserveds);
  }

  private static EnumConstantElement constant(Location location, String name, int tag) {
    return constant(location, name, tag, "", NO_OPTIONS);
  }

  private static EnumConstantElement constant(Location location, String name, int tag,
      String documentation, List<OptionElement> options) {
    return new EnumConstantElement(location, name, tag, documentation, options);
  }

  private static ExtendElement extendElement(Location location, String name,
      String documentation, List<FieldElement> fields) {
    return new ExtendElement(location, name, documentation, fields);
  }

  private static GroupElement groupElement(Label label, Location location, String name, int tag,
      String documentation, List<FieldElement> fields) {
    return new GroupElement(label, location, name, tag, documentation, fields);
  }

  private static OneOfElement oneOfElement(String name, String documentation,
      List<FieldElement> fields, List<GroupElement> groups, List<OptionElement> options,
      Location location) {
    return new OneOfElement(name, documentation, fields, groups, options, location);
  }

  private static ReservedElement reservedElement(Location location, String documentation,
      List<Object> values) {
    return new ReservedElement(location, documentation, values);
  }

  private static ExtensionsElement extensionsElement(Location location, String documentation,
      List<Object> values, List<OptionElement> options) {
    return new ExtensionsElement(location, documentation, values, options);
  }

  private static RpcElement rpcElement(Location location, String name, String documentation,
      String requestType, String responseType, boolean requestStreaming,
      boolean responseStreaming, List<OptionElement> options) {
    return new RpcElement(location, name, documentation, requestType, responseType,
        requestStreaming, responseStreaming, options);
  }

  private static ServiceElement serviceElement(Location location, String name,
      String documentation, List<RpcElement> rpcs, List<OptionElement> options) {
    return new ServiceElement(location, name, documentation, rpcs, options);
  }
}
