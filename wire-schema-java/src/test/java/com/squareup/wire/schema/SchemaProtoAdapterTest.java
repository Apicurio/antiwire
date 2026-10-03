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
package com.squareup.wire.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.squareup.wire.Bytes;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.SchemaBuilder;
import com.squareup.wire.internal.ProtocolException;
import java.io.EOFException; // TASK-13 adaptation: upstream imports okio.EOFException; the port's
// vendored okio subset surfaces the reader's end-of-input as the JDK type.
import java.util.Arrays;
import java.util.Map;
import okio.Buffer;
import okio.ByteString;
import org.junit.jupiter.api.Test;

/**
 * Upstream SchemaProtoAdapterTest translated (assertk to JUnit 5, buildSchema to SchemaBuilder,
 * upstream's default {@code includeUnknown = false} parameter spelled out at each call site).
 * Upstream's okio.ProtocolException is the port's
 * {@link com.squareup.wire.internal.ProtocolException}.
 */
public class SchemaProtoAdapterTest {
  private final Schema coffeeSchema = new SchemaBuilder()
      .add("coffee.proto", ""
          + "message CafeDrink {\n"
          + "  optional string customer_name = 1;\n"
          + "  repeated EspressoShot shots = 2;\n"
          + "  optional Foam foam = 3;\n"
          + "  optional int32 size_ounces = 14;\n"
          + "  optional Dairy dairy = 15;\n"
          + "\n"
          + "  enum Foam {\n"
          + "    NOT_FOAMY_AND_QUITE_BORING = 1;\n"
          + "    ZOMG_SO_FOAMY = 3;\n"
          + "  }\n"
          + "}\n"
          + "\n"
          + "message Dairy {\n"
          + "  optional int32 count = 2;\n"
          + "  optional string type = 1;\n"
          + "}\n"
          + "\n"
          + "message EspressoShot {\n"
          + "  optional string bean_type = 1;\n"
          + "  optional double caffeine_level = 2;\n"
          + "}\n")
      .build();

  // Golden data emitted by protoc using the schema above.
  private final Map<String, Object> dansCoffee = map(
      "customer_name", "Dan",
      "shots", Arrays.asList(map("caffeine_level", 0.5)),
      "size_ounces", 16,
      "dairy", map("count", 1));

  private final ByteString dansCoffeeEncoded =
      ByteString.decodeHex("0a0344616e120911000000000000e03f70107a021001");

  private final Map<String, Object> jessesCoffee = map(
      "customer_name", "Jesse",
      "shots", Arrays.asList(
          map("bean_type", "colombian", "caffeine_level", 1.0),
          map("bean_type", "colombian", "caffeine_level", 1.0)),
      "foam", "ZOMG_SO_FOAMY",
      "size_ounces", 24);

  private final ByteString jessesCoffeeEncoded = ByteString.decodeHex(
      "0a054a6573736512140a09636f6c"
          + "6f6d6269616e11000000000000f03f12140a09636f6c6f6d6269616e11000000000000f03f18037018");

  @Test public void decode() throws Exception {
    ProtoAdapter<Object> adapter = coffeeSchema.protoAdapter("CafeDrink", true);
    assertEquals(dansCoffee, adapter.decode(new Buffer().write(dansCoffeeEncoded)));
    assertEquals(jessesCoffee, adapter.decode(new Buffer().write(jessesCoffeeEncoded)));
  }

  @Test public void encode() throws Exception {
    ProtoAdapter<Object> adapter = coffeeSchema.protoAdapter("CafeDrink", true);
    assertEquals(dansCoffeeEncoded, adapter.encodeByteString(dansCoffee));
    assertEquals(jessesCoffeeEncoded, adapter.encodeByteString(jessesCoffee));
    assertEquals(dansCoffeeEncoded.size(), adapter.encodedSize(dansCoffee));
    assertEquals(jessesCoffeeEncoded.size(), adapter.encodedSize(jessesCoffee));
  }

  @Test public void groupsIgnored() throws Exception {
    ProtoAdapter<Object> adapter = new SchemaBuilder()
        .add("message.proto", ""
            + "message Message {\n"
            + "  optional string a = 1;\n"
            + "  // repeated group Group1 = 2 {\n"
            + "  //   optional SomeMessage a = 11;\n"
            + "  // }\n"
            + "  // repeated group Group2 = 3 {\n"
            + "  //   optional SomeMessage b = 21;\n"
            + "  // }\n"
            + "  optional string b = 4;\n"
            + "}\n")
        .build()
        // TASK-13 adaptation: upstream spells the default includeUnknown = false implicitly.
        .protoAdapter("Message", false);
    ByteString encoded = ByteString.decodeHex(
        "0a0161135a02080114135a02100214135a090803720568656c6c"
            + "6f141baa010208011c1baa010210021c1baa01090803720568656c6c6f1c220162");
    Map<String, Object> expected = map("a", "a", "b", "b");
    assertEquals(expected, adapter.decode(new Buffer().write(encoded)));
  }

  @Test public void startGroupWithoutEndGroup() throws Exception {
    ProtoAdapter<Object> adapter = new SchemaBuilder()
        .add("message.proto", ""
            + "message Message {\n"
            + "  optional string a = 1;\n"
            + "}\n")
        .build()
        .protoAdapter("Message", false);
    ByteString encoded = ByteString.decodeHex("130a0161");
    assertThrows(EOFException.class, () -> adapter.decode(new Buffer().write(encoded)));
  }

