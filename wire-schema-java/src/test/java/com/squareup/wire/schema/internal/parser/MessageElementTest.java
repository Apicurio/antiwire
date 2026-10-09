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

import static com.squareup.wire.schema.internal.SchemaUtil.MAX_TAG_VALUE;
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.BOOLEAN;
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.squareup.wire.schema.Field.Label;
import com.squareup.wire.schema.Location;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** Upstream MessageElementTest translated (assertk to JUnit 5). */
public class MessageElementTest {
  private final Location location = Location.get("file.proto");

  @Test public void emptyToSchema() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    assertEquals("message Message {}\n", element.toSchema());
  }

  @Test public void simpleToSchema() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(new FieldElement(location, Label.REQUIRED, "string", "name",
            null, null, 1, "", Collections.emptyList())),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertEquals("message Message {\n"
        + "  required string name = 1;\n"
        + "}\n", element.toSchema());
  }

  @Test public void addMultipleFields() {
    FieldElement firstName = new FieldElement(location, Label.REQUIRED, "string", "first_name",
        null, null, 1, "", Collections.emptyList());
    FieldElement lastName = new FieldElement(location, Label.REQUIRED, "string", "last_name",
        null, null, 2, "", Collections.emptyList());
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Arrays.asList(firstName, lastName), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    assertEquals(2, element.getFields().size());
  }

  @Test public void simpleWithDocumentationToSchema() {
    MessageElement element = new MessageElement(location, "Message", "Hello",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(new FieldElement(location, Label.REQUIRED, "string", "name",
            null, null, 1, "", Collections.emptyList())),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertEquals("// Hello\n"
        + "message Message {\n"
        + "  required string name = 1;\n"
        + "}\n", element.toSchema());
  }

  @Test public void simpleWithOptionsToSchema() {
    FieldElement field = new FieldElement(location, Label.REQUIRED, "string", "name", null, null,
        1, "", Collections.emptyList());
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.singletonList(OptionElement.create("kit", STRING,
        "kat")), Collections.emptyList(), Collections.singletonList(field),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertEquals("message Message {\n"
        + "  option kit = \"kat\";\n"
        + "\n"
        + "  required string name = 1;\n"
        + "}\n", element.toSchema());
  }

  @Test public void addMultipleOptions() {
    FieldElement field = new FieldElement(location, Label.REQUIRED, "string", "name", null, null,
        1, "", Collections.emptyList());
    OptionElement kitKat = OptionElement.create("kit", STRING, "kat");
    OptionElement fooBar = OptionElement.create("foo", STRING, "bar");
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Arrays.asList(kitKat, fooBar), Collections.emptyList(),
        Collections.singletonList(field), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    assertEquals(2, element.options.size());
  }

  @Test public void simpleWithNestedElementsToSchema() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.singletonList(new MessageElement(location, "Nested", "",
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.singletonList(new FieldElement(location, Label.REQUIRED, "string",
                "name", null, null, 1, "", Collections.emptyList())),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList())),
        Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(new FieldElement(location, Label.REQUIRED, "string", "name",
            null, null, 1, "", Collections.emptyList())),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertEquals("message Message {\n"
        + "  required string name = 1;\n"
        + "\n"
        + "  message Nested {\n"
        + "    required string name = 1;\n"
        + "  }\n"
        + "}\n", element.toSchema());
  }

  @Test public void addMultipleTypes() {
    MessageElement nested1 = new MessageElement(location, "Nested1", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    MessageElement nested2 = new MessageElement(location, "Nested2", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    MessageElement element = new MessageElement(location, "Message", "",
        Arrays.asList(nested1, nested2), Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(new FieldElement(location, Label.REQUIRED, "string", "name",
            null, null, 1, "", Collections.emptyList())),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertEquals(2, element.nestedTypes.size());
  }

  @Test public void simpleWithExtensionsToSchema() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(new FieldElement(location, Label.REQUIRED, "string", "name",
            null, null, 1, "", Collections.emptyList())),
        Collections.emptyList(),
        Collections.singletonList(new ExtensionsElement(location, "",
            Collections.singletonList(new int[] {500, 501}), Collections.emptyList())),
        Collections.emptyList(), Collections.emptyList());
    assertEquals("message Message {\n"
        + "  required string name = 1;\n"
        + "\n"
        + "  extensions 500 to 501;\n"
        + "}\n", element.toSchema());
  }

  @Test public void addMultipleExtensions() {
    ExtensionsElement fives = new ExtensionsElement(location, "",
        Collections.singletonList(new int[] {500, 501}), Collections.emptyList());
    ExtensionsElement sixes = new ExtensionsElement(location, "",
        Collections.singletonList(new int[] {600, 601}), Collections.emptyList());
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(new FieldElement(location, Label.REQUIRED, "string", "name",
            null, null, 1, "", Collections.emptyList())),
        Collections.emptyList(), Arrays.asList(fives, sixes), Collections.emptyList(),
        Collections.emptyList());
    assertEquals(2, element.getExtensions().size());
  }

  @Test public void oneOfToSchema() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(),
        Collections.singletonList(new OneOfElement("hi", "",
            Collections.singletonList(new FieldElement(location, null, "string", "name", null,
                null, 1, "", Collections.emptyList())),
            Collections.emptyList(), Collections.emptyList(), location)),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    assertEquals("message Message {\n"
        + "  oneof hi {\n"
        + "    string name = 1;\n"
        + "  }\n"
        + "}\n", element.toSchema());
  }

  @Test public void oneOfWithGroupToSchema() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(),
        Collections.singletonList(new OneOfElement("hi", "",
            Collections.singletonList(new FieldElement(location, null, "string", "name", null,
                null, 1, "", Collections.emptyList())),
            Collections.singletonList(new GroupElement(null, location.at(5, 5), "Stuff", 3, "",
                Arrays.asList(
                    new FieldElement(location.at(6, 7), Label.OPTIONAL, "int32",
                        "result_per_page", null, null, 4, "", Collections.emptyList()),
                    new FieldElement(location.at(7, 7), Label.OPTIONAL, "int32", "page_count",
                        null, null, 5, "", Collections.emptyList())))),
            Collections.emptyList(), location)),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    // spotless:off because spotless will remove the indents (trailing spaces) in the oneof block.
    assertEquals("message Message {\n"
        + "  oneof hi {\n"
        + "    string name = 1;\n"
        + "  \n"
        + "    group Stuff = 3 {\n"
        + "      optional int32 result_per_page = 4;\n"
        + "      optional int32 page_count = 5;\n"
        + "    }\n"
        + "  }\n"
        + "}\n", element.toSchema());
    // spotless:on
  }

  @Test public void addMultipleOneOfs() {
    OneOfElement hi = new OneOfElement("hi", "",
        Collections.singletonList(new FieldElement(location, null, "string", "name", null, null,
            1, "", Collections.emptyList())),
        Collections.emptyList(), Collections.emptyList(), location);
    OneOfElement hey = new OneOfElement("hey", "",
        Collections.singletonList(new FieldElement(location, null, "string", "city", null, null,
            2, "", Collections.emptyList())),
        Collections.emptyList(), Collections.emptyList(), location);
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Arrays.asList(hi, hey), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    assertEquals(2, element.getOneOfs().size());
  }

  @Test public void reservedToSchema() {
    MessageElement element = new MessageElement(location, "Message", "",
        Collections.emptyList(), Collections.emptyList(),
        Arrays.asList(
            new ReservedElement(location, "",
                Arrays.asList(10, new int[] {12, 14}, "foo")),
            new ReservedElement(location, "", Collections.singletonList(10)),
            new ReservedElement(location, "",
                Collections.singletonList(new int[] {12, MAX_TAG_VALUE})),
            new ReservedElement(location, "", Collections.singletonList("foo"))),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    assertEquals("message Message {\n"
        + "  reserved 10, 12 to 14, \"foo\";\n"
        + "  reserved 10;\n"
        + "  reserved 12 to max;\n"
        + "  reserved \"foo\";\n"
        + "}\n", element.toSchema());
  }

  @Test public void groupToSchema() {
    MessageElement element = new MessageElement(location.at(1, 1), "SearchResponse", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(new GroupElement(Label.REPEATED, location.at(2, 3), "Result",
            1, "",
            Arrays.asList(
                new FieldElement(location.at(3, 5), Label.REQUIRED, "string", "url", null, null,
                    2, "", Collections.emptyList()),
                new FieldElement(location.at(4, 5), Label.OPTIONAL, "string", "title", null,
                    null, 3, "", Collections.emptyList()),
                new FieldElement(location.at(5, 5), Label.REPEATED, "string", "snippets", null,
                    null, 4, "", Collections.emptyList())))),
        Collections.emptyList());
    assertEquals("message SearchResponse {\n"
        + "  repeated group Result = 1 {\n"
        + "    required string url = 2;\n"
        + "    optional string title = 3;\n"
        + "    repeated string snippets = 4;\n"
        + "  }\n"
        + "}\n", element.toSchema());
  }

  @Test public void multipleEverythingToSchema() {
    FieldElement field1 = new FieldElement(location.at(1, 2), Label.REQUIRED, "string", "name",
        null, null, 2, "", Collections.emptyList());
    FieldElement oneOf1Field1 = new FieldElement(location.at(1, 1), null, "string", "namey",
        null, null, 1, "", Collections.emptyList());
    FieldElement oneOf1Field2 = new FieldElement(location.at(2, 1), null, "int32", "aField",
        null, null, 5, "", Collections.emptyList());

    OneOfElement oneOf1 = new OneOfElement("thingy", "",
        Arrays.asList(oneOf1Field1, oneOf1Field2), Collections.emptyList(),
        Collections.emptyList(), location);
    FieldElement field2 = new FieldElement(location.at(2, 3), Label.REQUIRED, "bool",
        "other_name", null, null, 3, "", Collections.emptyList());
    FieldElement oneOf2Field = new FieldElement(location.at(3, 0), null, "string", "namer",
        null, null, 4, "", Collections.emptyList());
    OneOfElement oneOf2 = new OneOfElement("thinger", "",
        Collections.singletonList(oneOf2Field), Collections.emptyList(), Collections.emptyList(),
        location);
    ExtensionsElement extensions1 = new ExtensionsElement(location.at(5, 0), "",
        Collections.singletonList(new int[] {500, 501}), Collections.emptyList());
    ExtensionsElement extensions2 = new ExtensionsElement(location.at(6, 2), "",
        Collections.singletonList(503), Collections.emptyList());
    MessageElement nested = new MessageElement(location.at(7, 1), "Nested", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.singletonList(field1), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    OptionElement option = OptionElement.create("kit", STRING, "kat");
    MessageElement element = new MessageElement(location.at(0, 0), "Message", "",
        Collections.singletonList(nested), Collections.singletonList(option),
        Collections.emptyList(), Arrays.asList(field1, field2), Arrays.asList(oneOf1, oneOf2),
        Arrays.asList(extensions1, extensions2), Collections.emptyList(),
        Collections.emptyList());
    assertEquals("message Message {\n"
        + "  option kit = \"kat\";\n"
        + "\n"
        + "  oneof thingy {\n"
        + "    string namey = 1;\n"
        + "    int32 aField = 5;\n"
        + "  }\n"
        + "\n"
        + "  required string name = 2;\n"
        + "\n"
        + "  required bool other_name = 3;\n"
        + "\n"
        + "  oneof thinger {\n"
        + "    string namer = 4;\n"
        + "  }\n"
        + "\n"
        + "  extensions 500 to 501;\n"
        + "  extensions 503;\n"
        + "\n"
        + "  message Nested {\n"
        + "    required string name = 2;\n"
        + "  }\n"
        + "}\n", element.toSchema());
  }

  @Test public void fieldToSchema() {
    FieldElement field = new FieldElement(location, Label.REQUIRED, "string", "name", null, null,
        1, "", Collections.emptyList());
    assertEquals("required string name = 1;\n", field.toSchema());
  }

  @Test public void fieldWithDefaultStringToSchemaInProto2() {
    FieldElement field = new FieldElement(location, Label.REQUIRED, "string", "name", "benoît",
        null, 1, "", Collections.emptyList());
    assertEquals("required string name = 1 [default = \"benoît\"];\n", field.toSchema());
  }

  @Test public void fieldWithDefaultNumberToSchema() {
    FieldElement field = new FieldElement(location, Label.REQUIRED, "int32", "age", "34", null,
        1, "", Collections.emptyList());
    assertEquals("required int32 age = 1 [default = 34];\n", field.toSchema());
  }

  @Test public void fieldWithDefaultBoolToSchema() {
    FieldElement field = new FieldElement(location, Label.REQUIRED, "bool", "human", "true",
        null, 1, "", Collections.emptyList());
    assertEquals("required bool human = 1 [default = true];\n", field.toSchema());
  }

  @Test public void oneOfFieldToSchema() {
    FieldElement field = new FieldElement(location, null, "string", "name", null, null, 1, "",
        Collections.emptyList());
    assertEquals("string name = 1;\n", field.toSchema());
  }

  @Test public void fieldWithDocumentationToSchema() {
    FieldElement field = new FieldElement(location, Label.REQUIRED, "string", "name", null,
        null, 1, "Hello", Collections.emptyList());
    assertEquals("// Hello\n"
        + "required string name = 1;\n", field.toSchema());
  }

  @Test public void fieldWithOneOptionToSchema() {
    FieldElement field = new FieldElement(location, Label.REQUIRED, "string", "name", null,
        null, 1, "",
        Collections.singletonList(OptionElement.create("kit", STRING, "kat")));
    assertEquals("required string name = 1 [kit = \"kat\"];\n", field.toSchema());
  }

  @Test public void fieldWithMoreThanOneOptionToSchema() {
    FieldElement field = new FieldElement(location, Label.REQUIRED, "string", "name", null,
        null, 1, "",
        Arrays.asList(
            OptionElement.create("kit", STRING, "kat"),
            OptionElement.create("dup", STRING, "lo")));
    assertEquals("required string name = 1 [\n"
        + "  kit = \"kat\",\n"
        + "  dup = \"lo\"\n"
        + "];\n", field.toSchema());
  }

  @Test public void oneOfWithOptions() {
    String expected = "oneof page_info {\n"
        + "  option (my_option) = true;\n"
        + "\n"
        + "  int32 page_number = 2;\n"
        + "  int32 result_per_page = 3;\n"
        + "}\n";
    OneOfElement oneOf = new OneOfElement("page_info", "",
        Arrays.asList(
            new FieldElement(location.at(4, 5), null, "int32", "page_number", null, null, 2, "",
                Collections.emptyList()),
            new FieldElement(location.at(5, 5), null, "int32", "result_per_page", null, null, 3,
                "", Collections.emptyList())),
        Collections.emptyList(),
        Collections.singletonList(OptionElement.create("my_option", BOOLEAN, "true", true)),
        location);
    assertEquals(expected, oneOf.toSchema());
  }
}
