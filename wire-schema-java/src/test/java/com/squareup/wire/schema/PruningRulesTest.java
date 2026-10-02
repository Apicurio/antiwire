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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Upstream PruningRulesTest translated (assertk to JUnit 5). */
public class PruningRulesTest {
  @Test public void enclosing() {
    assertEquals("a.b.Outer#c.d.*", PruningRules.enclosing("a.b.Outer#c.d.member"));
    assertEquals("a.b.Outer#c.*", PruningRules.enclosing("a.b.Outer#c.d.*"));
    assertEquals("a.b.Outer#*", PruningRules.enclosing("a.b.Outer#c.*"));
    assertEquals("a.b.Outer", PruningRules.enclosing("a.b.Outer#*"));
    assertEquals("a.b.*", PruningRules.enclosing("a.b.Outer"));
    assertEquals("a.*", PruningRules.enclosing("a.b.*"));
    assertEquals("*", PruningRules.enclosing("a.*"));
    assertNull(PruningRules.enclosing("*"));
  }

  @Test public void enclosingOnNestedClass() {
    assertEquals("a.b.Outer.Inner#c.*", PruningRules.enclosing("a.b.Outer.Inner#c.member"));
    assertEquals("a.b.Outer.Inner#*", PruningRules.enclosing("a.b.Outer.Inner#c.*"));
    assertEquals("a.b.Outer.Inner", PruningRules.enclosing("a.b.Outer.Inner#*"));
    assertEquals("a.b.Outer.*", PruningRules.enclosing("a.b.Outer.Inner"));
    assertEquals("a.b.*", PruningRules.enclosing("a.b.Outer.*"));
  }

  @Test public void empty() {
    PruningRules set = new PruningRules.Builder().build();
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#member"));
  }

