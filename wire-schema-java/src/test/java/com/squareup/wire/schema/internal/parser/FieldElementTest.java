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

import com.squareup.wire.schema.Field.Label;
import com.squareup.wire.schema.Location;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** Upstream FieldElementTest translated (assertk to JUnit 5). */
public class FieldElementTest {
  private final Location location = Location.get("file.proto");

  @Test public void field() {
    FieldElement field = new FieldElement(location, Label.OPTIONAL, "CType", "ctype", null,
        null, 1, "", Arrays.asList(
            OptionElement.create("default", ENUM, "TEST"),
            OptionElement.create("deprecated", BOOLEAN, "true")));

    // TASK-13 adaptation: assertk containsOnly (order-insensitive) maps to list equality;
    // the order is fixed by construction so both assertions accept the same values.
    assertEquals(Arrays.asList(
        OptionElement.create("default", ENUM, "TEST"),
        OptionElement.create("deprecated", BOOLEAN, "true")), field.options);
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
        "defaultValue", "my_json", 1, "", Collections.emptyList());

    assertEquals("required string name = 1 [\n"
        + "  default = \"defaultValue\",\n"
        + "  json_name = \"my_json\"\n"
        + "];\n", field.toSchema());
  }

  @Test public void jsonName() {
    FieldElement field = new FieldElement(location, Label.REQUIRED, "string", "name", null,
        "my_json", 1, "", Collections.emptyList());

    assertEquals("required string name = 1 [json_name = \"my_json\"];\n", field.toSchema());
  }

  // TASK-13 adaptation: retained from the earlier partial FieldAndOneOfElementTest; not present
  // in upstream FieldElementTest at 7.1.0, kept so the partial's coverage is not lost.
  @Test public void deprecated() {
    OptionElement kitKat = OptionElement.create("kit", STRING, "kat");
    FieldElement field = new FieldElement(location, Label.OPTIONAL, "string", "kit", null, null,
        1, "", Collections.singletonList(kitKat));

    assertEquals("optional string kit = 1 [kit = \"kat\"];\n", field.toSchema());
  }
}
