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

import com.squareup.wire.internal.Internal;
import com.squareup.wire.internal.ProtocolException;
import com.squareup.wire.internal.Reflection;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import okio.Buffer;
import okio.ByteString;
import org.junit.jupiter.api.Test;

/**
 * TASK-17 security regression corpus, runtime reader and runtime-adapter items. One named case per
 * inventory row in docs/security-regression-inventory.md; each case fails when its defect is
 * reintroduced. Detection evidence (pre-fix scratch replay and the relocated upstream 7.1.0
 * oracle) is recorded in that inventory's "Detection evidence" section.
 */
public class RuntimeSecurityCorpusTest {

  // ---------------------------------------------------------------- GHSA-7xpr-hc2w-34m9 (#3597)

  /**
   * A crafted group whose skipped length-delimited field declares a negative length ({@code
   * 9b060a80ffffff0f9c06}: group 99, field 1 length {@code -128}, end group) must throw
   * {@code ProtocolException}, in both readers. Pre-fix this payload silently decoded.
   */
  @Test public void ghsa7xpr_negativeLengthGroupSkip_okioReader() {
    ByteString data = ByteString.decodeHex("9b060a80ffffff0f9c06");

    ProtocolException e = assertThrows(ProtocolException.class,
        () -> TestMessages.Person.ADAPTER.decode(new Buffer().write(data)));
    assertEquals("Negative length: -128. Reader position: 8. Last read tag: 1.", e.getMessage());
  }

  /** Same payload through the 32-bit byte-array reader. */
  @Test public void ghsa7xpr_negativeLengthGroupSkip_byteArrayReader32() {
    byte[] data = ByteString.decodeHex("9b060a80ffffff0f9c06").toByteArray();

    ProtocolException e = assertThrows(ProtocolException.class,
        () -> TestMessages.Person.ADAPTER.decode(data));
    assertEquals("Negative length: -128. Reader position: 8. Last read tag: 1.", e.getMessage());
  }

  // ---------------------------------------------------------------- GHSA-9rm7-3qhh-h2mc (#3635)

  /**
   * Fixed-width reads may not cross the current length-delimited limit even when the source holds
   * more bytes: the limit is 2, the fixed32 tag consumes one byte, and {@code 05000000} sits
   * outside the message. Pre-fix the reader returned 5.
   */
  @Test public void ghsa9rm7_fixed32CannotReadPastLengthDelimitedLimit() throws IOException {
    ByteString encoded = ByteString.decodeHex(
        "02" // varint32 length = 2
            + "0d" // 1: fixed32
            + "05000000" // present in the source, outside the current message
    );
    ProtoReader reader = new ProtoReader(new Buffer().write(encoded));

    assertEquals(2, reader.nextLengthDelimited());
    reader.beginMessage();
    assertEquals(1, reader.nextTag());
    assertThrows(EOFException.class, reader::readFixed32);
  }

  /** fixed64 twin of the fixed32 case. Pre-fix the reader returned 80. */
  @Test public void ghsa9rm7_fixed64CannotReadPastLengthDelimitedLimit() throws IOException {
    ByteString encoded = ByteString.decodeHex(
        "03" // varint32 length = 3
            + "11" // 2: fixed64
            + "5000000000000000" // present in the source, outside the current message
    );
    ProtoReader reader = new ProtoReader(new Buffer().write(encoded));

    assertEquals(3, reader.nextLengthDelimited());
    reader.beginMessage();
    assertEquals(2, reader.nextTag());
    assertThrows(EOFException.class, reader::readFixed64);
  }

