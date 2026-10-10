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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * TASK-33.3: upstream Wire is Kotlin and declares a checked exception only where a function carries
 * {@code @Throws}. This pins both halves of that contract on the port: top-level convenience
 * methods that upstream declares without {@code @Throws} surface an in-memory or user-adapter
 * failure as {@link UncheckedIOException} with the original as cause, while the methods upstream
 * does declare ({@code decode}, {@code encode(sink, value)}, the reader family) keep
 * {@code throws IOException} and let a raw {@link IOException} through.
 */
public class UncheckedExceptionContractTest {
  private static final IOException BOOM = new IOException("boom");

  /** An adapter whose encode and decode always fail with {@link #BOOM}. */
  private static final ProtoAdapter<String> FAILING = new ProtoAdapter<String>(
      FieldEncoding.LENGTH_DELIMITED, String.class, "type.googleapis.com/test.Failing",
      Syntax.PROTO_2, null, null) {
    @Override public int encodedSize(String value) {
      return 1;
    }

    @Override public void encode(ProtoWriter writer, String value) throws IOException {
      throw BOOM;
    }

    @Override public void encode(ReverseProtoWriter writer, String value) throws IOException {
      throw BOOM;
    }

    @Override public String decode(ProtoReader reader) throws IOException {
      throw BOOM;
    }

    @Override public String redact(String value) {
      return value;
    }
  };

  @Test public void encodeToByteArrayWrapsTheAdapterFailure() {
    assertWrapsBoom(() -> FAILING.encode("x"));
  }

  @SuppressWarnings("deprecation") // the okio-typed bridge is part of the surface under test
  @Test public void encodeByteStringWrapsTheAdapterFailure() {
    assertWrapsBoom(() -> FAILING.encodeByteString("x"));
  }

  @Test public void messageEncodeWrapsTheAdapterFailure() {
    assertWrapsBoom(() -> new FailingMessage().encode());
  }

  @Test public void anyMessageUnpackWrapsTheAdapterFailure() {
    AnyMessage any = new AnyMessage(FAILING.getTypeUrl(), okio.ByteString.of((byte) 1));
    assertWrapsBoom(() -> any.unpack(FAILING));
    assertWrapsBoom(() -> any.unpackOrNull(FAILING));
  }

  @Test public void anyMessagePackWrapsTheAdapterFailure() {
    assertWrapsBoom(() -> AnyMessage.pack(FAILING, "x"));
  }

  private static void assertWrapsBoom(org.junit.jupiter.api.function.Executable call) {
    UncheckedIOException e = assertThrows(UncheckedIOException.class, call);
    assertSame(BOOM, e.getCause());
  }

  @Test public void declaredDecodeLetsTheRawIoExceptionThrough() {
    IOException e = assertThrows(IOException.class, () -> FAILING.decode(new byte[] {1, 0}));
    assertSame(BOOM, e);
  }

  @Test public void readerFailuresInsideDecodeStayRawIoExceptions() {
    // A truncated length-delimited value: the reader reports EOFException, which must reach the
    // caller of the declared decode() unwrapped (upstream's ParseTest and ProtoReaderTest pin it).
    // assertThrows(IOException.class, ...) fails on an UncheckedIOException, so this proves the
    // failure is not wrapped.
    IOException e = assertThrows(IOException.class, () -> ProtoAdapter.STRING.decode(new byte[] {0x05, 'a'}));
    assertSame(java.io.EOFException.class, e.getClass());
  }

  /** writeReplace declares ObjectStreamException upstream: a failing encode must stay that type. */
  @Test public void javaSerializationOfAFailingMessageStaysAnObjectStreamException() {
    java.io.ObjectStreamException e = assertThrows(java.io.ObjectStreamException.class,
        () -> new java.io.ObjectOutputStream(new java.io.ByteArrayOutputStream())
            .writeObject(new FailingMessage()));
    assertTrue(e instanceof java.io.InvalidObjectException);
    assertSame(BOOM, e.getCause());
  }

