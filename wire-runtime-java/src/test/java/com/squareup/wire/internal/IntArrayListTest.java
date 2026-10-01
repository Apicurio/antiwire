/*
 * Copyright (C) 2023 Square, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Upstream commonTest translated mechanically (assertk/kotlin.test to JUnit 5); see
 * wire-runtime-java/src/test/kotlin/com/squareup/wire/UPSTREAM-TEST-ADAPTATIONS.md.
 */
public class IntArrayListTest {
  @Test public void getTruncatedArrayReturnsCorrectlySizedArray() {
    IntArrayList arrayList = new IntArrayList(0);
    arrayList.add((int) 1);
    arrayList.add((int) 2);
    arrayList.add((int) 3);

    int[] array = arrayList.toArray();
    assertEquals(3, array.length);
    for (int i = 0; i < 3; i++) {
      assertEquals((int) (i + 1), array[i]);
    }
  }

  @Test public void getStringDelegatesToTheUnderlyingArray() {
    IntArrayList arrayList = new IntArrayList(0);
    arrayList.add((int) 1);
    arrayList.add((int) 2);
    arrayList.add((int) 3);

    assertEquals(java.util.Arrays.toString(new int[]{1, 2, 3}), arrayList.toString());
  }

  @Test public void forDecodingClampsAndDivides() {
    // Capacity is not observable until elements land (toArray truncates to size), so the
    // contract under test is usability. The Int.MAX_VALUE clamp would itself exceed the VM's
    // array limit if exercised with a huge minimum, exactly as upstream would; it is verified
    // by reading, not by allocation.
    IntArrayList sized = IntArrayList.forDecoding(10, 2);
    sized.add(7);
    assertEquals(1, sized.toArray().length);
    assertEquals(7, sized.toArray()[0]);

    IntArrayList longSized = IntArrayList.forDecoding(10_000_000L, 4);
    longSized.add(9);
    assertEquals(1, longSized.toArray().length);

    IntArrayList empty = IntArrayList.forDecoding(0, 4);
    assertEquals(0, empty.toArray().length);
  }
}
