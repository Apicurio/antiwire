/*
 * Adapted from okio/src/commonTest/kotlin/okio/CommonBufferTest.kt,
 * okio/src/jvmTest/kotlin/okio/BufferedSourceTest.kt, and the shared test support of
 * okio/src/commonTest/kotlin/okio/OkioTesting.kt and MockSink.kt at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67.
 *
 * This file is compiled twice: against the port's okio package (wire-runtime-java) and
 * against upstream okio-jvm 3.18.2 in the isolated oracle module. Both compilations run
 * the same cases. Inputs, scenarios, expected values, and error-path assertions come from
 * the pinned upstream sources verbatim; only the mechanical adaptations recorded in
 * docs/decisions.md OPEN-1 and the adaptation ledger are applied (kotlin.test to JUnit 5,
 * BufferUnsizedSource-style factories dropped for the direct Buffer cases this file
 * selects, and the stream-level readShortLe/readLongLe vectors lifted from
 * BufferedSourceTest onto a Buffer sink and source pair).
 * Case selection: every CommonBufferTest.kt case runs here except fillAndDrainPool,
 * which observes SegmentPool.byteCount/MAX_SIZE and stays port-only in
 * wire-runtime-java/src/test/kotlin/okio/BufferInternalsTest.kt (ledger P2). The
 * segment-layout cases (moveBytesBetweenBuffers family, writeSplitSourceBuffer*,
 * writePrefixDoesntSplit*) read the same per-segment sizes upstream's OkioTesting.kt
 * segmentSizes() helper reads, through a reflection helper over the pinned JVM field
 * names, because Buffer.head and the Segment cursor fields are internal to the upstream
 * Kotlin module and the oracle module is not a friend of it (ledger R10). The
 * unsafe-cursor suite is out of the M0 subset. Where the upstream case references the
 * internal 8192 constant, the shared file uses its pinned value 8192 (ledger
 * row R6). DEPRECATION_ERROR is suppressed because the static construction spellings
 * resolve to the deprecated non-extension companion members against upstream. The
 * snapshotGetOnSegmentedSnapshot and sourceIndexOfWithInvalidRangeThrows cases are
 * bug-fix regressions added in the 2026-09-30 pass: authored expectations, each verified
 * against the pinned artifact before landing (ledger rows cite the upstream lines).
 */
@file:Suppress("DEPRECATION_ERROR")

package okio

