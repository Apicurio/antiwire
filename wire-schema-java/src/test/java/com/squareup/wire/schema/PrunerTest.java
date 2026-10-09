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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.wire.SchemaBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Upstream PrunerTest translated (assertk to JUnit 5, buildSchema to SchemaBuilder). */
public class PrunerTest {

  @Test public void retainType() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message MessageA {\n"
            + "}\n"
            + "message MessageB {\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("MessageA")
        .build());
    assertNotNull(pruned.getType("MessageA"));
    assertNull(pruned.getType("MessageB"));
  }

  /**
   * We test that all references of an opaque type are getting pruned correctly.
   */

  @Test public void opaqueTypePrunesItsReferences() {
    Schema schema = new SchemaBuilder()
        .add("source-path/cafe/cafe.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "package cafe;\n"
            + "\n"
            + "message CafeDrink {\n"
            + "  optional int32 size_ounces = 1;\n"
            + "  repeated EspressoShot shots = 2;\n"
            + "}\n"
            + "\n"
            + "message EspressoShot {\n"
            + "  optional Roast roast = 1;\n"
            + "  optional bool decaf = 2;\n"
            + "}\n"
            + "\n"
            + "enum Roast {\n"
            + "  MEDIUM = 1;\n"
            + "  DARK = 2;\n"
            + "}")
        .addOpaqueTypes(ProtoType.get("cafe.EspressoShot"))
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("cafe.CafeDrink")
        .build());
    assertNotNull(pruned.getType("cafe.CafeDrink"));
    // The field should not be pruned, and is of the opaque type `bytes`.
    assertEquals(ProtoType.BYTES, pruned.getField("cafe.CafeDrink", "shots").getType());
    // Types which were originally referred by `shots` are now pruned since the field is opaqued.
    assertNull(pruned.getType("cafe.EspressoShot"));
    assertNull(pruned.getType("cafe.Roast"));
  }

  @Test public void oneOfOptionsAreNotArbitrarilyPruned() {
    Schema schema = new SchemaBuilder()
        .add("test_event.proto", ""
            + "syntax = \"proto3\";\n"
            + "\n"
            + "import \"test_event_custom_option.proto\";\n"
            + "\n"
            + "package test.oneOf.options.test;\n"
            + "\n"
            + "message TestMessage {\n"
            + "  oneof element {\n"
            + "    option (my_custom_oneOf_option) = true;\n"
            + "    string one = 1;\n"
            + "    string two = 2;\n"
            + "  }\n"
            + "}")
        .add("test_event_custom_option.proto", ""
            + "syntax = \"proto3\";\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "package test.oneOf.options;\n"
            + "\n"
            + "extend google.protobuf.OneofOptions {\n"
            + "  bool my_custom_oneOf_option = 101400;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("test.oneOf.options.test.TestMessage")
        .build());
    // spotless:off because spotless will remove the trailing spaces.
    assertEquals(
        ""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: test_event.proto\n"
            + "\n"
            + "syntax = \"proto3\";\n"
            + "\n"
            + "package test.oneOf.options.test;\n"
            + "\n"
            + "import \"test_event_custom_option.proto\";\n"
            + "\n"
            + "message TestMessage {\n"
            + "  oneof element {\n"
            + "    option (my_custom_oneOf_option) = true;\n"
            + "  \n"
            + "    string one = 1;\n"
            + "    string two = 2;\n"
            + "  }\n"
            + "}\n",
        pruned.protoFile("test_event.proto").toSchema());
    // spotless:on
  }

  @Test public void rootCanHandleInlinedOptionWithMapFields() {
    Schema schema = new SchemaBuilder()
        .add("test.proto", ""
            + "syntax = \"proto3\";\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "package wire.issue;\n"
            + "\n"
            + "message Options {\n"
            + "  map<string, ConfigPayload> config = 1;\n"
            + "  map<string, SettingPayload> setting = 2;\n"
            + "  map<string, string> extra = 3;\n"
            + "}\n"
            + "\n"
            + "message ConfigPayload {\n"
            + "  optional string data = 1;\n"
            + "}\n"
            + "\n"
            + "message SettingPayload {\n"
            + "  optional string data = 1;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  repeated Options opt = 80000;\n"
            + "}\n"
            + "\n"
            + "message SomeMessage {\n"
            + "  option (wire.issue.opt) = {\n"
            + "    config: [\n"
            + "      {\n"
            + "        key: \"some_config_key_1\",\n"
            + "        value: { data: \"some_config_data_1\" }\n"
            + "      },\n"
            + "    ],\n"
            + "    setting: [\n"
            + "      {\n"
            + "        key: \"some_setting_key_1\",\n"
            + "        value: { data: \"some_setting_data_1\" }\n"
            + "      },\n"
            + "      {\n"
            + "        key: \"some_setting_key_2\",\n"
            + "        value: { data: \"some_setting_data_2\" }\n"
            + "      },\n"
            + "    ],\n"
            + "    extra: [\n"
            + "      {\n"
            + "        key: \"some_extra_key_1\",\n"
            + "        value: \"some_extra_data_1\"\n"
            + "      },\n"
            + "    ],\n"
            + "  };\n"
            + "\n"
            + "  string id = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("wire.issue.Options#config")
        .build());
    assertEquals(
        ""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: test.proto\n"
            + "\n"
            + "syntax = \"proto3\";\n"
            + "\n"
            + "package wire.issue;\n"
            + "\n"
            + "message Options {\n"
            + "  map<string, ConfigPayload> config = 1;\n"
            + "}\n"
            + "\n"
            + "message ConfigPayload {\n"
            + "  optional string data = 1;\n"
            + "}\n",
        pruned.protoFile("test.proto").toSchema());
  }

  /**
   * Map option entries may omit 'value' when the value type's default is desired. This is common
   * in OpenAPI v2 annotations, e.g. a security requirement that needs bearer auth but no scopes:
   *
   * ```
   * security: { security_requirement: { key: "bearer" } }
   * ```
   *
   * The missing 'value' means an empty SecurityRequirementValue (no scopes). Wire must handle
   * these null map values without crashing during pruning.
   */

  @Test public void rootCanHandleInlinedOptionWithMapFieldsMissingValue() {
    Schema schema = new SchemaBuilder()
        .add("openapiv2.proto", ""
            + "syntax = \"proto3\";\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "package grpc.gateway.protoc_gen_openapiv2.options;\n"
            + "\n"
            + "message SecurityRequirement {\n"
            + "  message SecurityRequirementValue {\n"
            + "    repeated string scope = 1;\n"
            + "  }\n"
            + "  map<string, SecurityRequirementValue> security_requirement = 1;\n"
            + "}\n"
            + "\n"
            + "message Swagger {\n"
            + "  repeated SecurityRequirement security = 1;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.FileOptions {\n"
            + "  Swagger openapiv2_swagger = 80000;\n"
            + "}")
        .add("test.proto", ""
            + "syntax = \"proto3\";\n"
            + "\n"
            + "import \"openapiv2.proto\";\n"
            + "\n"
            + "package test;\n"
            + "\n"
            + "option (grpc.gateway.protoc_gen_openapiv2.options.openapiv2_swagger) = {\n"
            + "  security: { security_requirement: { key: \"bearer\" } }\n"
            + "};")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("grpc.gateway.protoc_gen_openapiv2.options.SecurityRequirement#security_requirement")
        .build());
    assertEquals(
        ""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: openapiv2.proto\n"
            + "\n"
            + "syntax = \"proto3\";\n"
            + "\n"
            + "package grpc.gateway.protoc_gen_openapiv2.options;\n"
            + "\n"
            + "message SecurityRequirement {\n"
            + "  map<string, SecurityRequirementValue> security_requirement = 1;\n"
            + "\n"
            + "  message SecurityRequirementValue {\n"
            + "    repeated string scope = 1;\n"
            + "  }\n"
            + "}\n",
        pruned.protoFile("openapiv2.proto").toSchema());
  }

  @Disabled("Pruning inlined map options is not supported")
  @Test public void pruneCanHandleInlinedOptionMemberWithMapFields() {
    Schema schema = new SchemaBuilder()
        .add("test.proto", ""
            + "syntax = \"proto3\";\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "package wire.issue;\n"
            + "\n"
            + "message Options {\n"
            + "  map<string, ConfigPayload> config = 1;\n"
            + "  map<string, SettingPayload> setting = 2;\n"
            + "  map<string, string> extra = 3;\n"
            + "}\n"
            + "\n"
            + "message ConfigPayload {\n"
            + "  optional string data = 1;\n"
            + "}\n"
            + "\n"
            + "message SettingPayload {\n"
            + "  optional string data = 1;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  repeated Options opt = 80000;\n"
            + "}\n"
            + "\n"
            + "message SomeMessage {\n"
            + "  option (wire.issue.opt) = {\n"
            + "    config: [\n"
            + "      {\n"
            + "        key: \"some_config_key_1\",\n"
            + "        value: { data: \"some_config_data_1\" }\n"
            + "      },\n"
            + "    ],\n"
            + "    setting: [\n"
            + "      {\n"
            + "        key: \"some_setting_key_1\",\n"
            + "        value: { data: \"some_setting_data_1\" }\n"
            + "      },\n"
            + "      {\n"
            + "        key: \"some_setting_key_2\",\n"
            + "        value: { data: \"some_setting_data_2\" }\n"
            + "      },\n"
            + "    ],\n"
            + "    extra: [\n"
            + "      {\n"
            + "        key: \"some_extra_key_1\",\n"
            + "        value: \"some_extra_data_1\"\n"
            + "      },\n"
            + "    ],\n"
            + "  };\n"
            + "\n"
            + "  string id = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("wire.issue.Options#config")
        .build());
    assertEquals(
        ""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: test.proto\n"
            + "\n"
            + "syntax = \"proto3\";\n"
            + "\n"
            + "package wire.issue;\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "message Options {\n"
            + "  map<string, SettingPayload> setting = 2;\n"
            + "\n"
            + "  map<string, string> extra = 3;\n"
            + "}\n"
            + "\n"
            + "message SettingPayload {\n"
            + "  optional string data = 1;\n"
            + "}\n"
            + "\n"
            + "message SomeMessage {\n"
            + "  option (wire.issue.opt) = {\n"
            + "    setting: [\n"
            + "      {\n"
            + "        key: \"some_setting_key_1\",\n"
            + "        value: {\n"
            + "          data: \"some_setting_data_1\"\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        key: \"some_setting_key_2\",\n"
            + "        value: {\n"
            + "          data: \"some_setting_data_2\"\n"
            + "        }\n"
            + "      }\n"
            + "    ],\n"
            + "    extra: [\n"
            + "      {\n"
            + "        key: \"some_extra_key_1\",\n"
            + "        value: \"some_extra_data_1\"\n"
            + "      }\n"
            + "    ]\n"
            + "  };\n"
            + "\n"
            + "  string id = 1;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  repeated Options opt = 80000;\n"
            + "}\n",
        pruned.protoFile("test.proto").toSchema());
  }

  @Disabled("Pruning inlined map options is not supported")
  @Test public void pruneCanHandleInlinedOptionTypeWithMapFields() {
    Schema schema = new SchemaBuilder()
        .add("test.proto", ""
            + "syntax = \"proto3\";\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "package wire.issue;\n"
            + "\n"
            + "message Options {\n"
            + "  map<string, ConfigPayload> config = 1;\n"
            + "  map<string, SettingPayload> setting = 2;\n"
            + "  map<string, string> extra = 3;\n"
            + "}\n"
            + "\n"
            + "message ConfigPayload {\n"
            + "  optional string data = 1;\n"
            + "}\n"
            + "\n"
            + "message SettingPayload {\n"
            + "  optional string data = 1;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  repeated Options opt = 80000;\n"
            + "}\n"
            + "\n"
            + "message SomeMessage {\n"
            + "  option (wire.issue.opt) = {\n"
            + "    config: [\n"
            + "      {\n"
            + "        key: \"some_config_key_1\",\n"
            + "        value: { data: \"some_config_data_1\" }\n"
            + "      },\n"
            + "    ],\n"
            + "    setting: [\n"
            + "      {\n"
            + "        key: \"some_setting_key_1\",\n"
            + "        value: { data: \"some_setting_data_1\" }\n"
            + "      },\n"
            + "      {\n"
            + "        key: \"some_setting_key_2\",\n"
            + "        value: { data: \"some_setting_data_2\" }\n"
            + "      },\n"
            + "    ],\n"
            + "    extra: [\n"
            + "      {\n"
            + "        key: \"some_extra_key_1\",\n"
            + "        value: \"some_extra_data_1\"\n"
            + "      },\n"
            + "    ],\n"
            + "  };\n"
            + "\n"
            + "  string id = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("wire.issue.ConfigPayload")
        .build());
    assertEquals(
        ""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: test.proto\n"
            + "\n"
            + "syntax = \"proto3\";\n"
            + "\n"
            + "package wire.issue;\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "message Options {\n"
            + "  map<string, SettingPayload> setting = 2;\n"
            + "\n"
            + "  map<string, string> extra = 3;\n"
            + "}\n"
            + "\n"
            + "message SettingPayload {\n"
            + "  optional string data = 1;\n"
            + "}\n"
            + "\n"
            + "message SomeMessage {\n"
            + "  option (wire.issue.opt) = {\n"
            + "    setting: [\n"
            + "      {\n"
            + "        key: \"some_setting_key_1\",\n"
            + "        value: {\n"
            + "          data: \"some_setting_data_1\"\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        key: \"some_setting_key_2\",\n"
            + "        value: {\n"
            + "          data: \"some_setting_data_2\"\n"
            + "        }\n"
            + "      }\n"
            + "    ],\n"
            + "    extra: [\n"
            + "      {\n"
            + "        key: \"some_extra_key_1\",\n"
            + "        value: \"some_extra_data_1\"\n"
            + "      }\n"
            + "    ]\n"
            + "  };\n"
            + "\n"
            + "  string id = 1;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  repeated Options opt = 80000;\n"
            + "}\n",
        pruned.protoFile("test.proto").toSchema());
  }

  @Test public void oneOfOptionsArePruned() {
    Schema schema = new SchemaBuilder()
        .add("test_event.proto", ""
            + "syntax = \"proto3\";\n"
            + "\n"
            + "import \"test_event_custom_option.proto\";\n"
            + "\n"
            + "package test.oneOf.options.test;\n"
            + "\n"
            + "message TestMessage {\n"
            + "  oneof element {\n"
            + "    option (my_custom_oneOf_option) = true;\n"
            + "    string one = 1;\n"
            + "    string two = 2;\n"
            + "  }\n"
            + "}")
        .add("test_event_custom_option.proto", ""
            + "syntax = \"proto3\";\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "package test.oneOf.options;\n"
            + "\n"
            + "extend google.protobuf.OneofOptions {\n"
            + "  bool my_custom_oneOf_option = 101400;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("google.protobuf.OneofOptions#test.oneOf.options.my_custom_oneOf_option")
        .build());
    assertEquals(
        ""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: test_event.proto\n"
            + "\n"
            + "syntax = \"proto3\";\n"
            + "\n"
            + "package test.oneOf.options.test;\n"
            + "\n"
            + "message TestMessage {\n"
            + "  oneof element {\n"
            + "    string one = 1;\n"
            + "    string two = 2;\n"
            + "  }\n"
            + "}\n",
        pruned.protoFile("test_event.proto").toSchema());
  }

  @Test public void retainMap() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message MessageA {\n"
            + "  map<string, MessageB> maps = 1;\n"
            + "  message MessageB {\n"
            + "  }\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("MessageA")
        .build());
    assertNotNull(pruned.getType("MessageA"));
    assertNotNull(pruned.getField(ProtoMember.get("MessageA#maps")));
  }

  @Test public void excludeMap() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message MessageA {\n"
            + "  map<string, MessageB> maps = 1;\n"
            + "  message MessageB {\n"
            + "  }\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("MessageA")
        .prune("MessageA#maps")
        .build());
    assertNotNull(pruned.getType("MessageA"));
    assertNull(pruned.getField(ProtoMember.get("MessageA#maps")));
  }

  @Test public void retainTypeRetainsEnclosingButNotNested() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message A {\n"
            + "  message B {\n"
            + "    message C {\n"
            + "    }\n"
            + "  }\n"
            + "  message D {\n"
            + "  }\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("A.B")
        .build());
    assertTrue(pruned.getType("A") instanceof EnclosingType);
    assertTrue(pruned.getType("A.B") instanceof MessageType);
    assertNull(pruned.getType("A.B.C"));
    assertNull(pruned.getType("A.D"));
  }

  @Test public void retainTypeRetainsFieldTypesTransitively() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message MessageA {\n"
            + "  optional MessageB b = 1;\n"
            + "}\n"
            + "message MessageB {\n"
            + "  map<string, MessageC> c = 1;\n"
            + "}\n"
            + "message MessageC {\n"
            + "}\n"
            + "message MessageD {\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("MessageA")
        .build());
    assertNotNull(pruned.getType("MessageA"));
    assertNotNull(pruned.getType("MessageB"));
    assertNotNull(pruned.getType("MessageC"));
    assertNull(pruned.getType("MessageD"));
  }

  @Test public void retainRpcRetainsRequestAndResponseTypes() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message RequestA {\n"
            + "}\n"
            + "message ResponseA {\n"
            + "}\n"
            + "message RequestB {\n"
            + "}\n"
            + "message ResponseB {\n"
            + "}\n"
            + "service Service {\n"
            + "  rpc CallA (RequestA) returns (ResponseA);\n"
            + "  rpc CallB (RequestB) returns (ResponseB);\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Service#CallA")
        .build());
    assertNotNull(pruned.getService("Service").rpc("CallA"));
    assertNotNull(pruned.getType("RequestA"));
    assertNotNull(pruned.getType("ResponseA"));
    assertNull(pruned.getService("Service").rpc("CallB"));
    assertNull(pruned.getType("RequestB"));
    assertNull(pruned.getType("ResponseB"));
  }

  @Test public void retainField() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message MessageA {\n"
            + "  optional string b = 1;\n"
            + "  map<string, string> c = 2;\n"
            + "}\n"
            + "message MessageB {\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("MessageA#b")
        .build());
    assertNotNull(((MessageType) pruned.getType("MessageA")).field("b"));
    assertNull(((MessageType) pruned.getType("MessageA")).field("c"));
    assertNull(pruned.getType("MessageB"));
  }

  @Test public void retainFieldRetainsFieldTypesTransitively() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message MessageA {\n"
            + "  optional MessageB b = 1;\n"
            + "  optional MessageD d = 2;\n"
            + "}\n"
            + "message MessageB {\n"
            + "  optional MessageC c = 1;\n"
            + "}\n"
            + "message MessageC {\n"
            + "}\n"
            + "message MessageD {\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("MessageA#b")
        .build());
    assertNotNull(pruned.getType("MessageA"));
    assertNotNull(((MessageType) pruned.getType("MessageA")).field("b"));
    assertNull(((MessageType) pruned.getType("MessageA")).field("d"));
    assertNotNull(pruned.getType("MessageB"));
    assertNotNull(pruned.getType("MessageC"));
    assertNull(pruned.getType("MessageD"));
  }

  @Test public void oneOf() {
    Schema schema = new SchemaBuilder()
        .add("one_of_message.proto", ""
            + "package oneof;\n"
            + "\n"
            + "message OneOfMessage {\n"
            + "  oneof choice {\n"
            + "    int32 foo = 1;\n"
            + "    string bar = 3;\n"
            + "    string baz = 4;\n"
            + "  }\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("oneof.OneOfMessage")
        .build());
    List<OneOf> oneOfs = ((MessageType) pruned.getType("oneof.OneOfMessage")).getOneOfs();
    assertFalse(oneOfs.isEmpty());
    assertEquals(3, oneOfs.get(0).getFields().size());
  }

  @Test public void retainFieldPrunesOneOf() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message Message {\n"
            + "  oneof selection {\n"
            + "    string a = 1;\n"
            + "    string b = 2;\n"
            + "  }\n"
            + "  optional string c = 3;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message#c")
        .build());
    assertTrue(((MessageType) pruned.getType("Message")).getOneOfs().isEmpty());
  }

  @Test public void retainFieldRetainsOneOf() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message Message {\n"
            + "  oneof selection {\n"
            + "    string a = 1;\n"
            + "    string b = 2;\n"
            + "  }\n"
            + "  optional string c = 3;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message#b")
        .build());
    MessageType message = (MessageType) pruned.getType("Message");
    assertEquals(1, message.getOneOfs().size());
    OneOf onlyOneOf = message.getOneOfs().get(0);
    assertEquals("selection", onlyOneOf.getName());
    assertEquals(1, onlyOneOf.getFields().size());
    assertEquals("b", onlyOneOf.getFields().get(0).getName());
    assertNull(message.field("a"));
    assertNull(message.field("c"));
  }

  @Test public void typeWithRetainedMembersOnlyHasThoseMembersRetained() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message MessageA {\n"
            + "  optional MessageB b = 1;\n"
            + "}\n"
            + "message MessageB {\n"
            + "  optional MessageC c = 1;\n"
            + "  optional MessageD d = 2;\n"
            + "}\n"
            + "message MessageC {\n"
            + "}\n"
            + "message MessageD {\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("MessageA#b")
        .addRoot("MessageB#c")
        .build());
    assertNotNull(pruned.getType("MessageA"));
    assertNotNull(((MessageType) pruned.getType("MessageA")).field("b"));
    assertNotNull(pruned.getType("MessageB"));
    assertNotNull(((MessageType) pruned.getType("MessageB")).field("c"));
    assertNull(((MessageType) pruned.getType("MessageB")).field("d"));
    assertNotNull(pruned.getType("MessageC"));
    assertNull(pruned.getType("MessageD"));
  }

  @Test public void retainEnumConstant() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "enum Roshambo {\n"
            + "  ROCK = 0;\n"
            + "  SCISSORS = 1;\n"
            + "  PAPER = 2;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Roshambo#SCISSORS")
        .build());
    assertNull(((EnumType) pruned.getType("Roshambo")).constant("ROCK"));
    assertNotNull(((EnumType) pruned.getType("Roshambo")).constant("SCISSORS"));
    assertNull(((EnumType) pruned.getType("Roshambo")).constant("PAPER"));
  }

  @Test public void enumWithRetainedConstantHasThatConstantRetained() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message Message {\n"
            + "  optional Roshambo roshambo = 1;\n"
            + "}\n"
            + "enum Roshambo {\n"
            + "  ROCK = 0;\n"
            + "  SCISSORS = 1;\n"
            + "  PAPER = 2;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .addRoot("Roshambo#SCISSORS")
        .build());
    assertNotNull(pruned.getType("Message"));
    assertNotNull(((MessageType) pruned.getType("Message")).field("roshambo"));
    assertNotNull(pruned.getType("Roshambo"));
    assertNull(((EnumType) pruned.getType("Roshambo")).constant("ROCK"));
    assertNotNull(((EnumType) pruned.getType("Roshambo")).constant("SCISSORS"));
    assertNull(((EnumType) pruned.getType("Roshambo")).constant("PAPER"));
  }

  @Test public void retainedOptionRetainsOptionsType() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional string a = 22001;\n"
            + "}\n"
            + "message Message {\n"
            + "  optional string f = 1 [a = \"a\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message#f")
        .build());
    assertNotNull(((MessageType) pruned.getType("Message")).field("f"));
    assertNotNull(((MessageType) pruned.getType("google.protobuf.FieldOptions")));
  }

  @Test public void prunedExtensionOptionDoesNotRetainExtension() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional string a = 22001;\n"
            + "}\n"
            + "message Message {\n"
            + "  optional string f = 1 [a = \"a\"];\n"
            + "  optional string g = 2;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message#g")
        .build());

    MessageType fieldOptions = (MessageType) pruned.getType("google.protobuf.FieldOptions");
    assertNull(fieldOptions.extensionField("a"));

    ProtoFile service = pruned.protoFile("service.proto");
    assertTrue(service.getExtendList().isEmpty());
  }

  @Test public void optionRetainsField() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "message SomeFieldOptions {\n"
            + "  optional string a = 1; // Retained via option use.\n"
            + "  optional string b = 2; // Retained explicitly.\n"
            + "  optional string c = 3; // Should be pruned.\n"
            + "}\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional SomeFieldOptions some_field_options = 22001;\n"
            + "}\n"
            + "message Message {\n"
            + "  optional string f = 1 [some_field_options.a = \"a\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .addRoot("SomeFieldOptions#b")
        .build());
    assertNotNull(((MessageType) pruned.getType("Message")).field("f"));
    assertNotNull(((MessageType) pruned.getType("SomeFieldOptions")).field("a"));
    assertNotNull(((MessageType) pruned.getType("SomeFieldOptions")).field("b"));
    assertNull(((MessageType) pruned.getType("SomeFieldOptions")).field("c"));
  }

  @Test public void optionRetainsType() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "message SomeFieldOptions {\n"
            + "  optional string a = 1; // Retained via option use.\n"
            + "  optional string b = 2; // Retained because 'a' is retained.\n"
            + "  optional string c = 3; // Retained because 'a' is retained.\n"
            + "}\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional SomeFieldOptions some_field_options = 22001;\n"
            + "}\n"
            + "message Message {\n"
            + "  optional string f = 1 [some_field_options.a = \"a\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());
    assertNotNull(((MessageType) pruned.getType("Message")).field("f"));
    assertNotNull(((MessageType) pruned.getType("SomeFieldOptions")).field("a"));
    assertNotNull(((MessageType) pruned.getType("SomeFieldOptions")).field("b")); // TODO
    assertNotNull(((MessageType) pruned.getType("SomeFieldOptions")).field("c"));
  }

  @Test public void retainExtension() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message Message {\n"
            + "  optional string a = 1;\n"
            + "}\n"
            + "extend Message {\n"
            + "  optional string b = 2;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());
    assertNotNull(((MessageType) pruned.getType("Message")).field("a"));
    assertNotNull(((MessageType) pruned.getType("Message")).extensionField("b"));
  }

  @Test public void retainExtensionMembers() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message Message {\n"
            + "  optional string a = 1;\n"
            + "  optional string b = 2;\n"
            + "}\n"
            + "extend Message {\n"
            + "  optional string c = 3;\n"
            + "  optional string d = 4;\n"
            + "  repeated string e = 5;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message#a")
        .addRoot("Message#c")
        .build());
    assertNotNull(((MessageType) pruned.getType("Message")).field("a"));
    assertNull(((MessageType) pruned.getType("Message")).field("b"));
    assertNotNull(((MessageType) pruned.getType("Message")).extensionField("c"));
    assertNull(((MessageType) pruned.getType("Message")).extensionField("d"));
    assertNull(((MessageType) pruned.getType("Message")).extensionField("e"));
  }

  @Test public void retainingTypeRetainsExtensionMembers() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message Message {\n"
            + "  optional string a = 1;\n"
            + "  optional string b = 2;\n"
            + "}\n"
            + "extend Message {\n"
            + "  optional string c = 3;\n"
            + "  optional string d = 4;\n"
            + "  repeated string e = 5;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());
    assertNotNull(((MessageType) pruned.getType("Message")).field("a"));
    assertNotNull(((MessageType) pruned.getType("Message")).field("b"));
    assertNotNull(((MessageType) pruned.getType("Message")).extensionField("c"));
    assertNotNull(((MessageType) pruned.getType("Message")).extensionField("d"));
    assertNotNull(((MessageType) pruned.getType("Message")).extensionField("e"));
  }

  @Test public void includeExtensionMemberPrunesPeerMembers() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message Message {\n"
            + "  optional string a = 1;\n"
            + "  optional string b = 2;\n"
            + "}\n"
            + "extend Message {\n"
            + "  optional string c = 3;\n"
            + "  optional string d = 4;\n"
            + "  repeated string e = 5;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message#c")
        .build());
    assertNull(((MessageType) pruned.getType("Message")).field("a"));
    assertNull(((MessageType) pruned.getType("Message")).field("b"));
    assertNotNull(((MessageType) pruned.getType("Message")).extensionField("c"));
    assertNull(((MessageType) pruned.getType("Message")).extensionField("d"));
    assertNull(((MessageType) pruned.getType("Message")).extensionField("e"));
  }

  @Test public void namespacedExtensionFieldsAreRetained() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "package squareup;\n"
            + "\n"
            + "message ExternalMessage {\n"
            + "  extensions 100 to 200;\n"
            + "\n"
            + "  optional float f = 1;\n"
            + "}\n"
            + "\n"
            + "message Message {\n"
            + "  optional string a = 1;\n"
            + "  optional ExternalMessage external_message = 2;\n"
            + "}\n"
            + "\n"
            + "extend ExternalMessage {\n"
            + "  repeated int32 extension_field = 121;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("squareup.Message")
        .build());
    MessageType message = (MessageType) pruned.getType("squareup.Message");
    assertNotNull(message.field("a"));
    assertNotNull(message.field("external_message"));
    MessageType externalMessage = (MessageType) pruned.getType("squareup.ExternalMessage");
    assertNotNull(externalMessage.field("f"));
    assertNotNull(externalMessage.extensionField("squareup.extension_field"));
  }

  /** When we include excludes only, the mark phase is skipped.  */

  @Test public void excludeWithoutInclude() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message MessageA {\n"
            + "  optional string b = 1;\n"
            + "  optional string c = 2;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("MessageA#c")
        .build());
    assertNotNull(((MessageType) pruned.getType("MessageA")).field("b"));
    assertNull(((MessageType) pruned.getType("MessageA")).field("c"));
  }

  @Test public void excludeField() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message MessageA {\n"
            + "  optional string b = 1;\n"
            + "  optional string c = 2;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("MessageA")
        .prune("MessageA#c")
        .build());
    assertNotNull(((MessageType) pruned.getType("MessageA")).field("b"));
    assertNull(((MessageType) pruned.getType("MessageA")).field("c"));
  }

  @Test public void excludeTypeExcludesField() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message MessageA {\n"
            + "  optional MessageB b = 1;\n"
            + "  map<string, MessageC> c = 2;\n"
            + "}\n"
            + "message MessageB {\n"
            + "}\n"
            + "message MessageC {\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("MessageA")
        .prune("MessageC")
        .build());
    assertNotNull(pruned.getType("MessageB"));
    assertNotNull(((MessageType) pruned.getType("MessageA")).field("b"));
    assertNull(pruned.getType("MessageC"));
    assertNull(((MessageType) pruned.getType("MessageA")).field("c"));
  }

  @Test public void excludeTypeExcludesRpc() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "service ServiceA {\n"
            + "  rpc CallB (MessageB) returns (MessageB);\n"
            + "  rpc CallC (MessageC) returns (MessageC);\n"
            + "}\n"
            + "message MessageB {\n"
            + "}\n"
            + "message MessageC {\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("ServiceA")
        .prune("MessageC")
        .build());
    assertNotNull(pruned.getType("MessageB"));
    assertNotNull(pruned.getService("ServiceA").rpc("CallB"));
    assertNull(pruned.getType("MessageC"));
    assertNull(pruned.getService("ServiceA").rpc("CallC"));
  }

  @Test public void excludeRpcExcludesTypes() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "service ServiceA {\n"
            + "  rpc CallB (MessageB) returns (MessageB);\n"
            + "  rpc CallC (MessageC) returns (MessageC);\n"
            + "}\n"
            + "message MessageB {\n"
            + "}\n"
            + "message MessageC {\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("ServiceA")
        .prune("ServiceA#CallC")
        .build());
    assertNotNull(pruned.getType("MessageB"));
    assertNotNull(pruned.getService("ServiceA").rpc("CallB"));
    assertNull(pruned.getType("MessageC"));
    assertNull(pruned.getService("ServiceA").rpc("CallC"));
  }

  @Test public void excludeFieldExcludesTypes() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "message MessageA {\n"
            + "  optional MessageB b = 1;\n"
            + "  optional MessageC c = 2;\n"
            + "  map<string, MessageD> d = 3;\n"
            + "}\n"
            + "message MessageB {\n"
            + "}\n"
            + "message MessageC {\n"
            + "}\n"
            + "message MessageD {\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("MessageA")
        .prune("MessageA#c")
        .prune("MessageA#d")
        .build());
    assertNotNull(((MessageType) pruned.getType("MessageA")).field("b"));
    assertNotNull(pruned.getType("MessageB"));
    assertNull(((MessageType) pruned.getType("MessageA")).field("c"));
    assertNull(pruned.getType("MessageC"));
    assertNull(((MessageType) pruned.getType("MessageA")).field("d"));
    assertNull(pruned.getType("MessageD"));
  }

  @Test public void excludeEnumExcludesOptions() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "enum Enum {\n"
            + "  A = 0;\n"
            + "  B = 1  [message.c = 1];\n"
            + "}\n"
            + "extend google.protobuf.EnumValueOptions {\n"
            + "  optional Message message = 70000;\n"
            + "};\n"
            + "message Message {\n"
            + "  optional int32 c = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Enum")
        .prune("Enum#B")
        .build());
    assertNotNull(((EnumType) pruned.getType("Enum")).constant("A"));
    assertNull(((EnumType) pruned.getType("Enum")).constant("B"));
    assertNull(pruned.getType("Message"));
  }

  @Test public void excludedFieldPrunesTopLevelOption() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional string a = 22001;\n"
            + "  optional string b = 22002;\n"
            + "}\n"
            + "message Message {\n"
            + "  optional string f = 1 [a = \"a\", b = \"b\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("google.protobuf.FieldOptions#b")
        .build());
    Field field = ((MessageType) pruned.getType("Message")).field("f");
    assertEquals("a", field.getOptions().get(ProtoMember.get(Options.FIELD_OPTIONS, "a")));
    assertNull(field.getOptions().get(ProtoMember.get(Options.FIELD_OPTIONS, "b")));
  }

  @Test public void excludedTypePrunesTopLevelOption() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "message SomeFieldOptions {\n"
            + "  optional string a = 1;\n"
            + "}\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional SomeFieldOptions some_field_options = 22001;\n"
            + "  optional string b = 22002;\n"
            + "}\n"
            + "message Message {\n"
            + "  optional string f = 1 [some_field_options.a = \"a\", b = \"b\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("SomeFieldOptions")
        .build());
    Field field = ((MessageType) pruned.getType("Message")).field("f");
    Map<ProtoMember, Object> map = field.getOptions().getMap();
    assertEquals(1, map.size());
    Map.Entry<ProtoMember, Object> onlyOption = map.entrySet().iterator().next();
    assertEquals("b", onlyOption.getKey().getMember());
    assertEquals("b", onlyOption.getValue());
  }

  @Test public void excludedFieldPrunesNestedOption() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "message SomeFieldOptions {\n"
            + "  optional string a = 1;\n"
            + "  optional string b = 2;\n"
            + "}\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional SomeFieldOptions some_field_options = 22001;\n"
            + "}\n"
            + "message Message {\n"
            + "  optional string f = 1 [some_field_options = { a: \"a\", b: \"b\" }];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("SomeFieldOptions#b")
        .build());
    Field field = ((MessageType) pruned.getType("Message")).field("f");
    Map<?, ?> map = (Map<?, ?>) field.getOptions().get(ProtoMember.get(Options.FIELD_OPTIONS, "some_field_options"));
    assertEquals(1, map.size());
    Map.Entry<?, ?> onlyOption = map.entrySet().iterator().next();
    assertEquals("a", ((ProtoMember) onlyOption.getKey()).getMember());
    assertEquals("a", onlyOption.getValue());
  }

  @Test public void prunedFieldDocumentationsGetPruned() {
    Schema schema = new SchemaBuilder()
        .add("period.proto", ""
            + "enum Period {\n"
            + "  /* This is A. */\n"
            + "  A = 1;\n"
            + "\n"
            + "  /* This is reserved. */\n"
            + "  reserved 2;\n"
            + "\n"
            + "  /* This is C. */\n"
            + "  C = 3;\n"
            + "}")
        .build();

    Schema pruned = schema.prune(new PruningRules.Builder().build());

    EnumType type = (EnumType) pruned.getType("Period");
    assertEquals("This is A.", type.constant("A").getDocumentation());
    assertEquals("This is C.", type.constant("C").getDocumentation());
  }

  @Test public void excludedTypePrunesNestedOption() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "message SomeFieldOptions {\n"
            + "  optional Dimensions dimensions = 1;\n"
            + "}\n"
            + "message Dimensions {\n"
            + "  optional string length = 1;\n"
            + "  optional string width = 2;\n"
            + "}\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional SomeFieldOptions some_field_options = 22001;\n"
            + "  optional string b = 22002;\n"
            + "}\n"
            + "message Message {\n"
            + "  optional string f = 1 [\n"
            + "      some_field_options = {\n"
            + "          dimensions: { length: \"100\" }\n"
            + "      },\n"
            + "      b = \"b\"\n"
            + "  ];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("Dimensions")
        .build());
    Field field = ((MessageType) pruned.getType("Message")).field("f");
    Map<ProtoMember, Object> map = field.getOptions().getMap();
    assertEquals(1, map.size());
    Map.Entry<ProtoMember, Object> onlyOption = map.entrySet().iterator().next();
    assertEquals("b", onlyOption.getKey().getMember());
    assertEquals("b", onlyOption.getValue());
  }

  @Test public void excludeOptions() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional string a = 22001;\n"
            + "  optional string b = 22002;\n"
            + "}\n"
            + "message Message {\n"
            + "  optional string f = 1 [ a = \"a\", b = \"b\" ];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("google.protobuf.FieldOptions")
        .build());
    Field field = ((MessageType) pruned.getType("Message")).field("f");
    assertTrue(field.getOptions().getMap().isEmpty());
  }

  @Test public void excludeOneOfOptions() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "syntax = \"proto3\";\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "extend google.protobuf.OneofOptions {\n"
            + "  string my_oneof_option = 22101;\n"
            + "}\n"
            + "message Message {\n"
            + "  oneof choice {\n"
            + "    option (my_oneof_option) = \"Well done\";\n"
            + "\n"
            + "    string one = 1;\n"
            + "    string two = 2;\n"
            + "  }\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("google.protobuf.OneofOptions")
        .build());
    OneOf oneOf = ((MessageType) pruned.getType("Message")).getOneOfs().get(0);
    assertTrue(oneOf.getOptions().getMap().isEmpty());
  }

  @Test public void excludeRepeatedOptions() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  repeated string a = 22001;\n"
            + "  repeated string b = 22002;\n"
            + "}\n"
            + "message Message {\n"
            + "  option (a) = \"a1\";\n"
            + "  option (a) = \"a2\";\n"
            + "  option (b) = \"b1\";\n"
            + "  option (b) = \"b2\";\n"
            + "  optional string f = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("google.protobuf.MessageOptions#a")
        .build());
    MessageType message = (MessageType) pruned.getType("Message");
    assertNull(message.getOptions().get(ProtoMember.get(Options.MESSAGE_OPTIONS, "a")));
    assertEquals(Arrays.asList("b1", "b2"), message.getOptions().get(ProtoMember.get(Options.MESSAGE_OPTIONS, "b")));
  }

  @Test public void includePackage() {
    Schema schema = new SchemaBuilder()
        .add("a/b/messages.proto", ""
            + "package a.b;\n"
            + "message MessageAB {\n"
            + "}")
        .add("a/c/messages.proto", ""
            + "package a.c;\n"
            + "message MessageAC {\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("a.b.*")
        .build());
    assertNotNull(pruned.getType("a.b.MessageAB"));
    assertNull(pruned.getType("a.c.MessageAC"));
  }

  @Test public void excludePackage() {
    Schema schema = new SchemaBuilder()
        .add("a/b/messages.proto", ""
            + "package a.b;\n"
            + "message MessageAB {\n"
            + "}")
        .add("a/c/messages.proto", ""
            + "package a.c;\n"
            + "message MessageAC {\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("a.c.*")
        .build());
    assertNotNull(pruned.getType("a.b.MessageAB"));
    assertNull(pruned.getType("a.c.MessageAC"));
  }

  @Test public void specialOptionsNotPruned() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "option java_package = \"p\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional int32 a = 1 [deprecated = true, default = 5];\n"
            + "  repeated int32 b = 2 [packed = true, deprecated = true];\n"
            + "}\n"
            + "enum Enum {\n"
            + "  option allow_alias = true;\n"
            + "  option deprecated = true;\n"
            + "  A = 1;\n"
            + "  B = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("google.protobuf.*")
        .build());
    ProtoFile protoFile = pruned.protoFile("message.proto");
    assertEquals("p", protoFile.javaPackage());

    MessageType message = (MessageType) pruned.getType("Message");
    Field fieldA = message.field("a");
    assertEquals("5", fieldA.getDefault());
    assertTrue(fieldA.isDeprecated());
    Field fieldB = message.field("b");
    assertTrue(fieldB.isDeprecated());
    assertTrue(fieldB.isPacked());

    EnumType enumType = (EnumType) pruned.getType("Enum");
    assertTrue(enumType.allowAlias());
    assertTrue(enumType.isDeprecated());
  }

  @Test public void excludeUnusedImports() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import 'footer.proto';\n"
            + "import 'title.proto';\n"
            + "\n"
            + "message Message {\n"
            + "  optional Title title = 1;\n"
            + "}")
        .add("title.proto", ""
            + "message Title {\n"
            + "  optional string label = 1;\n"
            + "}")
        .add("footer.proto", ""
            + "message Footer {\n"
            + "  optional string label = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());

    assertTrue(pruned.protoFile("footer.proto").getTypes().isEmpty());
    assertFalse(pruned.protoFile("title.proto").getTypes().isEmpty());

    ProtoFile message = pruned.protoFile("message.proto");
    assertEquals(Arrays.asList("title.proto"), message.getImports());
  }

  @Test public void excludeUnusedPublicImports() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import public 'footer.proto';\n"
            + "import public 'title.proto';\n"
            + "\n"
            + "message Message {\n"
            + "}")
        .add("title.proto", ""
            + "message Title {\n"
            + "  optional string label = 1;\n"
            + "}")
        .add("footer.proto", ""
            + "message Footer {\n"
            + "  optional string label = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .addRoot("Title")
        .build());

    assertTrue(pruned.protoFile("footer.proto").getTypes().isEmpty());
    assertFalse(pruned.protoFile("title.proto").getTypes().isEmpty());

    ProtoFile message = pruned.protoFile("message.proto");
    assertEquals(Arrays.asList("title.proto"), message.getPublicImports());
  }

  /**
   * We had a bug in import pruning where we retained imports if the files were non-empty,
   * even if those imports were unnecessary.
   */

  @Test public void importPruningIsPrecise() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import 'footer.proto';\n"
            + "\n"
            + "message Message {\n"
            + "  optional string label = 1;\n"
            + "}\n"
            + "\n"
            + "message AnotherMessage {\n"
            + "  optional Footer footer = 1;\n"
            + "}")
        .add("footer.proto", ""
            + "message Footer {\n"
            + "  optional string label = 1;\n"
            + "}\n"
            + "\n"
            + "message Shoe {\n"
            + "  optional string label = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .addRoot("Shoe")
        .build());

    assertFalse(pruned.protoFile("footer.proto").getTypes().isEmpty());

    ProtoFile message = pruned.protoFile("message.proto");
    assertTrue(message.getImports().isEmpty());
  }

  @Test public void retainImportWhenUsedForMessageField() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import 'title.proto';\n"
            + "import 'footer.proto';\n"
            + "\n"
            + "message Message {\n"
            + "  optional Title title = 1;\n"
            + "}\n"
            + "\n"
            + "message AnotherMessage {\n"
            + "  optional Footer footer = 1;\n"
            + "}")
        .add("title.proto", ""
            + "message Title {\n"
            + "  optional string label = 1;\n"
            + "}")
        .add("footer.proto", ""
            + "message Footer {\n"
            + "  optional string label = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());

    ProtoFile message = pruned.protoFile("message.proto");
    assertEquals(Arrays.asList("title.proto"), message.getImports());
  }

  @Test public void retainImportWhenUsedForMessageOneOf() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import 'title.proto';\n"
            + "import 'footer.proto';\n"
            + "\n"
            + "message Message {\n"
            + "  oneof title_value {\n"
            + "    Title title = 1;\n"
            + "    string value = 2;\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "message AnotherMessage {\n"
            + "  oneof footer_value {\n"
            + "    Footer footer = 1;\n"
            + "    string value = 2;\n"
            + "  }\n"
            + "}")
        .add("title.proto", ""
            + "message Title {\n"
            + "  optional string label = 1;\n"
            + "}")
        .add("footer.proto", ""
            + "message Footer {\n"
            + "  optional string label = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());

    ProtoFile message = pruned.protoFile("message.proto");
    assertEquals(Arrays.asList("title.proto"), message.getImports());
  }

  @Test public void retainImportWhenUsedForServiceRpc() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "import 'call.proto';\n"
            + "import 'another_call.proto';\n"
            + "\n"
            + "service Service {\n"
            + "  rpc Call (CallRequest) returns (CallResponse);\n"
            + "  rpc AnotherCall (AnotherCallRequest) returns (AnotherCallResponse);\n"
            + "}")
        .add("call.proto", ""
            + "message CallRequest {\n"
            + "}\n"
            + "\n"
            + "message CallResponse {\n"
            + "}")
        .add("another_call.proto", ""
            + "message AnotherCallRequest {\n"
            + "}\n"
            + "\n"
            + "message AnotherCallResponse {\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Service#Call")
        .build());

    ProtoFile service = pruned.protoFile("service.proto");
    assertEquals(Arrays.asList("call.proto"), service.getImports());
  }

  @Test public void retainImportWhenUsedForExtendedMessage() {
    Schema schema = new SchemaBuilder()
        .add("extension.proto", ""
            + "import 'message.proto';\n"
            + "import 'title.proto';\n"
            + "import 'footer.proto';\n"
            + "\n"
            + "extend Message {\n"
            + "  optional Title title = 2;\n"
            + "}\n"
            + "\n"
            + "extend AnotherMessage {\n"
            + "  optional Footer footer = 2;\n"
            + "}")
        .add("message.proto", ""
            + "message Message {\n"
            + "  optional string value = 1;\n"
            + "}\n"
            + "\n"
            + "message AnotherMessage {\n"
            + "  optional string value = 1;\n"
            + "}")
        .add("title.proto", ""
            + "message Title {\n"
            + "  optional string label = 1;\n"
            + "}")
        .add("footer.proto", ""
            + "message Footer {\n"
            + "  optional string label = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());

    ProtoFile extension = pruned.protoFile("extension.proto");
    assertEquals(Arrays.asList("message.proto", "title.proto"), extension.getImports());
  }

  @Test public void retainImportWhenUsedForExtendingOptions() {
    Schema schema = new SchemaBuilder()
        .add("extension.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  optional string value = 10000;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("google.protobuf.MessageOptions")
        .build());

    ProtoFile extension = pruned.protoFile("extension.proto");
    assertEquals(Arrays.asList("google/protobuf/descriptor.proto"), extension.getImports());
  }

  @Test public void retainImportWhenUsedInMaps() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import 'title.proto';\n"
            + "import 'footer.proto';\n"
            + "\n"
            + "message OuterMessage {\n"
            + "  map<int32, Title> titles = 1;\n"
            + "\n"
            + "  message InnerMessage {\n"
            + "    map <string, Footer> footers = 1;\n"
            + "  }\n"
            + "}")
        .add("title.proto", ""
            + "message Title {\n"
            + "  optional string label = 1;\n"
            + "}")
        .add("footer.proto", ""
            + "message Footer {\n"
            + "  optional string label = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("OuterMessage")
        .build());

    ProtoFile message = pruned.protoFile("message.proto");
    assertEquals(Arrays.asList("title.proto"), message.getImports());
  }

  @Test public void retainImportWhenUsedInMapsWithinInnerTypes() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import 'title.proto';\n"
            + "import 'footer.proto';\n"
            + "\n"
            + "message OuterMessage {\n"
            + "  map<int32, Title> titles = 1;\n"
            + "\n"
            + "  message InnerMessage {\n"
            + "    map <string, Footer> footers = 1;\n"
            + "  }\n"
            + "}")
        .add("title.proto", ""
            + "message Title {\n"
            + "  optional string label = 1;\n"
            + "}")
        .add("footer.proto", ""
            + "message Footer {\n"
            + "  optional string label = 1;\n"
            + "}")
        .build();

    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("OuterMessage.InnerMessage")
        .build());

    ProtoFile message = pruned.protoFile("message.proto");
    assertEquals(Arrays.asList("footer.proto"), message.getImports());
  }

  @Test public void retainImportWhenUsedForNestedMessageField() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import 'title.proto';\n"
            + "import 'footer.proto';\n"
            + "\n"
            + "message Outer {\n"
            + "  message Message {\n"
            + "    optional Title title = 1;\n"
            + "  }\n"
            + "\n"
            + "  message AnotherMessage {\n"
            + "    optional Footer footer = 1;\n"
            + "  }\n"
            + "}")
        .add("title.proto", ""
            + "message Title {\n"
            + "  optional string label = 1;\n"
            + "}")
        .add("footer.proto", ""
            + "message Footer {\n"
            + "  optional string label = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Outer.Message")
        .build());

    ProtoFile message = pruned.protoFile("message.proto");
    assertEquals(Arrays.asList("title.proto"), message.getImports());
  }

  @Test public void retainImportWhenUsedForNestedExtendedMessage() {
    Schema schema = new SchemaBuilder()
        .add("extension.proto", ""
            + "import 'message.proto';\n"
            + "import 'title.proto';\n"
            + "import 'footer.proto';\n"
            + "\n"
            + "message Outer {\n"
            + "  extend Message {\n"
            + "    optional Title title = 2;\n"
            + "  }\n"
            + "\n"
            + "  extend AnotherMessage {\n"
            + "    optional Footer footer = 2;\n"
            + "  }\n"
            + "}")
        .add("message.proto", ""
            + "message Message {\n"
            + "  optional string value = 1;\n"
            + "}\n"
            + "\n"
            + "message AnotherMessage {\n"
            + "  optional string value = 1;\n"
            + "}")
        .add("title.proto", ""
            + "message Title {\n"
            + "  optional string label = 1;\n"
            + "}")
        .add("footer.proto", ""
            + "message Footer {\n"
            + "  optional string label = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());

    ProtoFile extension = pruned.protoFile("extension.proto");
    assertEquals(Arrays.asList("title.proto"), extension.getImports());
  }

  @Test public void retainImportWhenUsedForFileOption() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import 'option.proto';\n"
            + "\n"
            + "option custom_option = true;\n"
            + "\n"
            + "message Message {\n"
            + "}")
        .add("option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.FileOptions {\n"
            + "  optional bool custom_option = 10000;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());

    ProtoFile message = pruned.protoFile("message.proto");
    assertEquals(Arrays.asList("option.proto"), message.getImports());
  }

  @Test public void retainImportWhenUsedForMessageOption() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import 'option.proto';\n"
            + "import 'another_option.proto';\n"
            + "\n"
            + "message Message {\n"
            + "  option custom_option = true;\n"
            + "}\n"
            + "\n"
            + "message AnotherMessage {\n"
            + "  option another_custom_option = true;\n"
            + "}")
        .add("option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  optional bool custom_option = 10000;\n"
            + "}")
        .add("another_option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  optional bool another_custom_option = 10001;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());

    ProtoFile message = pruned.protoFile("message.proto");
    assertEquals(Arrays.asList("option.proto"), message.getImports());
  }

  @Test public void retainImportWhenUsedForFieldOption() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import 'option.proto';\n"
            + "import 'another_option.proto';\n"
            + "\n"
            + "message Message {\n"
            + "  optional string value = 1 [custom_option = true];\n"
            + "}\n"
            + "\n"
            + "message AnotherMessage {\n"
            + "  optional string value = 1 [another_custom_option = true];\n"
            + "}")
        .add("option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional bool custom_option = 10000;\n"
            + "}")
        .add("another_option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional bool another_custom_option = 10001;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());

    ProtoFile message = pruned.protoFile("message.proto");
    assertEquals(Arrays.asList("option.proto"), message.getImports());
  }

  @Test public void retainImportWhenUsedForOneOfOption() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import 'option.proto';\n"
            + "import 'another_option.proto';\n"
            + "\n"
            + "message Message {\n"
            + "  oneof value {\n"
            + "    option custom_option = true;\n"
            + "    string value_1 = 1;\n"
            + "    string value_2 = 2;\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "message AnotherMessage {\n"
            + "  oneof value {\n"
            + "    option another_custom_option = true;\n"
            + "    string value_1 = 1;\n"
            + "    string value_2 = 2;\n"
            + "  }\n"
            + "}")
        .add("option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.OneofOptions {\n"
            + "  optional bool custom_option = 10000;\n"
            + "}")
        .add("another_option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.OneofOptions {\n"
            + "  optional bool another_custom_option = 10001;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());

    ProtoFile message = pruned.protoFile("message.proto");
    assertEquals(Arrays.asList("option.proto"), message.getImports());
  }

  @Test public void retainImportWhenUsedForOneOfFieldOption() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import 'option.proto';\n"
            + "import 'another_option.proto';\n"
            + "\n"
            + "message Message {\n"
            + "  oneof value {\n"
            + "    string value_1 = 1 [custom_option = true];\n"
            + "    string value_2 = 2;\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "message AnotherMessage {\n"
            + "  oneof value {\n"
            + "    string value_1 = 1 [another_custom_option = true];\n"
            + "    string value_2 = 2;\n"
            + "  }\n"
            + "}")
        .add("option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional bool custom_option = 10000;\n"
            + "}")
        .add("another_option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional bool another_custom_option = 10001;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());

    ProtoFile message = pruned.protoFile("message.proto");
    assertEquals(Arrays.asList("option.proto"), message.getImports());
  }

  @Test public void retainImportWhenUsedForEnumOption() {
    Schema schema = new SchemaBuilder()
        .add("enum.proto", ""
            + "import 'option.proto';\n"
            + "import 'another_option.proto';\n"
            + "\n"
            + "enum Enum {\n"
            + "  option custom_option = true;\n"
            + "  ENUM_UNSPECIFIED = 0;\n"
            + "  ENUM_VALUE = 1;\n"
            + "}\n"
            + "\n"
            + "enum AnotherEnum {\n"
            + "  option another_custom_option = true;\n"
            + "  ANOTHER_ENUM_UNSPECIFIED = 0;\n"
            + "  ANOTHER_ENUM_VALUE = 1;\n"
            + "}")
        .add("option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.EnumOptions {\n"
            + "  optional bool custom_option = 10000;\n"
            + "}")
        .add("another_option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.EnumOptions {\n"
            + "  optional bool another_custom_option = 10001;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Enum")
        .build());

    ProtoFile enumType = pruned.protoFile("enum.proto");
    assertEquals(Arrays.asList("option.proto"), enumType.getImports());
  }

  @Test public void retainImportWhenUsedForEnumValueOption() {
    Schema schema = new SchemaBuilder()
        .add("enum.proto", ""
            + "import 'option.proto';\n"
            + "import 'another_option.proto';\n"
            + "\n"
            + "enum Enum {\n"
            + "  ENUM_UNSPECIFIED = 0;\n"
            + "  ENUM_VALUE = 1 [custom_option = true];\n"
            + "}\n"
            + "\n"
            + "enum AnotherEnum {\n"
            + "  ANOTHER_ENUM_UNSPECIFIED = 0;\n"
            + "  ANOTHER_ENUM_VALUE = 1 [another_custom_option = true];\n"
            + "}")
        .add("option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.EnumValueOptions {\n"
            + "  optional bool custom_option = 10000;\n"
            + "}")
        .add("another_option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.EnumValueOptions {\n"
            + "  optional bool another_custom_option = 10001;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Enum")
        .build());

    ProtoFile enumType = pruned.protoFile("enum.proto");
    assertEquals(Arrays.asList("option.proto"), enumType.getImports());
  }

  @Test public void retainImportWhenUsedForServiceOption() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "import 'option.proto';\n"
            + "import 'another_option.proto';\n"
            + "\n"
            + "message CallRequest {\n"
            + "}\n"
            + "\n"
            + "message CallResponse {\n"
            + "}\n"
            + "\n"
            + "service Service {\n"
            + "  option custom_option = true;\n"
            + "  rpc Call (CallRequest) returns (CallResponse);\n"
            + "}\n"
            + "\n"
            + "service AnotherService {\n"
            + "  option another_custom_option = true;\n"
            + "  rpc Call (CallRequest) returns (CallResponse);\n"
            + "}")
        .add("option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.ServiceOptions {\n"
            + "  optional bool custom_option = 10000;\n"
            + "}")
        .add("another_option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.ServiceOptions {\n"
            + "  optional bool another_custom_option = 10001;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Service")
        .build());

    ProtoFile service = pruned.protoFile("service.proto");
    assertEquals(Arrays.asList("option.proto"), service.getImports());
  }

  @Test public void retainImportWhenUsedForMethodOption() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "import 'option.proto';\n"
            + "import 'another_option.proto';\n"
            + "\n"
            + "message CallRequest {\n"
            + "}\n"
            + "\n"
            + "message CallResponse {\n"
            + "}\n"
            + "\n"
            + "service Service {\n"
            + "  rpc Call (CallRequest) returns (CallResponse) {\n"
            + "    option custom_option = true;\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "service AnotherService {\n"
            + "  rpc Call (CallRequest) returns (CallResponse) {\n"
            + "    option another_custom_option = true;\n"
            + "  }\n"
            + "}")
        .add("option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.MethodOptions {\n"
            + "  optional bool custom_option = 10000;\n"
            + "}")
        .add("another_option.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.MethodOptions {\n"
            + "  optional bool another_custom_option = 10001;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Service")
        .build());

    ProtoFile service = pruned.protoFile("service.proto");
    assertEquals(Arrays.asList("option.proto"), service.getImports());
  }

  @Test public void enumsAreKeptsIfUsed() {
    Schema schema = new SchemaBuilder()
        .add("currency_code.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "enum RoundingMode {\n"
            + "  PLAIN = 0;\n"
            + "  DOWN = 1;\n"
            + "  UP = 2;\n"
            + "  BANKERS = 3;\n"
            + "  DOWN_ON_HALF = 4;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.EnumValueOptions {\n"
            + "  optional int32 cash_rounding = 54000;\n"
            + "  optional RoundingMode rounding_mode = 54002;\n"
            + "}\n"
            + "\n"
            + "enum CurrencyCode {\n"
            + "  AFN = 971;\n"
            + "  ANG = 532;\n"
            + "  NZD = 554 [(cash_rounding) = 10, (rounding_mode) = DOWN_ON_HALF];\n"
            + "}\n")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("CurrencyCode")
        .build());

    assertNotNull(pruned.getType("RoundingMode"));
  }

  /**
   * When an extension field is reached via an option, consider only that option to be reachable.
   * Do not recursively mark the other extensions.
   */

  @Test public void markingExtensionFieldDoesNotMarkPeerFields() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional string a = 54000;\n"
            + "  optional string b = 54001;\n"
            + "}\n"
            + "\n"
            + "message Message {\n"
            + "  optional string s = 1 [a = \"a\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());

    MessageType fieldOptions = (MessageType) pruned.getType("google.protobuf.FieldOptions");
    assertNotNull(fieldOptions.extensionField("a"));
    assertNull(fieldOptions.extensionField("b"));
  }

  /**
   * When a message type is reached via an option, consider the entire type to be reachable.
   * Recursively mark the message's other fields.
   */

  @Test public void markingNonExtensionFieldDoesMarkPeerFields() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional MessageOption message_option = 54000;\n"
            + "}\n"
            + "\n"
            + "message MessageOption {\n"
            + "  optional string a = 1;\n"
            + "  optional string b = 2;\n"
            + "}\n"
            + "\n"
            + "message Message {\n"
            + "  optional string s = 1 [message_option.a = \"a\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .build());

    MessageType messageOption = (MessageType) pruned.getType("MessageOption");
    assertNotNull(messageOption.field("a"));
    assertNotNull(messageOption.field("b"));
  }

  /**
   * If we're pruning some members of a message type, reaching a different member via an option is
   * not sufficient to cause the entire message to be reachable.
   */

  @Test public void markingNonExtensionFieldDoesMarkPeerFieldsIfTypesMembersAreBeingPruned() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional MessageOption message_option = 54000;\n"
            + "}\n"
            + "\n"
            + "message MessageOption {\n"
            + "  optional string a = 1;\n"
            + "  optional string b = 2;\n"
            + "  optional string c = 3;\n"
            + "}\n"
            + "\n"
            + "message Message {\n"
            + "  optional string s = 1 [message_option.a = \"a\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message")
        .addRoot("MessageOption#c")
        .build());

    MessageType messageOption = (MessageType) pruned.getType("MessageOption");
    assertNotNull(messageOption.field("a"));
    assertNull(messageOption.field("b"));
    assertNotNull(messageOption.field("c"));
  }

  @Test public void includingFieldDoesNotIncludePeerFields() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "message Message {\n"
            + "  optional string a = 1;\n"
            + "  optional string b = 2;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("Message#a")
        .build());

    MessageType message = (MessageType) pruned.getType("Message");
    assertNotNull(message.field("a"));
    assertNull(message.field("b"));
  }

  @Test public void excludingGoogleProtobufPrunesAllOptionsOnEnums() {
    Schema schema = new SchemaBuilder()
        .add("currency_code.proto", ""
            + "package squareup;\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "message MessageOption {\n"
            + "  optional string a = 1;\n"
            + "  optional string b = 2;\n"
            + "  optional string c = 3;\n"
            + "}\n"
            + "\n"
            + "enum Style {\n"
            + "  VERBOSE = 0;\n"
            + "  POETIC = 1;\n"
            + "  RUDE = 2;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.EnumValueOptions {\n"
            + "  optional int32 max_length = 54000;\n"
            + "  optional MessageOption message_option = 54001;\n"
            + "  optional Style style = 54002;\n"
            + "}\n"
            + "\n"
            + "enum Author {\n"
            + "  ZEUS = 1 [(style) = POETIC, (max_length) = 12];\n"
            + "  ARTEMIS = 2;\n"
            + "  APOLLO = 3 [(style) = RUDE, (message_option) = {a: \"some\", c: \"things\"}];\n"
            + "  POSEIDON = 4 [deprecated = true];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("squareup.Author")
        .prune("google.protobuf.*")
        .build());

    assertNull(pruned.getType("squareup.Style"));
    assertNull(pruned.getType("squareup.MessageOption"));
    assertNotNull(pruned.getType("google.protobuf.EnumValueOptions"));

    EnumType authorType = (EnumType) pruned.getType("squareup.Author");
    assertTrue(authorType.constant("ZEUS").getOptions().fields().isEmpty());
    assertTrue(authorType.constant("ARTEMIS").getOptions().fields().isEmpty());
    assertTrue(authorType.constant("APOLLO").getOptions().fields().isEmpty());
    // Options defined in google.protobuf.descriptor.proto are not pruned.
    assertEquals(1, authorType.constant("POSEIDON").getOptions().fields().values().size());
  }

  @Test public void excludingGoogleProtobufPrunesAllOptionsOnMessages() {
    Schema schema = new SchemaBuilder()
        .add("currency_code.proto", ""
            + "package squareup;\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "message MessageOption {\n"
            + "  optional string a = 1;\n"
            + "  optional string b = 2;\n"
            + "  optional string c = 3;\n"
            + "}\n"
            + "\n"
            + "enum Style {\n"
            + "  VERBOSE = 0;\n"
            + "  POETIC = 1;\n"
            + "  RUDE = 2;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional int32 max_length = 54000;\n"
            + "  optional MessageOption message_option = 54001;\n"
            + "  optional Style style = 54002;\n"
            + "}\n"
            + "\n"
            + "message Letter {\n"
            + "  optional string header = 1 [(max_length) = 20];\n"
            + "  optional bool add_margin = 2 [deprecated = true];\n"
            + "  optional string author = 3 [(style) = RUDE, (message_option) = {a: \"some\", c: \"things\"}];\n"
            + "  optional string signature = 4 [default = \"Sent from Wire\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("squareup.Letter")
        .prune("google.protobuf.*")
        .build());

    assertNull(pruned.getType("squareup.Style"));
    assertNull(pruned.getType("squareup.MessageOption"));

    MessageType enumValueOptions = (MessageType) pruned.getType("google.protobuf.FieldOptions");
    assertNotNull(enumValueOptions.field("deprecated"));

    MessageType letterType = (MessageType) pruned.getType("squareup.Letter");
    assertTrue(letterType.field("header").getOptions().fields().isEmpty());

    // Options defined in google.protobuf.descriptor.proto are not pruned.
    assertEquals(1, letterType.field("add_margin").getOptions().fields().values().size());
    assertTrue(letterType.field("add_margin").isDeprecated());

    assertTrue(letterType.field("author").getOptions().fields().isEmpty());

    // Default are not options.
    assertEquals("Sent from Wire", letterType.field("signature").getDefault());
    assertTrue(letterType.field("signature").getOptions().fields().isEmpty());
  }

  @Test public void sinceAndUntilRetainOlder() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string radio = 1 [(wire.until) = \"1950\"];\n"
            + "  optional string video = 2 [(wire.since) = \"1950\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .since("1949")
        .until("1950")
        .build());
    MessageType message = (MessageType) pruned.getType("Message");
    assertNotNull(message.field("radio"));
    assertNull(message.field("video"));
  }

  @Test public void onlyRetainOlder() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string radio = 1 [(wire.until) = \"1950\"];\n"
            + "  optional string video = 2 [(wire.since) = \"1950\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .only("1949")
        .build());
    MessageType message = (MessageType) pruned.getType("Message");
    assertNotNull(message.field("radio"));
    assertNull(message.field("video"));
  }

  @Test public void sinceAndUntilRetainNewer() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string radio = 1 [(wire.until) = \"1950\"];\n"
            + "  optional string video = 2 [(wire.since) = \"1950\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .since("1950")
        .until("1951")
        .build());
    MessageType message = (MessageType) pruned.getType("Message");
    assertNull(message.field("radio"));
    assertNotNull(message.field("video"));
  }

  @Test public void onlyRetainNewer() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string radio = 1 [(wire.until) = \"1950\"];\n"
            + "  optional string video = 2 [(wire.since) = \"1950\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .only("1950")
        .build());
    MessageType message = (MessageType) pruned.getType("Message");
    assertNull(message.field("radio"));
    assertNotNull(message.field("video"));
  }

  @Test public void sinceRetainedWhenLessThanOrEqualToUntil() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string since_19 = 1 [(wire.since) = \"19\"];\n"
            + "  optional string since_20 = 2 [(wire.since) = \"20\"];\n"
            + "  optional string since_21 = 3 [(wire.since) = \"21\"];\n"
            + "\n"
            + "  optional string since_29 = 4 [(wire.since) = \"29\"];\n"
            + "  optional string since_30 = 5 [(wire.since) = \"30\"];\n"
            + "  optional string since_31 = 6 [(wire.since) = \"31\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .since("20")
        .until("30")
        .build());
    MessageType message = (MessageType) pruned.getType("Message");
    assertNotNull(message.field("since_19"));
    assertNotNull(message.field("since_20"));
    assertNotNull(message.field("since_21"));

    assertNotNull(message.field("since_29"));
    assertNull(message.field("since_30"));
    assertNull(message.field("since_31"));
  }

  @Test public void untilRetainedWhenGreaterThanSince() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string until_19 = 1 [(wire.until) = \"19\"];\n"
            + "  optional string until_20 = 2 [(wire.until) = \"20\"];\n"
            + "  optional string until_21 = 3 [(wire.until) = \"21\"];\n"
            + "\n"
            + "  optional string until_29 = 4 [(wire.until) = \"29\"];\n"
            + "  optional string until_30 = 5 [(wire.until) = \"30\"];\n"
            + "  optional string until_31 = 6 [(wire.until) = \"31\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .since("20")
        .until("30")
        .build());
    MessageType message = (MessageType) pruned.getType("Message");
    assertNull(message.field("until_19"));
    assertNull(message.field("until_20"));
    assertNotNull(message.field("until_21"));
    assertNotNull(message.field("until_29"));
    assertNotNull(message.field("until_30"));
    assertNotNull(message.field("until_31"));
  }

  @Test public void sinceRetainedWhenLessThanOrEqualToOnly() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string since_19 = 1 [(wire.since) = \"19\"];\n"
            + "  optional string since_20 = 2 [(wire.since) = \"20\"];\n"
            + "  optional string since_21 = 3 [(wire.since) = \"21\"];\n"
            + "\n"
            + "  optional string since_29 = 4 [(wire.since) = \"29\"];\n"
            + "  optional string since_30 = 5 [(wire.since) = \"30\"];\n"
            + "  optional string since_31 = 6 [(wire.since) = \"31\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .only("20")
        .build());
    MessageType message = (MessageType) pruned.getType("Message");
    assertNotNull(message.field("since_19"));
    assertNotNull(message.field("since_20"));
    assertNull(message.field("since_21"));

    assertNull(message.field("since_29"));
    assertNull(message.field("since_30"));
    assertNull(message.field("since_31"));
  }

  @Test public void untilRetainedWhenGreaterThanOnly() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string until_19 = 1 [(wire.until) = \"19\"];\n"
            + "  optional string until_20 = 2 [(wire.until) = \"20\"];\n"
            + "  optional string until_21 = 3 [(wire.until) = \"21\"];\n"
            + "\n"
            + "  optional string until_29 = 4 [(wire.until) = \"29\"];\n"
            + "  optional string until_30 = 5 [(wire.until) = \"30\"];\n"
            + "  optional string until_31 = 6 [(wire.until) = \"31\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .only("20")
        .build());
    MessageType message = (MessageType) pruned.getType("Message");
    assertNull(message.field("until_19"));
    assertNull(message.field("until_20"));
    assertNotNull(message.field("until_21"));
    assertNotNull(message.field("until_29"));
    assertNotNull(message.field("until_30"));
    assertNotNull(message.field("until_31"));
  }

  @Test public void sinceAndUntilDoNothingWithoutVersionPruning() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string until_20 = 1 [(wire.until) = \"20\"];\n"
            + "  optional string since_20 = 2 [(wire.since) = \"20\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder().build());
    MessageType message = (MessageType) pruned.getType("Message");
    assertNotNull(message.field("since_20"));
    assertNotNull(message.field("until_20"));
  }

  @Test public void versionPruningDoesNotImpactFieldsWithoutSinceAndUntil() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string always = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .since("20")
        .until("30")
        .build());
    MessageType message = (MessageType) pruned.getType("Message");
    assertNotNull(message.field("always"));
  }

  @Test public void onlyVersionPruningDoesNotImpactFieldsWithoutSinceAndUntil() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string always = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .only("20")
        .build());
    MessageType message = (MessageType) pruned.getType("Message");
    assertNotNull(message.field("always"));
  }

  @Test public void sinceUntilOnEnumConstant() {
    Schema schema = new SchemaBuilder()
        .add("roshambo.proto", ""
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "enum Roshambo {\n"
            + "  ROCK = 1 [(wire.constant_until) = \"29\"];\n"
            + "  SCISSORS = 2 [(wire.constant_since) = \"30\"];\n"
            + "  PAPER = 3 [(wire.constant_since) = \"29\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .since("29")
        .until("30")
        .build());
    EnumType enumType = (EnumType) pruned.getType("Roshambo");
    assertNull(enumType.constant("ROCK"));
    assertNull(enumType.constant("SCISSORS"));
    assertNotNull(enumType.constant("PAPER"));
  }

  @Test public void onlyOnEnumConstant() {
    Schema schema = new SchemaBuilder()
        .add("roshambo.proto", ""
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "enum Roshambo {\n"
            + "  ROCK = 1 [(wire.constant_until) = \"29\"];\n"
            + "  SCISSORS = 2 [(wire.constant_since) = \"30\"];\n"
            + "  PAPER = 3 [(wire.constant_since) = \"29\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .only("29")
        .build());
    EnumType enumType = (EnumType) pruned.getType("Roshambo");
    assertNull(enumType.constant("ROCK"));
    assertNull(enumType.constant("SCISSORS"));
    assertNotNull(enumType.constant("PAPER"));
  }

  @Test public void semVer() {
    Schema schema = new SchemaBuilder()
        .add("message.proto", ""
            + "import \"wire/extensions.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  optional string field_1 = 1 [(wire.until) = \"1.0.0-alpha\"];\n"
            + "  optional string field_2 = 2 [(wire.until) = \"1.0.0-alpha.1\"];\n"
            + "  optional string field_3 = 3 [(wire.until) = \"1.0.0-alpha.beta\"];\n"
            + "  optional string field_4 = 4 [(wire.since) = \"1.0.0-beta\"];\n"
            + "  optional string field_5 = 5 [(wire.since) = \"1.0.0\"];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .since("1.0.0-alpha.1")
        .until("1.0.0-beta")
        .build());
    MessageType message = (MessageType) pruned.getType("Message");
    assertNull(message.field("field_1"));
    assertNull(message.field("field_2"));
    assertNotNull(message.field("field_3"));
    assertNull(message.field("field_4"));
    assertNull(message.field("field_5"));
  }

  @Test public void typeIsRetainedIfMorePreciseRuleExists() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "package wire;\n"
            + "\n"
            + "message MessageA {\n"
            + "}\n"
            + "message MessageB {\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("wire.MessageA")
        .prune("wire.*")
        .build());
    assertNotNull(pruned.getType("wire.MessageA"));
    assertNull(pruned.getType("wire.MessageB"));
  }

  @Test public void fieldIsRetainedIfMorePreciseRuleExists() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message MyMessage {\n"
            + "  optional string a = 1;\n"
            + "  optional string b = 2;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("MyMessage#a")
        .prune("MyMessage")
        .build());
    assertNotNull(pruned.getType("MyMessage"));
    MessageType myMessageType = (MessageType) pruned.getType("MyMessage");
    assertNotNull(myMessageType.field("a"));
    assertNull(myMessageType.field("b"));
  }

  @Test public void enumConstantIsRetainedIfMorePreciseRuleExists() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "enum MyEnum {\n"
            + "  A = 1;\n"
            + "  B = 2;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("MyEnum#A")
        .prune("MyEnum")
        .build());
    assertNotNull(pruned.getType("MyEnum"));
    EnumType myEnumType = (EnumType) pruned.getType("MyEnum");
    assertNotNull(myEnumType.constant("A"));
    assertNull(myEnumType.constant("B"));
  }

  @Test public void optionFieldIsRetainedIfMorePreciseRuleExists() {
    Schema schema = new SchemaBuilder()
        .add("lecture.proto", ""
            + "package wire;\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional bool relevant = 22301;\n"
            + "  optional bool irrelevant = 22302;\n"
            + "  optional bool unused = 22303;\n"
            + "}\n"
            + "\n"
            + "message Lecture {\n"
            + "  optional string title = 1 [(wire.relevant) = true];\n"
            + "  optional string content = 2 [(wire.irrelevant) = true];\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("wire.Lecture")
        .addRoot("google.protobuf.FieldOptions#wire.relevant")
        .prune("google.protobuf.*")
        .build());
    MessageType fieldOptions = (MessageType) pruned.getType("google.protobuf.FieldOptions");
    assertNotNull(fieldOptions.extensionField("wire.relevant"));
    assertNull(fieldOptions.extensionField("wire.unused"));
    assertNull(fieldOptions.extensionField("wire.irrelevant"));

    MessageType messageType = (MessageType) pruned.getType("wire.Lecture");
    assertNotNull(messageType.field("title"));
    assertEquals(1, messageType.field("title").getOptions().getElements().size());
    assertNotNull(messageType.field("content"));
    assertTrue(messageType.field("content").getOptions().getElements().isEmpty());
  }

  @Test public void nestedInclusion() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message MyMessage {\n"
            + "  optional string a = 1;\n"
            + "  optional MyEnum b = 2;\n"
            + "}\n"
            + "\n"
            + "enum MyEnum {\n"
            + "  C = 1;\n"
            + "  D = 2;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("MyEnum#D")
        .addRoot("MyMessage#b")
        .prune("MyMessage")
        .build());
    assertNotNull(pruned.getType("MyMessage"));
    assertNotNull(pruned.getType("MyEnum"));
    MessageType myMessageType = (MessageType) pruned.getType("MyMessage");
    assertNull(myMessageType.field("a"));
    assertNotNull(myMessageType.field("b"));
    EnumType myEnumType = (EnumType) pruned.getType("MyEnum");
    assertNotNull(myEnumType.constant("C"));
    assertNull(myEnumType.constant("D"));
  }

  @Test public void includeMemberOfExcludedType() {
    Schema schema = new SchemaBuilder()
        .add("service.proto", ""
            + "message MessageA {\n"
            + "  optional string a = 1;\n"
            + "  optional Book book = 2;\n"
            + "}\n"
            + "\n"
            + "message MessageB {\n"
            + "  optional string a = 1;\n"
            + "  optional Book book = 2;\n"
            + "}\n"
            + "\n"
            + "message Book {\n"
            + "  optional string title = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("MessageA#book")
        .addRoot("MessageB")
        .prune("Book")
        .prune("Stuff")
        .build());

    MessageType messageA = (MessageType) pruned.getType("MessageA");
    assertNull(messageA.field("a"));
    // Book is excluded but the member is included so we keep it.
    assertNotNull(messageA.field("book"));

    MessageType messageB = (MessageType) pruned.getType("MessageB");
    assertNotNull(messageB.field("a"));
    // Book is excluded and MessageB#book isn't included so the field should be gone.
    assertNull(messageB.field("book"));

    // Book is excluded but because MessageA#book is included, we keep the type.
    MessageType bookType = (MessageType) pruned.getType("Book");
    assertNotNull(bookType.field("title"));
  }

  @Test public void rootCanHandleInlinedOptionWithMapValuesHavingMultipleFields() {
    Schema schema = new SchemaBuilder()
        .add("test.proto", ""
            + "syntax = \"proto3\";\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "package wire.issue;\n"
            + "\n"
            + "message Options {\n"
            + "  map<string, ConfigPayload> config = 1;\n"
            + "  map<string, SettingPayload> setting = 2;\n"
            + "}\n"
            + "\n"
            + "message ConfigPayload {\n"
            + "  optional string data = 1;\n"
            + "  optional string extra = 2;\n"
            + "}\n"
            + "\n"
            + "message SettingPayload {\n"
            + "  optional string data = 1;\n"
            + "  optional string extra = 2;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  repeated Options opt = 80000;\n"
            + "}\n"
            + "\n"
            + "message SomeMessage {\n"
            + "  option (wire.issue.opt) = {\n"
            + "    config: [\n"
            + "      {\n"
            + "        key: \"some_config_key_1\"\n"
            + "        value: {\n"
            + "          data: \"some_config_data_1\"\n"
            + "          extra: \"some_config_extra_1\"\n"
            + "        }\n"
            + "      },\n"
            + "    ],\n"
            + "    setting: [\n"
            + "      {\n"
            + "        key: \"some_setting_key_1\"\n"
            + "        value: {\n"
            + "          data: \"some_setting_data_1\"\n"
            + "          extra: \"some_setting_extra_1\"\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        key: \"some_setting_key_2\"\n"
            + "        value: {\n"
            + "          data: \"some_setting_data_2\"\n"
            + "          extra: \"some_setting_extra_2\"\n"
            + "        }\n"
            + "      }\n"
            + "    ],\n"
            + "  };\n"
            + "\n"
            + "  string id = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .addRoot("wire.issue.Options#config")
        .build());
    assertEquals(
        ""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: test.proto\n"
            + "\n"
            + "syntax = \"proto3\";\n"
            + "\n"
            + "package wire.issue;\n"
            + "\n"
            + "message Options {\n"
            + "  map<string, ConfigPayload> config = 1;\n"
            + "}\n"
            + "\n"
            + "message ConfigPayload {\n"
            + "  optional string data = 1;\n"
            + "\n"
            + "  optional string extra = 2;\n"
            + "}\n",
        pruned.protoFile("test.proto").toSchema());
  }

  @Disabled("Pruning inlined map options is not supported")
  @Test public void pruneCanHandleInlinedOptionMemberWithMapValuesHavingMultipleFields() {
    Schema schema = new SchemaBuilder()
        .add("test.proto", ""
            + "syntax = \"proto3\";\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "package wire.issue;\n"
            + "\n"
            + "message Options {\n"
            + "  map<string, ConfigPayload> config = 1;\n"
            + "  map<string, SettingPayload> setting = 2;\n"
            + "}\n"
            + "\n"
            + "message ConfigPayload {\n"
            + "  optional string data = 1;\n"
            + "  optional string extra = 2;\n"
            + "}\n"
            + "\n"
            + "message SettingPayload {\n"
            + "  optional string data = 1;\n"
            + "  optional string extra = 2;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  repeated Options opt = 80000;\n"
            + "}\n"
            + "\n"
            + "message SomeMessage {\n"
            + "  option (wire.issue.opt) = {\n"
            + "    config: [\n"
            + "      {\n"
            + "        key: \"some_config_key_1\"\n"
            + "        value: {\n"
            + "          data: \"some_config_data_1\"\n"
            + "          extra: \"some_config_extra_1\"\n"
            + "        }\n"
            + "      },\n"
            + "    ],\n"
            + "    setting: [\n"
            + "      {\n"
            + "        key: \"some_setting_key_1\"\n"
            + "        value: {\n"
            + "          data: \"some_setting_data_1\"\n"
            + "          extra: \"some_setting_extra_1\"\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        key: \"some_setting_key_2\",\n"
            + "        value: {\n"
            + "          data: \"some_setting_data_2\"\n"
            + "          extra: \"some_setting_extra_2\"\n"
            + "        }\n"
            + "      },\n"
            + "    ]\n"
            + "  };\n"
            + "\n"
            + "  string id = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("wire.issue.Options#config")
        .build());
    assertEquals(
        ""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: test.proto\n"
            + "\n"
            + "syntax = \"proto3\";\n"
            + "\n"
            + "package wire.issue;\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "message Options {\n"
            + "  map<string, SettingPayload> setting = 2;\n"
            + "}\n"
            + "\n"
            + "message SettingPayload {\n"
            + "  optional string data = 1;\n"
            + "\n"
            + "  optional string extra = 2;\n"
            + "}\n"
            + "\n"
            + "message SomeMessage {\n"
            + "  option (wire.issue.opt) = {\n"
            + "    setting: [\n"
            + "      {\n"
            + "        key: \"some_setting_key_1\",\n"
            + "        value: {\n"
            + "          data: \"some_setting_data_1\",\n"
            + "          extra: \"some_setting_extra_1\"\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        key: \"some_setting_key_2\",\n"
            + "        value: {\n"
            + "          data: \"some_setting_data_2\",\n"
            + "          extra: \"some_setting_extra_2\"\n"
            + "        }\n"
            + "      }\n"
            + "    ]\n"
            + "  };\n"
            + "\n"
            + "  string id = 1;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  repeated Options opt = 80000;\n"
            + "}\n",
        pruned.protoFile("test.proto").toSchema());
  }

  @Disabled("Pruning inlined map options is not supported")
  @Test public void pruneCanHandleInlinedOptionTypeWithMapValuesHavingMultipleFields() {
    Schema schema = new SchemaBuilder()
        .add("test.proto", ""
            + "syntax = \"proto3\";\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "package wire.issue;\n"
            + "\n"
            + "message Options {\n"
            + "  map<string, ConfigPayload> config = 1;\n"
            + "  map<string, SettingPayload> setting = 2;\n"
            + "}\n"
            + "\n"
            + "message ConfigPayload {\n"
            + "  optional string data = 1;\n"
            + "  optional string extra = 2;\n"
            + "}\n"
            + "\n"
            + "message SettingPayload {\n"
            + "  optional string data = 1;\n"
            + "  optional string extra = 2;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  optional Options opt = 80000;\n"
            + "}\n"
            + "\n"
            + "message SomeMessage {\n"
            + "  option (wire.issue.opt) = {\n"
            + "    config: [\n"
            + "      {\n"
            + "        key: \"some_config_key_1\",\n"
            + "        value: {\n"
            + "          data: \"some_config_data_1\"\n"
            + "          extra: \"some_config_extra_1\"\n"
            + "        }\n"
            + "      },\n"
            + "    ],\n"
            + "    setting: [\n"
            + "      {\n"
            + "        key: \"some_setting_key_1\",\n"
            + "        value: {\n"
            + "          data: \"some_setting_data_1\"\n"
            + "          extra: \"some_setting_extra_1\"\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        key: \"some_setting_key_2\",\n"
            + "        value: {\n"
            + "          data: \"some_setting_data_2\",\n"
            + "          extra: \"some_setting_extra_2\"\n"
            + "        }\n"
            + "      },\n"
            + "    ],\n"
            + "  };\n"
            + "\n"
            + "  string id = 1;\n"
            + "}")
        .build();
    Schema pruned = schema.prune(new PruningRules.Builder()
        .prune("wire.issue.ConfigPayload")
        .build());
    assertEquals(
        ""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: test.proto\n"
            + "\n"
            + "syntax = \"proto3\";\n"
            + "\n"
            + "package wire.issue;\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "message Options {\n"
            + "  map<string, SettingPayload> setting = 2;\n"
            + "}\n"
            + "\n"
            + "message SettingPayload {\n"
            + "  optional string data = 1;\n"
            + "\n"
            + "  optional string extra = 2;\n"
            + "}\n"
            + "\n"
            + "message SomeMessage {\n"
            + "  option (wire.issue.opt) = {\n"
            + "    setting: [\n"
            + "      {\n"
            + "        key: \"some_setting_key_1\",\n"
            + "        value: {\n"
            + "          data: \"some_setting_data_1\",\n"
            + "          extra: \"some_setting_extra_1\"\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        key: \"some_setting_key_2\",\n"
            + "        value: {\n"
            + "          data: \"some_setting_data_2\",\n"
            + "          extra: \"some_setting_extra_2\"\n"
            + "        }\n"
            + "      }\n"
            + "    ]\n"
            + "  };\n"
            + "\n"
            + "  string id = 1;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  optional Options opt = 80000;\n"
            + "}\n",
        pruned.protoFile("test.proto").toSchema());
  }
}
