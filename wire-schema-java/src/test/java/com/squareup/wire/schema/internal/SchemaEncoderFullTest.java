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
package com.squareup.wire.schema.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.DescriptorProto.ExtensionRange;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodOptions;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.UnknownFieldSet;
import com.squareup.wire.SchemaBuilder;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.Schema;
import java.io.IOException;
import okio.ByteString;
import org.junit.jupiter.api.Test;

/**
 * Upstream SchemaEncoderTest translated (assertk to JUnit 5, buildSchema to SchemaBuilder,
 * upstream backtick test names lower-camel-cased). Renamed to SchemaEncoderFullTest because the
 * port already carries a partial, port-authored SchemaEncoderTest that stays untouched.
 *
 * <p>TASK-13 adaptation: upstream parses the encoded descriptors with protobuf-java
 * (FileDescriptorProto.parseFrom), which upstream wire-schema declares as a jvmTest dependency;
 * this port mirrors that test-scoped dependency in wire-schema-java's pom.
 */
public class SchemaEncoderFullTest {
  @Test public void encodeSchema() throws IOException {
    Schema schema = new SchemaBuilder()
        .add("handle_service.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "import \"google/protobuf/descriptor.proto\";\n"
            + "\n"
            + "enum GreekLetter {\n"
            + "  ALPHA = 1;\n"
            + "  BETA = 2;\n"
            + "}\n"
            + "\n"
            + "extend google.protobuf.MethodOptions {\n"
            + "  optional double timeout = 22000;\n"
            + "  optional GreekLetter greek_letter = 22001;\n"
            + "  repeated GreekLetter fraternity = 22002;\n"
            + "}\n"
            + "\n"
            + "message HandleRequest {\n"
            + "}\n"
            + "\n"
            + "message HandleResponse {\n"
            + "}\n"
            + "\n"
            + "service HandleService {\n"
            + "  rpc Handle ( HandleRequest ) returns ( HandleResponse ) {\n"
            + "    option (timeout) = 2.1;\n"
            + "    option (greek_letter) = BETA;\n"
            + "    option (fraternity) = [ALPHA, BETA, ALPHA];\n"
            + "  }\n"
            + "}\n")
        .build();

    ProtoFile handleServiceProto = schema.protoFile("handle_service.proto");
    ByteString encoded = new SchemaEncoder(schema).encode(handleServiceProto);

    FileDescriptorProto fileDescriptorProto = FileDescriptorProto.parseFrom(encoded.toByteArray());
    assertEquals(
        FileDescriptorProto.newBuilder()
            .setName("handle_service.proto")
            .addDependency("google/protobuf/descriptor.proto")
            .addEnumType(
                EnumDescriptorProto.newBuilder()
                    .setName("GreekLetter")
                    .addValue(
                        EnumValueDescriptorProto.newBuilder()
                            .setName("ALPHA")
                            .setNumber(1)
                            .build())
                    .addValue(
                        EnumValueDescriptorProto.newBuilder()
                            .setName("BETA")
                            .setNumber(2)
                            .build())
                    .build())
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("HandleRequest")
                    .build())
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("HandleResponse")
                    .build())
            .addService(
                ServiceDescriptorProto.newBuilder()
                    .setName("HandleService")
                    .addMethod(
                        MethodDescriptorProto.newBuilder()
                            .setName("Handle")
                            .setInputType(".HandleRequest")
                            .setOutputType(".HandleResponse")
                            .setOptions(
                                MethodOptions.newBuilder()
                                    .setUnknownFields(
                                        UnknownFieldSet.newBuilder()
                                            .addField(
                                                22000,
                                                UnknownFieldSet.Field.newBuilder()
                                                    .addFixed64(Double.doubleToLongBits(2.1))
                                                    .build())
                                            .addField(
                                                22001,
                                                UnknownFieldSet.Field.newBuilder()
                                                    .addVarint(2L)
                                                    .build())
                                            .addField(
                                                22002,
                                                UnknownFieldSet.Field.newBuilder()
                                                    .addVarint(1L)
                                                    .addVarint(2L)
                                                    .addVarint(1L)
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build())
            .addExtension(
                FieldDescriptorProto.newBuilder()
                    .setName("timeout")
                    .setExtendee(".google.protobuf.MethodOptions")
                    .setNumber(22000)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .setType(FieldDescriptorProto.Type.TYPE_DOUBLE)
                    .build())
            .addExtension(
                FieldDescriptorProto.newBuilder()
                    .setName("greek_letter")
                    .setExtendee(".google.protobuf.MethodOptions")
                    .setNumber(22001)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .setType(FieldDescriptorProto.Type.TYPE_ENUM)
                    .setTypeName(".GreekLetter")
                    .build())
            .addExtension(
                FieldDescriptorProto.newBuilder()
                    .setName("fraternity")
                    .setExtendee(".google.protobuf.MethodOptions")
                    .setNumber(22002)
                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                    .setType(FieldDescriptorProto.Type.TYPE_ENUM)
                    .setTypeName(".GreekLetter")
                    .build())
            .build(),
        fileDescriptorProto);
  }

  @Test public void encodeExtensionRange() throws IOException {
    Schema schema = new SchemaBuilder()
        .add("test.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message TestMessage {\n"
            + "  extensions 5, 1000 to max;\n"
            + "}\n")
        .build();

    ProtoFile handleServiceProto = schema.protoFile("test.proto");
    ByteString encoded = new SchemaEncoder(schema).encode(handleServiceProto);

    FileDescriptorProto fileDescriptorProto = FileDescriptorProto.parseFrom(encoded.toByteArray());
    assertEquals(
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("TestMessage")
                    .addExtensionRange(
                        ExtensionRange.newBuilder()
                            .setStart(5)
                            .setEnd(6)
                            .build())
                    .addExtensionRange(
                        ExtensionRange.newBuilder()
                            .setStart(1000)
                            .setEnd(SchemaUtil.MAX_TAG_VALUE + 1)
                            .build())
                    .build())
            .build(),
        fileDescriptorProto);
  }

  @Test public void encodeNestedEnums() throws IOException {
    Schema schema = new SchemaBuilder()
        .add("test.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message TestMessage {\n"
            + "  enum Nested {\n"
            + "    NESTED_UNDEFINED = 0;\n"
            + "    NESTED_DEFINED = 1;\n"
            + "  }\n"
            + "}\n")
        .build();

    ProtoFile handleServiceProto = schema.protoFile("test.proto");
    ByteString encoded = new SchemaEncoder(schema).encode(handleServiceProto);

    FileDescriptorProto fileDescriptorProto = FileDescriptorProto.parseFrom(encoded.toByteArray());
    assertEquals(
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("TestMessage")
                    .addEnumType(
                        EnumDescriptorProto.newBuilder()
                            .setName("Nested")
                            .addValue(0, EnumValueDescriptorProto.newBuilder()
                                .setName("NESTED_UNDEFINED").setNumber(0))
                            .addValue(1, EnumValueDescriptorProto.newBuilder()
                                .setName("NESTED_DEFINED").setNumber(1)))
                    .build())
            .build(),
        fileDescriptorProto);
  }

