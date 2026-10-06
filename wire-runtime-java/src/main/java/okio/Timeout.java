/*
 * Translated from okio/src/jvmMain/kotlin/okio/Timeout.kt at square/okio tag parent-3.18.2,
 * commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under the notices below.
 * Translation notes for the M0 subset: cancel(), awaitSignal(), waitUntilNotified(), and the
 * instance deadline()/cancel-callback machinery are out of the subset (no wire main or wire
 * test use at the pin; the threading conveniences are exercised only by okio's own
 * concurrency suites). The retained surface is the policy core the streams use.
 *
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;

/**
 * A policy on how much time to spend on a task before giving up. When a task times out, it is left
 * in an unspecified state and should be abandoned. For example, if reading from a source times out,
 * that source should be closed and the read should be retried later. If writing to a sink times
 * out, the same rules apply: close the sink and retry later.
 */
public class Timeout {

  /**
   * An empty timeout that neither tracks nor detects timeouts. Use this when timeouts aren't
   * necessary, such as in implementations whose operations do not block.
   */
  public static final Timeout NONE = new Timeout() {
    @Override public Timeout timeout(long timeout, TimeUnit unit) {
      throw new UnsupportedOperationException("unreachable");
    }

    @Override public Timeout deadlineNanoTime(long deadlineNanoTime) {
      throw new UnsupportedOperationException("unreachable");
    }

    @Override public Timeout deadline(long duration, TimeUnit unit) {
      throw new UnsupportedOperationException("unreachable");
    }

    @Override public Timeout clearTimeout() {
      throw new UnsupportedOperationException("unreachable");
    }

    @Override public Timeout clearDeadline() {
      throw new UnsupportedOperationException("unreachable");
    }

    @Override public void throwIfReached() throws IOException {
      throw new AssertionError("unreachable");
    }
  };

  /** True if {@code timeoutNanos} field is a non-zero timeout. */
  private boolean hasTimeout;

  /** The non-zero timeout, or a zero timeout if {@code hasTimeout} is false. */
  private long timeoutNanos;

  private boolean hasDeadline;
  private long deadlineNanoTime;

  public Timeout timeout(long timeout, TimeUnit unit) {
    if (timeout < 0) {
      throw new IllegalArgumentException("timeout < 0: " + timeout);
    }
    if (unit == null) {
      throw new IllegalArgumentException("unit == null");
    }
    this.hasTimeout = timeout > 0;
    this.timeoutNanos = unit.toNanos(timeout);
    return this;
  }

  public long timeoutNanos() {
    return timeoutNanos;
  }

  public boolean hasTimeout() {
    return hasTimeout;
  }

  public long deadlineNanoTime() {
    if (!hasDeadline) {
      throw new IllegalStateException("No deadline");
    }
    return deadlineNanoTime;
  }

  public Timeout deadlineNanoTime(long deadlineNanoTime) {
    this.hasDeadline = true;
    this.deadlineNanoTime = deadlineNanoTime;
    return this;
  }

  public Timeout deadline(long duration, TimeUnit unit) {
    if (duration <= 0) {
      throw new IllegalArgumentException("duration <= 0: " + duration);
    }
    if (unit == null) {
      throw new IllegalArgumentException("unit == null");
    }
    return deadlineNanoTime(System.nanoTime() + unit.toNanos(duration));
  }

  public Timeout clearTimeout() {
    this.hasTimeout = false;
    this.timeoutNanos = 0L;
    return this;
  }

  public Timeout clearDeadline() {
    this.hasDeadline = false;
    this.deadlineNanoTime = 0L;
    return this;
  }

  /**
   * Throws an {@link InterruptedIOException} if the deadline reached or if the current thread is
   * interrupted.
   */
  public void throwIfReached() throws IOException {
    if (Thread.interrupted()) {
      Thread.currentThread().interrupt(); // Retain interrupted status.
      throw new InterruptedIOException("interrupted");
    }

    if (hasDeadline && deadlineNanoTime - System.nanoTime() <= 0) {
      throw new InterruptedIOException("deadline reached");
    }
  }
}
