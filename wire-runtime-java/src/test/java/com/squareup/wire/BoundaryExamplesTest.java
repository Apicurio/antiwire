package com.squareup.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import okio.Buffer;
import org.junit.jupiter.api.Test;

/**
 * TASK-5 AC#6: executable boundary examples for the null and exception contract between the
 * Kotlin upstream and the Java port (docs/translation-conventions.md sections 3 and 4). Any
 * behavior change here fails the build suite of the shared verification entry point.
 */
public class BoundaryExamplesTest {

  /** Non-null Kotlin parameter: NPE from the guard, never a silent fall-through. */
  @Test
  public void nonNullParameterThrowsNpe() {
    assertThrows(NullPointerException.class, () -> Syntax.get(null));
  }

  /** kotlin.require parity: IllegalArgumentException with the upstream message text. */
  @Test
  public void requireParityThrowsIaeWithUpstreamMessage() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> Syntax.get("bogus"));
    assertEquals("unexpected syntax: bogus", e.getMessage());
  }

  /** kotlin.check parity: IllegalStateException with the upstream message text. */
  @Test
  public void checkParityThrowsIseWithUpstreamMessage() {
    ProtoReader reader = new ProtoReader(new Buffer());
    // Fresh reader is in the length-delimited state; endMessage without beginMessage is the
    // upstream `check(state == STATE_TAG)` failure.
    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> reader.endMessageAndGetUnknownFields(0L));
    assertEquals("Unexpected call to endMessage()", e.getMessage());
  }

  /** Null boundary on the read path: nullable peekFieldEncoding stays null before nextTag. */
  @Test
  public void nullableBoundaryBeforeFirstTag() {
    ProtoReader reader = new ProtoReader(new Buffer());
    assertEquals(null, reader.peekFieldEncoding());
  }
}
