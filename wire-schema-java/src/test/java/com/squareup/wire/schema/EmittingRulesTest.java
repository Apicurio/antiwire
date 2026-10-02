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
package com.squareup.wire.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Upstream EmittingRulesTest translated (assertk to JUnit 5). */
public class EmittingRulesTest {
  @Test public void enclosing() {
    assertEquals("a.b.*", EmittingRules.enclosing("a.b.Outer"));
    assertEquals("a.*", EmittingRules.enclosing("a.b.*"));
    assertEquals("*", EmittingRules.enclosing("a.*"));
    assertEquals(null, EmittingRules.enclosing("*"));
  }

  @Test public void enclosingOnNestedClass() {
    assertEquals("a.b.Outer.*", EmittingRules.enclosing("a.b.Outer.Inner"));
    assertEquals("a.b.*", EmittingRules.enclosing("a.b.Outer.*"));
  }

  @Test public void empty() {
    EmittingRules rules = new EmittingRules.Builder().build();
    assertTrue(rules.includes(ProtoType.get("a.b.Message")));
  }

  /** Note that including a type includes nested members, but not nested types. */
  @Test public void includeType() {
    EmittingRules rules = new EmittingRules.Builder()
        .include("a.b.Message")
        .build();
    assertTrue(rules.includes(ProtoType.get("a.b.Message")));
    assertFalse(rules.includes(ProtoType.get("a.b.Message.Nested")));
    assertFalse(rules.includes(ProtoType.get("a.b.Another")));
  }

  @Test public void includePackage() {
    EmittingRules rules = new EmittingRules.Builder()
        .include("a.b.*")
        .build();
    assertTrue(rules.includes(ProtoType.get("a.b.Message")));
    assertTrue(rules.includes(ProtoType.get("a.b.c.Message")));
    assertFalse(rules.includes(ProtoType.get("a.c.Another")));
  }

  @Test public void includeAll() {
    EmittingRules rules = new EmittingRules.Builder()
        .include("*")
        .build();
    assertTrue(rules.includes(ProtoType.get("a.b.Message")));
  }

  @Test public void excludeType() {
    EmittingRules rules = new EmittingRules.Builder()
        .exclude("a.b.Message")
        .build();
    assertFalse(rules.includes(ProtoType.get("a.b.Message")));
    assertTrue(rules.includes(ProtoType.get("a.b.Another")));
  }

  @Test public void excludePackage() {
    EmittingRules rules = new EmittingRules.Builder()
        .exclude("a.b.*")
        .build();
    assertFalse(rules.includes(ProtoType.get("a.b.Message")));
    assertFalse(rules.includes(ProtoType.get("a.b.c.Message")));
    assertTrue(rules.includes(ProtoType.get("a.c.Another")));
  }

  @Test public void mostPreciseIncludeTakesPrecedenceOverExclude() {
    EmittingRules rules = new EmittingRules.Builder()
        .exclude("a.b.*")
        .include("a.b.Message")
        .build();
    assertTrue(rules.includes(ProtoType.get("a.b.Message")));
    assertFalse(rules.includes(ProtoType.get("a.b.Another")));
    assertFalse(rules.includes(ProtoType.get("a.c.YetAnother")));
  }

  @Test public void mostPreciseExcludeTakesPrecedenceOverInclude() {
    EmittingRules rules = new EmittingRules.Builder()
        .exclude("a.b.Message")
        .include("a.b.*")
        .build();
    assertFalse(rules.includes(ProtoType.get("a.b.Message")));
    assertTrue(rules.includes(ProtoType.get("a.b.Another")));
    assertFalse(rules.includes(ProtoType.get("a.c.YetAnother")));
  }

  @Test public void trackingUnusedIncludes() {
    EmittingRules rules = new EmittingRules.Builder()
        .include("a.*")
        .include("b.IncludedType")
        .build();
    assertEquals(Arrays.asList("a.*", "b.IncludedType"),
        new ArrayList<>(rules.unusedIncludes()));

    rules.includes(ProtoType.get("a.*"));
    assertEquals(Arrays.asList("b.IncludedType"), new ArrayList<>(rules.unusedIncludes()));

    rules.includes(ProtoType.get("b.IncludedType"));
    assertTrue(rules.unusedIncludes().isEmpty());
  }

  @Test public void trackingUnusedExcludes() {
    EmittingRules rules = new EmittingRules.Builder()
        .exclude("a.*")
        .exclude("b.ExcludedType")
        .build();
    assertEquals(Arrays.asList("a.*", "b.ExcludedType"),
        new ArrayList<>(rules.unusedExcludes()));

    rules.includes(ProtoType.get("a.*"));
    assertEquals(Arrays.asList("b.ExcludedType"), new ArrayList<>(rules.unusedExcludes()));

    rules.includes(ProtoType.get("b.ExcludedType"));
    assertTrue(rules.unusedExcludes().isEmpty());
  }

  @Test public void trackingUnusedIncludesPrecedence() {
    EmittingRules rules = new EmittingRules.Builder()
        .include("a.*")
        .include("a.IncludedType")
        .build();
    rules.includes(ProtoType.get("a.IncludedType.NestedType"));
    assertEquals(Arrays.asList("a.IncludedType"), new ArrayList<>(rules.unusedIncludes()));
  }

  @Test public void trackingUnusedExcludesPrecedence() {
    EmittingRules rules = new EmittingRules.Builder()
        .exclude("a.*")
        .exclude("a.IncludedType")
        .build();
    rules.includes(ProtoType.get("a.IncludedType.NestedType"));
    assertEquals(Arrays.asList("a.IncludedType"), new ArrayList<>(rules.unusedExcludes()));
  }

  @Test public void crashForConflictingRules() {
    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> new EmittingRules.Builder()
            .include("a.*")
            .exclude("a.*")
            .build());
    assertEquals("same rule(s) defined in both includes and excludes: a.*", exception.getMessage());
  }
}
