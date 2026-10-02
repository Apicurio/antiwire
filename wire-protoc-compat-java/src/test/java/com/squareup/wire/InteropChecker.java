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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import okio.Buffer;
import okio.ByteString;

/**
 * TASK-15 port of upstream wire-protoc-compatibility-tests InteropChecker.kt (pinned tag 7.1.0).
 * assertk becomes JUnit 5, and the Kotlin {@code forEachTag} extension (Java-hidden upstream,
 * conventions R3) becomes the port's explicit beginMessage/nextTag loop.
 *
 * <p>TASK-15 adaptation (DEC-6): the gson roundtrip ({@code WireTypeAdapterFactory}) and the
 * moshi roundtrip ({@code WireJsonAdapterFactory}) are dropped. The JSON adapters are declared
 * non-ported functionality, so no port equivalent exists to register with GsonBuilder or
 * Moshi.Builder. The protoc JSON roundtrip is retained and runs against the reference
 * implementation through protobuf-java-util 4.36.1. {@code wireCanonicalJson} and
 * {@code wireAlternateJsons} fed only the dropped roundtrips upstream; they stay constructor
 * parameters so every call site is identical to upstream, but nothing consumes them here.
 */
public class InteropChecker {
  private final Message protocMessage;

  /** JSON representation of the message expected for Wire and Protoc using all integrations. */
  private final String canonicalJson;

  /**
   * In proto2, JSON encoding was not specified by the protocol buffers spec. Wire uses
   * snake_case everywhere and protoc uses camelCase everywhere. Avoiding gross difference is one of
   * the best reasons to upgrade to proto3.
   */
  private final String wireCanonicalJson;

  /** JSON representations that should also decode to the message. */
  private final List<String> alternateJsons;

  /** Alternate forms of JSON we expect Wire to support but protoc doesn't. */
  private final List<String> wireAlternateJsons;

  private ByteString protocBytes;

  private final JsonFormat.TypeRegistry typeRegistry = JsonFormat.TypeRegistry.newBuilder()
      .build();

  private final JsonFormat.Printer jsonPrinter = JsonFormat.printer()
      .omittingInsignificantWhitespace()
      .usingTypeRegistry(typeRegistry);

  private final JsonFormat.Parser jsonParser = JsonFormat.parser()
      .usingTypeRegistry(typeRegistry);

  public InteropChecker(Message protocMessage, String canonicalJson) {
    this(protocMessage, canonicalJson, canonicalJson, Collections.<String>emptyList(),
        Collections.<String>emptyList());
  }

  public InteropChecker(
      Message protocMessage,
      String canonicalJson,
      String wireCanonicalJson,
      List<String> alternateJsons,
      List<String> wireAlternateJsons) {
    this.protocMessage = protocMessage;
    this.canonicalJson = canonicalJson;
    this.wireCanonicalJson = wireCanonicalJson;
    this.alternateJsons = alternateJsons;
    this.wireAlternateJsons = wireAlternateJsons;
  }

  public void check(Object message) throws IOException {
    protocBytes = ByteString.of(protocMessage.toByteArray());

    roundtripProtocJson();
    roundtripWireBytes(message);
    // TASK-15 adaptation (DEC-6): upstream also roundtrips the message through gson
    // (WireTypeAdapterFactory) and moshi (WireJsonAdapterFactory); the JSON adapters are excluded
    // functionality in this port, so those two roundtrips have no equivalent here.
  }

  private void roundtripProtocJson() throws IOException {
    assertEquals(canonicalJson, jsonPrinter.print(protocMessage));

    assertEquals(protocMessage, parse(canonicalJson));
    for (String json : alternateJsons) {
      assertEquals(protocMessage, parse(json));
    }
  }

  private Message parse(String json) throws IOException {
    Message.Builder builder = protocMessage.newBuilderForType();
    jsonParser.merge(json, builder);
    return builder.build();
  }

  private void roundtripWireBytes(Object message) throws IOException {
    ProtoAdapter<Object> adapter = (ProtoAdapter<Object>) ProtoAdapter.get(message.getClass());

    ByteString wireBytes = adapter.encodeByteString(message);
    assertProtobufByteStringEquality(protocBytes, wireBytes);
    assertProtobufByteStringEquality(adapter.encodeByteString(message), wireBytes);
    assertEquals(message, adapter.decode(protocBytes));
  }

  /**
   * Protoc and Wire might serialize their Protobuf messages in a different order. We thus try two
   * comparisons.
   */
  public void assertProtobufByteStringEquality(ByteString expected, ByteString actual)
      throws IOException {
    if (actual.equals(expected)) return;

    ByteString sortedActual = sortedByTagUnsafe(actual);
    if (sortedActual.equals(expected)) return;

    fail(":<" + expected + "> but was:<" + actual + "> or sorted by tags: <" + sortedActual + ">");
  }

  /**
   * Not safe for production code. Returns a re-encoded copy of this protobuf message with fields
   * ordered by tag number. Fields with the same tag (e.g. repeated fields) retain their relative
   * order. Nested message bytes are kept opaque. This is used solely for testing.
   */
  private static ByteString sortedByTagUnsafe(ByteString bytes) throws IOException {
    class Record {
      final int tag;
      final FieldEncoding fieldEncoding;
      final Object value;

      Record(int tag, FieldEncoding fieldEncoding, Object value) {
        this.tag = tag;
        this.fieldEncoding = fieldEncoding;
        this.value = value;
      }
    }

    List<Record> records = new ArrayList<>();
    ProtoReader reader = new ProtoReader(new Buffer().write(bytes));
    long token = reader.beginMessage();
    for (int tag = reader.nextTag(); tag != -1; tag = reader.nextTag()) {
      FieldEncoding fieldEncoding = reader.peekFieldEncoding();
      Object value = ((ProtoAdapter<Object>) fieldEncoding.rawProtoAdapter()).decode(reader);
      records.add(new Record(tag, fieldEncoding, value));
    }
    reader.endMessageAndGetUnknownFields(token);

    records.sort(Comparator.comparingInt(record -> record.tag));

    Buffer buffer = new Buffer();
    ProtoWriter writer = new ProtoWriter(buffer);
    for (Record record : records) {
      ((ProtoAdapter<Object>) record.fieldEncoding.rawProtoAdapter())
          .encodeWithTag(writer, record.tag, record.value);
    }
    return buffer.readByteString();
  }
}
