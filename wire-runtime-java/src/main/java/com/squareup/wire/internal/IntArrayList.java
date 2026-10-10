/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */
package com.squareup.wire.internal;

import java.util.Arrays;

public final class IntArrayList {
  private int[] data;
  private int size;

  public IntArrayList(int initialCapacity) {
    this.data = new int[initialCapacity];
    this.size = 0;
  }

  /**
   * Returns the underlying array, truncating as necessary so that it has the same size as the
   * number of elements. Call only after all elements have been added.
   */
  public int[] toArray() {
    if (size < data.length) {
      data = Arrays.copyOf(data, size);
    }
    return data;
  }

  public void add(int value) {
    ensureCapacity(size + 1);
    data[size++] = value;
  }

  public boolean isNotEmpty() {
    return size > 0;
  }

  private void ensureCapacity(int minCapacity) {
    if (minCapacity > data.length) {
      data = Arrays.copyOf(data, Math.max(data.length * 3 / 2 + 1, minCapacity));
    }
  }

  @Override public String toString() {
    return Arrays.toString(Arrays.copyOf(data, size));
  }

  public static IntArrayList forDecoding(long minLengthInBytes, long minimumElementByteSize) {
    // Upstream clamps the element count to Int.MAX_VALUE before narrowing.
    long minElements = Math.min(minLengthInBytes / minimumElementByteSize,
        (long) Integer.MAX_VALUE);
    return new IntArrayList((int) minElements);
  }

  public static IntArrayList forDecoding(int minLengthInBytes, int minimumElementByteSize) {
    int minElements = minLengthInBytes / minimumElementByteSize;
    return new IntArrayList(minElements);
  }

  /** Mirror of the Kotlin companion object: lets Java callers write {@code IntArrayList.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }

    public IntArrayList forDecoding(int minLengthInBytes, int minimumElementByteSize) {
      return IntArrayList.forDecoding(minLengthInBytes, minimumElementByteSize);
    }

    public IntArrayList forDecoding(long minLengthInBytes, long minimumElementByteSize) {
      return IntArrayList.forDecoding(minLengthInBytes, minimumElementByteSize);
    }
  }
}
