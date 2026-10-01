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
package com.squareup.wire.schema.internal.parser;

import com.squareup.wire.schema.internal.SchemaUtil;
import java.util.List;
import java.util.Map;

public final class OptionElement {
  public enum Kind {
    STRING,
    BOOLEAN,
    NUMBER,
    ENUM,
    MAP,
    LIST,
    OPTION
  }

  /** An internal representation of the Option primitive types. */
  public static final class OptionPrimitive {
    public final Kind kind;
    public final Object value;

    public OptionPrimitive(Kind kind, Object value) {
      this.kind = kind;
      this.value = value;
    }

    public OptionPrimitive copy(Kind kind, Object value) {
      return new OptionPrimitive(kind, value);
    }

    @Override public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof OptionPrimitive)) return false;
      OptionPrimitive that = (OptionPrimitive) other;
      return kind == that.kind && java.util.Objects.equals(value, that.value);
    }

    @Override public int hashCode() {
      return 31 * kind.hashCode() + java.util.Objects.hashCode(value);
    }
  }

  public static final OptionElement PACKED_OPTION_ELEMENT =
      new OptionElement("packed", Kind.BOOLEAN, "true", false);

  public final String name;
  public final Kind kind;
  public final Object value;
  /** If true, this OptionElement is a custom option. */
  public final boolean isParenthesized;

  private final String formattedName;

  public OptionElement(String name, Kind kind, Object value, boolean isParenthesized) {
    this.name = name;
    this.kind = kind;
    this.value = value;
    this.isParenthesized = isParenthesized;
    this.formattedName = isParenthesized ? "(" + name + ")" : name;
  }

  public static OptionElement create(String name, Kind kind, Object value) {
    return new OptionElement(name, kind, value, false);
  }

  public static OptionElement create(String name, Kind kind, Object value,
      boolean isParenthesized) {
    return new OptionElement(name, kind, value, isParenthesized);
  }

  private static String escapeQuotedString(String value) {
    StringBuilder result = new StringBuilder();
    for (char ch : value.toCharArray()) {
      switch (ch) {
        case '"': result.append("\\\""); break;
        case '\'': result.append("\\'"); break;
        case '\\': result.append("\\\\"); break;
        case '\b': result.append("\\b"); break;
        case '\n': result.append("\\n"); break;
        case '\r': result.append("\\r"); break;
        case '\t': result.append("\\t"); break;
        case '\u0007': result.append("\\a"); break;
        case '\f': result.append("\\f"); break;
        case '\u000b': result.append("\\v"); break;
        default: result.append(ch); break;
      }
    }
    return result.toString();
  }

  public String toSchema() {
    StringBuilder builder = new StringBuilder();
    switch (kind) {
      case STRING:
        builder.append(formattedName).append(" = \"").append(escapeQuotedString(value.toString()))
            .append('"');
        break;
      case BOOLEAN:
      case NUMBER:
      case ENUM:
        builder.append(formattedName).append(" = ").append(value);
        break;
      case OPTION: {
        // Treat nested options as non-parenthesized always, prevents double parentheses.
        builder.append(formattedName).append('.').append(((OptionElement) value).toSchema());
        break;
      }
      case MAP: {
        builder.append(formattedName).append(" = {\n");
        formatOptionMap(builder, (Map<String, ?>) value);
        builder.append('}');
        break;
      }
      case LIST: {
        builder.append(formattedName).append(" = ");
        SchemaUtil.appendOptions(builder, (List<OptionElement>) value);
        break;
      }
    }
    return builder.toString();
  }

  public String toSchemaDeclaration() {
    return "option " + toSchema() + ";\n";
  }

  private static void formatOptionMap(StringBuilder builder, Map<String, ?> valueMap) {
    int lastIndex = valueMap.size() - 1;
    int index = 0;
    for (Map.Entry<String, ?> entry : valueMap.entrySet()) {
      String endl = index != lastIndex ? "," : "";
      SchemaUtil.appendIndented(builder,
          entry.getKey() + ": " + formatOptionMapValue(entry.getValue()) + endl);
      index++;
    }
  }

  private static String formatOptionMapValue(Object value) {
    if (value instanceof String) {
      return "\"" + value + "\"";
    }
    if (value instanceof OptionPrimitive) {
      OptionPrimitive primitive = (OptionPrimitive) value;
      switch (primitive.kind) {
        case BOOLEAN:
        case NUMBER:
        case ENUM:
          return primitive.value.toString();
        default:
          return formatOptionMapValue(primitive.value);
      }
    }
    if (value instanceof Map) {
      StringBuilder builder = new StringBuilder();
      builder.append("{\n");
      formatOptionMap(builder, (Map<String, ?>) value);
      builder.append('}');
      return builder.toString();
    }
    if (value instanceof List) {
      StringBuilder builder = new StringBuilder();
      builder.append("[\n");
      List<?> list = (List<?>) value;
      int lastIndex = list.size() - 1;
      for (int index = 0; index < list.size(); index++) {
        String endl = index != lastIndex ? "," : "";
        SchemaUtil.appendIndented(builder, formatOptionMapValue(list.get(index)) + endl);
      }
      builder.append(']');
      return builder.toString();
    }
    return String.valueOf(value);
  }

  @Override public String toString() {
    return "OptionElement(name=" + name + ", kind=" + kind + ", value=" + value
        + ", isParenthesized=" + isParenthesized + ")";
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof OptionElement)) return false;
    OptionElement that = (OptionElement) other;
    return this.name.equals(that.name)
        && this.kind == that.kind
        && java.util.Objects.equals(this.value, that.value)
        && this.isParenthesized == that.isParenthesized;
  }

  @Override public int hashCode() {
    int result = java.util.Objects.hashCode(name);
    result = 31 * result + kind.hashCode();
    result = 31 * result + java.util.Objects.hashCode(value);
    result = 31 * result + (isParenthesized ? 1 : 0);
    return result;
  }
}
