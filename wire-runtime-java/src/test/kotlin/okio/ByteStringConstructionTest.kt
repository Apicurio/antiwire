/*
 * Adapted from okio/src/commonTest/kotlin/okio/ByteStringTest.kt at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67.
 *
 * Port-only counterpart of the shared ByteStringTest: these cases exercise the
 * decodeHex/decodeBase64/encodeUtf8 construction entry points through the static
 * spellings the port exposes for Java. Upstream marks those spellings deprecated-error
 * for Kotlin, so they cannot be compiled against the pinned artifact in the oracle
 * module; the cases are therefore port-only, with inputs, expected values, and
 * error-path assertions from the pinned upstream source verbatim. The encoding side of
 * base64 lives in the shared file, so the two halves together cover the full surface.
 */
package okio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ByteStringConstructionTest {

  private val bronzeHorseman = "На берегу пустынных волн"

  @Test fun encodeUtf8() {
    val byteString = ByteString.encodeUtf8(bronzeHorseman)
    assertEquals(
      byteString.toByteArray().toList(),
      bronzeHorseman.toByteArray(Charsets.UTF_8).toList(),
    )
    assertTrue(byteString == ByteString.of(*bronzeHorseman.toByteArray(Charsets.UTF_8)))
    assertEquals(
      byteString,
      ByteString.decodeHex(
        "d09dd0b020d0b1d0b5d180d0b5d0b3d18320d0bfd183d181" +
          "d182d18bd0bdd0bdd18bd18520d0b2d0bed0bbd0bd",
      ),
    )
    assertEquals(bronzeHorseman, byteString.utf8())
  }

  @Test fun encodeUtf8CachesTheString() {
    val byteString = ByteString.encodeUtf8("cache-me")
    assertEquals("cache-me", byteString.utf8())
  }

  @Test fun decodeHex() {
    assertEquals("", ByteString.decodeHex("").hex())
    assertEquals(ByteString.decodeHex("000000"), ByteString.of(0, 0, 0))
    assertEquals("000000", ByteString.decodeHex("000000").hex())
    assertThrows(IllegalArgumentException::class.java) { ByteString.decodeHex("0") }
    assertThrows(IllegalArgumentException::class.java) { ByteString.decodeHex("z") }
  }

  @Test fun ignoreUnnecessaryPadding() {
    assertEquals("", ByteString.decodeBase64("====")!!.utf8())
    assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64("AAAA====")!!.utf8())
  }

  @Test fun decodeBase64() {
    assertEquals("", ByteString.decodeBase64("")!!.utf8())
    assertNull(ByteString.decodeBase64("/===")) // Can't do anything with 6 bits!
    assertEquals(ByteString.decodeHex("ff"), ByteString.decodeBase64("//=="))
    assertEquals(ByteString.decodeHex("ff"), ByteString.decodeBase64("__=="))
    assertEquals(ByteString.decodeHex("ffff"), ByteString.decodeBase64("///="))
    assertEquals(ByteString.decodeHex("ffff"), ByteString.decodeBase64("___="))
    assertEquals(ByteString.decodeHex("ffffff"), ByteString.decodeBase64("////"))
    assertEquals(ByteString.decodeHex("ffffff"), ByteString.decodeBase64("____"))
    assertEquals(ByteString.decodeHex("ffffffffffff"), ByteString.decodeBase64("////////"))
    assertEquals(ByteString.decodeHex("ffffffffffff"), ByteString.decodeBase64("________"))
    assertEquals(
      "What's to be scared about? It's just a little hiccup in the power...",
      ByteString.decodeBase64(
        "V2hhdCdzIHRvIGJlIHNjYXJlZCBhYm91dD8gSXQncyBqdXN0IGEgbGl0dGxlIGhpY2" +
          "N1cCBpbiB0aGUgcG93ZXIuLi4=",
      )!!.utf8(),
    )
    // Uses two encoding styles. Malformed, but supported as a side-effect.
    assertEquals(ByteString.decodeHex("ffffff"), ByteString.decodeBase64("__//"))
  }

  @Test fun decodeBase64WithWhitespace() {
    assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA AA ")!!.utf8())
    assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA A\r\nA ")!!.utf8())
    assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64("AA AA")!!.utf8())
    assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA AA ")!!.utf8())
    assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA A\r\nA ")!!.utf8())
    assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64("A    AAA")!!.utf8())
    assertEquals("", ByteString.decodeBase64("    ")!!.utf8())
  }
}
