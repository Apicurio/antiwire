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

import static org.junit.jupiter.api.Assertions.assertThrows;

import okio.Path;
import org.junit.jupiter.api.Test;

/**
 * TASK-16 adoption of upstream SchemaHandlerTest:
 * {@code wire-schema/src/commonTest/kotlin/com/squareup/wire/schema/SchemaHandlerTest.kt} at
 * square/wire tag 7.1.0. Names, inputs, and expected exceptions are unchanged. Kotlin's
 * {@code assertFailsWith<IllegalArgumentException>} maps to {@code assertThrows} and raw-string
 * UNC paths map to Java string literals; the upstream cases run unadapted because the vendored
 * okio {@link Path} parses UNC roots lexically on every platform, like okio 3.18.2 (verified
 * 2026-10-02 against that tag's commonMain internal/Path.kt when adopting these cases).
 */
public class SchemaHandlerTest {
  private final TestSchemaHandler schemaHandler = new TestSchemaHandler();

  @Test public void generatedPathCanUseSameUncRoot() {
    Path outDirectory = Path.get("\\\\trusted\\generated");
    Path generatedPath = Path.get("\\\\trusted\\generated\\Message.java");

    schemaHandler.checkGeneratedPath(generatedPath, outDirectory);
  }

  @Test public void generatedPathCannotEscapeToAnotherUncRoot() {
    Path outDirectory = Path.get("\\\\trusted\\generated");
    Path generatedPath = Path.get("\\\\attacker\\generated\\Message.java");

    assertThrows(IllegalArgumentException.class,
        () -> schemaHandler.checkGeneratedPath(generatedPath, outDirectory));
  }

  /** Package-visible so the TASK-17 corpus can drive the same shim without a second copy. */
  static final class TestSchemaHandler extends SchemaHandler {
    void checkGeneratedPath(Path filePath, Path outDirectory) {
      checkPathInOutDirectory(filePath, outDirectory);
    }

    @Override public Path handle(Type type, Context context) {
      return null;
    }

    @Override public java.util.List<Path> handle(Service service, Context context) {
      return java.util.Collections.emptyList();
    }

    @Override public Path handle(Extend extend, Field field, Context context) {
      return null;
    }
  }
}
