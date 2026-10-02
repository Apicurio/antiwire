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
package com.squareup.wire.schema.internal;

import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.internal.Internal;
import com.squareup.wire.schema.EnumType;
import com.squareup.wire.schema.Extend;
import com.squareup.wire.schema.Field;
import com.squareup.wire.schema.Options;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.ProtoType;
import com.squareup.wire.schema.Schema;
import java.lang.annotation.ElementType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/*
 * This file contains logic common to code generators targeting the JVM: Kotlin and Java. It's
 * useful to keep these consistent because sometimes a schema will be generated with some parts in
 * one language and other parts in another language.
 */
public final class JvmLanguages {
  private JvmLanguages() {
  }

  public static String builtInAdapterString(ProtoType type) {
    return builtInAdapterString(type, false);
  }

  public static String builtInAdapterString(ProtoType type, boolean useArray) {
    String protoAdapterName = ProtoAdapter.class.getName();
    if (type.isScalar) {
      if (useArray) {
        // Upstream compares with Kotlin '==' which is equals(); the linker produces ProtoType
        // instances that are equal to but not identical with the scalar singletons.
        if (type.equals(ProtoType.INT32)) return protoAdapterName + "#INT32_ARRAY";
        if (type.equals(ProtoType.UINT32)) return protoAdapterName + "#UINT32_ARRAY";
        if (type.equals(ProtoType.SINT32)) return protoAdapterName + "#SINT32_ARRAY";
        if (type.equals(ProtoType.FIXED32)) return protoAdapterName + "#FIXED32_ARRAY";
        if (type.equals(ProtoType.SFIXED32)) return protoAdapterName + "#SFIXED32_ARRAY";
        if (type.equals(ProtoType.INT64)) return protoAdapterName + "#INT64_ARRAY";
        if (type.equals(ProtoType.UINT64)) return protoAdapterName + "#UINT64_ARRAY";
        if (type.equals(ProtoType.SINT64)) return protoAdapterName + "#SINT64_ARRAY";
        if (type.equals(ProtoType.FIXED64)) return protoAdapterName + "#FIXED64_ARRAY";
        if (type.equals(ProtoType.SFIXED64)) return protoAdapterName + "#SFIXED64_ARRAY";
        if (type.equals(ProtoType.FLOAT)) return protoAdapterName + "#FLOAT_ARRAY";
        if (type.equals(ProtoType.DOUBLE)) return protoAdapterName + "#DOUBLE_ARRAY";
        throw new IllegalArgumentException("No Array adapter for " + type);
      }

      return protoAdapterName + "#" + type.toString().toUpperCase(Locale.US);
    }
    if (type.equals(ProtoType.DURATION)) return protoAdapterName + "#DURATION";
    if (type.equals(ProtoType.TIMESTAMP)) return protoAdapterName + "#INSTANT";
    if (type.equals(ProtoType.EMPTY)) return protoAdapterName + "#EMPTY";
    if (type.equals(ProtoType.FIELD_MASK)) return protoAdapterName + "#FIELD_MASK";
    if (type.equals(ProtoType.STRUCT_MAP)) return protoAdapterName + "#STRUCT_MAP";
    if (type.equals(ProtoType.STRUCT_VALUE)) return protoAdapterName + "#STRUCT_VALUE";
    if (type.equals(ProtoType.STRUCT_NULL)) return protoAdapterName + "#STRUCT_NULL";
    if (type.equals(ProtoType.STRUCT_LIST)) return protoAdapterName + "#STRUCT_LIST";
    if (type.equals(ProtoType.DOUBLE_VALUE)) return protoAdapterName + "#DOUBLE_VALUE";
    if (type.equals(ProtoType.FLOAT_VALUE)) return protoAdapterName + "#FLOAT_VALUE";
    if (type.equals(ProtoType.INT64_VALUE)) return protoAdapterName + "#INT64_VALUE";
    if (type.equals(ProtoType.UINT64_VALUE)) return protoAdapterName + "#UINT64_VALUE";
    if (type.equals(ProtoType.INT32_VALUE)) return protoAdapterName + "#INT32_VALUE";
    if (type.equals(ProtoType.UINT32_VALUE)) return protoAdapterName + "#UINT32_VALUE";
    if (type.equals(ProtoType.BOOL_VALUE)) return protoAdapterName + "#BOOL_VALUE";
    if (type.equals(ProtoType.STRING_VALUE)) return protoAdapterName + "#STRING_VALUE";
    if (type.equals(ProtoType.BYTES_VALUE)) return protoAdapterName + "#BYTES_VALUE";
    return null;
  }

