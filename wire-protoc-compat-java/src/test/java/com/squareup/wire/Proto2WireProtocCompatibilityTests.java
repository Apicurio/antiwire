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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ExtensionRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import squareup.proto2.java.empty.EmptyLength;
import squareup.proto2.java.empty.EmptyLengthOuterClass;
import squareup.proto2.java.interop.InteropMessage;
import squareup.proto2.java.interop.InteropMessageOuterClass;
import squareup.proto2.java.interop.InteropRepeatedUint;
import squareup.proto2.java.interop.InteropTest;
import squareup.proto2.java.interop.SubInteropRepeatedUint;
import squareup.proto2.java.interop.type.EnumProto2;
import squareup.proto2.java.interop.type.InteropTypes;
import squareup.proto2.java.interop.type.MessageProto2;
import squareup.proto3.java.interop.type.EnumProto3;
import squareup.proto3.java.interop.type.MessageProto3;

/**
 * TASK-15 port of upstream wire-protoc-compatibility-tests Proto2WireProtocCompatibilityTests.kt
 * (pinned tag 7.1.0).
 *
 * <p>Applicability (TASK-15): upstream exercises proto2 fixtures from Kotlin proto packages
 * (com.squareup.wire.proto2.kotlin.*: SimpleMessage, AllTypes, MapTypes, RepeatedEnum/Quilt,
 * Easter, WireMessage extensions) alongside the Java interop fixtures (squareup.proto2.java.*).
 * The oracle module generates the Java fixtures only, so the cases backed exclusively by the
 * Kotlin-package fixtures are disabled with DEC-6 reasons and their upstream bodies preserved as
 * comments; the three cases with Java fixtures are ported. The companion constants that fed only
 * disabled cases (defaultAllTypesWire, defaultAllTypesProtoc, identityAllTypesWire,
 * identityAllTypesProtoc, interopWireK) are not ported; interopProtoc and interopWireJ are.
 */
public class Proto2WireProtocCompatibilityTests {
  @Test
  @Disabled("DEC-6: the SimpleMessage fixtures live in the Kotlin proto package "
      + "com.squareup.wire.proto2.kotlin.simple, which the oracle module's pinned fixture "
      + "generation excludes; no Java-model equivalent was generated.")
  public void simpleMessage() {
    /*
     * val wireMessage = SimpleMessageK(
     *   optional_nested_msg = SimpleMessageK.NestedMessage(806),
     *   no_default_nested_enum = SimpleMessageK.NestedEnum.BAR,
     *   repeated_double = listOf(1.0, 33.0),
     *   required_int32 = 46,
     *   other = "hello",
     * )
     *
     * val googleMessage = SimpleMessageOuterClass.SimpleMessage.newBuilder()
     *   .setOptionalNestedMsg(
     *     SimpleMessageOuterClass.SimpleMessage.NestedMessage.newBuilder().setBb(806).build(),
     *   )
     *   .setNoDefaultNestedEnum(SimpleMessageOuterClass.SimpleMessage.NestedEnum.BAR)
     *   .addAllRepeatedDouble(listOf(1.0, 33.0))
     *   .setRequiredInt32(46)
     *   .setOther("hello")
     *   .build()
     *
     * val encodedWireMessage: ByteArray = wireMessage.encode()
     * val encodedGoogleMessage: ByteArray = googleMessage.toByteArray()
     *
     * assertThat(encodedWireMessage).isEqualTo(encodedGoogleMessage)
     *
     * val wireMessageDecodedFromGoogleMessage =
     *   SimpleMessageK.ADAPTER.decode(encodedGoogleMessage)
     * val googleMessageDecodedFromWireMessage =
     *   SimpleMessageOuterClass.SimpleMessage.parseFrom(encodedWireMessage)
     *
     * assertThat(wireMessageDecodedFromGoogleMessage).isEqualTo(wireMessage)
     * assertThat(googleMessageDecodedFromWireMessage).isEqualTo(googleMessage)
     */
  }