  @Test public void methodsUpstreamDeclaresWithThrowsKeepTheCheckedException() throws Exception {
    // Upstream's @Throws(IOException::class), verified against the 7.1.0 jar with javap:
    assertDeclaresIoException(ProtoAdapter.class, "decode", ProtoReader.class);
    assertDeclaresIoException(ProtoAdapter.class, "encode", ProtoWriter.class, Object.class);
    assertDeclaresIoException(ProtoReader.class, "nextTag");
    assertDeclaresIoException(ProtoReader.class, "readVarint32");
    assertDeclaresIoException(ProtoWriter.class, "writeVarint32", int.class);
    assertDeclaresIoException(ReverseProtoWriter.class, "writeTo", okio.BufferedSink.class);
    assertDeclaresIoException(EnumAdapter.class, "decode", ProtoReader.class);
  }

  @Test public void methodsUpstreamDeclaresWithoutThrowsDeclareNoCheckedException() throws Exception {
    assertDeclaresNothing(Message.class, "encode");
    assertDeclaresNothing(Message.class, "encodeByteString");
    assertDeclaresNothing(ProtoAdapter.class, "encode", Object.class);
    assertDeclaresNothing(AnyMessage.class, "unpack", ProtoAdapter.class);
    assertDeclaresNothing(AnyMessage.class, "unpackOrNull", ProtoAdapter.class);
    assertDeclaresNothing(OneOf.class, "encodeWithTag", ProtoWriter.class);
    assertDeclaresNothing(ProtoReader.class, "readUnknownField", int.class);
    assertDeclaresNothing(ProtoReader.class, "addUnknownField", int.class, FieldEncoding.class, Object.class);
    assertDeclaresNothing(ReverseProtoWriter.class, "writeVarint32", int.class);
    assertDeclaresNothing(ReverseProtoWriter.class, "writeString", String.class);
  }

  private static void assertDeclaresIoException(Class<?> type, String name, Class<?>... params)
      throws Exception {
    Method m = type.getMethod(name, params);
    assertTrue(Arrays.asList(m.getExceptionTypes()).contains(IOException.class),
        type.getSimpleName() + "." + name + " must declare throws IOException (upstream @Throws)");
  }

  private static void assertDeclaresNothing(Class<?> type, String name, Class<?>... params)
      throws Exception {
    Method m = type.getMethod(name, params);
    assertEquals(0, m.getExceptionTypes().length,
        type.getSimpleName() + "." + name + " must declare no checked exception (upstream has no @Throws)");
  }

  /** A message whose adapter always fails; enough to drive {@link Message#encode()}. */
  private static final class FailingMessage extends Message<FailingMessage, FailingMessage.Builder> {
    static final ProtoAdapter<FailingMessage> ADAPTER = new ProtoAdapter<FailingMessage>(
        FieldEncoding.LENGTH_DELIMITED, FailingMessage.class, null, Syntax.PROTO_2, null, null) {
      @Override public int encodedSize(FailingMessage value) {
        return 1;
      }

      @Override public void encode(ProtoWriter writer, FailingMessage value) throws IOException {
        throw BOOM;
      }

      @Override public void encode(ReverseProtoWriter writer, FailingMessage value) throws IOException {
        throw BOOM;
      }

      @Override public FailingMessage decode(ProtoReader reader) throws IOException {
        throw BOOM;
      }

      @Override public FailingMessage redact(FailingMessage value) {
        return value;
      }
    };

    FailingMessage() {
      super(ADAPTER, okio.ByteString.EMPTY);
    }

    @Override public Builder newBuilder() {
      throw new AssertionError();
    }

    static final class Builder extends Message.Builder<FailingMessage, Builder> {
      @Override public FailingMessage build() {
        throw new AssertionError();
      }
    }
  }
}
