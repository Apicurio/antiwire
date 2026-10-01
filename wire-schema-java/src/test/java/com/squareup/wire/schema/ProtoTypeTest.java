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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Upstream commonTest translated (assertk to JUnit 5; see UPSTREAM-TEST-ADAPTATIONS.md, the
 * schema module's rows land beside the runtime module's).
 */
public class ProtoTypeTest {
  @Test public void simpleName() {
    assertEquals("Squaretime", ProtoType.get("Squaretime").simpleName());
    assertEquals("Money", ProtoType.get("squareup.Cash.Money").simpleName());
  }

  @Test public void enclosingTypeOrPackage() {
    assertNull(ProtoType.get("Squaretime").enclosingTypeOrPackage());
    assertEquals("squareup.Cash", ProtoType.get("squareup.Cash.Money").enclosingTypeOrPackage());
  }

  @Test public void simpleNameEnclosingTypeOrPackageIsNotDecomposedWithMap() {
    ProtoType money = ProtoType.get("map<string, squareup.Cash.Money>");
    assertTrue(money.isMap);
    // Upstream: the raw string is kept whole; simpleName splits on the last dot of the raw
    // string, which for a map ends with the value type's simple name including '>'.
    assertEquals("Money>", money.simpleName());
  }

  @Test public void scalar() {
    assertTrue(ProtoType.get("int32").isScalar);
    assertTrue(ProtoType.get("uint64").isScalar);
    assertTrue(ProtoType.get("sint32").isScalar);
    // Non-scalar names are not normalized into scalars.
    assertFalse(ProtoType.get("fixed16").isScalar);
  }

  @Test public void mapKeyMustBeScalar() {
    assertThrows(IllegalArgumentException.class,
        () -> ProtoType.get("map<squareup.Cash.Money, string>"));
  }

  @Test public void mapKeyMustNotBeBytesFloatOrDouble() {
    assertThrows(IllegalArgumentException.class, () -> ProtoType.get("map<bytes, string>"));
    assertThrows(IllegalArgumentException.class, () -> ProtoType.get("map<float, string>"));
    assertThrows(IllegalArgumentException.class, () -> ProtoType.get("map<double, string>"));
  }

  @Test public void protoTypeIsScalarOrMap() {
    ProtoType money = ProtoType.get("squareup.Cash.Money");
    assertFalse(money.isScalar);
    assertFalse(money.isMap);
    assertNull(money.keyType);
    assertNull(money.valueType);
  }

  @Test public void toStringReturnsString() {
    assertEquals("squareup.Cash.Money", ProtoType.get("squareup.Cash.Money").toString());
  }

  @Test public void nestedType() {
    ProtoType protoType = ProtoType.get("squareup.protos.simple.Person");
    assertEquals("squareup.protos.simple.Person.PhoneNumber",
        protoType.nestedType("PhoneNumber").toString());
  }

  @Test public void typeUrl() {
    assertEquals("type.googleapis.com/squareup.protos.simple.Person",
        ProtoType.get("squareup.protos.simple.Person").typeUrl());
    assertNull(ProtoType.get("int32").typeUrl());
  }
}
