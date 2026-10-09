/*
 * Copyright (C) 2019 Square, Inc.
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
package com.squareup.wire.schema.internal;

import com.squareup.wire.schema.EnclosingType;
import com.squareup.wire.schema.EnumType;
import com.squareup.wire.schema.MessageType;
import com.squareup.wire.schema.Options;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.ProtoType;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.Service;
import com.squareup.wire.schema.Type;
import com.squareup.wire.schema.internal.parser.OptionElement;
import java.util.List;

/** The schema-formatting helpers upstream declares on the Internal facade. */
public final class SchemaUtil {
  private SchemaUtil() {
  }

  public static final int MIN_TAG_VALUE = 1;
  public static final int MAX_TAG_VALUE = (1 << 29) - 1; // 536,870,911

  private static final int RESERVED_TAG_VALUE_START = 19000;
  private static final int RESERVED_TAG_VALUE_END = 19999;

  /** True if the supplied value is in the valid tag range and not reserved. */
  public static boolean isValidTag(int value) {
    return (value >= MIN_TAG_VALUE && value < RESERVED_TAG_VALUE_START)
        || (value > RESERVED_TAG_VALUE_END && value <= MAX_TAG_VALUE);
  }

  public static void appendDocumentation(StringBuilder builder, String documentation) {
    if (documentation.isEmpty()) {
      return;
    }
    String[] lines = documentation.split("\n", -1);
    int count = lines.length;
    if (count > 1 && lines[count - 1].isEmpty()) {
      count--;
    }
    for (int i = 0; i < count; i++) {
      builder.append("// ").append(lines[i]).append('\n');
    }
  }

  public static void appendOptions(StringBuilder builder, List<OptionElement> options) {
    int count = options.size();
    if (count == 1) {
      builder.append('[').append(options.get(0).toSchema()).append(']');
      return;
    }
    builder.append("[\n");
    for (int i = 0; i < count; i++) {
      String endl = i < count - 1 ? "," : "";
      appendIndented(builder, options.get(i).toSchema() + endl);
    }
    builder.append(']');
  }

  public static void appendIndented(StringBuilder builder, String value) {
    String[] lines = value.split("\n", -1);
    int count = lines.length;
    if (count > 1 && lines[count - 1].isEmpty()) {
      count--;
    }
    for (int i = 0; i < count; i++) {
      builder.append("  ").append(lines[i]).append('\n');
    }
  }

  /** Upstream's UtilJVM actual: lowercase(Locale.US), which is Unicode-aware. */
  public static String toEnglishLowerCase(String value) {
    return value.toLowerCase(java.util.Locale.US);
  }

  /** Values mix Integers and int[] ranges; compare ranges structurally like IntRange. */
  public static boolean valuesEqual(java.util.List<Object> a, java.util.List<Object> b) {
    if (a.size() != b.size()) return false;
    java.util.Iterator<Object> bi = b.iterator();
    for (Object value : a) {
      Object other = bi.next();
      if (value instanceof int[]) {
        if (!(other instanceof int[]) || !java.util.Arrays.equals((int[]) value, (int[]) other)) {
          return false;
        }
      } else if (!value.equals(other)) {
        return false;
      }
    }
    return true;
  }

  /** Values mix Integers and int[] ranges; hash ranges structurally like IntRange. */
  public static int valuesHashCode(java.util.List<Object> values) {
    int result = 1;
    for (Object value : values) {
      result = 31 * result
          + (value instanceof int[] ? java.util.Arrays.hashCode((int[]) value) : value.hashCode());
    }
    return result;
  }

  /**
   * Replace types in this schema which are present in {@code typesToStub} with empty shells that
   * have no outward references. This has to be done in this module so that we can access the
   * internal constructor to avoid re-linking.
   */
  public static Schema withStubs(Schema schema, java.util.Set<ProtoType> typesToStub) {
    if (typesToStub.isEmpty()) {
      return schema;
    }
    List<ProtoFile> protoFiles = new java.util.ArrayList<>();
    for (ProtoFile protoFile : schema.getProtoFiles()) {
      List<Type> types = new java.util.ArrayList<>();
      for (Type type : protoFile.getTypes()) {
        types.add(typesToStub.contains(type.getType()) ? asStub(type) : type);
      }
      List<Service> services = new java.util.ArrayList<>();
      for (Service service : protoFile.getServices()) {
        services.add(typesToStub.contains(service.type()) ? asStub(service) : service);
      }
      protoFiles.add(protoFile.copy(
          protoFile.getLocation(), protoFile.getImports(), protoFile.getPublicImports(),
          protoFile.getWeakImports(), protoFile.getPackageName(), types, services,
          protoFile.getExtendList(), protoFile.getOptions(), protoFile.getSyntax()));
    }
    return new Schema(protoFiles);
  }

  /** Return a copy of this type with all possible type references removed. */
  private static Type asStub(Type type) {
    // Don't stub the built-in protobuf types which model concepts like options.
    if (type.getType().toString().startsWith("google.protobuf.")) {
      return type;
    }

    if (type instanceof MessageType) {
      MessageType messageType = (MessageType) type;
      List<Type> nestedTypes = new java.util.ArrayList<>();
      for (Type nestedType : messageType.getNestedTypes()) {
        nestedTypes.add(asStub(nestedType));
      }
      return messageType.copy(
          messageType.getType(), messageType.getLocation(), messageType.getDocumentation(),
          messageType.getName(), java.util.Collections.emptyList(), new java.util.ArrayList<>(),
          messageType.getOneOfs(), nestedTypes, messageType.getNestedExtendList(),
          messageType.getExtensionsList(), messageType.reserveds(),
          new Options(Options.MESSAGE_OPTIONS, java.util.Collections.emptyList()),
          messageType.getSyntax());
    }

    if (type instanceof EnumType) {
      EnumType enumType = (EnumType) type;
      return enumType.copy(
          enumType.getType(), enumType.getLocation(), enumType.getDocumentation(), enumType.getName(),
          java.util.Collections.emptyList(), enumType.reserveds(),
          new Options(Options.ENUM_OPTIONS, java.util.Collections.emptyList()),
          enumType.getSyntax());
    }

    if (type instanceof EnclosingType) {
      EnclosingType enclosingType = (EnclosingType) type;
      List<Type> nestedTypes = new java.util.ArrayList<>();
      for (Type nestedType : enclosingType.getNestedTypes()) {
        nestedTypes.add(asStub(nestedType));
      }
      return enclosingType.copy(
          enclosingType.getLocation(), enclosingType.getType(), enclosingType.getName(),
          enclosingType.getDocumentation(), nestedTypes, enclosingType.getNestedExtendList(),
          enclosingType.getSyntax());
    }

    throw new AssertionError("Unknown type " + type.getType());
  }

  /** Return a copy of this service with all possible type references removed. */
  private static Service asStub(Service service) {
    return service.copy(
        service.type(), service.location(), service.documentation(), service.name(),
        java.util.Collections.emptyList(),
        new Options(Options.SERVICE_OPTIONS, java.util.Collections.emptyList()));
  }
}
