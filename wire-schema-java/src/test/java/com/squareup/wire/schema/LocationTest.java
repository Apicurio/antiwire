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

import org.junit.jupiter.api.Test;

/** Upstream commonTest translated (assertk to JUnit 5). */
public class LocationTest {
  @Test public void location() {
    Location location = Location.get("src/main/proto/squareup/dinosaurs/dinosaur.proto");
    assertEquals("src/main/proto/squareup/dinosaurs/dinosaur.proto", location.toString());
  }

  @Test public void locationWithBase() {
    Location location = Location.get("src/main/proto",
        "squareup/dinosaurs/dinosaur.proto");
    assertEquals("src/main/proto/squareup/dinosaurs/dinosaur.proto", location.toString());
  }

  @Test public void locationWithLineAndColumn() {
    Location location = Location.get("src/main/proto",
        "squareup/dinosaurs/dinosaur.proto").at(10, 20);
    assertEquals("src/main/proto/squareup/dinosaurs/dinosaur.proto:10:20", location.toString());
  }

  @Test public void locationWithLineOnly() {
    Location location = Location.get("src/main/proto",
        "squareup/dinosaurs/dinosaur.proto").at(10, -1);
    assertEquals("src/main/proto/squareup/dinosaurs/dinosaur.proto:10", location.toString());
  }

  @Test public void withoutBase() {
    Location location = Location.get("src/main/proto", "dinosaur.proto");
    assertEquals("dinosaur.proto", location.withoutBase().toString());
  }

  @Test public void withPathOnly() {
    Location location = Location.get("src/main/proto", "dinosaur.proto").at(10, 20);
    assertEquals("dinosaur.proto", location.withPathOnly().toString());
  }

  @Test public void windowsPaths() {
    Location location = Location.get("C:\\src\\main\\proto", "dinosaur.proto");
    assertEquals("C:/src/main/proto/dinosaur.proto", location.toString());
  }

  @Test public void windowsDriveRootPreserved() {
    // The preservation branch applies to a path argument carrying the drive root.
    Location location = Location.get("", "C:\\dinosaur.proto");
    assertEquals("C:\\dinosaur.proto", location.path);
    assertEquals("C:\\dinosaur.proto", location.toString());
  }
}
