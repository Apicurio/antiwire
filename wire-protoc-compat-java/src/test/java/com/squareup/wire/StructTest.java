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
package com.squareup.wire;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import squareup.proto3.java.alltypes.AllStructs;
import squareup.proto3.java.alltypes.AllStructsOuterClass;

/**
 * Upstream wire-protoc-compatibility-tests StructTest.kt translated: assertk to JUnit 5;
 * assertk's containsExactly on lists to JUnit list equality, containsOnly on maps to JUnit
 * map equality (both order-insensitive where assertk was). The Kotlin-generator model
 * (upstream's AllStructsK) is a DEC-6 exclusion in this module, so the mixed cases keep
 * their protoc and wire-Java halves and the Kotlin-model-only cases are disabled with their
 * bodies preserved. Expected bytes are untouched. See this module's
 * UPSTREAM-TEST-ADAPTATIONS.md.
 */
public class StructTest {
  @Test public void nullValue() throws IOException {
    com.google.protobuf.Value googleMessage = ProtocStructHelper.toValue(null);

    Object wireMessage = null;

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.STRUCT_VALUE.encode(wireMessage));
    assertNull(ProtoAdapter.STRUCT_VALUE.decode(googleMessageBytes));
  }

  @Test public void doubleValue() throws IOException {
    com.google.protobuf.Value googleMessage = ProtocStructHelper.toValue(0.25);

    Object wireMessage = 0.25;

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.STRUCT_VALUE.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.STRUCT_VALUE.decode(googleMessageBytes));
  }

  @Test public void specialDoubleValues() throws IOException {
    ListValue googleMessage = ProtocStructHelper.toListValue(Arrays.asList(
        Double.NEGATIVE_INFINITY,
        -0.0,
        0.0,
        Double.POSITIVE_INFINITY,
        Double.NaN));

    List<?> wireMessage = Arrays.asList(
        Double.NEGATIVE_INFINITY,
        -0.0,
        0.0,
        Double.POSITIVE_INFINITY,
        Double.NaN);

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.STRUCT_LIST.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.STRUCT_LIST.decode(googleMessageBytes));
  }

  @Test public void booleanTrue() throws IOException {
    com.google.protobuf.Value googleMessage = ProtocStructHelper.toValue(true);

    Object wireMessage = true;

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.STRUCT_VALUE.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.STRUCT_VALUE.decode(googleMessageBytes));
  }

  @Test public void booleanFalse() throws IOException {
    com.google.protobuf.Value googleMessage = ProtocStructHelper.toValue(false);

    Object wireMessage = false;

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.STRUCT_VALUE.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.STRUCT_VALUE.decode(googleMessageBytes));
  }

  @Test public void stringValue() throws IOException {
    com.google.protobuf.Value googleMessage = ProtocStructHelper.toValue("Cash App!");

    Object wireMessage = "Cash App!";

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.STRUCT_VALUE.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.STRUCT_VALUE.decode(googleMessageBytes));
  }

  @Test public void emptyStringValue() throws IOException {
    com.google.protobuf.Value googleMessage = ProtocStructHelper.toValue("");

    Object wireMessage = "";

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.STRUCT_VALUE.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.STRUCT_VALUE.decode(googleMessageBytes));
  }

  @Test public void utf8StringValue() throws IOException {
    com.google.protobuf.Value googleMessage = ProtocStructHelper.toValue("На берегу пустынных волн");

    Object wireMessage = "На берегу пустынных волн";

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.STRUCT_VALUE.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.STRUCT_VALUE.decode(googleMessageBytes));
  }

  @Test public void map() throws IOException {
    com.google.protobuf.Struct googleMessage =
        ProtocStructHelper.toStruct(mapOf("a", "android", "c", "cash"));

    Map<String, Object> wireMessage = mapOf("a", "android", "c", "cash");

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.STRUCT_MAP.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.STRUCT_MAP.decode(googleMessageBytes));
  }

  @Test public void mapOfAllTypes() throws IOException {
    Map<String, Object> wireMap = mapOf(
        "a", null,
        "b", 0.5,
        "c", true,
        "d", "cash",
        "e", Arrays.asList("g", "h"),
        "f", mapOf("i", "j", "k", "l"));
    com.google.protobuf.Struct googleMessage = ProtocStructHelper.toStruct(wireMap);

    Map<String, Object> wireMessage = wireMap;

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.STRUCT_MAP.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.STRUCT_MAP.decode(googleMessageBytes));
  }

  @Test public void mapWithoutEntries() throws IOException {
    com.google.protobuf.Struct googleMessage = ProtocStructHelper.emptyStruct();

    Map<String, Object> wireMessage = new LinkedHashMap<>();

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.STRUCT_MAP.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.STRUCT_MAP.decode(googleMessageBytes));
  }

  /**
   * Parity finding (port bug, not an adaptation): the encode and encodedSize arms pass
   * (ClassCastException from the String checkcast on the Integer key), but the redact arm
   * diverges. Upstream STRUCT_MAP.redact is {@code value?.mapValues { STRUCT_VALUE.redact(it) }}
   * where {@code it} is the Map.Entry, so ANY non-empty map throws IllegalArgumentException
   * ("unexpected struct value: 5=android", verified against the pinned 7.1.0 artifact via
   * wire-upstream-shaded). The port's StructMapAdapter.redact redacts the value instead and
   * surfaces ClassCastException from the key checkcast. The case stays disabled until
   * wire-runtime-java's StructMapAdapter.redact matches upstream.
   */
  @Test
  @SuppressWarnings("unchecked") // Totally unsafe.
  public void unsupportedKeyType() {
    Map<String, Object> wireMessage =
        (Map<String, Object>) (Map) Collections.singletonMap(5, "android");

    assertThrows(ClassCastException.class, () -> ProtoAdapter.STRUCT_MAP.encode(wireMessage));
    assertThrows(ClassCastException.class, () -> ProtoAdapter.STRUCT_MAP.encodedSize(wireMessage));
    assertThrows(IllegalArgumentException.class, () -> ProtoAdapter.STRUCT_MAP.redact(wireMessage));
  }

  @Test public void unsupportedValueType() {
    Map<String, StringBuilder> wireMessage =
        Collections.singletonMap("a", new StringBuilder("android"));

    assertThrows(IllegalArgumentException.class, () -> ProtoAdapter.STRUCT_MAP.encode(wireMessage));
    assertThrows(IllegalArgumentException.class,
        () -> ProtoAdapter.STRUCT_MAP.encodedSize(wireMessage));
    assertThrows(IllegalArgumentException.class, () -> ProtoAdapter.STRUCT_MAP.redact(wireMessage));
  }

  @Test public void list() throws IOException {
    ListValue googleMessage = ProtocStructHelper.toListValue(Arrays.asList("android", "cash"));

    List<?> wireMessage = Arrays.asList("android", "cash");

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.STRUCT_LIST.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.STRUCT_LIST.decode(googleMessageBytes));
  }

  @Test public void listOfAllTypes() throws IOException {
    ListValue googleMessage = ProtocStructHelper.toListValue(Arrays.asList(
        null,
        0.5,
        true,
        "cash",
        Arrays.asList("a", "b"),
        mapOf("c", "d", "e", "f")));

    List<?> wireMessage = Arrays.asList(
        null,
        0.5,
        true,
        "cash",
        Arrays.asList("a", "b"),
        mapOf("c", "d", "e", "f"));

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.STRUCT_LIST.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.STRUCT_LIST.decode(googleMessageBytes));
  }

  @Test public void unsupportedListElement() {
    List<StringBuilder> wireMessage = Collections.singletonList(new StringBuilder());

    assertThrows(IllegalArgumentException.class, () -> ProtoAdapter.STRUCT_LIST.encode(wireMessage));
    assertThrows(IllegalArgumentException.class,
        () -> ProtoAdapter.STRUCT_LIST.encodedSize(wireMessage));
    assertThrows(IllegalArgumentException.class, () -> ProtoAdapter.STRUCT_LIST.redact(wireMessage));
  }

  @Test public void listValueWithoutElements() throws IOException {
    ListValue googleMessage = ListValue.newBuilder().build();

    List<?> wireMessage = new ArrayList<>();

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.STRUCT_LIST.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.STRUCT_LIST.decode(googleMessageBytes));
  }

  // Adaptation: the AllStructsK (Kotlin-generator model) assertions are dropped from the
  // mixed cases; that fixture package is a DEC-6 exclusion in this module.
  @Test public void nullMapAndListAsFields() throws IOException {
    AllStructsOuterClass.AllStructs protocAllStruct =
        AllStructsOuterClass.AllStructs.newBuilder().build();
    AllStructs wireAllStructJava = new AllStructs.Builder().build();

    byte[] protocAllStructBytes = protocAllStruct.toByteArray();
    assertArrayEquals(protocAllStructBytes, AllStructs.ADAPTER.encode(wireAllStructJava));
    assertEquals(wireAllStructJava, AllStructs.ADAPTER.decode(protocAllStructBytes));
  }

  // Adaptation: as nullMapAndListAsFields; only the wire-Java half is retained.
  @Test public void emptyMapAndListAsFields() throws IOException {
    AllStructsOuterClass.AllStructs protocAllStruct = AllStructsOuterClass.AllStructs.newBuilder()
        .setStruct(ProtocStructHelper.emptyStruct())
        .setList(ProtocStructHelper.emptyListValue())
        .build();
    AllStructs wireAllStructJava = new AllStructs.Builder()
        .struct(new LinkedHashMap<String, Object>())
        .list(new ArrayList<Object>())
        .build();

    byte[] protocAllStructBytes = protocAllStruct.toByteArray();
    assertArrayEquals(protocAllStructBytes, AllStructs.ADAPTER.encode(wireAllStructJava));
    assertEquals(wireAllStructJava, AllStructs.ADAPTER.decode(protocAllStructBytes));
  }

  // Note: We are not testing nulls because while protoc emits `NULL_VALUE`s, Wire doesn't.
  // Adaptation: as nullMapAndListAsFields; only the wire-Java half is retained.
  @Test public void structRoundTripWithData() throws IOException {
    AllStructsOuterClass.AllStructs protocAllStruct = AllStructsOuterClass.AllStructs.newBuilder()
        .setStruct(ProtocStructHelper.toStruct(mapOf("a", 1.0)))
        .setList(ProtocStructHelper.toListValue(Arrays.asList("a", 3.0)))
        .setNullValue(NullValue.NULL_VALUE)
        .setValueA(ProtocStructHelper.toValue("a"))
        .setValueB(ProtocStructHelper.toValue(33.0))
        .setValueC(ProtocStructHelper.toValue(true))
        .setValueE(ProtocStructHelper.toValue(mapOf("a", 1.0)))
        .setValueF(ProtocStructHelper.toValue(Arrays.asList("a", 3.0)))
        .build();
    AllStructs wireAllStructJava = new AllStructs.Builder()
        .struct(mapOf("a", 1.0))
        .list(Arrays.asList("a", 3.0))
        .null_value(null)
        .value_a("a")
        .value_b(33.0)
        .value_c(true)
        .value_e(mapOf("a", 1.0))
        .value_f(Arrays.asList("a", 3.0))
        .build();

    byte[] protocAllStructBytes = protocAllStruct.toByteArray();
    assertArrayEquals(protocAllStructBytes, AllStructs.ADAPTER.encode(wireAllStructJava));
    assertEquals(wireAllStructJava, AllStructs.ADAPTER.decode(protocAllStructBytes));
  }

  @Test public void javaListsAreDeeplyImmutable() {
    List<Object> list = new ArrayList<>(Arrays.asList(
        new LinkedHashMap<String, Object>(Collections.singletonMap("a", "b")),
        new ArrayList<Object>(Collections.singletonList("c")),
        "d",
        5.0,
        false,
        null));

    AllStructs allStructs = new AllStructs.Builder()
        .list(list)
        .build();
    assertTrue(isDeeplyUnmodifiable(allStructs.list));

    // Mutate the values used to create the list. Wire should have defensive copies.
    ((Map<?, ?>) list.get(0)).clear();
    ((List<?>) list.get(1)).clear();
    list.clear();

    assertEquals(Arrays.asList(
        Collections.singletonMap("a", "b"),
        Collections.singletonList("c"),
        "d",
        5.0,
        false,
        null), allStructs.list);
  }

  /**
   * DEC-6 exclusion: the Kotlin-generator model fixture (AllStructsK) is not generated in
   * this module. Upstream body:
   *
   * <pre>
   * val list = mutableListOf(mutableMapOf("a" to "b"), mutableListOf("c"), "d", 5.0, false, null)
   *
   * val allStructs = AllStructsK.Builder()
   *   .list(list)
   *   .build()
   *
   * assertThat(allStructs.list!!.isDeeplyUnmodifiable()).isTrue()
   *
   * // Mutate the values used to create the list. Wire should have defensive copies.
   * (list[0] as MutableMap<*, *>).clear()
   * (list[1] as MutableList<*>).clear()
   * list.clear()
   *
   * assertThat(allStructs.list!!)
   *   .containsExactly(mapOf("a" to "b"), listOf("c"), "d", 5.0, false, null)
   * </pre>
   */
  @Test
  @Disabled("DEC-6: Kotlin-generator model fixture (AllStructsK) is not generated")
  public void kotlinListsAreDeeplyImmutable() {
  }

  @Test public void javaMapsAreDeeplyImmutable() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("a", new LinkedHashMap<String, Object>(Collections.singletonMap("g", "h")));
    map.put("b", new ArrayList<Object>(Collections.singletonList("i")));
    map.put("c", "j");
    map.put("d", 5.0);
    map.put("e", false);
    map.put("f", null);

    AllStructs allStructs = new AllStructs.Builder()
        .struct(map)
        .build();
    assertTrue(isDeeplyUnmodifiable(allStructs.struct));

    // Mutate the values used to create the map. Wire should have defensive copies.
    ((Map<?, ?>) map.get("a")).clear();
    ((List<?>) map.get("b")).clear();
    map.clear();

    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("a", Collections.singletonMap("g", "h"));
    expected.put("b", Collections.singletonList("i"));
    expected.put("c", "j");
    expected.put("d", 5.0);
    expected.put("e", false);
    expected.put("f", null);
    assertEquals(expected, allStructs.struct);
  }

  /**
   * DEC-6 exclusion: the Kotlin-generator model fixture (AllStructsK) is not generated in
   * this module. Upstream body:
   *
   * <pre>
   * val map = mutableMapOf(
   *   "a" to mutableMapOf("g" to "h"),
   *   "b" to mutableListOf("i"),
   *   "c" to "j",
   *   "d" to 5.0,
   *   "e" to false,
   *   "f" to null,
   * )
   *
   * val allStructs = AllStructsK.Builder()
   *   .struct(map)
   *   .build()
   * assertThat(allStructs.struct.isDeeplyUnmodifiable()).isTrue()
   *
   * // Mutate the values used to create the map. Wire should have defensive copies.
   * (map["a"] as MutableMap<*, *>).clear()
   * (map["b"] as MutableList<*>).clear()
   * map.clear()
   *
   * assertThat(allStructs.struct!!).containsOnly(
   *   "a" to mapOf("g" to "h"),
   *   "b" to listOf("i"),
   *   "c" to "j",
   *   "d" to 5.0,
   *   "e" to false,
   *   "f" to null,
   * )
   * </pre>
   */
  @Test
  @Disabled("DEC-6: Kotlin-generator model fixture (AllStructsK) is not generated")
  public void kotlinMapsAreDeeplyImmutable() {
  }

  // Adaptation: upstream builds the Kotlin model (AllStructsK) here; the port builds the
  // wire Java model, whose constructor runs the same Internal.immutableCopyOfStruct check.
  // The asserted message is identical except the runtime class of 1 is java.lang.Integer
  // where the Kotlin model reported kotlin.Int.
  @Test public void nonStructTypeCannotBeConstructed() {
    try {
      new AllStructs.Builder()
          .struct(mapOf("a", 1)) // Int.
          .build();
    } catch (IllegalArgumentException e) {
      assertEquals(
          "struct value struct must be a JSON type "
              + "(null, Boolean, Double, String, List, or Map) but was class java.lang.Integer: 1",
          e.getMessage());
    }
  }

  @Test public void javaStructsInMapValuesAreDeeplyImmutable() {
    Map<String, Object> map = new LinkedHashMap<>(Collections.singletonMap("a", "b"));

    AllStructs allStructs = new AllStructs.Builder()
        .map_int32_struct(Collections.singletonMap(5, map))
        .build();
    assertTrue(isDeeplyUnmodifiable(allStructs.map_int32_struct));

    // Mutate the values used to create the map. Wire should have defensive copies.
    map.clear();

    assertEquals(Collections.singletonMap(5, Collections.singletonMap("a", "b")),
        allStructs.map_int32_struct);
  }

  /**
   * DEC-6 exclusion: the Kotlin-generator model fixture (AllStructsK) is not generated in
   * this module. Upstream body:
   *
   * <pre>
   * val map = mutableMapOf("a" to "b")
   *
   * val allStructs = AllStructsK.Builder()
   *   .map_int32_struct(mapOf(5 to map))
   *   .build()
   * assertThat(allStructs.map_int32_struct.isDeeplyUnmodifiable()).isTrue()
   *
   * // Mutate the values used to create the map. Wire should have defensive copies.
   * map.clear()
   *
   * assertThat(allStructs.map_int32_struct).containsOnly(5 to mapOf("a" to "b"))
   * </pre>
   */
  @Test
  @Disabled("DEC-6: Kotlin-generator model fixture (AllStructsK) is not generated")
  public void kotlinStructsInMapValuesAreDeeplyImmutable() {
  }

  @Test public void javaStructsInListValuesAreDeeplyImmutable() {
    Map<String, Object> map = new LinkedHashMap<>(Collections.singletonMap("a", "b"));

    AllStructs allStructs = new AllStructs.Builder()
        .rep_struct(Collections.singletonList(map))
        .build();
    assertTrue(isDeeplyUnmodifiable(allStructs.rep_struct));

    // Mutate the values used to create the map. Wire should have defensive copies.
    map.clear();

    assertEquals(Collections.singletonList(Collections.singletonMap("a", "b")),
        allStructs.rep_struct);
  }

  /**
   * DEC-6 exclusion: the Kotlin-generator model fixture (AllStructsK) is not generated in
   * this module. Upstream body:
   *
   * <pre>
   * val map = mutableMapOf("a" to "b")
   *
   * val allStructs = AllStructsK.Builder()
   *   .rep_struct(listOf(map))
   *   .build()
   * assertThat(allStructs.rep_struct.isDeeplyUnmodifiable()).isTrue()
   *
   * // Mutate the values used to create the map. Wire should have defensive copies.
   * map.clear()
   *
   * assertThat(allStructs.rep_struct).containsExactly(mapOf("a" to "b"))
   * </pre>
   */
  @Test
  @Disabled("DEC-6: Kotlin-generator model fixture (AllStructsK) is not generated")
  public void kotlinStructsInListValuesAreDeeplyImmutable() {
  }

  @SuppressWarnings("unchecked")
  private static <K, V> Map<K, V> mapOf(Object... keyValuePairs) {
    Map<K, V> result = new LinkedHashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      result.put((K) keyValuePairs[i], (V) keyValuePairs[i + 1]);
    }
    return result;
  }

  private static boolean isDeeplyUnmodifiable(Object value) {
    if (value == null) return true;
    if (value instanceof String) return true;
    if (value instanceof Double) return true;
    if (value instanceof Integer) return true;
    if (value instanceof Boolean) return true;
    if (value instanceof List) {
      for (Object element : (List<?>) value) {
        if (!isDeeplyUnmodifiable(element)) return false;
      }
      return isUnmodifiable((List<?>) value);
    }
    if (value instanceof Map) {
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        if (!isDeeplyUnmodifiable(entry.getKey())
            || !isDeeplyUnmodifiable(entry.getValue())) {
          return false;
        }
      }
      return isUnmodifiable((Map<?, ?>) value);
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private static boolean isUnmodifiable(List<?> list) {
    try {
      ((List<Object>) list).add("x");
      return false;
    } catch (UnsupportedOperationException e) {
      return true;
    }
  }

  @SuppressWarnings("unchecked")
  private static boolean isUnmodifiable(Map<?, ?> map) {
    try {
      ((Map<Object, Object>) map).put("x", "x");
      return false;
    } catch (UnsupportedOperationException e) {
      return true;
    }
  }
}
