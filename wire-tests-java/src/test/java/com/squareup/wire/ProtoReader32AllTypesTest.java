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

import static org.junit.Assert.assertEquals;

import com.squareup.wire.protos.alltypes.AllTypes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okio.ByteString;
import java.util.Arrays;
import org.junit.Test;

/**
 * Upstream jvm-kotlin-proto-reader-32 suite ported: the same AllTypes corpus upstream drives
 * through the Kotlin-generated adapter, here through the Java-generated adapter, decoded via
 * the int-cursor reader directly and via its ProtoReader wrapper (mechanical adaptations
 * ledgered in UPSTREAM-TEST-ADAPTATIONS.md: Kotlin named arguments to builder fields, Kotlin
 * generator AllTypes to the Java generator's AllTypes).
 */
public class ProtoReader32AllTypesTest {
  private static final okio.ByteString BYTES =
      okio.ByteString.of((byte) 125, (byte) 225);

  private static AllTypes buildAllTypes() {
    AllTypes.Builder builder = new AllTypes.Builder();
    builder.opt_int32 = 111;
    builder.opt_uint32 = 112;
    builder.opt_sint32 = 113;
    builder.opt_fixed32 = 114;
    builder.opt_sfixed32 = 115;
    builder.opt_int64 = 116L;
    builder.opt_uint64 = 117L;
    builder.opt_sint64 = 118L;
    builder.opt_fixed64 = 119L;
    builder.opt_sfixed64 = 120L;
    builder.opt_bool = true;
    builder.opt_float = 122.0f;
    builder.opt_double = 123.0;
    builder.opt_string = "124";
    builder.opt_bytes = okio.ByteString.of((byte) 125, (byte) 225);
    builder.opt_nested_enum = AllTypes.NestedEnum.A;
    builder.opt_nested_message = new AllTypes.NestedMessage(999, ByteString.EMPTY);
    builder.req_int32 = 111;
    builder.req_uint32 = 112;
    builder.req_sint32 = 113;
    builder.req_fixed32 = 114;
    builder.req_sfixed32 = 115;
    builder.req_int64 = 116L;
    builder.req_uint64 = 117L;
    builder.req_sint64 = 118L;
    builder.req_fixed64 = 119L;
    builder.req_sfixed64 = 120L;
    builder.req_bool = true;
    builder.req_float = 122.0f;
    builder.req_double = 123.0;
    builder.req_string = "124";
    builder.req_bytes = okio.ByteString.of((byte) 125, (byte) 225);
    builder.req_nested_enum = AllTypes.NestedEnum.A;
    builder.req_nested_message = new AllTypes.NestedMessage(999, ByteString.EMPTY);
    builder.rep_int32.addAll(Arrays.asList(111, 111));
    builder.rep_uint32.addAll(Arrays.asList(112, 112));
    builder.rep_sint32.addAll(Arrays.asList(113, 113));
    builder.rep_fixed32.addAll(Arrays.asList(114, 114));
    builder.rep_sfixed32.addAll(Arrays.asList(115, 115));
    builder.rep_int64.addAll(Arrays.asList(116L, 116L));
    builder.rep_uint64.addAll(Arrays.asList(117L, 117L));
    builder.rep_sint64.addAll(Arrays.asList(118L, 118L));
    builder.rep_fixed64.addAll(Arrays.asList(119L, 119L));
    builder.rep_sfixed64.addAll(Arrays.asList(120L, 120L));
    builder.rep_bool.addAll(Arrays.asList(true, true));
    builder.rep_float.addAll(Arrays.asList(122.0f, 122.0f));
    builder.rep_double.addAll(Arrays.asList(123.0, 123.0));
    builder.rep_string.addAll(Arrays.asList("124", "124"));
    builder.rep_bytes.addAll(Arrays.asList(
        okio.ByteString.of((byte) 125, (byte) 225),
        okio.ByteString.of((byte) 125, (byte) 225)));
    builder.rep_nested_enum.addAll(Arrays.asList(
        AllTypes.NestedEnum.A, AllTypes.NestedEnum.A));
    builder.rep_nested_message.addAll(Arrays.asList(
        new AllTypes.NestedMessage(999, ByteString.EMPTY),
        new AllTypes.NestedMessage(999, ByteString.EMPTY)));
    builder.pack_int32.addAll(Arrays.asList(111, 111));
    builder.pack_uint32.addAll(Arrays.asList(112, 112));
    builder.pack_sint32.addAll(Arrays.asList(113, 113));
    builder.pack_fixed32.addAll(Arrays.asList(114, 114));
    builder.pack_sfixed32.addAll(Arrays.asList(115, 115));
    builder.pack_int64.addAll(Arrays.asList(116L, 116L));
    builder.pack_uint64.addAll(Arrays.asList(117L, 117L));
    builder.pack_sint64.addAll(Arrays.asList(118L, 118L));
    builder.pack_fixed64.addAll(Arrays.asList(119L, 119L));
    builder.pack_sfixed64.addAll(Arrays.asList(120L, 120L));
    builder.pack_bool.addAll(Arrays.asList(true, true));
    builder.pack_float.addAll(Arrays.asList(122.0f, 122.0f));
    builder.pack_double.addAll(Arrays.asList(123.0, 123.0));
    builder.pack_nested_enum.addAll(Arrays.asList(
        AllTypes.NestedEnum.A, AllTypes.NestedEnum.A));
    return builder.build();
  }

  @Test public void decodeProtoReader32ByteString() throws IOException {
    AllTypes allTypes = buildAllTypes();
    ProtoReader32 protoReader32 =
        new ByteArrayProtoReader32(allTypes.encodeByteString().toByteArray());
    assertEquals(allTypes, AllTypes.ADAPTER.decode(protoReader32));
  }

  @Test public void decodeProtoReader32ByteArray() throws IOException {
    AllTypes allTypes = buildAllTypes();
    byte[] encoded = allTypes.encode();
    ProtoReader32 protoReader32 = new ByteArrayProtoReader32(encoded);
    assertEquals(allTypes, AllTypes.ADAPTER.decode(protoReader32));
  }

  @Test public void decodeThroughAsProtoReaderWrapper() throws IOException {
    AllTypes allTypes = buildAllTypes();
    ProtoReader32 protoReader32 =
        new ByteArrayProtoReader32(allTypes.encodeByteString().toByteArray());
    assertEquals(allTypes, AllTypes.ADAPTER.decode(protoReader32.asProtoReader()));
  }

  @Test public void decodePackedOnly() throws IOException {
    // A message carrying only packed int32 fields: tag 241 (field 30, packed varint).
    okio.Buffer buffer = new okio.Buffer();
    ProtoWriter writer = new ProtoWriter(buffer);
    writer.writeTag(30, FieldEncoding.LENGTH_DELIMITED);
    writer.writeVarint32(3); // 111 (1 byte) + 222 (2 bytes)
    writer.writeVarint32(111);
    writer.writeVarint32(222);
    byte[] bytes = buffer.readByteArray();

    ProtoReader32 reader = new ByteArrayProtoReader32(bytes);
    int token = reader.beginMessage();
    List<Integer> packed = new ArrayList<>();
    int tag;
    while ((tag = reader.nextTag()) != -1) {
      // Upstream pattern (commonTryDecode): one check per nextTag; PACKED_TAG replays the tag.
      if (tag == 30) {
        if (reader.beforePossiblyPackedScalar()) {
          packed.add(ProtoAdapter.INT32.decode(reader));
        }
      }
    }
    reader.endMessageAndGetUnknownFields(token);
    assertEquals(Arrays.asList(111, 222), packed);
  }
}
