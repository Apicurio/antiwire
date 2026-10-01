package com.squareup.wire;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.squareup.wire.internal.Reflection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import okio.ByteString;
import org.junit.Test;

/**
 * Reflection machinery parity: the same hand-written generated-style message class is driven
 * through this port's RuntimeMessageAdapter and through the relocated upstream oracle's
 * newMessageAdapter reflection path, and must behave identically.
 */
public class ReflectionParityTest {

  /** A generated-style Java message: public final fields plus a public-field Builder. */
  public static class ReflectedMessage extends Message<ReflectedMessage, ReflectedMessage.Builder> {
    @WireField(
        tag = 1,
        adapter = "com.squareup.wire.ProtoAdapter#INT32"
    )
    public final Integer number;

    @WireField(
        tag = 2,
        adapter = "com.squareup.wire.ProtoAdapter#STRING"
    )
    public final String name;

    @WireField(
        tag = 3,
        adapter = "com.squareup.wire.ProtoAdapter#INT32",
        label = WireField.Label.REPEATED
    )
    public final List<Integer> counts;

    @WireField(
        tag = 4,
        adapter = "com.squareup.wire.ProtoAdapter#BYTES"
    )
    public final ByteString blob;

    public ReflectedMessage(Integer number, String name, List<Integer> counts, ByteString blob,
        ByteString unknownFields) {
      super(ADAPTER, unknownFields);
      this.number = number;
      this.name = name;
      this.counts = counts;
      this.blob = blob;
    }

    @Override public Builder newBuilder() {
      throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    public static final ProtoAdapter<ReflectedMessage> ADAPTER =
        (ProtoAdapter<ReflectedMessage>) (ProtoAdapter<?>) Reflection.createRuntimeMessageAdapter(
            ReflectedMessage.class, null, Syntax.PROTO_2);

    public static final class Builder extends Message.Builder<ReflectedMessage, Builder> {
      public Builder() {
      }

      public Integer number;
      public String name;
      public List<Integer> counts = Internal2.mutableCounts();
      public ByteString blob;

      public Builder number(Integer value) {
        this.number = value;
        return this;
      }

      public Builder name(String value) {
        this.name = value;
        return this;
      }

      public Builder counts(List<Integer> value) {
        this.counts = value;
        return this;
      }

      public Builder blob(ByteString value) {
        this.blob = value;
        return this;
      }

      @Override public ReflectedMessage build() {
        return new ReflectedMessage(number, name, counts, blob, buildUnknownFields());
      }
    }

    /** Tiny local helper so the fixture does not depend on Internal internals. */
    static final class Internal2 {
      static List<Integer> mutableCounts() {
        return new ArrayList<>();
      }
    }
  }

  private static ReflectedMessage sample() {
    return new ReflectedMessage.Builder()
        .number(-42)
        .name("héllo 😀")
        .counts(Arrays.asList(1, 150, -1, 0))
        .blob(ByteString.encodeUtf8("bytes"))
        .build();
  }

  /**
   * The oracle comparison is impossible for the reflection machinery itself: the relocated
   * upstream reads its own WireField annotation class, and annotations match by runtime type,
   * so it sees no fields on this port's classes. Instead the adapter is verified against
   * hand-built wire bytes through the proven ProtoWriter.
   */
  @Test public void encodeMatchesHandBuiltBytes() throws IOException {
    ReflectedMessage message = sample();

    okio.Buffer expected = new okio.Buffer();
    ProtoWriter writer = new ProtoWriter(expected);
    writer.writeTag(1, FieldEncoding.VARINT);
    writer.writeSignedVarint32(-42);
    writer.writeTag(2, FieldEncoding.LENGTH_DELIMITED);
    byte[] name = message.name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    writer.writeVarint32(name.length);
    writer.writeString(message.name);
    for (int count : message.counts) {
      writer.writeTag(3, FieldEncoding.VARINT);
      writer.writeSignedVarint32(count);
    }
    writer.writeTag(4, FieldEncoding.LENGTH_DELIMITED);
    writer.writeVarint32(message.blob.size());
    writer.writeBytes(message.blob);

    assertArrayEquals(expected.readByteArray(), ReflectedMessage.ADAPTER.encode(message));
  }

