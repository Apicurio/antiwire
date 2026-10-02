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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.squareup.wire.SchemaBuilder;
import com.squareup.wire.schema.ProtoType;
import com.squareup.wire.schema.Schema;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * TASK-16 adoption of upstream TypeMoverTest:
 * {@code wire-schema/src/jvmTest/kotlin/com/squareup/wire/schema/internal/TypeMoverTest.kt} at
 * square/wire tag 7.1.0. Moves, schemas, expected {@code toSchema()} outputs, and the
 * {@code moveInexistentType} error message are verbatim; assertk maps onto JUnit 5 and
 * {@code ProtoType.get("cafe", "EspressoShot")} becomes {@code ProtoType.get("cafe.EspressoShot")}.
 */
public class TypeMoverTest {
  /**
   * Move a type from one schema to another.
   *
   * <p>This move triggers 3 import changes:
   *
   * <ul>
   *   <li>Adding an import from the source file to the target file. (espresso.proto)
   *   <li>Removing an import from the source file that was only used by the target type.
   *       (roast.proto)
   *   <li>Adding an import to the target file required by the target type (roast.proto).
   * </ul>
   */
  @Test public void moveTypeToNewFile() {
    Schema oldSchema = new SchemaBuilder()
        .add("cafe/cafe.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "package cafe;\n"
            + "\n"
            + "import \"cafe/roast.proto\";\n"
            + "\n"
            + "message CafeDrink {\n"
            + "  optional int32 size_ounces = 1;\n"
            + "  repeated EspressoShot shots = 2;\n"
            + "}\n"
            + "\n"
            + "message EspressoShot {\n"
            + "  optional Roast roast = 1;\n"
            + "  optional bool decaf = 2;\n"
            + "}\n")
        .add("cafe/roast.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "package cafe;\n"
            + "\n"
            + "enum Roast {\n"
            + "  MEDIUM = 1;\n"
            + "  DARK = 2;\n"
            + "}\n")
        .build();

    Schema newSchema = new TypeMover(
        oldSchema,
        Collections.singletonList(
            new TypeMover.Move(ProtoType.get("cafe.EspressoShot"), "cafe/espresso.proto")))
        .move();

    assertEquals(""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: cafe/cafe.proto\n"
            + "\n"
            + "syntax = \"proto2\";\n"
            + "\n"
            + "package cafe;\n"
            + "\n"
            + "import \"cafe/espresso.proto\";\n"
            + "\n"
            + "message CafeDrink {\n"
            + "  optional int32 size_ounces = 1;\n"
            + "\n"
            + "  repeated EspressoShot shots = 2;\n"
            + "}\n",
        newSchema.protoFile("cafe/cafe.proto").toSchema());
    assertEquals(""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: cafe/espresso.proto\n"
            + "\n"
            + "syntax = \"proto2\";\n"
            + "\n"
            + "package cafe;\n"
            + "\n"
            + "import \"cafe/roast.proto\";\n"
            + "\n"
            + "message EspressoShot {\n"
            + "  optional Roast roast = 1;\n"
            + "\n"
            + "  optional bool decaf = 2;\n"
            + "}\n",
        newSchema.protoFile("cafe/espresso.proto").toSchema());
  }

  @Test public void moveTypeToExistingFile() {
    Schema oldSchema = new SchemaBuilder()
        .add("cafe/cafe.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "package cafe;\n"
            + "\n"
            + "import \"cafe/roast.proto\";\n"
            + "\n"
            + "message CafeDrink {\n"
            + "  optional int32 size_ounces = 1;\n"
            + "  repeated EspressoShot shots = 2;\n"
            + "}\n"
            + "\n"
            + "message EspressoShot {\n"
            + "  optional Roast roast = 1;\n"
            + "  optional bool decaf = 2;\n"
            + "}\n")
        .add("cafe/roast.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "package cafe;\n"
            + "\n"
            + "enum Roast {\n"
            + "  MEDIUM = 1;\n"
            + "  DARK = 2;\n"
            + "}\n")
        .build();

    Schema newSchema = new TypeMover(
        oldSchema,
        Collections.singletonList(
            new TypeMover.Move(ProtoType.get("cafe.EspressoShot"), "cafe/roast.proto")))
        .move();

    assertEquals(""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: cafe/cafe.proto\n"
            + "\n"
            + "syntax = \"proto2\";\n"
            + "\n"
            + "package cafe;\n"
            + "\n"
            + "import \"cafe/roast.proto\";\n"
            + "\n"
            + "message CafeDrink {\n"
            + "  optional int32 size_ounces = 1;\n"
            + "\n"
            + "  repeated EspressoShot shots = 2;\n"
            + "}\n",
        newSchema.protoFile("cafe/cafe.proto").toSchema());
    assertEquals(""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: cafe/roast.proto\n"
            + "\n"
            + "syntax = \"proto2\";\n"
            + "\n"
            + "package cafe;\n"
            + "\n"
            + "enum Roast {\n"
            + "  MEDIUM = 1;\n"
            + "  DARK = 2;\n"
            + "}\n"
            + "\n"
            + "message EspressoShot {\n"
            + "  optional Roast roast = 1;\n"
            + "\n"
            + "  optional bool decaf = 2;\n"
            + "}\n",
        newSchema.protoFile("cafe/roast.proto").toSchema());
  }

