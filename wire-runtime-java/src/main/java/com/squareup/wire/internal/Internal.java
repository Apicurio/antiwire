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
package com.squareup.wire.internal;

import com.squareup.wire.FieldEncoding;
import com.squareup.wire.FieldMask;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.ProtoReader;
import com.squareup.wire.ProtoWriter;
import com.squareup.wire.ReverseProtoWriter;
import okio.Buffer;
import okio.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Methods for generated code use only. Not subject to public API rules. Upstream declares these
 * as Kotlin file functions on class {@code Internal}; they become static methods here.
 *
 * <p>Batch scope note (TASK-6): the {@code decodePrimitive_*} overloads taking the 32-bit
 * reader stay unported (the reader defaults route through the adapter wrapper, byte-identical
 * and owned by TASK-20 if the direct forms ever matter for performance); the Instant and Duration
 * {@code commonEquals}/{@code commonHashCode} helpers exist only for non-JVM platforms where
 * those types are real classes, so they are not ported to the JVM-only artifact.
 */
public final class Internal {
  private Internal() {
  }

  public static <T> List<T> newMutableList() {
    return new MutableOnWriteList<T>(Collections.<T>emptyList());
  }

  public static <K, V> Map<K, V> newMutableMap() {
    return new LinkedHashMap<>();
  }

  @Deprecated
  public static <T> List<T> copyOf(String name, List<T> list) {
    return copyOf(list);
  }

  public static <T> List<T> copyOf(List<T> list) {
    if (list == Collections.<T>emptyList() || list instanceof ImmutableList) {
      return new MutableOnWriteList<T>(list);
    }
    return new ArrayList<T>(list);
  }

  @Deprecated
  public static <K, V> Map<K, V> copyOf(String name, Map<K, V> map) {
    return copyOf(map);
  }

  public static <K, V> Map<K, V> copyOf(Map<K, V> map) {
    return new LinkedHashMap<>(map);
  }

  public static <T> List<T> immutableCopyOf(String name, List<T> list) {
    if (list instanceof MutableOnWriteList) {
      list = ((MutableOnWriteList<T>) list).mutableList;
    }
    if (list == Collections.<T>emptyList() || list instanceof ImmutableList) {
      return list;
    }
    ImmutableList<T> result = new ImmutableList<>(list);
    // Check after the list has been copied to defend against races.
    if (result.contains(null)) throw new IllegalArgumentException(name + ".contains(null)");
    return result;
  }

  public static <K, V> Map<K, V> immutableCopyOf(String name, Map<K, V> map) {
    if (map.isEmpty()) {
      return Collections.emptyMap();
    }
    LinkedHashMap<K, V> result = new LinkedHashMap<>(map);
    // Check after the map has been copied to defend against races.
    for (K key : result.keySet()) {
      if (key == null) throw new IllegalArgumentException(name + ".containsKey(null)");
    }
    for (V value : result.values()) {
      if (value == null) throw new IllegalArgumentException(name + ".containsValue(null)");
    }
    return Collections.unmodifiableMap(result);
  }

  /** Confirms the values of {@code map} are structs and returns an immutable copy. */
  public static <K, V> Map<K, V> immutableCopyOfMapWithStructValues(String name, Map<K, V> map) {
    LinkedHashMap<Object, Object> copy = new LinkedHashMap<>();
    for (Map.Entry<K, V> entry : map.entrySet()) {
      if (entry.getKey() == null) throw new IllegalArgumentException(name + ".containsKey(null)");
      copy.put(entry.getKey(), immutableCopyOfStruct(name, entry.getValue()));
    }
    return (Map<K, V>) Collections.unmodifiableMap(copy);
  }

