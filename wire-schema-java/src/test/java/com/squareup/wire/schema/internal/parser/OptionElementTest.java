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
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.LIST;
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.MAP;
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.OPTION;
import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Upstream OptionElementTest translated (assertk to JUnit 5). */
public class OptionElementTest {
  @Test public void simpleToSchema() {
    OptionElement option = OptionElement.create("foo", STRING, "bar");
    assertEquals("foo = \"bar\"", option.toSchema());
  }

  @Test public void nestedToSchema() {
    OptionElement option = OptionElement.create("foo.boo", OPTION,
        OptionElement.create("bar", STRING, "baz"), true);
    assertEquals("(foo.boo).bar = \"baz\"", option.toSchema());
  }

  @Test public void listToSchema() {
    OptionElement option = OptionElement.create("foo", LIST,
        Arrays.asList(
            OptionElement.create("ping", STRING, "pong", true),
            OptionElement.create("kit", STRING, "kat")),
        true);
    assertEquals("(foo) = [\n  (ping) = \"pong\",\n  kit = \"kat\"\n]", option.toSchema());
  }

  @Test public void mapToSchema() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("ping", "pong");
    map.put("kit", Arrays.asList("kat", "kot"));
    OptionElement option = OptionElement.create("foo", MAP, map);
    assertEquals("foo = {\n"
        + "  ping: \"pong\",\n"
        + "  kit: [\n"
        + "    \"kat\",\n"
        + "    \"kot\"\n"
        + "  ]\n"
        + "}", option.toSchema());
  }

  @Test public void booleanToSchema() {
    OptionElement option = OptionElement.create("foo", BOOLEAN, "false");
    assertEquals("foo = false", option.toSchema());
  }

  // TASK-13 adaptation: retained from the earlier partial OptionElementTest; not present in
  // upstream OptionElementTest at 7.1.0, kept so the partial's coverage is not lost.
  @Test public void escapedStringToSchema() {
    OptionElement option = OptionElement.create("foo", STRING, "b\"ar\\baz\n");
    assertEquals("foo = \"b\\\"ar\\\\baz\\n\"", option.toSchema());
  }
}
