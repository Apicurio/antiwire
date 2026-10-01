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
import org.junit.jupiter.api.Test;

/** Upstream OneOfElementTest translated (assertk to JUnit 5). */
public class OneOfElementTest {
  private final Location location = Location.get("file.proto");

  @Test public void oneOfWithOptions() {
    // spotless:off because spotless will remove the indents (trailing spaces) in the oneof block.
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
    // spotless:on

    MessageElement expectedElement = new MessageElement(location.at(1, 1), "Message", "",
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(),
        Arrays.asList(
            new OneOfElement("choice", "You have to take one.",
                Arrays.asList(
                    new FieldElement(location.at(7, 5), null, "string", "one", null, null, 1, "",
                        Collections.emptyList()),
                    new FieldElement(location.at(8, 5), null, "string", "two", null, null, 2, "",
                        Collections.emptyList())),
                Collections.emptyList(),
                Arrays.asList(
                    new OptionElement("my_oneof_option", STRING, "Well done", true),
                    new OptionElement("my_other_oneof_option", STRING, "Yet again", true)),
                location.at(3, 3))),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    MessageElement element = (MessageElement) ProtoParser.parse(location, elementAsString).types.get(0);
    assertEquals(expectedElement, element);
    assertEquals(elementAsString, element.toSchema());
  }
}