import java.io.EOFException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BufferTest {

  /** Test-only construction helpers so upstream inputs keep their spelling (ledger R6). */
  private fun hex(s: String): ByteString {
    val bytes = ByteArray(s.length / 2) { i ->
      ((Character.digit(s[i * 2], 16) shl 4) or Character.digit(s[i * 2 + 1], 16)).toByte()
    }
    return ByteString.of(*bytes)
  }

  private fun utf8(s: String): ByteString = ByteString.of(*s.toByteArray(Charsets.UTF_8))

  @Test fun readAndWriteUtf8() {
    val buffer = Buffer()
    buffer.writeUtf8("ab")
    assertEquals(2L, buffer.size)
    buffer.writeUtf8("cdef")
    assertEquals(6L, buffer.size)
    assertEquals("abcd", buffer.readUtf8(4))
    assertEquals(2L, buffer.size)
    assertEquals("ef", buffer.readUtf8(2))
    assertEquals(0L, buffer.size)
    assertThrows(EOFException::class.java) { buffer.readUtf8(1) }
  }

  /** Buffer's toString is the same as ByteString's. */
  @Test fun bufferToString() {
    assertEquals("[size=0]", Buffer().toString())
    assertEquals(
      "[text=a\\r\\nb\\nc\\rd\\\\e]",
      Buffer().writeUtf8("a\r\nb\nc\rd\\e").toString(),
    )
    assertEquals(
      "[text=Tyrannosaur]",
      Buffer().writeUtf8("Tyrannosaur").toString(),
    )
    assertEquals(
      "[text=təˈranəˌsôr]",
      Buffer().write(hex("74c999cb8872616ec999cb8c73c3b472")).toString(),
    )
    assertEquals(
      "[hex=0000000000000000000000000000000000000000000000000000000000000000000000000000" +
        "0000000000000000000000000000000000000000000000000000]",
      Buffer().write(ByteArray(64)).toString(),
    )
  }

  @Test fun multipleSegmentBuffers() {
    val buffer = Buffer()
    buffer.writeUtf8("a".repeat(1000))
    buffer.writeUtf8("b".repeat(2500))
    buffer.writeUtf8("c".repeat(5000))
    buffer.writeUtf8("d".repeat(10000))
    buffer.writeUtf8("e".repeat(25000))
    buffer.writeUtf8("f".repeat(50000))

    assertEquals("a".repeat(999), buffer.readUtf8(999)) // a...a
    assertEquals("a" + "b".repeat(2500) + "c", buffer.readUtf8(2502)) // ab...bc
    assertEquals("c".repeat(4998), buffer.readUtf8(4998)) // c...c
    assertEquals("c" + "d".repeat(10000) + "e", buffer.readUtf8(10002)) // cd...de
    assertEquals("e".repeat(24998), buffer.readUtf8(24998)) // e...e
    assertEquals("e" + "f".repeat(50000), buffer.readUtf8(50001)) // ef...f
    assertEquals(0L, buffer.size)
  }

  @Test fun writeAllMultipleSegments() {
    val buffer = Buffer().writeUtf8("a".repeat(10 * 1024))
    val sink = Buffer()
    sink.writeAll(buffer)
    assertEquals(0L, buffer.size)
    assertEquals(0L, buffer.readUtf8().length.toLong())
    assertEquals(10 * 1024L, sink.size)
  }

  @Test fun moveAllRequestedBytesWithRead() {
    val sink = Buffer()
    sink.writeUtf8("a".repeat(10))

    val source = Buffer()
    source.writeUtf8("b".repeat(15))

    assertEquals(10L, source.read(sink, 10))
    assertEquals(20L, sink.size)
    assertEquals(5L, source.size)
    assertEquals("a".repeat(10) + "b".repeat(10), sink.readUtf8(20))
  }

  @Test fun moveFewerThanRequestedBytesWithRead() {
    val sink = Buffer()
    sink.writeUtf8("a".repeat(10))

    val source = Buffer()
    source.writeUtf8("b".repeat(20))

    assertEquals(20L, source.read(sink, 25))
    assertEquals(30L, sink.size)
    assertEquals(0L, source.size)
    assertEquals("a".repeat(10) + "b".repeat(20), sink.readUtf8(30))
  }

  @Test fun indexOfWithOffset() {
    val buffer = Buffer()
    val halfSegment = 8192 / 2
    buffer.writeUtf8("a".repeat(halfSegment))
    buffer.writeUtf8("b".repeat(halfSegment))
    buffer.writeUtf8("c".repeat(halfSegment))
    buffer.writeUtf8("d".repeat(halfSegment))
    assertEquals(0L, buffer.indexOf('a'.code.toByte(), 0))
    assertEquals((halfSegment - 1).toLong(), buffer.indexOf('a'.code.toByte(), (halfSegment - 1).toLong()))
    assertEquals(halfSegment.toLong(), buffer.indexOf('b'.code.toByte(), (halfSegment - 1).toLong()))
    assertEquals((halfSegment * 2).toLong(), buffer.indexOf('c'.code.toByte(), (halfSegment - 1).toLong()))
    assertEquals((halfSegment * 3).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment - 1).toLong()))
    assertEquals((halfSegment * 3).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment * 2).toLong()))
    assertEquals((halfSegment * 3).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment * 3).toLong()))
    assertEquals((halfSegment * 4 - 1).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment * 4 - 1).toLong()))
  }

  /**
   * Pins the argument guard of RealBufferedSource.indexOf (internal/RealBufferedSource.kt
   * require(fromIndex in 0L..toIndex)), restored in the 2026-09-30 bug-fix pass; the
   * expectation was verified against the pinned artifact. The source is a Buffer wrapped
   * in a real buffered source so the guard runs where upstream declares it.
   */
  @Test fun sourceIndexOfWithInvalidRangeThrows() {
    val source: BufferedSource = Okio.buffer(Buffer().writeUtf8("hello world") as Source)
    val over = assertThrows(IllegalArgumentException::class.java) {
      source.indexOf('l'.code.toByte(), 10L, 5L)
    }
    assertEquals("fromIndex=10 toIndex=5", over.message)
    val negative = assertThrows(IllegalArgumentException::class.java) {
      source.indexOf('l'.code.toByte(), -1L, 5L)
    }
    assertEquals("fromIndex=-1 toIndex=5", negative.message)
  }

  @Test fun byteAt() {
    val buffer = Buffer()
    buffer.writeUtf8("a")
    buffer.writeUtf8("b".repeat(8192))
    buffer.writeUtf8("c")
    assertEquals('a'.code.toLong(), buffer[0].toLong())
    assertEquals('a'.code.toLong(), buffer[0].toLong()) // getByte doesn't mutate!
    assertEquals('c'.code.toLong(), buffer[buffer.size - 1].toLong())
    assertEquals('b'.code.toLong(), buffer[buffer.size - 2].toLong())
    assertEquals('b'.code.toLong(), buffer[buffer.size - 3].toLong())
  }

  @Test fun getByteOfEmptyBuffer() {
    val buffer = Buffer()
    assertThrows(IndexOutOfBoundsException::class.java) { buffer[0] }
  }

  @Test fun copyTo() {
    val source = Buffer()
    source.writeUtf8("party")

    val target = Buffer()
    source.copyTo(target, 1, 3)

    assertEquals("art", target.readUtf8())
    assertEquals("party", source.readUtf8())
  }

  @Test fun copyToOnSegmentBoundary() {
    val `as` = "a".repeat(8192)
    val bs = "b".repeat(8192)
    val cs = "c".repeat(8192)
    val ds = "d".repeat(8192)

    val source = Buffer()
    source.writeUtf8(`as`)
    source.writeUtf8(bs)
    source.writeUtf8(cs)

    val target = Buffer()
    target.writeUtf8(ds)

    source.copyTo(target, `as`.length.toLong(), (bs.length + cs.length).toLong())
    assertEquals(ds + bs + cs, target.readUtf8())
  }

  @Test fun copyToOffSegmentBoundary() {
    val `as` = "a".repeat(8192 - 1)
    val bs = "b".repeat(8192 + 2)
    val cs = "c".repeat(8192 - 4)
    val ds = "d".repeat(8192 + 8)

    val source = Buffer()
    source.writeUtf8(`as`)
    source.writeUtf8(bs)
    source.writeUtf8(cs)

    val target = Buffer()
    target.writeUtf8(ds)

    source.copyTo(target, `as`.length.toLong(), (bs.length + cs.length).toLong())
    assertEquals(ds + bs + cs, target.readUtf8())
  }

  @Test fun copyToSourceAndTargetCanBeTheSame() {
    val `as` = "a".repeat(8192)
    val bs = "b".repeat(8192)

    val source = Buffer()
    source.writeUtf8(`as`)
    source.writeUtf8(bs)

    source.copyTo(source, 0, source.size)
    assertEquals(`as` + bs + `as` + bs, source.readUtf8())
  }

  @Test fun copyToEmptySource() {
    val source = Buffer()
    val target = Buffer().writeUtf8("aaa")
    source.copyTo(target, 0L, 0L)
    assertEquals("", source.readUtf8())
    assertEquals("aaa", target.readUtf8())
  }

  @Test fun copyToEmptyTarget() {
    val source = Buffer().writeUtf8("aaa")
    val target = Buffer()
    source.copyTo(target, 0L, 3L)
    assertEquals("aaa", source.readUtf8())
    assertEquals("aaa", target.readUtf8())
  }

  @Test fun snapshotReportsAccurateSize() {
    val buf = Buffer().write(byteArrayOf(0, 1, 2, 3))
    assertEquals(1, buf.snapshot(1).size())
  }

  @Test fun snapshotSpansMultipleSegments() {
    // Enough data to span multiple segments, exceeding the segmentation threshold.
    val buffer = Buffer().writeUtf8("x".repeat(64 * 1024))
    val snapshot = buffer.snapshot()
    assertEquals(64 * 1024, snapshot.size())
    assertEquals("x".repeat(64 * 1024), snapshot.utf8())
    assertEquals(snapshot, utf8("x".repeat(64 * 1024)))
    assertEquals(utf8("x".repeat(64 * 1024)), snapshot)
    assertEquals(snapshot.hashCode().toLong(), utf8("x".repeat(64 * 1024)).hashCode().toLong())
    // Taking a snapshot does not consume the buffer.
    assertEquals(64 * 1024L, buffer.size)
  }

  /**
   * A snapshot of a non-empty buffer is segmented on both sides, so the index operator
   * must resolve through the segmented lookup rather than a flat backing array (2026-09-30
   * bug-fix pass; upstream backs both get and getByte with the single internal lookup
   * overridden in SegmentedByteString).
   */
  @Test fun snapshotGetOnSegmentedSnapshot() {
    val snapshot = Buffer().writeUtf8("hello world").snapshot()
    assertEquals('h'.code.toByte(), snapshot[0])
    assertEquals('d'.code.toByte(), snapshot[10])
    assertThrows(IndexOutOfBoundsException::class.java) { snapshot[-1] }
    assertThrows(IndexOutOfBoundsException::class.java) { snapshot[11] }
  }

  @Test fun equalsSpanningSegments() {
    val a = Buffer().writeUtf8("a".repeat(20 * 1024))
    val b = Buffer().writeUtf8("a".repeat(20 * 1024))
    assertTrue(a == b)
    assertEquals(a.hashCode().toLong(), b.hashCode().toLong())
    a.writeUtf8("x")
    assertFalse(a == b)
  }

  @Test fun cloneIsIndependent() {
    val buffer = Buffer().writeUtf8("party")
    val clone = buffer.clone()
    assertEquals("party", clone.readUtf8())
    assertEquals("party", buffer.readUtf8())
  }

  @Test fun readShortLe() {
    val sink = Buffer()
    sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x10.toByte()))
    sink.emit()
    val source = sink.clone()
    assertEquals(0xcdab.toShort().toLong(), source.readShortLe().toLong())
    assertEquals(0x10ef.toShort().toLong(), source.readShortLe().toLong())
    assertTrue(source.exhausted())
  }

  @Test fun readLongLe() {
    val sink = Buffer()
    sink.write(
      byteArrayOf(
        0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x10.toByte(),
        0x87.toByte(), 0x65.toByte(), 0x43.toByte(), 0x21.toByte(),
        0x36.toByte(), 0x47.toByte(), 0x58.toByte(), 0x69.toByte(),
        0x12.toByte(), 0x23.toByte(), 0x34.toByte(), 0x45.toByte(),
      ),
    )
    sink.emit()
    val source = sink.clone()
    assertEquals(0x2143658710efcdabL, source.readLongLe())
    assertEquals(0x4534231269584736L, source.readLongLe())
    assertTrue(source.exhausted())
  }

  @Test fun readIntSplitAcrossMultipleSegments() {
    val buffer = Buffer()
    buffer.writeUtf8("a".repeat(8192 - 3))
    buffer.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x01.toByte()))
    buffer.emit()
    buffer.skip((8192 - 3).toLong())
    assertEquals(-0x543210ffL, buffer.readInt().toLong())
    assertTrue(buffer.exhausted())
  }

  @Test fun writeAndReadVarintLikeDecimalRoundTrip() {
    val buffer = Buffer()
    for (value in listOf(0L, -1L, 1L, Long.MIN_VALUE, Long.MAX_VALUE, 123456789L)) {
      buffer.writeDecimalLong(value)
      buffer.writeUtf8(",")
    }
    for (value in listOf(0L, -1L, 1L, Long.MIN_VALUE, Long.MAX_VALUE, 123456789L)) {
      assertEquals(value, buffer.readDecimalLong())
      assertEquals(",", buffer.readUtf8(1))
    }
    assertTrue(buffer.exhausted())
  }

  @Test fun writeAndReadHexadecimalUnsignedLongRoundTrip() {
    val buffer = Buffer()
    for (value in listOf(0L, -1L, 1L, Long.MIN_VALUE, Long.MAX_VALUE, 0x123456789aL)) {
      buffer.writeHexadecimalUnsignedLong(value)
      buffer.writeUtf8(",")
    }
    for (value in listOf(0L, -1L, 1L, Long.MIN_VALUE, Long.MAX_VALUE, 0x123456789aL)) {
      assertEquals(value, buffer.readHexadecimalUnsignedLong())
      assertEquals(",", buffer.readUtf8(1))
    }
    assertTrue(buffer.exhausted())
  }

  @Test fun select() {
    val options = Options.of(
      utf8("rocket"),
      utf8("rocks"),
      utf8("ran"),
      utf8("rap"),
    )

    val buffer = Buffer().writeUtf8("rocks")
    assertEquals(1, buffer.select(options))
    assertEquals("", buffer.readUtf8())

    val buffer2 = Buffer().writeUtf8("rocket")
    assertEquals(0, buffer2.select(options))
    assertEquals("", buffer2.readUtf8())

    val buffer3 = Buffer().writeUtf8("rar")
    assertEquals(-1, buffer3.select(options))
    assertEquals("rar", buffer3.readUtf8())
  }

  @Test fun requireAndSkip() {
    val buffer = Buffer().writeUtf8("party")
    buffer.require(5)
    assertThrows(EOFException::class.java) { buffer.require(6) }
    buffer.skip(5)
    assertThrows(EOFException::class.java) { buffer.skip(1) }
  }

  @Test fun clearIsEmpty() {
    val buffer = Buffer().writeUtf8("party")
    buffer.clear()
    assertEquals(0L, buffer.size)
    assertTrue(buffer.exhausted())
  }

  @Suppress("ReplaceAssertBooleanWithAssertEquality")
  @Test fun equalsAndHashCodeEmpty() {
    val a = Buffer()
    val b = Buffer()
    assertTrue(a == b)
    assertTrue(a.hashCode() == b.hashCode())
  }

  @Suppress("ReplaceAssertBooleanWithAssertEquality")
  @Test fun equalsAndHashCode() {
    val a = Buffer().writeUtf8("dog")
    val b = Buffer().writeUtf8("hotdog")
    assertFalse(a == b)
    assertFalse(a.hashCode() == b.hashCode())

    b.readUtf8(3) // Leaves b containing 'dog'.
    assertTrue(a == b)
    assertTrue(a.hashCode() == b.hashCode())
  }

  @Suppress("ReplaceAssertBooleanWithAssertEquality")
  @Test fun equalsAndHashCodeSpanningSegments() {
    val data = ByteArray(1024 * 1024)
    val dice = kotlin.random.Random(0)
    dice.nextBytes(data)

    val a = bufferWithRandomSegmentLayout(dice, data)
    val b = bufferWithRandomSegmentLayout(dice, data)
    assertTrue(a == b)
    assertTrue(a.hashCode() == b.hashCode())

    data[data.size / 2]++ // Change a single byte.
    val c = bufferWithRandomSegmentLayout(dice, data)
    assertFalse(a == c)
    assertFalse(a.hashCode() == c.hashCode())
  }

  @Test fun writePrefixToEmptyBuffer() {
    val sink = Buffer()
    val source = Buffer()
    source.writeUtf8("abcd")
    sink.write(source, 2)
    assertEquals("ab", sink.readUtf8(2))
  }

  @Test fun writePrefixDoesntSplit() {
    val sink = Buffer()
    sink.writeUtf8("b".repeat(10))

    val source = Buffer()
    source.writeUtf8("a".repeat(8192 * 2))
    sink.write(source, 20)

    assertEquals(listOf(30), segmentSizes(sink))
    assertEquals(listOf(8192 - 20, 8192), segmentSizes(source))
    assertEquals(30L, sink.size)
    assertEquals((8192 * 2 - 20).toLong(), source.size)
  }

  @Test fun writePrefixDoesntSplitButRequiresCompact() {
    val sink = Buffer()
    sink.writeUtf8("b".repeat(8192 - 10)) // limit = size - 10
    sink.readUtf8((8192 - 20).toLong()) // pos = size = 20

    val source = Buffer()
    source.writeUtf8("a".repeat(8192 * 2))
    sink.write(source, 20)

    assertEquals(listOf(30), segmentSizes(sink))
    assertEquals(listOf(8192 - 20, 8192), segmentSizes(source))
    assertEquals(30L, sink.size)
    assertEquals((8192 * 2 - 20).toLong(), source.size)
  }

  /** The big part of source's first segment is being moved.  */
  @Test fun writeSplitSourceBufferLeft() {
    val writeSize = 8192 / 2 + 1

    val sink = Buffer()
    sink.writeUtf8("b".repeat(8192 - 10))

    val source = Buffer()
    source.writeUtf8("a".repeat(8192 * 2))
    sink.write(source, writeSize.toLong())

    assertEquals(listOf(8192 - 10, writeSize), segmentSizes(sink))
    assertEquals(listOf(8192 - writeSize, 8192), segmentSizes(source))
  }

  /** The big part of source's first segment is staying put.  */
  @Test fun writeSplitSourceBufferRight() {
    val writeSize = 8192 / 2 - 1

    val sink = Buffer()
    sink.writeUtf8("b".repeat(8192 - 10))

    val source = Buffer()
    source.writeUtf8("a".repeat(8192 * 2))
    sink.write(source, writeSize.toLong())

    assertEquals(listOf(8192 - 10, writeSize), segmentSizes(sink))
    assertEquals(listOf(8192 - writeSize, 8192), segmentSizes(source))
  }

  @Test fun moveBytesBetweenBuffersShareSegment() {
    val size = 8192 / 2 - 1
    val segmentSizes = moveBytesBetweenBuffers("a".repeat(size), "b".repeat(size))
    assertEquals(listOf(size * 2), segmentSizes)
  }

  @Test fun moveBytesBetweenBuffersReassignSegment() {
    val size = 8192 / 2 + 1
    val segmentSizes = moveBytesBetweenBuffers("a".repeat(size), "b".repeat(size))
    assertEquals(listOf(size, size), segmentSizes)
  }

  @Test fun moveBytesBetweenBuffersMultipleSegments() {
    val size = 3 * 8192 + 1
    val segmentSizes = moveBytesBetweenBuffers("a".repeat(size), "b".repeat(size))
    assertEquals(
      listOf(
        8192,
        8192,
        8192,
        1,
        8192,
        8192,
        8192,
        1,
      ),
      segmentSizes,
    )
  }

  /**
   * When writing data that's already buffered, there's no reason to page the
   * data by segment.
   */
  @Test fun readAllWritesAllSegmentsAtOnce() {
    val write1 = Buffer().writeUtf8(
      "a".repeat(8192) +
        "b".repeat(8192) +
        "c".repeat(8192),
    )

    val source = Buffer().writeUtf8(
      "a".repeat(8192) +
        "b".repeat(8192) +
        "c".repeat(8192),
    )

    val mockSink = MockSink()

    assertEquals((8192 * 3).toLong(), source.readAll(mockSink))
    assertEquals(0L, source.size)
    mockSink.assertLog("write($write1, ${write1.size})")
  }
}