  @Test public void encodedSizeAndDecodeRoundTrip() throws IOException {
    ReflectedMessage message = sample();
    byte[] bytes = ReflectedMessage.ADAPTER.encode(message);
    assertEquals(bytes.length, ReflectedMessage.ADAPTER.encodedSize(message));

    ReflectedMessage decoded = ReflectedMessage.ADAPTER.decode(bytes);
    assertEquals(message.number, decoded.number);
    assertEquals(message.name, decoded.name);
    assertEquals(message.counts, decoded.counts);
    assertEquals(message.blob, decoded.blob);
    assertEquals("ReflectedMessage{number=-42, name=héllo 😀, counts=[1, 150, -1, 0], "
        + "blob=[text=bytes]}",
        ReflectedMessage.ADAPTER.toString(decoded));
  }

  @Test public void unknownFieldParity() throws IOException {
    // number on tag 1 (known), an unknown tag 15 fixed64, name on tag 2.
    okio.Buffer buffer = new okio.Buffer();
    ProtoWriter writer = new ProtoWriter(buffer);
    writer.writeTag(1, FieldEncoding.VARINT);
    writer.writeVarint32(7);
    writer.writeTag(15, FieldEncoding.FIXED64);
    writer.writeFixed64(Long.MAX_VALUE);
    writer.writeTag(2, FieldEncoding.LENGTH_DELIMITED);
    byte[] utf8 = "n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    writer.writeVarint32(utf8.length);
    writer.writeString("n");
    byte[] bytes = buffer.readByteArray();

    ReflectedMessage ours = ReflectedMessage.ADAPTER.decode(bytes);
    okio.Buffer unknown = new okio.Buffer().write(ours.unknownFields());
    assertEquals(0x79, unknown.readByte() & 0xff); // tag 15, FIXED64
    assertEquals(Long.MAX_VALUE, unknown.readLongLe());
  }

  @Test public void requiredRedactionFails() throws IOException {
    // A message with a REQUIRED redacted field refuses to redact (upstream contract).
    Object adapter = Reflection.createRuntimeMessageAdapter(RequiredRedacted.class, null,
        Syntax.PROTO_2);
    RequiredRedacted message = new RequiredRedacted("x", ByteString.EMPTY);
    UnsupportedOperationException ours = assertThrows(UnsupportedOperationException.class,
        () -> ((ProtoAdapter<RequiredRedacted>) adapter).redact(message));
    assertEquals(
        "Field 'secret' in class com.squareup.wire.ReflectionParityTest$RequiredRedacted "
            + "is required and cannot be redacted.",
        ours.getMessage());
  }

  public static class RequiredRedacted extends Message<RequiredRedacted, RequiredRedacted.Builder> {
    @WireField(
        tag = 1,
        adapter = "com.squareup.wire.ProtoAdapter#STRING",
        label = WireField.Label.REQUIRED,
        redacted = true
    )
    public final String secret;

    public RequiredRedacted(String secret, ByteString unknownFields) {
      super(ADAPTER, unknownFields);
      this.secret = secret;
    }

    @Override public Builder newBuilder() {
      throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    public static final ProtoAdapter<RequiredRedacted> ADAPTER =
        (ProtoAdapter<RequiredRedacted>) (ProtoAdapter<?>) Reflection.createRuntimeMessageAdapter(
            RequiredRedacted.class, null, Syntax.PROTO_2);

    public static final class Builder extends Message.Builder<RequiredRedacted, Builder> {
      public String secret;

      public Builder() {
      }

      @Override public RequiredRedacted build() {
        throw new UnsupportedOperationException();
      }
    }
  }

  @Test public void wireGet() {
    assertEquals("v", Wire.get("v", "d"));
    assertEquals("d", Wire.get(null, "d"));
  }

  @Test public void adapterStringLookup() {
    ProtoAdapter<?> adapter = ProtoAdapter.get(
        "com.squareup.wire.ReflectionParityTest$ReflectedMessage#ADAPTER");
    assertEquals(ReflectedMessage.ADAPTER, adapter);
  }
}
