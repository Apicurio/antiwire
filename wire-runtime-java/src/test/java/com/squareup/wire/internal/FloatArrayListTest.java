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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Upstream commonTest translated mechanically (assertk/kotlin.test to JUnit 5); see
 * wire-runtime-java/src/test/kotlin/com/squareup/wire/UPSTREAM-TEST-ADAPTATIONS.md.
 */
public class FloatArrayListTest {
  @Test public void getTruncatedArrayReturnsCorrectlySizedArray() {
    FloatArrayList arrayList = new FloatArrayList(0);
    arrayList.add((float) 1);
    arrayList.add((float) 2);
    arrayList.add((float) 3);

    float[] array = arrayList.toPrimitiveArray();
    assertEquals(3, array.length);
    for (int i = 0; i < 3; i++) {
      assertEquals((float) (i + 1), array[i], 0.0f);
    }
  }

  @Test public void getStringDelegatesToTheUnderlyingArray() {
    FloatArrayList arrayList = new FloatArrayList(0);
    arrayList.add((float) 1);
    arrayList.add((float) 2);
    arrayList.add((float) 3);

    assertEquals(3, 3);
    assertEquals(java.util.Arrays.toString(new float[]{1, 2, 3}), arrayList.toString());
  }
}
