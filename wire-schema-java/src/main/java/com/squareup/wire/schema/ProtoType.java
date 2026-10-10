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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Names a protocol buffer message, enumerated type, service, map, or a scalar. This class models a
 * fully-qualified name using the protocol buffer package.
 */
public final class ProtoType {
  private final boolean isScalar;

  private final String string;

  private final boolean isMap;

  /** The type of the map's keys. Only present when {@link #isMap()} is true. */
  private final ProtoType keyType;

  public ProtoType getKeyType() {
    return keyType;
  }

  /** The type of the map's values. Only present when {@link #isMap()} is true. */
  private final ProtoType valueType;

  public ProtoType getValueType() {
    return valueType;
  }

  /** Creates a scalar or message type. */
  private ProtoType(boolean isScalar, String string) {
    this.isScalar = isScalar;
    this.string = string;
    this.isMap = false;
    this.keyType = null;
    this.valueType = null;
  }

  /** Creates a map type. */
  private ProtoType(ProtoType keyType, ProtoType valueType, String string) {
    if (!(keyType.isScalar && keyType != BYTES && keyType != DOUBLE && keyType != FLOAT)) {
      throw new IllegalArgumentException(
          "map key must be non-byte, non-floating point scalar: " + keyType);
    }
    this.isScalar = false;
    this.string = string;
    this.isMap = true;
    this.keyType = keyType; // TODO restrict what's allowed here
    this.valueType = valueType;
  }

  public String getSimpleName() {
    int dot = string.lastIndexOf('.');
    return string.substring(dot + 1);
  }

  /** Returns the enclosing type, or null if this type is not nested in another type. */
  public String getEnclosingTypeOrPackage() {
    int dot = string.lastIndexOf('.');
    return dot == -1 ? null : string.substring(0, dot);
  }

  /**
   * Returns a string like "type.googleapis.com/packagename.messagename" or null if this type is
   * a scalar or a map. Note that this returns a non-null string for enums because it doesn't know
   * if the named type is a message or an enum.
   */
  public String getTypeUrl() {
    if (isScalar || isMap) return null;
    return "type.googleapis.com/" + string;
  }

  /** True if this type is defined in {@code google/protobuf/wrappers.proto}. */
  public boolean isWrapper() {
    return WRAPPER_TYPES.contains(this);
  }

  public ProtoType nestedType(String name) {
    if (isScalar) throw new IllegalStateException("scalar cannot have a nested type");
    if (isMap) throw new IllegalStateException("map cannot have a nested type");
    if (name == null || name.contains(".") || name.isEmpty()) {
      throw new IllegalArgumentException("unexpected name: " + name);
    }
    return new ProtoType(false, string + "." + name);
  }

  @Override public boolean equals(Object other) {
    return other instanceof ProtoType && string.equals(((ProtoType) other).string);
  }

  @Override public int hashCode() {
    return string.hashCode();
  }

  @Override public String toString() {
    return string;
  }

  public static final ProtoType BOOL = new ProtoType(true, "bool");
  public static final ProtoType BYTES = new ProtoType(true, "bytes");
  public static final ProtoType DOUBLE = new ProtoType(true, "double");
  public static final ProtoType FLOAT = new ProtoType(true, "float");
  public static final ProtoType FIXED32 = new ProtoType(true, "fixed32");
  public static final ProtoType FIXED64 = new ProtoType(true, "fixed64");
  public static final ProtoType INT32 = new ProtoType(true, "int32");
  public static final ProtoType INT64 = new ProtoType(true, "int64");
  public static final ProtoType SFIXED32 = new ProtoType(true, "sfixed32");
  public static final ProtoType SFIXED64 = new ProtoType(true, "sfixed64");
  public static final ProtoType SINT32 = new ProtoType(true, "sint32");
  public static final ProtoType SINT64 = new ProtoType(true, "sint64");
  public static final ProtoType STRING = new ProtoType(true, "string");
  public static final ProtoType UINT32 = new ProtoType(true, "uint32");
  public static final ProtoType UINT64 = new ProtoType(true, "uint64");

