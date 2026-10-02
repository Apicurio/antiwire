/*
 * Copyright (C) 2020 Square, Inc.
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

import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import java.util.Arrays;
import java.util.Collections;
import okio.ByteString;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import squareup.proto3.java.interop.InteropCamelCase;
import squareup.proto3.java.interop.InteropDuration;
import squareup.proto3.java.interop.InteropJsonName;
import squareup.proto3.java.interop.InteropOptional;
import squareup.proto3.java.interop.InteropUint64;
import squareup.proto3.java.interop.InteropWrappers;

/**
 * TASK-15 port of upstream wire-protoc-compatibility-tests InteropTest.kt (pinned tag 7.1.0).
 *
 * <p>Applicability (TASK-15): upstream checks each scenario against wire-generated Kotlin models
 * (squareup.*.kotlin.*) and Java models (squareup.*.java.*). The oracle module generates the Java
 * models only; every case below ports the Java-model checks verbatim, and cases whose only live
 * checks exercise a Kotlin-only model shape are disabled with a DEC-6 reason and their upstream
 * bodies preserved as comments. Kotlin backticked test names become camelCase Java identifiers.
 *
 * <p>TASK-15 adaptation: upstream's ProtocWrappersHelper.kt Kotlin extensions ({@code toDoubleValue()}
 * and siblings) become the protobuf-java value factories ({@code DoubleValue.of(double)} and
 * siblings), which is what those extensions wrap.
 */
public class InteropTest {
  @Test public void duration() throws Exception {
    InteropChecker checker = new InteropChecker(
        squareup.proto3.java.interop.InteropTest.InteropDuration.newBuilder()
            .setValue(
                Duration.newBuilder()
                    .setSeconds(99L)
                    .setNanos(987_654_321)
                    .build())
            .build(),
        "{\"value\":\"99.987654321s\"}",
        "{\"value\":\"99.987654321s\"}",
        Collections.<String>emptyList(),
        Arrays.asList(
            // TODO: move to alternateJsons once we can use ignoringUnknownFields().
            "{\"unused\": false, \"value\":\"99.987654321s\"}")
    );

    // TASK-15 adaptation: upstream also checks the Kotlin models InteropDurationK3 and
    // InteropDurationK2 (DEC-6); the two Java wire models below encode the same wire bytes.
    checker.check(new InteropDuration(java.time.Duration.ofSeconds(99L, 987_654_321L)));
    checker.check(new squareup.proto2.java.interop.InteropDuration(
        java.time.Duration.ofSeconds(99L, 987_654_321L)));
  }

  @Test public void uint64() throws Exception {
    InteropChecker zero = new InteropChecker(
        squareup.proto3.java.interop.InteropTest.InteropUint64.newBuilder()
            .setValue(0L)
            .build(),
        "{}",
        "{}",
        Arrays.asList(
            "{\"value\":\"0\"}",
            "{\"value\":0}",
            "{\"value\":\"-0\"}"),
        Collections.<String>emptyList()
    );
    // TASK-15 adaptation: upstream checks InteropUint64K3 and InteropUint64J3; the Kotlin model is
    // a DEC-6 exclusion, the Java model below is the port's equivalent surface.
    zero.check(new InteropUint64(0L));

    InteropChecker one = new InteropChecker(
        squareup.proto3.java.interop.InteropTest.InteropUint64.newBuilder()
            .setValue(1L)
            .build(),
        "{\"value\":\"1\"}",
        "{\"value\":\"1\"}",
        Arrays.asList(
            "{\"value\":1}",
            "{\"value\":\"1\"}",
            "{\"value\":\"1.0\"}"),
        Collections.<String>emptyList()
    );
    one.check(new InteropUint64(1L));

    InteropChecker max = new InteropChecker(
        squareup.proto3.java.interop.InteropTest.InteropUint64.newBuilder()
            .setValue(-1L)
            .build(),
        "{\"value\":\"18446744073709551615\"}",
        "{\"value\":\"18446744073709551615\"}",
        Collections.<String>emptyList(),
        Arrays.asList(
            "{\"value\":\"-1\"}")
    );
    max.check(new InteropUint64(-1L));
  }