  /**
   * A varint may not continue past the current length-delimited limit: the limit is 2, the tag
   * consumes one byte, {@code 80} is the last byte inside the message, and {@code 05} continues
   * the varint outside it. Pre-fix the reader returned 640.
   */
  @Test public void ghsa9rm7_varintCannotReadPastLengthDelimitedLimit() throws IOException {
    ByteString encoded = ByteString.decodeHex(
        "02" // varint32 length = 2
            + "08" // 1: varint
            + "8005" // 0x80 is inside the message, 0x05 continues the varint outside it
    );
    ProtoReader reader = new ProtoReader(new Buffer().write(encoded));

    assertEquals(2, reader.nextLengthDelimited());
    reader.beginMessage();
    assertEquals(1, reader.nextTag());
    assertThrows(EOFException.class, () -> ProtoAdapter.INT32.decode(reader));
  }

  /** The 32-bit reader enforces the same limit on fixed-width reads. */
  @Test public void ghsa9rm7_fixed32CannotReadPastLimit_byteArrayReader32() throws IOException {
    byte[] encoded = ByteString.decodeHex("020d05000000").toByteArray();
    ProtoReader32 reader = new ByteArrayProtoReader32(encoded);

    assertEquals(2, reader.nextLengthDelimited());
    reader.beginMessage();
    assertEquals(1, reader.nextTag());
    assertThrows(EOFException.class, reader::readFixed32);
  }

  /**
   * Positive lengths near {@code Int.MAX_VALUE} must be rejected as {@code IOException} on every
   * decode path: known field, unknown field (skip), and group-wrapped. This pins the contract of
   * upstream's own regression case for the 32-bit reader's cursor arithmetic.
   */
  @Test public void ghsa9rm7_positiveLengthOverflowRejected() {
    byte[] knownField = ByteString.decodeHex("0affffffff07").toByteArray();
    byte[] unknownField = ByteString.decodeHex("1affffffff07").toByteArray();
    byte[] groupWrapped = ByteString.decodeHex("0b0affffffff070c").toByteArray();

    assertThrows(IOException.class, () -> TestMessages.Person.ADAPTER.decode(knownField));
    assertThrows(IOException.class, () -> TestMessages.Person.ADAPTER.decode(unknownField));
    assertThrows(IOException.class, () -> TestMessages.Person.ADAPTER.decode(groupWrapped));
  }

  // ------------------------------------------------- recursion limit (2024, pre-window)

  /**
   * Deeply nested groups must trip the recursion limit instead of overflowing the stack. Present
   * in the pinned baseline since the 2024 group-recursion enforcement; kept in the corpus because
   * advisories do not cover every security fix.
   */
  @Test public void recursionLimit_nestedGroups() {
    byte[] data = new byte[50000];
    for (int i = 0; i < data.length; i++) {
      data[i] = (i % 2 == 0) ? (byte) 0xa3 : 0x01;
    }

    IOException e = assertThrows(IOException.class,
        () -> TestMessages.Person.ADAPTER.decode(new Buffer().write(data)));
    assertEquals("Wire recursion limit exceeded", e.getMessage());
  }

  // ------------------------------------------------- duplicate singular merge (#3652)

  /**
   * The reflection message adapter merges duplicate occurrences of a singular message field per
   * the protobuf specification ({@code {a: 5}} then {@code {b: 7}} merges to {@code {a: 5, b: 7}}).
   * Pre-#3652 the last occurrence replaced the earlier ones.
   */
  @Test public void issue3652_reflectionAdapterMergesDuplicateSingularMessage() throws IOException {
    // Outer field 1 (Child): { a: 5 } then { b: 7 }.
    ByteString encoded = ByteString.decodeHex("0a020805" + "0a021007");

    MergingOuter outer = MergingOuter.ADAPTER.decode(encoded);

    assertEquals(new MergingChild(5, 7), outer.child);
  }

  /**
   * The FieldMask branch of {@code Internal.decodeMessageOrMerge} appends paths instead of
   * replacing the earlier occurrence.
   */
  @Test public void issue3652_decodeMessageOrMergeAppendsFieldMaskPaths() throws IOException {
    // FieldMask { paths: ["a"] } and FieldMask { paths: ["b"] } as length-delimited values.
    ByteString first = ByteString.decodeHex("0a0161");
    ByteString second = ByteString.decodeHex("0a0162");

    FieldMask merged = Internal.decodeMessageOrMerge(
        ProtoAdapter.FIELD_MASK, new ProtoReader(new Buffer().write(second)),
        ProtoAdapter.FIELD_MASK.decode(first));

    assertEquals(new FieldMask(Arrays.asList("a", "b")), merged);
  }

