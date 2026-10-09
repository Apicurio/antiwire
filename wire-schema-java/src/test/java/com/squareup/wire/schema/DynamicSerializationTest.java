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

import com.squareup.wire.Bytes;
import com.squareup.wire.FieldMask;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.SchemaBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import okio.ByteString;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Upstream DynamicSerializationTest translated (assertk to JUnit 5, buildSchema to SchemaBuilder).
 * Upstream's {@code durationOfSeconds} / {@code ofEpochSecond} helpers construct the Kotlin
 * datetime values of upstream's well-known adapters; the port's adapters use the JDK types, so
 * those constructors are {@link Duration#ofSeconds} / {@link Instant#ofEpochSecond}.
 */
public class DynamicSerializationTest {
  @Test public void proto3TypesTest() throws Exception {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto3\";\n"
            + "import \"google/protobuf/duration.proto\";\n"
            + "import \"google/protobuf/timestamp.proto\";\n"
            + "import \"google/protobuf/empty.proto\";\n"
            + "import \"google/protobuf/field_mask.proto\";\n"
            + "import \"google/protobuf/struct.proto\";\n"
            + "import \"google/protobuf/wrappers.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  google.protobuf.Duration duration_field = 1;\n"
            + "  google.protobuf.Timestamp timestamp_field = 2;\n"
            + "  google.protobuf.Empty empty_field = 3;\n"
            + "  google.protobuf.Struct struct_field = 4;\n"
            + "  google.protobuf.Value value_field = 5;\n"
            + "  google.protobuf.ListValue list_value_field = 6;\n"
            + "  google.protobuf.NullValue null_value_field = 7;\n"
            + "  google.protobuf.DoubleValue double_value_field = 8;\n"
            + "  google.protobuf.FloatValue float_value_field = 9;\n"
            + "  google.protobuf.Int64Value int64_value_field = 10;\n"
            + "  google.protobuf.UInt64Value uInt64_value_field = 11;\n"
            + "  google.protobuf.Int32Value int32_value_field = 12;\n"
            + "  google.protobuf.UInt32Value uInt32_value_field = 13;\n"
            + "  google.protobuf.BoolValue bool_value_field = 14;\n"
            + "  google.protobuf.StringValue string_value_field = 15;\n"
            + "  google.protobuf.BytesValue bytes_value_field = 16;\n"
            + "  google.protobuf.FieldMask field_mask_field = 17;\n"
            + "}\n")
        .build();

    ProtoAdapter<Object> adapter = schema.protoAdapter("Message", true);
    // Upstream expects "empty_field" to Kotlin's Unit; the port's dynamic model maps it to the
    // UnitValue singleton (TASK-26, docs/api-surface.md).
    Map<String, Object> expected = map(
        "duration_field", Duration.ofSeconds(60L * 60 * 48, 0L), // 2 days.
        "timestamp_field", Instant.ofEpochSecond(123131234L, 23432423L),
        "empty_field", ProtoAdapter.UnitValue.INSTANCE,
        "struct_field", map("one", 1.0, "two", 2.0),
        "value_field", "Can be Anything",
        "list_value_field", Arrays.asList(Arrays.asList(1.0, 2.0, 3.0), 5.0, false, "boom"),
        "double_value_field", 33.0,
        "float_value_field", 33f,
        "int64_value_field", 33L,
        "uInt64_value_field", 33L,
        "int32_value_field", 33,
        "uInt32_value_field", 33,
        "bool_value_field", true,
        "string_value_field", "πάμε",
        "bytes_value_field", Bytes.encodeUtf8("πάμε"), // Phase 2: Bytes value type (docs/api-surface.md).
        "field_mask_field", new FieldMask(Arrays.asList("user.display_name", "photo")));
    assertEquals(expected, adapter.decode(adapter.encode(expected)));
  }

  @Test public void singularFieldMaskOccurrencesAreMerged() throws Exception {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto3\";\n"
            + "import \"google/protobuf/field_mask.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  google.protobuf.FieldMask field_mask_field = 1;\n"
            + "}\n")
        .build();

    ProtoAdapter<Object> adapter = schema.protoAdapter("Message", true);
    ByteString encoded = ByteString.decodeHex("0a030a01610a030a0162");

    assertEquals(map("field_mask_field", new FieldMask(Arrays.asList("a", "b"))),
        adapter.decode(encoded));
  }

  @Test public void singularFieldMaskOccurrencesInSameOneOfMemberAreMerged() throws Exception {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto3\";\n"
            + "import \"google/protobuf/field_mask.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  oneof choice {\n"
            + "    google.protobuf.FieldMask field_mask_field = 1;\n"
            + "    string name = 2;\n"
            + "  }\n"
            + "}\n")
        .build();

    ProtoAdapter<Object> adapter = schema.protoAdapter("Message", true);

    assertEquals(map("field_mask_field", new FieldMask(Arrays.asList("a", "b"))),
        adapter.decode(ByteString.decodeHex("0a030a01610a030a0162")));
    assertEquals(map("field_mask_field", new FieldMask(Arrays.asList("b"))),
        adapter.decode(ByteString.decodeHex("0a030a01611201780a030a0162")));
  }

  @Test public void manySingularFieldMaskOccurrencesAreMerged() throws Exception {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto3\";\n"
            + "import \"google/protobuf/field_mask.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  google.protobuf.FieldMask field_mask_field = 1;\n"
            + "}\n")
        .build();

    ProtoAdapter<Object> adapter = schema.protoAdapter("Message", true);
    StringBuilder hex = new StringBuilder();
    for (int i = 0; i < 10_000; i++) {
      hex.append("0a030a0178");
    }
    ByteString encoded = ByteString.decodeHex(hex.toString());

    Map<?, ?> decoded = (Map<?, ?>) adapter.decode(encoded);
    assertEquals(10_000, ((FieldMask) decoded.get("field_mask_field")).getPaths().size());
  }

  @Test public void singularDurationOccurrencesAreMerged() throws Exception {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto3\";\n"
            + "import \"google/protobuf/duration.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  google.protobuf.Duration duration_field = 1;\n"
            + "}\n")
        .build();

    ProtoAdapter<Object> adapter = schema.protoAdapter("Message", true);
    // Tag 1 twice: `{seconds: 5}` then `{nanos: 3}`.
    ByteString encoded = ByteString.decodeHex("0a0208050a021003");

    assertEquals(map("duration_field", Duration.ofSeconds(5L, 3L)), adapter.decode(encoded));
  }

  /**
   * TASK-26: a present Empty field decodes to the unit value and encodes back to the same
   * bytes. The bytes are the field framing only, tag 3 as (3 << 3) | 2 = 0x1a followed by
   * length 0x00: upstream's Unit adapter writes nothing itself (ProtoAdapter.kt
   * commonEmpty), exactly like the port's adapter.
   */
  @Test public void presentEmptyFieldRoundTripsAsTheUnitValue() throws Exception {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto3\";\n"
            + "import \"google/protobuf/empty.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  google.protobuf.Empty empty_field = 3;\n"
            + "}\n")
        .build();

    ProtoAdapter<Object> adapter = schema.protoAdapter("Message", true);
    ByteString encoded = ByteString.decodeHex("1a00");
    Map<String, Object> expected = map("empty_field", ProtoAdapter.UnitValue.INSTANCE);

    assertEquals(expected, adapter.decode(encoded));
    assertEquals(encoded, ByteString.of(adapter.encode(expected)));
  }

  /** TASK-26: a null Empty value is absent; presence is carried by the unit value alone. */
  @Test public void nullEmptyFieldIsAbsent() throws Exception {
    // `optional` spells out explicit presence; a plain proto3 Empty field is NULL_IF_ABSENT
    // too (message types always carry presence), so both spellings behave the same here.
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto3\";\n"
            + "import \"google/protobuf/empty.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional google.protobuf.Empty empty_field = 3;\n"
            + "}\n")
        .build();

    ProtoAdapter<Object> adapter = schema.protoAdapter("Message", true);

    assertEquals(ByteString.EMPTY, ByteString.of(adapter.encode(map("empty_field", (Object) null))));
    assertEquals(map(), adapter.decode(ByteString.EMPTY));
  }

  /** TASK-26: duplicated singular Empty occurrences merge, like every message-backed field. */
  @Test public void singularEmptyOccurrencesMergeToTheUnitValue() throws Exception {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto3\";\n"
            + "import \"google/protobuf/empty.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  google.protobuf.Empty empty_field = 3;\n"
            + "}\n")
        .build();

    ProtoAdapter<Object> adapter = schema.protoAdapter("Message", true);

    assertEquals(map("empty_field", ProtoAdapter.UnitValue.INSTANCE),
        adapter.decode(ByteString.decodeHex("1a001a00")));
  }

  @Disabled // Not supported.
  @Test public void mapTest() throws Exception {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "syntax = \"proto3\";\n"
            + "\n"
            + "message Message {\n"
            + "  map<string, int64> currencies = 1;\n"
            + "}\n")
        .build();

    ProtoAdapter<Object> adapter = schema.protoAdapter("Message", true);
    Map<String, Object> expected = map("currencies", map("USD", 44L, "EUR", 33L));
    assertEquals(expected, adapter.decode(adapter.encode(expected)));
  }

  /** Kotlin mapOf: alternating key/value arguments, insertion-ordered. */
  private static Map<String, Object> map(Object... keysAndValues) {
    return com.squareup.wire.testing.TestFiles.map(keysAndValues);
  }
}
