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
package com.squareup.wire.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Upstream commonTest translated (assertk to JUnit 5); see UPSTREAM-TEST-ADAPTATIONS.md. The
 * wireVersion case maps to Internal.WIRE_VERSION, this port's replacement for the generated
 * BuildConfig constant.
 */
public class InternalTest {
  @Test public void countNonNull() {
    assertEquals(0, Internal.countNonNull(null, null));
    assertEquals(1, Internal.countNonNull("xx", null));
    assertEquals(2, Internal.countNonNull("xx", "xx"));
    assertEquals(2, Internal.countNonNull("xx", "xx", null));
    assertEquals(3, Internal.countNonNull("xx", "xx", "xx"));
    assertEquals(3, Internal.countNonNull("xx", "xx", "xx", null));
    assertEquals(4, Internal.countNonNull("xx", "xx", "xx", "xx"));
    assertEquals(4, Internal.countNonNull("xx", "xx", "xx", "xx", null));
    assertEquals(5, Internal.countNonNull("xx", "xx", "xx", "xx", "xx"));
  }

  @Test public void sanitizeStrings() {
    assertEquals("\\,", Internal.sanitize(","));
    assertEquals("\\{", Internal.sanitize("{"));
    assertEquals("\\}", Internal.sanitize("}"));
    assertEquals("\\[", Internal.sanitize("["));
    assertEquals("\\]", Internal.sanitize("]"));
    assertEquals("\\\\", Internal.sanitize("\\"));
    assertEquals("Hi\\, I'm \\{CURRENT_HOST\\} dax!", Internal.sanitize("Hi, I'm {CURRENT_HOST} dax!"));

    assertEquals("[\\,, \\{, \\}, \\[, \\], \\\\]",
        Internal.sanitize(Arrays.asList(",", "{", "}", "[", "]", "\\")));
  }

  @Test public void lowerCamelCase() {
    assertEquals("", Internal.camelCase("", false));
    assertEquals("", Internal.camelCase("_", false));
    assertEquals("", Internal.camelCase("__", false));
    assertEquals("aBC", Internal.camelCase("a_b_c", false));
    assertEquals("aBC", Internal.camelCase("a_b_c_", false));
    assertEquals("ABC", Internal.camelCase("_a_b_c_", false));
    assertEquals("ABC", Internal.camelCase("ABC", false));
    assertEquals("ABC", Internal.camelCase("A_B_C", false));
    assertEquals("ABC", Internal.camelCase("A__B__C", false));
    assertEquals("ABC", Internal.camelCase("A__B__C__", false));
    assertEquals("ABC", Internal.camelCase("__A__B__C__", false));
    assertEquals("HelloWorld", Internal.camelCase("HelloWorld", false));
    assertEquals("helloWorld", Internal.camelCase("helloWorld", false));
    assertEquals("helloWorld", Internal.camelCase("hello_world", false));
    assertEquals("HelloWorld", Internal.camelCase("_hello_world", false));
    assertEquals("HelloWorld", Internal.camelCase("_hello_world_", false));
    assertEquals("🦕", Internal.camelCase("🦕", false));
    assertEquals("hello🦕world", Internal.camelCase("hello_🦕world", false));
    assertEquals("hello🦕World", Internal.camelCase("hello_🦕_world", false));
  }

  @Test public void upperCamelCase() {
    assertEquals("", Internal.camelCase("", true));
    assertEquals("", Internal.camelCase("_", true));
    assertEquals("", Internal.camelCase("__", true));
    assertEquals("ABC", Internal.camelCase("a_b_c", true));
    assertEquals("ABC", Internal.camelCase("a_b_c_", true));
    assertEquals("ABC", Internal.camelCase("ABC", true));
    assertEquals("ABC", Internal.camelCase("A_B_C", true));
    assertEquals("ABC", Internal.camelCase("A__B__C", true));
    assertEquals("ABC", Internal.camelCase("A__B__C__", true));
    assertEquals("ABC", Internal.camelCase("__A__B__C__", true));
    assertEquals("HelloWorld", Internal.camelCase("HelloWorld", true));
    assertEquals("HelloWorld", Internal.camelCase("helloWorld", true));
    assertEquals("HelloWorld", Internal.camelCase("hello_world", true));
    assertEquals("HelloWorld", Internal.camelCase("_hello_world", true));
    assertEquals("HelloWorld", Internal.camelCase("_hello_world_", true));
    assertEquals("🦕", Internal.camelCase("🦕", true));
    assertEquals("Hello🦕world", Internal.camelCase("hello_🦕world", true));
    assertEquals("Hello🦕World", Internal.camelCase("hello_🦕_world", true));
  }

  @Test public void versionIsExposed() {
    assertNotEquals(null, Internal.WIRE_VERSION);
  }
}