  // TASK-15 adaptation: upstream test name `uint64 proto 2`; Kotlin backticked names become
  // camelCase Java identifiers.
  @Test public void uint64Proto2() throws Exception {
    InteropChecker max = new InteropChecker(
        squareup.proto2.java.interop.InteropTest.InteropUint64.newBuilder()
            .setValue(-1L)
            .build(),
        "{\"value\":\"18446744073709551615\"}",
        "{\"value\":18446744073709551615}",
        Arrays.asList(
            "{\"value\":\"18446744073709551615\"}",
            "{\"value\":18446744073709551615}"),
        Arrays.asList(
            "{\"value\":\"-1\"}")
    );
    // TASK-15 adaptation: upstream checks InteropUint64K2 and InteropUint64J2; the Kotlin model is
    // a DEC-6 exclusion.
    max.check(new squareup.proto2.java.interop.InteropUint64(-1L));
  }

  // TASK-15 adaptation: upstream test name `camel case`.
  @Test public void camelCase() throws Exception {
    InteropChecker checker = new InteropChecker(
        squareup.proto3.java.interop.InteropTest.InteropCamelCase.newBuilder()
            .setHelloWorld("1")
            .setAB("2")
            .setCccDdd("3")
            .setEEeeFfGGg("4")
            .setABC("5")
            .setGHI("6")
            .setKLM("7")
            .setTUV("8")
            .setXYZ("9")
            .build(),
        "{\"helloWorld\":\"1\",\"aB\":\"2\",\"CccDdd\":\"3\",\"EEeeFfGGg\":\"4\",\"aBC\":\"5\",\"GHI\":\"6\",\"KLM\":\"7\",\"TUV\":\"8\",\"XYZ\":\"9\"}",
        "{\"helloWorld\":\"1\",\"aB\":\"2\",\"CccDdd\":\"3\",\"EEeeFfGGg\":\"4\",\"aBC\":\"5\",\"GHI\":\"6\",\"KLM\":\"7\",\"TUV\":\"8\",\"XYZ\":\"9\"}",
        Arrays.asList(
            "{\"hello_world\": \"1\", \"a__b\": \"2\", \"_Ccc_ddd\": \"3\", \"EEee_ff_gGg\": \"4\", \"a_b_c\": \"5\", \"GHI\": \"6\", \"K_L_M\": \"7\", \"__T__U__V__\": \"8\", \"_x_y_z_\": \"9\"}"),
        Collections.<String>emptyList()
    );

    // TASK-15 adaptation: upstream checks InteropCamelCaseK3 and InteropCamelCaseJ3; the Kotlin
    // model is a DEC-6 exclusion.
    checker.check(new InteropCamelCase("1", "2", "3", "4", "5", "6", "7", "8", "9"));
  }

