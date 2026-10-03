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

import static com.squareup.wire.testing.TestFiles.readUtf8;
import static com.squareup.wire.testing.TestFiles.upstreamClone;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The pinned golden corpus under the phase-2 bytes mapping (docs/api-surface.md). The port's
 * generated output diverges from upstream's by exactly the documented mechanical mapping that
 * swaps okio's {@code ByteString} for the wire-owned {@code com.squareup.wire.Bytes}; this test
 * rewrites the UPSTREAM golden by that mapping and requires the port's raw CLI output to be
 * byte-identical to it, so any other divergence, including a regression back to an okio-typed
 * member the mapping covers, fails. The upstream golden is the single Java golden of
 * wire-golden-files at the pinned tag, read from the clone fetched by
 * scripts/fetch-upstream.sh ($ANTIWIRE_UPSTREAM, default /tmp/wire).
 */
public class AllTypesGoldenBytesMappingTest {
  private static final Path UPSTREAM_GOLDEN = upstreamClone()
      .resolve("wire-golden-files/src/main/java/com/squareup/wire/proto3/java/all_types")
      .resolve("AllTypes.java");
  private static final Path UPSTREAM_PROTO_ROOT = upstreamClone()
      .resolve("wire-golden-files/src/main/proto");

  static {
    if (!Files.isRegularFile(UPSTREAM_GOLDEN)) {
      throw new IllegalStateException(
          "pinned upstream golden not found at " + UPSTREAM_GOLDEN
              + "; run scripts/fetch-upstream.sh");
    }
  }

  /**
   * The documented divergence mapping, upstream form to port form, as ordered regex/replacement
   * pairs over generated source text. Order matters where one pattern is a prefix of another
   * ({@code ProtoAdapter.BYTES_VALUE} before {@code ProtoAdapter.BYTES}; the qualified name
   * before the bare token). The word boundary keeps {@code ProtoAdapter.BYTES_VALUE} intact for
   * its own rule, and the import sort absorbs the mapped import's new collation position.
   */
  private static final String[][] BYTES_MAPPING = {
      {"okio\\.ByteString", "com.squareup.wire.Bytes"},
      {"\\bByteString\\b", "Bytes"},
      {"\\bendMessageAndGetUnknownFields\\(", "endMessageAndGetUnknownFieldsBytes("},
      {"\\bunknownFields\\(\\)", "unknownFieldsBytes()"},
      {"\\bsuper\\.buildUnknownFields\\(\\)", "super.buildUnknownFieldsBytes()"},
      {"ProtoAdapter\\.BYTES_VALUE\\b", "ProtoAdapter.WIRE_BYTES_VALUE"},
      {"ProtoAdapter#BYTES_VALUE\\b", "ProtoAdapter#WIRE_BYTES_VALUE"},
      {"ProtoAdapter\\.BYTES\\b", "ProtoAdapter.WIRE_BYTES"},
      {"ProtoAdapter#BYTES\\b", "ProtoAdapter#WIRE_BYTES"},
  };

  /** Applies the mapping to upstream-generated source, then sorts the import block. */
  static String mapUpstreamSource(String source) {
    String mapped = source;
    for (String[] rule : BYTES_MAPPING) {
      mapped = Pattern.compile(rule[0]).matcher(mapped).replaceAll(rule[1]);
    }

    String[] lines = mapped.split("\n", -1);
    int firstImport = -1;
    int lastImport = -1;
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].startsWith("import ")) {
        if (firstImport == -1) firstImport = i;
        lastImport = i;
      } else if (firstImport != -1) {
        break;
      }
    }
    if (firstImport != -1) {
      List<String> imports = new ArrayList<>();
      for (int i = firstImport; i <= lastImport; i++) imports.add(lines[i]);
      Collections.sort(imports);
      for (int i = firstImport; i <= lastImport; i++) lines[i] = imports.get(i - firstImport);
    }
    return String.join("\n", lines);
  }

  @TempDir Path tempDir;

  @Test public void generatedCorpusDivergesFromUpstreamByExactlyTheBytesMapping()
      throws IOException, WireException {
    String golden = readUtf8(UPSTREAM_GOLDEN);

    // Non-vacuity: the golden really is upstream's okio-typed output, so the mapping above has
    // something to rewrite on it.
    assertTrue(golden.contains("import okio.ByteString;"), golden.substring(0, 2000));
    assertTrue(golden.contains("ProtoAdapter.BYTES."), "golden lacks the bytes adapter usages");
    assertTrue(golden.contains("adapter = \"com.squareup.wire.ProtoAdapter#BYTES\""),
        "golden lacks the bytes adapter string");
    assertTrue(golden.contains("unknownFields()"), "golden lacks unknown-field call sites");

    Path out = tempDir.resolve("java_out");
    WireCompiler.forArgs(
        okio.FileSystem.SYSTEM,
        WireLogger.NONE,
        "--proto_path=" + UPSTREAM_PROTO_ROOT,
        "--java_out=" + out,
        "squareup/wire/all_types_proto3.proto")
        .compile();

    Path generatedFile = out.resolve("com/squareup/wire/proto3/java/all_types/AllTypes.java");
    assertTrue(Files.isRegularFile(generatedFile), "the CLI did not emit " + generatedFile);
    // The port's output is compared RAW: it must already be the mapped form, byte for byte
    // (JavaPoet emits the import block sorted, matching the golden-side sort).
    String generated = readUtf8(generatedFile);

    // The port's output is okio-free and uses the canonical bytes forms.
    assertFalse(generated.contains("okio."), "okio leaked into generated output");
    assertTrue(generated.contains("import com.squareup.wire.Bytes;"));
    assertTrue(generated.contains("ProtoAdapter.WIRE_BYTES."));
    assertTrue(generated.contains("adapter = \"com.squareup.wire.ProtoAdapter#WIRE_BYTES\""));

    String expected = mapUpstreamSource(golden);
    if (!expected.equals(generated)) {
      fail("generated output diverges from the mapped upstream golden; first difference at "
          + difference(expected, generated));
    }
  }

  /** The mapping is total: re-applying it to its own output changes nothing. */
  @Test public void mappingIsIdempotent() throws IOException {
    String once = mapUpstreamSource(readUtf8(UPSTREAM_GOLDEN));
    assertEquals(once, mapUpstreamSource(once));
  }

  /** Line number and both texts of the first differing line. */
  private static String difference(String expected, String actual) {
    String[] expectedLines = expected.split("\n", -1);
    String[] actualLines = actual.split("\n", -1);
    int i = 0;
    while (i < expectedLines.length && i < actualLines.length
        && expectedLines[i].equals(actualLines[i])) {
      i++;
    }
    return "line " + (i + 1) + ":\n  golden (mapped): "
        + (i < expectedLines.length ? expectedLines[i] : "<end>") + "\n  generated:        "
        + (i < actualLines.length ? actualLines[i] : "<end>");
  }
}