  @Test
  @Disabled("DEC-6: the proto2 AllTypes fixtures and their extensions live in the Kotlin proto "
      + "package com.squareup.wire.proto2.kotlin.alltypes, excluded by the oracle module's pinned "
      + "fixture generation; no Java-model equivalent was generated.")
  public void allTypesSerialization() {
    /*
     * val byteArrayWire = AllTypesK.ADAPTER.encode(defaultAllTypesWire)
     * val byteArrayProtoc = defaultAllTypesProtoc.toByteArray()
     *
     * assertThat(AllTypesK.ADAPTER.decode(byteArrayProtoc)).isEqualTo(defaultAllTypesWire)
     * assertThat(AllTypesOuterClass.AllTypes.parseFrom(byteArrayWire, allTypesRegistry))
     *   .isEqualTo(defaultAllTypesProtoc)
     */
  }

  @Test
  @Disabled("DEC-6: same fixtures as allTypesSerialization (com.squareup.wire.proto2.kotlin."
      + "alltypes), excluded by the oracle module's pinned fixture generation.")
  public void allTypesSerializationWithEmptyOrIdentityValues() {
    /*
     * val byteArrayWire = AllTypesK.ADAPTER.encode(identityAllTypesWire)
     * val byteArrayProtoc = identityAllTypesProtoc.toByteArray()
     *
     * assertThat(AllTypesK.ADAPTER.decode(byteArrayProtoc)).isEqualTo(identityAllTypesWire)
     *
     * assertThat(AllTypesOuterClass.AllTypes.parseFrom(byteArrayWire, allTypesRegistry))
     *   .isEqualTo(identityAllTypesProtoc)
     */
  }

  @Test
  @Disabled("DEC-6: the WireMessage extension fixtures live in the Kotlin proto packages "
      + "com.squareup.wire.proto2.wire.extensions and com.squareup.wire.proto2.kotlin.extensions, "
      + "excluded by the oracle module's pinned fixture generation.")
  public void protocDontThrowUpOnWireExtensions() {
    /*
     * assertThat(WireMessageOuterClass.WireMessage.newBuilder().build()).isNotNull()
     * assertThat(WireMessage()).isNotNull()
     */
  }

  @Test public void repeatedZeroLengthUint32Fields() throws Exception {
    byte[] bytes = new byte[] {18, 0};

    assertEquals(
        EmptyLengthOuterClass.EmptyLength.newBuilder().build(),
        EmptyLengthOuterClass.EmptyLength.parseFrom(bytes));
    // TASK-15 adaptation: upstream also decodes into the Kotlin fixture EmptyLengthK (DEC-6); the
    // Java wire model below exercises the same bytes.
    assertEquals(
        new EmptyLength(Collections.<Integer>emptyList()),
        EmptyLength.ADAPTER.decode(bytes));

    assertEquals(0, EmptyLengthOuterClass.EmptyLength.parseFrom(bytes).toByteArray().length);
    assertEquals(0, EmptyLength.ADAPTER.decode(bytes).encode().length);
  }

  @Test public void serializeProto2Proto3Interop() throws Exception {
    byte[] byteArrayWireJ = InteropMessage.ADAPTER.encode(interopWireJ);
    byte[] byteArrayProtoc = interopProtoc.toByteArray();

    // Note: we don't test equality between protoc byte arrays and ours because extensions are not
    // at the same location in the array.
    // TASK-15 adaptation: upstream also encodes interopWireK and asserts byteArrayWireK equals
    // byteArrayWireJ (DEC-6: the Kotlin model is not generated). That assertion compared two Wire
    // encoders of the same message; the cross-decode assertions below carry the interop substance.
    assertEquals(interopWireJ, InteropMessage.ADAPTER.decode(byteArrayProtoc));
    assertEquals(
        interopProtoc,
        InteropMessageOuterClass.InteropMessage.parseFrom(byteArrayWireJ, interopRegistry));
  }