  // TASK-15 adaptation: upstream test name `camel case proto 2`.
  @Test public void camelCaseProto2() throws Exception {
    InteropChecker checker = new InteropChecker(
        squareup.proto2.java.interop.InteropTest.InteropCamelCase.newBuilder()
            .setHelloWorld("1")
            .setAB("2")
            .setCccDdd("3")
            .setEEeeFfGGg("4")
            .setABC("5")
            .setGHI("6")
            .setKLM("7")
            .setTUV("8")
            .setXYZ("9")
            .build(),
        "{\"helloWorld\":\"1\",\"aB\":\"2\",\"CccDdd\":\"3\",\"EEeeFfGGg\":\"4\",\"aBC\":\"5\",\"GHI\":\"6\",\"KLM\":\"7\",\"TUV\":\"8\",\"XYZ\":\"9\"}",
        "{\"hello_world\":\"1\",\"a__b\":\"2\",\"_Ccc_ddd\":\"3\",\"EEee_ff_gGg\":\"4\",\"a_b_c\":\"5\",\"GHI\":\"6\",\"K_L_M\":\"7\",\"__T__U__V__\":\"8\",\"_x_y_z_\":\"9\"}",
        Arrays.asList(
            "{\"helloWorld\":\"1\",\"aB\":\"2\",\"CccDdd\":\"3\",\"EEeeFfGGg\":\"4\",\"aBC\":\"5\",\"GHI\":\"6\",\"KLM\":\"7\",\"TUV\":\"8\",\"XYZ\":\"9\"}"),
        Collections.<String>emptyList()
    );

    // TASK-15 adaptation: upstream checks InteropCamelCaseK2 and InteropCamelCaseJ2; the Kotlin
    // model is a DEC-6 exclusion.
    checker.check(new squareup.proto2.java.interop.InteropCamelCase(
        "1", "2", "3", "4", "5", "6", "7", "8", "9"));
  }

  // TASK-15 adaptation: upstream test name `json names`.
  @Test public void jsonNames() throws Exception {
    InteropChecker checker = new InteropChecker(
        squareup.proto3.java.interop.InteropTest.InteropJsonName.newBuilder()
            .setA("1")
            .setPublic("2")
            .setCamelCase("3")
            .build(),
        "{\"one\":\"1\",\"two\":\"2\",\"three\":\"3\"}",
        "{\"one\":\"1\",\"two\":\"2\",\"three\":\"3\"}",
        Arrays.asList(
            "{\"a\":\"1\",\"public\":\"2\",\"camel_case\":\"3\"}"),
        Collections.<String>emptyList()
    );

    // TASK-15 adaptation: upstream checks InteropJsonNameJ3 and InteropJsonNameK3; the Kotlin model
    // is a DEC-6 exclusion.
    checker.check(new InteropJsonName("1", "2", "3"));
  }

  // TASK-15 adaptation: upstream test name `json names proto2`.
  @Test public void jsonNamesProto2() throws Exception {
    InteropChecker checker = new InteropChecker(
        squareup.proto2.java.interop.InteropTest.InteropJsonName.newBuilder()
            .setA("1")
            .setPublic("2")
            .setCamelCase("3")
            .build(),
        "{\"one\":\"1\",\"two\":\"2\",\"three\":\"3\"}",
        "{\"one\":\"1\",\"two\":\"2\",\"three\":\"3\"}",
        Arrays.asList(
            "{\"a\":\"1\",\"public\":\"2\",\"camel_case\":\"3\"}"),
        Collections.<String>emptyList()
    );

    // TASK-15 adaptation: upstream checks InteropJsonNameJ2 and InteropJsonNameK2; the Kotlin model
    // is a DEC-6 exclusion.
    checker.check(new squareup.proto2.java.interop.InteropJsonName("1", "2", "3"));
  }

  @Test public void optionalNonIdentity() throws Exception {
    InteropChecker checker = new InteropChecker(
        squareup.proto3.java.interop.TestProto3Optional.InteropOptional.newBuilder()
            .setValue("hello")
            .build(),
        "{\"value\":\"hello\"}",
        "{\"value\":\"hello\"}",
        Collections.<String>emptyList(),
        Arrays.asList(
            "{\"unused\": false, \"value\":\"hello\"}")
    );

    // TASK-15 adaptation: upstream checks InteropOptionalK3 and InteropOptionalJ3; the Kotlin model
    // is a DEC-6 exclusion.
    checker.check(new InteropOptional("hello"));
  }

  @Test public void optionalIdentity() throws Exception {
    InteropChecker checker = new InteropChecker(
        squareup.proto3.java.interop.TestProto3Optional.InteropOptional.newBuilder()
            .setValue("")
            .build(),
        "{\"value\":\"\"}",
        "{\"value\":\"\"}",
        Collections.<String>emptyList(),
        Arrays.asList(
            "{\"unused\": false, \"value\":\"\"}")
    );

    // TASK-15 adaptation: upstream checks InteropOptionalK3 and InteropOptionalJ3; the Kotlin model
    // is a DEC-6 exclusion.
    checker.check(new InteropOptional(""));
  }

