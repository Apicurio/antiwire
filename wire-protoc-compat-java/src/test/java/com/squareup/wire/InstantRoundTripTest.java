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

import com.google.protobuf.Timestamp;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Upstream wire-protoc-compatibility-tests InstantRoundTripTest.kt translated: assertk to
 * JUnit 5; backtick test names to camelCase; the common {@code ofEpochSecond} helper is
 * upstream's JVM actual {@link java.time.Instant#ofEpochSecond(long, long)} (DurationTest
 * precedent in wire-runtime-java). Expected bytes are untouched. See this module's
 * UPSTREAM-TEST-ADAPTATIONS.md.
 */
public class InstantRoundTripTest {
  @Test public void positiveValues() throws IOException {
    Timestamp googleMessage = Timestamp.newBuilder()
        .setSeconds(1L)
        .setNanos(200_000_000)
        .build();

    java.time.Instant wireMessage = java.time.Instant.ofEpochSecond(1L, 200_000_000L);

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.INSTANT.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.INSTANT.decode(googleMessageBytes));
    assertEquals(googleMessageBytes.length, ProtoAdapter.INSTANT.encodedSize(wireMessage));
  }

  @Test public void zero() throws IOException {
    Timestamp googleMessage = Timestamp.newBuilder()
        .setSeconds(0L)
        .setNanos(0)
        .build();

    java.time.Instant wireMessage = java.time.Instant.ofEpochSecond(0L, 0L);

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.INSTANT.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.INSTANT.decode(googleMessageBytes));
    assertEquals(googleMessageBytes.length, ProtoAdapter.INSTANT.encodedSize(wireMessage));
  }

  @Test public void negativeNearZero() throws IOException {
    Timestamp googleMessage = Timestamp.newBuilder()
        .setSeconds(-1L)
        .setNanos(800_000_000)
        .build();

    java.time.Instant wireMessage = java.time.Instant.ofEpochSecond(0L, -200_000_000L);

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.INSTANT.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.INSTANT.decode(googleMessageBytes));
    assertEquals(googleMessageBytes.length, ProtoAdapter.INSTANT.encodedSize(wireMessage));
  }

  @Test public void negativeValues() throws IOException {
    Timestamp googleMessage = Timestamp.newBuilder()
        .setSeconds(-2L)
        .setNanos(800_000_000)
        .build();

    java.time.Instant wireMessage = java.time.Instant.ofEpochSecond(-1L, -200_000_000L);

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.INSTANT.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.INSTANT.decode(googleMessageBytes));
    assertEquals(googleMessageBytes.length, ProtoAdapter.INSTANT.encodedSize(wireMessage));
  }

  @Test public void decodeProtoWithNanosTooHigh() throws IOException {
    Timestamp googleMessage = Timestamp.newBuilder()
        .setSeconds(2L)
        .setNanos(2_000_000_000)
        .build();

    java.time.Instant wireMessage = java.time.Instant.ofEpochSecond(4L, 0L);

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertEquals(wireMessage, ProtoAdapter.INSTANT.decode(googleMessageBytes));
  }

  @Test public void decodeProtoWithNanosTooLow() throws IOException {
    Timestamp googleMessage = Timestamp.newBuilder()
        .setSeconds(2L)
        .setNanos(-1)
        .build();

    java.time.Instant wireMessage = java.time.Instant.ofEpochSecond(1L, 999_999_999L);

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertEquals(wireMessage, ProtoAdapter.INSTANT.decode(googleMessageBytes));
  }
}
