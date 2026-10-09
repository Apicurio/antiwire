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

import com.squareup.wire.Syntax;
import com.squareup.wire.schema.internal.SchemaUtil;
import com.squareup.wire.schema.internal.parser.EnumElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EnumType extends Type {
  static final ProtoMember ALLOW_ALIAS = ProtoMember.get(Options.ENUM_OPTIONS, "allow_alias");
  static final ProtoMember DEPRECATED = ProtoMember.get(Options.ENUM_OPTIONS, "deprecated");
  static final ProtoMember WIRE_ENUM_MODE =
      ProtoMember.get(Options.ENUM_OPTIONS, "wire.enum_mode");

  final ProtoType type;
  final Location location;
  final String documentation;
  final String name;
  final List<EnumConstant> constants;
  final List<Reserved> reserveds;
  final Options options;
  final Syntax syntax;

  private Object allowAlias;

  private Object deprecated;

  EnumType(ProtoType type, Location location, String documentation, String name,
      List<EnumConstant> constants, List<Reserved> reserveds, Options options, Syntax syntax) {
    this.type = type;
    this.location = location;
    this.documentation = documentation;
    this.name = name;
    this.constants = constants;
    this.reserveds = reserveds;
    this.options = options;
    this.syntax = syntax;
  }

  /**
   * Returns a copy of this enum type with the given property values, mirroring the Kotlin
   * data-class {@code copy}. Like upstream, the copy does not carry over the linked
   * {@code allowAlias} and {@code deprecated} option values.
   */
  public EnumType copy(ProtoType type, Location location, String documentation, String name,
      List<EnumConstant> constants, List<Reserved> reserveds, Options options, Syntax syntax) {
    return new EnumType(type, location, documentation, name, constants, reserveds, options,
        syntax);
  }

  @Override public ProtoType getType() {
    return type;
  }

  @Override public Location getLocation() {
    return location;
  }

  @Override public String getDocumentation() {
    return documentation;
  }

  @Override public String getName() {
    return name;
  }

  public List<EnumConstant> getConstants() {
    return constants;
  }

  public List<Reserved> reserveds() {
    return reserveds;
  }

  @Override public Options getOptions() {
    return options;
  }

  @Override public Syntax getSyntax() {
    return syntax;
  }

  @Override public List<Type> getNestedTypes() {
    return Collections.emptyList(); // Enums do not allow nested type declarations.
  }

  @Override public List<Extend> getNestedExtendList() {
    return Collections.emptyList(); // Enums do not allow nested type declarations.
  }

  public boolean allowAlias() {
    return "true".equals(allowAlias);
  }

  public boolean isDeprecated() {
    return "true".equals(deprecated);
  }

  public String getEnumMode() {
    Object mode = options.get(WIRE_ENUM_MODE);
    return mode != null ? mode.toString() : null;
  }

  /** Returns the constant named {@code name}, or null if this enum has no such constant. */
  public EnumConstant constant(String name) {
    for (EnumConstant constant : constants) {
      if (constant.getName().equals(name)) return constant;
    }
    return null;
  }

  /** Returns the constant tagged {@code tag}, or null if this enum has no such constant. */
  public EnumConstant constant(int tag) {
    for (EnumConstant constant : constants) {
      if (constant.getTag() == tag) return constant;
    }
    return null;
  }

  @Override void linkMembers(Linker linker) {
  }

  @Override void linkOptions(Linker linker, SyntaxRules syntaxRules, boolean validate) {
    Linker scoped = linker.withContext(this);
    options.link(scoped, location, validate);
    for (EnumConstant constant : constants) {
      constant.linkOptions(scoped, validate);
    }
    allowAlias = options.get(ALLOW_ALIAS);
    deprecated = options.get(DEPRECATED);
  }

  @Override void validate(Linker linker, SyntaxRules syntaxRules) {
    Linker scoped = linker.withContext(this);

    if (!"true".equals(allowAlias)) {
      validateTagUniqueness(scoped);
    }
    validateTagNameAmbiguity("true".equals(allowAlias), scoped);
    syntaxRules.validateEnumConstants(constants, scoped.getErrors());

    for (EnumConstant constant : constants) {
      for (Reserved reserved : reserveds) {
        if (reserved.matchesTag(constant.getTag())) {
          scoped.getErrors().at(constant)
              .add("tag " + constant.getTag() + " is reserved (" + reserved.getLocation() + ")");
        }
        if (reserved.matchesName(constant.getName())) {
          scoped.getErrors().at(constant)
              .add("name '" + constant.getName() + "' is reserved (" + reserved.getLocation() + ")");
        }
      }
    }
  }

  private void validateTagNameAmbiguity(boolean allowAlias, Linker linker) {
    Map<String, List<EnumConstant>> nameToConstants = new LinkedHashMap<>();
    for (EnumConstant constant : constants) {
      // Identifiers are ASCII-only ([a-zA-Z0-9_-]), so Locale.US lowercasing matches the
      // upstream per-char A-Z fold exactly.
      String key = SchemaUtil.toEnglishLowerCase(constant.getName());
      List<EnumConstant> list = nameToConstants.get(key);
      if (list == null) {
        list = new ArrayList<>();
        nameToConstants.put(key, list);
      }
      list.add(constant);
    }

    for (List<EnumConstant> ambiguous : nameToConstants.values()) {
      if (ambiguous.size() > 1) {
        if (allowAlias && countDistinctTags(ambiguous) == 1) continue;

        StringBuilder error = new StringBuilder();
        error.append("Ambiguous constant names (if you are using allow_alias, use the same value "
            + "for these constants):");
        for (EnumConstant constant : ambiguous) {
          error.append("\n  ").append(constant.getName()).append(":").append(constant.getTag())
              .append(" (").append(constant.getLocation()).append(")");
        }
        linker.getErrors().add(error.toString());
      }
    }
  }

  private int countDistinctTags(List<EnumConstant> constants) {
    List<Integer> tags = new ArrayList<>();
    for (EnumConstant constant : constants) {
      if (!tags.contains(constant.getTag())) tags.add(constant.getTag());
    }
    return tags.size();
  }

  private void validateTagUniqueness(Linker linker) {
    Map<Integer, List<EnumConstant>> tagToConstants = new LinkedHashMap<>();
    for (EnumConstant constant : constants) {
      List<EnumConstant> list = tagToConstants.get(constant.getTag());
      if (list == null) {
        list = new ArrayList<>();
        tagToConstants.put(constant.getTag(), list);
      }
      list.add(constant);
    }

    for (Map.Entry<Integer, List<EnumConstant>> entry : tagToConstants.entrySet()) {
      List<EnumConstant> sharing = entry.getValue();
      if (sharing.size() > 1) {
        StringBuilder error = new StringBuilder();
        error.append("multiple enum constants share tag ").append(entry.getKey()).append(":");
        for (int i = 0; i < sharing.size(); i++) {
          error.append("\n  ").append(i + 1).append(". ").append(sharing.get(i).getName())
              .append(" (").append(sharing.get(i).getLocation()).append(")");
        }
        linker.getErrors().add(error.toString());
      }
    }
  }

  @Override Type retainAll(Schema schema, MarkSet markSet) {
    // If this type is not retained, prune it.
    if (!markSet.contains(type)) return null;

    List<EnumConstant> retainedConstants = new ArrayList<>();
    for (EnumConstant constant : constants) {
      if (markSet.contains(ProtoMember.get(type, constant.getName()))) {
        retainedConstants.add(constant.retainAll(schema, markSet));
      }
    }

    EnumType result = new EnumType(type, location, documentation, name, retainedConstants,
        reserveds, options.retainAll(schema, markSet), syntax);
    result.allowAlias = allowAlias;
    result.deprecated = deprecated;
    return result;
  }

  @Override Type retainLinked(java.util.Set<ProtoType> linkedTypes,
      java.util.Set<Field> linkedFields) {
    if (!linkedTypes.contains(type)) {
      return null;
    }

    List<EnumConstant> retainedConstants = new ArrayList<>();
    for (EnumConstant constant : constants) {
      retainedConstants.add(constant.retainLinked());
    }

    return new EnumType(type, location, documentation, name, retainedConstants, reserveds,
        options.retainLinked(), syntax);
  }

  EnumElement toElement() {
    return new EnumElement(location, name, documentation, options.getElements(),
        EnumConstant.toElements(constants), Reserved.toElements(reserveds));
  }

  static EnumType fromElement(ProtoType protoType, EnumElement enumElement, Syntax syntax) {
    return new EnumType(protoType, enumElement.getLocation(), enumElement.getDocumentation(),
        enumElement.getName(), EnumConstant.fromElements(enumElement.getConstants()),
        Reserved.fromElements(enumElement.getReserveds()),
        new Options(Options.ENUM_OPTIONS, enumElement.getOptions()), syntax);
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof EnumType)) return false;
    EnumType that = (EnumType) other;
    return type.equals(that.type)
        && location.equals(that.location)
        && documentation.equals(that.documentation)
        && name.equals(that.name)
        && constants.equals(that.constants)
        && reserveds.equals(that.reserveds)
        && Objects.equals(options, that.options)
        && syntax == that.syntax;
  }

  @Override public int hashCode() {
    int result = type.hashCode();
    result = 31 * result + location.hashCode();
    result = 31 * result + documentation.hashCode();
    result = 31 * result + name.hashCode();
    result = 31 * result + constants.hashCode();
    result = 31 * result + reserveds.hashCode();
    result = 31 * result + Objects.hashCode(options);
    result = 31 * result + syntax.hashCode();
    return result;
  }

  @Override public String toString() {
    return "EnumType(type=" + type + ", location=" + location + ", documentation="
        + documentation + ", name=" + name + ", constants=" + constants + ", reserveds="
        + reserveds + ", options=" + options + ", syntax=" + syntax + ")";
  }
}
