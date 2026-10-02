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

import static com.squareup.wire.testing.TestFiles.add;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-16 adoption of upstream CycleCheckerTest:
 * {@code wire-compiler/src/test/java/com/squareup/wire/schema/CycleCheckerTest.kt} at square/wire
 * tag 7.1.0. Inputs and expected messages are verbatim (the cycle messages print import paths
 * only, so the temp-root adaptation does not touch them); upstream's in-memory FakeFileSystem
 * becomes a JUnit {@link TempDir} tree and assertk maps onto JUnit 5.
 */
public class CycleCheckerTest {
  @TempDir Path tempDir;

  @Test
  public void singleFileImportCycle() throws IOException {
    add(tempDir, "source-path/ouroboros.proto", ""
        + "syntax = \"proto2\";\n"
        + "import \"ouroboros.proto\";\n"
        + "message Snake {\n"
        + "}\n");

    SchemaException e = assertThrows(SchemaException.class, this::loadAndLinkSchema);
    assertEquals(""
        + "imports form a cycle:\n"
        + "  ouroboros.proto:\n"
        + "    import \"ouroboros.proto\";",
        e.getMessage());
  }

  @Test
  public void threeFileImportCycle() throws IOException {
    add(tempDir, "source-path/paper.proto", ""
        + "syntax = \"proto2\";\n"
        + "import \"rock.proto\";\n"
        + "message Paper {\n"
        + "}\n");
    add(tempDir, "source-path/rock.proto", ""
        + "syntax = \"proto2\";\n"
        + "import \"scissors.proto\";\n"
        + "message Rock {\n"
        + "}\n");
    add(tempDir, "source-path/scissors.proto", ""
        + "syntax = \"proto2\";\n"
        + "import \"paper.proto\";\n"
        + "message Scissors {\n"
        + "}\n");

    SchemaException e = assertThrows(SchemaException.class, this::loadAndLinkSchema);
    assertEquals(""
        + "imports form a cycle:\n"
        + "  paper.proto:\n"
        + "    import \"rock.proto\";\n"
        + "  rock.proto:\n"
        + "    import \"scissors.proto\";\n"
        + "  scissors.proto:\n"
        + "    import \"paper.proto\";",
        e.getMessage());
  }

