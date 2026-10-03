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

import static com.squareup.wire.testing.TestFiles.readUtf8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.squareup.javapoet.JavaFile;
import com.squareup.wire.SchemaBuilder;
import com.squareup.wire.internal.Reflection;
import com.squareup.wire.java.JavaGenerator;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.Type;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 2 of the no-okio public API, proven end to end (docs/api-surface.md): the port's
 * generator emits bytes-typed fields as {@link Bytes}, and that output must compile against
 * the port runtime and keep the wire format byte-identical; only the Java value type changes.
 * This generates a model covering every bytes shape (scalar, repeated, wrapper, map value),
 * compiles it with the JDK compiler at test time, and round-trips it through both the
 * generated adapter and the reflection machinery, against hand-built wire bytes.
 */
public class GeneratedBytesCompileTest {
  private static final String PROTO = ""
      + "syntax = \"proto3\";\n"
      + "import \"google/protobuf/wrappers.proto\";\n"
      + "\n"
      + "package antibytes;\n"
      + "\n"
      + "message Payload {\n"
      + "  bytes data = 1;\n"
      + "  repeated bytes chunks = 2;\n"
      + "  google.protobuf.BytesValue wrapped = 3;\n"
      + "  map<string, bytes> blobs = 4;\n"
      + "  int32 count = 5;\n"
      + "}\n";

  /** proto2 exercises the defaulted-bytes emission the proto3 corpus cannot reach. */
  private static final String PROTO2_WITH_DEFAULT = ""
      + "syntax = \"proto2\";\n"
      + "\n"
      + "package antibytes.defaults;\n"
      + "\n"
      + "message Defaulted {\n"
      + "  optional bytes default_bytes = 1 [default = \"grr\"];\n"
      + "  optional int32 plain = 2;\n"
      + "}\n";

  @TempDir Path tempDir;

