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

import com.google.protobuf.Empty;
import java.io.IOException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Upstream wire-protoc-compatibility-tests EmptyRoundTripTest.kt translated: assertk to
 * JUnit 5. The wire-side model of google.protobuf.Empty is {@code Unit} upstream and
 * {@link Void} in the port, so upstream's {@code val wireMessage = Unit} becomes
 * {@code null} (the port's EmptyAdapter is a ProtoAdapter<Void>); the protoc-side bytes are
 * untouched. See this module's UPSTREAM-TEST-ADAPTATIONS.md.
 */
@SuppressWarnings("deprecation") // The Void-typed EMPTY is the protoc-oracle subject here.
public class EmptyRoundTripTest {
  @Test public void empty() throws IOException {
    Empty googleMessage = Empty.newBuilder().build();

    Void wireMessage = null;

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.EMPTY.encode(wireMessage));
    assertNull(ProtoAdapter.EMPTY.decode(googleMessageBytes));
    assertEquals(googleMessageBytes.length, ProtoAdapter.EMPTY.encodedSize(wireMessage));
  }

  /**
   * Generated-code Empty divergence (compatibility-matrix section E, owner TASK-16 under
   * DEC-6): the pinned upstream generator models google.protobuf.Empty as kotlin.Unit, which
   * this module refuses in compile scope, so the wire-side AllEmpty model is not generated and
   * the matching protoc-side reference class was excluded with it (one proto, both sides). The
   * dynamic-model Empty question is separate and resolved (TASK-26, UnitValue); it does not
   * land this fixture. The upstream body is preserved below verbatim for the generated-code
   * resolution.
   */
  @Test
  @Disabled("Generated-code Empty divergence (wire-side AllEmpty model not generated; TASK-16/DEC-6)")
  public void allEmpty() {
    /*
    val googleMessage = AllEmptyOuterClass.AllEmpty.newBuilder()
      .setEmpty(Empty.newBuilder().build())
      .addRepEmpty(Empty.newBuilder().build())
      .addRepEmpty(Empty.newBuilder().build())
      .putMapInt32Empty(1, Empty.newBuilder().build())
      .setOneofEmpty(Empty.newBuilder().build())
      .build()

    val wireMessageJava = AllEmptyJ.Builder()
      .empty(Unit)
      .rep_empty(listOf(Unit, Unit))
      .map_int32_empty(mapOf(1 to Unit))
      .oneof_empty(Unit)
      .build()

    val wireMessageKotlin = AllEmptyK(
      empty = Unit,
      rep_empty = listOf(Unit, Unit),
      map_int32_empty = mapOf(1 to Unit),
      oneof_empty = Unit,
    )

    val googleMessageBytes = googleMessage.toByteArray()
    val wireMessageJavaBytes = AllEmptyJ.ADAPTER.encode(wireMessageJava)
    assertThat(AllEmptyOuterClass.AllEmpty.parseFrom(wireMessageJavaBytes)).isEqualTo(googleMessage)
    assertThat(AllEmptyJ.ADAPTER.decode(wireMessageJavaBytes)).isEqualTo(wireMessageJava)
    assertThat(AllEmptyJ.ADAPTER.decode(googleMessageBytes)).isEqualTo(wireMessageJava)
    assertThat(AllEmptyJ.ADAPTER.encodedSize(wireMessageJava)).isEqualTo(googleMessageBytes.size)
    val wireMessageKotlinBytes = AllEmptyK.ADAPTER.encode(wireMessageKotlin)
    assertThat(AllEmptyOuterClass.AllEmpty.parseFrom(wireMessageKotlinBytes)).isEqualTo(googleMessage)
    assertThat(AllEmptyK.ADAPTER.decode(wireMessageKotlinBytes)).isEqualTo(wireMessageKotlin)
    assertThat(AllEmptyK.ADAPTER.decode(googleMessageBytes)).isEqualTo(wireMessageKotlin)
    assertThat(AllEmptyK.ADAPTER.encodedSize(wireMessageKotlin)).isEqualTo(googleMessageBytes.size)
    */
  }
}
