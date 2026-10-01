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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Upstream commonTest translated (assertk to JUnit 5; ofEpochSecond is the JVM factory); see
 * UPSTREAM-TEST-ADAPTATIONS.md.
 */
public class ProtoAdapterTest {
  @Test public void repeatedRepeatedProtoAdapterForbidden() {
    assertThrows(UnsupportedOperationException.class,
        () -> ProtoAdapter.BOOL.asRepeated().asRepeated());
  }

  @Test public void packedPackedProtoAdapterForbidden() {
    // Unable to pack a length-delimited type.
    assertThrows(IllegalArgumentException.class,
        () -> ProtoAdapter.BOOL.asPacked().asPacked());
  }

  @Test public void repeatedPackedProtoAdapterForbidden() {
    assertThrows(UnsupportedOperationException.class,
        () -> ProtoAdapter.BOOL.asRepeated().asPacked());
  }

  @Test public void packedRepeatedProtoAdapterForbidden() {
    assertThrows(UnsupportedOperationException.class,
        () -> ProtoAdapter.BOOL.asPacked().asRepeated());
  }

  @Test public void instantEncodeValidMinBoundary() throws java.io.IOException {
    // 0001-01-01T00:00:00Z.
    java.time.Instant instant = java.time.Instant.ofEpochSecond(-62135596800L, 0L);
    byte[] bytes = ProtoAdapter.INSTANT.encode(instant);
    assertEquals(-62135596800L, ProtoAdapter.INSTANT.decode(bytes).getEpochSecond());
  }

  @Test public void instantEncodeValidMaxBoundary() throws java.io.IOException {
    // 9999-12-31T23:59:59Z.
    java.time.Instant instant = java.time.Instant.ofEpochSecond(253402300799L, 999_999_999L);
    byte[] bytes = ProtoAdapter.INSTANT.encode(instant);
    java.time.Instant decoded = ProtoAdapter.INSTANT.decode(bytes);
    assertEquals(253402300799L, decoded.getEpochSecond());
    assertEquals(999_999_999, decoded.getNano());
  }

  @Test public void instantEncodeRejectsSecondsBelowMin() {
    // 0001-01-01T00:00:00Z - 1 second.
    java.time.Instant instant = java.time.Instant.ofEpochSecond(-62135596801L, 0L);
    assertThrows(IllegalArgumentException.class, () -> ProtoAdapter.INSTANT.encode(instant));
  }

  @Test public void instantEncodeRejectsSecondsAboveMax() {
    // 9999-12-31T23:59:59Z + 1 second.
    java.time.Instant instant = java.time.Instant.ofEpochSecond(253402300800L, 0L);
    assertThrows(IllegalArgumentException.class, () -> ProtoAdapter.INSTANT.encode(instant));
  }

  @Test public void instantEncodedSizeRejectsOutOfRange() {
    // 0001-01-01T00:00:00Z - 1 second.
    java.time.Instant instant = java.time.Instant.ofEpochSecond(-62135596801L, 0L);
    assertThrows(IllegalArgumentException.class,
        () -> ProtoAdapter.INSTANT.encodedSize(instant));
  }
}