/**
 * Upstream's shared test support (commonTest/okio/OkioTesting.kt) reads buffer.head and
 * the Segment cursor fields directly; those declarations are internal to the upstream
 * Kotlin module and the oracle module is not a friend of it, so the same per-segment
 * sizes are read here through reflection over the pinned JVM field names (the
 * declarations are @JvmField upstream and plain fields on the port, so the names are
 * stable across both implementations; ledger R10).
 */
private fun segmentSizes(buffer: Buffer): List<Int> {
  val headField = Buffer::class.java.getDeclaredField("head")
  headField.isAccessible = true
  val head = headField.get(buffer) ?: return emptyList()

  val sizes = mutableListOf<Int>()
  var segment: Any? = head
  while (true) {
    val pos = segment!!.javaClass.getDeclaredField("pos").apply { isAccessible = true }.getInt(segment)
    val limit = segment.javaClass.getDeclaredField("limit").apply { isAccessible = true }.getInt(segment)
    sizes.add(limit - pos)
    segment = segment.javaClass.getDeclaredField("next").apply { isAccessible = true }.get(segment)
    if (segment === head) break
  }
  return sizes
}

/** Upstream CommonBufferTest.kt helper, verbatim but for the 8192 spelling (ledger R6). */
private fun moveBytesBetweenBuffers(vararg contents: String): List<Int> {
  val expected = StringBuilder()
  val buffer = Buffer()
  for (s in contents) {
    val source = Buffer()
    source.writeUtf8(s)
    buffer.writeAll(source)
    expected.append(s)
  }
  val segmentSizes = segmentSizes(buffer)
  assertEquals(expected.toString(), buffer.readUtf8(expected.length.toLong()))
  return segmentSizes
}

