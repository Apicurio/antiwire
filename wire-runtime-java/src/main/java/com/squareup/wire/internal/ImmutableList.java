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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;

/**
 * An immutable list. Upstream declares this Kotlin-internal; it is public here per the
 * translation conventions (Kotlin internal is public in JVM bytecode).
 */
public class ImmutableList<T> extends AbstractList<T> implements RandomAccess, Serializable {
  private final ArrayList<T> list;

  public ImmutableList(List<? extends T> list) {
    this.list = new ArrayList<>(list);
  }

  /** The upstream-visible name of {@link #size()} (Kotlin collections expose {@code getSize()}). */
  public int getSize() {
    return size();
  }

  @Override public int size() {
    return list.size();
  }

  @Override public T get(int index) {
    return list.get(index);
  }

  @Override public Object[] toArray() {
    return list.toArray(); // Optimizing for mutable copy by MutableOnWriteList.
  }

  private Object writeReplace() throws ObjectStreamException {
    return Collections.unmodifiableList(list);
  }
}
