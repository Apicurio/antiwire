/*
 * Copyright (C) 2015 Square, Inc.
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

import com.squareup.wire.internal.Internal;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;
import okio.Okio;

/**
 * Converts values of type {@code E} to and from their protobuf-encoded representation.
 *
 * <p>Scope notes for this port (docs/m1-ownership-map.md): the base-class defaults
 * {@code decode(ProtoReader32)} and {@code tryDecode(ProtoReader32)} route through the reader
 * wrapper, byte-identical to upstream's JVM default; the per-adapter direct overloads are
 * performance work owned by TASK-20. {@code decode(byte[])} stays on the long reader (proven
 * transcript-identical by ProtoReader32ParityTest; recorded in the ownership map). The
 * companion members that need reflection (newMessageAdapter, newEnumAdapter, get) land with
 * TASK-7.
 */
public abstract class ProtoAdapter<E> {
  final FieldEncoding fieldEncoding;
  public final Class<?> type;
  public final String typeUrl;
  public final Syntax syntax;
  public final E identity;
  public final String sourceFile;

  final ProtoAdapter<List<E>> packedAdapter;
  final ProtoAdapter<List<E>> repeatedAdapter;

  protected ProtoAdapter(FieldEncoding fieldEncoding, Class<?> type, String typeUrl,
      Syntax syntax, E identity, String sourceFile) {
    this.fieldEncoding = fieldEncoding;
    this.type = type;
    this.typeUrl = typeUrl;
    this.syntax = syntax;
    this.identity = identity;
    this.sourceFile = sourceFile;
    this.packedAdapter = (this instanceof PackedProtoAdapter
        || this instanceof RepeatedProtoAdapter
        || fieldEncoding == FieldEncoding.LENGTH_DELIMITED)
        ? null : new PackedProtoAdapter<E>(this);
    this.repeatedAdapter = (this instanceof PackedProtoAdapter
        || this instanceof RepeatedProtoAdapter)
        ? null : new RepeatedProtoAdapter<E>(this);
  }

  protected ProtoAdapter(FieldEncoding fieldEncoding, Class<?> type, String typeUrl,
      Syntax syntax) {
    this(fieldEncoding, type, typeUrl, syntax, null, null);
  }

  protected ProtoAdapter(FieldEncoding fieldEncoding, Class<?> type, String typeUrl,
      Syntax syntax, E identity) {
    this(fieldEncoding, type, typeUrl, syntax, identity, null);
  }

  /** Returns the redacted form of {@code value}. */
  public abstract E redact(E value);

  /**
   * The size of the non-null data {@code value}. This does not include the size required for a
   * length-delimited prefix (should the type require one).
   */
  public abstract int encodedSize(E value);

  /**
   * The size of {@code tag} and {@code value} in the wire format. This size includes the tag,
   * type, length-delimited prefix (should the type require one), and value. Returns 0 if
   * {@code value} is null.
   */
  public int encodedSizeWithTag(int tag, E value) {
    if (value == null) return 0;
    int size = encodedSize(value);
    if (fieldEncoding == FieldEncoding.LENGTH_DELIMITED) {
      size += ProtoWriter.varint32Size(size);
    }
    return size + ProtoWriter.tagSize(tag);
  }

  /** Write non-null {@code value} to {@code writer}. */
  public abstract void encode(ProtoWriter writer, E value) throws IOException;

  /** Write non-null {@code value} to {@code writer}. */
  public void encode(ReverseProtoWriter writer, E value) throws IOException {
    writer.writeForward(forwardWriter -> encode(forwardWriter, value));
  }

  /** Write {@code tag} and {@code value} to {@code writer}. If value is null this does nothing. */
  public void encodeWithTag(ProtoWriter writer, int tag, E value) throws IOException {
    if (value == null) return;
    writer.writeTag(tag, fieldEncoding);
    if (fieldEncoding == FieldEncoding.LENGTH_DELIMITED) {
      writer.writeVarint32(encodedSize(value));
    }
    encode(writer, value);
  }

  /** Write {@code tag} and {@code value} to {@code writer}. If value is null this does nothing. */
  public void encodeWithTag(ReverseProtoWriter writer, int tag, E value) throws IOException {
    if (value == null) return;
    if (fieldEncoding == FieldEncoding.LENGTH_DELIMITED) {
      int byteCountBefore = writer.byteCount();
      encode(writer, value);
      writer.writeVarint32(writer.byteCount() - byteCountBefore);
    } else {
      encode(writer, value);
    }
    writer.writeTag(tag, fieldEncoding);
  }

  /** Encode {@code value} and write it to {@code sink}. */
  public void encode(BufferedSink sink, E value) throws IOException {
    ReverseProtoWriter writer = new ReverseProtoWriter();
    encode(writer, value);
    writer.writeTo(sink);
  }

  /** Encode {@code value} as a {@code byte[]}. */
  public byte[] encode(E value) throws IOException {
    Buffer buffer = new Buffer();
    encode(buffer, value);
    return buffer.readByteArray();
  }

  /** Encode {@code value} as a {@link ByteString}. */
  public ByteString encodeByteString(E value) throws IOException {
    Buffer buffer = new Buffer();
    encode(buffer, value);
    return buffer.readByteString();
  }

  /** Encode {@code value} and write it to {@code stream}. */
  public void encode(OutputStream stream, E value) throws IOException {
    BufferedSink buffer = Okio.buffer(Okio.sink(stream));
    encode(buffer, value);
    buffer.emit();
  }

  /** Read a non-null value from {@code reader}. */
  public abstract E decode(ProtoReader reader) throws IOException;

  /** Read a non-null value from {@code reader}, a 32-bit-cursor reader. */
  public E decode(ProtoReader32 reader) throws IOException {
    return decode(reader.asProtoReader());
  }

  /**
   * Reads a value and appends it to {@code destination} if this has data available. Otherwise,
   * it will only clear the reader state.
   */
  public void tryDecode(ProtoReader32 reader, List<E> destination) throws IOException {
    if (reader.beforePossiblyPackedScalar()) {
      destination.add(decode(reader));
    }
  }

  /**
   * Read an encoded message from {@code bytes}. Like upstream's 32-bit-reader path, bare
   * decoding of top-level length-delimited scalars (a naked string or byte string) is not a
   * supported form: both this port and the upstream oracle throw there. Such values decode
   * through their tagged form inside a message.
   */
  public E decode(byte[] bytes) throws IOException {
    // TASK-20: upstream's commonDecode enters via the array-backed 32-bit reader; the buffer
    // wrap below copies the whole payload into segments (docs/performance.md, decode cells).
    return decode(new ByteArrayProtoReader32(bytes));
  }

  /** Read an encoded message from {@code bytes}. See {@link #decode(byte[])}. */
  public E decode(ByteString bytes) throws IOException {
    return decode(new ByteArrayProtoReader32(bytes.toByteArray()));
  }

  /** Read an encoded message from {@code source}. */
  public E decode(BufferedSource source) throws IOException {
    return decode(new ProtoReader(source));
  }

  /** Read an encoded message from {@code stream}. */
  public E decode(InputStream stream) throws IOException {
    return decode(Okio.buffer(Okio.source(stream)));
  }

  /**
   * Reads a value and appends it to {@code destination} if this has data available. Otherwise, it
   * will only clear the reader state.
   */
  public void tryDecode(ProtoReader reader, List<E> destination) throws IOException {
    if (reader.beforePossiblyPackedScalar()) {
      destination.add(decode(reader));
    }
  }

  /** Returns a human-readable version of the given {@code value}. */
  public String toString(E value) {
    return String.valueOf(value);
  }

  public ProtoAdapter<?> withLabel(WireField.Label label) {
    if (label.isRepeated()) {
      return label.isPacked() ? asPacked() : asRepeated();
    }
    return this;
  }

  /** Returns an adapter for {@code E} but as a packed, repeated value. */
  @SuppressWarnings("unchecked")
  public ProtoAdapter<List<E>> asPacked() {
    if (fieldEncoding == FieldEncoding.LENGTH_DELIMITED) {
      throw new IllegalArgumentException("Unable to pack a length-delimited type.");
    }
    if (packedAdapter == null) {
      throw new UnsupportedOperationException(
          "Can't create a packed adapter from a packed or repeated adapter.");
    }
    return packedAdapter;
  }