  public static boolean eligibleAsAnnotationMember(Schema schema, Field field) {
    ProtoType type = field.type();

    if (type.equals(ProtoType.BYTES)) {
      return false;
    }

    if (!type.isScalar && !(schema.getType(type) instanceof EnumType)) {
      return false;
    }

    String qualifiedName = field.qualifiedName();
    if (qualifiedName.startsWith("google.protobuf.")
        || qualifiedName.startsWith("wire.")) {
      return false; // Don't emit annotations for packed, since, etc.
    }

    if (field.name().equals("redacted")) {
      return false; // Redacted is built-in.
    }

    return true;
  }

  public static ElementType annotationTargetType(Extend extend) {
    ProtoType type = extend.type();
    if (type.equals(Options.MESSAGE_OPTIONS) || type.equals(Options.ENUM_OPTIONS)
        || type.equals(Options.SERVICE_OPTIONS)) {
      return ElementType.TYPE;
    }
    if (type.equals(Options.FIELD_OPTIONS) || type.equals(Options.ENUM_VALUE_OPTIONS)) {
      return ElementType.FIELD;
    }
    if (type.equals(Options.METHOD_OPTIONS)) {
      return ElementType.METHOD;
    }
    return null;
  }

  public static int optionValueToInt(Object value) {
    if (value == null) return 0;

    String string = value.toString();
    String negativeSign = string.startsWith("-") ? "-" : "";
    String hexPrefix = negativeSign + "0x";

    if (string.regionMatches(true, 0, hexPrefix, 0, hexPrefix.length())) {
      // Hexadecimal.
      return Integer.parseInt(negativeSign + string.substring(hexPrefix.length()), 16);
    } else if (string.startsWith("0") && !string.equals("0")) {
      // Octal.
      throw new IllegalStateException("Octal literal unsupported: " + value);
    } else {
      // Decimal.
      return new BigInteger(string).intValue();
    }
  }

  public static long optionValueToLong(Object value) {
    if (value == null) return 0L;

    String string = value.toString();
    String negativeSign = string.startsWith("-") ? "-" : "";
    String hexPrefix = negativeSign + "0x";

    if (string.regionMatches(true, 0, hexPrefix, 0, hexPrefix.length())) {
      // Hexadecimal.
      return Long.parseLong(negativeSign + string.substring(hexPrefix.length()), 16);
    } else if (string.startsWith("0") && !string.equals("0")) {
      // Octal.
      throw new IllegalStateException("Octal literal unsupported: " + value);
    } else {
      // Decimal.
      return new BigInteger(string).longValue();
    }
  }

  /**
   * Returns the JVM package for {@code protoFile}, as configured by its package options.
   *
   * <p>Wire emits the value of a package option into generated code as written. This function
   * rejects a value that cannot be a package name, because such a value produces code that does
   * not match the intent of the option.
   */
  public static String javaPackage(ProtoFile protoFile) {
    String wirePackage = protoFile.wirePackage();
    if (wirePackage != null) {
      return checkPackageOptionValue(wirePackage, "wire.wire_package", protoFile);
    }

    String javaPackage = protoFile.javaPackage();
    if (javaPackage != null) {
      return checkPackageOptionValue(javaPackage, "java_package", protoFile);
    }

    return protoFile.packageName() != null ? protoFile.packageName() : "";
  }

