/*
 * Translated from okio/src/jvmMain/kotlin/okio/SegmentPool.kt at square/okio tag parent-3.18.2,
 * commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under the notices below.
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

import java.util.concurrent.atomic.AtomicReference;

/**
 * This class pools segments in a lock-free singly-linked list. Though it interacts with
 * {@link Segment}, this class merely compacts a pool of segments.
 */
final class SegmentPool {

  /** The maximum number of bytes to pool per hash bucket. */
  // TODO: Is 64 KiB a good maximum size? Do we ever have that many idle segments?
  static final long MAX_SIZE = 64 * 1024L; // 64 KiB.

  /** A sentinel segment to indicate that the linked list is currently being modified. */
  private static final Segment LOCK =
      new Segment(new byte[0], 0, 0, false, false);

  /** The number of hash buckets. Should be a power of two. */
  private static final int HASH_BUCKET_COUNT =
      Integer.highestOneBit(Math.max(1, Runtime.getRuntime().availableProcessors() * 2 - 1));

  private static final AtomicReference<Segment>[] HASH_BUCKETS = newBuckets();

  @SuppressWarnings("unchecked")
  private static AtomicReference<Segment>[] newBuckets() {
    AtomicReference<Segment>[] result = new AtomicReference[HASH_BUCKET_COUNT];
    for (int i = 0; i < HASH_BUCKET_COUNT; i++) {
      result[i] = new AtomicReference<>(); // null value implies an empty bucket
    }
    return result;
  }

  private SegmentPool() {
  }

  static long byteCount() {
    Segment first = firstRef().get();
    return first != null ? first.limit : 0L;
  }

  static Segment take() {
    AtomicReference<Segment> firstRef = firstRef();

    Segment first = firstRef.getAndSet(LOCK);
    if (first == LOCK) {
      // We didn't acquire the lock. Don't take a pooled segment.
      return new Segment();
    } else if (first == null) {
      // We acquired the lock but the pool was empty. Unlock and return a new segment.
      firstRef.set(null);
      return new Segment();
    } else {
      // We acquired the lock and the pool was not empty. Pop the first element and return it.
      firstRef.set(first.next);
      first.next = null;
      first.limit = 0;
      return first;
    }
  }

  static void recycle(Segment segment) {
    if (segment.next != null || segment.prev != null) {
      throw new IllegalArgumentException("segment.next == null && segment.prev == null required");
    }
    if (segment.shared) {
      return; // This segment cannot be recycled.
    }

    AtomicReference<Segment> firstRef = firstRef();

    Segment first = firstRef.getAndSet(LOCK);
    if (first == LOCK) {
      return; // A take() or recycle() is currently in progress.
    }
    long firstLimit = first != null ? first.limit : 0L;
    if (firstLimit >= MAX_SIZE) {
      firstRef.set(first); // Pool is full.
      return;
    }

    segment.next = first;
    segment.pos = 0;
    segment.limit = (int) (firstLimit + Segment.SIZE);

    firstRef.set(segment);
  }

  private static AtomicReference<Segment> firstRef() {
    // Get a value in [0..HASH_BUCKET_COUNT) based on the current thread.
    int hashBucket = (int) (Thread.currentThread().getId() & (HASH_BUCKET_COUNT - 1L));
    return HASH_BUCKETS[hashBucket];
  }
}