  /**
   * Returns an adapter for {@code E} but as a repeated value.
   *
   * <p>Note: Repeated items are not required to be encoded sequentially. Thus, when decoding
   * using the returned adapter, only single-element lists will be returned and it is the caller's
   * responsibility to merge them into the final list.
   */
  public ProtoAdapter<List<E>> asRepeated() {
    if (repeatedAdapter == null) {
      throw new UnsupportedOperationException(
          "Can't create a repeated adapter from a repeated or packed adapter.");
    }
    return repeatedAdapter;
  }

  final boolean isStruct() {
    return this == STRUCT_MAP || this == STRUCT_LIST || this == STRUCT_VALUE
        || this == STRUCT_NULL;
  }

  public static final class EnumConstantNotFoundException extends IllegalArgumentException {
    public final int value;

    public EnumConstantNotFoundException(int value, Class<?> type) {
      super("Unknown enum tag " + value + " for " + (type == null ? null : type.getName()));
      this.value = value;
    }
  }

  /** See {@link Internal#decodeMessageOrMerge}. */
  E decodeMessageOrMerge(ProtoReader reader, E existing) throws IOException {
    return Internal.decodeMessageOrMerge(this, reader, existing);
  }

  /**
   * Creates a new proto adapter for a map using {@code keyAdapter} and {@code valueAdapter}.
   *
   * <p>Note: Map entries are not required to be encoded sequentially. Thus, when decoding using
   * the returned adapter, only single-element maps will be returned and it is the caller's
   * responsibility to merge them into the final map.
   */
  public static <K, V> ProtoAdapter<Map<K, V>> newMapAdapter(ProtoAdapter<K> keyAdapter,
      ProtoAdapter<V> valueAdapter) {
    return new MapProtoAdapter<K, V>(keyAdapter, valueAdapter);
  }

  /** Creates a new proto adapter for a generated message type through reflection. */
  public static <M extends Message<M, B>, B extends Message.Builder<M, B>> ProtoAdapter<M>
      newMessageAdapter(Class<M> type) {
    return com.squareup.wire.internal.Reflection.createRuntimeMessageAdapter(type, null,
        Syntax.PROTO_2);
  }

  /** Obsolete; for classes generated before syntax was added. */
  public static <M extends Message<M, B>, B extends Message.Builder<M, B>> ProtoAdapter<M>
      newMessageAdapter(Class<M> type, String typeUrl) {
    return com.squareup.wire.internal.Reflection.createRuntimeMessageAdapter(type, typeUrl,
        Syntax.PROTO_2);
  }

  /** Obsolete; for classes generated before classLoader was added. */
  public static <M extends Message<M, B>, B extends Message.Builder<M, B>> ProtoAdapter<M>
      newMessageAdapter(Class<M> type, String typeUrl, Syntax syntax) {
    return com.squareup.wire.internal.Reflection.createRuntimeMessageAdapter(type, typeUrl,
        syntax);
  }

  /** Creates a new proto adapter for {@code type}. */
  public static <M extends Message<M, B>, B extends Message.Builder<M, B>> ProtoAdapter<M>
      newMessageAdapter(Class<M> type, String typeUrl, Syntax syntax, ClassLoader classLoader) {
    return com.squareup.wire.internal.Reflection.createRuntimeMessageAdapter(type, typeUrl,
        syntax, classLoader);
  }

  /** Creates a new proto adapter for {@code type}. */
  public static <E extends WireEnum> EnumAdapter<E> newEnumAdapter(Class<E> type) {
    return new RuntimeEnumAdapter<>(type);
  }

  /** Returns the adapter for the type of {@code message}. */
  public static <M extends Message<?, ?>> ProtoAdapter<M> get(M message) {
    return (ProtoAdapter<M>) get(message.getClass());
  }

  /** Returns the adapter for {@code type}. */
  public static <M> ProtoAdapter<M> get(Class<M> type) {
    try {
      return (ProtoAdapter<M>) type.getField("ADAPTER").get(null);
    } catch (IllegalAccessException | NoSuchFieldException e) {
      throw new IllegalArgumentException("failed to access " + type.getName() + "#ADAPTER", e);
    }
  }

  /**
   * Returns the adapter for a given {@code adapterString}, in the form
   * {@code com.squareup.wire.protos.person.Person#ADAPTER}.
   */
  public static ProtoAdapter<?> get(String adapterString) {
    return get(adapterString, ProtoAdapter.class.getClassLoader());
  }

