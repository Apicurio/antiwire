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
package com.squareup.wire.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-25 validation consumer: exercises {@link JdkSchemaLoader} the way Apicurio's
 * ProtobufSchemaLoader will after the migration (TASK-18), and makes the no-okio-signature rule
 * (TASK-25 AC#1/AC#3, DEC-2) permanent by walking the facade's public surface with reflection.
 */
public class JdkSchemaLoaderValidationTest {
  @TempDir Path tempDir;

  private static final String MESSAGES_PROTO_RESOURCE = "com/example/validation/messages.proto";

  private Path write(Path base, String relativePath, String content) throws IOException {
    Path file = base.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    return file;
  }

  @Test public void classpathLoadingResolvesJavaPackageWithoutFakeFileSystem() throws Exception {
    try (JdkSchemaLoader loader =
        JdkSchemaLoader.forClasspath(getClass().getClassLoader(), MESSAGES_PROTO_RESOURCE)) {
      Schema schema = loader.loadSchema();

      assertNotNull(schema.getType("com.example.validation.Order"));
      ProtoFile protoFile = schema.protoFile(MESSAGES_PROTO_RESOURCE);
      assertNotNull(protoFile);
      assertEquals("com.example.validation", protoFile.javaPackage());
      // descriptor.proto resolved well enough to link the java_package option.
      assertEquals(1, loader.sourcePathFiles().size());
    }
  }

  @Test public void classpathLoadingResolvesWellKnownImports() throws Exception {
    // messages.proto imports google/protobuf/timestamp.proto; that must resolve from the
    // classpath without any synthetic file system wiring.
    try (JdkSchemaLoader loader =
        JdkSchemaLoader.forClasspath(getClass().getClassLoader(), MESSAGES_PROTO_RESOURCE)) {
      Schema schema = loader.loadSchema();
      assertNotNull(schema.getType("google.protobuf.Timestamp"));
    }
  }

  @Test public void loadFromTempDirPathTreeWithNestedPackageDir() throws Exception {
    Path source = Files.createDirectories(tempDir.resolve("src"));
    write(source, "com/example/petstore/pet.proto", ""
        + "syntax = \"proto3\";\n"
        + "package com.example.petstore;\n"
        + "option java_package = \"com.example.petstore\";\n"
        + "message Pet { string name = 1; }\n");

    try (JdkSchemaLoader loader = new JdkSchemaLoader()) {
      loader.initRoots(Collections.singletonList(source));
      Schema schema = loader.loadSchema();

      assertNotNull(schema.getType("com.example.petstore.Pet"));
      assertEquals("com.example.petstore",
          schema.protoFile("com/example/petstore/pet.proto").javaPackage());
    }
  }

  @Test public void loadSingleProtoFileFromPath() throws Exception {
    Path source = Files.createDirectories(tempDir.resolve("src"));
    Path single = write(source, "com/example/single/solo.proto", ""
        + "syntax = \"proto3\";\n"
        + "package com.example.single;\n"
        + "message Solo { int32 n = 1; }\n");

    try (JdkSchemaLoader loader = new JdkSchemaLoader()) {
      loader.initRoots(Collections.singletonList(single));
      Schema schema = loader.loadSchema();

      assertNotNull(schema.getType("com.example.single.Solo"));
    }
  }

  @Test public void loadFromZipArchive() throws Exception {
    Path zip = tempDir.resolve("protos.zip");
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
      out.putNextEntry(new ZipEntry("com/example/zipped/zipped.proto"));
      out.write((""
          + "syntax = \"proto3\";\n"
          + "package com.example.zipped;\n"
          + "message Zipped { bool b = 1; }\n").getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }

    try (JdkSchemaLoader loader = new JdkSchemaLoader()) {
      loader.initRoots(Collections.singletonList(zip));
      Schema schema = loader.loadSchema();

      assertNotNull(schema.getType("com.example.zipped.Zipped"));
    }
  }

  @Test public void errorsSurfaceAsSchemaException() throws Exception {
    Path source = Files.createDirectories(tempDir.resolve("src"));
    write(source, "com/example/broken/broken.proto", ""
        + "syntax = \"proto3\";\n"
        + "package com.example.broken;\n"
        + "message Broken { undefined.Type t = 1; }\n");

    try (JdkSchemaLoader loader = new JdkSchemaLoader()) {
      loader.initRoots(Collections.singletonList(source));
      assertThrows(SchemaException.class, loader::loadSchema);
    }
  }

  @Test public void knobsAreFluent() throws Exception {
    JdkSchemaLoader loader = new JdkSchemaLoader()
        .setPermitPackageCycles(true)
        .setLoadExhaustively(true)
        .setOpaqueTypes(Collections.singletonList(ProtoType.get("com.example.Opaque")));
    assertTrue(loader.permitPackageCycles());
    assertTrue(loader.loadExhaustively());
    assertEquals(Collections.singletonList(ProtoType.get("com.example.Opaque")),
        loader.opaqueTypes());
    loader.close();
  }

  /** permitPackageCycles changes a load outcome, not just the getter. */
  @Test public void permitPackageCyclesKnobIsEffective() throws Exception {
    // Two packages that depend on each other through different files, so the file import
    // graph itself is acyclic and only the package-level check fires (CycleChecker's shape).
    Path source = Files.createDirectories(tempDir.resolve("src"));
    write(source, "locations/office.proto", ""
        + "syntax = \"proto2\";\n"
        + "package locations;\n"
        + "import \"people/office_manager.proto\";\n"
        + "message Office { optional people.OfficeManager manager = 1; }\n");
    write(source, "people/office_manager.proto", ""
        + "syntax = \"proto2\";\n"
        + "package people;\n"
        + "message OfficeManager { optional string name = 1; }\n");
    write(source, "people/employee.proto", ""
        + "syntax = \"proto2\";\n"
        + "package people;\n"
        + "import \"locations/office.proto\";\n"
        + "message Employee { optional locations.Office office = 1; }\n");

    try (JdkSchemaLoader strict = new JdkSchemaLoader()) {
      strict.initRoots(Collections.singletonList(source));
      SchemaException e = assertThrows(SchemaException.class, strict::loadSchema);
      assertTrue(e.getMessage().contains("packages form a cycle"),
          "unexpected error: " + e.getMessage());
    }
    try (JdkSchemaLoader lenient = new JdkSchemaLoader()) {
      lenient.setPermitPackageCycles(true).initRoots(Collections.singletonList(source));
      Schema schema = lenient.loadSchema();
      assertNotNull(schema.getType("locations.Office"));
      assertNotNull(schema.getType("people.Employee"));
    }
  }

  /**
   * AC#1/AC#3 made mechanical for the consumer surface: every public constructor, field, method,
   * parameter, and implemented interface of the facade, including generic type arguments and
   * array components, must be free of {@code okio.*} types. The consumer surface is this
   * facade; the engine surface in the same package ({@code SchemaLoader}, which keeps its
   * okio-typed constructor for the port's own suites) is deliberately outside the rule and is
   * recorded as such in docs/loading-api-inventory.md.
   */
  @Test public void noOkioTypesInPublicSignatures() {
    List<String> offenders = new ArrayList<>();
    // Public members only: the rule governs the consumer-facing surface, not private internals.
    for (Constructor<?> constructor : JdkSchemaLoader.class.getConstructors()) {
      collectOkioOffenders(constructor, offenders);
    }
    for (Method method : JdkSchemaLoader.class.getMethods()) {
      if (method.getDeclaringClass() != JdkSchemaLoader.class) continue;
      collectOkioOffenders(method, offenders);
    }
    for (java.lang.reflect.Field field : JdkSchemaLoader.class.getFields()) {
      if (isOkio(field.getType())) {
        offenders.add(field + " exposes okio type");
      }
    }
    for (Class<?> implemented : JdkSchemaLoader.class.getInterfaces()) {
      if (isOkio(implemented)) {
        offenders.add("implements okio type " + implemented.getName());
      }
    }
    assertTrue(offenders.isEmpty(),
        "okio types leaked into the consumer-facing API: " + offenders);
  }

  private static void collectOkioOffenders(Executable executable, List<String> offenders) {
    if (isOkio(executable.getDeclaringClass())) {
      offenders.add(executable + " declares okio type");
    }
    if (executable instanceof Method && isOkio(((Method) executable).getReturnType())) {
      offenders.add(executable + " returns okio type");
    }
    for (Class<?> parameter : executable.getParameterTypes()) {
      if (isOkio(parameter)) {
        offenders.add(executable + " takes okio type " + parameter.getName());
      }
    }
    for (Type type : executable.getGenericParameterTypes()) {
      collectGenericOkioOffenders(type, executable, offenders);
    }
    if (executable instanceof Method) {
      collectGenericOkioOffenders(((Method) executable).getGenericReturnType(), executable,
          offenders);
    }
  }

  private static void collectGenericOkioOffenders(Type type, Executable executable,
      List<String> offenders) {
    if (type instanceof Class && isOkio((Class<?>) type)) {
      offenders.add(executable + " uses okio type " + type.getTypeName());
    } else if (type instanceof ParameterizedType) {
      for (Type argument : ((ParameterizedType) type).getActualTypeArguments()) {
        collectGenericOkioOffenders(argument, executable, offenders);
      }
    } else if (type instanceof GenericArrayType) {
      collectGenericOkioOffenders(((GenericArrayType) type).getGenericComponentType(), executable,
          offenders);
    }
  }

  private static boolean isOkio(Class<?> type) {
    return type.getName().startsWith("okio.");
  }
}