  @Test
  @Disabled("DEC-6: the MapTypes fixtures live in the Kotlin proto package "
      + "com.squareup.wire.proto2.kotlin, excluded by the oracle module's pinned fixture "
      + "generation; no Java-model equivalent was generated.")
  public void mapKeysAndValuesDefaultsToTheirRespectiveIdentityValue() {
    /*
     * // Bytes for the message `MapType` message with 2 entries on the field `map_string_string`. The
     * // first one has a key but not value, the second one has a value without key. Those are manually
     * // generated because Protoc and Wire don't write maps this way but can decode them though.
     * val bytes = listOf(
     *   0x0a, // MapType.map_string_string tag -> 1|010 -> 10 -> x0a
     *   0x04, // length
     *   0x0a, // map key tag 1 -> 1|010 -> 10 -> x0a
     *   0x02, // length
     *   0x64, 0x65, // de
     *   0x0a, // MapType.map_string_string tag -> 1|010 -> 10 -> x0a
     *   0x04, // length
     *   0x12, // map value tag 2 -> 10|010 -> 18 -> x12
     *   0x02, // length
     *   0x65, 0x64, // ed
     * ).map { it.toByte() }.toByteArray()
     *
     * val mapTypeProtoc = MapTypesOuterClass.MapTypes.parseFrom(bytes)
     *
     * assertThat(mapTypeProtoc.mapStringStringCount).isEqualTo(2)
     * assertThat(mapTypeProtoc.mapStringStringMap["de"]).isEqualTo("")
     * assertThat(mapTypeProtoc.mapStringStringMap[""]).isEqualTo("ed")
     *
     * val mapTypeWire = MapTypes.ADAPTER.decode(bytes)
     *
     * assertThat(mapTypeWire.map_string_string.size).isEqualTo(2)
     * assertThat(mapTypeWire.map_string_string["de"]).isEqualTo("")
     * assertThat(mapTypeWire.map_string_string[""]).isEqualTo("ed")
     */
  }

  @Test public void decodingRepeatedEntries() throws Exception {
    // ── 1 ┐
    //      ├─ 1: 3
    //      ╰- 1: 3
    byte[] byteArray = new byte[] {10, 3, 10, 1, 3, 10, 3, 10, 1, 3};
    assertEquals(
        InteropTest.InteropRepeatedUint.newBuilder()
            .setSubMessage(
                InteropTest.SubInteropRepeatedUint.newBuilder()
                    .addAllRepeatedValues(Arrays.asList(3, 3))
                    .build())
            .build(),
        InteropTest.InteropRepeatedUint.parseFrom(byteArray));
    // TASK-15 adaptation: upstream also decodes into the Kotlin model InteropRepeatedUintK2
    // (DEC-6); the Java wire model below exercises the same bytes.
    assertEquals(
        new InteropRepeatedUint.Builder()
            .sub_message(
                new SubInteropRepeatedUint.Builder()
                    .repeated_values(Arrays.asList(3, 3))
                    .build())
            .build(),
        InteropRepeatedUint.ADAPTER.decode(byteArray));
  }

  /**
   * The TS-proto library encodes enums differently from Wire or Protoc. Protoc is fine with this,
   * but it causes Wire to crash. The fix is to make Wire accept this format also.
   *
   * https://github.com/stephenh/ts-proto
   */
  @Test
  @Disabled("DEC-6: the QuiltContainer/Quilt/RepeatedEnum fixtures live in the Kotlin proto "
      + "package com.squareup.wire.proto2.kotlin.interop, excluded by the oracle module's pinned "
      + "fixture generation; no Java-model equivalent was generated.")
  public void repeatedEnumIsPackedAndLengthIsZero() {
    /*
     * val wireMessage = QuiltContainer(
     *   quilt = Quilt(
     *     fringe = listOf(QuiltColor.GREEN),
     *     cozy = true,
     *   ),
     * )
     *
     * // A sample value encoded by ts-proto.
     * val encodedByTsProto = "12091201041a0022003801".decodeHex()
     *
     * val googleMessage = RepeatedEnum.QuiltContainer.newBuilder()
     *   .setQuilt(
     *     RepeatedEnum.Quilt.newBuilder()
     *       .addAllFringe(listOf(RepeatedEnum.QuiltColor.GREEN))
     *       .setCozy(true)
     *       .build(),
     *   ).build()
     *
     * val wireMessageDecodedFromGoogleMessage =
     *   QuiltContainer.ADAPTER.decode(encodedByTsProto)
     * val googleMessageDecodedFromWireMessage =
     *   RepeatedEnum.QuiltContainer.parseFrom(encodedByTsProto.toByteArray())
     *
     * assertThat(wireMessageDecodedFromGoogleMessage).isEqualTo(wireMessage)
     * assertThat(googleMessageDecodedFromWireMessage).isEqualTo(googleMessage)
     */
  }