  /**
   * Returns the adapter for a given {@code adapterString} using {@code classLoader}, in the
   * form {@code com.squareup.wire.protos.person.Person#ADAPTER}.
   */
  public static ProtoAdapter<?> get(String adapterString, ClassLoader classLoader) {
    try {
      int hash = adapterString.indexOf('#');
      String className = adapterString.substring(0, hash);
      String fieldName = adapterString.substring(hash + 1);
      return (ProtoAdapter<Object>) Class.forName(className, true, classLoader)
          .getField(fieldName).get(null);
    } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
      throw new IllegalArgumentException("failed to access " + adapterString, e);
    }
  }

  public static final ProtoAdapter<Boolean> BOOL = new BoolAdapter();
  public static final ProtoAdapter<Integer> INT32 = new Int32Adapter();
  public static final ProtoAdapter<int[]> INT32_ARRAY = new IntArrayProtoAdapter(INT32);
  public static final ProtoAdapter<Integer> UINT32 = new Uint32Adapter();
  public static final ProtoAdapter<int[]> UINT32_ARRAY = new IntArrayProtoAdapter(UINT32);
  public static final ProtoAdapter<Integer> SINT32 = new Sint32Adapter();
  public static final ProtoAdapter<int[]> SINT32_ARRAY = new IntArrayProtoAdapter(SINT32);
  public static final ProtoAdapter<Integer> FIXED32 = new Fixed32Adapter();
  public static final ProtoAdapter<int[]> FIXED32_ARRAY = new IntArrayProtoAdapter(FIXED32);
  public static final ProtoAdapter<Integer> SFIXED32 = FIXED32;
  public static final ProtoAdapter<int[]> SFIXED32_ARRAY = new IntArrayProtoAdapter(SFIXED32);
  public static final ProtoAdapter<Long> INT64 = new Int64Adapter();
  public static final ProtoAdapter<long[]> INT64_ARRAY = new LongArrayProtoAdapter(INT64);
  /**
   * Like INT64, but negative longs are interpreted as large positive values, and encoded that
   * way in JSON.
   */
  public static final ProtoAdapter<Long> UINT64 = new Uint64Adapter();
  public static final ProtoAdapter<long[]> UINT64_ARRAY = new LongArrayProtoAdapter(UINT64);
  public static final ProtoAdapter<Long> SINT64 = new Sint64Adapter();
  public static final ProtoAdapter<long[]> SINT64_ARRAY = new LongArrayProtoAdapter(SINT64);
  public static final ProtoAdapter<Long> FIXED64 = new Fixed64Adapter();
  public static final ProtoAdapter<long[]> FIXED64_ARRAY = new LongArrayProtoAdapter(FIXED64);
  public static final ProtoAdapter<Long> SFIXED64 = FIXED64;
  public static final ProtoAdapter<long[]> SFIXED64_ARRAY = new LongArrayProtoAdapter(SFIXED64);
  public static final ProtoAdapter<Float> FLOAT = new FloatProtoAdapter();
  public static final ProtoAdapter<float[]> FLOAT_ARRAY = new FloatArrayProtoAdapter(FLOAT);
  public static final ProtoAdapter<Double> DOUBLE = new DoubleProtoAdapter();
  public static final ProtoAdapter<double[]> DOUBLE_ARRAY = new DoubleArrayProtoAdapter(DOUBLE);
  public static final ProtoAdapter<ByteString> BYTES = new BytesAdapter();
  public static final ProtoAdapter<String> STRING = new StringAdapter();
  public static final ProtoAdapter<java.time.Duration> DURATION = new DurationAdapter();
  public static final ProtoAdapter<java.time.Instant> INSTANT = new InstantAdapter();
  public static final ProtoAdapter<Void> EMPTY = new EmptyAdapter();
  public static final ProtoAdapter<FieldMask> FIELD_MASK = new FieldMaskAdapter();
  public static final ProtoAdapter<Map<String, ?>> STRUCT_MAP = new StructMapAdapter();
  public static final ProtoAdapter<List<?>> STRUCT_LIST = new StructListAdapter();
  /** Upstream types this ProtoAdapter<Nothing?>; the Java-interop rendering is Void. */
  public static final ProtoAdapter<Void> STRUCT_NULL = new StructNullAdapter();
  public static final ProtoAdapter<Object> STRUCT_VALUE = new StructValueAdapter();
  @SuppressWarnings("unchecked")
  public static final ProtoAdapter<Double> DOUBLE_VALUE =
      new WrapperAdapter<Double>(DOUBLE, "type.googleapis.com/google.protobuf.DoubleValue");
  @SuppressWarnings("unchecked")
  public static final ProtoAdapter<Float> FLOAT_VALUE =
      new WrapperAdapter<Float>(FLOAT, "type.googleapis.com/google.protobuf.FloatValue");
  @SuppressWarnings("unchecked")
  public static final ProtoAdapter<Long> INT64_VALUE =
      new WrapperAdapter<Long>(INT64, "type.googleapis.com/google.protobuf.Int64Value");
  @SuppressWarnings("unchecked")
  public static final ProtoAdapter<Long> UINT64_VALUE =
      new WrapperAdapter<Long>(UINT64, "type.googleapis.com/google.protobuf.UInt64Value");
  @SuppressWarnings("unchecked")
  public static final ProtoAdapter<Integer> INT32_VALUE =
      new WrapperAdapter<Integer>(INT32, "type.googleapis.com/google.protobuf.Int32Value");
  @SuppressWarnings("unchecked")
  public static final ProtoAdapter<Integer> UINT32_VALUE =
      new WrapperAdapter<Integer>(UINT32, "type.googleapis.com/google.protobuf.UInt32Value");
  @SuppressWarnings("unchecked")
  public static final ProtoAdapter<Boolean> BOOL_VALUE =
      new WrapperAdapter<Boolean>(BOOL, "type.googleapis.com/google.protobuf.BoolValue");
  @SuppressWarnings("unchecked")
  public static final ProtoAdapter<String> STRING_VALUE =
      new WrapperAdapter<String>(STRING, "type.googleapis.com/google.protobuf.StringValue");
  @SuppressWarnings("unchecked")
  public static final ProtoAdapter<ByteString> BYTES_VALUE =
      new WrapperAdapter<ByteString>(BYTES, "type.googleapis.com/google.protobuf.BytesValue");

  static final class PackedProtoAdapter<E> extends ProtoAdapter<List<E>> {
    private final ProtoAdapter<E> originalAdapter;

    PackedProtoAdapter(ProtoAdapter<E> originalAdapter) {
      super(FieldEncoding.LENGTH_DELIMITED, List.class, null, originalAdapter.syntax,
          Collections.<E>emptyList());
      this.originalAdapter = originalAdapter;
    }

    @Override public void encodeWithTag(ProtoWriter writer, int tag, List<E> value)
        throws IOException {
      if (value != null && !value.isEmpty()) {
        super.encodeWithTag(writer, tag, value);
      }
    }

    @Override public void encodeWithTag(ReverseProtoWriter writer, int tag, List<E> value)
        throws IOException {
      if (value != null && !value.isEmpty()) {
        super.encodeWithTag(writer, tag, value);
      }
    }

    @Override public int encodedSize(List<E> value) {
      int size = 0;
      for (int i = 0; i < value.size(); i++) {
        size += originalAdapter.encodedSize(value.get(i));
      }
      return size;
    }

    @Override public int encodedSizeWithTag(int tag, List<E> value) {
      return value == null || value.isEmpty() ? 0 : super.encodedSizeWithTag(tag, value);
    }

    @Override public void encode(ProtoWriter writer, List<E> value) throws IOException {
      for (int i = 0; i < value.size(); i++) {
        originalAdapter.encode(writer, value.get(i));
      }
    }

    @Override public void encode(ReverseProtoWriter writer, List<E> value) throws IOException {
      for (int i = value.size() - 1; i >= 0; i--) {
        originalAdapter.encode(writer, value.get(i));
      }
    }

    @Override public List<E> decode(ProtoReader reader) throws IOException {
      return Collections.singletonList(originalAdapter.decode(reader));
    }

    @Override public List<E> redact(List<E> value) {
      return Collections.emptyList();
    }
  }

  static final class RepeatedProtoAdapter<E> extends ProtoAdapter<List<E>> {
    private final ProtoAdapter<E> originalAdapter;

    RepeatedProtoAdapter(ProtoAdapter<E> originalAdapter) {
      super(originalAdapter.fieldEncoding, List.class, null, originalAdapter.syntax,
          Collections.<E>emptyList());
      this.originalAdapter = originalAdapter;
    }

    @Override public int encodedSize(List<E> value) {
      throw new UnsupportedOperationException("Repeated values can only be sized with a tag.");
    }

    @Override public int encodedSizeWithTag(int tag, List<E> value) {
      if (value == null) return 0;
      int size = 0;
      for (int i = 0; i < value.size(); i++) {
        size += originalAdapter.encodedSizeWithTag(tag, value.get(i));
      }
      return size;
    }

    @Override public void encode(ProtoWriter writer, List<E> value) {
      throw new UnsupportedOperationException("Repeated values can only be encoded with a tag.");
    }

    @Override public void encode(ReverseProtoWriter writer, List<E> value) {
      throw new UnsupportedOperationException("Repeated values can only be encoded with a tag.");
    }

    @Override public void encodeWithTag(ProtoWriter writer, int tag, List<E> value)
        throws IOException {
      if (value == null) return;
      for (int i = 0; i < value.size(); i++) {
        originalAdapter.encodeWithTag(writer, tag, value.get(i));
      }
    }

    @Override public void encodeWithTag(ReverseProtoWriter writer, int tag, List<E> value)
        throws IOException {
      if (value == null) return;
      for (int i = value.size() - 1; i >= 0; i--) {
        originalAdapter.encodeWithTag(writer, tag, value.get(i));
      }
    }

    @Override public List<E> decode(ProtoReader reader) throws IOException {
      return Collections.singletonList(originalAdapter.decode(reader));
    }

    @Override public List<E> redact(List<E> value) {
      return Collections.emptyList();
    }
  }

  static final class IntArrayProtoAdapter extends ProtoAdapter<int[]> {
    private final ProtoAdapter<Integer> originalAdapter;

    IntArrayProtoAdapter(ProtoAdapter<Integer> originalAdapter) {
      super(FieldEncoding.LENGTH_DELIMITED, int[].class, null, originalAdapter.syntax,
          new int[0]);
      this.originalAdapter = originalAdapter;
    }

    @Override public void encodeWithTag(ProtoWriter writer, int tag, int[] value)
        throws IOException {
      if (value != null && value.length != 0) {
        super.encodeWithTag(writer, tag, value);
      }
    }

    @Override public void encodeWithTag(ReverseProtoWriter writer, int tag, int[] value)
        throws IOException {
      if (value != null && value.length != 0) {
        super.encodeWithTag(writer, tag, value);
      }
    }

    @Override public int encodedSize(int[] value) {
      int size = 0;
      for (int i = 0; i < value.length; i++) {
        size += originalAdapter.encodedSize(value[i]);
      }
      return size;
    }

    @Override public int encodedSizeWithTag(int tag, int[] value) {
      return value == null || value.length == 0 ? 0 : super.encodedSizeWithTag(tag, value);
    }

    @Override public void encode(ProtoWriter writer, int[] value) throws IOException {
      for (int i = 0; i < value.length; i++) {
        originalAdapter.encode(writer, value[i]);
      }
    }

    @Override public void encode(ReverseProtoWriter writer, int[] value) throws IOException {
      for (int i = value.length - 1; i >= 0; i--) {
        originalAdapter.encode(writer, value[i]);
      }
    }

    @Override public int[] decode(ProtoReader reader) throws IOException {
      return new int[] { originalAdapter.decode(reader) };
    }

    @Override public int[] redact(int[] value) {
      return new int[0];
    }
  }

  static final class LongArrayProtoAdapter extends ProtoAdapter<long[]> {
    private final ProtoAdapter<Long> originalAdapter;

    LongArrayProtoAdapter(ProtoAdapter<Long> originalAdapter) {
      super(FieldEncoding.LENGTH_DELIMITED, long[].class, null, originalAdapter.syntax,
          new long[0]);
      this.originalAdapter = originalAdapter;
    }

    @Override public void encodeWithTag(ProtoWriter writer, int tag, long[] value)
        throws IOException {
      if (value != null && value.length != 0) {
        super.encodeWithTag(writer, tag, value);
      }
    }

    @Override public void encodeWithTag(ReverseProtoWriter writer, int tag, long[] value)
        throws IOException {
      if (value != null && value.length != 0) {
        super.encodeWithTag(writer, tag, value);
      }
    }

    @Override public int encodedSize(long[] value) {
      int size = 0;
      for (int i = 0; i < value.length; i++) {
        size += originalAdapter.encodedSize(value[i]);
      }
      return size;
    }

    @Override public int encodedSizeWithTag(int tag, long[] value) {
      return value == null || value.length == 0 ? 0 : super.encodedSizeWithTag(tag, value);
    }

    @Override public void encode(ProtoWriter writer, long[] value) throws IOException {
      for (int i = 0; i < value.length; i++) {
        originalAdapter.encode(writer, value[i]);
      }
    }

    @Override public void encode(ReverseProtoWriter writer, long[] value) throws IOException {
      for (int i = value.length - 1; i >= 0; i--) {
        originalAdapter.encode(writer, value[i]);
      }
    }

    @Override public long[] decode(ProtoReader reader) throws IOException {
      return new long[] { originalAdapter.decode(reader) };
    }

    @Override public long[] redact(long[] value) {
      return new long[0];
    }
  }

  static final class FloatArrayProtoAdapter extends ProtoAdapter<float[]> {
    private final ProtoAdapter<Float> originalAdapter;

    FloatArrayProtoAdapter(ProtoAdapter<Float> originalAdapter) {
      super(FieldEncoding.LENGTH_DELIMITED, float[].class, null, originalAdapter.syntax,
          new float[0]);
      this.originalAdapter = originalAdapter;
    }

    @Override public void encodeWithTag(ProtoWriter writer, int tag, float[] value)
        throws IOException {
      if (value != null && value.length != 0) {
        super.encodeWithTag(writer, tag, value);
      }
    }

    @Override public void encodeWithTag(ReverseProtoWriter writer, int tag, float[] value)
        throws IOException {
      if (value != null && value.length != 0) {
        super.encodeWithTag(writer, tag, value);
      }
    }

    @Override public int encodedSize(float[] value) {
      int size = 0;
      for (int i = 0; i < value.length; i++) {
        size += originalAdapter.encodedSize(value[i]);
      }
      return size;
    }

    @Override public int encodedSizeWithTag(int tag, float[] value) {
      return value == null || value.length == 0 ? 0 : super.encodedSizeWithTag(tag, value);
    }

    @Override public void encode(ProtoWriter writer, float[] value) throws IOException {
      for (int i = 0; i < value.length; i++) {
        originalAdapter.encode(writer, value[i]);
      }
    }

    @Override public void encode(ReverseProtoWriter writer, float[] value) throws IOException {
      for (int i = value.length - 1; i >= 0; i--) {
        writer.writeFixed32(Float.floatToIntBits(value[i]));
      }
    }

    @Override public float[] decode(ProtoReader reader) throws IOException {
      return new float[] { Float.intBitsToFloat(reader.readFixed32()) };
    }

    @Override public float[] redact(float[] value) {
      return new float[0];
    }
  }

  static final class DoubleArrayProtoAdapter extends ProtoAdapter<double[]> {
    private final ProtoAdapter<Double> originalAdapter;

    DoubleArrayProtoAdapter(ProtoAdapter<Double> originalAdapter) {
      super(FieldEncoding.LENGTH_DELIMITED, double[].class, null, originalAdapter.syntax,
          new double[0]);
      this.originalAdapter = originalAdapter;
    }

    @Override public void encodeWithTag(ProtoWriter writer, int tag, double[] value)
        throws IOException {
      if (value != null && value.length != 0) {
        super.encodeWithTag(writer, tag, value);
      }
    }

    @Override public void encodeWithTag(ReverseProtoWriter writer, int tag, double[] value)
        throws IOException {
      if (value != null && value.length != 0) {
        super.encodeWithTag(writer, tag, value);
      }
    }

    @Override public int encodedSize(double[] value) {
      int size = 0;
      for (int i = 0; i < value.length; i++) {
        size += originalAdapter.encodedSize(value[i]);
      }
      return size;
    }

    @Override public int encodedSizeWithTag(int tag, double[] value) {
      return value == null || value.length == 0 ? 0 : super.encodedSizeWithTag(tag, value);
    }

    @Override public void encode(ProtoWriter writer, double[] value) throws IOException {
      for (int i = 0; i < value.length; i++) {
        originalAdapter.encode(writer, value[i]);
      }
    }

    @Override public void encode(ReverseProtoWriter writer, double[] value) throws IOException {
      for (int i = value.length - 1; i >= 0; i--) {
        writer.writeFixed64(Double.doubleToLongBits(value[i]));
      }
    }

    @Override public double[] decode(ProtoReader reader) throws IOException {
      return new double[] { Double.longBitsToDouble(reader.readFixed64()) };
    }

    @Override public double[] redact(double[] value) {
      return new double[0];
    }
  }

  static final class MapProtoAdapter<K, V> extends ProtoAdapter<Map<K, V>> {
    private final MapEntryProtoAdapter<K, V> entryAdapter;

    MapProtoAdapter(ProtoAdapter<K> keyAdapter, ProtoAdapter<V> valueAdapter) {
      super(FieldEncoding.LENGTH_DELIMITED, Map.class, null, valueAdapter.syntax,
          Collections.<K, V>emptyMap());
      this.entryAdapter = new MapEntryProtoAdapter<>(keyAdapter, valueAdapter);
    }

    @Override public int encodedSize(Map<K, V> value) {
      throw new UnsupportedOperationException("Repeated values can only be sized with a tag.");
    }

    @Override public int encodedSizeWithTag(int tag, Map<K, V> value) {
      if (value == null) return 0;
      int size = 0;
      for (Map.Entry<K, V> entry : value.entrySet()) {
        size += entryAdapter.encodedSizeWithTag(tag, entry);
      }
      return size;
    }

    @Override public void encode(ProtoWriter writer, Map<K, V> value) {
      throw new UnsupportedOperationException("Repeated values can only be encoded with a tag.");
    }

    @Override public void encode(ReverseProtoWriter writer, Map<K, V> value) {
      throw new UnsupportedOperationException("Repeated values can only be encoded with a tag.");
    }

    @Override public void encodeWithTag(ProtoWriter writer, int tag, Map<K, V> value)
        throws IOException {
      if (value == null) return;
      for (Map.Entry<K, V> entry : value.entrySet()) {
        entryAdapter.encodeWithTag(writer, tag, entry);
      }
    }

    @Override public void encodeWithTag(ReverseProtoWriter writer, int tag, Map<K, V> value)
        throws IOException {
      if (value == null) return;
      List<Map.Entry<K, V>> entries = new ArrayList<>(value.entrySet());
      java.util.Collections.reverse(entries);
      for (Map.Entry<K, V> entry : entries) {
        entryAdapter.encodeWithTag(writer, tag, entry);
      }
    }

    @Override public Map<K, V> decode(ProtoReader reader) throws IOException {
      K key = entryAdapter.keyAdapter.identity;
      V value = entryAdapter.valueAdapter.identity;

      long token = reader.beginMessage();
      int tag;
      while ((tag = reader.nextTag()) != -1) {
        switch (tag) {
          case 1:
            key = entryAdapter.keyAdapter.decode(reader);
            break;
          case 2:
            value = entryAdapter.valueAdapter.decode(reader);
            break;
          default:
            // Ignore unknown tags in map entries.
        }
      }
      reader.endMessageAndGetUnknownFields(token);

      if (key == null) throw new IllegalStateException("Map entry with null key");
      if (value == null) throw new IllegalStateException("Map entry with null value");
      return Collections.singletonMap(key, value);
    }

    @Override public Map<K, V> redact(Map<K, V> value) {
      return Collections.emptyMap();
    }
  }

  private static final class MapEntryProtoAdapter<K, V> extends ProtoAdapter<Map.Entry<K, V>> {
    final ProtoAdapter<K> keyAdapter;
    final ProtoAdapter<V> valueAdapter;

    MapEntryProtoAdapter(ProtoAdapter<K> keyAdapter, ProtoAdapter<V> valueAdapter) {
      super(FieldEncoding.LENGTH_DELIMITED, Map.Entry.class, null, valueAdapter.syntax, null,
          null);
      this.keyAdapter = keyAdapter;
      this.valueAdapter = valueAdapter;
    }

    @Override public int encodedSize(Map.Entry<K, V> value) {
      return keyAdapter.encodedSizeWithTag(1, value.getKey())
          + valueAdapter.encodedSizeWithTag(2, value.getValue());
    }

    @Override public void encode(ProtoWriter writer, Map.Entry<K, V> value) throws IOException {
      keyAdapter.encodeWithTag(writer, 1, value.getKey());
      valueAdapter.encodeWithTag(writer, 2, value.getValue());
    }

    @Override public void encode(ReverseProtoWriter writer, Map.Entry<K, V> value)
        throws IOException {
      valueAdapter.encodeWithTag(writer, 2, value.getValue());
      keyAdapter.encodeWithTag(writer, 1, value.getKey());
    }

    @Override public Map.Entry<K, V> decode(ProtoReader reader) {
      throw new UnsupportedOperationException();
    }

    @Override public Map.Entry<K, V> redact(Map.Entry<K, V> value) {
      throw new UnsupportedOperationException();
    }
  }

  static final class FloatProtoAdapter extends ProtoAdapter<Float> {
    FloatProtoAdapter() {
      super(FieldEncoding.FIXED32, Float.class, null, Syntax.PROTO_2, 0.0f);
    }

    @Override public void encode(ProtoWriter writer, Float value) throws IOException {
      writer.writeFixed32(Float.floatToIntBits(value));
    }

    @Override public void encode(ReverseProtoWriter writer, Float value) throws IOException {
      writer.writeFixed32(Float.floatToIntBits(value));
    }

    @Override public Float decode(ProtoReader reader) throws IOException {
      return Float.intBitsToFloat(reader.readFixed32());
    }

    @Override public int encodedSize(Float value) {
      return 4;
    }

    @Override public Float redact(Float value) {
      throw new UnsupportedOperationException();
    }
  }

  static final class DoubleProtoAdapter extends ProtoAdapter<Double> {
    DoubleProtoAdapter() {
      super(FieldEncoding.FIXED64, Double.class, null, Syntax.PROTO_2, 0.0);
    }

    @Override public int encodedSize(Double value) {
      return 8;
    }

    @Override public void encode(ProtoWriter writer, Double value) throws IOException {
      writer.writeFixed64(Double.doubleToLongBits(value));
    }

    @Override public void encode(ReverseProtoWriter writer, Double value) throws IOException {
      writer.writeFixed64(Double.doubleToLongBits(value));
    }

    @Override public Double decode(ProtoReader reader) throws IOException {
      return Double.longBitsToDouble(reader.readFixed64());
    }

    @Override public Double redact(Double value) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class BoolAdapter extends ProtoAdapter<Boolean> {
    BoolAdapter() {
      super(FieldEncoding.VARINT, Boolean.class, null, Syntax.PROTO_2, false);
    }

    @Override public int encodedSize(Boolean value) {
      return 1;
    }

    @Override public void encode(ProtoWriter writer, Boolean value) throws IOException {
      writer.writeVarint32(value ? 1 : 0);
    }

    @Override public void encode(ReverseProtoWriter writer, Boolean value) throws IOException {
      writer.writeVarint32(value ? 1 : 0);
    }

    @Override public Boolean decode(ProtoReader reader) throws IOException {
      return reader.readVarint32() != 0; // We are lenient to match protoc behavior.
    }

    @Override public Boolean redact(Boolean value) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class Int32Adapter extends ProtoAdapter<Integer> {
    Int32Adapter() {
      super(FieldEncoding.VARINT, Integer.class, null, Syntax.PROTO_2, 0);
    }

    @Override public int encodedSize(Integer value) {
      return ProtoWriter.int32Size(value);
    }

    @Override public void encode(ProtoWriter writer, Integer value) throws IOException {
      writer.writeSignedVarint32(value);
    }

    @Override public void encode(ReverseProtoWriter writer, Integer value) throws IOException {
      writer.writeSignedVarint32(value);
    }

    @Override public Integer decode(ProtoReader reader) throws IOException {
      return reader.readVarint32();
    }

    @Override public Integer redact(Integer value) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class Uint32Adapter extends ProtoAdapter<Integer> {
    Uint32Adapter() {
      super(FieldEncoding.VARINT, Integer.class, null, Syntax.PROTO_2, 0);
    }

    @Override public int encodedSize(Integer value) {
      return ProtoWriter.varint32Size(value);
    }

    @Override public void encode(ProtoWriter writer, Integer value) throws IOException {
      writer.writeVarint32(value);
    }

    @Override public void encode(ReverseProtoWriter writer, Integer value) throws IOException {
      writer.writeVarint32(value);
    }

    @Override public Integer decode(ProtoReader reader) throws IOException {
      return reader.readVarint32();
    }

    @Override public Integer redact(Integer value) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class Sint32Adapter extends ProtoAdapter<Integer> {
    Sint32Adapter() {
      super(FieldEncoding.VARINT, Integer.class, null, Syntax.PROTO_2, 0);
    }

    @Override public int encodedSize(Integer value) {
      return ProtoWriter.varint32Size(ProtoWriter.encodeZigZag32(value));
    }

    @Override public void encode(ProtoWriter writer, Integer value) throws IOException {
      writer.writeVarint32(ProtoWriter.encodeZigZag32(value));
    }

    @Override public void encode(ReverseProtoWriter writer, Integer value) throws IOException {
      writer.writeVarint32(ProtoWriter.encodeZigZag32(value));
    }

    @Override public Integer decode(ProtoReader reader) throws IOException {
      return ProtoWriter.decodeZigZag32(reader.readVarint32());
    }

    @Override public Integer redact(Integer value) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class Fixed32Adapter extends ProtoAdapter<Integer> {
    Fixed32Adapter() {
      super(FieldEncoding.FIXED32, Integer.class, null, Syntax.PROTO_2, 0);
    }

    @Override public int encodedSize(Integer value) {
      return 4;
    }

    @Override public void encode(ProtoWriter writer, Integer value) throws IOException {
      writer.writeFixed32(value);
    }

    @Override public void encode(ReverseProtoWriter writer, Integer value) throws IOException {
      writer.writeFixed32(value);
    }

    @Override public Integer decode(ProtoReader reader) throws IOException {
      return reader.readFixed32();
    }

    @Override public Integer redact(Integer value) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class Int64Adapter extends ProtoAdapter<Long> {
    Int64Adapter() {
      super(FieldEncoding.VARINT, Long.class, null, Syntax.PROTO_2, 0L);
    }

    @Override public int encodedSize(Long value) {
      return ProtoWriter.varint64Size(value);
    }

    @Override public void encode(ProtoWriter writer, Long value) throws IOException {
      writer.writeVarint64(value);
    }

    @Override public void encode(ReverseProtoWriter writer, Long value) throws IOException {
      writer.writeVarint64(value);
    }

    @Override public Long decode(ProtoReader reader) throws IOException {
      return reader.readVarint64();
    }

    @Override public Long redact(Long value) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class Uint64Adapter extends ProtoAdapter<Long> {
    Uint64Adapter() {
      super(FieldEncoding.VARINT, Long.class, null, Syntax.PROTO_2, 0L);
    }

    @Override public int encodedSize(Long value) {
      return ProtoWriter.varint64Size(value);
    }

    @Override public void encode(ProtoWriter writer, Long value) throws IOException {
      writer.writeVarint64(value);
    }

    @Override public void encode(ReverseProtoWriter writer, Long value) throws IOException {
      writer.writeVarint64(value);
    }

    @Override public Long decode(ProtoReader reader) throws IOException {
      return reader.readVarint64();
    }

    @Override public Long redact(Long value) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class Sint64Adapter extends ProtoAdapter<Long> {
    Sint64Adapter() {
      super(FieldEncoding.VARINT, Long.class, null, Syntax.PROTO_2, 0L);
    }

    @Override public int encodedSize(Long value) {
      return ProtoWriter.varint64Size(ProtoWriter.encodeZigZag64(value));
    }

    @Override public void encode(ProtoWriter writer, Long value) throws IOException {
      writer.writeVarint64(ProtoWriter.encodeZigZag64(value));
    }

    @Override public void encode(ReverseProtoWriter writer, Long value) throws IOException {
      writer.writeVarint64(ProtoWriter.encodeZigZag64(value));
    }

    @Override public Long decode(ProtoReader reader) throws IOException {
      return ProtoWriter.decodeZigZag64(reader.readVarint64());
    }

    @Override public Long redact(Long value) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class Fixed64Adapter extends ProtoAdapter<Long> {
    Fixed64Adapter() {
      super(FieldEncoding.FIXED64, Long.class, null, Syntax.PROTO_2, 0L);
    }

    @Override public int encodedSize(Long value) {
      return 8;
    }

    @Override public void encode(ProtoWriter writer, Long value) throws IOException {
      writer.writeFixed64(value);
    }

    @Override public void encode(ReverseProtoWriter writer, Long value) throws IOException {
      writer.writeFixed64(value);
    }

    @Override public Long decode(ProtoReader reader) throws IOException {
      return reader.readFixed64();
    }

    @Override public Long redact(Long value) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class StringAdapter extends ProtoAdapter<String> {
    StringAdapter() {
      super(FieldEncoding.LENGTH_DELIMITED, String.class, null, Syntax.PROTO_2, "");
    }

    @Override public int encodedSize(String value) {
      return (int) okio.Utf8.size(value);
    }

    @Override public void encode(ProtoWriter writer, String value) throws IOException {
      writer.writeString(value);
    }

    @Override public void encode(ReverseProtoWriter writer, String value) throws IOException {
      writer.writeString(value);
    }

    @Override public String decode(ProtoReader reader) throws IOException {
      return reader.readString();
    }

    @Override public String redact(String value) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class BytesAdapter extends ProtoAdapter<ByteString> {
    BytesAdapter() {
      super(FieldEncoding.LENGTH_DELIMITED, ByteString.class, null, Syntax.PROTO_2,
          ByteString.EMPTY);
    }

    @Override public int encodedSize(ByteString value) {
      return value.size();
    }

    @Override public void encode(ProtoWriter writer, ByteString value) throws IOException {
      writer.writeBytes(value);
    }

    @Override public void encode(ReverseProtoWriter writer, ByteString value) throws IOException {
      writer.writeBytes(value);
    }

    @Override public ByteString decode(ProtoReader reader) throws IOException {
      return reader.readBytes();
    }

    @Override public ByteString redact(ByteString value) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class DurationAdapter extends ProtoAdapter<java.time.Duration> {
    DurationAdapter() {
      super(FieldEncoding.LENGTH_DELIMITED, java.time.Duration.class,
          "type.googleapis.com/google.protobuf.Duration", Syntax.PROTO_3, null, null);
    }

    @Override public int encodedSize(java.time.Duration value) {
      int result = 0;
      long seconds = sameSignSeconds(value);
      if (seconds != 0L) result += INT64.encodedSizeWithTag(1, seconds);
      int nanos = sameSignNanos(value);
      if (nanos != 0) result += INT32.encodedSizeWithTag(2, nanos);
      return result;
    }

    @Override public void encode(ProtoWriter writer, java.time.Duration value)
        throws IOException {
      long seconds = sameSignSeconds(value);
      if (seconds != 0L) INT64.encodeWithTag(writer, 1, seconds);
      int nanos = sameSignNanos(value);
      if (nanos != 0) INT32.encodeWithTag(writer, 2, nanos);
    }

    @Override public void encode(ReverseProtoWriter writer, java.time.Duration value)
        throws IOException {
      int nanos = sameSignNanos(value);
      if (nanos != 0) INT32.encodeWithTag(writer, 2, nanos);
      long seconds = sameSignSeconds(value);
      if (seconds != 0L) INT64.encodeWithTag(writer, 1, seconds);
    }

    @Override public java.time.Duration decode(ProtoReader reader) throws IOException {
      long seconds = 0L;
      int nanos = 0;
      long token = reader.beginMessage();
      int tag;
      while ((tag = reader.nextTag()) != -1) {
        switch (tag) {
          case 1:
            seconds = INT64.decode(reader);
            break;
          case 2:
            nanos = INT32.decode(reader);
            break;
          default:
            reader.readUnknownField(tag);
        }
      }
      reader.endMessageAndGetUnknownFields(token);
      return java.time.Duration.ofSeconds(seconds, nanos);
    }

    @Override public java.time.Duration redact(java.time.Duration value) {
      return value;
    }

    /**
     * Returns a value like 1 for 1.200s and -1 for -1.200s; unlike {@code getSeconds()}, which
     * floors negative values.
     */
    private static long sameSignSeconds(java.time.Duration value) {
      return value.getSeconds() < 0L && value.getNano() != 0
          ? value.getSeconds() + 1L
          : value.getSeconds();
    }

    /**
     * Returns a value like 200_000_000 for 1.200s and -200_000_000 for -1.200s; unlike
     * {@code getNano()}, which is positive when seconds is negative.
     */
    private static int sameSignNanos(java.time.Duration value) {
      return value.getSeconds() < 0L && value.getNano() != 0
          ? value.getNano() - 1_000_000_000
          : value.getNano();
    }
  }

  // Protobuf Timestamp valid range: 0001-01-01T00:00:00Z to 9999-12-31T23:59:59.999999999Z.
  private static final long TIMESTAMP_SECONDS_MIN = -62135596800L;
  private static final long TIMESTAMP_SECONDS_MAX = 253402300799L;

  private static void checkTimestampRange(long seconds, int nanos) {
    if (seconds < TIMESTAMP_SECONDS_MIN || seconds > TIMESTAMP_SECONDS_MAX) {
      throw new IllegalArgumentException(
          "Timestamp seconds (" + seconds + ") must be in range [" + TIMESTAMP_SECONDS_MIN
              + ", " + TIMESTAMP_SECONDS_MAX + "]");
    }
    if (nanos < 0 || nanos > 999_999_999) {
      throw new IllegalArgumentException(
          "Timestamp nanos (" + nanos + ") must be in range [0, 999999999]");
    }
  }

  private static final class InstantAdapter extends ProtoAdapter<java.time.Instant> {
    InstantAdapter() {
      super(FieldEncoding.LENGTH_DELIMITED, java.time.Instant.class,
          "type.googleapis.com/google.protobuf.Timestamp", Syntax.PROTO_3, null, null);
    }

    @Override public int encodedSize(java.time.Instant value) {
      long seconds = value.getEpochSecond();
      int nanos = value.getNano();
      checkTimestampRange(seconds, nanos);
      int result = 0;
      if (seconds != 0L) result += INT64.encodedSizeWithTag(1, seconds);
      if (nanos != 0) result += INT32.encodedSizeWithTag(2, nanos);
      return result;
    }

    @Override public void encode(ProtoWriter writer, java.time.Instant value)
        throws IOException {
      long seconds = value.getEpochSecond();
      int nanos = value.getNano();
      checkTimestampRange(seconds, nanos);
      if (seconds != 0L) INT64.encodeWithTag(writer, 1, seconds);
      if (nanos != 0) INT32.encodeWithTag(writer, 2, nanos);
    }

    @Override public void encode(ReverseProtoWriter writer, java.time.Instant value)
        throws IOException {
      long seconds = value.getEpochSecond();
      int nanos = value.getNano();
      checkTimestampRange(seconds, nanos);
      if (nanos != 0) INT32.encodeWithTag(writer, 2, nanos);
      if (seconds != 0L) INT64.encodeWithTag(writer, 1, seconds);
    }

    @Override public java.time.Instant decode(ProtoReader reader) throws IOException {
      long seconds = 0L;
      int nanos = 0;
      long token = reader.beginMessage();
      int tag;
      while ((tag = reader.nextTag()) != -1) {
        switch (tag) {
          case 1:
            seconds = INT64.decode(reader);
            break;
          case 2:
            nanos = INT32.decode(reader);
            break;
          default:
            reader.readUnknownField(tag);
        }
      }
      reader.endMessageAndGetUnknownFields(token);
      return java.time.Instant.ofEpochSecond(seconds, nanos);
    }

    @Override public java.time.Instant redact(java.time.Instant value) {
      return value;
    }
  }

  private static final class EmptyAdapter extends ProtoAdapter<Void> {
    EmptyAdapter() {
      super(FieldEncoding.LENGTH_DELIMITED, Void.class,
          "type.googleapis.com/google.protobuf.Empty", Syntax.PROTO_3, null, null);
    }

    @Override public int encodedSize(Void value) {
      return 0;
    }

    @Override public void encode(ProtoWriter writer, Void value) {
    }

    @Override public void encode(ReverseProtoWriter writer, Void value) {
    }

    @Override public Void decode(ProtoReader reader) throws IOException {
      long token = reader.beginMessage();
      int tag;
      while ((tag = reader.nextTag()) != -1) {
        reader.readUnknownField(tag);
      }
      reader.endMessageAndGetUnknownFields(token);
      return null;
    }

    @Override public Void redact(Void value) {
      return value;
    }
  }

  private static final class FieldMaskAdapter extends ProtoAdapter<FieldMask> {
    FieldMaskAdapter() {
      super(FieldEncoding.LENGTH_DELIMITED, FieldMask.class,
          "type.googleapis.com/google.protobuf.FieldMask", Syntax.PROTO_3, null, null);
    }

    @Override public int encodedSize(FieldMask value) {
      int result = 0;
      for (String path : value.paths()) {
        result += STRING.encodedSizeWithTag(1, path);
      }
      return result;
    }

    @Override public void encode(ProtoWriter writer, FieldMask value) throws IOException {
      for (String path : value.paths()) {
        STRING.encodeWithTag(writer, 1, path);
      }
    }

    @Override public void encode(ReverseProtoWriter writer, FieldMask value) throws IOException {
      List<String> paths = value.paths();
      for (int i = paths.size() - 1; i >= 0; i--) {
        STRING.encodeWithTag(writer, 1, paths.get(i));
      }
    }

    @Override public FieldMask decode(ProtoReader reader) throws IOException {
      List<String> paths = new ArrayList<>();
      long token = reader.beginMessage();
      int tag;
      while ((tag = reader.nextTag()) != -1) {
        switch (tag) {
          case 1:
            paths.add(STRING.decode(reader));
            break;
          default:
            reader.readUnknownField(tag);
        }
      }
      reader.endMessageAndGetUnknownFields(token);
      return new FieldMask(paths);
    }

    @Override public FieldMask redact(FieldMask value) {
      return value;
    }
  }

  private static final class StructMapAdapter extends ProtoAdapter<Map<String, ?>> {
    StructMapAdapter() {
      super(FieldEncoding.LENGTH_DELIMITED, Map.class,
          "type.googleapis.com/google.protobuf.Struct", Syntax.PROTO_3, null, null);
    }

    @Override public int encodedSize(Map<String, ?> value) {
      if (value == null) return 0;
      int size = 0;
      for (Map.Entry<String, ?> entry : value.entrySet()) {
        int entrySize = STRING.encodedSizeWithTag(1, entry.getKey())
            + STRUCT_VALUE.encodedSizeWithTag(2, entry.getValue());
        size += ProtoWriter.tagSize(1) + ProtoWriter.varint32Size(entrySize) + entrySize;
      }
      return size;
    }

    @Override public void encode(ProtoWriter writer, Map<String, ?> value) throws IOException {
      if (value == null) return;
      for (Map.Entry<String, ?> entry : value.entrySet()) {
        int entrySize = STRING.encodedSizeWithTag(1, entry.getKey())
            + STRUCT_VALUE.encodedSizeWithTag(2, entry.getValue());
        writer.writeTag(1, FieldEncoding.LENGTH_DELIMITED);
        writer.writeVarint32(entrySize);
        STRING.encodeWithTag(writer, 1, entry.getKey());
        STRUCT_VALUE.encodeWithTag(writer, 2, entry.getValue());
      }
    }

    @Override public void encode(ReverseProtoWriter writer, Map<String, ?> value)
        throws IOException {
      if (value == null) return;
      List<Map.Entry<String, ?>> entries = new ArrayList<Map.Entry<String, ?>>(value.entrySet());
      java.util.Collections.reverse(entries);
      for (Map.Entry<String, ?> entry : entries) {
        int byteCountBefore = writer.byteCount();
        STRUCT_VALUE.encodeWithTag(writer, 2, entry.getValue());
        STRING.encodeWithTag(writer, 1, entry.getKey());
        writer.writeVarint32(writer.byteCount() - byteCountBefore);
        writer.writeTag(1, FieldEncoding.LENGTH_DELIMITED);
      }
    }

    @Override public Map<String, ?> decode(ProtoReader reader) throws IOException {
      java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
      long entryToken = reader.beginMessage();
      int entryTag;
      while ((entryTag = reader.nextTag()) != -1) {
        if (entryTag != 1) {
          reader.skip();
          continue;
        }
        String key = null;
        Object value = null;
        long token = reader.beginMessage();
        int tag;
        while ((tag = reader.nextTag()) != -1) {
          switch (tag) {
            case 1:
              key = STRING.decode(reader);
              break;
            case 2:
              value = STRUCT_VALUE.decode(reader);
              break;
            default:
              reader.readUnknownField(tag);
          }
        }
        reader.endMessageAndGetUnknownFields(token);
        if (key != null) result.put(key, value);
      }
      reader.endMessageAndGetUnknownFields(entryToken);
      return result;
    }

    @Override public Map<String, ?> redact(Map<String, ?> value) {
      if (value == null) return null;
      // Upstream is `value?.mapValues { STRUCT_VALUE.redact(it) }`: `it` is the Map.Entry and
      // Kotlin passes it without re-checking the key's type, so any non-empty map throws
      // IllegalArgumentException("unexpected struct value: ...") from the value dispatch
      // before the key matters. Verified against the pinned 7.1.0 artifact (TASK-15 finding);
      // the quirk is preserved, which is why the loop iterates the raw map.
      java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : value.entrySet()) {
        // Always throws for a Map.Entry; kept in the mapValues shape to mirror upstream.
        STRUCT_VALUE.redact(entry);
      }
      return result;
    }
  }

  private static final class StructListAdapter extends ProtoAdapter<List<?>> {
    StructListAdapter() {
      // Upstream passes Map::class here (not List::class); the quirk is preserved so
      // adapter.type matches upstream, and this comment records the non-obvious choice.
      super(FieldEncoding.LENGTH_DELIMITED, Map.class,
          "type.googleapis.com/google.protobuf.ListValue", Syntax.PROTO_3, null, null);
    }

    @Override public int encodedSize(List<?> value) {
      if (value == null) return 0;
      int result = 0;
      for (Object v : value) {
        result += STRUCT_VALUE.encodedSizeWithTag(1, v);
      }
      return result;
    }

    @Override public void encode(ProtoWriter writer, List<?> value) throws IOException {
      if (value == null) return;
      for (Object v : value) {
        STRUCT_VALUE.encodeWithTag(writer, 1, v);
      }
    }

    @Override public void encode(ReverseProtoWriter writer, List<?> value) throws IOException {
      if (value == null) return;
      for (int i = value.size() - 1; i >= 0; i--) {
        STRUCT_VALUE.encodeWithTag(writer, 1, value.get(i));
      }
    }

    @Override public List<?> decode(ProtoReader reader) throws IOException {
      List<Object> result = new ArrayList<>();
      long token = reader.beginMessage();
      int entryTag;
      while ((entryTag = reader.nextTag()) != -1) {
        if (entryTag != 1) {
          reader.skip();
          continue;
        }
        result.add(STRUCT_VALUE.decode(reader));
      }
      reader.endMessageAndGetUnknownFields(token);
      return result;
    }

    @Override public List<?> redact(List<?> value) {
      if (value == null) return null;
      List<Object> result = new ArrayList<>();
      for (Object v : value) {
        result.add(STRUCT_VALUE.redact(v));
      }
      return result;
    }
  }

  @SuppressWarnings("rawtypes")
  private static final class StructNullAdapter extends ProtoAdapter<Void> {
    StructNullAdapter() {
      super(FieldEncoding.VARINT, Void.class,
          "type.googleapis.com/google.protobuf.NullValue", Syntax.PROTO_3, null, null);
    }

    @Override public int encodedSize(Void value) {
      return ProtoWriter.varint32Size(0);
    }

    @Override public int encodedSizeWithTag(int tag, Void value) {
      int size = encodedSize(value);
      return ProtoWriter.tagSize(tag) + ProtoWriter.varint32Size(size);
    }

    @Override public void encode(ProtoWriter writer, Void value) throws IOException {
      writer.writeVarint32(0);
    }

    @Override public void encode(ReverseProtoWriter writer, Void value) throws IOException {
      writer.writeVarint32(0);
    }

    @Override public void encodeWithTag(ProtoWriter writer, int tag, Void value)
        throws IOException {
      writer.writeTag(tag, fieldEncoding);
      encode(writer, value);
    }

    @Override public void encodeWithTag(ReverseProtoWriter writer, int tag, Void value)
        throws IOException {
      encode(writer, value);
      writer.writeTag(tag, fieldEncoding);
    }

    @Override public Void decode(ProtoReader reader) throws IOException {
      int value = reader.readVarint32();
      if (value != 0) throw new IOException("expected 0 but was " + value);
      return null;
    }

    @Override public Void redact(Void value) {
      return null;
    }
  }

  @SuppressWarnings("rawtypes")
  private static final class StructValueAdapter extends ProtoAdapter<Object> {
    StructValueAdapter() {
      super(FieldEncoding.LENGTH_DELIMITED, Object.class,
          "type.googleapis.com/google.protobuf.Value", Syntax.PROTO_3, null, null);
    }

    private static int dispatchEncodedSize(Object value) {
      if (value == null) return STRUCT_NULL.encodedSizeWithTag(1, null);
      if (value instanceof Number) return DOUBLE.encodedSizeWithTag(2, ((Number) value).doubleValue());
      if (value instanceof String) return STRING.encodedSizeWithTag(3, (String) value);
      if (value instanceof Boolean) return BOOL.encodedSizeWithTag(4, (Boolean) value);
      if (value instanceof Map) {
        return STRUCT_MAP.encodedSizeWithTag(5, (Map<String, ?>) value);
      }
      if (value instanceof List) return STRUCT_LIST.encodedSizeWithTag(6, (List<?>) value);
      throw new IllegalArgumentException("unexpected struct value: " + value);
    }

    private static void dispatchEncode(ProtoWriter writer, Object value) throws IOException {
      if (value == null) {
        STRUCT_NULL.encodeWithTag(writer, 1, null);
      } else if (value instanceof Number) {
        DOUBLE.encodeWithTag(writer, 2, ((Number) value).doubleValue());
      } else if (value instanceof String) {
        STRING.encodeWithTag(writer, 3, (String) value);
      } else if (value instanceof Boolean) {
        BOOL.encodeWithTag(writer, 4, (Boolean) value);
      } else if (value instanceof Map) {
        STRUCT_MAP.encodeWithTag(writer, 5, (Map<String, ?>) value);
      } else if (value instanceof List) {
        STRUCT_LIST.encodeWithTag(writer, 6, (List<?>) value);
      } else {
        throw new IllegalArgumentException("unexpected struct value: " + value);
      }
    }

    private static void dispatchEncode(ReverseProtoWriter writer, Object value)
        throws IOException {
      if (value == null) {
        STRUCT_NULL.encodeWithTag(writer, 1, null);
      } else if (value instanceof Number) {
        DOUBLE.encodeWithTag(writer, 2, ((Number) value).doubleValue());
      } else if (value instanceof String) {
        STRING.encodeWithTag(writer, 3, (String) value);
      } else if (value instanceof Boolean) {
        BOOL.encodeWithTag(writer, 4, (Boolean) value);
      } else if (value instanceof Map) {
        STRUCT_MAP.encodeWithTag(writer, 5, (Map<String, ?>) value);
      } else if (value instanceof List) {
        STRUCT_LIST.encodeWithTag(writer, 6, (List<?>) value);
      } else {
        throw new IllegalArgumentException("unexpected struct value: " + value);
      }
    }

    @Override public int encodedSize(Object value) {
      return dispatchEncodedSize(value);
    }

    @Override public int encodedSizeWithTag(int tag, Object value) {
      if (value == null) {
        int size = encodedSize(value);
        return ProtoWriter.tagSize(tag) + ProtoWriter.varint32Size(size) + size;
      }
      return super.encodedSizeWithTag(tag, value);
    }

    @Override public void encode(ProtoWriter writer, Object value) throws IOException {
      dispatchEncode(writer, value);
    }

    @Override public void encode(ReverseProtoWriter writer, Object value) throws IOException {
      dispatchEncode(writer, value);
    }

    @Override public void encodeWithTag(ProtoWriter writer, int tag, Object value)
        throws IOException {
      if (value == null) {
        writer.writeTag(tag, fieldEncoding);
        writer.writeVarint32(encodedSize(value));
        encode(writer, value);
      } else {
        super.encodeWithTag(writer, tag, value);
      }
    }

    @Override public void encodeWithTag(ReverseProtoWriter writer, int tag, Object value)
        throws IOException {
      if (value == null) {
        int byteCountBefore = writer.byteCount();
        encode(writer, value);
        writer.writeVarint32(writer.byteCount() - byteCountBefore);
        writer.writeTag(tag, fieldEncoding);
      } else {
        super.encodeWithTag(writer, tag, value);
      }
    }

    @Override public Object decode(ProtoReader reader) throws IOException {
      Object result = null;
      long token = reader.beginMessage();
      int tag;
      while ((tag = reader.nextTag()) != -1) {
        switch (tag) {
          case 1:
            result = STRUCT_NULL.decode(reader);
            break;
          case 2:
            result = DOUBLE.decode(reader);
            break;
          case 3:
            result = STRING.decode(reader);
            break;
          case 4:
            result = BOOL.decode(reader);
            break;
          case 5:
            result = STRUCT_MAP.decode(reader);
            break;
          case 6:
            result = STRUCT_LIST.decode(reader);
            break;
          default:
            reader.skip();
        }
      }
      reader.endMessageAndGetUnknownFields(token);
      return result;
    }

    @Override public Object redact(Object value) {
      if (value == null) return STRUCT_NULL.redact(null);
      if (value instanceof Number) return value;
      if (value instanceof String) return null;
      if (value instanceof Boolean) return value;
      if (value instanceof Map) return STRUCT_MAP.redact((Map<String, ?>) value);
      if (value instanceof List) return STRUCT_LIST.redact((List<?>) value);
      throw new IllegalArgumentException("unexpected struct value: " + value);
    }
  }

  /**
   * Wire implements scalar wrapper types as nullable scalar ones. Protoc omits both null wrappers
   * and wrappers whose scalar value is the identity value of the scalar type.
   */
  static final class WrapperAdapter<T> extends ProtoAdapter<T> {
    private final ProtoAdapter<T> delegate;

    WrapperAdapter(ProtoAdapter<T> delegate, String typeUrl) {
      super(FieldEncoding.LENGTH_DELIMITED, delegate.type, typeUrl, Syntax.PROTO_3,
          delegate.identity, null);
      this.delegate = delegate;
    }

    @Override public int encodedSize(T value) {
      if (value == null || areEqual(value, delegate.identity)) return 0;
      return delegate.encodedSizeWithTag(1, value);
    }

    private static boolean areEqual(Object a, Object b) {
      return a == b || (a != null && b != null && a.equals(b));
    }

    @Override public void encode(ProtoWriter writer, T value) throws IOException {
      if (value != null && !areEqual(value, delegate.identity)) {
        delegate.encodeWithTag(writer, 1, value);
      }
    }

    @Override public void encode(ReverseProtoWriter writer, T value) throws IOException {
      if (value != null && !areEqual(value, delegate.identity)) {
        delegate.encodeWithTag(writer, 1, value);
      }
    }

    @Override public T decode(ProtoReader reader) throws IOException {
      T result = delegate.identity;
      long token = reader.beginMessage();
      int tag;
      while ((tag = reader.nextTag()) != -1) {
        switch (tag) {
          case 1:
            result = delegate.decode(reader);
            break;
          default:
            reader.readUnknownField(tag);
        }
      }
      reader.endMessageAndGetUnknownFields(token);
      return result;
    }

    @Override public T redact(T value) {
      if (value == null) return null;
      return delegate.redact(value);
    }
  }
}
