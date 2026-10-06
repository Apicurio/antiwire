/*
 * New shared case for okio's FileSystem.listRecursively, added in the 2026-09-30
 * bug-fix pass at square/okio tag parent-3.18.2, commit
 * 9403a094ac98d5a6ba8bad4ec3301399796fad67.
 *
 * This file is compiled twice: against the port's okio package (wire-runtime-java) and
 * against upstream okio-jvm 3.18.2 in the isolated oracle module. Both compilations run
 * the same case. The scenario pins the symlink-cycle detection of commonListRecursively
 * (internal/FileSystem.kt:94 seeds the traversal stack with the starting directory, so
 * the cycle is reported at the symlink itself on first visit, before any duplicate
 * yields); the expectation was verified against the pinned artifact on this machine.
 * The case needs a real filesystem with symlink support, so it goes through
 * FileSystem.SYSTEM and a JUnit temporary directory rather than an in-memory fake. The
 * port returns an eager Iterable where upstream returns a lazy Sequence, so the case
 * drains the result inside the assertion block: the eager port throws from the call,
 * upstream's sequence throws during iteration, and both surface in the same place.
 * Path construction uses the reflective path() helper of the shared PathTest spelling
 * (ledger R11).
 */
package okio

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path as NioPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FileSystemTest {

  /** Test-only construction spelling compiled by both implementations (ledger R11). */
  private fun path(s: String): Path {
    return Path::class.java.getMethod("get", String::class.java).invoke(null, s) as Path
  }

  @Test fun listRecursivelyDetectsSymlinkCycleOnFirstVisit(@TempDir dir: NioPath) {
    Files.write(dir.resolve("f.txt"), "x".toByteArray())
    Files.createSymbolicLink(dir.resolve("self"), dir)
    val okioDir = path(dir.toString())

    val e = assertThrows(IOException::class.java) {
      for (visited in FileSystem.SYSTEM.listRecursively(okioDir, true)) {
        // Draining the traversal forces the cycle check on both shapes.
      }
    }
    assertEquals("symlink cycle at $okioDir/self", e.message)

    val listed = FileSystem.SYSTEM.listRecursively(okioDir, false).toList().map { it.toString() }
    assertEquals(listOf("$okioDir/f.txt", "$okioDir/self"), listed)
  }
}