  @Test
  @Disabled("DEC-6: the Easter fixtures live in the Kotlin proto package "
      + "com.squareup.wire.proto2.kotlin.unrecognized_constant, excluded by the oracle module's "
      + "pinned fixture generation; no Java-model equivalent was generated.")
  public void encodingAndDecodingOfUnrecognizedEnumConstants_negativeValue_proto2Message() {
    /*
     * // ┌─ 2: -1
     * // ╰- 3: 2
     * val bytes = "10ffffffffffffffffff011802"
     * val wireMessage: EasterK2 = EasterK2.ADAPTER.decode(bytes.decodeHex())
     * val protocMessage: EasterP2 = EasterP2.parseFrom(bytes.decodeHex().toByteArray())
     *
     * assertThat(protocMessage.optionalEasterAnimal).isEqualTo(EasterAnimalP3.EASTER_ANIMAL_DEFAULT)
     * assertThat(protocMessage.optionalEasterAnimal.number).isEqualTo(0)
     *
     * // Keeping that around to clearly show that Wire has a different behavior that protoc. Not sure
     * // that we want to fix this. Protoc assigns it to 0 even for proto2 messages using the proto3
     * // enum.
     * assertThat(wireMessage.optional_easter_animal).isEqualTo(EasterAnimalK3.Unrecognized(-1))
     */
  }

  @Test
  @Disabled("DEC-6: the Easter fixtures live in the Kotlin proto package "
      + "com.squareup.wire.proto2.kotlin.unrecognized_constant, excluded by the oracle module's "
      + "pinned fixture generation; no Java-model equivalent was generated.")
  public void encodingAndDecodingOfUnrecognizedEnumConstants_knownValue_proto2Message() {
    /*
     * // ┌─ 2: 1
     * // ╰- 3: 1
     * val bytes = "10011801"
     * val wireMessage: EasterK2 = EasterK2.ADAPTER.decode(bytes.decodeHex())
     * val protocMessage: EasterP2 = EasterP2.parseFrom(bytes.decodeHex().toByteArray())
     *
     * assertThat(wireMessage.required_easter_animal.value).isEqualTo(protocMessage.requiredEasterAnimal.number)
     * assertThat(wireMessage.optional_easter_animal!!.value).isEqualTo(protocMessage.optionalEasterAnimal.number)
     *
     * assertThat(protocMessage.optionalEasterAnimal).isEqualTo(EasterAnimalP3.BUNNY)
     * assertThat(protocMessage.optionalEasterAnimal.number).isEqualTo(EasterAnimalP3.BUNNY_VALUE)
     * assertThat(protocMessage.requiredEasterAnimal).isEqualTo(EasterAnimalP3.BUNNY)
     * assertThat(protocMessage.requiredEasterAnimal.number).isEqualTo(EasterAnimalP3.BUNNY_VALUE)
     *
     * assertThat(wireMessage.optional_easter_animal).isEqualTo(EasterAnimalK3.BUNNY)
     * assertThat(wireMessage.required_easter_animal).isEqualTo(EasterAnimalK3.BUNNY)
     */
  }

