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

import static com.squareup.wire.testing.GeneratedMessages.adapterOf;
import static com.squareup.wire.testing.GeneratedMessages.build;
import static com.squareup.wire.testing.GeneratedMessages.builderOf;
import static com.squareup.wire.testing.GeneratedMessages.field;
import static com.squareup.wire.testing.GeneratedMessages.invoke;
import static com.squareup.wire.testing.TestFiles.readUtf8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.wire.testing.TestCompilers;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-27 regression coverage: user types whose names collide with generated runtime references
 * produce models that compile against the port runtime at Java 11 source level with no Kotlin
 * on the classpath, and every field binds to its intended type, runtime {@link Bytes} versus
 * user message or enum, in round-trips. The 2026-10-03 review alleged a same-package
 * {@code message Bytes} breaks compilation through import shadowing; the 2026-10-06 audit
 * refuted that for the tested top-level proto3 shapes, and this test pins the audited shapes
 * plus the variants probed for this task (nested {@code Bytes}, proto2, oneof, extension, and
 * messages colliding with always-emitted imports). The mechanism in every tested case: the
 * generator (JavaPoet) emits no import whose simple name a user type in scope contests and
 * fully qualifies those runtime references instead, so the bare name stays the user type. That
 * emission behavior, not the language, is the protection: were the contested import ever
 * emitted, JLS 6.4.1 would shadow the same-package sibling unconditionally.
 */
public class GeneratedBytesCollisionCompileTest {
  private static final String SELF_BYTES = ""
      + "syntax = \"proto3\";\n"
      + "package collide.self;\n"
      + "\n"
      + "message Bytes {\n"
      + "  bytes data = 1;\n"
      + "  string id = 2;\n"
      + "}\n"
      + "\n"
      + "message Holder {\n"
      + "  bytes payload = 1;\n"
      + "  Bytes meta = 2;\n"
      + "  repeated bytes chunks = 3;\n"
      + "  map<string, bytes> tags = 4;\n"
      + "  repeated Bytes metas = 5;\n"
      + "}\n";

  private static final String CROSS_PACKAGE_P1 = ""
      + "syntax = \"proto3\";\n"
      + "package collide.p1;\n"
      + "\n"
      + "message Bytes {\n"
      + "  string id = 1;\n"
      + "}\n";

  private static final String CROSS_PACKAGE_P2 = ""
      + "syntax = \"proto3\";\n"
      + "package collide.p2;\n"
      + "\n"
      + "import \"collide/bytes/cross/p1.proto\";\n"
      + "\n"
      + "message User {\n"
      + "  collide.p1.Bytes meta = 1;\n"
      + "  bytes payload = 2;\n"
      + "}\n";

  private static final String ENUM_BYTES = ""
      + "syntax = \"proto3\";\n"
      + "package collide.enumbytes;\n"
      + "\n"
      + "enum Bytes {\n"
      + "  B0 = 0;\n"
      + "}\n"
      + "\n"
      + "message Holder {\n"
      + "  bytes payload = 1;\n"
      + "  Bytes tag = 2;\n"
      + "}\n";

  private static final String NESTED_BYTES = ""
      + "syntax = \"proto3\";\n"
      + "package collide.nested;\n"
      + "\n"
      + "message Holder {\n"
      + "  bytes payload = 1;\n"
      + "  message Bytes {\n"
      + "    string id = 1;\n"
      + "  }\n"
      + "  Bytes meta = 2;\n"
      + "}\n";

  private static final String PROTO2_BYTES = ""
      + "syntax = \"proto2\";\n"
      + "package collide.proto2;\n"
      + "\n"
      + "message Bytes {\n"
      + "  optional bytes data = 1;\n"
      + "  optional string id = 2;\n"
      + "}\n"
      + "\n"
      + "message Holder {\n"
      + "  optional bytes payload = 1;\n"
      + "  optional Bytes meta = 2;\n"
      + "}\n";

  private static final String ONEOF_BYTES = ""
      + "syntax = \"proto3\";\n"
      + "package collide.oneof;\n"
      + "\n"
      + "message Bytes {\n"
      + "  string id = 1;\n"
      + "}\n"
      + "\n"
      + "message Holder {\n"
      + "  bytes payload = 1;\n"
      + "  oneof choice {\n"
      + "    Bytes meta = 2;\n"
      + "    string name = 3;\n"
      + "  }\n"
      + "}\n";

  private static final String EXTENSION_BYTES = ""
      + "syntax = \"proto2\";\n"
      + "package collide.ext;\n"
      + "\n"
      + "message Bytes {\n"
      + "  optional string id = 1;\n"
      + "}\n"
      + "\n"
      + "message Holder {\n"
      + "  optional bytes payload = 1;\n"
      + "  optional int32 base = 2;\n"
      + "}\n"
      + "\n"
      + "extend Holder {\n"
      + "  optional Bytes extra = 100;\n"
      + "}\n";

