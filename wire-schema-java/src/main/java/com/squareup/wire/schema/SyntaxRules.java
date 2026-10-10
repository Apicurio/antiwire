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

import com.squareup.wire.Syntax;
import com.squareup.wire.internal.Internal;
import java.util.List;

/** A set of rules which defines schema requirements for a specific {@link Syntax}. */
public interface SyntaxRules {
  void validateDefaultValue(boolean hasDefaultValue, ErrorCollector errors);

  void validateExtension(ProtoType protoType, ErrorCollector errors);

  void validateEnumConstants(List<EnumConstant> constants, ErrorCollector errors);

  void validateTypeReference(Type type, ErrorCollector errors);

  boolean isPackedByDefault(ProtoType type, Field.Label label);

  Field.EncodeMode getEncodeMode(ProtoType protoType, Field.Label label, boolean isPacked,
      boolean isOneOf);

  String jsonName(String name, String declaredJsonName);

  SyntaxRules PROTO_2_SYNTAX_RULES = new SyntaxRules() {
    @Override public void validateDefaultValue(boolean hasDefaultValue, ErrorCollector errors) {
    }

    @Override public void validateExtension(ProtoType protoType, ErrorCollector errors) {
    }

    @Override public void validateEnumConstants(List<EnumConstant> constants,
        ErrorCollector errors) {
    }

    @Override public void validateTypeReference(Type type, ErrorCollector errors) {
    }

    @Override public boolean isPackedByDefault(ProtoType type, Field.Label label) {
      return false;
    }

    @Override public Field.EncodeMode getEncodeMode(ProtoType protoType, Field.Label label,
        boolean isPacked, boolean isOneOf) {
      if (label == Field.Label.REPEATED) {
        return isPacked ? Field.EncodeMode.PACKED : Field.EncodeMode.REPEATED;
      }
      if (label == Field.Label.OPTIONAL) return Field.EncodeMode.NULL_IF_ABSENT;
      if (label == Field.Label.REQUIRED) return Field.EncodeMode.REQUIRED;
      // Field.Label.ONE_OF, or null.
      return protoType.isMap() ? Field.EncodeMode.MAP : Field.EncodeMode.NULL_IF_ABSENT;
    }

    @Override public String jsonName(String name, String declaredJsonName) {
      return declaredJsonName != null ? declaredJsonName : name;
    }
  };

  SyntaxRules PROTO_3_SYNTAX_RULES = new SyntaxRules() {
    @Override public void validateDefaultValue(boolean hasDefaultValue, ErrorCollector errors) {
      if (hasDefaultValue) {
        errors.add("user-defined default values are not permitted in proto3");
      }
    }

    @Override public void validateExtension(ProtoType protoType, ErrorCollector errors) {
      if (!Options.isGoogleProtobufOptionType(protoType)) {
        errors.add("extensions are not allowed in proto3");
      }
    }

    @Override public void validateEnumConstants(List<EnumConstant> constants,
        ErrorCollector errors) {
      if (constants.isEmpty() || constants.get(0).getTag() != 0) {
        errors.add("missing a zero value at the first element in proto3");
      }
    }

    @Override public void validateTypeReference(Type type, ErrorCollector errors) {
      if (type == null) return;
      if (!(type instanceof EnumType)) return;
      if (type.getSyntax() == Syntax.PROTO_3) return;

      errors.add("Proto2 enums cannot be referenced in a proto3 message");
    }

    @Override public boolean isPackedByDefault(ProtoType type, Field.Label label) {
      return label == Field.Label.REPEATED && ProtoType.NUMERIC_SCALAR_TYPES.contains(type);
    }

    @Override public Field.EncodeMode getEncodeMode(ProtoType protoType, Field.Label label,
        boolean isPacked, boolean isOneOf) {
      if (label == Field.Label.REPEATED) {
        return isPacked ? Field.EncodeMode.PACKED : Field.EncodeMode.REPEATED;
      }
      if (protoType.isMap()) return Field.EncodeMode.MAP;
      if (isOneOf) return Field.EncodeMode.NULL_IF_ABSENT;
      if (label == Field.Label.OPTIONAL) return Field.EncodeMode.NULL_IF_ABSENT;

      return Field.EncodeMode.OMIT_IDENTITY;
    }

    @Override public String jsonName(String name, String declaredJsonName) {
      return declaredJsonName != null ? declaredJsonName : Internal.camelCase(name, false);
    }
  };

  static SyntaxRules get(Syntax syntax) {
    if (syntax == Syntax.PROTO_3) return PROTO_3_SYNTAX_RULES;
    // PROTO_2, or null.
    return PROTO_2_SYNTAX_RULES;
  }

  /** Mirror of the Kotlin companion object: lets Java callers write {@code SyntaxRules.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }

    public SyntaxRules get(Syntax syntax) {
      return SyntaxRules.get(syntax);
    }
  }
}
