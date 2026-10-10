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

import com.squareup.wire.schema.Field.Label;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.ProtoType;
import com.squareup.wire.schema.internal.SchemaUtil;
import java.util.ArrayList;
import java.util.List;

public final class FieldElement {
  private final Location location;

  public Location getLocation() {
    return location;
  }

  private final Label label;

  public Label getLabel() {
    return label;
  }

  private final String type;

  public String getType() {
    return type;
  }

  private final String name;

  public String getName() {
    return name;
  }

  private final String defaultValue;

  public String getDefaultValue() {
    return defaultValue;
  }

  private final String jsonName;

  public String getJsonName() {
    return jsonName;
  }

  private final int tag;

  public int getTag() {
    return tag;
  }

  private final String documentation;

  public String getDocumentation() {
    return documentation;
  }

  private final List<OptionElement> options;

  public List<OptionElement> getOptions() {
    return options;
  }

  public FieldElement(Location location, Label label, String type, String name,
      String defaultValue, String jsonName, int tag, String documentation,
      List<OptionElement> options) {
    this.location = location;
    this.label = label;
    this.type = type;
    this.name = name;
    this.defaultValue = defaultValue;
    this.jsonName = jsonName;
    this.tag = tag;
    this.documentation = documentation;
    this.options = options;
  }

  int getLine() {
    return location.getLine();
  }

  int getColumn() {
    return location.getColumn();
  }

  public String toSchema() {
    StringBuilder builder = new StringBuilder();
    SchemaUtil.appendDocumentation(builder, documentation);

    if (label != null) {
      builder.append(SchemaUtil.toEnglishLowerCase(label.name())).append(' ');
    }
    builder.append(type).append(' ').append(name).append(" = ").append(tag);

    List<OptionElement> optionsWithDefault = optionsWithSpecialValues();
    if (!optionsWithDefault.isEmpty()) {
      builder.append(' ');
      SchemaUtil.appendOptions(builder, optionsWithDefault);
    }

    builder.append(";\n");
    return builder.toString();
  }

  /**
   * Both default and json_name are defined in the schema like options but they are actually not
   * options themselves as they're missing from google.protobuf.FieldOptions.
   */
  private List<OptionElement> optionsWithSpecialValues() {
    List<OptionElement> result = new ArrayList<>(options);
    if (defaultValue != null) {
      ProtoType protoType = ProtoType.get(type);
      result.add(OptionElement.create("default", toKind(protoType), defaultValue));
    }
    if (jsonName != null) {
      result.add(OptionElement.create("json_name", OptionElement.Kind.STRING, jsonName));
    }
    return result;
  }

  // Only non-repeated scalar types and Enums support default values.
  private static OptionElement.Kind toKind(ProtoType protoType) {
    String simpleName = protoType.getSimpleName();
    switch (simpleName) {
      case "bool":
        return OptionElement.Kind.BOOLEAN;
      case "string":
        return OptionElement.Kind.STRING;
      case "bytes":
      case "double":
      case "float":
      case "fixed32":
      case "fixed64":
      case "int32":
      case "int64":
      case "sfixed32":
      case "sfixed64":
      case "sint32":
      case "sint64":
      case "uint32":
      case "uint64":
        return OptionElement.Kind.NUMBER;
      default:
        return OptionElement.Kind.ENUM;
    }
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof FieldElement)) return false;
    FieldElement that = (FieldElement) other;
    return true
      && java.util.Objects.equals(this.location, that.location)
      && java.util.Objects.equals(this.label, that.label)
      && java.util.Objects.equals(this.type, that.type)
      && java.util.Objects.equals(this.name, that.name)
      && java.util.Objects.equals(this.defaultValue, that.defaultValue)
      && java.util.Objects.equals(this.jsonName, that.jsonName)
      && this.tag == that.tag
      && java.util.Objects.equals(this.documentation, that.documentation)
      && java.util.Objects.equals(this.options, that.options);
  }

  @Override public int hashCode() {
    int result = java.util.Objects.hashCode(location);
    result = 31 * result + java.util.Objects.hashCode(label);result = 31 * result + java.util.Objects.hashCode(type);result = 31 * result + java.util.Objects.hashCode(name);result = 31 * result + java.util.Objects.hashCode(defaultValue);result = 31 * result + java.util.Objects.hashCode(jsonName);result = 31 * result + tag;result = 31 * result + java.util.Objects.hashCode(documentation);result = 31 * result + java.util.Objects.hashCode(options);    return result;
  }

  @Override public String toString() {
    return "FieldElement(" + "location=" + location + ", " + "label=" + label + ", " + "type=" + type + ", " + "name=" + name + ", " + "defaultValue=" + defaultValue + ", " + "jsonName=" + jsonName + ", " + "tag=" + tag + ", " + "documentation=" + documentation + ", " + "options=" + options + ")";
  }
}
