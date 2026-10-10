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
package com.squareup.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import okio.FileSystem;
import okio.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DryRunFileSystem now fails with an unchecked exception (TASK-33.3). The handlers that write
 * through it must still add their "Error emitting ..." context, so the failure message and the
 * cause chain stay what they were when the failure was a checked IOException.
 */
public class DryRunErrorContextTest {
  @TempDir java.nio.file.Path tempDir;

  @Test public void sinkOnAnExistingFileThrowsUncheckedIoExceptionWithTheOriginalCause() throws IOException {
    java.nio.file.Path existing = Files.createFile(tempDir.resolve("a.txt"));
    DryRunFileSystem fs = new DryRunFileSystem(FileSystem.SYSTEM);
    UncheckedIOException e = assertThrows(UncheckedIOException.class,
        () -> fs.sink(Path.get(existing.toString()), true));
    assertTrue(e.getCause().getMessage().startsWith("already exists: "), e.getCause().getMessage());
  }

  @Test public void appendingToAMissingFileThrowsUncheckedIoException() {
    DryRunFileSystem fs = new DryRunFileSystem(FileSystem.SYSTEM);
    UncheckedIOException e = assertThrows(UncheckedIOException.class,
        () -> fs.appendingSink(Path.get(tempDir.resolve("missing.txt").toString()), true));
    assertEquals("doesn't exist: " + tempDir.resolve("missing.txt"), e.getCause().getMessage());
  }
}