  @Test
  @Disabled("DEC-6: the Easter fixtures live in the Kotlin proto package "
      + "com.squareup.wire.proto2.kotlin.unrecognized_constant, excluded by the oracle module's "
      + "pinned fixture generation; no Java-model equivalent was generated.")
  public void encodingAndDecodingOfUnrecognizedEnumConstants_unknownValue_proto2Message() {
    /*
     * // Both Wire and Protoc throw if the required field isn't known.
     * // ┌─ 2: 5
     * // ├─ 3: 2
     * // ├─ 4: 5
     * // ╰- 4: 5
     * val bytes = "1005180220052005"
     * val wireMessage: EasterK2 = EasterK2.ADAPTER.decode(bytes.decodeHex())
     * val protocMessage: EasterP2 = EasterP2.parseFrom(bytes.decodeHex().toByteArray())
     *
     * assertThat(protocMessage.optionalEasterAnimal).isEqualTo(EasterAnimalP3.EASTER_ANIMAL_DEFAULT)
     * assertThat(protocMessage.optionalEasterAnimal.number).isEqualTo(0)
     * assertThat(protocMessage.easterAnimalsList).isEmpty()
     *
     * // Keeping that around to clearly show that Wire has a different behavior that protoc. Not sure
     * // that we want to fix this. Protoc assigns it to 0 even for proto2 messages using the proto3
     * // enum.
     * assertThat(wireMessage.optional_easter_animal).isEqualTo(EasterAnimalK3.Unrecognized(5))
     * assertThat(wireMessage.easter_animals).isEqualTo(listOf(EasterAnimalK3.Unrecognized(5), EasterAnimalK3.Unrecognized(5)))
     */
  }

  private static final ExtensionRegistry interopRegistry = createInteropRegistry();

  private static ExtensionRegistry createInteropRegistry() {
    ExtensionRegistry registry = ExtensionRegistry.newInstance();
    registry.add(InteropMessageOuterClass.extOptProto2Enum);
    registry.add(InteropMessageOuterClass.extOptProto2Message);
    registry.add(InteropMessageOuterClass.extOptProto3Enum);
    registry.add(InteropMessageOuterClass.extOptProto3Message);
    registry.add(InteropMessageOuterClass.extRepProto2Enum);
    registry.add(InteropMessageOuterClass.extRepProto2Message);
    registry.add(InteropMessageOuterClass.extRepProto3Enum);
    registry.add(InteropMessageOuterClass.extRepProto3Message);
    return registry;
  }

  private static final InteropMessageOuterClass.InteropMessage interopProtoc =
      InteropMessageOuterClass.InteropMessage.newBuilder()
          .setOptProto2Enum(InteropTypes.EnumProto2.UNKNOWN)
          .setOptProto2Message(
              InteropTypes.MessageProto2.newBuilder().setA(33).setB("Grant").build())
          .setOptProto3Enum(squareup.proto3.java.interop.type.InteropTypes.EnumProto3.A)
          .setOptProto3Message(
              squareup.proto3.java.interop.type.InteropTypes.MessageProto3.newBuilder()
                  .setA(806).build())
          .setReqProto2Enum(InteropTypes.EnumProto2.A)
          .setReqProto2Message(
              InteropTypes.MessageProto2.newBuilder().setA(1).setB("Penny").build())
          .setReqProto3Enum(squareup.proto3.java.interop.type.InteropTypes.EnumProto3.UNKNOWN)
          .setReqProto3Message(
              squareup.proto3.java.interop.type.InteropTypes.MessageProto3.newBuilder()
                  .setA(-1).build())
          .addAllRepProto2Enum(list(InteropTypes.EnumProto2.A))
          .addAllRepProto2Message(
              list(InteropTypes.MessageProto2.newBuilder().setA(7).setB("Pete").build()))
          .addAllRepProto3Enum(
              list(squareup.proto3.java.interop.type.InteropTypes.EnumProto3.UNKNOWN))
          .addAllRepProto3Message(
              list(squareup.proto3.java.interop.type.InteropTypes.MessageProto3.newBuilder()
                  .setA(0).build()))
          .addAllPackProto2Enum(list(InteropTypes.EnumProto2.A))
          .addAllPackProto3Enum(
              list(squareup.proto3.java.interop.type.InteropTypes.EnumProto3.A))
          .putMapStringProto2Enum("a", InteropTypes.EnumProto2.UNKNOWN)
          .putMapStringProto2Message(
              "b", InteropTypes.MessageProto2.newBuilder().setA(21).setB("Jimmy").build())
          .putMapStringProto3Enum(
              "c", squareup.proto3.java.interop.type.InteropTypes.EnumProto3.A)
          .putMapStringProto3Message(
              "d", squareup.proto3.java.interop.type.InteropTypes.MessageProto3.newBuilder()
                  .setA(33333).build())
          .setOneofProto3Enum(squareup.proto3.java.interop.type.InteropTypes.EnumProto3.A)
          .setExtension(InteropMessageOuterClass.extOptProto2Enum, InteropTypes.EnumProto2.A)
          .setExtension(
              InteropMessageOuterClass.extOptProto2Message,
              InteropTypes.MessageProto2.newBuilder().setA(8).setB("Kobe").build())
          .setExtension(
              InteropMessageOuterClass.extOptProto3Enum,
              squareup.proto3.java.interop.type.InteropTypes.EnumProto3.A)
          .setExtension(
              InteropMessageOuterClass.extOptProto3Message,
              squareup.proto3.java.interop.type.InteropTypes.MessageProto3.newBuilder()
                  .setA(24).build())
          .setExtension(
              InteropMessageOuterClass.extRepProto2Enum, list(InteropTypes.EnumProto2.A))
          .setExtension(
              InteropMessageOuterClass.extRepProto2Message,
              list(InteropTypes.MessageProto2.newBuilder().setA(3).setB("Dwyane").build()))
          .setExtension(
              InteropMessageOuterClass.extRepProto3Enum,
              list(squareup.proto3.java.interop.type.InteropTypes.EnumProto3.A))
          .setExtension(
              InteropMessageOuterClass.extRepProto3Message,
              list(squareup.proto3.java.interop.type.InteropTypes.MessageProto3.newBuilder()
                  .setA(1).build()))
          .build();

