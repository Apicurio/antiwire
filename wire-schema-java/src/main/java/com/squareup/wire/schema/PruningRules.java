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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A set of rules that describes which types and members to retain and which to remove.
 *
 * <p>Members may be pruned using either their identifier (package, type name, member name) or
 * their version (since and until options).
 *
 * <p>Despite the builder, instances of this class are not safe for concurrent use.
 *
 * <h2>Identifier Matching</h2>
 *
 * <p>If a member is a root in the set, its type is implicitly also considered a root. A type
 * that is a root without a specific member implicitly set all of that type's members as roots,
 * but not its nested types.
 *
 * <p>Identifiers in this set may be in the following forms:
 *
 * <ul>
 *   <li>Package names, followed by {@code .*}, like {@code squareup.protos.person.*}. This
 *       matches types and services defined in the package and its descendant packages.
 *   <li>Fully qualified type and service names, like
 *       {@code squareup.protos.person.Person}.
 *   <li>Fully qualified member names, which are type names followed by a {@code #}, followed
 *       by a member name, like {@code squareup.protos.person.Person#address}. Members may be
 *       fields, enum constants or RPCs.
 * </ul>
 *
 * <p>An identifier set populated with {@code Movie} and {@code Actor#name} contains all members
 * of {@code Movie} (such as {@code Movie#name} and {@code Movie#release_date}). It contains the
 * type {@code Actor} and one member {@code Actor#name}, but not {@code Actor#birth_date} or
 * {@code Actor#oscar_count}.
 *
 * <p>This set has <em>root identifiers</em> and <em>prune identifiers</em>, with the most
 * precise identifier taking precedence over the other ones. For instance, if there is one root
 * identifier {@code a.Movie} along a pruning identifier {@code a.*}, the type {@code a.Movie}
 * is considered a root.
 *
 * <p>If the roots set is empty, that implies that all elements are considered roots. Use this
 * to prune unwanted types and members without also marking everything else as roots.
 *
 * <h2>Version Matching</h2>
 *
 * <p>Members may be declared with {@code wire.since} and {@code wire.until} options. For
 * example, these options declare a field {@code age} that was replaced with {@code birth_date}
 * in version "5.0":
 *
 * <pre>{@code
 *   optional int32 age = 3 [(wire.until) = "5.0"];
 *   optional Date birth_date = 4 [(wire.since) = "5.0"];
 * }</pre>
 *
 * <p>Client code should typically target a single version. In this example, versions &lt;=
 * "4.0" will have the {@code age} field only and versions &gt;= "5.0" will have the
 * {@code birth_date} field only. One can target a single version using {@link Builder#only}.
 *
 * <p>Service code that supports many clients should support the union of versions of all
 * supported clients. Such code will have both the {@code age} and {@code birth_date} fields.
 */
public final class PruningRules {
  static final ProtoMember FIELD_SINCE = ProtoMember.get(Options.FIELD_OPTIONS, "wire.since");
  static final ProtoMember FIELD_UNTIL = ProtoMember.get(Options.FIELD_OPTIONS, "wire.until");
  static final ProtoMember ENUM_CONSTANT_SINCE =
      ProtoMember.get(Options.ENUM_VALUE_OPTIONS, "wire.constant_since");
  static final ProtoMember ENUM_CONSTANT_UNTIL =
      ProtoMember.get(Options.ENUM_VALUE_OPTIONS, "wire.constant_until");

  private final Set<String> roots;
  private final Set<String> prunes;

  private final SemVer since;
  private final SemVer until;
  private final SemVer only;

  private final Set<String> usedRoots = new LinkedHashSet<>();
  private final Set<String> usedPrunes = new LinkedHashSet<>();

  private PruningRules(Builder builder) {
    this.roots = new LinkedHashSet<>(builder.roots);
    this.prunes = new LinkedHashSet<>(builder.prunes);
    this.since = builder.since;
    this.until = builder.until;
    this.only = builder.only;
  }

  public Set<String> getRoots() {
    return roots;
  }

  public Set<String> getPrunes() {
    return prunes;
  }

  public String getSince() {
    return since != null ? since.version : null;
  }

  public String getUntil() {
    return until != null ? until.version : null;
  }

  public String getOnly() {
    return only != null ? only.version : null;
  }

  public boolean isEmpty() {
    return roots.isEmpty() && prunes.isEmpty() && since == null && until == null && only == null;
  }

  /** Returns true unless {@code options} specifies a version that is outside the range. */
  public boolean isFieldRetainedVersion(Options options) {
    return isRetainedVersion(options, FIELD_SINCE, FIELD_UNTIL);
  }

  /** Returns true unless {@code options} specifies a version that is outside the range. */
  public boolean isEnumConstantRetainedVersion(Options options) {
    return isRetainedVersion(options, ENUM_CONSTANT_SINCE, ENUM_CONSTANT_UNTIL);
  }

  private boolean isRetainedVersion(Options options, ProtoMember sinceMember,
      ProtoMember untilMember) {
    if (until != null || only != null) {
      Object sinceOption = options.get(sinceMember);
      SemVer since = sinceOption instanceof String
          ? SemVer.toLowerCaseSemVer((String) sinceOption)
          : null;
      if (until != null && since != null && since.compareTo(until) >= 0
          || only != null && since != null && since.compareTo(only) > 0) {
        return false;
      }
    }

    if (this.since != null || only != null) {
      SemVer lowerBound = this.since != null ? this.since : only;
      Object untilOption = options.get(untilMember);
      SemVer until = untilOption instanceof String
          ? SemVer.toLowerCaseSemVer((String) untilOption)
          : null;
      if (until != null && until.compareTo(lowerBound) <= 0) return false;
    }

    return true;
  }

  /** Returns true if {@code type} is a root. */
  public boolean isRoot(ProtoType type) {
    return isRoot(type.toString());
  }

  /** Returns true if {@code protoMember} is a root. */
  public boolean isRoot(ProtoMember protoMember) {
    return isRoot(protoMember.toString());
  }

  /**
   * Returns true if {@code identifier} or any of its enclosing identifiers is a root. If any
   * enclosing identifier is pruned, that takes precedence and this returns false unless the root
   * identifier is more precise.
   */
  private boolean isRoot(String identifier) {
    if (roots.isEmpty()) return !prunes(identifier);

    String rootMatch = null;
    String pruneMatch = null;
    String rule = identifier;
    while (rule != null) {
      if (pruneMatch == null && prunes.contains(rule)) {
        pruneMatch = rule;
      }
      if (rootMatch == null && roots.contains(rule)) {
        rootMatch = rule;
      }
      rule = enclosing(rule);
    }

    boolean isRoot;
    if (pruneMatch != null && rootMatch != null) {
      isRoot = pruneMatch.length() < rootMatch.length();
    } else if (pruneMatch != null) {
      isRoot = false;
    } else {
      isRoot = rootMatch != null;
    }
    if (isRoot) {
      usedRoots.add(rootMatch);
    } else if (pruneMatch != null) {
      usedPrunes.add(pruneMatch);
    }
    return isRoot;
  }

  /**
   * Returns true if {@code type} should be pruned, even if it is a transitive dependency of a
   * root. In that case, the referring member is also pruned.
   */
  public boolean prunes(ProtoType type) {
    return prunes(type.toString());
  }

  /** Returns true if {@code protoMember} should be pruned. */
  public boolean prunes(ProtoMember protoMember) {
    return prunes(protoMember.toString());
  }

  /** Returns true if {@code identifier} or any of its enclosing identifiers is pruned. */
  private boolean prunes(String identifier) {
    String rootMatch = null;
    String pruneMatch = null;
    String rule = identifier;
    while (rule != null) {
      if (pruneMatch == null && prunes.contains(rule)) {
        pruneMatch = rule;
      }
      if (rootMatch == null && roots.contains(rule)) {
        rootMatch = rule;
      }
      rule = enclosing(rule);
    }

    boolean pruned;
    if (pruneMatch != null && rootMatch != null) {
      pruned = pruneMatch.length() >= rootMatch.length();
    } else {
      pruned = pruneMatch != null;
    }
    if (pruned) {
      usedPrunes.add(pruneMatch);
    }
    return pruned;
  }

  public Set<String> unusedRoots() {
    Set<String> result = new LinkedHashSet<>(roots);
    result.removeAll(usedRoots);
    return result;
  }

  public Set<String> unusedPrunes() {
    Set<String> result = new LinkedHashSet<>(prunes);
    result.removeAll(usedPrunes);
    return result;
  }

  public static class Builder {
    final Set<String> roots = new LinkedHashSet<>();
    final Set<String> prunes = new LinkedHashSet<>();
    SemVer since;
    SemVer until;
    SemVer only;

    public Builder addRoot(String identifier) {
      roots.add(identifier);
      return this;
    }

    public Builder addRoot(Iterable<String> identifiers) {
      for (String identifier : identifiers) {
        roots.add(identifier);
      }
      return this;
    }

    public Builder prune(String identifier) {
      prunes.add(identifier);
      return this;
    }

    public Builder prune(Iterable<String> identifiers) {
      for (String identifier : identifiers) {
        prunes.add(identifier);
      }
      return this;
    }

    /**
     * The exclusive lower bound of the version range. Fields with {@code until} values greater
     * than this are retained.
     */
    public Builder since(String since) {
      this.since = since != null ? SemVer.toLowerCaseSemVer(since) : null;
      return this;
    }

    /**
     * The inclusive upper bound of the version range. Fields with {@code since} values less
     * than or equal to this are retained.
     */
    public Builder until(String until) {
      this.until = until != null ? SemVer.toLowerCaseSemVer(until) : null;
      return this;
    }

    /**
     * The only version of the version range. Fields with {@code until} values greater than
     * this, as well as fields with {@code since} values less than or equal to this, are
     * retained.
     */
    public Builder only(String only) {
      this.only = only != null ? SemVer.toLowerCaseSemVer(only) : null;
      return this;
    }

    public PruningRules build() {
      if (only != null && (since != null || until != null)) {
        throw new IllegalStateException("only cannot be set along side since and until");
      }
      if (since != null && until != null && since.compareTo(until) >= 0) {
        throw new IllegalStateException(
            "expected since " + since + " < until " + until);
      }
      List<String> conflictingRules = new ArrayList<>();
      for (String rule : roots) {
        if (prunes.contains(rule)) conflictingRules.add(rule);
      }
      if (!conflictingRules.isEmpty()) {
        throw new IllegalStateException(
            "same rule(s) defined in both roots and prunes: " + String.join(", ",
                conflictingRules));
      }
      return new PruningRules(this);
    }
  }

  /**
   * Returns the identifier or wildcard that encloses {@code identifier}, or null if it is not
   * enclosed.
   *
   * <ul>
   *   <li>If {@code identifier} is a member this returns the enclosing type. If the member is a
   *       qualified name, this returns instead the enclosing package of this member, like
   *       {@code squareup.dinosaurs.Alligator#myextension.*}.
   *   <li>If it is a type it returns the enclosing package with a wildcard, like
   *       {@code squareup.dinosaurs.*}.
   *   <li>If it is a package with a wildcard, it returns the parent package with a wildcard,
   *       like {@code squareup.*}. The root wildcard is a lone asterisk, {@code *}.
   * </ul>
   */
  static String enclosing(String identifier) {
    int hash = identifier.lastIndexOf('#');
    if (hash != -1) {
      String beforeHash = identifier.substring(0, hash);
      String afterHash = enclosing(identifier.substring(hash + 1));

      return afterHash != null ? beforeHash + "#" + afterHash : beforeHash;
    }

    int from = identifier.endsWith(".*") ? identifier.length() - 3 : identifier.length() - 1;
    int dot = identifier.lastIndexOf('.', from);

    if (hash != -1 && hash > dot) return identifier.substring(0, hash);
    if (dot != -1) return identifier.substring(0, dot) + ".*";

    if (!identifier.equals("*")) return "*";
    return null;
  }
}
