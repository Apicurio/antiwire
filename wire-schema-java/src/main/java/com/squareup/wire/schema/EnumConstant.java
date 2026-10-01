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

import com.squareup.wire.schema.internal.parser.EnumConstantElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EnumConstant {
  private static final ProtoMember DEPRECATED =
      ProtoMember.get(Options.ENUM_VALUE_OPTIONS, "deprecated");

  final Location location;
  final String name;
  final int tag;
  final String documentation;
  final Options options;

  EnumConstant(Location location, String name, int tag, String documentation, Options options) {
    this.location = location;
    this.name = name;
    this.tag = tag;
    this.documentation = documentation;
    this.options = options;
  }

  public Location location() {
    return location;
  }

  public String name() {
    return name;
  }

  public int tag() {
    return tag;
  }

  public String documentation() {
    return documentation;
  }

  public Options options() {
    return options;
  }

  public boolean isDeprecated() {
    return "true".equals(options.get(DEPRECATED));
  }

  EnumConstantElement toElement() {
    return new EnumConstantElement(location, name, tag, documentation, options.elements());
  }

  void linkOptions(Linker linker, boolean validate) {
    Linker scoped = linker.withContext(this);
    options.link(scoped, location, validate);
  }

  EnumConstant retainAll(Schema schema, MarkSet markSet) {
    return new EnumConstant(location, name, tag, documentation, options.retainAll(schema, markSet));
  }

  EnumConstant retainLinked() {
    return new EnumConstant(location, name, tag, documentation, options.retainLinked());
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof EnumConstant)) return false;
    EnumConstant that = (EnumConstant) other;
    return location.equals(that.location)
        && name.equals(that.name)
        && tag == that.tag
        && documentation.equals(that.documentation)
        && Objects.equals(options, that.options);
  }

  @Override public int hashCode() {
    int result = location.hashCode();
    result = 31 * result + name.hashCode();
    result = 31 * result + tag;
    result = 31 * result + documentation.hashCode();
    result = 31 * result + Objects.hashCode(options);
    return result;
  }

  @Override public String toString() {
    return "EnumConstant(location=" + location + ", name=" + name + ", tag=" + tag
        + ", documentation=" + documentation + ", options=" + options + ")";
  }

  public static List<EnumConstant> fromElements(List<EnumConstantElement> elements) {
    List<EnumConstant> result = new ArrayList<>();
    for (EnumConstantElement element : elements) {
      result.add(new EnumConstant(element.location, element.name, element.tag,
          element.documentation, new Options(Options.ENUM_VALUE_OPTIONS, element.options)));
    }
    return result;
  }

  public static List<EnumConstantElement> toElements(List<EnumConstant> constants) {
    List<EnumConstantElement> result = new ArrayList<>();
    for (EnumConstant constant : constants) {
      result.add(constant.toElement());
    }
    return result;
  }
}
