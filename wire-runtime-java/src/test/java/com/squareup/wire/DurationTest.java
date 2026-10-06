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

import okio.ByteString;
import org.junit.jupiter.api.Test;

/**
 * Upstream commonTest translated (assertk/kotlin.test to JUnit 5); see
 * UPSTREAM-TEST-ADAPTATIONS.md. On the JVM upstream maps Duration to java.time.Duration, so
 * getSeconds/getNano are the platform accessors and durationOfSeconds is ofSeconds.
 */
public class DurationTest {
  @Test public void positiveValues() {
    java.time.Duration wireMessage = java.time.Duration.ofSeconds(1L, 200_000_000L);
    assertEquals(1L, wireMessage.getSeconds());
    assertEquals(200_000_000, wireMessage.getNano());
  }

  @Test public void zero() {
    java.time.Duration wireMessage = java.time.Duration.ofSeconds(0L, 0L);
    assertEquals(0L, wireMessage.getSeconds());
    assertEquals(0, wireMessage.getNano());
  }

  @Test public void negativeNearZero() {
    java.time.Duration wireMessage = java.time.Duration.ofSeconds(0L, -200_000_000L);
    assertEquals(-1L, wireMessage.getSeconds());
    assertEquals(800_000_000, wireMessage.getNano());
  }

  @Test public void negativeValues() {
    java.time.Duration wireMessage = java.time.Duration.ofSeconds(-1L, -200_000_000L);
    assertEquals(-2L, wireMessage.getSeconds());
    assertEquals(800_000_000, wireMessage.getNano());
  }

  @Test public void equality() {
    assertEquals(java.time.Duration.ofSeconds(0L, 0L), java.time.Duration.ofSeconds(0L, 0L));
  }
}
