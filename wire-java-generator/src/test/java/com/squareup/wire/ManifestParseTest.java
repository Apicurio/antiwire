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
package com.squareup.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.squareup.wire.schema.WireRun;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * TASK-16 adoption of upstream ManifestParseTest:
 * {@code wire-compiler/src/test/java/com/squareup/wire/ManifestParseTest.kt} at square/wire tag
 * 7.1.0. Inputs and expected module graphs are verbatim; upstream calls the {@code internal}
 * top-level {@code parseManifestModules}, which the port keeps as a package-private static on
 * {@link WireCompiler}. The unknown-key case asserts upstream kaml's exact "Unknown property"
 * wording, which the port's parser reproduces.
 */
public class ManifestParseTest {
  @Test public void parseFormat() {
    String yaml = ""
        + "one:\n"
        + "  dependencies:\n"
        + "    - two\n"
        + "    - three\n"
        + "  roots:\n"
        + "    - example.A\n"
        + "    - example.B\n"
        + "  prunes:\n"
        + "    - example.C\n"
        + "    - example.D\n"
        + "two: {}\n"
        + "three: {}\n";

    Map<String, WireRun.Module> modules = WireCompiler.parseManifestModules(yaml);
    assertEquals(3, modules.size());
    assertTrue(modules.containsKey("one"));
    assertTrue(modules.containsKey("two"));
    assertTrue(modules.containsKey("three"));

    WireRun.Module one = modules.get("one");
    assertEquals(2, one.dependencies().size());
    assertTrue(one.dependencies().contains("two"));
    assertTrue(one.dependencies().contains("three"));
    assertEquals(2, one.pruningRules().roots().size());
    assertTrue(one.pruningRules().roots().contains("example.A"));
    assertTrue(one.pruningRules().roots().contains("example.B"));
    assertEquals(2, one.pruningRules().prunes().size());
    assertTrue(one.pruningRules().prunes().contains("example.C"));
    assertTrue(one.pruningRules().prunes().contains("example.D"));
  }

  @Test public void parseFormatFailsOnUnknownKey() {
    String yaml = ""
        + "one:\n"
        + "  includes:\n"
        + "    - example.A\n";

    try {
      WireCompiler.parseManifestModules(yaml);
      fail();
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Unknown property 'includes'"), e.getMessage());
    }
  }

  @Test public void outOfOrderDependency() {
    String yaml = ""
        + "one:\n"
        + "  dependencies:\n"
        + "    - two\n"
        + "two: {}\n";

    Map<String, WireRun.Module> modules = WireCompiler.parseManifestModules(yaml);
    assertEquals(2, modules.size());
    assertTrue(modules.containsKey("one"));
    assertTrue(modules.containsKey("two"));
    assertEquals(java.util.Collections.singleton("two"), modules.get("one").dependencies());
  }
}
