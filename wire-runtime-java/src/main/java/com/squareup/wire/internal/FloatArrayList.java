/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */
package com.squareup.wire.internal;

import java.util.Arrays;

public final class FloatArrayList {
  private float[] data;
  private int size;

  public FloatArrayList(int initialCapacity) {
    this.data = new float[initialCapacity];
    this.size = 0;
  }

  /**
   * Returns the underlying array, truncating as necessary so that it has the same size as the
   * number of elements. Call only after all elements have been added.
   */
  public float[] toArray() {
    if (size < data.length) {
      data = Arrays.copyOf(data, size);
    }
    return data;
  }

  public void add(float value) {
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

  public static FloatArrayList forDecoding(long minLengthInBytes, long minimumElementByteSize) {
    // Upstream clamps the element count to Int.MAX_VALUE before narrowing.
    long minElements = Math.min(minLengthInBytes / minimumElementByteSize,
        (long) Integer.MAX_VALUE);
    return new FloatArrayList((int) minElements);
  }

  public static FloatArrayList forDecoding(int minLengthInBytes, int minimumElementByteSize) {
    int minElements = minLengthInBytes / minimumElementByteSize;
    return new FloatArrayList(minElements);
  }

  /** Kotlin companion mirror: Java may write {@code FloatArrayList.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }

    public FloatArrayList forDecoding(int minLengthInBytes, int minimumElementByteSize) {
      return FloatArrayList.forDecoding(minLengthInBytes, minimumElementByteSize);
    }

    public FloatArrayList forDecoding(long minLengthInBytes, long minimumElementByteSize) {
      return FloatArrayList.forDecoding(minLengthInBytes, minimumElementByteSize);
    }
  }
}
