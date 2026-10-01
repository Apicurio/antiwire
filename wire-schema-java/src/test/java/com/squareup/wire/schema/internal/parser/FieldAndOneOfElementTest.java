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
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.ENUM;
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.squareup.wire.schema.Field.Label;
import com.squareup.wire.schema.Location;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** Upstream FieldElementTest and OneOfElementTest translated (assertk to JUnit 5). */
public class FieldAndOneOfElementTest {
  private final Location location = Location.get("file.proto");

  @Test public void field() {
    FieldElement field = new FieldElement(location, Label.OPTIONAL, "CType", "ctype", null,
        null, 1, "", Arrays.asList(
            OptionElement.create("default", ENUM, "TEST"),
            OptionElement.create("deprecated", BOOLEAN, "true")));

    assertEquals(2, field.options.size());
    assertEquals("default", field.options.get(0).name);
  }

  @Test public void addMultipleOptions() {
    OptionElement kitKat = OptionElement.create("kit", STRING, "kat");
    OptionElement fooBar = OptionElement.create("foo", STRING, "bar");
    FieldElement field = new FieldElement(location, Label.REQUIRED, "string", "name", null,
        null, 1, "", Arrays.asList(kitKat, fooBar));

    assertEquals(2, field.options.size());
  }

  @Test public void defaultIsSet() {
    FieldElement field = new FieldElement(location, Label.REQUIRED, "string", "name",
        "defaultValue", null, 1, "", Collections.emptyList());

    assertEquals("required string name = 1 [default = \"defaultValue\"];\n", field.toSchema());
  }

  @Test public void jsonNameAndDefaultValue() {
    FieldElement field = new FieldElement(location, Label.REQUIRED, "string", "name",
        "defaultValue", "json_value", 1, "", Collections.emptyList());

    assertEquals("required string name = 1 [\n"
        + "  default = \"defaultValue\",\n"
        + "  json_name = \"json_value\"\n"
        + "];\n", field.toSchema());
  }

  @Test public void deprecated() {
    OptionElement kitKat = OptionElement.create("kit", STRING, "kat");
    FieldElement field = new FieldElement(location, Label.OPTIONAL, "string", "kit", null, null,
        1, "", Collections.singletonList(kitKat));

    assertEquals("optional string kit = 1 [kit = \"kat\"];\n", field.toSchema());
  }

  @Test public void oneOfWithOptions() {
    String elementAsString = "message Message {\n"
        + "  // You have to take one.\n"
        + "  oneof choice {\n"
        + "    option (my_oneof_option) = \"Well done\";\n"
        + "    option (my_other_oneof_option) = \"Yet again\";\n"
        + "  \n"
        + "    string one = 1;\n"
        + "    string two = 2;\n"
        + "  }\n"
        + "}\n";

    Object parsed = ProtoParser.parse(location, elementAsString).types.get(0);
    MessageElement element = (MessageElement) parsed;
    assertEquals("Message", element.name);
    assertEquals(1, element.oneOfs.size());
    OneOfElement choice = element.oneOfs.get(0);
    assertEquals("choice", choice.name);
    assertEquals("You have to take one.", choice.documentation);
    assertEquals(2, choice.fields.size());
    assertEquals(2, choice.options.size());
    assertEquals("my_oneof_option", choice.options.get(0).name);
    assertNull(choice.options.get(0).value.equals("Well done") ? null : "x");
  }
}
