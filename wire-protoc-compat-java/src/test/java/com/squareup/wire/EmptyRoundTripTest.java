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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.Empty;
import com.squareup.wire.testing.GeneratedMessages;
import com.squareup.wire.testing.TestCompilers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import squareup.proto3.java.alltypes.AllEmptyOuterClass;

/**
 * Upstream wire-protoc-compatibility-tests EmptyRoundTripTest.kt translated: assertk to
 * JUnit 5. The wire-side model of google.protobuf.Empty is {@code Unit} upstream and
 * {@link ProtoAdapter.UnitValue} in the port (TASK-26 dynamic model, TASK-16.1 generated
 * code), so upstream's {@code val wireMessage = Unit} becomes the singleton; the
 * Void-typed deprecated {@code ProtoAdapter.EMPTY} stays the protoc-oracle subject of the
 * first case. See this module's UPSTREAM-TEST-ADAPTATIONS.md.
 */
@SuppressWarnings("deprecation") // The Void-typed EMPTY is the protoc-oracle subject here.
public class EmptyRoundTripTest {
  @TempDir static Path tempDir;

  /**
   * The generated model, shared by the two cases that need it: generation plus compilation
   * costs seconds, and both cases exercise the same class. Cached across the per-case test
   * instances; the class stays usable after the class-level temp directory is cleaned because
   * loading already happened.
   */
  private static Class<?> generatedAllEmpty;

