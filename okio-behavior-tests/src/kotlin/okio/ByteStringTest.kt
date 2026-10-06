/*
 * Adapted from okio/src/commonTest/kotlin/okio/ByteStringTest.kt at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67.
 *
 * This file is compiled twice: against the port's okio package (wire-runtime-java) and
 * against upstream okio-jvm 3.18.2 in the isolated oracle module. Both compilations run
 * the same cases. Inputs, scenarios, expected values, and error-path assertions come from
 * the pinned upstream source verbatim; only the mechanical adaptations recorded in
 * docs/decisions.md OPEN-1 and the adaptation ledger are applied:
 *  - the Burst factory parameter is dropped; every case runs on the plain ByteString
 *    implementation (the factory matrix covers multiplatform variants the port does not
 *    ship; SegmentedByteString is covered by the BufferTest snapshot cases);
 *  - kotlin.test assertions become JUnit 5 assertions;
 *  - call spellings are the forms both sides compile: upstream inputs written as
 *    decodeHex("ab12") are constructed by the local hex() helper, which decodes the same
 *    hex digits into of(...) bytes (ledger row R6); encodeUtf8 inputs become the local
 *    utf8() helper over of(...) with String.getBytes(UTF_8); the size property replaces
 *    size(); the index operator resolves because the port declares get(index);
 *  - cases that exercise the decodeHex/decodeBase64/encodeUtf8 construction entry
 *    points themselves run port-only (ByteStringConstructionTest in wire-runtime-java):
 *    upstream marks the static spellings deprecated-error for Kotlin, so they cannot be
 *    compiled against the pinned artifact.
 * Case selection: hashing (testHash) is not adapted because the M0 subset excludes the
 * digest surface (no wire main or wire test use at wire tag 7.1.0). The
 * toStringOnLongTextIsTruncated case joined the shared suite after the port's
 * ByteString.codePointIndexToCharIndex gained the missing four-byte UTF-8 branch
 * (TASK-4 scope fix, 2026-09-30; see the adaptation ledger notes).
 */
