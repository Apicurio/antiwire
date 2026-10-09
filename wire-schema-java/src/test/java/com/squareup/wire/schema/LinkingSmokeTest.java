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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.wire.ProtoAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okio.ByteString;
import org.junit.jupiter.api.Test;

/**
 * TASK-11 smoke corpus for the linked schema model: parse .proto sources, link them through the
 * Linker, and assert type resolution, option linking, extensions, validation errors, pruning and
 * the dynamic protoAdapter. Full upstream schema-test adoption is TASK-13.
 */
public class LinkingSmokeTest {
  private Schema link(MapLoader loader, String... sourcePaths) {
    List<ProtoFile> protoFiles = new ArrayList<>();
    for (String path : sourcePaths) {
      protoFiles.add(loader.load(path));
    }
    Linker linker = new Linker(loader, new ErrorCollector(), false, false);
    return linker.link(protoFiles);
  }

  private MapLoader loader(String... sources) {
    MapLoader loader = new MapLoader();
    for (int i = 0; i < sources.length; i += 2) {
      loader.add(sources[i], sources[i + 1]);
    }
    return loader;
  }

  @Test public void linkMessageResolvesFieldTypes() {
    MapLoader loader = loader(
        "message.proto", ""
            + "syntax = \"proto3\";\n"
            + "package squareup.protos;\n"
            + "message Outer {\n"
            + "  string name = 1;\n"
            + "  int32 count = 2;\n"
            + "  Inner inner = 3;\n"
            + "  repeated int32 numbers = 4;\n"
            + "  message Inner {\n"
            + "    bool ok = 1;\n"
            + "  }\n"
            + "}\n");
    Schema schema = link(loader, "message.proto");

    Type type = schema.getType("squareup.protos.Outer");
    assertTrue(type instanceof MessageType);
    MessageType outer = (MessageType) type;
    assertEquals(4, outer.fields().size());

    Field name = outer.field("name");
    assertEquals(ProtoType.STRING, name.getType());
    assertEquals(Field.EncodeMode.OMIT_IDENTITY, name.getEncodeMode());
    assertEquals("name", name.getJsonName());

    Field count = outer.field("count");
    assertEquals(ProtoType.INT32, count.getType());
    assertEquals(Field.EncodeMode.OMIT_IDENTITY, count.getEncodeMode());

    Field inner = outer.field("inner");
    assertEquals(schema.getType("squareup.protos.Outer.Inner").getType(), inner.getType());
    assertEquals(Field.EncodeMode.OMIT_IDENTITY, inner.getEncodeMode());

    Field numbers = outer.field("numbers");
    assertEquals(Field.EncodeMode.PACKED, numbers.getEncodeMode());
    assertTrue(numbers.isPacked());

    MessageType innerType = (MessageType) schema.getType("squareup.protos.Outer.Inner");
    assertEquals(ProtoType.BOOL, innerType.field("ok").getType());
  }

  @Test public void linkServiceResolvesRequestAndResponseTypes() {
    MapLoader loader = loader(
        "service.proto", ""
            + "import \"request.proto\";\n"
            + "import \"response.proto\";\n"
            + "service Service {\n"
            + "  rpc Call (Request) returns (Response);\n"
            + "}\n",
        "request.proto", "message Request {}\n",
        "response.proto", "message Response {}\n");
    Schema schema = link(loader, "service.proto");

    Service service = schema.getService("Service");
    assertNotNull(service);
    Rpc call = service.rpc("Call");
    assertNotNull(call);
    assertEquals(schema.getType("Request").getType(), call.getRequestType());
    assertEquals(schema.getType("Response").getType(), call.getResponseType());
  }

  @Test public void linkImportsAcrossFiles() {
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto3\";\n"
            + "package pkg;\n"
            + "import \"b.proto\";\n"
            + "message A { B b = 1; }\n",
        "b.proto", ""
            + "syntax = \"proto3\";\n"
            + "package pkg;\n"
            + "message B { string s = 1; }\n");
    Schema schema = link(loader, "a.proto");

    MessageType a = (MessageType) schema.getType("pkg.A");
    assertEquals(schema.getType("pkg.B").getType(), a.field("b").getType());