  @Test
  @Disabled("DEC-6: the Kotlin boxed-oneof model shape (OneOf(InteropBoxOneOfK*.OPTION_A, ...)) "
      + "is a Kotlin-generator product; the oracle module generates the Java models only.")
  public void boxOneOfsKotlin() {
    /*
     * val checker = InteropChecker(
     *   protocMessage = InteropBoxOneOfP3.newBuilder()
     *     .setA("Hello")
     *     .build(),
     *   canonicalJson = """{"a":"Hello"}""",
     * )
     * checker.check(
     *   InteropBoxOneOfK2.Builder()
     *     .option(OneOf(InteropBoxOneOfK2.OPTION_A, "Hello"))
     *     .build(),
     * )
     * checker.check(
     *   InteropBoxOneOfK3.Builder()
     *     .option(OneOf(InteropBoxOneOfK3.OPTION_A, "Hello"))
     *     .build(),
     * )
     */
  }

  @Test
  @Disabled("DEC-6: sealed oneofs with builders-only Kotlin models are Kotlin-generator products; "
      + "the oracle module generates the Java models only.")
  public void sealedOneOfsKotlin_BuildersOnly() {
    /*
     * val checker = InteropChecker(
     *   protocMessage = InteropSealedOneOfBuildersOnlyP3.newBuilder()
     *     .setA("Hello")
     *     .setG(InteropSealedOneOfBuildersOnlyP3.SealedMessage.newBuilder().setContent("content").build())
     *     .setH("in the middle")
     *     .build(),
     *   canonicalJson = """{"sayMyName":"Hello","g":{"content":"content"},"h":"in the middle"}""",
     * )
     * checker.check(
     *   InteropSealedOneOfBuildersOnlyK2.Builder()
     *     .first_method(InteropSealedOneOfBuildersOnlyK2.FirstMethod.A("Hello"))
     *     .h("in the middle")
     *     .second_method(InteropSealedOneOfBuildersOnlyK2.SecondMethod.G(InteropSealedOneOfBuildersOnlyK2.SealedMessage.build { content("content") }))
     *     .build(),
     * )
     * checker.check(
     *   InteropSealedOneOfBuildersOnlyK3.Builder()
     *     .first_method(InteropSealedOneOfBuildersOnlyK3.FirstMethod.A("Hello"))
     *     .h("in the middle")
     *     .second_method(InteropSealedOneOfBuildersOnlyK3.SecondMethod.G(InteropSealedOneOfBuildersOnlyK3.SealedMessage.build { content("content") }))
     *     .build(),
     * )
     */
  }

  @Test
  @Disabled("DEC-6: sealed oneofs are a Kotlin-generator product; the oracle module generates the "
      + "Java models only.")
  public void sealedOneOfsKotlin() {
    /*
     * val checker = InteropChecker(
     *   protocMessage = InteropSealedOneOfP3.newBuilder()
     *     .setA("Hello")
     *     .setG(InteropSealedOneOfP3.SealedMessage.newBuilder().setContent("content").build())
     *     .setH("in the middle")
     *     .build(),
     *   canonicalJson = """{"sayMyName":"Hello","g":{"content":"content"},"h":"in the middle"}""",
     * )
     * checker.check(
     *   InteropSealedOneOfK2(
     *     first_method = InteropSealedOneOfK2.FirstMethod.A("Hello"),
     *     h = "in the middle",
     *     second_method = InteropSealedOneOfK2.SecondMethod.G(InteropSealedOneOfK2.SealedMessage(content = "content")),
     *   ),
     * )
     * checker.check(
     *   InteropSealedOneOfK3(
     *     first_method = InteropSealedOneOfK3.FirstMethod.A("Hello"),
     *     h = "in the middle",
     *     second_method = InteropSealedOneOfK3.SecondMethod.G(InteropSealedOneOfK3.SealedMessage(content = "content")),
     *   ),
     * )
     */
  }