  @Test
  public void multipleCycleImportProblem() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "syntax = \"proto2\";\n"
        + "import \"b.proto\";\n"
        + "import \"d.proto\";\n"
        + "message A {\n"
        + "}\n");
    add(tempDir, "source-path/b.proto", ""
        + "syntax = \"proto2\";\n"
        + "import \"c.proto\";\n"
        + "message B {\n"
        + "}\n");
    add(tempDir, "source-path/c.proto", ""
        + "syntax = \"proto2\";\n"
        + "import \"a.proto\";\n"
        + "import \"b.proto\";\n"
        + "message C {\n"
        + "}\n");
    add(tempDir, "source-path/d.proto", ""
        + "syntax = \"proto2\";\n"
        + "message D {\n"
        + "}\n");

    SchemaException e = assertThrows(SchemaException.class, this::loadAndLinkSchema);
    assertEquals(""
        + "imports form a cycle:\n"
        + "  a.proto:\n"
        + "    import \"b.proto\";\n"
        + "  b.proto:\n"
        + "    import \"c.proto\";\n"
        + "  c.proto:\n"
        + "    import \"a.proto\";\n"
        + "    import \"b.proto\";",
        e.getMessage());
  }

  /** The files form a dag, but the packages form a cycle. */
  @Test
  public void packageCycle() throws IOException {
    add(tempDir, "source-path/people/employee.proto", ""
        + "syntax = \"proto2\";\n"
        + "import \"locations/office.proto\";\n"
        + "import \"locations/residence.proto\";\n"
        + "package people;\n"
        + "message Employee {\n"
        + "  optional locations.Office office = 1;\n"
        + "  optional locations.Residence residence = 2;\n"
        + "}\n");
    add(tempDir, "source-path/locations/office.proto", ""
        + "syntax = \"proto2\";\n"
        + "import \"people/office_manager.proto\";\n"
        + "package locations;\n"
        + "message Office {\n"
        + "  optional people.OfficeManager office_manager = 1;\n"
        + "}\n");
    add(tempDir, "source-path/locations/residence.proto", ""
        + "syntax = \"proto2\";\n"
        + "package locations;\n"
        + "message Residence {\n"
        + "}\n");
    add(tempDir, "source-path/people/office_manager.proto", ""
        + "syntax = \"proto2\";\n"
        + "package people;\n"
        + "message OfficeManager {\n"
        + "}\n");

    SchemaException e = assertThrows(SchemaException.class, this::loadAndLinkSchema);
    assertEquals(""
        + "packages form a cycle:\n"
        + "  locations imports people\n"
        + "    locations/office.proto:\n"
        + "      import \"people/office_manager.proto\";\n"
        + "  people imports locations\n"
        + "    people/employee.proto:\n"
        + "      import \"locations/office.proto\";\n"
        + "      import \"locations/residence.proto\";",
        e.getMessage());
  }

  /**
   * In this example the files form a dag, but the packages don't because d.proto and e.proto are
   * in packages a and b. This test confirms that the Go package is used to detect and display
   * cycles.
   */
  @Test
  public void goPackagePreferredWhenResolvingPackageCycles() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "syntax = \"proto2\";\n"
        + "import \"b.proto\";\n"
        + "option go_package = \"a\";\n");
    add(tempDir, "source-path/b.proto", ""
        + "syntax = \"proto2\";\n"
        + "package b;\n"
        + "import \"c.proto\";\n"
        + "option go_package = \"b\";\n");
    add(tempDir, "source-path/c.proto", ""
        + "syntax = \"proto2\";\n"
        + "import \"d.proto\";\n"
        + "option go_package = \"c\";\n");
    add(tempDir, "source-path/d.proto", ""
        + "syntax = \"proto2\";\n"
        + "package d;\n"
        + "import \"e.proto\";\n"
        + "option go_package = \"a\";\n");
    add(tempDir, "source-path/e.proto", ""
        + "syntax = \"proto2\";\n"
        + "package b;\n");

    SchemaException e = assertThrows(SchemaException.class, this::loadAndLinkSchema);
    assertEquals(""
        + "packages form a cycle:\n"
        + "  a imports b\n"
        + "    a.proto:\n"
        + "      import \"b.proto\";\n"
        + "    d.proto:\n"
        + "      import \"e.proto\";\n"
        + "  b imports c\n"
        + "    b.proto:\n"
        + "      import \"c.proto\";\n"
        + "  c imports a\n"
        + "    c.proto:\n"
        + "      import \"d.proto\";",
        e.getMessage());
  }

  /** Check messaging when the cycle involves a file without a package specified. */
  @Test
  public void emptyPackageCycle() throws IOException {
    add(tempDir, "source-path/a.proto", ""
        + "syntax = \"proto2\";\n"
        + "import \"b.proto\";\n");
    add(tempDir, "source-path/b.proto", ""
        + "syntax = \"proto2\";\n"
        + "package b;\n"
        + "import \"c.proto\";\n");
    add(tempDir, "source-path/c.proto", ""
        + "syntax = \"proto2\";\n");

    SchemaException e = assertThrows(SchemaException.class, this::loadAndLinkSchema);
    assertEquals(""
        + "packages form a cycle:\n"
        + "  <default> imports b\n"
        + "    a.proto:\n"
        + "      import \"b.proto\";\n"
        + "  b imports <default>\n"
        + "    b.proto:\n"
        + "      import \"c.proto\";",
        e.getMessage());
  }

  private Schema loadAndLinkSchema() throws IOException {
    SchemaLoader loader = new SchemaLoader(okio.FileSystem.SYSTEM);
    loader.initRoots(
        Collections.singletonList(Location.get(tempDir.resolve("source-path").toString())),
        Collections.emptyList());
    return loader.loadSchema();
  }
}