  private static final String ALWAYS_IMPORTED_NAMES = ""
      + "syntax = \"proto3\";\n"
      + "package collide.imports;\n"
      + "\n"
      + "message FieldEncoding {\n"
      + "  string id = 1;\n"
      + "}\n"
      + "\n"
      + "message ProtoWriter {\n"
      + "  string id = 1;\n"
      + "}\n"
      + "\n"
      + "message Internal {\n"
      + "  string id = 1;\n"
      + "}\n";

  private static final String ALWAYS_IMPORTED_NAMES_HOLDER = ""
      + "syntax = \"proto3\";\n"
      + "package collide.imports;\n"
      + "\n"
      + "import \"collide/bytes/imports/imports.proto\";\n"
      + "\n"
      + "message Holder {\n"
      + "  FieldEncoding f = 1;\n"
      + "  ProtoWriter w = 2;\n"
      + "  Internal i = 3;\n"
      + "  bytes payload = 4;\n"
      + "}\n";

  @TempDir static Path tempDir;

  /**
   * The generated, compiled, and loaded models, shared across the cases: one CLI generation
   * over every proto and one {@code javac --release 11} compile over the whole output tree.
   * Cached statically because JUnit gives each case a fresh test instance; the compiling
   * classloader stays open because the models load siblings lazily.
   */
  private static ClassLoader generatedModels;

  @Test public void samePackageMessageBytesBindsRuntimeAndUserTypes() throws Exception {
    Class<?> holder = load("collide.self.Holder");
    Class<?> userBytes = userTypeOf(holder);
    Object builder = builderOf(holder);
    invoke(builder, "payload", Bytes.encodeUtf8("pl"));
    invoke(builder, "meta", userValue(holder));
    invoke(builder, "chunks", Arrays.asList(Bytes.encodeUtf8("c1"), Bytes.encodeUtf8("c2")));
    Map<String, Bytes> tags = new LinkedHashMap<>();
    tags.put("k", Bytes.encodeUtf8("v"));
    invoke(builder, "tags", tags);
    invoke(builder, "metas", Arrays.asList(userValue(holder)));
    Object message = build(builder);

    byte[] wire = adapterOf(holder).encode(message);
    Object decoded = adapterOf(holder).decode(wire);
    assertTrue(userBytes.isInstance(field(holder, decoded, "meta")), "meta bound to the wrong type");
    assertEquals(wire.length, adapterOf(holder).encodedSize(message));
    assertEquals(message, decoded);

    // The emission mechanism this suite pins: the sibling file keeps the bare name for the
    // user type and qualifies the runtime bytes references instead of importing them.
    String source = readUtf8(sourceOf("collide/self/Holder.java"));
    assertFalse(source.contains("import com.squareup.wire.Bytes;"), source);
    assertTrue(source.contains("com.squareup.wire.Bytes"), source);
  }

  @Test public void crossPackageMessageBytesBindsToItsOwnPackage() throws Exception {
    Class<?> user = load("collide.p2.User");
    Class<?> p1Bytes = load("collide.p1.Bytes");
    Object message = newBuilder(user, true);
    byte[] encoded = adapterOf(user).encode(message);
    // meta is the p1.Bytes message (id tag 1) nested in tag 1, then the runtime payload in tag 2.
    assertArrayEquals(new byte[] { 0x0a, 0x04, 0x0a, 0x02, 'k', '1', 0x12, 0x02, 'p', 'l' },
        encoded);
    Object decoded = adapterOf(user).decode(encoded);
    assertTrue(p1Bytes.isInstance(field(user, decoded, "meta")), "meta bound to the wrong type");
    assertEquals("k1", field(p1Bytes, field(user, decoded, "meta"), "id"));
    assertEquals(Bytes.encodeUtf8("pl"), field(user, decoded, "payload"));
    assertEquals(message, decoded);
  }

  @Test public void enumNamedBytesBindsTheEnumNotRuntimeBytes() throws Exception {
    roundTripHolder(load("collide.enumbytes.Holder"));
  }

  @Test public void nestedBytesBindsTheNestedType() throws Exception {
    roundTripHolder(load("collide.nested.Holder"));
  }

  @Test public void proto2BytesShapesBindLikeProto3() throws Exception {
    Class<?> holder = load("collide.proto2.Holder");
    roundTripHolder(holder);

    // proto2 optional absence: neither field set encodes nothing and decodes back to nulls.
    byte[] empty = adapterOf(holder).encode(newBuilder(holder, false));
    assertEquals(0, empty.length);
    Object absentDecoded = adapterOf(holder).decode(empty);
    assertNull(field(holder, absentDecoded, "payload"));
    assertNull(field(holder, absentDecoded, "meta"));
  }