  @Test public void empty() throws IOException {
    Empty googleMessage = Empty.newBuilder().build();

    Void wireMessage = null;

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.EMPTY.encode(wireMessage));
    assertNull(ProtoAdapter.EMPTY.decode(googleMessageBytes));
    assertEquals(googleMessageBytes.length, ProtoAdapter.EMPTY.encodedSize(wireMessage));
  }

  /**
   * The generated-model case, revived by TASK-16.1. Upstream builds its wire-side AllEmptyJ
   * fixture with the pinned upstream generator, which models Empty as kotlin.Unit; this port
   * generates the same model with its own generator at test time (the bounded Empty mapping,
   * docs/api-surface.md), compiles it with javac --release 11 against the port runtime, and
   * runs upstream's assertions against the protoc oracle unchanged: upstream's
   * {@code Unit} becomes {@link ProtoAdapter.UnitValue#INSTANCE}. Upstream's Kotlin-model half
   * (AllEmptyK) is the only part that stays excluded: DEC-6 excludes the Kotlin generator
   * product, not Java messages containing Empty.
   */
  @Test public void allEmpty() throws Exception {
    Class<?> allEmpty = generateCompileLoadAllEmpty();
    ProtoAdapter<Object> adapter = GeneratedMessages.adapterOf(allEmpty);

    AllEmptyOuterClass.AllEmpty googleMessage = AllEmptyOuterClass.AllEmpty.newBuilder()
        .setEmpty(Empty.newBuilder().build())
        .addRepEmpty(Empty.newBuilder().build())
        .addRepEmpty(Empty.newBuilder().build())
        .putMapInt32Empty(1, Empty.newBuilder().build())
        .setOneofEmpty(Empty.newBuilder().build())
        .build();

    Object wireMessage = newAllEmpty(allEmpty, true);

    byte[] googleMessageBytes = googleMessage.toByteArray();
    byte[] wireMessageBytes = adapter.encode(wireMessage);
    assertEquals(googleMessage, AllEmptyOuterClass.AllEmpty.parseFrom(wireMessageBytes));
    assertEquals(wireMessage, adapter.decode(wireMessageBytes));
    assertEquals(wireMessage, adapter.decode(googleMessageBytes));
    assertEquals(googleMessageBytes.length, adapter.encodedSize(wireMessage));
  }

  /**
   * Port-added presence coverage on the same generated model (TASK-16.1 acceptance): an absent
   * Empty field encodes nothing and decodes back to null, and duplicate singular occurrences on
   * the wire merge into one present field, matching the protoc oracle's parse of both forms.
   */
  @Test public void allEmptyPresenceAndDuplicateOccurrences() throws Exception {
    Class<?> allEmpty = generateCompileLoadAllEmpty();
    ProtoAdapter<Object> adapter = GeneratedMessages.adapterOf(allEmpty);

    AllEmptyOuterClass.AllEmpty absentGoogle = AllEmptyOuterClass.AllEmpty.newBuilder().build();
    byte[] absentBytes = absentGoogle.toByteArray();
    assertEquals(0, absentBytes.length);
    Object absentWire = newAllEmpty(allEmpty, false);
    assertArrayEquals(absentBytes, adapter.encode(absentWire));
    assertNull(GeneratedMessages.field(allEmpty, adapter.decode(absentBytes), "empty"));

    // A single present field, from the oracle itself, then the same occurrence twice.
    byte[] single = AllEmptyOuterClass.AllEmpty.newBuilder()
        .setEmpty(Empty.newBuilder().build())
        .build()
        .toByteArray();
    byte[] duplicated = Arrays.copyOf(single, single.length * 2);
    System.arraycopy(single, 0, duplicated, single.length, single.length);

    Object decoded = adapter.decode(duplicated);
    assertEquals(ProtoAdapter.UnitValue.INSTANCE, GeneratedMessages.field(allEmpty, decoded, "empty"));
    assertEquals(AllEmptyOuterClass.AllEmpty.parseFrom(duplicated),
        AllEmptyOuterClass.AllEmpty.parseFrom(adapter.encode(decoded)));
    assertArrayEquals(single, adapter.encode(decoded));
  }

  /**
   * Generates squareup.proto3.java.alltypes.AllEmpty from the pinned clone's all_empty.proto
   * with the port CLI, compiles it under javac --release 11 with no Kotlin on the classpath,
   * and loads it; the first caller pays, later callers reuse the cached class.
   */
  private static synchronized Class<?> generateCompileLoadAllEmpty() throws Exception {
    if (generatedAllEmpty != null) return generatedAllEmpty;
    Path out = tempDir.resolve("java_out");
    WireCompiler.forArgs(
        okio.FileSystem.SYSTEM,
        WireLogger.NONE,
        "--proto_path=" + SchemaEncoderInteropTest.PROTO_ROOT,
        "--java_out=" + out,
        "squareup/proto3/java/alltypes/all_empty.proto")
        .compile();
    Path sourceFile = out.resolve("squareup/proto3/java/alltypes/AllEmpty.java");
    assertTrue(Files.isRegularFile(sourceFile), "the CLI did not emit " + sourceFile);
    generatedAllEmpty = TestCompilers.compileRelease11(
        sourceFile, "squareup.proto3.java.alltypes.AllEmpty",
        Files.createDirectories(tempDir.resolve("classes")), EmptyRoundTripTest.class);
    return generatedAllEmpty;
  }

  /** Builds the wire-side model, mirroring upstream's builder chain with UnitValue for Unit. */
  private static Object newAllEmpty(Class<?> messageClass, boolean present) throws Exception {
    Class<?> builderClass = Class.forName(
        "squareup.proto3.java.alltypes.AllEmpty$Builder", true, messageClass.getClassLoader());
    Object builder = builderClass.getDeclaredConstructor().newInstance();
    if (present) {
      Map<Integer, ProtoAdapter.UnitValue> map = new LinkedHashMap<>();
      map.put(1, ProtoAdapter.UnitValue.INSTANCE);
      GeneratedMessages.invoke(builder, "empty", ProtoAdapter.UnitValue.INSTANCE);
      GeneratedMessages.invoke(builder, "rep_empty", Arrays.asList(
          ProtoAdapter.UnitValue.INSTANCE, ProtoAdapter.UnitValue.INSTANCE));
      GeneratedMessages.invoke(builder, "map_int32_empty", map);
      GeneratedMessages.invoke(builder, "oneof_empty", ProtoAdapter.UnitValue.INSTANCE);
    }
    return builderClass.getMethod("build").invoke(builder);
  }

}
