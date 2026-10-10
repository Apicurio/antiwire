/*
 * Copyright (C) 2020 Square, Inc.
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
 * A set of rules that describes which types to generate.
 *
 * <p>Despite the builder, instances of this class are not safe for concurrent use.
 *
 * <h2>Identifier Matching</h2>
 *
 * <p>Identifiers in this set may be in the following forms:
 *
 * <ul>
 *   <li>Package names, followed by {@code .*}, like {@code squareup.protos.person.*}. This
 *       matches types and services defined in the package and its descendant packages.
 *   <li>Fully qualified type and service names, like
 *       {@code squareup.protos.person.Person}.
 * </ul>
 *
 * <p>Identifiers should not contain member names.
 *
 * <p>This set has <em>included identifiers</em> and <em>excluded identifiers</em>, with the most
 * precise identifier taking precedence over the other ones. For instance, if there is one
 * included identifier {@code a.Movie} along an excluded identifier {@code a.*}, the type
 * {@code a.Movie} is considered included in the set.
 *
 * <p>If the includes set is empty, that implies that all elements should be included. Use this
 * to exclude unwanted types and members without also including everything else.
 */
public final class EmittingRules {
  private final Set<String> includes;
  private final Set<String> excludes;
  private final Set<String> usedIncludes = new LinkedHashSet<>();
  private final Set<String> usedExcludes = new LinkedHashSet<>();

  public EmittingRules() {
    this(new Builder());
  }

  private EmittingRules(Builder builder) {
    this.includes = new LinkedHashSet<>(builder.includes);
    this.excludes = new LinkedHashSet<>(builder.excludes);
  }

  public boolean isEmpty() {
    return includes.isEmpty() && excludes.isEmpty();
  }

  /** Returns true if {@code type} should be generated. */
  public boolean includes(ProtoType type) {
    return includes(type.toString());
  }

  private boolean includes(String identifier) {
    if (includes.isEmpty()) return !exclude(identifier);

    String includeMatch = null;
    String excludeMatch = null;
    String rule = identifier;
    while (rule != null) {
      if (excludeMatch == null && excludes.contains(rule)) {
        excludeMatch = rule;
      }
      if (includeMatch == null && includes.contains(rule)) {
        includeMatch = rule;
      }
      rule = enclosing(rule);
    }

    boolean isIncluded;
    if (excludeMatch != null && includeMatch != null) {
      isIncluded = excludeMatch.length() < includeMatch.length();
    } else if (excludeMatch != null) {
      isIncluded = false;
    } else {
      isIncluded = includeMatch != null;
    }
    if (isIncluded) {
      usedIncludes.add(includeMatch);
    } else if (excludeMatch != null) {
      usedExcludes.add(excludeMatch);
    }
    return isIncluded;
  }

  private boolean exclude(String identifier) {
    String includeMatch = null;
    String excludeMatch = null;
    String rule = identifier;
    while (rule != null) {
      if (excludeMatch == null && excludes.contains(rule)) {
        excludeMatch = rule;
      }
      if (includeMatch == null && includes.contains(rule)) {
        includeMatch = rule;
      }
      rule = enclosing(rule);
    }

    boolean excluded;
    if (excludeMatch != null && includeMatch != null) {
      excluded = excludeMatch.length() >= includeMatch.length();
    } else {
      excluded = excludeMatch != null;
    }
    if (excluded) {
      usedExcludes.add(excludeMatch);
    }
    return excluded;
  }

  public Set<String> unusedIncludes() {
    Set<String> result = new LinkedHashSet<>(includes);
    result.removeAll(usedIncludes);
    return result;
  }

  public Set<String> unusedExcludes() {
    Set<String> result = new LinkedHashSet<>(excludes);
    result.removeAll(usedExcludes);
    return result;
  }

  public static class Builder {
    final Set<String> includes = new LinkedHashSet<>();
    final Set<String> excludes = new LinkedHashSet<>();

    public Builder include(String identifier) {
      includes.add(identifier);
      return this;
    }

    public Builder include(Iterable<String> identifiers) {
      for (String identifier : identifiers) {
        includes.add(identifier);
      }
      return this;
    }

    public Builder exclude(Iterable<String> identifiers) {
      for (String identifier : identifiers) {
        excludes.add(identifier);
      }
      return this;
    }

    public Builder exclude(String identifier) {
      excludes.add(identifier);
      return this;
    }

    public EmittingRules build() {
      List<String> conflictingRules = new ArrayList<>();
      for (String rule : includes) {
        if (excludes.contains(rule)) conflictingRules.add(rule);
      }
      if (!conflictingRules.isEmpty()) {
        throw new IllegalStateException("same rule(s) defined in both includes and excludes: "
            + String.join(", ", conflictingRules));
      }
      return new EmittingRules(this);
    }
  }

  /**
   * Returns the identifier or wildcard that encloses {@code identifier}, or null if it is not
   * enclosed.
   *
   * <ul>
   *   <li>If it is a type it returns the enclosing package with a wildcard, like
   *       {@code squareup.dinosaurs.*}.
   *   <li>If it is a package with a wildcard, it returns the parent package with a wildcard,
   *       like {@code squareup.*}. The root wildcard is a lone asterisk, {@code *}.
   * </ul>
   */
  static String enclosing(String identifier) {
    int from = identifier.endsWith(".*") ? identifier.length() - 3 : identifier.length() - 1;
    int dot = identifier.lastIndexOf('.', from);
    if (dot != -1) return identifier.substring(0, dot) + ".*";

    if (!identifier.equals("*")) return "*";
    return null;
  }

  /** Kotlin companion mirror: Java may write {@code EmittingRules.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }
  }
}