  @Test
  @Disabled("Needs to implement boxed oneofs in Java.")
  public void boxOneOfsJava() {
    /*
     * val checker = InteropChecker(
     *   protocMessage = InteropBoxOneOfP3.newBuilder()
     *     .setA("Hello")
     *     .build(),
     *   canonicalJson = """{"a":"Hello"}""",
     * )
     * checker.check(InteropBoxOneOfJ2.Builder().a("Hello").build())
     * checker.check(InteropBoxOneOfJ3.Builder().a("Hello").build())
     */
    // TASK-15 note: upstream ships this case @Ignore("Needs to implement boxed oneofs in Java.");
    // the disable is preserved verbatim rather than silently enabled.
  }

  @Test public void wrappersDoesNotOmitWrappedIdentityValues() throws Exception {
    InteropChecker checker = new InteropChecker(
        squareup.proto3.java.interop.InteropTest.InteropWrappers.newBuilder()
            .setDoubleValue(DoubleValue.of(0.0))
            .setFloatValue(FloatValue.of(0f))
            .setInt64Value(Int64Value.of(0L))
            .setUint64Value(UInt64Value.of(0L))
            .setInt32Value(Int32Value.of(0))
            .setUint32Value(UInt32Value.of(0))
            .setBoolValue(BoolValue.of(false))
            .setStringValue(StringValue.of(""))
            .setBytesValue(BytesValue.of(com.google.protobuf.ByteString.EMPTY))
            .build(),
        "{\"doubleValue\":0.0,\"floatValue\":0.0,\"int64Value\":\"0\",\"uint64Value\":\"0\",\"int32Value\":0,\"uint32Value\":0,\"boolValue\":false,\"stringValue\":\"\",\"bytesValue\":\"\"}",
        "{\"doubleValue\":0.0,\"floatValue\":0.0,\"int64Value\":\"0\",\"uint64Value\":\"0\",\"int32Value\":0,\"uint32Value\":0,\"boolValue\":false,\"stringValue\":\"\",\"bytesValue\":\"\"}",
        Collections.<String>emptyList(),
        Collections.<String>emptyList()
    );
    // TASK-15 adaptation: upstream checks InteropWrappersJ3 and InteropWrappersK3; the Kotlin model
    // is a DEC-6 exclusion.
    checker.check(
        new InteropWrappers.Builder()
            .double_value(0.0)
            .float_value(0f)
            .int64_value(0L)
            .uint64_value(0L)
            .int32_value(0)
            .uint32_value(0)
            .bool_value(false)
            .string_value("")
            .bytes_value(ByteString.EMPTY)
            .build());
  }

  @Test public void wrappersWithNulls() throws Exception {
    InteropChecker checker = new InteropChecker(
        squareup.proto3.java.interop.InteropTest.InteropWrappers.newBuilder().build(),
        "{}",
        "{}",
        Collections.<String>emptyList(),
        Collections.<String>emptyList()
    );
    // TASK-15 adaptation: upstream checks InteropWrappersJ3 and InteropWrappersK3; the Kotlin model
    // is a DEC-6 exclusion.
    checker.check(new InteropWrappers.Builder().build());
  }

  @Test
  @Disabled("DEC-6: the Easter fixtures (squareup.proto3.kotlin.unrecognized_constant on the wire "
      + "side, EasterOuterClass on the protoc side) are Kotlin-generator proto packages that the "
      + "oracle module's pinned fixture generation excludes.")
  public void keywordNamedConstant() {
    /*
     * // ┌─ 2: 15
     * // ├─ 3: 2
     * // ├─ 4: 1
     * // ├─ 4: 15
     * // ╰- 5: 15
     * val bytes = "100f18022001200f2a010f"
     * val wireMessage: EasterK3 = EasterK3.ADAPTER.decode(bytes.decodeHex())
     * val protocMessage: EasterP3 = EasterP3.parseFrom(bytes.decodeHex().toByteArray())
     *
     * val checker = InteropChecker(
     *   protocMessage = protocMessage,
     *   canonicalJson = """{"optionalEasterAnimal":"object","identityEasterAnimal":"HEN","easterAnimalsRepeated":["BUNNY","object"],"easterAnimalsPacked":["object"]}""",
     * )
     *
     * checker.check(wireMessage)
     */
  }

