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
    assertEquals(Syntax.PROTO_2, file.getSyntax());
    assertEquals("squareup.protos.parser", file.getPackageName());
    assertEquals(1, file.getTypes().size());
    MessageElement person = (MessageElement) file.getTypes().get(0);
    assertEquals("Person", person.getName());
    assertEquals(3, person.getFields().size());
    FieldElement name = person.getFields().get(0);
    assertEquals(Label.REQUIRED, name.getLabel());
    assertEquals("string", name.getType());
    assertEquals("name", name.getName());
    assertEquals(1, name.getTag());
    assertNull(name.getDefaultValue());
  }

  @Test public void proto3DefaultsAndJsonName() {
    String source = ""
        + "syntax = \"proto3\";\n"
        + "message Msg {\n"
        + "  string query = 1;\n"
        + "  int32 page = 2 [default = 10, json_name = \"page_number\"];\n"
        + "}\n";
    ProtoFileElement file = ProtoParser.parse(Location.get("msg.proto"), source);
    MessageElement msg = (MessageElement) file.getTypes().get(0);
    FieldElement page = msg.getFields().get(1);
    assertNull(page.getLabel());
    assertEquals("10", page.getDefaultValue());
    assertEquals("page_number", page.getJsonName());
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
    MessageElement m = (MessageElement) file.getTypes().get(0);
    FieldElement counts = m.getFields().get(0);
    assertEquals("map<string, int32>", counts.getType());
    assertNull(counts.getLabel());
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
    EnumElement e = (EnumElement) file.getTypes().get(0);
    assertEquals(2, e.getConstants().size());
    assertEquals(2, e.getReserveds().size());
    assertEquals("reserved 5 to 8, 12;\n", e.getReserveds().get(0).toSchema());
  }

  @Test public void oneOf() {
    String source = ""
        + "syntax = \"proto2\";\n"
        + "message M {\n"
        + "  oneof choice { string foo = 1; int32 bar = 2; }\n"
        + "}\n";
    ProtoFileElement file = ProtoParser.parse(Location.get("m.proto"), source);
    MessageElement m = (MessageElement) file.getTypes().get(0);
    assertEquals(1, m.getOneOfs().size());
    assertEquals(2, m.getOneOfs().get(0).getFields().size());
    assertEquals("foo", m.getOneOfs().get(0).getFields().get(0).getName());
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
    assertEquals(1, file.getOptions().size());
    assertEquals("java_package", file.getOptions().get(0).getName());
    MessageElement m = (MessageElement) file.getTypes().get(0);
    FieldElement s = m.getFields().get(0);
    assertEquals(2, s.getOptions().size());
    assertEquals(1, m.getExtensions().size());
    assertEquals(1, file.getExtendDeclarations().size());
    assertEquals("M", file.getExtendDeclarations().get(0).getName());
  }

  @Test public void service() {
    String source = ""
        + "syntax = \"proto3\";\n"
        + "service S {\n"
        + "  rpc Get (Req) returns (stream Resp);\n"
        + "}\n";
    ProtoFileElement file = ProtoParser.parse(Location.get("s.proto"), source);
    assertEquals(1, file.getServices().size());
    assertEquals("S", file.getServices().get(0).getName());
    assertEquals("Req", file.getServices().get(0).getRpcs().get(0).getRequestType());
    assertTrue(file.getServices().get(0).getRpcs().get(0).getResponseStreaming());
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
    assertEquals(file.getTypes().size(), reparsed.getTypes().size());
    assertEquals(((MessageElement) file.getTypes().get(0)).getFields().size(),
        ((MessageElement) reparsed.getTypes().get(0)).getFields().size());
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