  /** Generated-style fixture for the reflection merge case. */
  public static class MergingChild extends Message<MergingChild, MergingChild.Builder> {
    public static final ProtoAdapter<MergingChild> ADAPTER =
        new ProtoAdapter<MergingChild>(FieldEncoding.LENGTH_DELIMITED, MergingChild.class,
            "type.googleapis.com/MergingChild", Syntax.PROTO_2, null, null) {
          @Override public MergingChild redact(MergingChild value) {
            return value;
          }

          @Override public int encodedSize(MergingChild value) {
            return INT32.encodedSizeWithTag(1, value.a) + INT32.encodedSizeWithTag(2, value.b);
          }

          @Override public void encode(ProtoWriter writer, MergingChild value) throws IOException {
            INT32.encodeWithTag(writer, 1, value.a);
            INT32.encodeWithTag(writer, 2, value.b);
          }

          @Override public MergingChild decode(ProtoReader reader) throws IOException {
            Integer a = null;
            Integer b = null;
            long token = reader.beginMessage();
            int tag;
            while ((tag = reader.nextTag()) != -1) {
              switch (tag) {
                case 1: a = INT32.decode(reader); break;
                case 2: b = INT32.decode(reader); break;
                default: reader.readUnknownField(tag);
              }
            }
            reader.endMessageAndGetUnknownFields(token);
            return new MergingChild(a, b);
          }
        };

    public final Integer a;
    public final Integer b;

    public MergingChild(Integer a, Integer b) {
      this(ADAPTER, a, b);
    }

    MergingChild(ProtoAdapter<MergingChild> adapter, Integer a, Integer b) {
      super(adapter, ByteString.EMPTY);
      this.a = a;
      this.b = b;
    }

    @Override public Builder newBuilder() {
      throw new UnsupportedOperationException();
    }

    @Override public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof MergingChild)) return false;
      MergingChild that = (MergingChild) other;
      return Objects.equals(a, that.a) && Objects.equals(b, that.b);
    }

    @Override public int hashCode() {
      return Objects.hash(a, b);
    }

    public static final class Builder extends Message.Builder<MergingChild, Builder> {
      public Integer a;
      public Integer b;

      @Override public MergingChild build() {
        return new MergingChild(a, b);
      }
    }
  }

  /** Generated-style outer fixture whose field 1 is the singular message {@link MergingChild}. */
  public static class MergingOuter extends Message<MergingOuter, MergingOuter.Builder> {
    @WireField(tag = 1, adapter = "com.squareup.wire.RuntimeSecurityCorpusTest$MergingChild#ADAPTER")
    public final MergingChild child;

    public static final ProtoAdapter<MergingOuter> ADAPTER =
        (ProtoAdapter<MergingOuter>) (ProtoAdapter<?>) Reflection
            .createRuntimeMessageAdapter(MergingOuter.class, null, Syntax.PROTO_2);

    public MergingOuter(MergingChild child) {
      this(ADAPTER, child);
    }

    MergingOuter(ProtoAdapter<MergingOuter> adapter, MergingChild child) {
      super(adapter, ByteString.EMPTY);
      this.child = child;
    }

    @Override public Builder newBuilder() {
      throw new UnsupportedOperationException();
    }

    @Override public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof MergingOuter)) return false;
      return Objects.equals(child, ((MergingOuter) other).child);
    }

    @Override public int hashCode() {
      return child == null ? 0 : child.hashCode();
    }

    public static final class Builder extends Message.Builder<MergingOuter, Builder> {
      public MergingChild child;

      @Override public MergingOuter build() {
        return new MergingOuter(child);
      }
    }
  }
}
