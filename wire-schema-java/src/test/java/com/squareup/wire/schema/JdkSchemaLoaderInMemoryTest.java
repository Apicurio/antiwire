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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TASK-33.5: {@link JdkSchemaLoader#forFileSystem(FileSystem)} over an in-memory Jimfs
 * filesystem. This is the route that restores what okio's FakeFileSystem gave consumers before
 * the migration: no staging into a host temp directory, and resolution semantics that belong to
 * the supplied filesystem instead of the host.
 */
public class JdkSchemaLoaderInMemoryTest {
  private FileSystem fs;

  @BeforeEach public void setUp() {
    fs = Jimfs.newFileSystem();
  }

  @AfterEach public void tearDown() throws IOException {
    fs.close();
  }

  private Path write(String path, String content) throws IOException {
    Path file = fs.getPath(path);
    Files.createDirectories(file.getParent());
    Files.write(file, content.getBytes(UTF_8));
    return file;
  }

  /**
   * A schema and its import load and link entirely from memory. No host path is involved: the
   * facade created by {@code forFileSystem} holds only the supplied filesystem, so there is no
   * temp-directory staging to observe; the proof that resolution went through memory is that
   * the imported file exists nowhere on the host (the test never creates it there) yet the
   * imported type links.
   */
  @Test public void loadsSchemaAndImportsEntirelyFromMemory() throws Exception {
    write("/protos/com/example/inmem/common.proto", ""
        + "syntax = \"proto3\";\n"
        + "package com.example.inmem;\n"
        + "message Common { string id = 1; }\n");
    write("/protos/com/example/inmem/messages.proto", ""
        + "syntax = \"proto3\";\n"
        + "package com.example.inmem;\n"
        + "import \"com/example/inmem/common.proto\";\n"
        + "message Messages { Common common = 1; }\n");

    try (JdkSchemaLoader loader = JdkSchemaLoader.forFileSystem(fs)) {
      loader.initRoots(Collections.singletonList(fs.getPath("/protos")));
      Schema schema = loader.loadSchema();

      assertNotNull(schema.getType("com.example.inmem.Messages"));
      assertNotNull(schema.getType("com.example.inmem.Common"), "the import did not resolve");
      assertNotNull(schema.protoFile("com/example/inmem/common.proto"));
    }
  }

  /**
   * Resolution follows the supplied filesystem's rules, not the host's. Jimfs is case-sensitive
   * by design, so the wrong-case import must fail even though the same schema would resolve on a
   * case-insensitive host through the old temp-directory staging (the macOS APFS leak of the
   * Apicurio migration). This host is Linux, where the host filesystem is also case-sensitive;
   * the pair still proves the point that matters here: the decision is made inside the supplied
   * filesystem, because the file with the exact staged casing is the only one that exists, on
   * any host. The macOS behavior is stated, not faked.
   */
  @Test public void caseSensitivityComesFromTheSuppliedFileSystem() throws Exception {
    write("/protos/com/example/cased/pet.proto", ""
        + "syntax = \"proto3\";\n"
        + "package com.example.cased;\n"
        + "message Pet { string name = 1; }\n");

    write("/protos/com/example/cased/owner.proto", ""
        + "syntax = \"proto3\";\n"
        + "package com.example.cased;\n"
        + "import \"com/example/cased/pet.proto\";\n"
        + "message Owner { Pet pet = 1; }\n");
    try (JdkSchemaLoader correct = JdkSchemaLoader.forFileSystem(fs)) {
      correct.initRoots(Collections.singletonList(fs.getPath("/protos")));
      assertNotNull(correct.loadSchema().getType("com.example.cased.Owner"));
    }

    write("/protos2/com/example/cased/pet.proto", ""
        + "syntax = \"proto3\";\n"
        + "package com.example.cased;\n"
        + "message Pet { string name = 1; }\n");
    write("/protos2/com/example/cased/owner.proto", ""
        + "syntax = \"proto3\";\n"
        + "package com.example.cased;\n"
        + "import \"com/example/cased/Pet.proto\";\n"
        + "message Owner { Pet pet = 1; }\n");

    try (JdkSchemaLoader wrongCase = JdkSchemaLoader.forFileSystem(fs)) {
      wrongCase.initRoots(Collections.singletonList(fs.getPath("/protos2")));
      assertThrows(SchemaException.class, wrongCase::loadSchema);
    }
  }
}
