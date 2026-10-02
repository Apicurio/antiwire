/*
 * Copyright (C) 2026 Square, Inc.
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

import com.google.protobuf.Any;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Upstream wire-protoc-compatibility-tests FieldMaskRoundTripTest.kt translated: assertk to
 * JUnit 5; backtick test names to camelCase. The binary cases are byte-faithful. The three
 * JSON cases need Wire's moshi and gson adapter factories plus protobuf-java-util
 * (JsonFormat), which are DEC-6 exclusions absent from this module's classpath; their bodies
 * are preserved below verbatim. See this module's UPSTREAM-TEST-ADAPTATIONS.md.
 */
public class FieldMaskRoundTripTest {
  @Test public void binaryRoundTrip() throws IOException {
    com.google.protobuf.FieldMask googleMessage = com.google.protobuf.FieldMask.newBuilder()
        .addPaths("user.display_name")
        .addPaths("photo")
        .build();

    FieldMask wireMessage = new FieldMask(Arrays.asList("user.display_name", "photo"));

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.FIELD_MASK.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.FIELD_MASK.decode(googleMessageBytes));
    assertEquals(googleMessageBytes.length, ProtoAdapter.FIELD_MASK.encodedSize(wireMessage));
  }

  @Test public void emptyBinaryRoundTrip() throws IOException {
    com.google.protobuf.FieldMask googleMessage = com.google.protobuf.FieldMask.newBuilder().build();

    FieldMask wireMessage = new FieldMask();

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, ProtoAdapter.FIELD_MASK.encode(wireMessage));
    assertEquals(wireMessage, ProtoAdapter.FIELD_MASK.decode(googleMessageBytes));
    assertEquals(googleMessageBytes.length, ProtoAdapter.FIELD_MASK.encodedSize(wireMessage));
  }

  /**
   * DEC-6 exclusion: JSON adapters (WireJsonAdapterFactory for moshi, WireTypeAdapterFactory
   * for gson) and protobuf-java-util are not part of the ported surface. Upstream body:
   *
   * <pre>
   * val googleMessage = com.google.protobuf.FieldMask.newBuilder()
   *   .addPaths("user.display_name")
   *   .addPaths("photo")
   *   .addPaths("foo_bar.baz_qux")
   *   .build()
   *
   * val wireMessage = FieldMask(listOf("user.display_name", "photo", "foo_bar.baz_qux"))
   *
   * val googleJson = JsonFormat.printer().print(googleMessage)
   * assertThat(googleJson).isEqualTo("\"user.displayName,photo,fooBar.bazQux\"")
   *
   * assertThat(moshi.adapter(FieldMask::class.java).toJson(wireMessage)).isEqualTo(googleJson)
   * assertThat(gson.toJson(wireMessage, FieldMask::class.java)).isEqualTo(googleJson)
   *
   * val googleParsed = com.google.protobuf.FieldMask.newBuilder()
   *   .apply { JsonFormat.parser().merge(googleJson, this) }
   *   .build()
   * assertThat(googleParsed).isEqualTo(googleMessage)
   * assertThat(moshi.adapter(FieldMask::class.java).fromJson(googleJson)).isEqualTo(wireMessage)
   * assertThat(gson.fromJson(googleJson, FieldMask::class.java)).isEqualTo(wireMessage)
   * </pre>
   */
  @Test
  @Disabled("DEC-6: JSON adapters (moshi/gson) and protobuf-java-util are not ported")
  public void jsonRoundTrip() {
  }

  /**
   * DEC-6 exclusion: JSON adapters, as in {@link #jsonRoundTrip()}. Upstream body:
   *
   * <pre>
   * // These paths violate the protobuf style guide and don't survive the camelCase round trip.
   * // Wire intentionally matches protobuf-java's lossy conversions instead of erroring, so both
   * // the printed JSON and the re-parsed paths must be identical to protobuf-java's.
   * val paths = listOf("fooBar", "_foo", "foo_", "foo__bar", "foo_1bar", "Foo.Bar")
   * val googleMessage = com.google.protobuf.FieldMask.newBuilder().addAllPaths(paths).build()
   * val wireMessage = FieldMask(paths)
   *
   * val googleJson = JsonFormat.printer().print(googleMessage)
   * assertThat(moshi.adapter(FieldMask::class.java).toJson(wireMessage)).isEqualTo(googleJson)
   * assertThat(gson.toJson(wireMessage, FieldMask::class.java)).isEqualTo(googleJson)
   *
   * val googleParsed = com.google.protobuf.FieldMask.newBuilder()
   *   .apply { JsonFormat.parser().merge(googleJson, this) }
   *   .build()
   * val wireParsed = moshi.adapter(FieldMask::class.java).fromJson(googleJson)!!
   * assertThat(wireParsed.paths).isEqualTo(googleParsed.pathsList)
   * assertThat(gson.fromJson(googleJson, FieldMask::class.java)).isEqualTo(wireParsed)
   * </pre>
   */
  @Test
  @Disabled("DEC-6: JSON adapters (moshi/gson) and protobuf-java-util are not ported")
  public void degeneratePathsMatchProtobufJava() {
  }

  @Test public void binaryRoundTripPackedInAny() throws IOException {
    Any googleMessage = Any.pack(
        com.google.protobuf.FieldMask.newBuilder()
            .addPaths("user.display_name")
            .addPaths("photo")
            .build());

    AnyMessage wireMessage = AnyMessage.pack(
        ProtoAdapter.FIELD_MASK,
        new FieldMask(Arrays.asList("user.display_name", "photo")));

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, AnyMessage.ADAPTER.encode(wireMessage));
    assertEquals(wireMessage, AnyMessage.ADAPTER.decode(googleMessageBytes));
  }

  /**
   * DEC-6 exclusion: JSON adapters, as in {@link #jsonRoundTrip()}. Upstream body:
   *
   * <pre>
   * val googleMessage = com.google.protobuf.Any.pack(
   *   com.google.protobuf.FieldMask.newBuilder()
   *     .addPaths("user.display_name")
   *     .addPaths("photo")
   *     .build(),
   * )
   *
   * val wireMessage = AnyMessage.pack(
   *   ProtoAdapter.FIELD_MASK,
   *   FieldMask(listOf("user.display_name", "photo")),
   * )
   *
   * val typeRegistry = JsonFormat.TypeRegistry.newBuilder()
   *   .add(com.google.protobuf.FieldMask.getDescriptor())
   *   .build()
   *
   * val googleJson = JsonFormat.printer().usingTypeRegistry(typeRegistry).print(googleMessage)
   * assertJsonEquals(googleJson, moshi.adapter(AnyMessage::class.java).toJson(wireMessage))
   * assertJsonEquals(googleJson, gson.toJson(wireMessage, AnyMessage::class.java))
   *
   * val googleParsed = com.google.protobuf.Any.newBuilder()
   *   .apply { JsonFormat.parser().usingTypeRegistry(typeRegistry).merge(googleJson, this) }
   *   .build()
   * assertThat(googleParsed).isEqualTo(googleMessage)
   * assertThat(moshi.adapter(AnyMessage::class.java).fromJson(googleJson)).isEqualTo(wireMessage)
   * assertThat(gson.fromJson(googleJson, AnyMessage::class.java)).isEqualTo(wireMessage)
   * </pre>
   */
  @Test
  @Disabled("DEC-6: JSON adapters (moshi/gson) and protobuf-java-util are not ported")
  public void jsonRoundTripPackedInAny() {
  }
}
