/*
 * Copyright (C) 2021 Square, Inc.
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.ExtensionRegistry;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.SchemaLoader;
import com.squareup.wire.schema.internal.SchemaEncoder;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import squareup.proto2.java.interop.InteropMessageOuterClass;
import squareup.proto2.java.interop.InteropTest;
import squareup.proto3.java.interop.type.InteropTypes;
import squareup.proto3.java.alltypes.AllTypesOuterClass;

/**
 * Upstream wire-protoc-compatibility-tests SchemaEncoderInteropTest.kt translated: assertk
 * to JUnit 5; backtick test names to camelCase. Upstream compares the wire-schema parse of
 * its own module protos against the protoc-generated Kotlin-package descriptors; this module
 * only carries the java-package corpus, so each retained case points at the java-package
 * proto and its protoc reference class (both from the pinned clone at
 * $ANTIWIRE_UPSTREAM, default /tmp/wire, fetched by scripts/fetch-upstream.sh). The cases
 * whose protos exist only in the Kotlin package (interop_service, proto2 all_types,
 * all_options) are disabled: their protoc reference fixtures are DEC-6 exclusions here. See
 * this module's UPSTREAM-TEST-ADAPTATIONS.md.
 */
public class SchemaEncoderInteropTest {
  private static final Path PROTO_ROOT = upstreamClone()
      .resolve("wire-protoc-compatibility-tests/src/main/proto");

  private static Path upstreamClone() {
    String clone = System.getenv("ANTIWIRE_UPSTREAM");
    Path path = Path.of(clone == null || clone.isEmpty() ? "/tmp/wire" : clone);
    if (!Files.isDirectory(path)) {
      throw new IllegalStateException(
          "pinned upstream clone not found at " + path + "; run scripts/fetch-upstream.sh");
    }
    return path;
  }

  private final Schema schema = loadSchema();

  private static Schema loadSchema() {
    try {
      SchemaLoader loader = new SchemaLoader(FileSystems.getDefault());
      loader.initRoots(Collections.singletonList(Location.get(PROTO_ROOT.toString())));
      return loader.loadSchema();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private final ExtensionRegistry extensionRegistry = createExtensionRegistry();

  private static ExtensionRegistry createExtensionRegistry() {
    ExtensionRegistry registry = ExtensionRegistry.newInstance();
    // Adaptation: upstream registers the descriptor-option extensions of the Kotlin-package
    // interop_service.proto; the java-package corpus declares no descriptor-option
    // extensions, so the registry mirrors the shape with the message extensions that exist.
    InteropMessageOuterClass.registerAllExtensions(registry);
    return registry;
  }

  @Test public void proto2InteropTest() throws IOException {
    ProtoFile wireProtoFile = schema.protoFile("squareup/proto2/java/interop/interop_test.proto");
    assertNotNull(wireProtoFile);
    checkFileSchemasMatch(wireProtoFile, InteropTest.getDescriptor().toProto());
  }

  @Test public void proto3InteropTypes() throws IOException {
    ProtoFile wireProtoFile = schema.protoFile("squareup/proto3/java/interop/type/interop_types.proto");
    assertNotNull(wireProtoFile);
    checkFileSchemasMatch(wireProtoFile, InteropTypes.getDescriptor().toProto());
  }

  /**
   * DEC-6 exclusion: interop_service.proto exists only in the Kotlin package of the pinned
   * module, and its protoc reference class (InteropServiceOuterClassP2) is not generated
   * here (the gRPC surface is excluded). Upstream body:
   *
   * <pre>
   * checkFileSchemasMatch(
   *   wireProtoFile = schema.protoFile("squareup/proto2/kotlin/interop/interop_service.proto")!!,
   *   protocProtoFile = InteropServiceOuterClassP2.getDescriptor().toProto(),
   * )
   * </pre>
   */
  @Test
  @Disabled("DEC-6: Kotlin-package interop_service.proto and its gRPC fixtures are excluded")
  public void proto2InteropService() {
  }

  /**
   * DEC-6 exclusion: the proto2 alltypes corpus exists only in the Kotlin package of the
   * pinned module; no protoc reference class is generated here. Upstream body:
   *
   * <pre>
   * checkFileSchemasMatch(
   *   wireProtoFile = schema.protoFile("squareup/proto2/kotlin/alltypes/all_types.proto")!!,
   *   protocProtoFile = AllTypesOuterClassP2.getDescriptor().toProto(),
   * )
   * </pre>
   */
  @Test
  @Disabled("DEC-6: Kotlin-package proto2 alltypes fixtures are excluded")
  public void proto2AllTypes() {
  }

  // Adaptation: upstream loads all_types_test_proto3_optional.proto from the Kotlin package;
  // the java-package twin carries the same AllTypes message and java_outer_classname.
  @Test public void proto3AllTypes() throws IOException {
    ProtoFile wireProtoFile = schema.protoFile(
        "squareup/proto3/java/alltypes/all_types_test_proto3_optional.proto");
    assertNotNull(wireProtoFile);
    checkFileSchemasMatch(wireProtoFile, AllTypesOuterClass.getDescriptor().toProto());
  }

  /**
   * DEC-6 exclusion: all_options.proto exists only in the Kotlin package of the pinned
   * module; no protoc reference class is generated here. Upstream body:
   *
   * <pre>
   * checkFileSchemaOptionsMatch(
   *   wireProtoFile = schema.protoFile("squareup/proto2/kotlin/alloptions/all_options.proto")!!,
   *   protocProtoFile = AllOptionsP2.getDescriptor().toProto(),
   * )
   * </pre>
   */
  @Test
  @Disabled("DEC-6: Kotlin-package alloptions fixtures are excluded")
  public void proto2AllOptions() {
  }

  /**
   * Confirm the schemas described by {@code wireProtoFile} and {@code protocProtoFile} are equal.
   *
   *
   */
  private void checkFileSchemasMatch(
      ProtoFile wireProtoFile,
      FileDescriptorProto protocProtoFile) throws IOException {
    byte[] wireBytes = new SchemaEncoder(schema).encode(wireProtoFile).toByteArray();
    FileDescriptorProto wireDescriptor =
        FileDescriptorProto.parseFrom(wireBytes, extensionRegistry);
    UnwantedValueStripper unwantedValueStripper = new UnwantedValueStripper(true);
    assertEquals(
        unwantedValueStripper.stripOptionsAndDefaults(protocProtoFile),
        unwantedValueStripper.stripOptionsAndDefaults(wireDescriptor));
  }

  /**
   * Confirm the encoded {@code wireProtoFile} and the re-encoded {@code protocProtoFile} match. We must
   * re-encode to strip extension name and type information because that data isn't retained in the
   * encoded form.
   */
  private void checkFileSchemaOptionsMatch(
      ProtoFile wireProtoFile,
      FileDescriptorProto protocProtoFile) throws IOException {
    byte[] wireBytes = new SchemaEncoder(schema).encode(wireProtoFile).toByteArray();
    FileDescriptorProto wireDescriptor =
        FileDescriptorProto.parseFrom(wireBytes, extensionRegistry);
    FileDescriptorProto protocDescriptorReencoded = FileDescriptorProto.parseFrom(
        protocProtoFile.toByteArray(),
        extensionRegistry);
    assertEquals(wireDescriptor, protocDescriptorReencoded);
  }
}
