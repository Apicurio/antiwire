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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Upstream commonTest translated (assertk to JUnit 5; try/fail/catch collapsed to assertThrows).
 * Kotlin property reads become member calls: {@code simpleName}, {@code typeUrl}, and {@code
 * enclosingTypeOrPackage} are methods in the port; {@code isScalar} is a public field on both
 * sides.
 */
public class ProtoTypeTest {
  @Test public void get() {
    assertSame(ProtoType.INT32, ProtoType.get("int32"));
    assertEquals(ProtoType.get("Person"), ProtoType.get("Person"));
    assertEquals(ProtoType.get("squareup.protos.person", "Person"),
        ProtoType.get("squareup.protos.person.Person"));
  }

  @Test public void simpleName() {
    ProtoType person = ProtoType.get("squareup.protos.person.Person");
    assertEquals("Person", person.simpleName());
  }

  @Test public void scalarToString() {
    assertEquals("int32", ProtoType.INT32.toString());
    assertEquals("string", ProtoType.STRING.toString());
    assertEquals("bytes", ProtoType.BYTES.toString());
  }

  @Test public void nestedType() {
    assertEquals(ProtoType.get("squareup.protos.person.Person.PhoneType"),
        ProtoType.get("squareup.protos.person.Person").nestedType("PhoneType"));
  }

  @Test public void primitivesCannotNest() {
    assertThrows(IllegalStateException.class, () -> ProtoType.INT32.nestedType("PhoneType"));
  }

  @Test public void mapsCannotNest() {
    assertThrows(IllegalStateException.class,
        () -> ProtoType.get("map<string, string>").nestedType("PhoneType"));
  }

  @Test public void mapFormat() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ProtoType.get("map<string>"));
    assertEquals("expected ',' in map type: map<string>", e.getMessage());
  }

  @Test public void mapKeyScalarType() {
    assertThrows(IllegalArgumentException.class, () -> ProtoType.get("map<bytes, string>"));
    assertThrows(IllegalArgumentException.class, () -> ProtoType.get("map<double, string>"));
    assertThrows(IllegalArgumentException.class, () -> ProtoType.get("map<float, string>"));
    assertThrows(IllegalArgumentException.class, () -> ProtoType.get("map<some.Message, string>"));
  }

  @Test public void messageToString() {
    ProtoType person = ProtoType.get("squareup.protos.person.Person");
    assertEquals("squareup.protos.person.Person", person.toString());

    ProtoType phoneType = person.nestedType("PhoneType");
    assertEquals("squareup.protos.person.Person.PhoneType", phoneType.toString());
  }

  @Test public void fieldMask() {
    assertEquals(ProtoType.get("google.protobuf.FieldMask"), ProtoType.FIELD_MASK);
    assertEquals("type.googleapis.com/google.protobuf.FieldMask", ProtoType.FIELD_MASK.typeUrl());
  }

  @Test public void enclosingTypeOrPackage() {
    assertNull(ProtoType.STRING.enclosingTypeOrPackage());

    ProtoType person = ProtoType.get("squareup.protos.person.Person");
    assertEquals("squareup.protos.person", person.enclosingTypeOrPackage());

    ProtoType phoneType = person.nestedType("PhoneType");
    assertEquals("squareup.protos.person.Person", phoneType.enclosingTypeOrPackage());
  }

  @Test public void isScalar() {
    assertTrue(ProtoType.INT32.isScalar);
    assertTrue(ProtoType.STRING.isScalar);
    assertTrue(ProtoType.BYTES.isScalar);
    assertFalse(ProtoType.get("squareup.protos.person.Person").isScalar);
  }

  // TASK-13 adaptation: the cases above are the full upstream file. The cases below are
  // port-authored carryovers from the previous partial ProtoTypeTest retained because upstream
  // 7.1.0 has no counterpart for them (map-name quirks, key/value accessors, scalar typeUrl).

  @Test public void simpleNameEnclosingTypeOrPackageIsNotDecomposedWithMap() {
    ProtoType money = ProtoType.get("map<string, squareup.Cash.Money>");
    assertTrue(money.isMap);
    // Upstream: the raw string is kept whole; simpleName splits on the last dot of the raw
    // string, which for a map ends with the value type's simple name including '>'.
    assertEquals("Money>", money.simpleName());
  }

  @Test public void protoTypeIsScalarOrMap() {
    ProtoType money = ProtoType.get("squareup.Cash.Money");
    assertFalse(money.isScalar);
    assertFalse(money.isMap);
    assertNull(money.keyType);
    assertNull(money.valueType);
  }

  @Test public void typeUrl() {
    assertEquals("type.googleapis.com/squareup.protos.simple.Person",
        ProtoType.get("squareup.protos.simple.Person").typeUrl());
    assertNull(ProtoType.get("int32").typeUrl());
  }
}