  @Test public void multipleMovesFromSingleSource() {
    Schema oldSchema = new SchemaBuilder()
        .add("abc.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message A {\n"
            + "  optional B b = 1;\n"
            + "  optional C c = 2;\n"
            + "}\n"
            + "\n"
            + "message B {\n"
            + "  optional C c = 1;\n"
            + "}\n"
            + "\n"
            + "message C {\n"
            + "}\n")
        .build();

    Schema newSchema = new TypeMover(
        oldSchema,
        Arrays.asList(
            new TypeMover.Move(ProtoType.get("A"), "a.proto"),
            new TypeMover.Move(ProtoType.get("B"), "b.proto"),
            new TypeMover.Move(ProtoType.get("C"), "c.proto")))
        .move();

    assertEquals(""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: abc.proto\n"
            + "\n"
            + "syntax = \"proto2\";\n",
        newSchema.protoFile("abc.proto").toSchema());

    assertEquals(""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: a.proto\n"
            + "\n"
            + "syntax = \"proto2\";\n"
            + "\n"
            + "import \"b.proto\";\n"
            + "import \"c.proto\";\n"
            + "\n"
            + "message A {\n"
            + "  optional B b = 1;\n"
            + "\n"
            + "  optional C c = 2;\n"
            + "}\n",
        newSchema.protoFile("a.proto").toSchema());

    assertEquals(""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: b.proto\n"
            + "\n"
            + "syntax = \"proto2\";\n"
            + "\n"
            + "import \"c.proto\";\n"
            + "\n"
            + "message B {\n"
            + "  optional C c = 1;\n"
            + "}\n",
        newSchema.protoFile("b.proto").toSchema());

    assertEquals(""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: c.proto\n"
            + "\n"
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message C {}\n",
        newSchema.protoFile("c.proto").toSchema());
  }

  @Test public void moveWithServiceDependency() {
    Schema oldSchema = new SchemaBuilder()
        .add("abc.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message A {\n"
            + "}\n"
            + "\n"
            + "message B {\n"
            + "}\n"
            + "\n"
            + "service C {\n"
            + "  rpc Go (A) returns (B);\n"
            + "}\n")
        .build();

    Schema newSchema = new TypeMover(
        oldSchema,
        Arrays.asList(
            new TypeMover.Move(ProtoType.get("A"), "a.proto"),
            new TypeMover.Move(ProtoType.get("B"), "b.proto")))
        .move();

    assertEquals(""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: abc.proto\n"
            + "\n"
            + "syntax = \"proto2\";\n"
            + "\n"
            + "import \"a.proto\";\n"
            + "import \"b.proto\";\n"
            + "\n"
            + "service C {\n"
            + "  rpc Go (A) returns (B);\n"
            + "}\n",
        newSchema.protoFile("abc.proto").toSchema());
  }

  @Test public void swapTypes() {
    Schema oldSchema = new SchemaBuilder()
        .add("a.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "import \"b.proto\";\n"
            + "\n"
            + "message A {\n"
            + "  optional B b = 1;\n"
            + "}\n")
        .add("b.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message B {\n"
            + "}\n")
        .build();

    Schema newSchema = new TypeMover(
        oldSchema,
        Arrays.asList(
            new TypeMover.Move(ProtoType.get("A"), "b.proto"),
            new TypeMover.Move(ProtoType.get("B"), "a.proto")))
        .move();

    assertEquals(""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: b.proto\n"
            + "\n"
            + "syntax = \"proto2\";\n"
            + "\n"
            + "import \"a.proto\";\n"
            + "\n"
            + "message A {\n"
            + "  optional B b = 1;\n"
            + "}\n",
        newSchema.protoFile("b.proto").toSchema());
    assertEquals(""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: a.proto\n"
            + "\n"
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message B {}\n",
        newSchema.protoFile("a.proto").toSchema());
  }

  @Test public void unrelatedUnusedImportsNotPruned() {
    Schema oldSchema = new SchemaBuilder()
        .add("a.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "import \"b.proto\";\n"
            + "\n"
            + "message A {\n"
            + "}\n")
        .add("b.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n")
        .build();

    Schema newSchema = new TypeMover(
        oldSchema,
        Collections.singletonList(new TypeMover.Move(ProtoType.get("A"), "c.proto")))
        .move();

    assertEquals(""
            + "// Proto schema formatted by Wire, do not edit.\n"
            + "// Source: a.proto\n"
            + "\n"
            + "syntax = \"proto2\";\n"
            + "\n"
            + "import \"b.proto\";\n",
        newSchema.protoFile("a.proto").toSchema());
  }

  @Test public void moveInexistentType() {
    Schema oldSchema = new SchemaBuilder()
        .add("a.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message A {\n"
            + "}\n")
        .build();

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
      new TypeMover(
          oldSchema,
          Collections.singletonList(new TypeMover.Move(ProtoType.get("B"), "b.proto")))
          .move();
    });
    assertEquals("cannot move B, it isn't in this schema", e.getMessage());
  }
}
