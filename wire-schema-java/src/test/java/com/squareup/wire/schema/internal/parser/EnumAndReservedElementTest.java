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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Upstream EnumElementTest (representative cases) and Reserved/Extensions formatting. */
public class EnumAndReservedElementTest {
  private final Location location = Location.get("file.proto");

  private static List<OptionElement> noOptions() {
    return Collections.emptyList();
  }

  private EnumConstantElement constant(String name, int tag) {
    return new EnumConstantElement(null, name, tag, "", Collections.emptyList());
  }

  @Test public void emptyToSchema() {
    EnumElement element = new EnumElement(location, "Enum", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    assertEquals("enum Enum {}\n", element.toSchema());
  }

  @Test public void simpleToSchema() {
    EnumElement element = new EnumElement(location, "Enum", "",
        noOptions(),
        Arrays.asList(constant("ONE", 1), constant("TWO", 2), constant("SIX", 6)),
        Collections.emptyList());
    assertEquals("enum Enum {\n"
        + "  ONE = 1;\n"
        + "  TWO = 2;\n"
        + "  SIX = 6;\n"
        + "}\n", element.toSchema());
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

  @Test public void simpleWithDocumentationToSchema() {
    EnumElement element = new EnumElement(location, "Enum", "Hello",
        noOptions(),
        Arrays.asList(constant("ONE", 1), constant("TWO", 2), constant("SIX", 6)),
        Collections.emptyList());
    assertEquals("// Hello\n"
        + "enum Enum {\n"
        + "  ONE = 1;\n"
        + "  TWO = 2;\n"
        + "  SIX = 6;\n"
        + "}\n", element.toSchema());
  }

  @Test public void reservedWithMax() {
    ReservedElement element = new ReservedElement(location, "",
        Arrays.asList(10, new int[] {12, (1 << 29) - 1}));
    assertEquals("reserved 10, 12 to max;\n", element.toSchema());
  }

  @Test public void reservedWithRange() {
    ReservedElement element = new ReservedElement(location, "",
        Collections.singletonList(new int[] {5, 8}));
    assertEquals("reserved 5 to 8;\n", element.toSchema());
  }

  @Test public void reservedWithName() {
    ReservedElement element = new ReservedElement(location, "",
        Arrays.<Object>asList("OLD", "NEW"));
    assertEquals("reserved \"OLD\", \"NEW\";\n", element.toSchema());
  }

  @Test public void extensionsWithRange() {
    ExtensionsElement element = new ExtensionsElement(location, "",
        Collections.singletonList(new int[] {100, (1 << 29) - 1}), Collections.emptyList());
    assertEquals("extensions 100 to max;\n", element.toSchema());
  }
}