  /** Confirms {@code value} is a struct and returns an immutable copy. */
  public static <T> T immutableCopyOfStruct(String name, Object value) {
    if (value == null || value instanceof Boolean || value instanceof Double
        || value instanceof String) {
      return (T) value;
    }
    if (value instanceof List) {
      List<Object> copy = new ArrayList<>();
      for (Object element : (List<?>) value) {
        copy.add(immutableCopyOfStruct(name, element));
      }
      return (T) Collections.unmodifiableList(copy);
    }
    if (value instanceof Map) {
      Map<Object, Object> copy = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        copy.put(immutableCopyOfStruct(name, entry.getKey()),
            immutableCopyOfStruct(name, entry.getValue()));
      }
      return (T) Collections.unmodifiableMap(copy);
    }
    throw new IllegalArgumentException(
        "struct value " + name + " must be a JSON type "
            + "(null, Boolean, Double, String, List, or Map) but was " + value.getClass() + ": "
            + value);
  }

  /** Returns the enum constant of {@code type} with tag 0, or null. */
  public static <E extends com.squareup.wire.WireEnum> E identityOrNull(Class<E> type) {
    for (E constant : type.getEnumConstants()) {
      if (constant.getValue() == 0) return constant;
    }
    return null;
  }

  /** Upstream's camelCase helper from the Internal facade (one-line form). */
  public static String camelCase(String value, boolean upperCamel) {
    StringBuilder result = new StringBuilder();
    boolean capitalize = upperCamel;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '_') {
        capitalize = true;
      } else if (capitalize) {
        result.append(Character.toUpperCase(c));
        capitalize = false;
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  public static boolean equals(Object a, Object b) {
    return a == b || (a != null && a.equals(b));
  }

  /**
   * Create an exception for missing required fields.
   *
   * @param args Alternating field value and field name pairs.
   */
  public static IllegalStateException missingRequiredFields(Object... args) {
    String plural = "";
    StringBuilder fields = new StringBuilder();
    for (int i = 0; i < args.length; i += 2) {
      if (args[i] == null) {
        if (fields.length() > 0) {
          plural = "s"; // Found more than one missing field
        }
        fields.append("\n  ");
        fields.append(args[i + 1]);
      }
    }
    throw new IllegalStateException("Required field" + plural + " not set:" + fields);
  }

  /** Throw {@link NullPointerException} if any of {@code list}'s items is null. */
  public static void checkElementsNotNull(List<?> list) {
    for (int i = 0; i < list.size(); i++) {
      if (list.get(i) == null) {
        throw new NullPointerException("Element at index " + i + " is null");
      }
    }
  }

  /** Throw {@link NullPointerException} if any of {@code map}'s keys or values is null. */
  public static void checkElementsNotNull(Map<?, ?> map) {
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry.getKey() == null) {
        throw new NullPointerException("map.containsKey(null)");
      }
      if (entry.getValue() == null) {
        throw new NullPointerException("Value for key " + entry.getKey() + " is null");
      }
    }
  }

  /** Returns the number of non-null values in {@code a, b}. */
  public static int countNonNull(Object a, Object b) {
    return (a != null ? 1 : 0) + (b != null ? 1 : 0);
  }

  /** Returns the number of non-null values in {@code a, b, c}. */
  public static int countNonNull(Object a, Object b, Object c) {
    return (a != null ? 1 : 0) + (b != null ? 1 : 0) + (c != null ? 1 : 0);
  }

  /** Returns the number of non-null values in {@code a, b, c, d, rest}. */
  public static int countNonNull(Object a, Object b, Object c, Object d, Object... rest) {
    int result = 0;
    if (a != null) result++;
    if (b != null) result++;
    if (c != null) result++;
    if (d != null) result++;
    for (Object o : rest) {
      if (o != null) result++;
    }
    return result;
  }

  private static final String ESCAPED_CHARS = ",[]{}\\";

  /** Return a string where {@code ,[]{}\} are escaped with a {@code \}. */
  public static String sanitize(String value) {
    StringBuilder result = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (ESCAPED_CHARS.indexOf(c) != -1) result.append('\\');
      result.append(c);
    }
    return result.toString();
  }

  /** Return a string where {@code ,[]{}\} are escaped with a {@code \}. */
  public static String sanitize(List<String> values) {
    StringBuilder result = new StringBuilder("[");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) result.append(", ");
      result.append(sanitize(values.get(i)));
    }
    return result.append(']').toString();
  }

  public static String boxedOneOfClassName(String oneOfName) {
    if (oneOfName.isEmpty()) return oneOfName;
    char first = Character.toTitleCase(oneOfName.charAt(0));
    return first + oneOfName.substring(1);
  }

  /** Maps {@code oneOfName} and {@code fieldName} to the key representing a boxed oneof field. */
  public static String boxedOneOfKeyFieldName(String oneOfName, String fieldName) {
    return (oneOfName + "_" + fieldName).toUpperCase(Locale.ROOT);
  }

  /** Maps {@code oneOfName} to the field of type {@code Set} holding the eligible keys. */
  public static String boxedOneOfKeysFieldName(String oneOfName) {
    return (oneOfName + "_keys").toUpperCase(Locale.ROOT);
  }

  public static <T> List<T> redactElements(List<T> list, ProtoAdapter<T> adapter) {
    List<T> result = new ArrayList<>(list.size());
    for (T value : list) {
      result.add(adapter.redact(value));
    }
    return result;
  }

  public static <K, V> Map<K, V> redactElements(Map<K, V> map, ProtoAdapter<V> adapter) {
    Map<K, V> result = new LinkedHashMap<>(map.size());
    for (Map.Entry<K, V> entry : map.entrySet()) {
      result.put(entry.getKey(), adapter.redact(entry.getValue()));
    }
    return result;
  }

  /**
   * Decodes a message from {@code reader}, merging with {@code existing} if not null. Per the
   * proto specification, when an embedded message field appears multiple times, the values are
   * merged: repeated fields are concatenated, singular fields take the later value.
   */
  public static <E> E decodeMessageOrMerge(ProtoAdapter<E> adapter, ProtoReader reader, E existing)
      throws java.io.IOException {
    if (existing == null) return adapter.decode(reader);
    if (adapter == ProtoAdapter.FIELD_MASK) {
      return (E) ((FieldMask) existing).append(ProtoAdapter.FIELD_MASK.decode(reader).paths());
    }
    ByteString bytes = reader.readBytes();
    Buffer buffer = new Buffer();
    adapter.encode(buffer, existing);
    buffer.write(bytes);
    return adapter.decode(buffer);
  }

  public static double decodePrimitive_double(ProtoReader reader) throws java.io.IOException {
    return Double.longBitsToDouble(reader.readFixed64());
  }

  public static int decodePrimitive_fixed32(ProtoReader reader) throws java.io.IOException {
    return reader.readFixed32();
  }

  public static long decodePrimitive_fixed64(ProtoReader reader) throws java.io.IOException {
    return reader.readFixed64();
  }

  public static float decodePrimitive_float(ProtoReader reader) throws java.io.IOException {
    return Float.intBitsToFloat(reader.readFixed32());
  }

  public static int decodePrimitive_int32(ProtoReader reader) throws java.io.IOException {
    return reader.readVarint32();
  }

  public static long decodePrimitive_int64(ProtoReader reader) throws java.io.IOException {
    return reader.readVarint64();
  }

  public static int decodePrimitive_sfixed32(ProtoReader reader) throws java.io.IOException {
    return reader.readFixed32();
  }

  public static long decodePrimitive_sfixed64(ProtoReader reader) throws java.io.IOException {
    return reader.readFixed64();
  }

  public static int decodePrimitive_sint32(ProtoReader reader) throws java.io.IOException {
    return ProtoWriter.decodeZigZag32(reader.readVarint32());
  }

  public static long decodePrimitive_sint64(ProtoReader reader) throws java.io.IOException {
    return ProtoWriter.decodeZigZag64(reader.readVarint64());
  }

  public static int decodePrimitive_uint32(ProtoReader reader) throws java.io.IOException {
    return reader.readVarint32();
  }

  public static long decodePrimitive_uint64(ProtoReader reader) throws java.io.IOException {
    return reader.readVarint64();
  }

  public static void encodeArray_int32(int[] array, ReverseProtoWriter writer, int tag)
      throws java.io.IOException {
    if (array.length != 0) {
      int byteCountBefore = writer.byteCount();
      for (int i = array.length - 1; i >= 0; i--) {
        writer.writeSignedVarint32(array[i]);
      }
      writer.writeVarint32(writer.byteCount() - byteCountBefore);
      writer.writeTag(tag, FieldEncoding.LENGTH_DELIMITED);
    }
  }

  public static void encodeArray_uint32(int[] array, ReverseProtoWriter writer, int tag)
      throws java.io.IOException {
    if (array.length != 0) {
      int byteCountBefore = writer.byteCount();
      for (int i = array.length - 1; i >= 0; i--) {
        writer.writeVarint32(array[i]);
      }
      writer.writeVarint32(writer.byteCount() - byteCountBefore);
      writer.writeTag(tag, FieldEncoding.LENGTH_DELIMITED);
    }
  }

  public static void encodeArray_sint32(int[] array, ReverseProtoWriter writer, int tag)
      throws java.io.IOException {
    if (array.length != 0) {
      int byteCountBefore = writer.byteCount();
      for (int i = array.length - 1; i >= 0; i--) {
        writer.writeVarint32(ProtoWriter.encodeZigZag32(array[i]));
      }
      writer.writeVarint32(writer.byteCount() - byteCountBefore);
      writer.writeTag(tag, FieldEncoding.LENGTH_DELIMITED);
    }
  }

  public static void encodeArray_fixed32(int[] array, ReverseProtoWriter writer, int tag)
      throws java.io.IOException {
    if (array.length != 0) {
      int byteCountBefore = writer.byteCount();
      for (int i = array.length - 1; i >= 0; i--) {
        writer.writeFixed32(array[i]);
      }
      writer.writeVarint32(writer.byteCount() - byteCountBefore);
      writer.writeTag(tag, FieldEncoding.LENGTH_DELIMITED);
    }
  }

  public static void encodeArray_sfixed32(int[] array, ReverseProtoWriter writer, int tag)
      throws java.io.IOException {
    encodeArray_fixed32(array, writer, tag);
  }

  public static void encodeArray_int64(long[] array, ReverseProtoWriter writer, int tag)
      throws java.io.IOException {
    if (array.length != 0) {
      int byteCountBefore = writer.byteCount();
      for (int i = array.length - 1; i >= 0; i--) {
        writer.writeVarint64(array[i]);
      }
      writer.writeVarint32(writer.byteCount() - byteCountBefore);
      writer.writeTag(tag, FieldEncoding.LENGTH_DELIMITED);
    }
  }

  public static void encodeArray_uint64(long[] array, ReverseProtoWriter writer, int tag)
      throws java.io.IOException {
    encodeArray_int64(array, writer, tag);
  }

  public static void encodeArray_sint64(long[] array, ReverseProtoWriter writer, int tag)
      throws java.io.IOException {
    if (array.length != 0) {
      int byteCountBefore = writer.byteCount();
      for (int i = array.length - 1; i >= 0; i--) {
        writer.writeVarint64(ProtoWriter.encodeZigZag64(array[i]));
      }
      writer.writeVarint32(writer.byteCount() - byteCountBefore);
      writer.writeTag(tag, FieldEncoding.LENGTH_DELIMITED);
    }
  }

  public static void encodeArray_fixed64(long[] array, ReverseProtoWriter writer, int tag)
      throws java.io.IOException {
    if (array.length != 0) {
      int byteCountBefore = writer.byteCount();
      for (int i = array.length - 1; i >= 0; i--) {
        writer.writeFixed64(array[i]);
      }
      writer.writeVarint32(writer.byteCount() - byteCountBefore);
      writer.writeTag(tag, FieldEncoding.LENGTH_DELIMITED);
    }
  }

  public static void encodeArray_sfixed64(long[] array, ReverseProtoWriter writer, int tag)
      throws java.io.IOException {
    encodeArray_fixed64(array, writer, tag);
  }

  public static void encodeArray_float(float[] array, ReverseProtoWriter writer, int tag)
      throws java.io.IOException {
    if (array.length != 0) {
      int byteCountBefore = writer.byteCount();
      for (int i = array.length - 1; i >= 0; i--) {
        writer.writeFixed32(Float.floatToIntBits(array[i]));
      }
      writer.writeVarint32(writer.byteCount() - byteCountBefore);
      writer.writeTag(tag, FieldEncoding.LENGTH_DELIMITED);
    }
  }

  public static void encodeArray_double(double[] array, ReverseProtoWriter writer, int tag)
      throws java.io.IOException {
    if (array.length != 0) {
      int byteCountBefore = writer.byteCount();
      for (int i = array.length - 1; i >= 0; i--) {
        writer.writeFixed64(Double.doubleToLongBits(array[i]));
      }
      writer.writeVarint32(writer.byteCount() - byteCountBefore);
      writer.writeTag(tag, FieldEncoding.LENGTH_DELIMITED);
    }
  }
}