package okio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ByteStringTest {

  private val bronzeHorseman = "На берегу пустынных волн"

  /** Test-only hex construction so upstream hex inputs stay hex in this file (ledger R6). */
  private fun hex(s: String): ByteString {
    val bytes = ByteArray(s.length / 2) { i ->
      ((Character.digit(s[i * 2], 16) shl 4) or Character.digit(s[i * 2 + 1], 16)).toByte()
    }
    return ByteString.of(*bytes)
  }

  private fun utf8(s: String): ByteString = ByteString.of(*s.toByteArray(Charsets.UTF_8))

  @Test fun get() {
    val actual = utf8("abc")
    assertEquals(3, actual.size)
    assertEquals(actual[0], 'a'.code.toByte())
    assertEquals(actual[1], 'b'.code.toByte())
    assertEquals(actual[2], 'c'.code.toByte())
    assertThrows(IndexOutOfBoundsException::class.java) { actual[-1] }
    assertThrows(IndexOutOfBoundsException::class.java) { actual[3] }
  }

  @Test fun getByte() {
    val byteString = hex("ab12")
    assertEquals(-85L, byteString[0].toLong())
    assertEquals(18L, byteString[1].toLong())
  }

  @Test fun startsWithByteString() {
    val byteString = hex("112233")
    assertTrue(byteString.startsWith(hex("")))
    assertTrue(byteString.startsWith(hex("11")))
    assertTrue(byteString.startsWith(hex("1122")))
    assertTrue(byteString.startsWith(hex("112233")))
    assertFalse(byteString.startsWith(hex("2233")))
    assertFalse(byteString.startsWith(hex("11223344")))
    assertFalse(byteString.startsWith(hex("112244")))
  }

  @Test fun endsWithByteString() {
    val byteString = hex("112233")
    assertTrue(byteString.endsWith(hex("")))
    assertTrue(byteString.endsWith(hex("33")))
    assertTrue(byteString.endsWith(hex("2233")))
    assertTrue(byteString.endsWith(hex("112233")))
    assertFalse(byteString.endsWith(hex("1122")))
    assertFalse(byteString.endsWith(hex("00112233")))
    assertFalse(byteString.endsWith(hex("002233")))
  }

  @Test fun indexOfByteString() {
    val byteString = hex("112233")
    assertEquals(0L, byteString.indexOf(hex("112233")).toLong())
    assertEquals(0L, byteString.indexOf(hex("1122")).toLong())
    assertEquals(0L, byteString.indexOf(hex("11")).toLong())
    assertEquals(0L, byteString.indexOf(hex("11"), 0).toLong())
    assertEquals(0L, byteString.indexOf(hex("")).toLong())
    assertEquals(0L, byteString.indexOf(hex(""), 0).toLong())
    assertEquals(1L, byteString.indexOf(hex("2233")).toLong())
    assertEquals(1L, byteString.indexOf(hex("22")).toLong())
    assertEquals(1L, byteString.indexOf(hex("22"), 1).toLong())
    assertEquals(1L, byteString.indexOf(hex(""), 1).toLong())
    assertEquals(2L, byteString.indexOf(hex("33")).toLong())
    assertEquals(2L, byteString.indexOf(hex("33"), 2).toLong())
    assertEquals(2L, byteString.indexOf(hex(""), 2).toLong())
    assertEquals(3L, byteString.indexOf(hex(""), 3).toLong())
    assertEquals(-1L, byteString.indexOf(hex("112233"), 1).toLong())
    assertEquals(-1L, byteString.indexOf(hex("44")).toLong())
    assertEquals(-1L, byteString.indexOf(hex("11223344")).toLong())
    assertEquals(-1L, byteString.indexOf(hex("112244")).toLong())
    assertEquals(-1L, byteString.indexOf(hex("2233"), 2).toLong())
    assertEquals(-1L, byteString.indexOf(hex("33"), 3).toLong())
    assertEquals(-1L, byteString.indexOf(hex(""), 4).toLong())
  }

  @Test fun indexOfWithOffset() {
    val byteString = hex("112233112233")
    assertEquals(0L, byteString.indexOf(hex("112233"), -1).toLong())
    assertEquals(0L, byteString.indexOf(hex("112233"), 0).toLong())
    assertEquals(0L, byteString.indexOf(hex("112233")).toLong())
    assertEquals(3L, byteString.indexOf(hex("112233"), 1).toLong())
    assertEquals(3L, byteString.indexOf(hex("112233"), 2).toLong())
    assertEquals(3L, byteString.indexOf(hex("112233"), 3).toLong())
    assertEquals(-1L, byteString.indexOf(hex("112233"), 4).toLong())
  }

  @Test fun lastIndexOfByteString() {
    val byteString = hex("112233")
    assertEquals(0L, byteString.lastIndexOf(hex("112233")).toLong())
    assertEquals(0L, byteString.lastIndexOf(hex("1122")).toLong())
    assertEquals(0L, byteString.lastIndexOf(hex("11")).toLong())
    assertEquals(0L, byteString.lastIndexOf(hex("11"), 3).toLong())
    assertEquals(0L, byteString.lastIndexOf(hex("11"), 0).toLong())
    assertEquals(0L, byteString.lastIndexOf(hex(""), 0).toLong())
    assertEquals(1L, byteString.lastIndexOf(hex("2233")).toLong())
    assertEquals(1L, byteString.lastIndexOf(hex("22")).toLong())
    assertEquals(1L, byteString.lastIndexOf(hex("22"), 3).toLong())
    assertEquals(1L, byteString.lastIndexOf(hex("22"), 1).toLong())
    assertEquals(1L, byteString.lastIndexOf(hex(""), 1).toLong())
    assertEquals(2L, byteString.lastIndexOf(hex("33")).toLong())
    assertEquals(2L, byteString.lastIndexOf(hex("33"), 3).toLong())
    assertEquals(2L, byteString.lastIndexOf(hex("33"), 2).toLong())
    assertEquals(2L, byteString.lastIndexOf(hex(""), 2).toLong())
    assertEquals(3L, byteString.lastIndexOf(hex(""), 3).toLong())
    assertEquals(3L, byteString.lastIndexOf(hex("")).toLong())
    assertEquals(-1L, byteString.lastIndexOf(hex("112233"), -1).toLong())
    assertEquals(-1L, byteString.lastIndexOf(hex("112233"), -2).toLong())
    assertEquals(-1L, byteString.lastIndexOf(hex("44")).toLong())
    assertEquals(-1L, byteString.lastIndexOf(hex("11223344")).toLong())
    assertEquals(-1L, byteString.lastIndexOf(hex("112244")).toLong())
    assertEquals(-1L, byteString.lastIndexOf(hex("2233"), 0).toLong())
    assertEquals(-1L, byteString.lastIndexOf(hex("33"), 1).toLong())
    assertEquals(-1L, byteString.lastIndexOf(hex(""), -1).toLong())
  }

  @Test fun equalsTest() {
    val byteString = hex("000102")
    assertEquals(byteString, byteString)
    assertEquals(byteString, hex("000102"))
    assertNotEquals(byteString, Any())
    assertNotEquals(byteString, hex("000201"))
  }

  @Test fun equalsEmptyTest() {
    assertEquals(hex(""), ByteString.EMPTY)
    assertEquals(hex(""), ByteString.of())
    assertEquals(ByteString.EMPTY, hex(""))
    assertEquals(ByteString.of(), hex(""))
  }

  @Test fun equalsConstantTime() {
    val byteString = hex("000102")
    assertTrue(byteString.equals(byteString, true))
    assertTrue(byteString.equals(hex("000102"), true))
    assertFalse(byteString.equals(hex("800102"), true))
    assertFalse(byteString.equals(hex("000180"), true))
    assertFalse(byteString.equals(hex("0001"), true))
    assertFalse(byteString.equals(hex("00010203"), true))
  }

  @Test fun equalsConstantTimeEmptyTest() {
    assertTrue(hex("").equals(ByteString.EMPTY, true))
    assertFalse(hex("").equals(hex("00"), true))
  }

  @Test fun equalsNotConstantTime() {
    val byteString = hex("000102")
    assertTrue(byteString.equals(byteString, false))
    assertTrue(byteString.equals(hex("000102"), false))
    assertFalse(byteString.equals(hex("800102"), false))
    assertFalse(byteString.equals(hex("000180"), false))
    assertFalse(byteString.equals(hex("0001"), false))
    assertFalse(byteString.equals(hex("00010203"), false))
  }

  @Test fun utf8() {
    val byteString = utf8(bronzeHorseman)
    assertEquals(
      byteString.toByteArray().toList(),
      bronzeHorseman.toByteArray(Charsets.UTF_8).toList(),
    )
    assertTrue(byteString == ByteString.of(*bronzeHorseman.toByteArray(Charsets.UTF_8)))
    assertEquals(
      byteString,
      hex(
        "d09dd0b020d0b1d0b5d180d0b5d0b3d18320d0bfd183d181" +
          "d182d18bd0bdd0bdd18bd18520d0b2d0bed0bbd0bd",
      ),
    )
    assertEquals(bronzeHorseman, byteString.utf8())
  }

  @Test fun testHashCode() {
    val byteString = hex("0102")
    assertEquals(byteString.hashCode().toLong(), byteString.hashCode().toLong())
    assertEquals(byteString.hashCode().toLong(), hex("0102").hashCode().toLong())
  }

  @Test fun toAsciiLowerCaseNoUppercase() {
    val s = utf8("a1_+")
    assertEquals(s, s.toAsciiLowercase())
    assertSame(s, s.toAsciiLowercase())
  }

  @Test fun toAsciiAllUppercase() {
    assertEquals(utf8("ab"), utf8("AB").toAsciiLowercase())
  }

  @Test fun substring() {
    val byteString = utf8("Hello, World!")

    assertEquals(byteString, byteString.substring(0))
    assertEquals(utf8("Hello"), byteString.substring(0, 5))
    assertEquals(utf8("World!"), byteString.substring(7))
    assertEquals(utf8(""), byteString.substring(6, 6))
  }

  @Test fun substringWithInvalidBounds() {
    val byteString = utf8("Hello, World!")

    assertThrows(IllegalArgumentException::class.java) { byteString.substring(-1) }
    assertThrows(IllegalArgumentException::class.java) { byteString.substring(0, 14) }
    assertThrows(IllegalArgumentException::class.java) { byteString.substring(8, 7) }
  }

  @Test fun encodeBase64() {
    assertEquals("", utf8("").base64())
    assertEquals("AA==", utf8("\u0000").base64())
    assertEquals("AAA=", utf8("\u0000\u0000").base64())
    assertEquals("AAAA", utf8("\u0000\u0000\u0000").base64())
    assertEquals(
      "SG93IG1hbnkgbGluZXMgb2YgY29kZSBhcmUgdGhlcmU/ICdib3V0IDIgbWlsbGlvbi4=",
      utf8("How many lines of code are there? 'bout 2 million.").base64(),
    )
  }

  @Test fun encodeBase64WithoutPadding() {
    assertEquals("", utf8("").base64(false))
    assertEquals("AA", utf8("\u0000").base64(false))
    assertEquals("AAA", utf8("\u0000\u0000").base64(false))
    assertEquals("AAAA", utf8("\u0000\u0000\u0000").base64(false))
    assertEquals(
      "SG93IG1hbnkgbGluZXMgb2YgY29kZSBhcmUgdGhlcmU/ICdib3V0IDIgbWlsbGlvbi4",
      utf8("How many lines of code are there? 'bout 2 million.").base64(false),
    )
  }

  @Test fun encodeBase64Url() {
    assertEquals("", utf8("").base64Url())
    assertEquals("AA==", utf8("\u0000").base64Url())
    assertEquals("AAA=", utf8("\u0000\u0000").base64Url())
    assertEquals("AAAA", utf8("\u0000\u0000\u0000").base64Url())
    assertEquals(
      "SG93IG1hbnkgbGluZXMgb2YgY29kZSBhcmUgdGhlcmU_ICdib3V0IDIgbWlsbGlvbi4=",
      utf8("How many lines of code are there? 'bout 2 million.").base64Url(),
    )
  }

  @Test fun encodeBase64UrlWithoutPadding() {
    assertEquals("", utf8("").base64Url(false))
    assertEquals("AA", utf8("\u0000").base64Url(false))
    assertEquals("AAA", utf8("\u0000\u0000").base64Url(false))
    assertEquals("AAAA", utf8("\u0000\u0000\u0000").base64Url(false))
    assertEquals(
      "SG93IG1hbnkgbGluZXMgb2YgY29kZSBhcmUgdGhlcmU_ICdib3V0IDIgbWlsbGlvbi4",
      utf8("How many lines of code are there? 'bout 2 million.").base64Url(false),
    )
  }

  @Test fun toStringOnEmpty() {
    assertEquals("[size=0]", utf8("").toString())
  }

  @Test fun toStringOnShortText() {
    assertEquals("[text=Tyrannosaur]", utf8("Tyrannosaur").toString())
  }

  @Test fun toStringOnLongTextIsTruncated() {
    val raw = (
      "Um, I'll tell you the problem with the scientific power that you're using here, " +
        "it didn't require any discipline to attain it. You read what others had done and you " +
        "took the next step. You didn't earn the knowledge for yourselves, so you don't take any " +
        "responsibility for it. You stood on the shoulders of geniuses to accomplish something " +
        "as fast as you could, and before you even knew what you had, you patented it, and " +
        "packaged it, and slapped it on a plastic lunchbox, and now you're selling it, you wanna " +
        "sell it."
      )
    assertEquals(
      "[size=517 text=Um, I'll tell you the problem with the scientific power that " +
        "you…]",
      utf8(raw).toString(),
    )
    val war = (
      "Սｍ, I'll 𝓽𝖾ll ᶌօ𝘂 ᴛℎ℮ 𝜚𝕣०ｂl𝖾ｍ ｗі𝕥𝒽 𝘵𝘩𝐞 𝓼𝙘𝐢𝔢𝓷𝗍𝜄𝚏𝑖ｃ 𝛠𝝾ｗ𝚎𝑟 𝕥ｈ⍺𝞃 𝛄𝓸𝘂'𝒓𝗲 υ𝖘𝓲𝗇ɡ 𝕙𝚎𝑟ｅ, " +
        "𝛊𝓽 ⅆ𝕚𝐝𝝿'𝗍 𝔯𝙚𝙦ᴜ𝜾𝒓𝘦 𝔞𝘯𝐲 ԁ𝜄𝑠𝚌ι𝘱lι𝒏ｅ 𝑡𝜎 𝕒𝚝𝖙𝓪і𝞹 𝔦𝚝. 𝒀ο𝗎 𝔯𝑒⍺𝖉 ｗ𝐡𝝰𝔱 𝞂𝞽һ𝓮𝓇ƽ հ𝖺𝖉 ⅾ𝛐𝝅ⅇ 𝝰πԁ 𝔂ᴑᴜ 𝓉ﮨ၀𝚔 " +
        "т𝒽𝑒 𝗇𝕖ⅹ𝚝 𝔰𝒕е𝓅. 𝘠ⲟ𝖚 𝖉ⅰԁ𝝕'τ 𝙚𝚊ｒ𝞹 𝘵Ꮒ𝖾 𝝒𝐧هｗl𝑒𝖉ƍ𝙚 𝓯૦ｒ 𝔂𝞼𝒖𝕣𝑠𝕖l𝙫𝖊𝓼, 𐑈о ｙ𝘰𝒖 ⅆە𝗇'ｔ 𝜏α𝒌𝕖 𝛂𝟉ℽ " +
        "𝐫ⅇ𝗌ⲣ๐ϖ𝖘ꙇᖯ𝓲l𝓲𝒕𝘆 𝐟𝞼𝘳 𝚤𝑡. 𝛶𝛔𝔲 ｓ𝕥σσ𝐝 ﮩ𝕟 𝒕𝗁𝔢 𝘴𝐡𝜎ᴜlⅾ𝓮𝔯𝚜 𝛐𝙛 ᶃ𝚎ᴨᎥս𝚜𝘦𝓈 𝓽𝞸 ａ𝒄𝚌𝞸ｍρl𝛊ꜱ𝐡 𝓈𝚘ｍ𝚎𝞃𝔥⍳𝞹𝔤 𝐚𝗌 𝖋ａ𝐬𝒕 " +
        "αｓ γ𝛐𝕦 𝔠ﻫ𝛖lԁ, 𝚊π𝑑 Ь𝑒𝙛૦𝓇𝘦 𝓎٥𝖚 ⅇｖℯ𝝅 𝜅ո𝒆ｗ ｗ𝗵𝒂𝘁 ᶌ੦𝗎 ｈ𝐚𝗱, 𝜸ﮨ𝒖 𝓹𝝰𝔱𝖾𝗇𝓽𝔢ⅆ і𝕥, 𝚊𝜛𝓭 𝓹𝖺ⅽϰ𝘢ℊеᏧ 𝑖𝞃, " +
        "𝐚𝛑ꓒ 𝙨l𝔞р𝘱𝔢𝓭 ɩ𝗍 ہ𝛑 𝕒 ｐl𝛂ѕᴛ𝗂𝐜 l𝞄ℼ𝔠𝒽𝑏ﮪ⨯, 𝔞ϖ𝒹 ｎ𝛔ｗ 𝛾𝐨𝞄'𝗿𝔢 ꜱ℮ll𝙞ｎɡ ɩ𝘁, 𝙮𝕠𝛖 ｗ𝑎ℼ𝚗𝛂 𝕤𝓮ll 𝙞𝓉."
      )
    assertEquals(
      "[size=1496 text=Սｍ, I'll 𝓽𝖾ll ᶌօ𝘂 ᴛℎ℮ 𝜚𝕣०ｂl𝖾ｍ ｗі𝕥𝒽 𝘵𝘩𝐞 𝓼𝙘𝐢𝔢𝓷𝗍𝜄𝚏𝑖ｃ 𝛠𝝾ｗ𝚎𝑟 𝕥ｈ⍺𝞃 " +
        "𝛄𝓸𝘂…]",
      utf8(war).toString(),
    )
  }

  /**
   * The four cases below pin the toString verdicts of the upstream UTF-8 decoder for the
   * inputs where the port's former inline scan diverged (2026-09-30 bug-fix pass):
   * overlong and surrogate encodings yield the replacement code point in
   * process2/3Utf8Bytes, so the scan returns -1 and toString renders hex, while a
   * malformed byte landing exactly at the 64-code-point cut still renders text because
   * the closure checks j++ == codePointCount before the replacement check
   * (Utf8.kt processUtf8CodePoints). Expectations verified against the pinned artifact.
   */
  @Test fun toStringOnOverlongTwoByteEncodingIsHex() {
    assertEquals("[hex=c180]", hex("c180").toString())
  }

  @Test fun toStringOnOverlongThreeByteEncodingIsHex() {
    assertEquals("[hex=e080bf]", hex("e080bf").toString())
  }

  @Test fun toStringOnThreeByteSurrogateEncodingIsHex() {
    assertEquals("[hex=eda080]", hex("eda080").toString())
  }

  @Test fun toStringOnMalformedByteAtCodePointCutIsText() {
    val bytes = ByteArray(65) { i -> if (i < 64) 'a'.code.toByte() else 0x80.toByte() }
    assertEquals("[size=65 text=" + "a".repeat(64) + "…]", ByteString.of(*bytes).toString())
  }

  @Test fun compareToSingleBytes() {
    val originalByteStrings = listOf(
      hex("00"),
      hex("01"),
      hex("7e"),
      hex("7f"),
      hex("80"),
      hex("81"),
      hex("fe"),
      hex("ff"),
    )

    val sortedByteStrings = originalByteStrings.toMutableList()
    sortedByteStrings.shuffle(kotlin.random.Random(0))
    assertNotEquals(originalByteStrings, sortedByteStrings)

    sortedByteStrings.sort()
    assertEquals(originalByteStrings, sortedByteStrings)
  }

  @Test fun compareToMultipleBytes() {
    val originalByteStrings = listOf(
      hex(""),
      hex("00"),
      hex("0000"),
      hex("000000"),
      hex("00000000"),
      hex("0000000000"),
      hex("0000000001"),
      hex("000001"),
      hex("00007f"),
      hex("0000ff"),
      hex("000100"),
      hex("000101"),
      hex("007f00"),
      hex("00ff00"),
      hex("010000"),
      hex("010001"),
      hex("01007f"),
      hex("0100ff"),
      hex("010100"),
      hex("01010000"),
      hex("0101000000"),
      hex("0101000001"),
      hex("010101"),
      hex("7f0000"),
      hex("7f0000ffff"),
      hex("ffffff"),
    )

    val sortedByteStrings = originalByteStrings.toMutableList()
    sortedByteStrings.shuffle(kotlin.random.Random(0))
    assertNotEquals(originalByteStrings, sortedByteStrings)

    sortedByteStrings.sort()
    assertEquals(originalByteStrings, sortedByteStrings)
  }

  @Test fun copyInto() {
    val byteString = utf8("abcdefgh")
    val byteArray = "WwwwXxxxYyyyZzzz".toByteArray(Charsets.UTF_8)
    byteString.copyInto(0, byteArray, 0, 5)
    assertEquals("abcdexxxYyyyZzzz", String(byteArray, Charsets.UTF_8))
  }

  @Test fun copyIntoFullRange() {
    val byteString = utf8("abcdefgh")
    val byteArray = "WwwwXxxxYyyyZzzz".toByteArray(Charsets.UTF_8)
    byteString.copyInto(0, byteArray, 0, byteString.size)
    assertEquals("abcdefgh", String(byteArray, 0, 8, Charsets.UTF_8))
  }

  @Test fun copyIntoWithTargetOffset() {
    val byteString = utf8("abcdefgh")
    val byteArray = "WwwwXxxxYyyyZzzz".toByteArray(Charsets.UTF_8)
    byteString.copyInto(0, byteArray, 11, 5)
    assertEquals("WwwwXxxxYyyabcde", String(byteArray, Charsets.UTF_8))
  }

  @Test fun startsWithByteArray() {
    val byteString = hex("112233")
    assertTrue(byteString.startsWith(hex("").toByteArray()))
    assertTrue(byteString.startsWith(hex("11").toByteArray()))
    assertTrue(byteString.startsWith(hex("1122").toByteArray()))
    assertTrue(byteString.startsWith(hex("112233").toByteArray()))
    assertFalse(byteString.startsWith(hex("2233").toByteArray()))
    assertFalse(byteString.startsWith(hex("11223344").toByteArray()))
    assertFalse(byteString.startsWith(hex("112244").toByteArray()))
  }

  @Test fun endsWithByteArray() {
    val byteString = hex("112233")
    assertTrue(byteString.endsWith(hex("").toByteArray()))
    assertTrue(byteString.endsWith(hex("33").toByteArray()))
    assertTrue(byteString.endsWith(hex("2233").toByteArray()))
    assertTrue(byteString.endsWith(hex("112233").toByteArray()))
    assertFalse(byteString.endsWith(hex("1122").toByteArray()))
    assertFalse(byteString.endsWith(hex("00112233").toByteArray()))
    assertFalse(byteString.endsWith(hex("002233").toByteArray()))
  }

  @Test fun indexOfByteArray() {
    val byteString = hex("112233")
    assertEquals(0L, byteString.indexOf(hex("112233").toByteArray()).toLong())
    assertEquals(1L, byteString.indexOf(hex("2233").toByteArray()).toLong())
    assertEquals(2L, byteString.indexOf(hex("33").toByteArray()).toLong())
    assertEquals(-1L, byteString.indexOf(hex("112244").toByteArray()).toLong())
  }

  @Test fun lastIndexOfByteArray() {
    val byteString = hex("112233")
    assertEquals(0L, byteString.lastIndexOf(hex("112233").toByteArray()).toLong())
    assertEquals(1L, byteString.lastIndexOf(hex("2233").toByteArray()).toLong())
    assertEquals(2L, byteString.lastIndexOf(hex("33").toByteArray()).toLong())
    assertEquals(3L, byteString.lastIndexOf(hex("").toByteArray()).toLong())
  }

  @Test fun toAsciiStartsLowercaseEndsUppercase() {
    assertEquals(utf8("abcd"), utf8("abCD").toAsciiLowercase())
  }

  @Test fun toAsciiStartsUppercaseEndsLowercase() {
    assertEquals(utf8("ABCD"), utf8("ABcd").toAsciiUppercase())
  }

  @Test fun toStringOnTextWithNewlines() {
    // Instead of emitting a literal newline in the toString(), these are escaped as "\n".
    assertEquals(
      "[text=a\\r\\nb\\nc\\rd\\\\e]",
      utf8("a\r\nb\nc\rd\\e").toString(),
    )
  }

  @Test fun toStringOnData() {
    val byteString = hex(
      "" +
        "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55" +
        "4bf0b54023c29b624de9ef9c2f931efc580f9afb",
    )
    assertEquals(
      "[hex=" +
        "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55" +
        "4bf0b54023c29b624de9ef9c2f931efc580f9afb]",
      byteString.toString(),
    )
  }

  @Test fun toStringOnLongDataIsTruncated() {
    val byteString = hex(
      "" +
        "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55" +
        "4bf0b54023c29b624de9ef9c2f931efc580f9afba1",
    )
    assertEquals(
      "[size=65 hex=" +
        "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55" +
        "4bf0b54023c29b624de9ef9c2f931efc580f9afb…]",
      byteString.toString(),
    )
  }

  @Test fun copyIntoWithSourceOffset() {
    val byteString = utf8("abcdefgh")
    val byteArray = "WwwwXxxxYyyyZzzz".toByteArray(Charsets.UTF_8)
    byteString.copyInto(3, byteArray, 0, 5)
    assertEquals("defghxxxYyyyZzzz", String(byteArray, Charsets.UTF_8))
  }

  @Test fun copyIntoWithAllParameters() {
    val byteString = utf8("abcdefgh")
    val byteArray = "WwwwXxxxYyyyZzzz".toByteArray(Charsets.UTF_8)
    byteString.copyInto(3, byteArray, 11, 5)
    assertEquals("WwwwXxxxYyydefgh", String(byteArray, Charsets.UTF_8))
  }

  @Test fun copyIntoBoundsChecks() {
    val byteString = utf8("abcdefgh")
    val byteArray = "WwwwXxxxYyyyZzzz".toByteArray(Charsets.UTF_8)
    assertThrows(IndexOutOfBoundsException::class.java) {
      byteString.copyInto(-1, byteArray, 1, 1)
    }
    assertThrows(IndexOutOfBoundsException::class.java) {
      byteString.copyInto(9, byteArray, 0, 0)
    }
    assertThrows(IndexOutOfBoundsException::class.java) {
      byteString.copyInto(1, byteArray, -1, 1)
    }
    assertThrows(IndexOutOfBoundsException::class.java) {
      byteString.copyInto(1, byteArray, 17, 1)
    }
    assertThrows(IndexOutOfBoundsException::class.java) {
      byteString.copyInto(7, byteArray, 1, 2)
    }
    assertThrows(IndexOutOfBoundsException::class.java) {
      byteString.copyInto(1, byteArray, 15, 2)
    }
  }

  @Test fun copyEmptyAtBounds() {
    val byteString = utf8("abcdefgh")
    val byteArray = "WwwwXxxxYyyyZzzz".toByteArray(Charsets.UTF_8)
    byteString.copyInto(0, byteArray, 0, 0)
    assertEquals("WwwwXxxxYyyyZzzz", String(byteArray, Charsets.UTF_8))
    byteString.copyInto(0, byteArray, 16, 0)
    assertEquals("WwwwXxxxYyyyZzzz", String(byteArray, Charsets.UTF_8))
    byteString.copyInto(8, byteArray, 0, 0)
    assertEquals("WwwwXxxxYyyyZzzz", String(byteArray, Charsets.UTF_8))
  }

  @Test fun ofCopy() {
    val bytes = "Hello, World!".toByteArray(Charsets.UTF_8)
    val byteString = ByteString.of(*bytes)
    // Verify that the bytes were copied out.
    bytes[4] = 'a'.code.toByte()
    assertEquals("Hello, World!", byteString.utf8())
  }

  @Test fun ofCopyRange() {
    val bytes = "Hello, World!".toByteArray(Charsets.UTF_8)
    val byteString = ByteString.of(*bytes.copyOfRange(2, 2 + 9))
    // Verify that the bytes were copied out.
    bytes[4] = 'a'.code.toByte()
    assertEquals("llo, Worl", byteString.utf8())
  }

  @Test fun getByteOutOfBounds() {
    val byteString = hex("ab12")
    assertThrows(IndexOutOfBoundsException::class.java) { byteString[2] }
  }
}
