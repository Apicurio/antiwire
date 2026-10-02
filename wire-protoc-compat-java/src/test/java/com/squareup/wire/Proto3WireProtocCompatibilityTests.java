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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.Value;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import okio.ByteString;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import squareup.proto2.java.interop.type.InteropTypes;
import squareup.proto2.java.interop.type.MessageProto2;
import squareup.proto3.java.alltypes.AllStructs;
import squareup.proto3.java.alltypes.AllStructsOuterClass;
import squareup.proto3.java.alltypes.AllTypes;
import squareup.proto3.java.alltypes.AllTypesOuterClass;
import squareup.proto3.java.alltypes.AllWrappers;
import squareup.proto3.java.alltypes.AllWrappersOuterClass;
import squareup.proto3.java.interop.InteropDuration;
import squareup.proto3.java.interop.InteropMessage;
import squareup.proto3.java.interop.InteropMessageOuterClass;
import squareup.proto3.java.interop.InteropRepeatedEnums;
import squareup.proto3.java.interop.InteropTest;
import squareup.proto3.java.interop.type.EnumProto3;
import squareup.proto3.java.interop.type.MessageProto3;

/**
 * Java 11 port of the upstream Kotlin suite
 * wire-protoc-compatibility-tests/src/test/java/com/squareup/wire/Proto3WireProtocCompatibilityTests.kt
 * (pinned tag 7.1.0), TASK-15.
 *
 * <p>Adaptation ledger (translation-conventions.md section 6; rules R1 through R5 plus the
 * TASK-15 applicability rule):
 *
 * <ul>
 *   <li>assertk is mapped to JUnit 5 assertions; {@code assertJsonEquals} is realized by parsing
 *       both operands with the reference implementation's own JsonFormat parser into
 *       DynamicMessages of the case's type and asserting message equality, which preserves
 *       upstream's order-insensitive JSON-tree comparison.
 *   <li>Kotlin-model halves ({@code squareup.proto3.kotlin.*}) of otherwise portable cases are
 *       omitted: those fixture packages are not generated in this module (DEC-6). Every such case
 *       keeps its wire-java-versus-protoc assertions, which cover the same wire bytes.
 *   <li>Cases whose fixtures live only in Kotlin packages (pizza, easter, all32/all64,
 *       camel_case, map_types, required_extension, wire extensions) are disabled under DEC-6 with
 *       their bodies preserved below. The six AllTypes JSON golden cases are disabled because the
 *       java-package AllTypes proto uses unprefixed field names ({@code int32} versus upstream's
 *       {@code my_int32}) while the goldens are written for the Kotlin-package model; expected
 *       values may not change (section 6.4).
 *   <li>{@code durationProto} and {@code structProto} are ported onto the equivalent java-package
 *       fixtures ({@code InteropDuration}, {@code AllStructs}); the Duration/Struct payload values
 *       and the wire-versus-protoc assertions are unchanged.
 *   <li>The backticked upstream case name {@code protoc validation camel case json} becomes the
 *       Java identifier {@code protocValidationCamelCaseJson} (Java identifier constraint).
 * </ul>
 */
public class Proto3WireProtocCompatibilityTests {
  // Note: this test mostly make sure we compile required extension without failing.
  @Test
  @Disabled("DEC-6: fixtures live only in the kotlin packages (required_extension.proto is not\n"
      + "generated: com.squareup.wire.proto3.kotlin.requiredextension on the wire side and\n"
      + "squareup.proto3.kotlin.requiredextension on the protoc side).")
  public void protocAndRequiredExtensions() {
    /*
    val wireMessage = RequiredExtensionMessageK("Yo")

    val googleMessage = RequiredExtensionK.RequiredExtensionMessage.newBuilder()
      .setStringField("Yo")
      .build()

    assertThat(wireMessage.encode()).isEqualTo(googleMessage.toByteArray())

    // Although the custom options has no label, it shouldn't be "required" to instantiate
    // `FieldOptions`. We should not fail.
    assertThat(DescriptorProtos.FieldOptions.newBuilder().build()).isNotNull()
    assertThat(FieldOptions()).isNotNull()
    */
  }

  @Test
  @Disabled("DEC-6: PizzaOuterClass and PizzaDeliveryK are kotlin-package fixtures (pizza.proto),\n"
      + "not generated in this module; the case's assertJsonEquals also relies on the\n"
      + "excluded moshi JSON surface.")
  public void protocJson() {
    /*
    val pizzaDelivery = PizzaOuterClass.PizzaDelivery.newBuilder()
      .setAddress("507 Cross Street")
      .setDeliveredWithinOrFree(
        Duration.newBuilder()
          .setSeconds(1_799)
          .setNanos(500_000_000)
          .build(),
      )
      .addPizzas(
        PizzaOuterClass.Pizza.newBuilder()
          .addToppings("pineapple")
          .addToppings("onion")
          .build(),
      )
      .setPromotion(
        Any.pack(
          PizzaOuterClass.BuyOneGetOnePromotion.newBuilder()
            .setCoupon("MAUI")
            .build(),
        ),
      )
      .setOrderedAt(
        Timestamp.newBuilder()
          .setSeconds(-631152000L) // 1950-01-01T00:00:00.250Z.
          .setNanos(250_000_000)
          .build(),
      )
      .setLoyalty(emptyMap<String, Any>().toStruct())
      .build()

    val typeRegistry = JsonFormat.TypeRegistry.newBuilder()
      .add(PizzaOuterClass.BuyOneGetOnePromotion.getDescriptor())
      .add(PizzaOuterClass.FreeGarlicBreadPromotion.getDescriptor())
      .build()

    // The shared proto schema don't have the same package.
    val json = PIZZA_DELIVERY_JSON.replace(
      "type.googleapis.com/squareup.proto3.BuyOneGetOnePromotion",
      "type.googleapis.com/squareup.proto3.kotlin.pizza.BuyOneGetOnePromotion",
    )

    val jsonPrinter = JsonFormat.printer()
      .usingTypeRegistry(typeRegistry)
    assertJsonEquals(jsonPrinter.print(pizzaDelivery), json)

    val jsonParser = JsonFormat.parser().usingTypeRegistry(typeRegistry)
    val parsed = PizzaOuterClass.PizzaDelivery.newBuilder()
      .apply { jsonParser.merge(json, this) }
      .build()
    assertThat(parsed).isEqualTo(pizzaDelivery)
    */
  }

  @Test
  @Disabled("DEC-6: the JSON golden (all_types_proto3.json) is written for the kotlin-package\n"
      + "AllTypes model, whose scalar fields are named my_int32/my_uint32/...; the java-package\n"
      + "AllTypes proto generated here uses unprefixed names (int32/uint32/...). Expected values\n"
      + "may not change (translation-conventions.md 6.4), so the golden cannot be reused.")
  public void serializeDefaultAllTypesProtoc() {
    /*
    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(DEFAULT_ALL_TYPES_JSON, jsonPrinter.print(defaultAllTypesProtoc))
    */
  }

  @Test
  public void defaultAllTypes() throws IOException {
    byte[] protocBytes = defaultAllTypesProtoc.toByteArray();
    assertArrayEquals(protocBytes, AllTypes.ADAPTER.encode(defaultAllTypesWireJava));
    assertEquals(defaultAllTypesWireJava, AllTypes.ADAPTER.decode(protocBytes));
    // TASK-15 adaptation: the AllTypesK halves are omitted (kotlin-package fixtures are DEC-6
    // exclusions); the wire-java-versus-protoc assertions cover the same bytes.
  }

  @Test
  public void explicitIdentityAllTypes() throws IOException {
    byte[] protocBytes = explicitIdentityAllTypesProtoc.toByteArray();
    assertArrayEquals(protocBytes, AllTypes.ADAPTER.encode(explicitIdentityAllTypesWireJava));
    assertEquals(explicitIdentityAllTypesWireJava, AllTypes.ADAPTER.decode(protocBytes));
    // TASK-15 adaptation: AllTypesK halves omitted (DEC-6), same bytes covered above.
  }

  @Test
  public void implicitIdentityAllTypes() throws IOException {
    AllTypesOuterClass.AllTypes protocMessage = AllTypesOuterClass.AllTypes.newBuilder().build();
    AllTypes wireMessageJava = new AllTypes.Builder().build();

    byte[] protocBytes = protocMessage.toByteArray();
    assertArrayEquals(protocBytes, AllTypes.ADAPTER.encode(wireMessageJava));
    assertEquals(wireMessageJava, AllTypes.ADAPTER.decode(protocBytes));
    // TASK-15 adaptation: AllTypesK halves omitted (DEC-6), same bytes covered above.
  }