  @Test public void oneofTagOrder() throws IOException {
    Schema schema = new SchemaBuilder()
        .add("test.proto", ""
            + "syntax = \"proto3\";\n"
            + "\n"
            + "message AMessage {\n"
            + "  string two = 2;\n"
            + "\n"
            + "  oneof a_oneof {\n"
            + "    string one = 1;\n"
            + "    string three = 3;\n"
            + "  }\n"
            + "}\n")
        .build();
    ProtoFile handleServiceProto = schema.protoFile("test.proto");
    ByteString encoded = new SchemaEncoder(schema).encode(handleServiceProto);

    FileDescriptorProto fileDescriptorProto = FileDescriptorProto.parseFrom(encoded.toByteArray());
    assertNotNull(fileDescriptorProto);
    assertEquals(
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setSyntax("proto3")
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("AMessage")
                    .addField(
                        FieldDescriptorProto.newBuilder()
                            .setType(FieldDescriptorProto.Type.TYPE_STRING)
                            .setName("two")
                            .setNumber(2)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .build())
                    .addField(
                        FieldDescriptorProto.newBuilder()
                            .setType(FieldDescriptorProto.Type.TYPE_STRING)
                            .setName("one")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setOneofIndex(0)
                            .build())
                    .addField(
                        FieldDescriptorProto.newBuilder()
                            .setType(FieldDescriptorProto.Type.TYPE_STRING)
                            .setName("three")
                            .setNumber(3)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setOneofIndex(0)
                            .build())
                    .addOneofDecl(
                        DescriptorProtos.OneofDescriptorProto.newBuilder()
                            .setName("a_oneof")
                            .build())
                    .build())
            .build(),
        fileDescriptorProto);
  }

  @Test public void encodeImportPublicDependency() throws IOException {
    Schema schema = new SchemaBuilder()
        .add("test.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "import \"standard.proto\";\n"
            + "import public \"public.proto\";\n"
            + "\n"
            + "message TestMessage {\n"
            + "  optional StandardMessage standard_message = 1;\n"
            + "  optional PublicMessage public_message = 2;\n"
            + "}\n")
        .add("standard.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message StandardMessage {\n"
            + "  optional string value = 1;\n"
            + "}\n")
        .add("public.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message PublicMessage {\n"
            + "  optional string value = 1;\n"
            + "}\n")
        .build();

    ProtoFile handleServiceProto = schema.protoFile("test.proto");
    ByteString encoded = new SchemaEncoder(schema).encode(handleServiceProto);

    FileDescriptorProto fileDescriptorProto = FileDescriptorProto.parseFrom(encoded.toByteArray());
    assertEquals(
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .addDependency("standard.proto")
            .addDependency("public.proto")
            .addPublicDependency(1)
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("TestMessage")
                    .addField(
                        FieldDescriptorProto.newBuilder()
                            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setName("standard_message")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setTypeName(".StandardMessage")
                            .build())
                    .addField(
                        FieldDescriptorProto.newBuilder()
                            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setName("public_message")
                            .setNumber(2)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setTypeName(".PublicMessage")
                            .build())
                    .build())
            .build(),
        fileDescriptorProto);
  }
}