  @Test
  @Disabled("DEC-6: the wire side of this case is the Kotlin unrecognized-constant enum shape "
      + "(EasterK3 with Unrecognized values), a Kotlin-generator product the oracle module does "
      + "not generate.")
  public void unknownEnumsWithUnrecognizedConstant() {
    /*
     * // ┌─ 2: 5
     * // ├─ 3: 6
     * // ├─ 4: 7
     * // ├─ 4: 2
     * // ├─ 4: 6
     * // ├─ 5: 8
     * // ├─ 5: 2
     * // ├─ 5: 9
     * // ╰- 5: 1
     * val bytes = "100518062007200220062a0408020901"
     * val wireMessage: EasterK3 = EasterK3.ADAPTER.decode(bytes.decodeHex())
     * val protocMessage: EasterP3 = EasterP3.parseFrom(bytes.decodeHex().toByteArray())
     *
     * val checker = InteropChecker(
     *   protocMessage = protocMessage,
     *   canonicalJson = """{"optionalEasterAnimal":5,"identityEasterAnimal":6,"easterAnimalsRepeated":[7,"HEN",6],"easterAnimalsPacked":[8,"HEN",9,"BUNNY"]}""",
     * )
     *
     * checker.check(wireMessage)
     */
  }

  @Test
  @Disabled("DEC-6: the builders-only Kotlin model (squareup.proto2.kotlin.buildersonly) is a "
      + "Kotlin-generator product the oracle module does not generate.")
  public void buildersOnlyMessage() {
    /*
     * val checker = InteropChecker(
     *   protocMessage = BuildersOnlyMessageP2.newBuilder()
     *     .setBuilder("my_builder")
     *     .setData(64)
     *     .addAllMessage(listOf(33, 806))
     *     .setNestedMessage(NestedMessageP2.newBuilder().setA(99).build())
     *     .setInt32(32)
     *     .setValue(95)
     *     .addAllInt64(listOf(94440L, 77000L, 79510L, 44880L))
     *     .putAllMap(mapOf("one" to "un", "two" to "deux"))
     *     .build(),
     *   canonicalJson = """{"builder":"my_builder","data":64,"message":[33,806],"nestedMessage":{"a":99},"int64":["94440","77000","79510","44880"],"map":{"one":"un","two":"deux"},"int32":32,"value":95}""",
     *   wireCanonicalJson = """{"builder":"my_builder","data":64,"message":[33,806],"nested_message":{"a":99},"int64":[94440,77000,79510,44880],"map":{"one":"un","two":"deux"},"int32":32,"value":95}""",
     * )
     *
     * val wireMessage = BuildersOnlyMessageK2.Builder()
     *   .builder_("my_builder")
     *   .data_(64)
     *   .message(listOf(33, 806))
     *   .squareup_proto2_kotlin_buildersonly_int32(32)
     *   .package_(OneOf(PACKAGE_VALUE, 95))
     *   .map(mapOf("one" to "un", "two" to "deux"))
     *   .squareup_proto2_kotlin_buildersonly_int64(listOf(94440L, 77000L, 79510L, 44880L))
     *   .nested_message(NestedMessageK2.Builder().a(99).build())
     *   .build()
     * checker.check(wireMessage)
     */
  }

