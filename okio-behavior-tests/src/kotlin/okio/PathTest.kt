/*
 * Adapted from okio/src/commonTest/kotlin/okio/PathTest.kt at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67.
 *
 * This file is compiled twice: against the port's okio package (wire-runtime-java) and
 * against upstream okio-jvm 3.18.2 in the isolated oracle module. Both compilations run
 * the same cases. The adapted cases below are the Windows-volume and relative-to vectors
 * that the 2026-09-30 bug-fix pass re-verified against the pin (rootLength's volume
 * letter check, the volumeLetter property, and commonRelativeTo's null-safe root
 * equality); inputs, expected values, and error paths come from the pinned upstream
 * tests and implementation verbatim. Construction spelling (ledger R11): the port
 * exposes the Java static Path.get(String) while upstream declares String.toPath() as a
 * companion extension carrying @JvmStatic/@JvmName("get"), which Kotlin resolves only
 * under its declaration name; the one spelling both sides expose is the JVM static
 * bridge, reached here through reflection in the local path() helper. Property spellings
 * (root, parent, volumeLetter, isAbsolute, isRoot, segments) resolve against the port's
 * Java getters and upstream's Kotlin properties alike.
 */
package okio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PathTest {

  /** Test-only construction spelling compiled by both implementations (ledger R11). */
  private fun path(s: String): Path {
    return Path::class.java.getMethod("get", String::class.java).invoke(null, s) as Path
  }

  private fun assertRelativeTo(a: Path, b: Path, expected: Path) {
    assertEquals(expected, a.relativeTo(b))
  }

  @Test fun windowsVolumeLetter() {
    val path = path("C:\\")
    assertEquals(path, path.normalized())
    assertEquals(path("C:\\"), path.root)
    assertEquals(listOf<String>(), path.segments)
    assertEquals("C:\\", path.toString())
    assertNull(path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test fun windowsAbsolutePathWithVolumeLetter() {
    val path = path("C:\\Windows\\notepad.exe")
    assertEquals(path, path.normalized())
    assertEquals(path("C:\\"), path.root)
    assertEquals(listOf("Windows", "notepad.exe"), path.segments)
    assertEquals("C:\\Windows\\notepad.exe", path.toString())
    assertEquals(path("C:\\Windows"), path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertTrue(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test fun windowsRelativePathWithVolumeLetter() {
    val path = path("C:Windows\\notepad.exe")
    assertEquals(path, path.normalized())
    assertNull(path.root)
    assertEquals(listOf("C:Windows", "notepad.exe"), path.segments)
    assertEquals("C:Windows\\notepad.exe", path.toString())
    assertEquals(path("C:Windows"), path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  /**
   * Bug-fix regression (2026-09-30): rootLength's volume-letter check missed its De
   * Morgan parentheses in the port, so these paths parsed as relative with no root and no
   * volume letter (internal/Path.kt:97). Expectations verified against the pinned
   * artifact, including the non-letter negative case.
   */
  @Test fun windowsVolumeRootParsing() {
    val path = path("C:\\Windows\\file.txt")
    assertTrue(path.isAbsolute)
    assertFalse(path.isRelative)
    assertEquals(path("C:\\"), path.root)
    assertEquals('C', path.volumeLetter)
    assertEquals(path("C:\\a"), path("C:\\a\\b").parent)

    val nonLetter = path("1:\\x")
    assertFalse(nonLetter.isAbsolute)
    assertNull(nonLetter.root)
    assertNull(nonLetter.volumeLetter)
  }

  /**
   * Bug-fix regression (2026-09-30): commonResolve returns a child that is absolute or
   * carries a volume letter unchanged (internal/Path.kt:206); the port reaches that
   * branch through getVolumeLetter, which the same fix repaired.
   */
  @Test fun resolveOnWindowsVolumeRoot() {
    assertEquals(path("C:\\Windows"), path("C:\\").resolve("Windows"))
    assertEquals(path("C:temp"), path("C:\\").resolve(path("C:temp")))
  }

  /**
   * Adapted from PathTest.relativeToRelative (commonTest PathTest.kt:646) plus the exact
   * regression vector of the 2026-09-30 fix: commonRelativeTo's require(root ==
   * other.root) is null-safe structural equality, so two rootless paths compare equal and
   * the relative path resolves instead of crashing on a null root
   * (internal/Path.kt:221-222).
   */
  @Test fun relativeToRelative() {
    assertRelativeTo(path("a/b"), path("a"), path("b"))
    assertRelativeTo(path("a/b/c"), path("a"), path("b/c"))
    assertRelativeTo(path("a"), path("a/b"), path(".."))

    val c = path("documents/resume.txt")
    val d = path("downloads/2021/taxes.txt")
    assertRelativeTo(c, d, path("../../../documents/resume.txt"))
    assertRelativeTo(d, c, path("../../downloads/2021/taxes.txt"))
  }
}
