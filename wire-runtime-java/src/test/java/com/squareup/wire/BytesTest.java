/*
 * Copyright (C) 2026 the antiwire authors
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Behavior of the wire-owned bytes value for the JDK-typed consumer API (docs/api-surface.md):
 * equality, immutability (defensive copy in, copy out), the utf8 round trip, and the decode
 * entries delegated to the vendored okio.
 */
public class BytesTest {
  @Test public void ofDefensivelyCopiesInput() {
    byte[] data = { 1, 2, 3 };
    Bytes bytes = Bytes.of(data);
    data[0] = 99;
    assertArrayEquals(new byte[] { 1, 2, 3 }, bytes.toByteArray());
  }

  @Test public void toByteArrayReturnsACopy() {
    Bytes bytes = Bytes.of(new byte[] { 1, 2, 3 });
    bytes.toByteArray()[0] = 99;
    assertArrayEquals(new byte[] { 1, 2, 3 }, bytes.toByteArray());
  }

  @Test public void rangeFactoryCopiesTheRangeOnly() {
    byte[] data = { 1, 2, 3, 4, 5 };
    Bytes bytes = Bytes.of(data, 1, 3);
    assertEquals(3, bytes.size());
    assertArrayEquals(new byte[] { 2, 3, 4 }, bytes.toByteArray());
    data[1] = 99;
    assertArrayEquals(new byte[] { 2, 3, 4 }, bytes.toByteArray());
  }

  @Test public void rangeFactoryRejectsOutOfBoundsRanges() {
    byte[] data = { 1, 2, 3 };
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> Bytes.of(data, -1, 2));
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> Bytes.of(data, 0, 4));
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> Bytes.of(data, 2, 2));
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> Bytes.of(data, 1, -1));
    // Boundary ranges are legal: empty and full.
    assertEquals(0, Bytes.of(data, 2, 0).size());
    assertEquals(3, Bytes.of(data, 0, 3).size());
  }

  @Test public void nullArgumentsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> Bytes.of(null));
    assertThrows(IllegalArgumentException.class, () -> Bytes.of(null, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> Bytes.encodeUtf8(null));
    assertThrows(IllegalArgumentException.class, () -> Bytes.decodeBase64(null));
    assertThrows(IllegalArgumentException.class, () -> Bytes.decodeHex(null));
  }

  @Test public void equalityIsContentBased() {
    assertEquals(Bytes.of(new byte[] { 1, 2, 3 }), Bytes.of(new byte[] { 1, 2, 3 }));
    assertEquals(Bytes.of(new byte[] { 1, 2, 3 }).hashCode(),
        Bytes.of(new byte[] { 1, 2, 3 }).hashCode());
    assertNotEquals(Bytes.of(new byte[] { 1, 2, 3 }), Bytes.of(new byte[] { 1, 2, 4 }));
    assertNotEquals(Bytes.of(new byte[] { 1, 2, 3 }), Bytes.of(new byte[] { 1, 2, 3, 0 }));
    assertNotEquals(Bytes.of(new byte[] { 1, 2, 3 }), Bytes.EMPTY);
    assertEquals(Bytes.EMPTY, Bytes.of(new byte[0]));
  }

  @Test public void utf8RoundTrip() {
    String s = "héllo ☃ wire";
    assertEquals(s, Bytes.encodeUtf8(s).utf8());
    assertEquals(s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
        Bytes.encodeUtf8(s).size());
    assertEquals("", Bytes.encodeUtf8("").utf8());
  }

  @Test public void decodeHexDecodesLowercaseAndUppercase() {
    assertArrayEquals(new byte[] { (byte) 0xca, (byte) 0xfe },
        Bytes.decodeHex("cafe").toByteArray());
    assertArrayEquals(new byte[] { (byte) 0xca, (byte) 0xfe },
        Bytes.decodeHex("CAFE").toByteArray());
    assertEquals(0, Bytes.decodeHex("").size());
    assertThrows(IllegalArgumentException.class, () -> Bytes.decodeHex("abc"));
    assertThrows(IllegalArgumentException.class, () -> Bytes.decodeHex("zz"));
  }

  @Test public void decodeBase64MatchesJdkOracle() {
    byte[] data = { 1, 2, 3, 4, 5, 6, 7 };
    String encoded = Base64.getEncoder().encodeToString(data);
    assertArrayEquals(data, Bytes.decodeBase64(encoded).toByteArray());
    assertEquals(0, Bytes.decodeBase64("").size());
    assertNull(Bytes.decodeBase64("this is not base64!"));
  }

  @Test public void toStringShowsHexPrefix() {
    assertEquals("Bytes[size=0]", Bytes.EMPTY.toString());
    String toString = Bytes.decodeHex("cafebabe").toString();
    assertTrue(toString.contains("hex=cafebabe"), toString);
    // Long payloads truncate instead of printing everything.
    byte[] longPayload = new byte[100];
    for (int i = 0; i < longPayload.length; i++) longPayload[i] = (byte) i;
    String longString = Bytes.of(longPayload).toString();
    assertTrue(longString.contains("size=100"), longString);
    assertTrue(longString.endsWith("…]"), longString);
  }
}