  /**
   * Characters that a package option value must not carry.
   *
   * <p>Each one ends, or escapes, a declaration of a generated Java file. The backslash is on
   * the list because javac decodes Unicode escapes, like an escaped semicolon, before it reads
   * tokens, so a backslash reintroduces every other character on the list. Whitespace and
   * control characters are rejected too, because they also separate declarations.
   */
  private static final String FORBIDDEN_PACKAGE_CHARACTERS = ";{}()/*\"\\";

  /** Returns {@code value}, confirming first that it can be a package name in generated code. */
  private static String checkPackageOptionValue(
      String value, String optionName, ProtoFile protoFile) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      boolean forbidden = FORBIDDEN_PACKAGE_CHARACTERS.indexOf(c) != -1
          || Character.isWhitespace(c)
          || Character.isISOControl(c);
      if (forbidden) {
        throw new IllegalArgumentException(
            "Refusing to use a package option value that cannot be a package name. Wire emits "
                + "this value into generated code as written. The character below ends or "
                + "escapes a declaration.\n"
                + "  option:    " + optionName + "\n"
                + "  value:     " + displayForError(value) + "\n"
                + "  character: " + displayForError(c) + "\n"
                + "  file:      " + protoFile.location());
      }
    }
    return value;
  }

  /** Returns a form of this string which stays on one line in an error message. */
  private static String displayForError(String value) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (needsUnicodeEscape(c)) {
        result.append(String.format("\\u%04x", (int) c));
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  /** Returns a quoted form of this character which is readable in an error message. */
  private static String displayForError(char c) {
    return needsUnicodeEscape(c)
        ? "'" + String.format("\\u%04x", (int) c) + "'"
        : "'" + c + "'";
  }

  /** True if this character does not print readably on one line of an error message. */
  private static boolean needsUnicodeEscape(char c) {
    return Character.isISOControl(c) || (Character.isWhitespace(c) && c != ' ');
  }

  public static boolean hasEponymousType(Schema schema, Field field) {
    // See if the package in which the field is defined already has a
    // type by the same name. If so, the field and type name may collide.
    return schema.getType(legacyQualifiedFieldName(field)) != null;
    // TODO: This is likely incomplete. Ideally, we could search for
    //   symbols in the generated Java/Kotlin code to find conflicts based
    //   on the actual lexical scope in which the field is defined. And
    //   even then, instead of mangling the field name, we could instead
    //   use fully-qualified references to the types.
  }

  public static String legacyQualifiedFieldName(Field field) {
    // for backwards compatibility with older generated code, we use
    // package name + field name instead of the fully-qualified name.
    return field.packageName().isBlank()
        ? field.name()
        : field.packageName() + "." + field.name();
    // TODO: If a qualified name is really appropriate, it should
    //   be the fully-qualified name, not this weird hybrid.
  }

  public static <T> T annotationName(
      ProtoFile protoFile, Field extension, NameFactory<T> factory) {
    return annotationName(protoFile, extension, factory, "Option");
  }

  public static <T> T annotationName(
      ProtoFile protoFile, Field extension, NameFactory<T> factory, String simpleNameSuffix) {
    String simpleName = Internal.camelCase(extension.name(), true) + simpleNameSuffix;
    // collect class names: all enclosing message names plus simpleName
    List<String> namespaces = extension.namespaces();
    List<String> names;
    if (namespaces.size() == 0 || namespaces.size() == 1) {
      // 0 means no package and no enclosing messages
      // 1 means a package, but no enclosing messages
      names = Collections.singletonList(simpleName);
    } else {
      // 2 or more: first is a package name, the rest are enclosing messages
      names = new ArrayList<>(namespaces.subList(1, namespaces.size()));
      names.add(simpleName);
    }
    // we know that names has at least one element (simpleName), so the loop
    // below will produce a non-null type
    T type = null;
    for (String n : names) {
      type = type == null
          ? factory.newName(javaPackage(protoFile), n)
          : factory.nestedName(type, n);
    }
    if (type == null) {
      // should not be possible; keeping compiler happy
      throw new NullPointerException();
    }
    return type;
  }
}
