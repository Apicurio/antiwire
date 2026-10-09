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
  @Test public void getWithForwardSlashes() {
    Location location = Location.get("base/dir", "sub/file.proto");
    assertEquals("base/dir", location.getBase());
    assertEquals("sub/file.proto", location.getPath());
  }

  @Test public void getTrimsTrailingSlashFromBase() {
    Location location = Location.get("base/dir/", "file.proto");
    assertEquals("base/dir", location.getBase());
  }

  @Test public void getNormalizesWindowsBackslashesInBase() {
    Location location = Location.get("C:\\Users\\username\\protos", "language\\language.proto");
    assertEquals("C:/Users/username/protos", location.getBase());
    assertEquals("language/language.proto", location.getPath());
  }

  @Test public void getNormalizesWindowsBackslashesInPath() {
    Location location = Location.get("", "language\\language.proto");
    assertEquals("language/language.proto", location.getPath());
  }

  @Test public void getWithMixedSeparatorsInBase() {
    Location location = Location.get("C:\\Users\\username\\protos/", "file.proto");
    assertEquals("C:/Users/username/protos", location.getBase());
  }

  @Test public void getPreservesWindowsDriveRootBackslashInPath() {
    // okio requires 'C:\' (backslash) to recognize Windows absolute paths; 'C:/' is not supported
    // in older okio versions. Preserve the root backslash while converting internal separators.
    Location location = Location.get("", "C:\\Users\\protos");
    assertEquals("C:\\Users/protos", location.getPath());
  }

  // TASK-13 adaptation: the six cases above are the full upstream file. The cases below are
  // port-authored carryovers from the previous partial LocationTest; upstream 7.1.0 covers only
  // Location.get normalization, so these stay to keep at()/withoutBase()/withPathOnly()/toString()
  // under direct test.

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
}
