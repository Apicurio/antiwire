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
package com.squareup.wire.schema.internal.parser;

import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.BOOLEAN;
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.STRING;
import static com.squareup.wire.schema.internal.parser.OptionElement.PACKED_OPTION_ELEMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.squareup.wire.Syntax;
import com.squareup.wire.schema.Field.Label;
import com.squareup.wire.schema.Location;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** Upstream ProtoFileElementTest translated (assertk to JUnit 5). */
public class ProtoFileElementTest {
  private final Location location = Location.get("some/folder", "file.proto");

  @Test public void emptyToSchema() {
    ProtoFileElement file = new ProtoFileElement(location, null, null, Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    assertEquals("// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n", file.toSchema());
  }

  @Test public void emptyWithPackageToSchema() {
    ProtoFileElement file = new ProtoFileElement(location, "example.simple", null,
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertEquals("// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n"
        + "\n"
        + "package example.simple;\n", file.toSchema());
  }

  @Test public void simpleToSchema() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    ProtoFileElement file = new ProtoFileElement(location, null, null, Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.singletonList(element),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    assertEquals("// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n"
        + "\n"
        + "message Message {}\n", file.toSchema());
  }

  @Test public void simpleWithImportsToSchema() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    ProtoFileElement file = new ProtoFileElement(location, null, null,
        Collections.singletonList("example.other"), Collections.emptyList(),
        Collections.emptyList(), Collections.singletonList(element), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    assertEquals("// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n"
        + "\n"
        + "import \"example.other\";\n"
        + "\n"
        + "message Message {}\n", file.toSchema());
  }

  @Test public void addMultipleDependencies() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    ProtoFileElement file = new ProtoFileElement(location, null, null,
        Arrays.asList("example.other", "example.another"), Collections.emptyList(),
        Collections.emptyList(), Collections.singletonList(element), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    assertEquals(2, file.imports.size());
  }

  @Test public void simpleWithPublicImportsToSchema() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    ProtoFileElement file = new ProtoFileElement(location, null, null, Collections.emptyList(),
        Collections.singletonList("example.other"), Collections.emptyList(),
        Collections.singletonList(element), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertEquals("// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n"
        + "\n"
        + "import public \"example.other\";\n"
        + "\n"
        + "message Message {}\n", file.toSchema());
  }

  @Test public void addMultiplePublicDependencies() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    ProtoFileElement file = new ProtoFileElement(location, null, null, Collections.emptyList(),
        Arrays.asList("example.other", "example.another"), Collections.emptyList(),
        Collections.singletonList(element), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertEquals(2, file.publicImports.size());
  }

  @Test public void simpleWithMultipleImportTypesToSchema() {
    MessageElement element = new MessageElement(location.at(8, 1), "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    ProtoFileElement file = new ProtoFileElement(location, null, null,
        Collections.singletonList("example.thing"), Collections.singletonList("example.other"),
        Collections.singletonList("yet.other"), Collections.singletonList(element),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    String expected = "// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n"
        + "\n"
        + "import \"example.thing\";\n"
        + "import public \"example.other\";\n"
        + "import weak \"yet.other\";\n"
        + "\n"
        + "message Message {}\n";
    assertEquals(expected, file.toSchema());

    // Re-parse the expected string into a ProtoFile and ensure they're equal.
    ProtoFileElement parsed = ProtoParser.parse(location, expected);
    assertEquals(file, parsed);
  }

  @Test public void simpleWithServicesToSchema() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    ServiceElement service = new ServiceElement(location, "Service", "",
        Collections.emptyList(), Collections.emptyList());
    ProtoFileElement file = new ProtoFileElement(location, null, null, Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.singletonList(element),
        Collections.singletonList(service), Collections.emptyList(), Collections.emptyList());
    assertEquals("// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n"
        + "\n"
        + "message Message {}\n"
        + "\n"
        + "service Service {}\n", file.toSchema());
  }

  @Test public void addMultipleServices() {
    ServiceElement service1 = new ServiceElement(location, "Service1", "",
        Collections.emptyList(), Collections.emptyList());
    ServiceElement service2 = new ServiceElement(location, "Service2", "",
        Collections.emptyList(), Collections.emptyList());
    ProtoFileElement file = new ProtoFileElement(location, null, null, Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Arrays.asList(service1, service2), Collections.emptyList(), Collections.emptyList());
    assertEquals(2, file.services.size());
  }

  @Test public void simpleWithOptionsToSchema() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    OptionElement option = OptionElement.create("kit", STRING, "kat");
    ProtoFileElement file = new ProtoFileElement(location, null, null, Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.singletonList(element),
        Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(option));
    assertEquals("// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n"
        + "\n"
        + "option kit = \"kat\";\n"
        + "\n"
        + "message Message {}\n", file.toSchema());
  }

  @Test public void addMultipleOptions() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    OptionElement kitKat = OptionElement.create("kit", STRING, "kat");
    OptionElement fooBar = OptionElement.create("foo", STRING, "bar");
    ProtoFileElement file = new ProtoFileElement(location, null, null, Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.singletonList(element),
        Collections.emptyList(), Collections.emptyList(), Arrays.asList(kitKat, fooBar));
    assertEquals(2, file.options.size());
  }