  @Test public void oneofBytesBindsTheUserMessageInsideTheChoice() throws Exception {
    roundTripHolder(load("collide.oneof.Holder"));
  }

  @Test public void extensionOfBytesTypeBindsTheUserMessage() throws Exception {
    roundTripHolder(load("collide.ext.Holder"));
  }

  @Test public void alwaysImportedNamesKeepTheirUserTypesInSiblings() throws Exception {
    Class<?> holder = load("collide.imports.Holder");
    Object builder = builderOf(holder);
    invoke(builder, "f", idMessage(load("collide.imports.FieldEncoding"), "fe1"));
    invoke(builder, "w", idMessage(load("collide.imports.ProtoWriter"), "pw1"));
    invoke(builder, "i", idMessage(load("collide.imports.Internal"), "in1"));
    invoke(builder, "payload", Bytes.encodeUtf8("pl"));
    Object message = build(builder);

    Object decoded = adapterOf(holder).decode(adapterOf(holder).encode(message));
    assertTrue(load("collide.imports.FieldEncoding").isInstance(field(holder, decoded, "f")));
    assertTrue(load("collide.imports.ProtoWriter").isInstance(field(holder, decoded, "w")));
    assertTrue(load("collide.imports.Internal").isInstance(field(holder, decoded, "i")));
    assertEquals(Bytes.encodeUtf8("pl"), field(holder, decoded, "payload"));
    assertEquals(message, decoded);

    // The holder file references all three user types bare: none of the contested
    // always-emitted imports may appear, and each runtime reference must be qualified.
    String source = readUtf8(sourceOf("collide/imports/Holder.java"));
    assertFalse(source.contains("import com.squareup.wire.FieldEncoding;"), source);
    assertFalse(source.contains("import com.squareup.wire.ProtoWriter;"), source);
    assertFalse(source.contains("import com.squareup.wire.internal.Internal;"), source);
    assertTrue(source.contains("com.squareup.wire.FieldEncoding.LENGTH_DELIMITED"), source);
    assertTrue(source.contains("com.squareup.wire.ProtoWriter writer"), source);
    assertTrue(source.contains("com.squareup.wire.internal.Internal."), source);
  }

  /**
   * The shared shape drive: build a holder whose bytes field is the runtime {@link Bytes} and
   * whose single user-typed field carries a user {@code Bytes} value with an inner bytes and an
   * id, then round-trip through the generated adapter and check both bindings and contents.
   */
  private static byte[] roundTripHolder(Class<?> holder) throws Exception {
    Object message = newBuilder(holder, true);
    ProtoAdapter<Object> adapter = adapterOf(holder);
    byte[] wire = adapter.encode(message);
    Object decoded = adapter.decode(wire);
    String userField = userFieldName(holder);
    assertTrue(userTypeOf(holder).isInstance(field(holder, decoded, userField)),
        userField + " bound to the wrong type");
    assertArrayEquals(wire, adapter.encode(decoded));
    assertEquals(message, decoded);
    return wire;
  }

  /**
   * Each fixture's single user-typed field and its user type, declared with the fixture
   * instead of derived from package prefixes: a new fixture that forgets its entry fails here
   * with the holder's name, after the shared generation and compilation.
   */
  private static final Map<String, String> USER_FIELD_BY_HOLDER = Map.of(
      "collide.self.Holder", "meta",
      "collide.p2.User", "meta",
      "collide.enumbytes.Holder", "tag",
      "collide.nested.Holder", "meta",
      "collide.proto2.Holder", "meta",
      "collide.oneof.Holder", "meta",
      "collide.ext.Holder", "extra");

  private static final Map<String, String> USER_TYPE_BY_HOLDER = Map.of(
      "collide.self.Holder", "collide.self.Bytes",
      "collide.p2.User", "collide.p1.Bytes",
      "collide.enumbytes.Holder", "collide.enumbytes.Bytes",
      "collide.nested.Holder", "collide.nested.Holder$Bytes",
      "collide.proto2.Holder", "collide.proto2.Bytes",
      "collide.oneof.Holder", "collide.oneof.Bytes",
      "collide.ext.Holder", "collide.ext.Bytes");

  private static String userFieldName(Class<?> holder) {
    String field = USER_FIELD_BY_HOLDER.get(holder.getName());
    if (field == null) {
      throw new IllegalArgumentException(
          "no USER_FIELD_BY_HOLDER entry for " + holder.getName());
    }
    return field;
  }