  @Test public void unexpectedEndGroup() throws Exception {
    ProtoAdapter<Object> adapter = new SchemaBuilder()
        .add("message.proto", ""
            + "message Message {\n"
            + "  optional string a = 1;\n"
            + "}\n")
        .build()
        .protoAdapter("Message", false);
    ByteString encoded = ByteString.decodeHex("0a01611c");
    ProtocolException expected = assertThrows(ProtocolException.class,
        () -> adapter.decode(new Buffer().write(encoded)));
    assertEquals("Unexpected end group. Reader position: 4. Last read tag: 3.",
        expected.getMessage());
  }

  @Test public void endGroupDoesntMatchStartGroup() throws Exception {
    ProtoAdapter<Object> adapter = new SchemaBuilder()
        .add("message.proto", ""
            + "message Message {\n"
            + "  optional string a = 1;\n"
            + "}\n")
        .build()
        .protoAdapter("Message", false);
    ByteString encoded = ByteString.decodeHex("130a01611c");
    ProtocolException expected = assertThrows(ProtocolException.class,
        () -> adapter.decode(new Buffer().write(encoded)));
    assertEquals("Unexpected end group. Reader position: 5. Last read tag: 3.",
        expected.getMessage());
  }

  @Test public void decodeToUnpacked() throws Exception {
    ProtoAdapter<Object> adapter = new SchemaBuilder()
        .add("message.proto", ""
            + "message Message {\n"
            + "  repeated int32 a = 90 [packed = false];\n"
            + "}\n")
        .build()
        .protoAdapter("Message", false);
    Map<String, Object> expected = map("a", Arrays.asList(601, 701));

    ByteString packedEncoded = ByteString.decodeHex("d20504d904bd05");
    assertEquals(expected, adapter.decode(new Buffer().write(packedEncoded)));

    ByteString unpackedEncoded = ByteString.decodeHex("d005d904d005bd05");
    assertEquals(expected, adapter.decode(new Buffer().write(unpackedEncoded)));
  }

  @Test public void decodeToPacked() throws Exception {
    ProtoAdapter<Object> adapter = new SchemaBuilder()
        .add("message.proto", ""
            + "message Message {\n"
            + "  repeated int32 a = 90 [packed = true];\n"
            + "}\n")
        .build()
        .protoAdapter("Message", false);
    Map<String, Object> expected = map("a", Arrays.asList(601, 701));

    ByteString unpackedEncoded = ByteString.decodeHex("d005d904d005bd05");
    assertEquals(expected, adapter.decode(new Buffer().write(unpackedEncoded)));

    ByteString packedEncoded = ByteString.decodeHex("d20504d904bd05");
    assertEquals(expected, adapter.decode(new Buffer().write(packedEncoded)));
  }

  @Test public void recursiveMessage() throws Exception {
    ProtoAdapter<Object> adapter = new SchemaBuilder()
        .add("tree.proto", ""
            + "message BinaryTreeNode {\n"
            + "  optional BinaryTreeNode left = 1;\n"
            + "  optional BinaryTreeNode right = 2;\n"
            + "  optional string value = 3;\n"
            + "}\n")
        .build()
        .protoAdapter("BinaryTreeNode", false);
    Map<String, Object> value = map(
        "value", "D",
        "left", map(
            "value", "B",
            "left", map("value", "A"),
            "right", map("value", "C")),
        "right", map(
            "value", "F",
            "left", map("value", "E"),
            "right", map("value", "G")));
    ByteString encoded = ByteString.decodeHex(
        "0a0d0a031a014112031a01431a0142120d0a031a014512031a01471a01461a0144");
    assertEquals(encoded, adapter.encodeByteString(value));
    assertEquals(value, adapter.decode(new Buffer().write(encoded)));
  }

  @Test public void includeUnknowns() throws Exception {
    Schema schema = new SchemaBuilder()
        .add("coffee.proto", ""
            + "message CafeDrink {\n"
            + "  optional string customer_name = 1;\n"
            + "  optional int32 size_ounces = 14;\n"
            + "}\n")
        .build();

    // Phase 2: unknown length-delimited values decode as the wire-owned Bytes
    // (docs/api-surface.md), where upstream's dynamic model would carry okio's ByteString.
    Map<String, Object> dansCoffeeWithUnknowns = map(
        "customer_name", "Dan",
        "2", Arrays.asList(Bytes.decodeHex("11000000000000e03f")),
        "size_ounces", 16,
        "15", Arrays.asList(Bytes.decodeHex("1001")));

    ProtoAdapter<Object> adapter = schema.protoAdapter("CafeDrink", true);
    assertEquals(dansCoffeeWithUnknowns, adapter.decode(new Buffer().write(dansCoffeeEncoded)));
  }

  @Test public void omitUnknowns() throws Exception {
    Schema schema = new SchemaBuilder()
        .add("coffee.proto", ""
            + "message CafeDrink {\n"
            + "  optional string customer_name = 1;\n"
            + "  optional int32 size_ounces = 14;\n"
            + "}\n")
        .build();

    Map<String, Object> dansCoffeeWithoutUnknowns =
        map("customer_name", "Dan", "size_ounces", 16);

    ProtoAdapter<Object> adapter = schema.protoAdapter("CafeDrink", false);
    assertEquals(dansCoffeeWithoutUnknowns, adapter.decode(new Buffer().write(dansCoffeeEncoded)));
  }

  /** Kotlin mapOf: alternating key/value arguments, insertion-ordered. */
  private static Map<String, Object> map(Object... keysAndValues) {
    return com.squareup.wire.testing.TestFiles.map(keysAndValues);
  }
}