  @Test public void simpleWithExtendsToSchema() {
    ProtoFileElement file = new ProtoFileElement(location, null, null, Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(new MessageElement(location, "Message", "",
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList())),
        Collections.emptyList(),
        Collections.singletonList(new ExtendElement(location.at(5, 1), "Extend", "",
            Collections.emptyList())),
        Collections.emptyList());
    assertEquals("// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n"
        + "\n"
        + "message Message {}\n"
        + "\n"
        + "extend Extend {}\n", file.toSchema());
  }

  @Test public void addMultipleExtends() {
    ExtendElement extend1 = new ExtendElement(location, "Extend1", "",
        Collections.emptyList());
    ExtendElement extend2 = new ExtendElement(location, "Extend2", "",
        Collections.emptyList());
    ProtoFileElement file = new ProtoFileElement(location, null, null, Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Arrays.asList(extend1, extend2), Collections.emptyList());
    assertEquals(2, file.extendDeclarations.size());
  }

  @Test public void multipleEverythingToSchema() {
    MessageElement element1 = new MessageElement(location.at(12, 1), "Message1", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    MessageElement element2 = new MessageElement(location.at(14, 1), "Message2", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    ExtendElement extend1 = new ExtendElement(location.at(16, 1), "Extend1", "",
        Collections.emptyList());
    ExtendElement extend2 = new ExtendElement(location.at(18, 1), "Extend2", "",
        Collections.emptyList());
    OptionElement option1 = OptionElement.create("kit", STRING, "kat");
    OptionElement option2 = OptionElement.create("foo", STRING, "bar");
    ServiceElement service1 = new ServiceElement(location.at(20, 1), "Service1", "",
        Collections.emptyList(), Collections.emptyList());
    ServiceElement service2 = new ServiceElement(location.at(22, 1), "Service2", "",
        Collections.emptyList(), Collections.emptyList());
    ProtoFileElement file = new ProtoFileElement(location, "example.simple", null,
        Collections.singletonList("example.thing"), Collections.singletonList("example.other"),
        Collections.emptyList(), Arrays.asList(element1, element2),
        Arrays.asList(service1, service2), Arrays.asList(extend1, extend2),
        Arrays.asList(option1, option2));
    String expected = "// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n"
        + "\n"
        + "package example.simple;\n"
        + "\n"
        + "import \"example.thing\";\n"
        + "import public \"example.other\";\n"
        + "\n"
        + "option kit = \"kat\";\n"
        + "option foo = \"bar\";\n"
        + "\n"
        + "message Message1 {}\n"
        + "\n"
        + "message Message2 {}\n"
        + "\n"
        + "extend Extend1 {}\n"
        + "\n"
        + "extend Extend2 {}\n"
        + "\n"
        + "service Service1 {}\n"
        + "\n"
        + "service Service2 {}\n";
    assertEquals(expected, file.toSchema());

    // Re-parse the expected string into a ProtoFile and ensure they're equal.
    ProtoFileElement parsed = ProtoParser.parse(location, expected);
    assertEquals(file, parsed);
  }

  @Test public void syntaxToSchema() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    ProtoFileElement file = new ProtoFileElement(location, null, Syntax.PROTO_2,
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(element), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertEquals("// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n"
        + "\n"
        + "syntax = \"proto2\";\n"
        + "\n"
        + "message Message {}\n", file.toSchema());
  }

  @Test public void defaultIsSetInProto2() {
    FieldElement field = new FieldElement(location.at(12, 3), Label.REQUIRED, "string", "name",
        "defaultValue", null, 1, "", Collections.emptyList());
    MessageElement message = new MessageElement(location.at(11, 1), "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(field), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    ProtoFileElement file = new ProtoFileElement(location, "example.simple", Syntax.PROTO_2,
        Collections.singletonList("example.thing"), Collections.singletonList("example.other"),
        Collections.emptyList(), Collections.singletonList(message), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    String expected = "// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n"
        + "\n"
        + "syntax = \"proto2\";\n"
        + "\n"
        + "package example.simple;\n"
        + "\n"
        + "import \"example.thing\";\n"
        + "import public \"example.other\";\n"
        + "\n"
        + "message Message {\n"
        + "  required string name = 1 [default = \"defaultValue\"];\n"
        + "}\n";
    assertEquals(expected, file.toSchema());

    // Re-parse the expected string into a ProtoFile and ensure they're equal.
    ProtoFileElement parsed = ProtoParser.parse(location, expected);
    assertEquals(file, parsed);
  }

  @Test public void convertPackedOptionFromWireSchemaInProto2() {
    FieldElement fieldNumeric = new FieldElement(location.at(9, 3), Label.REPEATED, "int32",
        "numeric_without_packed_option", null, null, 1, "", Collections.emptyList());
    FieldElement fieldNumericPackedTrue = new FieldElement(location.at(11, 3), Label.REPEATED,
        "int32", "numeric_packed_true", null, null, 2, "",
        Collections.singletonList(PACKED_OPTION_ELEMENT));
    // TASK-13 adaptation: upstream uses PACKED_OPTION_ELEMENT.copy(value = "false"); the port
    // has no OptionElement.copy, so construct the equivalent element directly.
    FieldElement fieldNumericPackedFalse = new FieldElement(location.at(13, 3), Label.REPEATED,
        "int32", "numeric_packed_false", null, null, 3, "",
        Collections.singletonList(OptionElement.create("packed", BOOLEAN, "false")));
    FieldElement fieldString = new FieldElement(location.at(15, 3), Label.REPEATED, "string",
        "string_without_packed_option", null, null, 4, "", Collections.emptyList());
    FieldElement fieldStringPackedTrue = new FieldElement(location.at(17, 3), Label.REPEATED,
        "string", "string_packed_true", null, null, 5, "",
        Collections.singletonList(PACKED_OPTION_ELEMENT));
    // TASK-13 adaptation: upstream uses PACKED_OPTION_ELEMENT.copy(value = "false"); the port
    // has no OptionElement.copy, so construct the equivalent element directly.
    FieldElement fieldStringPackedFalse = new FieldElement(location.at(19, 3), Label.REPEATED,
        "string", "string_packed_false", null, null, 6, "",
        Collections.singletonList(OptionElement.create("packed", BOOLEAN, "false")));

    MessageElement message = new MessageElement(location.at(8, 1), "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Arrays.asList(
            fieldNumeric,
            fieldNumericPackedTrue,
            fieldNumericPackedFalse,
            fieldString,
            fieldStringPackedTrue,
            fieldStringPackedFalse),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    ProtoFileElement file = new ProtoFileElement(location, "example.simple", Syntax.PROTO_2,
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(message), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    String expected = "// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n"
        + "\n"
        + "syntax = \"proto2\";\n"
        + "\n"
        + "package example.simple;\n"
        + "\n"
        + "message Message {\n"
        + "  repeated int32 numeric_without_packed_option = 1;\n"
        + "\n"
        + "  repeated int32 numeric_packed_true = 2 [packed = true];\n"
        + "\n"
        + "  repeated int32 numeric_packed_false = 3 [packed = false];\n"
        + "\n"
        + "  repeated string string_without_packed_option = 4;\n"
        + "\n"
        + "  repeated string string_packed_true = 5 [packed = true];\n"
        + "\n"
        + "  repeated string string_packed_false = 6 [packed = false];\n"
        + "}\n";
    assertEquals(expected, file.toSchema());

    // Re-parse the expected string into a ProtoFile and ensure they're equal.
    ProtoFileElement parsed = ProtoParser.parse(location, expected);
    assertEquals(file, parsed);
  }

  @Test public void convertPackedOptionFromWireSchemaInProto3() {
    FieldElement fieldNumeric = new FieldElement(location.at(9, 3), Label.REPEATED, "int32",
        "numeric_without_packed_option", null, null, 1, "", Collections.emptyList());
    FieldElement fieldNumericPackedTrue = new FieldElement(location.at(11, 3), Label.REPEATED,
        "int32", "numeric_packed_true", null, null, 2, "",
        Collections.singletonList(PACKED_OPTION_ELEMENT));
    // TASK-13 adaptation: upstream uses PACKED_OPTION_ELEMENT.copy(value = "false"); the port
    // has no OptionElement.copy, so construct the equivalent element directly.
    FieldElement fieldNumericPackedFalse = new FieldElement(location.at(13, 3), Label.REPEATED,
        "int32", "numeric_packed_false", null, null, 3, "",
        Collections.singletonList(OptionElement.create("packed", BOOLEAN, "false")));
    FieldElement fieldString = new FieldElement(location.at(15, 3), Label.REPEATED, "string",
        "string_without_packed_option", null, null, 4, "", Collections.emptyList());
    FieldElement fieldStringPackedTrue = new FieldElement(location.at(17, 3), Label.REPEATED,
        "string", "string_packed_true", null, null, 5, "",
        Collections.singletonList(PACKED_OPTION_ELEMENT));
    // TASK-13 adaptation: upstream uses PACKED_OPTION_ELEMENT.copy(value = "false"); the port
    // has no OptionElement.copy, so construct the equivalent element directly.
    FieldElement fieldStringPackedFalse = new FieldElement(location.at(19, 3), Label.REPEATED,
        "string", "string_packed_false", null, null, 6, "",
        Collections.singletonList(OptionElement.create("packed", BOOLEAN, "false")));

    MessageElement message = new MessageElement(location.at(8, 1), "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Arrays.asList(
            fieldNumeric,
            fieldNumericPackedTrue,
            fieldNumericPackedFalse,
            fieldString,
            fieldStringPackedTrue,
            fieldStringPackedFalse),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    ProtoFileElement file = new ProtoFileElement(location, "example.simple", Syntax.PROTO_3,
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(message), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    String expected = "// Proto schema formatted by Wire, do not edit.\n"
        + "// Source: file.proto\n"
        + "\n"
        + "syntax = \"proto3\";\n"
        + "\n"
        + "package example.simple;\n"
        + "\n"
        + "message Message {\n"
        + "  repeated int32 numeric_without_packed_option = 1;\n"
        + "\n"
        + "  repeated int32 numeric_packed_true = 2 [packed = true];\n"
        + "\n"
        + "  repeated int32 numeric_packed_false = 3 [packed = false];\n"
        + "\n"
        + "  repeated string string_without_packed_option = 4;\n"
        + "\n"
        + "  repeated string string_packed_true = 5 [packed = true];\n"
        + "\n"
        + "  repeated string string_packed_false = 6 [packed = false];\n"
        + "}\n";
    assertEquals(expected, file.toSchema());

    // Re-parse the expected string into a ProtoFile and ensure they're equal.
    ProtoFileElement parsed = ProtoParser.parse(location, expected);
    assertEquals(file, parsed);
  }
}