  @Test
  public void deserializeUnknownEnumConstantName() throws IOException {
    String json = "{\"availableSizes\":[\"SMALL\",\"MEDIUM\",\"SURPRISE\",\"LARGE\"]}";

    com.google.protobuf.Message.Builder jsonBuilder =
        InteropTest.InteropRepeatedEnums.newBuilder();
    JsonFormat.parser().ignoringUnknownFields().merge(json, jsonBuilder);
    InteropTest.InteropRepeatedEnums parsed = (InteropTest.InteropRepeatedEnums)
        jsonBuilder.build();

    // Protobuf ignored the unknown constant.
    InteropTest.InteropRepeatedEnums expectedProtoc = InteropTest.InteropRepeatedEnums.newBuilder()
        .addAllAvailableSizes(
            Arrays.asList(
                InteropTest.InteropRepeatedEnums.Size.SMALL,
                InteropTest.InteropRepeatedEnums.Size.MEDIUM,
                InteropTest.InteropRepeatedEnums.Size.LARGE))
        .build();
    assertEquals(expectedProtoc, parsed);
    String printed = JsonFormat.printer().print(expectedProtoc);
    assertJsonEquals(
        "{\"availableSizes\":[\"SMALL\",\"MEDIUM\",\"LARGE\"]}",
        printed,
        InteropTest.InteropRepeatedEnums.getDefaultInstance());

    // TASK-15 adaptation: the two moshi halves (InteropRepeatedEnumsJ3 and the sealed
    // InteropRepeatedEnumsK3) are DEC-6 exclusions (moshi-adapter, Kotlin models):
    //
    // val expectedJava = InteropRepeatedEnumsJ3(
    //   listOf(
    //     InteropRepeatedEnumsJ3.Size.SMALL,
    //     InteropRepeatedEnumsJ3.Size.MEDIUM,
    //     InteropRepeatedEnumsJ3.Size.LARGE,
    //   ),
    // )
    // assertThat(moshi.adapter(InteropRepeatedEnumsJ3::class.java).fromJson(json))
    //   .isEqualTo(expectedJava)
    //
    // val expectedKotlinSealedMode = InteropRepeatedEnumsK3(
    //   available_sizes = listOf(
    //     InteropRepeatedEnumsK3.Size.SMALL,
    //     InteropRepeatedEnumsK3.Size.MEDIUM,
    //     InteropRepeatedEnumsK3.Size.LARGE,
    //   ),
    // )
    // assertThat(moshi.adapter(InteropRepeatedEnumsK3::class.java).fromJson(json))
    //   .isEqualTo(expectedKotlinSealedMode)
  }

  @Test
  public void deserializeUnknownEnumConstantValue() throws IOException {
    String json = "{\"availableSizes\":[\"SMALL\",\"MEDIUM\",6,\"LARGE\"]}";

    com.google.protobuf.Message.Builder jsonBuilder =
        InteropTest.InteropRepeatedEnums.newBuilder();
    JsonFormat.parser().ignoringUnknownFields().merge(json, jsonBuilder);
    InteropTest.InteropRepeatedEnums parsed = (InteropTest.InteropRepeatedEnums)
        jsonBuilder.build();

    // Protobuf stored the unknown constant value.
    InteropTest.InteropRepeatedEnums expectedProtoc = InteropTest.InteropRepeatedEnums.newBuilder()
        .addAllAvailableSizesValue(
            Arrays.asList(
                InteropTest.InteropRepeatedEnums.Size.SMALL_VALUE,
                InteropTest.InteropRepeatedEnums.Size.MEDIUM_VALUE,
                6,
                InteropTest.InteropRepeatedEnums.Size.LARGE_VALUE))
        .build();
    assertEquals(expectedProtoc, parsed);
    String printed = JsonFormat.printer().print(expectedProtoc);
    assertJsonEquals(json, printed, InteropTest.InteropRepeatedEnums.getDefaultInstance());

    // TASK-15 adaptation: the two moshi halves are DEC-6 exclusions (moshi-adapter; the sealed
    // Unrecognized enum shape of the Kotlin model):
    //
    // // The unknown constant is lost for regular enums.
    // val expectedJava = InteropRepeatedEnumsJ3(
    //   listOf(
    //     InteropRepeatedEnumsJ3.Size.SMALL,
    //     InteropRepeatedEnumsJ3.Size.MEDIUM,
    //     InteropRepeatedEnumsJ3.Size.LARGE,
    //   ),
    // )
    // assertThat(moshi.adapter(InteropRepeatedEnumsJ3::class.java).fromJson(json))
    //   .isEqualTo(expectedJava)
    //
    // // The unknown constant is stored in the `Unrecognized` for `EnumMode.SEALED_CLASS`.
    // val expectedKotlinSealedMode = InteropRepeatedEnumsK3(
    //   available_sizes = listOf(
    //     InteropRepeatedEnumsK3.Size.SMALL,
    //     InteropRepeatedEnumsK3.Size.MEDIUM,
    //     InteropRepeatedEnumsK3.Size.Unrecognized(6),
    //     InteropRepeatedEnumsK3.Size.LARGE,
    //   ),
    // )
    // assertThat(moshi.adapter(InteropRepeatedEnumsK3::class.java).fromJson(json))
    //   .isEqualTo(expectedKotlinSealedMode)
  }

  @Test
  @Disabled("DEC-6: the JSON golden (all_types_proto3.json) targets the kotlin-package AllTypes\n"
      + "model (my_-prefixed field names); see the serializeDefaultAllTypesProtoc note.")
  public void deserializeDefaultAllTypesProtoc() {
    /*
    val allTypes = defaultAllTypesProtoc
    val jsonParser = JsonFormat.parser()
    val parsed = AllTypesOuterClass.AllTypes.newBuilder()
      .apply { jsonParser.merge(DEFAULT_ALL_TYPES_JSON, this) }
      .build()

    assertThat(parsed).isEqualTo(allTypes)
    assertThat(parsed.toString()).isEqualTo(allTypes.toString())
    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(jsonPrinter.print(parsed), jsonPrinter.print(allTypes))
    */
  }

  @Test
  @Disabled("DEC-6: the JSON golden (all_types_identity_proto3.json) targets the kotlin-package\n"
      + "AllTypes model (my_-prefixed field names); see the serializeDefaultAllTypesProtoc note.")
  public void serializeIdentityAllTypesProtoc() {
    /*
    val identityAllTypes = AllTypesOuterClass.AllTypes.newBuilder().build()

    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(IDENTITY_ALL_TYPES_JSON, jsonPrinter.print(identityAllTypes))
    */
  }

  @Test
  @Disabled("DEC-6: the JSON golden (all_types_explicit_identity_proto3.json) targets the\n"
      + "kotlin-package AllTypes model (my_-prefixed field names); see the\n"
      + "serializeDefaultAllTypesProtoc note.")
  public void serializeExplicitIdentityAllTypesProtoc() {
    /*
    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(
      EXPLICIT_IDENTITY_ALL_TYPES_JSON,
      jsonPrinter.print(explicitIdentityAllTypesProtoc),
    )
    */
  }

  @Test
  @Disabled("DEC-6: easter fixtures live only in the kotlin package (unrecognized_constant);\n"
      + "EasterK3's Unrecognized sealed-enum shape is the Kotlin model itself and the protoc\n"
      + "reference (EasterOuterClass) is generated from the same excluded package.")
  public void encodingAndDecodingOfUnrecognizedEnumConstants_negativeValue_proto3Message() {
    /*
    // ┌─ 2: -1
    // ├─ 3: -1
    // ├─ 4: -1
    // ├─ 4: -1
    // ├─ 5: -1
    // ╰- 5: -1
    val bytes = "10ffffffffffffffffff0118ffffffffffffffffff0120ffffffffffffffffff0120ffffffffffffffffff012a14ffffffffffffffffff01ffffffffffffffffff01"
    val wireMessage: EasterK3 = EasterK3.ADAPTER.decode(bytes.decodeHex())
    val protocMessage: EasterP3 = EasterP3.parseFrom(bytes.decodeHex().toByteArray())

    assertThat(wireMessage.identity_easter_animal.value).isEqualTo(protocMessage.identityEasterAnimalValue)
    assertThat(wireMessage.optional_easter_animal!!.value).isEqualTo(protocMessage.optionalEasterAnimalValue)

    assertThat(protocMessage.optionalEasterAnimal).isEqualTo(EasterAnimalP3.UNRECOGNIZED)
    assertThat(protocMessage.optionalEasterAnimalValue).isEqualTo(-1)
    assertThat(protocMessage.identityEasterAnimal).isEqualTo(EasterAnimalP3.UNRECOGNIZED)
    assertThat(protocMessage.identityEasterAnimalValue).isEqualTo(-1)
    assertThat(protocMessage.easterAnimalsRepeatedList).isEqualTo(listOf(EasterAnimalP3.UNRECOGNIZED, EasterAnimalP3.UNRECOGNIZED))
    assertThat(protocMessage.easterAnimalsRepeatedValueList).isEqualTo(listOf(-1, -1))
    assertThat(protocMessage.easterAnimalsPackedList).isEqualTo(listOf(EasterAnimalP3.UNRECOGNIZED, EasterAnimalP3.UNRECOGNIZED))
    assertThat(protocMessage.easterAnimalsPackedValueList).isEqualTo(listOf(-1, -1))

    assertThat(wireMessage.optional_easter_animal).isEqualTo(EasterAnimalK3.Unrecognized(-1))
    assertThat(wireMessage.identity_easter_animal).isEqualTo(EasterAnimalK3.Unrecognized(-1))
    assertThat(wireMessage.easter_animals_repeated).isEqualTo(listOf(EasterAnimalK3.Unrecognized(-1), EasterAnimalK3.Unrecognized(-1)))
    assertThat(wireMessage.easter_animals_packed).isEqualTo(listOf(EasterAnimalK3.Unrecognized(-1), EasterAnimalK3.Unrecognized(-1)))
    */
  }