  @Test public void generatedModelCompilesAndRoundTripsByteIdentically() throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "tests must run on a JDK: no system java compiler");

    // Generate the model exactly as JavaSchemaHandler does for a JavaTarget.
    Schema schema = new SchemaBuilder().add("antibytes/payload.proto", PROTO).build();
    JavaGenerator generator = JavaGenerator.get(schema);
    Type type = schema.getType("antibytes.Payload");
    JavaFile javaFile = JavaFile.builder(
        generator.generatedTypeName(type).packageName(), generator.generateType(type)).build();
    Path sources = tempDir.resolve("sources");
    javaFile.writeTo(sources);
    Path sourceFile = sources.resolve("antibytes/Payload.java");
    assertTrue(Files.isRegularFile(sourceFile), "generator wrote " + sourceFile);

    String source = readUtf8(sourceFile);
    assertTrue(source.contains("import com.squareup.wire.Bytes;"), source);
    assertTrue(source.contains("ProtoAdapter.WIRE_BYTES."), source);
    assertTrue(source.contains("ProtoAdapter.WIRE_BYTES_VALUE"), source);
    assertTrue(source.contains("\"com.squareup.wire.ProtoAdapter#WIRE_BYTES\""), source);
    assertTrue(!source.contains("okio."), "generated source must be okio-free:\n" + source);

    Class<?> messageClass = compileGenerated(compiler, sources, sourceFile, "antibytes.Payload");
    roundTripThroughGeneratedAdapter(messageClass);
    roundTripThroughReflectionAdapter(messageClass);
  }

  @Test public void proto2DefaultedBytesFieldCompilesAndDecodesTheDefault() throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "tests must run on a JDK: no system java compiler");

    Schema schema =
        new SchemaBuilder().add("antibytes/defaults/defaults.proto", PROTO2_WITH_DEFAULT).build();
    JavaGenerator generator = JavaGenerator.get(schema);
    Type type = schema.getType("antibytes.defaults.Defaulted");
    Path sources = tempDir.resolve("sources2");
    JavaFile.builder(generator.generatedTypeName(type).packageName(), generator.generateType(type))
        .build()
        .writeTo(sources);
    Path sourceFile = sources.resolve("antibytes/defaults/Defaulted.java");
    String source = readUtf8(sourceFile);
    // The default constant decodes through the wire-owned type, not okio's.
    assertTrue(source.contains("DEFAULT_DEFAULT_BYTES = Bytes.decodeBase64("), source);

    Class<?> messageClass =
        compileGenerated(compiler, sources, sourceFile, "antibytes.defaults.Defaulted");
    Object declaredDefault = messageClass.getField("DEFAULT_DEFAULT_BYTES").get(null);
    assertEquals(Bytes.of("grr".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)),
        declaredDefault);

    // Upstream semantics: an absent proto2 optional field decodes to null; the DEFAULT_
    // constant documents the declared default without the decoder applying it.
    Object decoded = adapterOf(messageClass)
        .decode(new byte[] { 0x10, 0x01 }); // plain = 1; default_bytes absent.
    assertNull(field(messageClass, decoded, "default_bytes"));

    // A present defaulted field round-trips through the wire-owned type.
    Object present = adapterOf(messageClass).decode(new byte[] {
        0x0a, 0x03, 'g', 'r', 'r', 0x10, 0x01 });
    assertEquals(declaredDefault, field(messageClass, present, "default_bytes"));
  }

  /** Compiles {@code sourceFile} against this test's classpath and loads {@code className}. */
  private Class<?> compileGenerated(
      JavaCompiler compiler, Path sources, Path sourceFile, String className) throws Exception {
    Path classes = Files.createDirectories(tempDir.resolve(
        "classes-" + sources.getFileName()));
    int exit = compiler.run(null, null, System.err, "-classpath", compilerClasspath(),
        "-d", classes.toString(), sourceFile.toString());
    assertEquals(0, exit, "generated model did not compile");
    try (URLClassLoader loader = new URLClassLoader(
        new URL[] { classes.toUri().toURL() }, getClass().getClassLoader())) {
      return Class.forName(className, true, loader);
    }
  }

  /**
   * The classpath to compile generated sources against, taken from this test's own classloader
   * so the compile works under launchers that pass a manifest-jar {@code java.class.path} (the
   * property is the fallback).
   */
  private static String compilerClasspath() {
    ClassLoader loader = GeneratedBytesCompileTest.class.getClassLoader();
    if (loader instanceof URLClassLoader) {
      StringBuilder classpath = new StringBuilder();
      for (URL url : ((URLClassLoader) loader).getURLs()) {
        if (!"file".equals(url.getProtocol())) {
          return System.getProperty("java.class.path");
        }
        if (classpath.length() > 0) classpath.append(java.io.File.pathSeparatorChar);
        classpath.append(Path.of(java.net.URI.create(url.toString())));
      }
      if (classpath.length() > 0) return classpath.toString();
    }
    return System.getProperty("java.class.path");
  }

  private void roundTripThroughGeneratedAdapter(Class<?> messageClass) throws Exception {
    ProtoAdapter<Object> adapter = adapterOf(messageClass);
    Object message = newMessage(messageClass);

    byte[] encoded = adapter.encode(message);
    assertEquals(encoded.length, adapter.encodedSize(message));
    assertArrayEquals(expectedWireBytes(), encoded);

    Object decoded = adapter.decode(encoded);
    assertEquals(field(messageClass, message, "data"), field(messageClass, decoded, "data"));
    assertEquals(field(messageClass, message, "chunks"), field(messageClass, decoded, "chunks"));
    assertEquals(field(messageClass, message, "wrapped"), field(messageClass, decoded, "wrapped"));
    assertEquals(field(messageClass, message, "blobs"), field(messageClass, decoded, "blobs"));
    assertEquals(field(messageClass, message, "count"), field(messageClass, decoded, "count"));
    assertArrayEquals(encoded, adapter.encode(decoded));
  }

  private void roundTripThroughReflectionAdapter(Class<?> messageClass) throws Exception {
    ProtoAdapter<Object> adapter = reflectionAdapterOf(messageClass);
    byte[] expected = expectedWireBytes();

    Object decoded = adapter.decode(expected);
    assertEquals(Bytes.encodeUtf8("main"), field(messageClass, decoded, "data"));
    assertArrayEquals(expected, adapter.encode(decoded));

    // Unknown fields survive the reflection round-trip: tag 15 varint is preserved verbatim.
    byte[] withUnknown = new okio.Buffer().write(expected)
        .write(new byte[] { 0x78, 0x2a }) // tag 15, varint 42.
        .readByteArray();
    Object withUnknownDecoded = adapter.decode(withUnknown);
    assertTrue(((Bytes) messageClass.getMethod("unknownFieldsBytes").invoke(withUnknownDecoded))
        .size() > 0, "unknown field was dropped");
    assertArrayEquals(withUnknown, adapter.encode(withUnknownDecoded));
  }

  /**
   * The reflection adapter for the generated class, as a consumer without generated code gets
   * one; raw because the generated type parameters are unknown to this test.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ProtoAdapter<Object> reflectionAdapterOf(Class<?> messageClass) {
    return (ProtoAdapter<Object>) (ProtoAdapter<?>) Reflection
        .createRuntimeMessageAdapter((Class) messageClass, null, Syntax.PROTO_3);
  }

  /** The adapter the generated class declares; fails if the class does not declare one. */
  @SuppressWarnings("unchecked")
  private static ProtoAdapter<Object> adapterOf(Class<?> messageClass) throws Exception {
    return (ProtoAdapter<Object>) messageClass.getField("ADAPTER").get(null);
  }

  private Object newMessage(Class<?> messageClass) throws Exception {
    Class<?> builderClass = Class.forName("antibytes.Payload$Builder", true,
        messageClass.getClassLoader());
    Object builder = builderClass.getDeclaredConstructor().newInstance();
    Map<String, Bytes> blobs = new LinkedHashMap<>();
    blobs.put("k", Bytes.encodeUtf8("v"));
    invoke(builder, "data", Bytes.encodeUtf8("main"));
    invoke(builder, "chunks", Arrays.asList(Bytes.encodeUtf8("c1"), Bytes.encodeUtf8("c2")));
    invoke(builder, "wrapped", Bytes.encodeUtf8("w"));
    invoke(builder, "blobs", blobs);
    invoke(builder, "count", 7);
    return builderClass.getMethod("build").invoke(builder);
  }

  private static void invoke(Object builder, String setter, Object value) throws Exception {
    for (Method method : builder.getClass().getMethods()) {
      if (method.getName().equals(setter) && method.getParameterCount() == 1) {
        method.invoke(builder, value);
        return;
      }
    }
    fail("no setter " + setter + " on " + builder.getClass());
  }

  private static Object field(Class<?> messageClass, Object message, String name)
      throws Exception {
    return messageClass.getField(name).get(message);
  }

  /**
   * The same bytes an okio-typed twin of this model would encode: data, two chunks, a
   * BytesValue-wrapped payload (nested tag 1), a one-entry map (key tag 1, value tag 2), and
   * the control int32, in tag order.
   */
  private static byte[] expectedWireBytes() throws IOException {
    okio.Buffer buffer = new okio.Buffer();
    ProtoWriter writer = new ProtoWriter(buffer);

    writer.writeTag(1, FieldEncoding.LENGTH_DELIMITED);
    writer.writeVarint32(4);
    writer.writeBytes(Bytes.encodeUtf8("main"));

    writer.writeTag(2, FieldEncoding.LENGTH_DELIMITED);
    writer.writeVarint32(2);
    writer.writeBytes(Bytes.encodeUtf8("c1"));
    writer.writeTag(2, FieldEncoding.LENGTH_DELIMITED);
    writer.writeVarint32(2);
    writer.writeBytes(Bytes.encodeUtf8("c2"));

    writer.writeTag(3, FieldEncoding.LENGTH_DELIMITED);
    writer.writeVarint32(3);
    writer.writeTag(1, FieldEncoding.LENGTH_DELIMITED);
    writer.writeVarint32(1);
    writer.writeBytes(Bytes.encodeUtf8("w"));

    // Map entry: length 6 = key (tag 1, len 1, 'k') + value (tag 2, len 1, 'v').
    writer.writeTag(4, FieldEncoding.LENGTH_DELIMITED);
    writer.writeVarint32(6);
    writer.writeTag(1, FieldEncoding.LENGTH_DELIMITED);
    writer.writeVarint32(1);
    writer.writeString("k");
    writer.writeTag(2, FieldEncoding.LENGTH_DELIMITED);
    writer.writeVarint32(1);
    writer.writeBytes(Bytes.encodeUtf8("v"));

    writer.writeTag(5, FieldEncoding.VARINT);
    writer.writeVarint32(7);

    return buffer.readByteArray();
  }

}
