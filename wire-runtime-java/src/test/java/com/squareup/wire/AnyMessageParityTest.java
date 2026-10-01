package com.squareup.wire;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.squareup.wire.internal.Internal;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import okio.ByteString;
import org.junit.Test;

/**
 * AnyMessage parity against the relocated upstream oracle: pack/encode/decode/unpack through
 * the same adapters on both sides, plus the hashCode caching contract.
 */
public class AnyMessageParityTest {
  private static final io.github.paoloantinori.antiwire.parity.wire.ProtoAdapter<io.github.paoloantinori.antiwire.parity.wire.FieldMask>
      THEIR_FIELD_MASK = io.github.paoloantinori.antiwire.parity.wire.ProtoAdapter.FIELD_MASK;

  @Test public void fieldMaskRoundTrip() throws IOException {
    FieldMask mask = new FieldMask(Arrays.asList("a.b", "c"));
    AnyMessage any = AnyMessage.pack(ProtoAdapter.FIELD_MASK, mask);
    io.github.paoloantinori.antiwire.parity.wire.AnyMessage theirAny =
        io.github.paoloantinori.antiwire.parity.wire.AnyMessage.Companion.pack(
            THEIR_FIELD_MASK,
            new io.github.paoloantinori.antiwire.parity.wire.FieldMask(Arrays.asList("a.b", "c")));

    assertArrayEquals("Any bytes", theirAny.encode(), any.encode());
    assertEquals("Any toString", theirAny.toString(), any.toString());

    AnyMessage decoded = AnyMessage.ADAPTER.decode(any.encodeByteString());
    assertEquals(any, decoded);
    FieldMask unpacked = decoded.unpack(ProtoAdapter.FIELD_MASK);
    assertEquals(Arrays.asList("a.b", "c"), unpacked.paths());
  }

  @Test public void structRoundTrip() throws IOException {
    Map<String, Object> struct = new LinkedHashMap<>();
    struct.put("n", 1.5);
    struct.put("s", "v");
    struct.put("l", Arrays.asList(1.0, null));
    AnyMessage any = AnyMessage.pack(ProtoAdapter.STRUCT_MAP, struct);
    AnyMessage decoded = AnyMessage.ADAPTER.decode(any.encodeByteString());
    @SuppressWarnings("unchecked")
    Map<String, Object> unpacked = (Map<String, Object>) (Object) decoded.unpack(
        ProtoAdapter.STRUCT_MAP);
    assertEquals(1.5, unpacked.get("n"));
    assertEquals("v", unpacked.get("s"));
  }

  @Test public void unpackTypeMismatch() throws IOException {
    FieldMask mask = new FieldMask(Arrays.asList("x"));
    AnyMessage any = AnyMessage.pack(ProtoAdapter.FIELD_MASK, mask);
    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> any.unpack(ProtoAdapter.DURATION));
    assertTrue(e.getMessage().startsWith("type mismatch: "));
    // The null-returning variant stays quiet instead.
    assertEquals(null, any.unpackOrNull(ProtoAdapter.DURATION));
  }

  @Test public void hashCodeCaching() throws IOException {
    FieldMask mask = new FieldMask(Arrays.asList("a"));
    AnyMessage any = AnyMessage.pack(ProtoAdapter.FIELD_MASK, mask);
    int first = any.hashCode();
    assertEquals(first, any.hashCode());
    AnyMessage same = AnyMessage.pack(ProtoAdapter.FIELD_MASK,
        new FieldMask(Arrays.asList("a")));
    assertEquals(any.hashCode(), same.hashCode());
    assertEquals(any, same);
  }

  @Test public void adapterWithoutTypeUrlCannotPack() {
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> AnyMessage.pack(ProtoAdapter.STRING, "x"));
    assertTrue(e.getMessage().contains("no type URL"));
  }

  @Test public void messageSerializationRoundTrip() throws Exception {
    FieldMask mask = new FieldMask(Arrays.asList("ser"));
    AnyMessage any = AnyMessage.pack(ProtoAdapter.FIELD_MASK, mask);
    // MessageSerializedForm replaces the message in the stream; the ADAPTER field is public.
    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
      out.writeObject(any);
    }
    Object read;
    try (java.io.ObjectInputStream in =
        new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
      read = in.readObject();
    }
    assertEquals(any, read);
  }
}