  @Test
  @Disabled("DEC-6: easter fixtures live only in the kotlin package (unrecognized_constant);\n"
      + "see the negativeValue case note.")
  public void encodingAndDecodingOfUnrecognizedEnumConstants_knownValue_proto3Message() {
    /*
    // ┌─ 2: 1
    // ├─ 3: 1
    // ├─ 4: 1
    // ├─ 4: 1
    // ├─ 4: 1
    // ├─ 5: 1
    // ├─ 5: 1
    // ├─ 5: 1
    // ╰- 5: 1
    val bytes = "100118012001200120012a0401010101"
    val wireMessage: EasterK3 = EasterK3.ADAPTER.decode(bytes.decodeHex())
    val protocMessage: EasterP3 = EasterP3.parseFrom(bytes.decodeHex().toByteArray())

    assertThat(wireMessage.identity_easter_animal.value).isEqualTo(protocMessage.identityEasterAnimalValue)
    assertThat(wireMessage.optional_easter_animal!!.value).isEqualTo(protocMessage.optionalEasterAnimalValue)

    assertThat(protocMessage.optionalEasterAnimal).isEqualTo(EasterAnimalP3.BUNNY)
    assertThat(protocMessage.optionalEasterAnimalValue).isEqualTo(EasterAnimalP3.BUNNY_VALUE)
    assertThat(protocMessage.identityEasterAnimal).isEqualTo(EasterAnimalP3.BUNNY)
    assertThat(protocMessage.identityEasterAnimalValue).isEqualTo(EasterAnimalP3.BUNNY_VALUE)
    assertThat(protocMessage.easterAnimalsRepeatedList).isEqualTo(listOf(EasterAnimalP3.BUNNY, EasterAnimalP3.BUNNY, EasterAnimalP3.BUNNY))
    assertThat(protocMessage.easterAnimalsRepeatedValueList).isEqualTo(listOf(EasterAnimalP3.BUNNY_VALUE, EasterAnimalP3.BUNNY_VALUE, EasterAnimalP3.BUNNY_VALUE))
    assertThat(protocMessage.easterAnimalsPackedList).isEqualTo(listOf(EasterAnimalP3.BUNNY, EasterAnimalP3.BUNNY, EasterAnimalP3.BUNNY, EasterAnimalP3.BUNNY))
    assertThat(protocMessage.easterAnimalsPackedValueList).isEqualTo(listOf(EasterAnimalP3.BUNNY_VALUE, EasterAnimalP3.BUNNY_VALUE, EasterAnimalP3.BUNNY_VALUE, EasterAnimalP3.BUNNY_VALUE))

    assertThat(wireMessage.optional_easter_animal).isEqualTo(EasterAnimalK3.BUNNY)
    assertThat(wireMessage.identity_easter_animal).isEqualTo(EasterAnimalK3.BUNNY)
    assertThat(wireMessage.easter_animals_repeated).isEqualTo(listOf(EasterAnimalK3.BUNNY, EasterAnimalK3.BUNNY, EasterAnimalK3.BUNNY))
    assertThat(wireMessage.easter_animals_packed).isEqualTo(listOf(EasterAnimalK3.BUNNY, EasterAnimalK3.BUNNY, EasterAnimalK3.BUNNY, EasterAnimalK3.BUNNY))
    */
  }

  @Test
  @Disabled("DEC-6: easter fixtures live only in the kotlin package (unrecognized_constant);\n"
      + "see the negativeValue case note.")
  public void encodingAndDecodingOfUnrecognizedEnumConstants_unknownValue_proto3Message() {
    /*
    // ┌─ 2: 5
    // ├─ 3: 6
    // ├─ 4: 7
    // ├─ 4: 2
    // ├─ 4: 6
    // ├─ 5: 8
    // ├─ 5: 2
    // ├─ 5: 9
    // ╰- 5: 1
    val bytes = "100518062007200220062a0408020901"
    val wireMessage: EasterK3 = EasterK3.ADAPTER.decode(bytes.decodeHex())
    val protocMessage: EasterP3 = EasterP3.parseFrom(bytes.decodeHex().toByteArray())

    assertThat(protocMessage.optionalEasterAnimal).isEqualTo(EasterAnimalP3.UNRECOGNIZED)
    assertThat(protocMessage.optionalEasterAnimalValue).isEqualTo(5)
    assertThat(protocMessage.identityEasterAnimal).isEqualTo(EasterAnimalP3.UNRECOGNIZED)
    assertThat(protocMessage.identityEasterAnimalValue).isEqualTo(6)
    assertThat(protocMessage.easterAnimalsRepeatedList).isEqualTo(listOf(EasterAnimalP3.UNRECOGNIZED, EasterAnimalP3.HEN, EasterAnimalP3.UNRECOGNIZED))
    assertThat(protocMessage.easterAnimalsRepeatedValueList).isEqualTo(listOf(7, 2, 6))
    assertThat(protocMessage.easterAnimalsPackedList).isEqualTo(listOf(EasterAnimalP3.UNRECOGNIZED, EasterAnimalP3.HEN, EasterAnimalP3.UNRECOGNIZED, EasterAnimalP3.BUNNY))
    assertThat(protocMessage.easterAnimalsPackedValueList).isEqualTo(listOf(8, 2, 9, 1))

    assertThat(wireMessage.optional_easter_animal).isEqualTo(EasterAnimalK3.Unrecognized(5))
    assertThat(wireMessage.identity_easter_animal).isEqualTo(EasterAnimalK3.Unrecognized(6))
    assertThat(wireMessage.easter_animals_repeated).isEqualTo(listOf(EasterAnimalK3.Unrecognized(7), EasterAnimalK3.HEN, EasterAnimalK3.Unrecognized(6)))
    assertThat(wireMessage.easter_animals_packed).isEqualTo(listOf(EasterAnimalK3.Unrecognized(8), EasterAnimalK3.HEN, EasterAnimalK3.Unrecognized(9), EasterAnimalK3.BUNNY))
    */
  }

  @Test
  @Disabled("DEC-6: the JSON golden (all_types_identity_proto3.json) targets the kotlin-package\n"
      + "AllTypes model (my_-prefixed field names); see the serializeDefaultAllTypesProtoc note.")
  public void deserializeIdentityAllTypesProtoc() {
    /*
    val identityAllTypes = AllTypesOuterClass.AllTypes.newBuilder().build()
    val jsonParser = JsonFormat.parser()
    val parsed = AllTypesOuterClass.AllTypes.newBuilder()
      .apply { jsonParser.merge(IDENTITY_ALL_TYPES_JSON, this) }
      .build()

    assertThat(parsed).isEqualTo(identityAllTypes)
    assertThat(parsed.toString()).isEqualTo(identityAllTypes.toString())
    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(jsonPrinter.print(parsed), jsonPrinter.print(identityAllTypes))
    */
  }