  private static final InteropMessage interopWireJ =
      new InteropMessage.Builder()
          .opt_proto2_enum(EnumProto2.UNKNOWN)
          .opt_proto2_message(new MessageProto2(33, "Grant"))
          .opt_proto3_enum(EnumProto3.A)
          .opt_proto3_message(
              new MessageProto3.Builder().a(806).build())
          .req_proto2_enum(EnumProto2.A)
          .req_proto2_message(new MessageProto2(1, "Penny"))
          .req_proto3_enum(EnumProto3.UNKNOWN)
          .req_proto3_message(
              new MessageProto3.Builder().a(-1).build())
          .rep_proto2_enum(list(EnumProto2.A))
          .rep_proto2_message(list(new MessageProto2(7, "Pete")))
          .rep_proto3_enum(list(EnumProto3.UNKNOWN))
          .rep_proto3_message(
              list(new MessageProto3.Builder().a(0).build()))
          .pack_proto2_enum(list(EnumProto2.A))
          .pack_proto3_enum(list(EnumProto3.A))
          .map_string_proto2_enum(Collections.singletonMap("a", EnumProto2.UNKNOWN))
          .map_string_proto2_message(
              Collections.singletonMap("b", new MessageProto2(21, "Jimmy")))
          .map_string_proto3_enum(Collections.singletonMap("c", EnumProto3.A))
          .map_string_proto3_message(
              Collections.singletonMap(
                  "d",
                  new MessageProto3.Builder().a(33333).build()))
          .ext_opt_proto2_enum(EnumProto2.A)
          .ext_opt_proto2_message(new MessageProto2(8, "Kobe"))
          .ext_opt_proto3_enum(EnumProto3.A)
          .ext_opt_proto3_message(
              new MessageProto3.Builder().a(24).build())
          .ext_rep_proto2_enum(list(EnumProto2.A))
          .ext_rep_proto2_message(list(new MessageProto2(3, "Dwyane")))
          .ext_rep_proto3_enum(list(EnumProto3.A))
          .ext_rep_proto3_message(
              list(new MessageProto3.Builder().a(1).build()))
          .oneof_proto3_enum(EnumProto3.A)
          .build();

  private static <T> List<T> list(T t) {
    return Arrays.asList(t, t);
  }
}
