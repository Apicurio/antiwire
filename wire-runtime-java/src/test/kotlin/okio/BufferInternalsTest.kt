/*
 * Adapted from okio/src/commonTest/kotlin/okio/CommonBufferTest.kt at square/okio tag
 * parent-3.18.2, commit 9403a094ac98d5a6ba8bad4ec3301399796fad67.
 *
 * Port-only counterpart of the shared BufferTest: these cases observe Segment and
 * SegmentPool internals, which upstream declares internal and which the port exposes as
 * package-private members of the okio package (the source-level analog of Kotlin
 * internal), so they cannot run against upstream okio-jvm in the oracle module. The
 * moveBytesBetweenBuffers segment-layout cases left this file for the shared BufferTest
 * when the segmentSizes reflection helper (ledger R10) made them runnable on both
 * implementations; fillAndDrainPool stays here because SegmentPool.byteCount/MAX_SIZE
 * have no uniform observation point. Inputs, scenarios, and expected values come from
 * the pinned upstream source verbatim.
 */
package okio

import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BufferInternalsTest {

  @Test fun fillAndDrainPool() {
    val buffer = Buffer()

    // Take 2 * MAX_SIZE segments. This will drain the pool, even if other tests filled it.
    buffer.write(ByteArray(SegmentPool.MAX_SIZE.toInt()))
    buffer.write(ByteArray(SegmentPool.MAX_SIZE.toInt()))
    assertEquals(0L, SegmentPool.byteCount())

    // Recycle MAX_SIZE segments. They're all in the pool.
    buffer.skip(SegmentPool.MAX_SIZE)
    assertEquals(SegmentPool.MAX_SIZE, SegmentPool.byteCount())

    // Recycle MAX_SIZE more segments. The pool is full so they get garbage collected.
    buffer.skip(SegmentPool.MAX_SIZE)
    assertEquals(SegmentPool.MAX_SIZE, SegmentPool.byteCount())

    // Take MAX_SIZE segments to drain the pool.
    buffer.write(ByteArray(SegmentPool.MAX_SIZE.toInt()))
    assertEquals(0L, SegmentPool.byteCount())

    // Take MAX_SIZE more segments. The pool is drained so these will need to be allocated.
    buffer.write(ByteArray(SegmentPool.MAX_SIZE.toInt()))
    assertEquals(0L, SegmentPool.byteCount())
  }

  @Test fun segmentLayoutsAfterSnapshot() {
    // Snapshotting shares segments; the buffer must keep its own readable copy.
    val buffer = Buffer().writeUtf8("a".repeat(8192 + 10))
    val snapshot = buffer.snapshot()
    buffer.skip(8192)
    assertEquals("a".repeat(10), buffer.readUtf8())
    assertEquals("a".repeat(8192 + 10), snapshot.utf8())
  }

  /**
   * Bug-fix regression (2026-09-30): the getByte spelling must resolve through the
   * segmented lookup on snapshots, like the index operator (the shared BufferTest case).
   * Port-only because upstream marks the getByte spelling deprecated-error for Kotlin
   * (ledger P1-style exclusion); upstream backs both spellings with the single
   * internalGet lookup (jvmMain SegmentedByteString.kt:80).
   */
  @Test fun snapshotGetByteOnSegmentedSnapshot() {
    val snapshot = Buffer().writeUtf8("hello world").snapshot()
    assertEquals('h'.code.toByte(), snapshot.getByte(0))
    assertEquals('d'.code.toByte(), snapshot.getByte(10))
  }
}
