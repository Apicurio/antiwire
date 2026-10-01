/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.wire.internal;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.RandomAccess;

/**
 * A primitive ArrayList copy (JetBrains Apache-2.0 original) avoiding boxing; upstream declares
 * it Kotlin-internal, public here per the translation conventions.
 */
public final class LongArrayList extends AbstractList<Long> implements RandomAccess {
  private long[] data;
  private int size;

  public LongArrayList(int initialCapacity) {
    this.data = new long[initialCapacity];
    this.size = 0;
  }

  @Override public Long get(int index) {
    if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
    return data[index];
  }

  @Override public int size() {
    return size;
  }

  @Override public Long set(int index, Long element) {
    if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
    long previous = data[index];
    data[index] = element;
    return previous;
  }

  @Override public boolean add(Long element) {
    add(element.longValue());
    return true;
  }

  /**
   * Returns the underlying array, truncating as necessary so that it has the same size as the
   * number of elements. Call only after all elements have been added.
   */
  public long[] toPrimitiveArray() {
    if (size < data.length) {
      data = Arrays.copyOf(data, size);
    }
    return data;
  }

  public void add(long value) {
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
}
