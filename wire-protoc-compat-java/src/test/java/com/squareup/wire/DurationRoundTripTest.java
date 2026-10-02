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

import com.google.protobuf.Duration;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Upstream wire-protoc-compatibility-tests DurationRoundTripTest.kt translated: assertk to
 * JUnit 5; backtick test names to camelCase; the common {@code durationOfSeconds} helper is
 * upstream's JVM actual {@link java.time.Duration#ofSeconds(long, long)} (DurationTest
 * precedent in wire-runtime-java). Expected bytes are untouched. See this module's
 * UPSTREAM-TEST-ADAPTATIONS.md.
 */
public class DurationRoundTripTest {
  @Test public void positiveValues() throws IOException {
    Duration googleMessage = Duration.newBuilder()
        .setSeconds(1L)
        .setNanos(200_000_000)
        .build();

    java.time.Duration wireMessage = java.time.Duration.ofSeconds(1L, 200_000_000L);

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.DURATION.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.DURATION.decode(googleMessageBytes));
    assertEquals(googleMessageBytes.length, ProtoAdapter.DURATION.encodedSize(wireMessage));
  }

  @Test public void zero() throws IOException {
    Duration googleMessage = Duration.newBuilder()
        .setSeconds(0L)
        .setNanos(0)
        .build();

    java.time.Duration wireMessage = java.time.Duration.ofSeconds(0L, 0L);

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.DURATION.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.DURATION.decode(googleMessageBytes));
    assertEquals(googleMessageBytes.length, ProtoAdapter.DURATION.encodedSize(wireMessage));
  }

  @Test public void negativeNearZero() throws IOException {
    Duration googleMessage = Duration.newBuilder()
        .setSeconds(0L)
        .setNanos(-200_000_000)
        .build();

    java.time.Duration wireMessage = java.time.Duration.ofSeconds(0L, -200_000_000L);

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.DURATION.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.DURATION.decode(googleMessageBytes));
    assertEquals(googleMessageBytes.length, ProtoAdapter.DURATION.encodedSize(wireMessage));
  }

  @Test public void negativeValues() throws IOException {
    Duration googleMessage = Duration.newBuilder()
        .setSeconds(-1L)
        .setNanos(-200_000_000)
        .build();

    java.time.Duration wireMessage = java.time.Duration.ofSeconds(-1L, -200_000_000L);

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.DURATION.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.DURATION.decode(googleMessageBytes));
    assertEquals(googleMessageBytes.length, ProtoAdapter.DURATION.encodedSize(wireMessage));
  }
}
