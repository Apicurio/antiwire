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
package com.squareup.wire.schema.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Upstream UtilTest translated (assertk to JUnit 5). Upstream's Util.kt appendIndented and
 * appendDocumentation StringBuilder extensions map to SchemaUtil statics taking the builder
 * first.
 */
public class UtilTest {
  @Test
  public void indentationTest() {
    String input = "Foo\nBar\nBaz";
    String expected = "  Foo\n  Bar\n  Baz\n";
    StringBuilder actual = new StringBuilder();
    SchemaUtil.appendIndented(actual, input);
    assertEquals(expected, actual.toString());
  }

  @Test
  public void documentationTest() {
    String input = "Foo\nBar\nBaz";
    String expected = "// Foo\n// Bar\n// Baz\n";
    StringBuilder actual = new StringBuilder();
    SchemaUtil.appendDocumentation(actual, input);
    assertEquals(expected, actual.toString());
  }
}
