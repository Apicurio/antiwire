/*
 * Copyright (C) 2015 Square, Inc.
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

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A mark set is used in three phases:
 *
 * <ol>
 *   <li>Marking root types and root members. These are the identifiers specifically identified
 *       by the user in the includes set. In this phase it is an error to mark a type that is
 *       excluded, or to mark both a type and one of its members.
 *   <li>Marking members transitively reachable by those roots. In this phase if a member is
 *       visited, the member's enclosing type is marked instead, unless it is of a type that has
 *       a specific member already marked.
 *   <li>Retaining which members and types have been marked.
 * </ol>
 */
public final class MarkSet {
  private static final ProtoType UNKNOWN_TYPE = ProtoType.get("(unknown type)");

  final PruningRules pruningRules;

  /** The types to retain. We may retain a type but not all of its members. */
  final Set<ProtoType> types = new LinkedHashSet<>();

  /** The members to retain. Any member not in here should be pruned! */
  final Map<ProtoType, Set<ProtoMember>> members = new HashMap<>();

  /** The root members which are never to be pruned, including their referenced type. */
  private final Map<ProtoMember, ProtoType> rootMemberTypes = new HashMap<>();

  /** The members this MarkSet have seen. The values should be non-null once the marking is done. */
  private final Map<ProtoMember, ProtoType> memberTypes = new HashMap<>();

  public MarkSet(PruningRules pruningRules) {
    this.pruningRules = pruningRules;
  }

  /**
   * Marks {@code protoMember}, throwing if it is explicitly excluded. This implicitly excludes
   * other members of the same type.
   */
  void root(ProtoMember protoMember) {
    if (pruningRules.prunes(protoMember)) {
      throw new IllegalStateException("Check failed.");
    }
    types.add(protoMember.type);
    rootMemberTypes.put(protoMember, UNKNOWN_TYPE);
    Set<ProtoMember> memberSet = members.computeIfAbsent(protoMember.type, k -> new LinkedHashSet<>());
    memberSet.add(protoMember);
  }

  /** Marks {@code type}, throwing if it is explicitly excluded. */
  void root(ProtoType type) {
    if (pruningRules.prunes(type)) {
      throw new IllegalStateException("Check failed.");
    }
    types.add(type);
  }

  /**
   * Marks a type as transitively reachable by the includes set. Returns true if the mark is new,
   * the type will be retained, and reachable objects should be traversed.
   *
   * <p>If there is an exclude for {@code type}, non-root members referencing it will be pruned.
   * The type itself will also be pruned unless it is referenced by a root member.
   */
  boolean mark(ProtoType type, ProtoMember reference) {
    memberTypes.put(reference, type);

    if (rootMemberTypes.containsKey(reference)) {
      rootMemberTypes.put(reference, type);
      // We keep.
      return types.add(type);
    }

    return mark(type);
  }

  /**
   * Marks a type as transitively reachable by the includes set. Returns true if the mark is new,
   * the type will be retained, and reachable objects should be traversed.
   */
  boolean mark(ProtoType type) {
    if (pruningRules.prunes(type)) return false;
    return types.add(type);
  }

  /**
   * Marks a member as transitively reachable by the includes set. Returns true if the mark is
   * new, the member will be retained, and reachable objects should be traversed.
   */
  boolean mark(ProtoMember protoMember) {
    if (pruningRules.prunes(protoMember)) return false;
    types.add(protoMember.type);
    Set<ProtoMember> memberSet = members.computeIfAbsent(protoMember.type, k -> new LinkedHashSet<>());
    return memberSet.add(protoMember);
  }

  /** Returns true if {@code type} is marked and should be retained. */
  boolean contains(ProtoType type) {
    return types.contains(type);
  }

  /** Returns true if {@code member} is marked and should be retained. */
  boolean contains(ProtoMember protoMember) {
    ProtoType memberType = memberTypes.get(protoMember);

    // We do not contain non-root members whose referenced type is excluded.
    if (!rootMemberTypes.containsKey(protoMember)
        && memberType != null
        && pruningRules.prunes(memberType)) {
      return false;
    }

    // A member cannot be included if its referencing type is excluded unless a root member of
    // this referenced type exists.
    if (pruningRules.prunes(protoMember.type)
        && rootMemberTypes.containsValue(protoMember.type)) {
      return true;
    }

    if (pruningRules.prunes(protoMember)) return false;
    Set<ProtoMember> memberSet = members.get(protoMember.type);
    return memberSet != null && memberSet.contains(protoMember);
  }
}
