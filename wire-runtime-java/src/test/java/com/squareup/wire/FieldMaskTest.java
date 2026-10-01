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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import okio.ByteString;
import org.junit.jupiter.api.Test;

/**
 * Upstream commonTest translated (assertk to JUnit 5; paths() for the Kotlin property,
 * copy(paths) via the copy overload); see UPSTREAM-TEST-ADAPTATIONS.md.
 */
public class FieldMaskTest {
  @Test public void storesPaths() {
    FieldMask fieldMask = new FieldMask(Arrays.asList("user.display_name", "photo"));
    assertEquals(Arrays.asList("user.display_name", "photo"), fieldMask.paths());
  }

  @Test public void defaultPathsIsEmpty() {
    assertEquals(new ArrayList<String>(), new FieldMask().paths());
  }

  @Test public void pathsAreImmutableCopy() {
    List<String> paths = new ArrayList<>();
    paths.add("user.display_name");
    FieldMask fieldMask = new FieldMask(paths);

    paths.add("photo");

    assertEquals(Arrays.asList("user.display_name"), fieldMask.paths());
  }

  @Test public void copy() {
    FieldMask fieldMask = new FieldMask(Arrays.asList("user.display_name"));
    assertEquals(new FieldMask(Arrays.asList("photo")), fieldMask.copy(Arrays.asList("photo")));
  }

  @Test public void protoAdapterEncodesPaths() throws java.io.IOException {
    FieldMask fieldMask = new FieldMask(Arrays.asList("user.display_name", "photo"));
    assertEquals(ByteString.decodeHex("0a11757365722e646973706c61795f6e616d650a0570686f746f"),
        ProtoAdapter.FIELD_MASK.encodeByteString(fieldMask));
  }

  @Test public void protoAdapterDecodesPaths() throws java.io.IOException {
    ByteString bytes = ByteString.decodeHex("0a11757365722e646973706c61795f6e616d650a0570686f746f");
    assertEquals(new FieldMask(Arrays.asList("user.display_name", "photo")),
        ProtoAdapter.FIELD_MASK.decode(bytes));
  }

  @Test public void protoAdapterHasTypeUrl() {
    assertEquals("type.googleapis.com/google.protobuf.FieldMask",
        ProtoAdapter.FIELD_MASK.typeUrl);
  }

  @Test public void protoAdapterDiscardsUnknownFields() throws java.io.IOException {
    // Path "a", followed by unknown field 2 with varint value 5.
    ByteString bytes = ByteString.decodeHex("0a01611005");
    assertEquals(new FieldMask(Arrays.asList("a")), ProtoAdapter.FIELD_MASK.decode(bytes));
  }
}
