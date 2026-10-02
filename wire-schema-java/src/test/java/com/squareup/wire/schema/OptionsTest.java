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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.squareup.wire.SchemaBuilder;
import com.squareup.wire.schema.internal.parser.OptionElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/** Upstream OptionsTest translated (assertk to JUnit 5, buildSchema to SchemaBuilder). */
public class OptionsTest {
  @Test public void structuredAndUnstructuredOptions() {
    // From https://developers.google.com/protocol-buffers/docs/proto#options
    Schema schema = new SchemaBuilder()
        .add("foo.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "message FooOptions {\n"
            + "  optional int32 opt1 = 1;\n"
            + "  optional string opt2 = 2;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional FooOptions foo_options = 1234;\n"
            + "}\n"
            + "\n"
            + "message Bar {\n"
            + "  optional int32 a = 1 [(foo_options).opt1 = 123, (foo_options).opt2 = \"baz\"];\n"
            + "  optional int32 b = 2 [(foo_options) = { opt1: 456 opt2: \"quux\" }];\n"
            + "}")
        .build();

    ProtoMember fooOptions = ProtoMember.get(Options.FIELD_OPTIONS, "foo_options");
    ProtoMember opt1 = ProtoMember.get(ProtoType.get("FooOptions"), "opt1");
    ProtoMember opt2 = ProtoMember.get(ProtoType.get("FooOptions"), "opt2");

    MessageType bar = (MessageType) schema.getType("Bar");
    Map<ProtoMember, Object> expectedA = new LinkedHashMap<>();
    Map<ProtoMember, Object> opt1Map = new LinkedHashMap<>();
    opt1Map.put(opt1, "123");
    opt1Map.put(opt2, "baz");
    expectedA.put(fooOptions, opt1Map);
    assertEquals(expectedA, bar.field("a").options().map());
    Map<ProtoMember, Object> expectedB = new LinkedHashMap<>();
    Map<ProtoMember, Object> opt2Map = new LinkedHashMap<>();
    opt2Map.put(opt1, "456");
    opt2Map.put(opt2, "quux");
    expectedB.put(fooOptions, opt2Map);
    assertEquals(expectedB, bar.field("b").options().map());
  }

  // https://github.com/square/wire/issues/3672
  @Test public void parenthesizedExtensionAfterFieldPathComponent() {
    // Shaped after protovalidate's "(buf.validate.field).string.(buf.validate.predefined)".
    Schema schema = new SchemaBuilder()
        .add("foo.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "message FieldConstraints {\n"
            + "  optional StringRules string = 1;\n"
            + "}\n"
            + "message StringRules {\n"
            + "  extensions 1000 to max;\n"
            + "}\n"
            + "\n"
            + "extend StringRules {\n"
            + "  optional bool datetime = 1000;\n"
            + "}\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional FieldConstraints field = 1234;\n"
            + "}\n"
            + "\n"
            + "message Bar {\n"
            + "  optional string a = 1 [(field).string.(datetime) = true];\n"
            + "}")
        .build();

    ProtoMember field = ProtoMember.get(Options.FIELD_OPTIONS, "field");
    ProtoMember string = ProtoMember.get(ProtoType.get("FieldConstraints"), "string");
    ProtoMember datetime = ProtoMember.get(ProtoType.get("StringRules"), "datetime");

    MessageType bar = (MessageType) schema.getType("Bar");
    Map<ProtoMember, Object> expected = new LinkedHashMap<>();
    Map<ProtoMember, Object> stringMap = new LinkedHashMap<>();
    Map<ProtoMember, Object> datetimeMap = new LinkedHashMap<>();
    datetimeMap.put(datetime, "true");
    stringMap.put(string, datetimeMap);
    expected.put(field, stringMap);
    assertEquals(expected, bar.field("a").options().map());
  }

  @Test public void textFormatCanOmitMapValueSeparator() {
    Schema schema = new SchemaBuilder()
        .add("foo.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "message FooOptions {\n"
            + "  optional BarOptions bar = 2;\n"
            + "}\n"
            + "message BarOptions {\n"
            + "  optional int32 baz = 2;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.FieldOptions {\n"
            + "  optional FooOptions foo = 1234;\n"
            + "}\n"
            + "\n"
            + "message Message {\n"
            + "  optional int32 b = 2 [(foo) = { bar { baz: 123 } }];\n"
            + "}")
        .build();

    ProtoMember foo = ProtoMember.get(Options.FIELD_OPTIONS, "foo");
    ProtoMember bar = ProtoMember.get(ProtoType.get("FooOptions"), "bar");
    ProtoMember baz = ProtoMember.get(ProtoType.get("BarOptions"), "baz");

    Map<ProtoMember, Object> bazMap = new LinkedHashMap<>();
    bazMap.put(baz, "123");
    Map<ProtoMember, Object> barMap = new LinkedHashMap<>();
    barMap.put(bar, bazMap);
    Map<ProtoMember, Object> expected = new LinkedHashMap<>();
    expected.put(foo, barMap);
    MessageType message = (MessageType) schema.getType("Message");
    assertEquals(expected, message.field("b").options().map());
  }

  @Test public void testOptionsToSchema() {
    Schema schema = new SchemaBuilder()
        .add("foo.proto", ""
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "enum FooParameterType {\n"
            + "   NUMBER = 1;\n"
            + "   STRING = 2;\n"
            + "}\n"
            + "enum Scheme {\n"
            + "  UNKNOWN = 0;\n"
            + "  HTTP = 1;\n"
            + "  HTTPS = 2;\n"
            + "}\n"
            + "\n"
            + "message FooOptions {\n"
            + "  optional string name = 1;\n"
            + "  optional FooParameterType type = 2;\n"
            + "  repeated Scheme schemes = 3;\n"
            + "}\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  repeated FooOptions foo = 12345;\n"
            + "}\n"
            + "\n"
            + "message Message {\n"
            + "  option (foo) = {\n"
            + "    name: \"test\"\n"
            + "    type: STRING\n"
            + "    schemes: HTTP\n"
            + "    schemes: HTTPS\n"
            + "  };\n"
            + "\n"
            + "  option (foo) = {\n"
            + "    name: \"test2\"\n"
            + "    type: NUMBER\n"
            + "    schemes: [HTTP, HTTPS]\n"
            + "  };\n"
            + "\n"
            + "  optional int32 b = 2;\n"
            + "}")
        .build();

    ProtoFile protoFile = schema.protoFile("foo.proto");

    List<OptionElement> optionElements = protoFile.types().stream()
        .filter(t -> t instanceof MessageType
            && ((MessageType) t).toElement().name().equals("Message"))
        .map(t -> ((MessageType) t).options().elements())
        .findFirst()
        .orElseThrow(NoSuchElementException::new);

    assertEquals("(foo) = {\n"
        + "  name: \"test\",\n"
        + "  type: STRING,\n"
        + "  schemes: [\n"
        + "    HTTP,\n"
        + "    HTTPS\n"
        + "  ]\n"
        + "}", optionElements.get(0).toSchema());

    ProtoMember foo = ProtoMember.get(Options.MESSAGE_OPTIONS, "foo");

    ProtoMember name = ProtoMember.get(ProtoType.get("FooOptions"), "name");
    ProtoMember type = ProtoMember.get(ProtoType.get("FooOptions"), "type");
    ProtoMember schemes = ProtoMember.get(ProtoType.get("FooOptions"), "schemes");

    MessageType message = (MessageType) schema.getType("Message");
    message.toElement().name();

    Map<ProtoMember, Object> first = new LinkedHashMap<>();
    first.put(name, "test");
    first.put(type, "STRING");
    first.put(schemes, Arrays.asList("HTTP", "HTTPS"));
    Map<ProtoMember, Object> second = new LinkedHashMap<>();
    second.put(name, "test2");
    second.put(type, "NUMBER");
    second.put(schemes, Arrays.asList("HTTP", "HTTPS"));
    Map<ProtoMember, Object> expected = new LinkedHashMap<>();
    expected.put(foo, new ArrayList<>(Arrays.asList(first, second)));
    assertEquals(expected, message.options().map());
  }

  @Test public void fullyQualifiedOptionFields() {
    Schema schema = new SchemaBuilder()
        .add("a/b/more_options.proto", ""
            + "syntax = \"proto2\";\n"
            + "package a.b;\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "extend google.protobuf.MessageOptions {\n"
            + "  optional MoreOptions more_options = 17000;\n"
            + "}\n"
            + "\n"
            + "message MoreOptions {\n"
            + "  extensions 100 to 200;\n"
            + "}")
        .add("a/c/event_more_options.proto", ""
            + "syntax = \"proto2\";\n"
            + "package a.c;\n"
            + "\n"
            + "import \"a/b/more_options.proto\";\n"
            + "\n"
            + "extend a.b.MoreOptions {\n"
            + "  optional EvenMoreOptions even_more_options = 100;\n"
            + "}\n"
            + "\n"
            + "message EvenMoreOptions {\n"
            + "  optional string string_option = 1;\n"
            + "}")
        .add("a/d/message.proto", ""
            + "syntax = \"proto2\";\n"
            + "package a.d;\n"
            + "\n"
            + "import \"a/b/more_options.proto\";\n"
            + "import \"a/c/event_more_options.proto\";\n"
            + "\n"
            + "message Message {\n"
            + "  option (a.b.more_options) = {\n"
            + "    [a.c.even_more_options]: {string_option: \"foo\"}\n"
            + "  };\n"
            + "}")
        .build();
    ProtoType moreOptionsType = ProtoType.get("a.b.MoreOptions");
    ProtoType evenMoreOptionsType = ProtoType.get("a.c.EvenMoreOptions");
    ProtoMember moreOptions = ProtoMember.get(Options.MESSAGE_OPTIONS, "a.b.more_options");
    ProtoMember evenMoreOptions = ProtoMember.get(moreOptionsType, "a.c.even_more_options");
    ProtoMember stringOption = ProtoMember.get(evenMoreOptionsType, "string_option");
    MessageType message = (MessageType) schema.getType("a.d.Message");

    Map<ProtoMember, Object> stringOptionMap = new LinkedHashMap<>();
    stringOptionMap.put(stringOption, "foo");
    Map<ProtoMember, Object> evenMoreOptionsMap = new LinkedHashMap<>();
    evenMoreOptionsMap.put(evenMoreOptions, stringOptionMap);
    Map<ProtoMember, Object> expected = new LinkedHashMap<>();
    expected.put(moreOptions, evenMoreOptionsMap);
    assertEquals(expected, message.options().map());
  }

  @Test public void resolveFieldPathMatchesLeadingDotFirstSegment() {
    assertArrayEquals(new String[] {"a", "b", "c", "d"},
        Options.resolveFieldPath(".a.b.c.d", new HashSet<>(Arrays.asList("a", "z", "y"))));
  }

  @Test public void resolveFieldPathMatchesFirstSegment() {
    assertArrayEquals(new String[] {"a", "b", "c", "d"},
        Options.resolveFieldPath("a.b.c.d", new HashSet<>(Arrays.asList("a", "z", "y"))));
  }

  @Test public void resolveFieldPathMatchesMultipleSegments() {
    assertArrayEquals(new String[] {"a.b", "c", "d"},
        Options.resolveFieldPath("a.b.c.d", new HashSet<>(Arrays.asList("a.b", "z.b", "y.b"))));
  }

  @Test public void resolveFieldPathMatchesAllSegments() {
    assertArrayEquals(new String[] {"a.b.c.d"},
        Options.resolveFieldPath("a.b.c.d", new HashSet<>(Arrays.asList("a.b.c.d", "z.b.c.d"))));
  }

  @Test public void resolveFieldPathMatchesOnlySegment() {
    assertArrayEquals(new String[] {"a"},
        Options.resolveFieldPath("a", new HashSet<>(Arrays.asList("a", "b"))));
  }

  @Test public void resolveFieldPathDoesntMatch() {
    assertNull(Options.resolveFieldPath("a.b", new HashSet<>(Arrays.asList("c", "d"))));
  }

  @Test public void mapFieldEntriesLinking() {
    Schema schema = new SchemaBuilder()
        .add("my_package/some_enum.proto", ""
            + "syntax = \"proto2\";\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "package my_package;\n"
            + "\n"
            + "enum SomeEnum {\n"
            + "  TEST = 0 [(my_package.my_option) = {\n"
            + "    entries: [\n"
            + "      {\n"
            + "        key: 'key-1',\n"
            + "        value: {\n"
            + "          some_string: \"value-1\"\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        value: {\n"
            + "          some_string: \"value-2\"\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        key: 'key-3',\n"
            + "        value: {\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        key: 'key-4',\n"
            + "      },\n"
            + "      {\n"
            + "        key: 'key-5',\n"
            + "        value: {\n"
            + "          some_string: \"value-5\",\n"
            + "          some_int32: 5\n"
            + "        }\n"
            + "      }\n"
            + "    ]\n"
            + "  }];\n"
            + "}\n"
            + "\n"
            + "message SomeMessage {\n"
            + "  optional string some_string = 1;\n"
            + "  optional int32 some_int32 = 2;\n"
            + "}\n"
            + "\n"
            + "message MyOption {\n"
            + "  map<string, SomeMessage> entries = 2;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.EnumValueOptions {\n"
            + "  optional MyOption my_option = 1000;\n"
            + "}")
        .build();

    EnumType enumType = (EnumType) schema.getType(ProtoType.get("my_package.SomeEnum"));
    ProtoMember myOption = ProtoMember.get(Options.ENUM_VALUE_OPTIONS, "my_package.my_option");
    ProtoMember entries = ProtoMember.get(ProtoType.get("my_package.MyOption"), "entries");
    ProtoMember someString = ProtoMember.get(ProtoType.get("my_package.SomeMessage"), "some_string");
    ProtoMember someInt32 = ProtoMember.get(ProtoType.get("my_package.SomeMessage"), "some_int32");

    Map<Object, Object> entry1 = new LinkedHashMap<>();
    Map<ProtoMember, Object> value1 = new LinkedHashMap<>();
    value1.put(someString, "value-1");
    entry1.put("key-1", value1);
    Map<Object, Object> entry2 = new LinkedHashMap<>();
    Map<ProtoMember, Object> value2 = new LinkedHashMap<>();
    value2.put(someString, "value-2");
    entry2.put(null, value2);
    Map<Object, Object> entry3 = new LinkedHashMap<>();
    entry3.put("key-3", new LinkedHashMap<>());
    Map<Object, Object> entry4 = new LinkedHashMap<>();
    entry4.put("key-4", null);
    Map<Object, Object> entry5 = new LinkedHashMap<>();
    Map<ProtoMember, Object> value5 = new LinkedHashMap<>();
    value5.put(someString, "value-5");
    value5.put(someInt32, "5");
    entry5.put("key-5", value5);
    Map<ProtoMember, Object> entriesMap = new LinkedHashMap<>();
    entriesMap.put(entries, Arrays.asList(entry1, entry2, entry3, entry4, entry5));
    Map<ProtoMember, Object> expected = new LinkedHashMap<>();
    expected.put(myOption, entriesMap);
    assertEquals(expected, enumType.constant(0).options().map());
  }

  @Test public void mapFieldEntriesWriting() {
    Schema schema = new SchemaBuilder()
        .add("my_package/some_enum.proto", ""
            + "syntax = \"proto3\";\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "package my_package;\n"
            + "\n"
            + "enum SomeEnum {\n"
            + "  TEST = 0 [(my_package.my_option) = {\n"
            + "    entries: [\n"
            + "      {\n"
            + "        key: 'key-1',\n"
            + "        value: {\n"
            + "          some_string: \"value-1\"\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        key: 'key-2',\n"
            + "        value: {\n"
            + "          some_string: \"value-2\",\n"
            + "          some_int32: 2\n"
            + "        }\n"
            + "      }\n"
            + "    ]\n"
            + "  }];\n"
            + "}\n"
            + "\n"
            + "message SomeMessage {\n"
            + "  string some_string = 1;\n"
            + "  int32 some_int32 = 2;\n"
            + "}\n"
            + "\n"
            + "message MyOption {\n"
            + "  map<string, SomeMessage> entries = 2;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.EnumValueOptions {\n"
            + "  MyOption my_option = 1000;\n"
            + "}")
        .build();

    EnumType enumType = (EnumType) schema.getType(ProtoType.get("my_package.SomeEnum"));
    OptionElement optionElement = enumType.constant(0).options().elements().get(0);
    // We do print "key" and "value" keys for map fields, even though the linked schema doesn't
    // know about them.
    String expected = "(my_package.my_option) = {\n"
        + "  entries: [\n"
        + "    {\n"
        + "      key: \"key-1\",\n"
        + "      value: {\n"
        + "        some_string: \"value-1\"\n"
        + "      }\n"
        + "    },\n"
        + "    {\n"
        + "      key: \"key-2\",\n"
        + "      value: {\n"
        + "        some_string: \"value-2\",\n"
        + "        some_int32: 2\n"
        + "      }\n"
        + "    }\n"
        + "  ]\n"
        + "}";
    assertEquals(expected, optionElement.toSchema());
  }
}