  @Test
  @Disabled("DEC-6: the JSON golden (all_types_explicit_identity_proto3.json) targets the\n"
      + "kotlin-package AllTypes model (my_-prefixed field names); see the\n"
      + "serializeDefaultAllTypesProtoc note.")
  public void deserializeExplicitIdentityAllTypesProtoc() {
    /*
    val jsonParser = JsonFormat.parser()
    val parsed = AllTypesOuterClass.AllTypes.newBuilder()
      .apply { jsonParser.merge(EXPLICIT_IDENTITY_ALL_TYPES_JSON, this) }
      .build()

    assertThat(parsed).isEqualTo(explicitIdentityAllTypesProtoc)
    assertThat(parsed.toString()).isEqualTo(explicitIdentityAllTypesProtoc.toString())
    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(jsonPrinter.print(parsed), jsonPrinter.print(explicitIdentityAllTypesProtoc))
    */
  }

  // TASK-15 adaptation: upstream backticked name `protoc validation camel case json`;
  // Java identifiers cannot contain spaces.
  @Test
  @Disabled("DEC-6: CamelCaseOuterClass is a kotlin-package fixture (camel_case.proto), not\n"
      + "generated in this module.")
  public void protocValidationCamelCaseJson() {
    /*
    val protocCamel = CamelCaseOuterClass.CamelCase.newBuilder()
      .setNestedMessage(CamelCaseOuterClass.CamelCase.NestedCamelCase.newBuilder().setOneInt32(1))
      .addAllRepInt32(listOf(1, 2))
      .setIDitItMyWAy("frank")
      .putMapInt32Int32(1, 2)
    assertJsonEquals(CAMEL_CASE_JSON, JsonFormat.printer().print(protocCamel))
    */
  }

  @Test
  @Disabled("DEC-6: All64OuterClass is a kotlin-package fixture (all64.proto), not generated in\n"
      + "this module.")
  public void all64JsonProtocMaxValue() {
    /*
    val all64 = All64OuterClass.All64.newBuilder()
      .setMyInt64(Long.MAX_VALUE)
      .setMyUint64(Long.MAX_VALUE)
      .setMySint64(Long.MAX_VALUE)
      .setMyFixed64(Long.MAX_VALUE)
      .setMySfixed64(Long.MAX_VALUE)
      .addAllRepInt64(list(-1L))
      .addAllRepUint64(list(-1L))
      .addAllRepSint64(list(-1L))
      .addAllRepFixed64(list(-1L))
      .addAllRepSfixed64(list(-1L))
      .addAllPackInt64(list(Long.MAX_VALUE))
      .addAllPackUint64(list(Long.MAX_VALUE))
      .addAllPackSint64(list(Long.MAX_VALUE))
      .addAllPackFixed64(list(Long.MAX_VALUE))
      .addAllPackSfixed64(list(Long.MAX_VALUE))
      .setOneofInt64(Long.MAX_VALUE)
      .putMapInt64Int64(Long.MAX_VALUE, Long.MAX_VALUE)
      .putMapInt64Uint64(Long.MAX_VALUE, -1L)
      .putMapInt64Sint64(Long.MAX_VALUE, Long.MAX_VALUE)
      .putMapInt64Fixed64(Long.MAX_VALUE, -1L)
      .putMapInt64Sfixed64(Long.MAX_VALUE, Long.MAX_VALUE)
      .build()

    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(jsonPrinter.print(all64), ALL_64_JSON_MAX_VALUE)

    val jsonParser = JsonFormat.parser()
    val parsed = All64OuterClass.All64.newBuilder()
      .apply { jsonParser.merge(ALL_64_JSON_MAX_VALUE, this) }
      .build()
    assertThat(parsed).isEqualTo(all64)
    */
  }

  @Test
  @Disabled("DEC-6: All64OuterClass is a kotlin-package fixture (all64.proto), not generated in\n"
      + "this module.")
  public void all64JsonProtocMinValue() {
    /*
    val all64 = All64OuterClass.All64.newBuilder()
      .setMyInt64(Long.MIN_VALUE)
      .setMyUint64(Long.MIN_VALUE)
      .setMySint64(Long.MIN_VALUE)
      .setMyFixed64(Long.MIN_VALUE)
      .setMySfixed64(Long.MIN_VALUE)
      .addAllRepInt64(list(0L))
      .addAllRepUint64(list(0L))
      .addAllRepSint64(list(0L))
      .addAllRepFixed64(list(0L))
      .addAllRepSfixed64(list(0L))
      .addAllPackInt64(list(Long.MIN_VALUE))
      .addAllPackUint64(list(Long.MIN_VALUE))
      .addAllPackSint64(list(Long.MIN_VALUE))
      .addAllPackFixed64(list(Long.MIN_VALUE))
      .addAllPackSfixed64(list(Long.MIN_VALUE))
      .setOneofInt64(Long.MIN_VALUE)
      .putMapInt64Int64(Long.MIN_VALUE, Long.MIN_VALUE)
      .putMapInt64Uint64(Long.MIN_VALUE, 0L)
      .putMapInt64Sint64(Long.MIN_VALUE, Long.MIN_VALUE)
      .putMapInt64Fixed64(Long.MIN_VALUE, 0L)
      .putMapInt64Sfixed64(Long.MIN_VALUE, Long.MIN_VALUE)
      .build()

    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(jsonPrinter.print(all64), ALL_64_JSON_MIN_VALUE)

    val jsonParser = JsonFormat.parser()
    val parsed = All64OuterClass.All64.newBuilder()
      .apply { jsonParser.merge(ALL_64_JSON_MIN_VALUE, this) }
      .build()
    assertThat(parsed).isEqualTo(all64)
    */
  }