/**
 * Upstream shared test support (commonTest/okio/OkioTesting.kt), verbatim but for the
 * 8192 spelling of the internal Segment.SIZE constant (ledger R6).
 */
private fun bufferWithRandomSegmentLayout(dice: kotlin.random.Random, data: ByteArray): Buffer {
  val result = Buffer()

  // Writing to result directly will yield packed segments. Instead, write to
  // other buffers, then write those buffers to result.
  var pos = 0
  var byteCount: Int
  while (pos < data.size) {
    byteCount = 8192 / 2 + dice.nextInt(8192 / 2)
    if (byteCount > data.size - pos) byteCount = data.size - pos
    val offset = dice.nextInt(8192 - byteCount)

    val segment = Buffer()
    segment.write(ByteArray(offset))
    segment.write(data, pos, byteCount)
    segment.skip(offset.toLong())

    result.write(segment, byteCount.toLong())
    pos += byteCount
  }

  return result
}

/**
 * Upstream shared test support (commonTest/okio/MockSink.kt), trimmed to the recording
 * surface the adapted case uses (no scheduleThrow scripting; ledger R10).
 */
private class MockSink : Sink {
  private val log = mutableListOf<String>()

  fun assertLog(vararg messages: String) {
    assertEquals(messages.toList(), log)
  }

  override fun write(source: Buffer, byteCount: Long) {
    log.add("write($source, $byteCount)")
    source.skip(byteCount)
  }

  override fun flush() {
    log.add("flush()")
  }

  override fun timeout(): Timeout {
    log.add("timeout()")
    return Timeout.NONE
  }

  override fun close() {
    log.add("close()")
  }
}