  private static Class<?> userTypeOf(Class<?> holder) {
    String type = USER_TYPE_BY_HOLDER.get(holder.getName());
    if (type == null) {
      throw new IllegalArgumentException(
          "no USER_TYPE_BY_HOLDER entry for " + holder.getName());
    }
    try {
      return Class.forName(type, true, loader());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** A holder with its fields set; {@code present} toggles every field at once. */
  private static Object newBuilder(Class<?> holder, boolean present) throws Exception {
    Object builder = builderOf(holder);
    if (!present) return build(builder);
    invoke(builder, "payload", Bytes.encodeUtf8("pl"));
    invoke(builder, userFieldName(holder), userValue(holder));
    return build(builder);
  }

  /** The user-typed value for the holder's single user field: a message or an enum constant. */
  private static Object userValue(Class<?> holder) throws Exception {
    Class<?> userType = userTypeOf(holder);
    if (userType.isEnum()) return userType.getEnumConstants()[0];
    Object builder = builderOf(userType);
    if (hasField(userType, "data")) {
      invoke(builder, "data", Bytes.encodeUtf8("inner"));
    }
    invoke(builder, "id", "k1");
    return build(builder);
  }

  /** A user message with only its {@code id} set. */
  private static Object idMessage(Class<?> type, String id) throws Exception {
    Object builder = builderOf(type);
    invoke(builder, "id", id);
    return build(builder);
  }

  private static boolean hasField(Class<?> type, String name) {
    for (Field f : type.getFields()) {
      if (f.getName().equals(name)) return true;
    }
    return false;
  }

  private static ClassLoader loader() throws Exception {
    if (generatedModels == null) generateCompileLoadAll();
    return generatedModels;
  }

  private static Class<?> load(String className) throws Exception {
    return Class.forName(className, true, loader());
  }

  private static Path sourceOf(String relativePath) {
    return tempDir.resolve("java_out").resolve(relativePath);
  }

  /** Generates every fixture proto with the port CLI, compiles all output with one javac. */
  private static synchronized void generateCompileLoadAll() throws Exception {
    if (generatedModels != null) return;
    Path protoRoot = Files.createDirectories(tempDir.resolve("proto"));
    write(protoRoot, "collide/bytes/self/self.proto", SELF_BYTES);
    write(protoRoot, "collide/bytes/cross/p1.proto", CROSS_PACKAGE_P1);
    write(protoRoot, "collide/bytes/cross/p2.proto", CROSS_PACKAGE_P2);
    write(protoRoot, "collide/bytes/enum/enum.proto", ENUM_BYTES);
    write(protoRoot, "collide/bytes/nested/nested.proto", NESTED_BYTES);
    write(protoRoot, "collide/bytes/proto2/proto2.proto", PROTO2_BYTES);
    write(protoRoot, "collide/bytes/oneof/oneof.proto", ONEOF_BYTES);
    write(protoRoot, "collide/bytes/ext/ext.proto", EXTENSION_BYTES);
    write(protoRoot, "collide/bytes/imports/imports.proto", ALWAYS_IMPORTED_NAMES);
    write(protoRoot, "collide/bytes/imports/holder.proto", ALWAYS_IMPORTED_NAMES_HOLDER);
    List<String> protos = Arrays.asList(
        "collide/bytes/self/self.proto",
        "collide/bytes/cross/p1.proto",
        "collide/bytes/cross/p2.proto",
        "collide/bytes/enum/enum.proto",
        "collide/bytes/nested/nested.proto",
        "collide/bytes/proto2/proto2.proto",
        "collide/bytes/oneof/oneof.proto",
        "collide/bytes/ext/ext.proto",
        "collide/bytes/imports/imports.proto",
        "collide/bytes/imports/holder.proto");

    Path out = tempDir.resolve("java_out");
    List<String> args = new ArrayList<>(Arrays.asList(
        "--proto_path=" + protoRoot,
        "--java_out=" + out));
    args.addAll(protos);
    WireCompiler.forArgs(
        okio.FileSystem.SYSTEM,
        WireLogger.NONE,
        args.toArray(new String[0]))
        .compile();

    Path classes = Files.createDirectories(tempDir.resolve("classes"));
    List<Path> sources = new ArrayList<>();
    try (java.util.stream.Stream<Path> walk = Files.walk(out)) {
      walk.filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".java"))
          .forEach(sources::add);
    }
    generatedModels = TestCompilers.compileRelease11(
        sources.toArray(new Path[0]), classes, GeneratedBytesCollisionCompileTest.class);
  }

  private static void write(Path protoRoot, String relativePath, String content)
      throws Exception {
    Path file = protoRoot.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
  }
}