  // TASK-15 adaptation: ported onto the java-package InteropDuration fixture. Upstream used
  // PizzaDeliveryK/PizzaOuterClass (kotlin packages, DEC-6 exclusions); the Duration payload
  // values (1799s/500ms) and both assertions are unchanged, so the wire-versus-protoc Duration
  // bytes are still compared exactly.
  @Test
  public void durationProto() throws IOException {
    InteropTest.InteropDuration googleMessage = InteropTest.InteropDuration.newBuilder()
        .setValue(
            com.google.protobuf.Duration.newBuilder()
                .setSeconds(1_799L)
                .setNanos(500_000_000)
                .build())
        .build();

    InteropDuration wireMessage = new InteropDuration(Duration.ofSeconds(1_799L, 500_000_000L));

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, wireMessage.encode());
    assertEquals(wireMessage, InteropDuration.ADAPTER.decode(googleMessageBytes));
  }

  @Test
  @Disabled("DEC-6: the Timestamp well-known interop has no java-package fixture; upstream's\n"
      + "PizzaDeliveryK/PizzaOuterClass (kotlin packages) are not generated in this module.")
  public void instantProto() {
    /*
    val googleMessage = PizzaOuterClass.PizzaDelivery.newBuilder()
      .setOrderedAt(
        Timestamp.newBuilder()
          .setSeconds(-631152000L) // 1950-01-01T00:00:00.250Z.
          .setNanos(250_000_000)
          .build(),
      )
      .build()

    val wireMessage = PizzaDeliveryK(
      ordered_at = ofEpochSecond(-631152000L, 250_000_000L),
    )

    val googleMessageBytes = googleMessage.toByteArray()
    assertThat(wireMessage.encode()).isEqualTo(googleMessageBytes)
    assertThat(PizzaDeliveryK.ADAPTER.decode(googleMessageBytes)).isEqualTo(wireMessage)
    */
  }

  // TASK-15 adaptation: ported onto the java-package AllStructs fixture. Upstream used
  // PizzaDeliveryK/PizzaOuterClass (kotlin packages, DEC-6 exclusions); the Struct payload
  // (stamps: 5.0, members: ["Benoît", "Jesse"]) and both assertions are unchanged.
  @Test
  public void structProto() throws IOException {
    AllStructsOuterClass.AllStructs googleMessage = AllStructsOuterClass.AllStructs.newBuilder()
        .setStruct(
            Struct.newBuilder()
                .putFields("stamps", Value.newBuilder().setNumberValue(5.0).build())
                .putFields(
                    "members",
                    Value.newBuilder()
                        .setListValue(
                            com.google.protobuf.ListValue.newBuilder()
                                .addValues(Value.newBuilder().setStringValue("Benoît").build())
                                .addValues(Value.newBuilder().setStringValue("Jesse").build())
                                .build())
                        .build())
                .build())
        .build();

    Map<String, Object> loyalty = new java.util.LinkedHashMap<>();
    loyalty.put("stamps", 5.0);
    loyalty.put("members", Arrays.asList("Benoît", "Jesse"));
    AllStructs wireMessage = new AllStructs.Builder()
        .struct(loyalty)
        .build();

    byte[] googleMessageBytes = googleMessage.toByteArray();
    assertArrayEquals(googleMessageBytes, wireMessage.encode());
    assertEquals(wireMessage, AllStructs.ADAPTER.decode(googleMessageBytes));
  }

  @Test
  public void wrappersProtoc() throws IOException {
    byte[] protocBytes = defaultAllWrappersProtoc.toByteArray();
    assertArrayEquals(protocBytes, AllWrappers.ADAPTER.encode(defaultAllWrappersWireJava));
    assertEquals(defaultAllWrappersWireJava, AllWrappers.ADAPTER.decode(protocBytes));
    // TASK-15 adaptation: the AllWrappersK halves are omitted (DEC-6), same bytes covered above.
  }

  @Test
  public void interopTests() throws IOException {
    byte[] byteArrayWireJ = InteropMessage.ADAPTER.encode(interopWireJ);
    byte[] byteArrayProtoc = interopProtoc.toByteArray();

    // TASK-15 adaptation: byteArrayWireK (InteropMessageK, DEC-6) is omitted; upstream asserted
    // it equals byteArrayWireJ, so the protoc round trip below reuses byteArrayWireJ directly.
    assertArrayEquals(byteArrayProtoc, InteropMessage.ADAPTER.encode(interopWireJ));
    assertEquals(interopWireJ, InteropMessage.ADAPTER.decode(byteArrayProtoc));
    assertEquals(interopProtoc,
        InteropMessageOuterClass.InteropMessage.parseFrom(byteArrayWireJ));
  }

  @Test
  public void wrappersProtocJson() throws IOException {
    String printed = JsonFormat.printer().print(defaultAllWrappersProtoc);
    assertJsonEquals(ALL_WRAPPERS_JSON, printed,
        AllWrappersOuterClass.AllWrappers.getDefaultInstance());

    com.google.protobuf.Message.Builder parsedBuilder =
        AllWrappersOuterClass.AllWrappers.newBuilder();
    JsonFormat.parser().merge(ALL_WRAPPERS_JSON, parsedBuilder);
    AllWrappersOuterClass.AllWrappers parsed =
        (AllWrappersOuterClass.AllWrappers) parsedBuilder.build();
    assertEquals(defaultAllWrappersProtoc, parsed);
  }

  @Test
  public void minusDoubleZero() throws IOException {
    AllTypesOuterClass.AllTypes protoc = AllTypesOuterClass.AllTypes.newBuilder()
        .setDouble(-0.0)
        .build();
    AllTypes wireJava = new AllTypes.Builder().double_(-0.0).build();
    // TASK-15 adaptation: the AllTypesK half is omitted (DEC-6), same bytes covered below; the
    // protoc JSON sub-assertion is preserved verbatim in comment because its expected literal
    // names the kotlin-package field myDouble while the java-package model names the field
    // double, and expected values may not change (translation-conventions.md 6.4):
    //   val protocJson = JsonFormat.printer().print(protoc)
    //   assertJsonEquals("{\"myDouble\": -0.0}", protocJson)

    byte[] protocByteArray = protoc.toByteArray();
    assertArrayEquals(protocByteArray, AllTypes.ADAPTER.encode(wireJava));
    assertEquals(wireJava, AllTypes.ADAPTER.decode(protocByteArray));
  }

  @Test
  public void minusFloatZero() throws IOException {
    AllTypesOuterClass.AllTypes protoc = AllTypesOuterClass.AllTypes.newBuilder()
        .setFloat(-0f)
        .build();
    AllTypes wireJava = new AllTypes.Builder().float_(-0f).build();
    // TASK-15 adaptation: the AllTypesK half is omitted (DEC-6), same bytes covered above; the
    // protoc JSON sub-assertion is preserved verbatim in comment (expected literal names the
    // kotlin-package field myFloat; the java-package model names the field float):
    //   val protocJson = JsonFormat.printer().print(protoc)
    //   assertJsonEquals("{\"myFloat\": -0.0}", protocJson)

    byte[] protocByteArray = protoc.toByteArray();
    assertArrayEquals(protocByteArray, AllTypes.ADAPTER.encode(wireJava));
    assertEquals(wireJava, AllTypes.ADAPTER.decode(protocByteArray));
  }

  @Test
  public void cannotPassNullToIdentityString() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new AllTypes.Builder().string(null).build());
    assertEquals("builder.string == null", exception.getMessage());
  }

  @Test
  public void cannotPassNullToIdentityBytes() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new AllTypes.Builder().bytes(null).build());
    assertEquals("builder.bytes == null", exception.getMessage());
  }

  @Test
  public void cannotPassNullToIdentityEnum() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new AllTypes.Builder().nested_enum(null).build());
    assertEquals("builder.nested_enum == null", exception.getMessage());
  }

  @Test
  @Disabled("DEC-6: both fixtures are kotlin-package (WireMessageOuterClass from\n"
      + "squareup.proto3.kotlin.extensions, WireMessage from squareup.proto3.wire.extensions),\n"
      + "not generated in this module.")
  public void protocDontThrowUpOnWireExtensions() {
    /*
    assertThat(WireMessageOuterClass.WireMessage.newBuilder().build()).isNotNull()
    assertThat(WireMessage()).isNotNull()
    */
  }

  @Test
  @Disabled("DEC-6: MapTypesOuterClass is a kotlin-package fixture (map_types.proto), not\n"
      + "generated in this module.")
  public void validateMapTypesJson() {
    /*
    val value = MapTypesOuterClass.MapTypes.newBuilder()
      .putAllMapStringString(mapOf("a" to "A", "b" to "B"))
      .putAllMapInt32Int32(
        mapOf(
          Int.MIN_VALUE to Int.MIN_VALUE + 1,
          Int.MAX_VALUE to Int.MAX_VALUE - 1,
        ),
      )
      .putAllMapSint32Sint32(
        mapOf(
          Int.MIN_VALUE to Int.MIN_VALUE + 1,
          Int.MAX_VALUE to Int.MAX_VALUE - 1,
        ),
      )
      .putAllMapSfixed32Sfixed32(
        mapOf(
          Int.MIN_VALUE to Int.MIN_VALUE + 1,
          Int.MAX_VALUE to Int.MAX_VALUE - 1,
        ),
      )
      .putAllMapFixed32Fixed32(
        mapOf(
          Int.MIN_VALUE to Int.MIN_VALUE + 1,
          Int.MAX_VALUE to Int.MAX_VALUE - 1,
        ),
      )
      .putAllMapUint32Uint32(
        mapOf(
          Int.MIN_VALUE to Int.MIN_VALUE + 1,
          Int.MAX_VALUE to Int.MAX_VALUE - 1,
        ),
      )
      .putAllMapInt64Int64(
        mapOf(
          Long.MIN_VALUE to Long.MIN_VALUE + 1L,
          Long.MAX_VALUE to Long.MAX_VALUE - 1L,
        ),
      )
      .putAllMapSfixed64Sfixed64(
        mapOf(
          Long.MIN_VALUE to Long.MIN_VALUE + 1L,
          Long.MAX_VALUE to Long.MAX_VALUE - 1L,
        ),
      )
      .putAllMapSint64Sint64(
        mapOf(
          Long.MIN_VALUE to Long.MIN_VALUE + 1L,
          Long.MAX_VALUE to Long.MAX_VALUE - 1L,
        ),
      )
      .putAllMapFixed64Fixed64(
        mapOf(
          Long.MIN_VALUE to Long.MIN_VALUE + 1L,
          Long.MAX_VALUE to Long.MAX_VALUE - 1L,
        ),
      )
      .putAllMapUint64Uint64(
        mapOf(
          Long.MIN_VALUE to Long.MIN_VALUE + 1L,
          Long.MAX_VALUE to Long.MAX_VALUE - 1L,
        ),
      )
      .build()

    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(jsonPrinter.print(value), MAP_TYPES_JSON)

    val jsonParser = JsonFormat.parser()
    val parsed = MapTypesOuterClass.MapTypes.newBuilder()
      .apply { jsonParser.merge(MAP_TYPES_JSON, this) }
      .build()
    assertThat(parsed).isEqualTo(value)
    */
  }

  @Test
  @Disabled("DEC-6: All32OuterClass is a kotlin-package fixture (all32.proto), not generated in\n"
      + "this module.")
  public void validateAll32MinJson() {
    /*
    val value = All32OuterClass.All32.newBuilder()
      .setMyInt32(Int.MIN_VALUE)
      .setMyUint32(Int.MIN_VALUE)
      .setMySint32(Int.MIN_VALUE)
      .setMyFixed32(Int.MIN_VALUE)
      .setMySfixed32(Int.MIN_VALUE)
      .addAllRepInt32(list(0))
      .addAllRepUint32(list(0))
      .addAllRepSint32(list(0))
      .addAllRepFixed32(list(0))
      .addAllRepSfixed32(list(0))
      .addAllPackInt32(list(Int.MIN_VALUE))
      .addAllPackUint32(list(Int.MIN_VALUE))
      .addAllPackSint32(list(Int.MIN_VALUE))
      .addAllPackFixed32(list(Int.MIN_VALUE))
      .addAllPackSfixed32(list(Int.MIN_VALUE))
      .setOneofInt32(Int.MIN_VALUE)
      .putMapInt32Int32(Int.MIN_VALUE, Int.MIN_VALUE + 1)
      .putMapInt32Uint32(Int.MIN_VALUE, 0)
      .putMapInt32Sint32(Int.MIN_VALUE, Int.MIN_VALUE + 1)
      .putMapInt32Fixed32(Int.MIN_VALUE, 0)
      .putMapInt32Sfixed32(Int.MIN_VALUE, Int.MIN_VALUE + 1)
      .build()

    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(jsonPrinter.print(value), ALL_32_JSON_MIN_VALUE)

    val jsonParser = JsonFormat.parser()
    val parsed = All32OuterClass.All32.newBuilder()
      .apply { jsonParser.merge(ALL_32_JSON_MIN_VALUE, this) }
      .build()
    assertThat(parsed).isEqualTo(value)
    */
  }

  @Test
  @Disabled("DEC-6: MapTypesOuterClass and MapTypesK are kotlin-package fixtures\n"
      + "(map_types.proto), not generated in this module.")
  public void mapKeysAndValuesDefaultsToTheirRespectiveIdentityValue() {
    /*
    // Bytes for the message `MapType` message with 2 entries on the field `map_string_string`. The
    // first one has a key but not value, the second one has a value without key. Those are manually
    // generated because Protoc and Wire don't write maps this way but can decode them though.
    val bytes = listOf(
      0x0a, // MapType.map_string_string tag -> 1|010 -> 10 -> 0x0a
      0x04, // length
      0x0a, // map key tag 1 -> 1|010 -> 10 -> 0x0a
      0x02, // length
      0x64, 0x65, // de
      0x0a, // MapType.map_string_string tag -> 1|010 -> 10 -> 0x0a
      0x04, // length
      0x12, // map value tag 2 -> 10|010 -> 18 -> 0x12
      0x02, // length
      0x65, 0x64, // ed
    ).map { it.toByte() }.toByteArray()

    val mapTypeProtoc = MapTypesOuterClass.MapTypes.parseFrom(bytes)

    assertThat(mapTypeProtoc.mapStringStringCount).isEqualTo(2)
    assertThat(mapTypeProtoc.mapStringStringMap["de"]).isEqualTo("")
    assertThat(mapTypeProtoc.mapStringStringMap[""]).isEqualTo("ed")

    val mapTypeWire = MapTypesK.ADAPTER.decode(bytes)

    assertThat(mapTypeWire.map_string_string.size).isEqualTo(2)
    assertThat(mapTypeWire.map_string_string["de"]).isEqualTo("")
    assertThat(mapTypeWire.map_string_string[""]).isEqualTo("ed")
    */
  }

  @Test
  @Disabled("DEC-6: All32OuterClass is a kotlin-package fixture (all32.proto), not generated in\n"
      + "this module.")
  public void validateAll32MaxJson() {
    /*
    val value = All32OuterClass.All32.newBuilder()
      .setMyInt32(Int.MAX_VALUE)
      .setMyUint32(Int.MAX_VALUE)
      .setMySint32(Int.MAX_VALUE)
      .setMyFixed32(Int.MAX_VALUE)
      .setMySfixed32(Int.MAX_VALUE)
      .addAllRepInt32(list(-1))
      .addAllRepUint32(list(-1))
      .addAllRepSint32(list(-1))
      .addAllRepFixed32(list(-1))
      .addAllRepSfixed32(list(-1))
      .addAllPackInt32(list(Int.MAX_VALUE))
      .addAllPackUint32(list(Int.MAX_VALUE))
      .addAllPackSint32(list(Int.MAX_VALUE))
      .addAllPackFixed32(list(Int.MAX_VALUE))
      .addAllPackSfixed32(list(Int.MAX_VALUE))
      .setOneofInt32(Int.MAX_VALUE)
      .putMapInt32Int32(Int.MAX_VALUE, Int.MAX_VALUE - 1)
      .putMapInt32Uint32(Int.MAX_VALUE, -1)
      .putMapInt32Sint32(Int.MAX_VALUE, Int.MAX_VALUE - 1)
      .putMapInt32Fixed32(Int.MAX_VALUE, -1)
      .putMapInt32Sfixed32(Int.MAX_VALUE, Int.MAX_VALUE - 1)
      .build()

    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(jsonPrinter.print(value), ALL_32_JSON_MAX_VALUE)

    val jsonParser = JsonFormat.parser()
    val parsed = All32OuterClass.All32.newBuilder()
      .apply { jsonParser.merge(ALL_32_JSON_MAX_VALUE, this) }
      .build()
    assertThat(parsed).isEqualTo(value)
    */
  }

  private static final AllTypesOuterClass.AllTypes defaultAllTypesProtoc =
      AllTypesOuterClass.AllTypes.newBuilder()
          .setInt32(111)
          .setUint32(112)
          .setSint32(113)
          .setFixed32(114)
          .setSfixed32(115)
          .setInt64(116L)
          .setUint64(117L)
          .setSint64(118L)
          .setFixed64(119L)
          .setSfixed64(120L)
          .setBool(true)
          .setFloat(122.0F)
          .setDouble(123.0)
          .setString("124")
          .setBytes(com.google.protobuf.ByteString.copyFrom(new byte[] {123, 125}))
          .setNestedEnum(AllTypesOuterClass.AllTypes.NestedEnum.A)
          .setNestedMessage(
              AllTypesOuterClass.AllTypes.NestedMessage.newBuilder().setA(999).build())
          .setOptInt32(111)
          .setOptUint32(112)
          .setOptSint32(113)
          .setOptFixed32(114)
          .setOptSfixed32(115)
          .setOptInt64(116L)
          .setOptUint64(117L)
          .setOptSint64(118L)
          .setOptFixed64(119L)
          .setOptSfixed64(120L)
          .setOptBool(true)
          .setOptFloat(122.0F)
          .setOptDouble(123.0)
          .setOptString("124")
          .setOptBytes(com.google.protobuf.ByteString.copyFrom(new byte[] {123, 125}))
          .addAllRepInt32(list(111))
          .addAllRepUint32(list(112))
          .addAllRepSint32(list(113))
          .addAllRepFixed32(list(114))
          .addAllRepSfixed32(list(115))
          .addAllRepInt64(list(116L))
          .addAllRepUint64(list(117L))
          .addAllRepSint64(list(118L))
          .addAllRepFixed64(list(119L))
          .addAllRepSfixed64(list(120L))
          .addAllRepBool(list(true))
          .addAllRepFloat(list(122.0F))
          .addAllRepDouble(list(123.0))
          .addAllRepString(list("124"))
          .addAllRepBytes(
              list(com.google.protobuf.ByteString.copyFrom(new byte[] {123, 125})))
          .addAllRepNestedEnum(list(AllTypesOuterClass.AllTypes.NestedEnum.A))
          .addAllRepNestedMessage(
              list(AllTypesOuterClass.AllTypes.NestedMessage.newBuilder().setA(999).build()))
          .addAllPackInt32(list(111))
          .addAllPackUint32(list(112))
          .addAllPackSint32(list(113))
          .addAllPackFixed32(list(114))
          .addAllPackSfixed32(list(115))
          .addAllPackInt64(list(116L))
          .addAllPackUint64(list(117L))
          .addAllPackSint64(list(118L))
          .addAllPackFixed64(list(119L))
          .addAllPackSfixed64(list(120L))
          .addAllPackBool(list(true))
          .addAllPackFloat(list(122.0F))
          .addAllPackDouble(list(123.0))
          .addAllPackNestedEnum(list(AllTypesOuterClass.AllTypes.NestedEnum.A))
          .putMapInt32Int32(1, 2)
          .putMapStringString("key", "value")
          .putMapStringMessage(
              "message",
              AllTypesOuterClass.AllTypes.NestedMessage.newBuilder().setA(1).build())
          .putMapStringEnum("enum", AllTypesOuterClass.AllTypes.NestedEnum.A)
          .setOneofInt32(0)
          .build();

  private static final AllTypes defaultAllTypesWireJava = new AllTypes.Builder()
      .int32(111)
      .uint32(112)
      .sint32(113)
      .fixed32(114)
      .sfixed32(115)
      .int64(116L)
      .uint64(117L)
      .sint64(118L)
      .fixed64(119L)
      .sfixed64(120L)
      .bool(true)
      .float_(122.0F)
      .double_(123.0)
      .string("124")
      .bytes(ByteString.of(new byte[] {123, 125}))
      .nested_enum(AllTypes.NestedEnum.A)
      .nested_message(new AllTypes.NestedMessage(999))
      .opt_int32(111)
      .opt_uint32(112)
      .opt_sint32(113)
      .opt_fixed32(114)
      .opt_sfixed32(115)
      .opt_int64(116L)
      .opt_uint64(117L)
      .opt_sint64(118L)
      .opt_fixed64(119L)
      .opt_sfixed64(120L)
      .opt_bool(true)
      .opt_float(122.0F)
      .opt_double(123.0)
      .opt_string("124")
      .opt_bytes(ByteString.of(new byte[] {123, 125}))
      .rep_int32(list(111))
      .rep_uint32(list(112))
      .rep_sint32(list(113))
      .rep_fixed32(list(114))
      .rep_sfixed32(list(115))
      .rep_int64(list(116L))
      .rep_uint64(list(117L))
      .rep_sint64(list(118L))
      .rep_fixed64(list(119L))
      .rep_sfixed64(list(120L))
      .rep_bool(list(true))
      .rep_float(list(122.0F))
      .rep_double(list(123.0))
      .rep_string(list("124"))
      .rep_bytes(list(ByteString.of(new byte[] {123, 125})))
      .rep_nested_enum(list(AllTypes.NestedEnum.A))
      .rep_nested_message(list(new AllTypes.NestedMessage(999)))
      .pack_int32(list(111))
      .pack_uint32(list(112))
      .pack_sint32(list(113))
      .pack_fixed32(list(114))
      .pack_sfixed32(list(115))
      .pack_int64(list(116L))
      .pack_uint64(list(117L))
      .pack_sint64(list(118L))
      .pack_fixed64(list(119L))
      .pack_sfixed64(list(120L))
      .pack_bool(list(true))
      .pack_float(list(122.0F))
      .pack_double(list(123.0))
      .pack_nested_enum(list(AllTypes.NestedEnum.A))
      .map_int32_int32(Collections.singletonMap(1, 2))
      .map_string_string(Collections.singletonMap("key", "value"))
      .map_string_message(Collections.singletonMap("message", new AllTypes.NestedMessage(1)))
      .map_string_enum(Collections.singletonMap("enum", AllTypes.NestedEnum.A))
      .oneof_int32(0)
      .build();

  // TASK-15 adaptation: upstream's toDoubleValue()/toBoolValue()/... receiver extensions are
  // this module's ProtocWrappersHelper (translated alongside the proto2 suite).
  private static final AllWrappersOuterClass.AllWrappers defaultAllWrappersProtoc =
      AllWrappersOuterClass.AllWrappers.newBuilder()
          .setDoubleValue(ProtocWrappersHelper.toDoubleValue(33.0))
          .setFloatValue(ProtocWrappersHelper.toFloatValue(806f))
          .setInt64Value(ProtocWrappersHelper.toInt64Value(Long.MIN_VALUE))
          .setUint64Value(ProtocWrappersHelper.toUInt64Value(Long.MIN_VALUE))
          .setInt32Value(ProtocWrappersHelper.toInt32Value(Integer.MIN_VALUE))
          .setUint32Value(ProtocWrappersHelper.toUInt32Value(Integer.MIN_VALUE))
          .setBoolValue(ProtocWrappersHelper.toBoolValue(true))
          .setStringValue(ProtocWrappersHelper.toStringValue("Bo knows wrappers"))
          .setBytesValue(
              ProtocWrappersHelper.toBytesValue(ByteString.of(new byte[] {123, 125})))
          .addAllRepDoubleValue(list(ProtocWrappersHelper.toDoubleValue(-33.0)))
          .addAllRepFloatValue(list(ProtocWrappersHelper.toFloatValue(-806f)))
          .addAllRepInt64Value(list(ProtocWrappersHelper.toInt64Value(Long.MAX_VALUE)))
          .addAllRepUint64Value(list(ProtocWrappersHelper.toUInt64Value(-1L)))
          .addAllRepInt32Value(list(ProtocWrappersHelper.toInt32Value(Integer.MAX_VALUE)))
          .addAllRepUint32Value(list(ProtocWrappersHelper.toUInt32Value(-1)))
          .addAllRepBoolValue(list(ProtocWrappersHelper.toBoolValue(true)))
          .addAllRepStringValue(list(ProtocWrappersHelper.toStringValue("Bo knows wrappers")))
          .addAllRepBytesValue(
              list(ProtocWrappersHelper.toBytesValue(ByteString.of(new byte[] {123, 125}))))
          .putAllMapInt32DoubleValue(
              Collections.singletonMap(23, ProtocWrappersHelper.toDoubleValue(33.0)))
          .putAllMapInt32FloatValue(
              Collections.singletonMap(23, ProtocWrappersHelper.toFloatValue(806f)))
          .putAllMapInt32Int64Value(
              Collections.singletonMap(23, ProtocWrappersHelper.toInt64Value(Long.MIN_VALUE)))
          .putAllMapInt32Uint64Value(
              Collections.singletonMap(23, ProtocWrappersHelper.toUInt64Value(-1L)))
          .putAllMapInt32Int32Value(
              Collections.singletonMap(23, ProtocWrappersHelper.toInt32Value(Integer.MIN_VALUE)))
          .putAllMapInt32Uint32Value(
              Collections.singletonMap(23, ProtocWrappersHelper.toUInt32Value(-1)))
          .putAllMapInt32BoolValue(
              Collections.singletonMap(23, ProtocWrappersHelper.toBoolValue(true)))
          .putAllMapInt32StringValue(
              Collections.singletonMap(23,
                  ProtocWrappersHelper.toStringValue("Bo knows wrappers")))
          .putAllMapInt32BytesValue(
              Collections.singletonMap(
                  23,
                  ProtocWrappersHelper.toBytesValue(ByteString.of(new byte[] {123, 125}))))
          .build();

  private static final AllWrappers defaultAllWrappersWireJava = new AllWrappers.Builder()
      .double_value(33.0)
      .float_value(806f)
      .int64_value(Long.MIN_VALUE)
      .uint64_value(Long.MIN_VALUE)
      .int32_value(Integer.MIN_VALUE)
      .uint32_value(Integer.MIN_VALUE)
      .bool_value(true)
      .string_value("Bo knows wrappers")
      .bytes_value(ByteString.of(new byte[] {123, 125}))
      .rep_double_value(list(-33.0))
      .rep_float_value(list(-806f))
      .rep_int64_value(list(Long.MAX_VALUE))
      .rep_uint64_value(list(-1L))
      .rep_int32_value(list(Integer.MAX_VALUE))
      .rep_uint32_value(list(-1))
      .rep_bool_value(list(true))
      .rep_string_value(list("Bo knows wrappers"))
      .rep_bytes_value(list(ByteString.of(new byte[] {123, 125})))
      .map_int32_double_value(Collections.singletonMap(23, 33.0))
      .map_int32_float_value(Collections.singletonMap(23, 806f))
      .map_int32_int64_value(Collections.singletonMap(23, Long.MIN_VALUE))
      .map_int32_uint64_value(Collections.singletonMap(23, -1L))
      .map_int32_int32_value(Collections.singletonMap(23, Integer.MIN_VALUE))
      .map_int32_uint32_value(Collections.singletonMap(23, -1))
      .map_int32_bool_value(Collections.singletonMap(23, true))
      .map_int32_string_value(Collections.singletonMap(23, "Bo knows wrappers"))
      .map_int32_bytes_value(Collections.singletonMap(23, ByteString.of(new byte[] {123, 125})))
      .build();

  private static final String ALL_WRAPPERS_JSON = loadJson("all_wrappers_proto3.json");

  private static final AllTypes explicitIdentityAllTypesWireJava = new AllTypes.Builder()
      .int32(0)
      .uint32(0)
      .sint32(0)
      .fixed32(0)
      .sfixed32(0)
      .int64(0L)
      .uint64(0L)
      .sint64(0L)
      .fixed64(0L)
      .sfixed64(0L)
      .bool(false)
      .float_(0F)
      .double_(0.0)
      .string("")
      .bytes(ByteString.EMPTY)
      .nested_enum(AllTypes.NestedEnum.UNKNOWN)
      .nested_message(new AllTypes.NestedMessage(0))
      .rep_int32(list(0))
      .rep_uint32(list(0))
      .rep_sint32(list(0))
      .rep_fixed32(list(0))
      .rep_sfixed32(Collections.emptyList())
      .rep_int64(Collections.emptyList())
      .rep_uint64(Collections.emptyList())
      .rep_sint64(Collections.emptyList())
      .rep_fixed64(Collections.emptyList())
      .rep_sfixed64(Collections.emptyList())
      .rep_bool(Collections.emptyList())
      .rep_float(Collections.emptyList())
      .rep_double(Collections.emptyList())
      .rep_string(list(""))
      .rep_bytes(list(ByteString.EMPTY))
      .rep_nested_enum(Collections.emptyList())
      .rep_nested_message(Collections.emptyList())
      .pack_int32(Collections.emptyList())
      .pack_uint32(Collections.emptyList())
      .pack_sint32(Collections.emptyList())
      .pack_fixed32(Collections.emptyList())
      .pack_sfixed32(list(0))
      .pack_int64(list(0L))
      .pack_uint64(list(0L))
      .pack_sint64(list(0L))
      .pack_fixed64(list(0L))
      .pack_sfixed64(list(0L))
      .pack_bool(list(false))
      .pack_float(list(0F))
      .pack_double(list(0.0))
      .pack_nested_enum(list(AllTypes.NestedEnum.UNKNOWN))
      .map_int32_int32(Collections.singletonMap(0, 0))
      .map_string_message(Collections.singletonMap("", new AllTypes.NestedMessage.Builder().build()))
      .map_string_enum(Collections.singletonMap("", AllTypes.NestedEnum.UNKNOWN))
      .oneof_int32(0)
      .build();

  private static final AllTypesOuterClass.AllTypes explicitIdentityAllTypesProtoc =
      AllTypesOuterClass.AllTypes.newBuilder()
          .setInt32(0)
          .setUint32(0)
          .setSint32(0)
          .setFixed32(0)
          .setSfixed32(0)
          .setInt64(0L)
          .setUint64(0L)
          .setSint64(0L)
          .setFixed64(0L)
          .setSfixed64(0L)
          .setBool(false)
          .setFloat(0F)
          .setDouble(0.0)
          .setString("")
          .setBytes(com.google.protobuf.ByteString.copyFrom(ByteString.EMPTY.toByteArray()))
          .setNestedEnum(AllTypesOuterClass.AllTypes.NestedEnum.UNKNOWN)
          .setNestedMessage(
              AllTypesOuterClass.AllTypes.NestedMessage.newBuilder().setA(0).build())
          .addAllRepInt32(list(0))
          .addAllRepUint32(list(0))
          .addAllRepSint32(list(0))
          .addAllRepFixed32(list(0))
          .addAllRepSfixed32(Collections.emptyList())
          .addAllRepInt64(Collections.emptyList())
          .addAllRepUint64(Collections.emptyList())
          .addAllRepSint64(Collections.emptyList())
          .addAllRepFixed64(Collections.emptyList())
          .addAllRepSfixed64(Collections.emptyList())
          .addAllRepBool(Collections.emptyList())
          .addAllRepFloat(Collections.emptyList())
          .addAllRepDouble(Collections.emptyList())
          .addAllRepString(list(""))
          .addAllRepBytes(
              list(com.google.protobuf.ByteString.copyFrom(ByteString.EMPTY.toByteArray())))
          .addAllRepNestedEnum(Collections.emptyList())
          .addAllRepNestedMessage(Collections.emptyList())
          .addAllPackInt32(Collections.emptyList())
          .addAllPackUint32(Collections.emptyList())
          .addAllPackSint32(Collections.emptyList())
          .addAllPackFixed32(Collections.emptyList())
          .addAllPackSfixed32(list(0))
          .addAllPackInt64(list(0L))
          .addAllPackUint64(list(0L))
          .addAllPackSint64(list(0L))
          .addAllPackFixed64(list(0L))
          .addAllPackSfixed64(list(0L))
          .addAllPackBool(list(false))
          .addAllPackFloat(list(0F))
          .addAllPackDouble(list(0.0))
          .addAllPackNestedEnum(list(AllTypesOuterClass.AllTypes.NestedEnum.UNKNOWN))
          .putMapInt32Int32(0, 0)
          .putMapStringMessage("",
              AllTypesOuterClass.AllTypes.NestedMessage.newBuilder().build())
          .putMapStringEnum("", AllTypesOuterClass.AllTypes.NestedEnum.UNKNOWN)
          .setOneofInt32(0)
          .build();

  private static final InteropMessageOuterClass.InteropMessage interopProtoc =
      InteropMessageOuterClass.InteropMessage.newBuilder()
          .setProto2Message(
              InteropTypes.MessageProto2.newBuilder().setA(23).setB("MJ").build())
          .setProto3Enum(squareup.proto3.java.interop.type.InteropTypes.EnumProto3.A)
          .setProto3Message(
              squareup.proto3.java.interop.type.InteropTypes.MessageProto3.newBuilder()
                  .setA(45).setB("MJ").build())
          .addAllRepProto2Message(
              list(InteropTypes.MessageProto2.newBuilder().setA(1).setB("1").build()))
          .addAllRepProto3Enum(
              list(squareup.proto3.java.interop.type.InteropTypes.EnumProto3.UNKNOWN))
          .addAllRepProto3Message(
              list(
                  squareup.proto3.java.interop.type.InteropTypes.MessageProto3.newBuilder()
                      .setA(2).build()))
          .addAllPackProto3Enum(
              list(squareup.proto3.java.interop.type.InteropTypes.EnumProto3.A))
          .putMapStringProto2Message("one",
              InteropTypes.MessageProto2.newBuilder().setA(23).setB("MJ").build())
          .putMapStringProto3Enum("two",
              squareup.proto3.java.interop.type.InteropTypes.EnumProto3.A)
          .putMapStringProto3Message("three",
              squareup.proto3.java.interop.type.InteropTypes.MessageProto3.newBuilder()
                  .setB("three").build())
          .setOneofProto3Message(
              squareup.proto3.java.interop.type.InteropTypes.MessageProto3.newBuilder().build())
          .build();

  private static final InteropMessage interopWireJ = new InteropMessage.Builder()
      .proto2_message(new MessageProto2(23, "MJ"))
      .proto3_enum(EnumProto3.A)
      .proto3_message(new MessageProto3(45, "MJ"))
      .rep_proto2_message(list(new MessageProto2(1, "1")))
      .rep_proto3_enum(list(EnumProto3.UNKNOWN))
      .rep_proto3_message(list(new MessageProto3.Builder().a(2).build()))
      .pack_proto3_enum(list(EnumProto3.A))
      .map_string_proto2_message(Collections.singletonMap("one", new MessageProto2(23, "MJ")))
      .map_string_proto3_enum(Collections.singletonMap("two", EnumProto3.A))
      .map_string_proto3_message(
          Collections.singletonMap("three", new MessageProto3.Builder().b("three").build()))
      .oneof_proto3_message(new MessageProto3.Builder().build())
      .build();

  /** Upstream's private {@code list(t)} helper returns a two-element list [t, t]. */
  private static <T> List<T> list(T t) {
    return Arrays.asList(t, t);
  }

  /**
   * Upstream's assertJsonEquals (wire's json test package, moshi-backed and excluded under
   * DEC-6): both operands are parsed with the reference implementation's own JSON parser into
   * messages of {@code prototype}'s type, then compared, which is order-insensitive for object
   * keys exactly like the upstream tree comparison.
   */
  private static void assertJsonEquals(String expected, String actual,
      com.google.protobuf.Message prototype) throws IOException {
    com.google.protobuf.Message.Builder expectedBuilder = prototype.newBuilderForType();
    JsonFormat.parser().merge(expected, expectedBuilder);
    com.google.protobuf.Message.Builder actualBuilder = prototype.newBuilderForType();
    JsonFormat.parser().merge(actual, actualBuilder);
    assertEquals(expectedBuilder.build(), actualBuilder.build());
  }

  /** Upstream loads the goldens from ../wire-tests/fixtures/shared/json; pinned copy on the
   * classpath (src/test/resources, verbatim from the pinned clone). */
  private static String loadJson(String fileName) {
    InputStream in = Proto3WireProtocCompatibilityTests.class
        .getResourceAsStream("/" + fileName);
    if (in == null) {
      throw new IllegalStateException("missing JSON fixture " + fileName);
    }
    try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      for (int read; (read = in.read(buffer)) != -1; ) {
        out.write(buffer, 0, read);
      }
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("cannot read JSON fixture " + fileName, e);
    }
  }
}
