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

import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.SchemaUtil;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** Upstream EnumElementTest translated (assertk to JUnit 5). */
public class EnumElementTest {
  private final Location location = Location.get("file.proto");

  @Test public void emptyToSchema() {
    EnumElement element = new EnumElement(location, "Enum", "", Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    assertEquals("enum Enum {}\n", element.toSchema());
  }

  @Test public void simpleToSchema() {
    EnumElement element = new EnumElement(location, "Enum", "", Collections.emptyList(),
        Arrays.asList(constant("ONE", 1), constant("TWO", 2), constant("SIX", 6)),
        Collections.emptyList());
    assertEquals("enum Enum {\n"
        + "  ONE = 1;\n"
        + "  TWO = 2;\n"
        + "  SIX = 6;\n"
        + "}\n", element.toSchema());
  }

  @Test public void addMultipleConstants() {
    EnumConstantElement one = constant("ONE", 1);
    EnumConstantElement two = constant("TWO", 2);
    EnumConstantElement six = constant("SIX", 6);
    EnumElement element = new EnumElement(location, "Enum", "", Collections.emptyList(),
        Arrays.asList(one, two, six), Collections.emptyList());
    assertEquals(3, element.constants.size());
  }

  @Test public void simpleWithOptionsToSchema() {
    EnumElement element = new EnumElement(location, "Enum", "",
        Collections.singletonList(OptionElement.create("kit", STRING, "kat")),
        Arrays.asList(constant("ONE", 1), constant("TWO", 2), constant("SIX", 6)),
        Collections.emptyList());
    assertEquals("enum Enum {\n"
        + "  option kit = \"kat\";\n"
        + "  ONE = 1;\n"
        + "  TWO = 2;\n"
        + "  SIX = 6;\n"
        + "}\n", element.toSchema());
  }

  @Test public void addMultipleOptions() {
    OptionElement kitKat = OptionElement.create("kit", STRING, "kat");
    OptionElement fooBar = OptionElement.create("foo", STRING, "bar");
    EnumElement element = new EnumElement(location, "Enum", "",
        Arrays.asList(kitKat, fooBar),
        Collections.singletonList(constant("ONE", 1)),
        Collections.emptyList());
    assertEquals(2, element.options.size());
  }

  @Test public void simpleWithDocumentationToSchema() {
    EnumElement element = new EnumElement(location, "Enum", "Hello", Collections.emptyList(),
        Arrays.asList(constant("ONE", 1), constant("TWO", 2), constant("SIX", 6)),
        Collections.emptyList());
    assertEquals("// Hello\n"
        + "enum Enum {\n"
        + "  ONE = 1;\n"
        + "  TWO = 2;\n"
        + "  SIX = 6;\n"
        + "}\n", element.toSchema());
  }

  @Test public void simpleWithReservedToSchema() {
    EnumElement element = new EnumElement(location, "Enum", "", Collections.emptyList(),
        Arrays.asList(constant("ONE", 1), constant("TWO", 2), constant("SIX", 6)),
        Arrays.asList(
            new ReservedElement(location, "",
                Arrays.<Object>asList(10, new int[] {12, 14}, "FOO")),
            new ReservedElement(location, "", Collections.<Object>singletonList(10)),
            new ReservedElement(location, "",
                Collections.singletonList(new int[] {25, SchemaUtil.MAX_TAG_VALUE})),
            new ReservedElement(location, "", Collections.<Object>singletonList("FOO"))));
    assertEquals("enum Enum {\n"
        + "  reserved 10, 12 to 14, \"FOO\";\n"
        + "  reserved 10;\n"
        + "  reserved 25 to max;\n"
        + "  reserved \"FOO\";\n"
        + "  ONE = 1;\n"
        + "  TWO = 2;\n"
        + "  SIX = 6;\n"
        + "}\n", element.toSchema());
  }

  @Test public void fieldToSchema() {
    EnumConstantElement value = constant("NAME", 1);
    assertEquals("NAME = 1;\n", value.toSchema());
  }

  @Test public void fieldWithDocumentationToSchema() {
    EnumConstantElement value = new EnumConstantElement(location, "NAME", 1, "Hello",
        Collections.emptyList());
    assertEquals("// Hello\n"
        + "NAME = 1;\n", value.toSchema());
  }

  @Test public void fieldWithOptionsToSchema() {
    EnumConstantElement value = new EnumConstantElement(location, "NAME", 1, "",
        Arrays.asList(
            OptionElement.create("kit", STRING, "kat", true),
            OptionElement.create("tit", STRING, "tat")));
    assertEquals("NAME = 1 [\n"
        + "  (kit) = \"kat\",\n"
        + "  tit = \"tat\"\n"
        + "];\n", value.toSchema());
  }

  private EnumConstantElement constant(String name, int tag) {
    return new EnumConstantElement(location, name, tag, "", Collections.emptyList());
  }
}
