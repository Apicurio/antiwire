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

import org.junit.jupiter.api.Test;

/**
 * Upstream commonTest translated; on the JVM Instant maps to java.time.Instant, so
 * ofEpochSecond is the platform factory and getEpochSecond/getNano the accessors.
 */
public class InstantTest {
  @Test public void positiveValues() {
    java.time.Instant wireMessage = java.time.Instant.ofEpochSecond(1L, 200_000_000L);
    assertEquals(1L, wireMessage.getEpochSecond());
    assertEquals(200_000_000, wireMessage.getNano());
  }

  @Test public void zero() {
    java.time.Instant wireMessage = java.time.Instant.ofEpochSecond(0L, 0L);
    assertEquals(0L, wireMessage.getEpochSecond());
    assertEquals(0, wireMessage.getNano());
  }

  @Test public void equality() {
    assertEquals(java.time.Instant.ofEpochSecond(0L, 0L), java.time.Instant.ofEpochSecond(0L, 0L));
  }

  // Upstream's negative cases pass negative nanos to the common ofEpochSecond, which
  // normalizes; java.time.Instant.ofEpochSecond normalizes the same way (verified:
  // ofEpochSecond(0, -200M) is -1s + 800M nano), so both cases run here unchanged.
  // TASK-14.2: they were previously recorded missing on the false premise that the JVM
  // factory rejects negative nano; the port's own never-compiled boundary lock asserted
  // that premise and failed the first time it ran, so the cases are ported for real.
  @Test public void negativeNearZero() {
    java.time.Instant wireMessage = java.time.Instant.ofEpochSecond(0L, -200_000_000L);
    assertEquals(-1L, wireMessage.getEpochSecond());
    assertEquals(800_000_000, wireMessage.getNano());
  }

  @Test public void negativeValues() {
    java.time.Instant wireMessage = java.time.Instant.ofEpochSecond(-1L, -200_000_000L);
    assertEquals(-2L, wireMessage.getEpochSecond());
    assertEquals(800_000_000, wireMessage.getNano());
  }
}
