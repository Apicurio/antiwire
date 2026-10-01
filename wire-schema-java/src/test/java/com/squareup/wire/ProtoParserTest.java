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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.EnumElement;
import com.squareup.wire.schema.Field.Label;
import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.OptionElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Smoke corpus for the translated parser (TASK-10): parse representative proto2/proto3 sources
 * and assert the element model, before the full wire-schema test suite adoption (TASK-13).
 */
public class ProtoParserTest {
  @Test public void simpleMessage() {
    String source = ""
        + "syntax = \"proto2\";\n"
        + "package squareup.protos.parser;\n"
        + "\n"
        + "message Person {\n"
        + "  required string name = 1;\n"
        + "  optional int32 id = 2;\n"
        + "  optional string email = 3;\n"
        + "}\n";
    ProtoFileElement file = ProtoParser.parse(Location.get("person.proto"), source);
    assertEquals(Syntax.PROTO_2, file.syntax);
    assertEquals("squareup.protos.parser", file.packageName);
    assertEquals(1, file.types.size());
    MessageElement person = (MessageElement) file.types.get(0);
    assertEquals("Person", person.name);
    assertEquals(3, person.fields.size());
    FieldElement name = person.fields.get(0);
    assertEquals(Label.REQUIRED, name.label);
    assertEquals("string", name.type);
    assertEquals("name", name.name);
    assertEquals(1, name.tag);
    assertNull(name.defaultValue);
  }

  @Test public void proto3DefaultsAndJsonName() {
    String source = ""
        + "syntax = \"proto3\";\n"
        + "message Msg {\n"
        + "  string query = 1;\n"
        + "  int32 page = 2 [default = 10, json_name = \"page_number\"];\n"
        + "}\n";
    ProtoFileElement file = ProtoParser.parse(Location.get("msg.proto"), source);
    MessageElement msg = (MessageElement) file.types.get(0);
    FieldElement page = msg.fields.get(1);
    assertNull(page.label);
    assertEquals("10", page.defaultValue);
    assertEquals("page_number", page.jsonName);
  }

  @Test public void requiredForbiddenInProto3() {
    String source = ""
        + "syntax = \"proto3\";\n"
        + "message M { required string s = 1; }\n";
    RuntimeException e = assertThrows(RuntimeException.class,
        () -> ProtoParser.parse(Location.get("m.proto"), source));
    assertTrue(e.getMessage().contains("'required' label forbidden in proto3"));
  }

  @Test public void mapField() {
    String source = ""
        + "syntax = \"proto2\";\n"
        + "message M { map<string, int32> counts = 1; }\n";
    ProtoFileElement file = ProtoParser.parse(Location.get("m.proto"), source);
    MessageElement m = (MessageElement) file.types.get(0);
    FieldElement counts = m.fields.get(0);
    assertEquals("map<string, int32>", counts.type);
    assertNull(counts.label);
  }

  @Test public void enumWithReserved() {
    String source = ""
        + "syntax = \"proto2\";\n"
        + "enum E {\n"
        + "  A = 0;\n"
        + "  B = 1;\n"
        + "  reserved 5 to 8, 12;\n"
        + "  reserved \"OLD\";\n"
        + "}\n";
    ProtoFileElement file = ProtoParser.parse(Location.get("e.proto"), source);
    EnumElement e = (EnumElement) file.types.get(0);
    assertEquals(2, e.constants.size());
    assertEquals(2, e.reserveds.size());
    assertEquals("reserved 5 to 8, 12;\n", e.reserveds.get(0).toSchema());
  }

  @Test public void oneOf() {
    String source = ""
        + "syntax = \"proto2\";\n"
        + "message M {\n"
        + "  oneof choice { string foo = 1; int32 bar = 2; }\n"
        + "}\n";
    ProtoFileElement file = ProtoParser.parse(Location.get("m.proto"), source);
    MessageElement m = (MessageElement) file.types.get(0);
    assertEquals(1, m.oneOfs.size());
    assertEquals(2, m.oneOfs.get(0).fields.size());
    assertEquals("foo", m.oneOfs.get(0).fields.get(0).name);
  }

  @Test public void optionsAndExtensions() {
    String source = ""
        + "syntax = \"proto2\";\n"
        + "package p;\n"
        + "option java_package = \"com.p\";\n"
        + "message M {\n"
        + "  optional string s = 1 [(p.custom).field = true, deprecated = false];\n"
        + "  extensions 100 to max;\n"
        + "}\n"
        + "extend M { optional int32 ext = 100; }\n";
    ProtoFileElement file = ProtoParser.parse(Location.get("p.proto"), source);
    assertEquals(1, file.options.size());
    assertEquals("java_package", file.options.get(0).name);
    MessageElement m = (MessageElement) file.types.get(0);
    FieldElement s = m.fields.get(0);
    assertEquals(2, s.options.size());
    assertEquals(1, m.extensions.size());
    assertEquals(1, file.extendDeclarations.size());
    assertEquals("M", file.extendDeclarations.get(0).name);
  }

  @Test public void service() {
    String source = ""
        + "syntax = \"proto3\";\n"
        + "service S {\n"
        + "  rpc Get (Req) returns (stream Resp);\n"
        + "}\n";
    ProtoFileElement file = ProtoParser.parse(Location.get("s.proto"), source);
    assertEquals(1, file.services.size());
    assertEquals("S", file.services.get(0).name);
    assertEquals("Req", file.services.get(0).rpcs.get(0).requestType);
    assertTrue(file.services.get(0).rpcs.get(0).responseStreaming);
  }

  @Test public void toSchemaRoundTrip() {
    String source = ""
        + "// Doc comment.\n"
        + "syntax = \"proto2\";\n"
        + "package round.trip;\n"
        + "\n"
        + "message M {\n"
        + "  optional string s = 1;\n"
        + "}\n";
    ProtoFileElement file = ProtoParser.parse(Location.get("rt.proto"), source);
    String schema = file.toSchema();
    ProtoFileElement reparsed = ProtoParser.parse(Location.get("rt.proto"), schema);
    assertEquals(file.types.size(), reparsed.types.size());
    assertEquals(((MessageElement) file.types.get(0)).fields.size(),
        ((MessageElement) reparsed.types.get(0)).fields.size());
  }

  @Test public void syntaxMustBeFirst() {
    String source = ""
        + "message M { optional string s = 1; }\n"
        + "syntax = \"proto2\";\n";
    RuntimeException e = assertThrows(RuntimeException.class,
        () -> ProtoParser.parse(Location.get("m.proto"), source));
    assertTrue(e.getMessage().contains("'syntax' element must be the first declaration"));
  }
}