  @Test public void wrappers() throws Exception {
    InteropChecker checker = new InteropChecker(
        squareup.proto3.java.interop.InteropTest.InteropWrappers.newBuilder()
            .setDoubleValue(DoubleValue.of(1.0))
            .setFloatValue(FloatValue.of(2f))
            .setInt64Value(Int64Value.of(3L))
            .setUint64Value(UInt64Value.of(4L))
            .setInt32Value(Int32Value.of(5))
            .setUint32Value(UInt32Value.of(6))
            .setBoolValue(BoolValue.of(true))
            .setStringValue(StringValue.of("string"))
            .setBytesValue(BytesValue.of(com.google.protobuf.ByteString.copyFrom(new byte[] {1})))
            .build(),
        "{\"doubleValue\":1.0,\"floatValue\":2.0,\"int64Value\":\"3\",\"uint64Value\":\"4\",\"int32Value\":5,\"uint32Value\":6,\"boolValue\":true,\"stringValue\":\"string\",\"bytesValue\":\"AQ==\"}",
        "{\"doubleValue\":1.0,\"floatValue\":2.0,\"int64Value\":\"3\",\"uint64Value\":\"4\",\"int32Value\":5,\"uint32Value\":6,\"boolValue\":true,\"stringValue\":\"string\",\"bytesValue\":\"AQ==\"}",
        Collections.<String>emptyList(),
        Collections.<String>emptyList()
    );
    // TASK-15 adaptation: upstream checks InteropWrappersJ3 and InteropWrappersK3; the Kotlin model
    // is a DEC-6 exclusion.
    checker.check(
        new InteropWrappers.Builder()
            .double_value(1.0)
            .float_value(2f)
            .int64_value(3L)
            .uint64_value(4L)
            .int32_value(5)
            .uint32_value(6)
            .bool_value(true)
            .string_value("string")
            .bytes_value(ByteString.of((byte) 1))
            .build());
  }

  @Test
  @Disabled("DEC-6: the only live upstream check builds InteropRepeatedEnumsK3 with an "
      + "Unrecognized(8) constant; the Kotlin sealed-enum shape is a Kotlin-generator product and "
      + "the Java enum model cannot express an unrecognized constant. Upstream's Java-model check "
      + "is commented out upstream (\"Without EnumMode.SEALED_CLASS, Wire fails to print the "
      + "unknown constant\").")
  public void repeatedEnums() {
    /*
     * val checker = InteropChecker(
     *   protocMessage = InteropRepeatedEnums.newBuilder()
     *     .addAllAvailableSizes(
     *       listOf(
     *         InteropRepeatedEnums.Size.SMALL,
     *         InteropRepeatedEnums.Size.MEDIUM,
     *         InteropRepeatedEnums.Size.LARGE,
     *       ),
     *     )
     *     .addAllAvailableSizesValue(listOf(8))
     *     .build(),
     *   canonicalJson = """{"availableSizes":["SMALL","MEDIUM","LARGE",8]}""",
     * )
     * // TODO(Benoit) Without EnumMode.SEALED_CLASS, Wire fails to print the unknown constant.
     * // checker.check(
     * //   InteropRepeatedEnumsJ3.Builder()
     * //     .available_sizes(listOf(
     * //       InteropRepeatedEnumsJ3.Size.SMALL,
     * //       InteropRepeatedEnumsJ3.Size.MEDIUM,
     * //       InteropRepeatedEnumsJ3.Size.LARGE,
     * //       ))
     * //     .addUnknownField(3, FieldEncoding.VARINT, 8)
     * //     .build(),
     * // )
     * checker.check(
     *   InteropRepeatedEnumsK3.Builder()
     *     .available_sizes(
     *       listOf(
     *         InteropRepeatedEnumsK3.Size.SMALL,
     *         InteropRepeatedEnumsK3.Size.MEDIUM,
     *         InteropRepeatedEnumsK3.Size.LARGE,
     *         InteropRepeatedEnumsK3.Size.Unrecognized(8),
     *       ),
     *     )
     *     .build(),
     * )
     */
  }
}