  /** Note that including a type includes nested members, but not nested types.  */
  @Test public void includeType() {
    PruningRules set = new PruningRules.Builder()
        .addRoot("a.b.Message")
        .build();
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message.Nested"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#member"));
  }

  @Test public void includeMember() {
    PruningRules set = new PruningRules.Builder()
        .addRoot("a.b.Message#member")
        .build();
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message.Nested"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message#other"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#member"));
  }

  @Test public void includeMemberWithQualifiedName() {
    PruningRules set = new PruningRules.Builder()
        .addRoot("a.b.Message#c.d.member")
        .build();
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message.Nested"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#c.d.member"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message#c.d.other"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message#c.e.member"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message#other"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#member"));
  }

  @Test public void includeMemberWithQualifiedNameAndWildstar() {
    PruningRules set = new PruningRules.Builder()
        .addRoot("a.b.Message#c.d.*")
        .build();
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message.Nested"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#c.d.member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#c.d.other"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message#c.e.member"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message#other"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#member"));
  }

  @Test public void includeMemberWithWildstar() {
    PruningRules set = new PruningRules.Builder()
        .addRoot("a.b.Message#*")
        .build();
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Message.Nested"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#c.d.member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#c.d.other"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#c.e.member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#other"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#member"));
  }

  @Test public void excludeMemberWithQualifiedName() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.b.Message#c.d.member")
        .build();
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message.Nested"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#c.d.member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#c.d.other"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#c.e.member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#other"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Another"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Another#member"));
  }

  @Test public void excludeMemberWithQualifiedNameAndWildstar() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.b.Message#c.d.*")
        .build();
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message.Nested"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#c.d.member"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#c.d.other"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#c.e.member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#other"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Another"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Another#member"));
  }

  @Test public void excludeMemberWithWildstar() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.b.Message#*")
        .build();
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message.Nested"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#c.d.member"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#c.d.other"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#c.e.member"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#other"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Another"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Another#member"));
  }

  @Test public void includePackage() {
    PruningRules set = new PruningRules.Builder()
        .addRoot("a.b.*")
        .build();
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.c.Message"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.c.Message#member"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.c.Another"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.c.Another#member"));
  }

  @Test public void includeAll() {
    PruningRules set = new PruningRules.Builder()
        .addRoot("*")
        .build();
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#member"));
  }

  @Test public void excludeType() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.b.Message")
        .build();
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Another"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Another#member"));
  }

  @Test public void excludeMember() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.b.Message#member")
        .build();
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#other"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Another"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Another#member"));
  }

  @Test public void excludePackage() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.b.*")
        .build();
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.c.Message"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.c.Message#member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.c.Another"));
    assertEquals(Policy.INCLUDED, policy(set, "a.c.Another#member"));
  }

  @Test public void excludePackageDoesNotTakePrecedenceOverIncludeType() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.b.*")
        .addRoot("a.b.Message")
        .build();
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Another"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Another#member"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.c.YetAnother"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.c.YetAnother#member"));
  }

  @Test public void excludeTypeDoesNotTakePrecedenceOverIncludeMember() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.b.Message")
        .addRoot("a.b.Message#member")
        .build();
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#other"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#member"));
  }

  @Test public void excludeTypeWithQualifiedNameDoesNotTakePrecedenceOverIncludeMember() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.b.Message")
        .addRoot("a.b.Message#c.member")
        .build();
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#c.member"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#c.other"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#d.other"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#c.member"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#member"));
  }

  @Test public void excludeTypeWithQualifiedNameWildcardDoesNotTakePrecedenceOverIncludeMember() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.b.Message")
        .addRoot("a.b.Message#c.*")
        .build();
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#c.member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#c.other"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#d.other"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#c.member"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#member"));
  }

  @Test public void excludeMemberTakesPrecedenceOverIncludeType() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.b.Message#member")
        .addRoot("a.b.Message")
        .build();
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#other"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#member"));
  }

  @Test public void excludeMemberTakesPrecedenceOverIncludeTypeWithQualifiedName() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.b.Message#c.member")
        .addRoot("a.b.Message")
        .build();
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#c.member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#c.other"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#d.other"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#c.member"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#member"));
  }

  @Test public void excludeMemberTakesPrecedenceOverIncludeTypeWithQualifiedNameWildcard() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.b.Message#c.*")
        .addRoot("a.b.Message")
        .build();
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#c.member"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#c.other"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#d.other"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#c.member"));
    assertEquals(Policy.UNSPECIFIED, policy(set, "a.b.Another#member"));
  }

  @Test public void mostPreciseRuleTakesPrecedence() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.b.Message#member")
        .addRoot("a.b.Message")
        .prune("a.b.*")
        .build();
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Message#member"));
    assertEquals(Policy.INCLUDED, policy(set, "a.b.Message#other"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Another"));
    assertEquals(Policy.EXCLUDED, policy(set, "a.b.Another#member"));
  }

  @Test public void trackingUnusedIncludes() {
    PruningRules set = new PruningRules.Builder()
        .addRoot("a.*")
        .addRoot("b.IncludedType")
        .addRoot("c.IncludedMember#member")
        .addRoot("d.IncludedMember#e.member")
        .addRoot("f.IncludedMember#g.*")
        .build();
    assertEquals(Arrays.asList(
        "a.*",
        "b.IncludedType",
        "c.IncludedMember#member",
        "d.IncludedMember#e.member",
        "f.IncludedMember#g.*"), new ArrayList<>(set.unusedRoots()));

    set.isRoot(ProtoType.get("a.*"));
    assertEquals(Arrays.asList(
        "b.IncludedType",
        "c.IncludedMember#member",
        "d.IncludedMember#e.member",
        "f.IncludedMember#g.*"), new ArrayList<>(set.unusedRoots()));

    set.isRoot(ProtoType.get("b.IncludedType"));
    assertEquals(Arrays.asList(
        "c.IncludedMember#member",
        "d.IncludedMember#e.member",
        "f.IncludedMember#g.*"), new ArrayList<>(set.unusedRoots()));

    set.isRoot(ProtoMember.get("c.IncludedMember#member"));
    assertEquals(Arrays.asList(
        "d.IncludedMember#e.member",
        "f.IncludedMember#g.*"), new ArrayList<>(set.unusedRoots()));

    set.isRoot(ProtoMember.get("d.IncludedMember#e.member"));
    assertEquals(Arrays.asList("f.IncludedMember#g.*"), new ArrayList<>(set.unusedRoots()));

    set.isRoot(ProtoMember.get("f.IncludedMember#g.*"));
    assertTrue(set.unusedRoots().isEmpty());
  }

  @Test public void trackingUnusedExcludes() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.*")
        .prune("b.ExcludedType")
        .prune("c.ExcludedMember#member")
        .prune("d.ExcludedMember#e.member")
        .prune("f.ExcludedMember#g.*")
        .build();
    assertEquals(Arrays.asList(
        "a.*",
        "b.ExcludedType",
        "c.ExcludedMember#member",
        "d.ExcludedMember#e.member",
        "f.ExcludedMember#g.*"), new ArrayList<>(set.unusedPrunes()));

    set.isRoot(ProtoType.get("a.*"));
    assertEquals(Arrays.asList(
        "b.ExcludedType",
        "c.ExcludedMember#member",
        "d.ExcludedMember#e.member",
        "f.ExcludedMember#g.*"), new ArrayList<>(set.unusedPrunes()));

    set.isRoot(ProtoType.get("b.ExcludedType"));
    assertEquals(Arrays.asList(
        "c.ExcludedMember#member",
        "d.ExcludedMember#e.member",
        "f.ExcludedMember#g.*"), new ArrayList<>(set.unusedPrunes()));

    set.isRoot(ProtoMember.get("c.ExcludedMember#member"));
    assertEquals(Arrays.asList(
        "d.ExcludedMember#e.member",
        "f.ExcludedMember#g.*"), new ArrayList<>(set.unusedPrunes()));

    set.isRoot(ProtoMember.get("d.ExcludedMember#e.member"));
    assertEquals(Arrays.asList("f.ExcludedMember#g.*"), new ArrayList<>(set.unusedPrunes()));

    set.isRoot(ProtoMember.get("f.ExcludedMember#g.*"));
    assertTrue(set.unusedPrunes().isEmpty());
  }

  @Test public void trackingUnusedIncludesPrecedence() {
    PruningRules set = new PruningRules.Builder()
        .addRoot("a.*")
        .addRoot("a.IncludedType")
        .build();
    set.isRoot(ProtoMember.get("a.IncludedType#member"));
    assertEquals(Arrays.asList("a.*"), new ArrayList<>(set.unusedRoots()));
  }

  @Test public void trackingUnusedExcludesPrecedence() {
    PruningRules set = new PruningRules.Builder()
        .prune("a.*")
        .prune("a.IncludedType")
        .build();
    set.isRoot(ProtoMember.get("a.IncludedType#member"));
    assertEquals(Arrays.asList("a.*"), new ArrayList<>(set.unusedPrunes()));
  }

  @Test public void crashForConflictingRules() {
    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> new PruningRules.Builder()
            .addRoot("a.*")
            .prune("a.*")
            .build());
    assertEquals("same rule(s) defined in both roots and prunes: a.*", exception.getMessage());
  }

  @Test public void onlyCannotBeSetAlongSideSince() {
    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> new PruningRules.Builder()
            .only("3")
            .since("3")
            .build());
    assertEquals("only cannot be set along side since and until", exception.getMessage());
  }

  @Test public void onlyCannotBeSetAlongSideUntil() {
    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> new PruningRules.Builder()
            .only("3")
            .until("3.1")
            .build());
    assertEquals("only cannot be set along side since and until", exception.getMessage());
  }

  private Policy policy(PruningRules set, String identifier) {
    if (identifier.contains("#")) {
      ProtoMember protoMember = ProtoMember.get(identifier);
      if (set.isRoot(protoMember)) return Policy.INCLUDED;
      return set.prunes(protoMember) ? Policy.EXCLUDED : Policy.UNSPECIFIED;
    } else {
      ProtoType protoType = ProtoType.get(identifier);
      if (set.isRoot(protoType)) return Policy.INCLUDED;
      return set.prunes(protoType) ? Policy.EXCLUDED : Policy.UNSPECIFIED;
    }
  }

  private enum Policy {
    INCLUDED,
    UNSPECIFIED,
    EXCLUDED,
  }
}