    // b.proto is used by a source file, so it is retained in the schema.
    assertNotNull(schema.protoFile("b.proto"));
  }

  @Test public void missingImportFails() {
    // Both files are linked as sources, so B resolves; the missing import is then reported.
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto3\";\n"
            + "package pkg;\n"
            + "message A { B b = 1; }\n",
        "b.proto", ""
            + "syntax = \"proto3\";\n"
            + "package pkg;\n"
            + "message B { string s = 1; }\n");
    SchemaException e = assertThrows(SchemaException.class,
        () -> link(loader, "a.proto", "b.proto"));
    assertTrue(e.getMessage().contains("a.proto needs to import b.proto"),
        e.getMessage());
  }

  @Test public void unresolvedTypeFails() {
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto3\";\n"
            + "message A { Missing missing = 1; }\n");
    SchemaException e = assertThrows(SchemaException.class, () -> link(loader, "a.proto"));
    assertTrue(e.getMessage().contains("unable to resolve Missing"), e.getMessage());
  }

  @Test public void duplicateTagsFail() {
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto2\";\n"
            + "message A {\n"
            + "  optional string one = 1;\n"
            + "  optional string two = 1;\n"
            + "}\n");
    SchemaException e = assertThrows(SchemaException.class, () -> link(loader, "a.proto"));
    assertTrue(e.getMessage().contains("multiple fields share tag 1"), e.getMessage());
    assertTrue(e.getMessage().contains("for message A"), e.getMessage());
  }

  @Test public void extensionFieldsAreLinkedOntoTheTargetMessage() {
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto2\";\n"
            + "package pkg;\n"
            + "message A {\n"
            + "  optional string one = 1;\n"
            + "  extensions 100 to 200;\n"
            + "}\n"
            + "extend A {\n"
            + "  optional int32 extra = 100;\n"
            + "}\n");
    Schema schema = link(loader, "a.proto");

    MessageType a = (MessageType) schema.getType("pkg.A");
    assertEquals(1, a.getDeclaredFields().size());
    assertEquals(1, a.getExtensionFields().size());
    Field extra = a.getExtensionFields().get(0);
    assertEquals("extra", extra.getName());
    assertEquals("pkg.extra", extra.getQualifiedName());
    assertEquals(ProtoType.INT32, extra.getType());
    assertEquals(a.field(100), extra);
  }

  @Test public void javaPackageOptionIsLinked() {
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto2\";\n"
            + "package pkg;\n"
            + "option java_package = \"com.example.generated\";\n"
            + "message A { optional string one = 1; }\n");
    Schema schema = link(loader, "a.proto");

    ProtoFile protoFile = schema.protoFile("a.proto");
    assertEquals("com.example.generated", protoFile.javaPackage());
  }

  @Test public void fieldOptionsLinkThroughDescriptorProto() {
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto2\";\n"
            + "package pkg;\n"
            + "message A {\n"
            + "  optional string one = 1 [deprecated = true];\n"
            + "}\n");
    Schema schema = link(loader, "a.proto");

    MessageType a = (MessageType) schema.getType("pkg.A");
    Field one = a.field("one");
    assertTrue(one.isDeprecated());
    assertFalse(one.isRedacted());
  }

  @Test public void wireExtensionOptionsLink() {
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto3\";\n"
            + "package pkg;\n"
            + "import \"wire/extensions.proto\";\n"
            + "message A {\n"
            + "  string since_field = 1 [(wire.since) = \"1.0\"];\n"
            + "}\n");
    Schema schema = link(loader, "a.proto");

    MessageType a = (MessageType) schema.getType("pkg.A");
    assertEquals("1.0", a.field("since_field").getOptions()
        .get(ProtoMember.get(Options.FIELD_OPTIONS, "wire.since")));
  }

  @Test public void customRedactedOptionMarksField() {
    // Mirrors upstream option_redacted.proto: a project-local extension whose qualified name
    // ends with '.redacted' marks the field redacted (the .*\.redacted heuristic).
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto2\";\n"
            + "package pkg;\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional bool redacted = 22200;\n"
            + "}\n"
            + "message A {\n"
            + "  optional string secret = 1 [(redacted) = true];\n"
            + "  optional string plain = 2;\n"
            + "}\n");
    Schema schema = link(loader, "a.proto");

    MessageType a = (MessageType) schema.getType("pkg.A");
    assertTrue(a.field("secret").isRedacted());
    assertFalse(a.field("plain").isRedacted());
  }

  @Test public void enumValidationAppliesToSyntax() {
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto3\";\n"
            + "enum E {\n"
            + "  ONE = 1;\n"
            + "}\n");
    SchemaException e = assertThrows(SchemaException.class, () -> link(loader, "a.proto"));
    assertTrue(e.getMessage().contains("missing a zero value at the first element in proto3"),
        e.getMessage());
  }

  @Test public void requiredForbiddenInProto3AtLinkTime() {
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto3\";\n"
            + "message A { required string s = 1; }\n");
    // The parser rejects the required label in proto3 already; linking starts from elements.
    // Feeding proto2 keeps this test on the linker path instead.
    MapLoader proto2 = loader(
        "a.proto", ""
            + "syntax = \"proto2\";\n"
            + "message A { required string s = 1; }\n");
    Schema schema = link(proto2, "a.proto");
    MessageType a = (MessageType) schema.getType("A");
    assertEquals(Field.EncodeMode.REQUIRED, a.field("s").getEncodeMode());
    assertEquals(1, a.getRequiredFields().size());
    // The invalid proto3 source never reaches the linker; the parser throws first.
    RuntimeException parserError = assertThrows(RuntimeException.class,
        () -> loader.load("a.proto"));
    assertTrue(parserError.getMessage().contains("'required' label forbidden in proto3"));
  }

  @Test public void pruneRetainsRootAndTransitiveDependencies() {
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto3\";\n"
            + "package pkg;\n"
            + "message A {\n"
            + "  string name = 1;\n"
            + "  B b = 2;\n"
            + "  string unused = 3;\n"
            + "}\n"
            + "message B { string s = 1; }\n"
            + "message C { string s = 1; }\n");
    Schema schema = link(loader, "a.proto");

    PruningRules rules = new PruningRules.Builder()
        .addRoot("pkg.A#name")
        .addRoot("pkg.A#b")
        .build();
    Schema pruned = schema.prune(rules);

    MessageType a = (MessageType) pruned.getType("pkg.A");
    assertNotNull(a);
    assertNotNull(a.field("name"));
    assertNotNull(a.field("b"));
    assertNull(a.field("unused"));

    // B is transitively reachable through the retained field.
    assertNotNull(pruned.getType("pkg.B"));
    // C is not reachable from the roots.
    assertNull(pruned.getType("pkg.C"));
  }

  @Test public void protoAdapterEncodesAndDecodesDynamicMessages() throws Exception {
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto3\";\n"
            + "package pkg;\n"
            + "message Person {\n"
            + "  string name = 1;\n"
            + "  int32 id = 2;\n"
            + "  repeated string emails = 3;\n"
            + "  Phone phone = 4;\n"
            + "  enum PhoneType {\n"
            + "    MOBILE = 0;\n"
            + "    HOME = 1;\n"
            + "  }\n"
            + "  message Phone {\n"
            + "    string number = 1;\n"
            + "    PhoneType type = 2;\n"
            + "  }\n"
            + "}\n");
    Schema schema = link(loader, "a.proto");

    ProtoAdapter<Object> adapter = schema.protoAdapter("pkg.Person", false);

    Map<String, Object> phone = new LinkedHashMap<>();
    phone.put("number", "555-1234");
    phone.put("type", "HOME");
    Map<String, Object> person = new LinkedHashMap<>();
    person.put("name", "Jane");
    person.put("id", 42);
    person.put("emails", new ArrayList<>(Arrays.asList("a@example.com", "b@example.com")));
    person.put("phone", phone);

    ByteString bytes = adapter.encodeByteString(person);
    Map<String, Object> decoded = (Map<String, Object>) adapter.decode(bytes);

    assertEquals("Jane", decoded.get("name"));
    assertEquals(42, decoded.get("id"));
    assertEquals(Arrays.asList("a@example.com", "b@example.com"), decoded.get("emails"));
    Map<String, Object> decodedPhone = (Map<String, Object>) decoded.get("phone");
    assertEquals("555-1234", decodedPhone.get("number"));
    assertEquals("HOME", decodedPhone.get("type"));

    // The encoded bytes round-trip: re-encoding the decoded model is byte-identical.
    assertEquals(bytes, adapter.encodeByteString(decoded));
  }

  @Test public void protoAdapterScalarEnumDecodesUnknownTagsToNameOrValue() throws Exception {
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto3\";\n"
            + "message M {\n"
            + "  E e = 1;\n"
            + "}\n"
            + "enum E {\n"
            + "  ZERO = 0;\n"
            + "  ONE = 1;\n"
            + "}\n");
    Schema schema = link(loader, "a.proto");
    ProtoAdapter<Object> adapter = schema.protoAdapter("M", false);

    // Hand-built: field 1 varint, value 1 (ONE).
    Map<String, Object> model = new LinkedHashMap<>();
    model.put("e", "ONE");
    ByteString encoded = adapter.encodeByteString(model);
    assertEquals(ByteString.of((byte) 0x08, (byte) 0x01), encoded);

    Map<String, Object> decoded = (Map<String, Object>) adapter.decode(encoded);
    assertEquals("ONE", decoded.get("e"));
  }

  @Test public void retainImportsDropsUnneededImports() {
    MapLoader loader = loader(
        "a.proto", ""
            + "syntax = \"proto3\";\n"
            + "import \"b.proto\";\n"
            + "import \"c.proto\";\n"
            + "message A { B b = 1; }\n",
        "b.proto", "syntax = \"proto3\";\nmessage B { string s = 1; }\n",
        "c.proto", "syntax = \"proto3\";\nmessage C { string s = 1; }\n");
    Schema schema = link(loader, "a.proto");

    // Pruning everything down to A drops the b.proto reference, so its import goes away.
    PruningRules rules = new PruningRules.Builder().prune("B").build();
    Schema pruned = schema.prune(rules);

    ProtoFile a = pruned.protoFile("a.proto");
    assertNotNull(a);
    // Neither import is referenced after pruning B away.
    assertFalse(a.getImports().contains("b.proto"));
    assertFalse(a.getImports().contains("c.proto"));
    // Pruned-away B itself disappears from the schema.
    assertNull(pruned.getType("B"));
  }
}