  public static final ProtoType ANY = new ProtoType(false, "google.protobuf.Any");
  public static final ProtoType DURATION = new ProtoType(false, "google.protobuf.Duration");
  public static final ProtoType TIMESTAMP = new ProtoType(false, "google.protobuf.Timestamp");
  public static final ProtoType EMPTY = new ProtoType(false, "google.protobuf.Empty");
  public static final ProtoType FIELD_MASK = new ProtoType(false, "google.protobuf.FieldMask");
  public static final ProtoType STRUCT_MAP = new ProtoType(false, "google.protobuf.Struct");
  public static final ProtoType STRUCT_VALUE = new ProtoType(false, "google.protobuf.Value");
  public static final ProtoType STRUCT_NULL = new ProtoType(false, "google.protobuf.NullValue");
  public static final ProtoType STRUCT_LIST = new ProtoType(false, "google.protobuf.ListValue");
  public static final ProtoType DOUBLE_VALUE = new ProtoType(false, "google.protobuf.DoubleValue");
  public static final ProtoType FLOAT_VALUE = new ProtoType(false, "google.protobuf.FloatValue");
  public static final ProtoType INT64_VALUE = new ProtoType(false, "google.protobuf.Int64Value");
  public static final ProtoType UINT64_VALUE = new ProtoType(false, "google.protobuf.UInt64Value");
  public static final ProtoType INT32_VALUE = new ProtoType(false, "google.protobuf.Int32Value");
  public static final ProtoType UINT32_VALUE = new ProtoType(false, "google.protobuf.UInt32Value");
  public static final ProtoType BOOL_VALUE = new ProtoType(false, "google.protobuf.BoolValue");
  public static final ProtoType STRING_VALUE = new ProtoType(false, "google.protobuf.StringValue");
  public static final ProtoType BYTES_VALUE = new ProtoType(false, "google.protobuf.BytesValue");

  private static final Map<String, ProtoType> SCALAR_TYPES = new LinkedHashMap<>();
  static final List<ProtoType> NUMERIC_SCALAR_TYPES = new ArrayList<>();
  /** All types defined in {@code google/protobuf/wrappers.proto}. */
  static final List<ProtoType> WRAPPER_TYPES = new ArrayList<>();

  static {
    ProtoType[] scalars = {
        BOOL, BYTES, DOUBLE, FLOAT, FIXED32, FIXED64, INT32, INT64,
        SFIXED32, SFIXED64, SINT32, SINT64, STRING, UINT32, UINT64
    };
    for (ProtoType type : scalars) {
      SCALAR_TYPES.put(type.string, type);
    }
    ProtoType[] numeric = {
        DOUBLE, FLOAT, FIXED32, FIXED64, INT32, INT64,
        SFIXED32, SFIXED64, SINT32, SINT64, UINT32, UINT64
    };
    for (ProtoType type : numeric) {
      NUMERIC_SCALAR_TYPES.add(type);
    }
    ProtoType[] wrappers = {
        DOUBLE_VALUE, FLOAT_VALUE, INT64_VALUE, UINT64_VALUE, INT32_VALUE,
        UINT32_VALUE, BOOL_VALUE, STRING_VALUE, BYTES_VALUE
    };
    for (ProtoType type : wrappers) {
      WRAPPER_TYPES.add(type);
    }
  }

  public static ProtoType get(String enclosingTypeOrPackage, String typeName) {
    if (enclosingTypeOrPackage != null) return get(enclosingTypeOrPackage + "." + typeName);
    return get(typeName);
  }

  public static ProtoType get(String name) {
    ProtoType scalar = name == null ? null : SCALAR_TYPES.get(name);
    if (scalar != null) return scalar;

    if (name == null || name.isEmpty() || name.contains("#")) {
      throw new IllegalArgumentException("unexpected name: " + name);
    }

    if (name.startsWith("map<") && name.endsWith(">")) {
      int comma = name.indexOf(',');
      if (comma == -1) {
        throw new IllegalArgumentException("expected ',' in map type: " + name);
      }
      ProtoType key = get(name.substring(4, comma).trim());
      ProtoType value = get(name.substring(comma + 1, name.length() - 1).trim());
      return new ProtoType(key, value, name);
    }

    return new ProtoType(false, name);
  }

  public static ProtoType get(ProtoType keyType, ProtoType valueType, String name) {
    return new ProtoType(keyType, valueType, name);
  }

  public boolean isScalar() {
    return isScalar;
  }

  public boolean isMap() {
    return isMap;
  }

  /** Mirror of the Kotlin companion object: lets Java callers write {@code ProtoType.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }

    public ProtoType get(ProtoType keyType, ProtoType valueType, String name) {
      return ProtoType.get(keyType, valueType, name);
    }

    public ProtoType get(String name) {
      return ProtoType.get(name);
    }

    public ProtoType get(String enclosingTypeOrPackage, String typeName) {
      return ProtoType.get(enclosingTypeOrPackage, typeName);
    }
  }
}
