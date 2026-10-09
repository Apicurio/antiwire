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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.squareup.wire.schema.Field.Label;
import com.squareup.wire.schema.Location;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** Upstream ExtendElementTest translated (assertk to JUnit 5). */
public class ExtendElementTest {
  private final Location location = Location.get("file.proto");

  @Test public void emptyToSchema() {
    ExtendElement extend = new ExtendElement(location, "Name", "", Collections.emptyList());
    assertEquals("extend Name {}\n", extend.toSchema());
  }

  @Test public void simpleToSchema() {
    ExtendElement extend = new ExtendElement(location, "Name", "",
        Collections.singletonList(
            new FieldElement(location, Label.REQUIRED, "string", "name", null, null, 1, "",
                Collections.emptyList())));
    assertEquals("extend Name {\n"
        + "  required string name = 1;\n"
        + "}\n", extend.toSchema());
  }

  @Test public void addMultipleFields() {
    FieldElement firstName = new FieldElement(location, Label.REQUIRED, "string", "first_name",
        null, null, 1, "", Collections.emptyList());
    FieldElement lastName = new FieldElement(location, Label.REQUIRED, "string", "last_name",
        null, null, 2, "", Collections.emptyList());
    ExtendElement extend = new ExtendElement(location, "Name", "",
        java.util.Arrays.asList(firstName, lastName));
    assertEquals(2, extend.getFields().size());
  }

  @Test public void simpleWithDocumentationToSchema() {
    ExtendElement extend = new ExtendElement(location, "Name", "Hello",
        Collections.singletonList(
            new FieldElement(location, Label.REQUIRED, "string", "name", null, null, 1, "",
                Collections.emptyList())));
    assertEquals("// Hello\n"
        + "extend Name {\n"
        + "  required string name = 1;\n"
        + "}\n", extend.toSchema());
  }

  @Test public void jsonNameToSchema() {
    ExtendElement extend = new ExtendElement(location, "Name", "",
        Collections.singletonList(
            new FieldElement(location, Label.REQUIRED, "string", "name", null, "my_json", 1, "",
                Collections.emptyList())));
    assertEquals("extend Name {\n"
        + "  required string name = 1 [json_name = \"my_json\"];\n"
        + "}\n", extend.toSchema());
  }

  @Test public void defaultIsSetInProto2File() {
    ExtendElement extend = new ExtendElement(location, "Name", "Hello",
        Collections.singletonList(
            new FieldElement(location, Label.REQUIRED, "string", "name", "defaultValue", null, 1,
                "", Collections.emptyList())));
    assertEquals("// Hello\n"
        + "extend Name {\n"
        + "  required string name = 1 [default = \"defaultValue\"];\n"
        + "}\n", extend.toSchema());
  }
}
