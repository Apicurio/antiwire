/*
 * Copyright (C) 2020 Square, Inc.
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
package com.squareup.wire.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A minimal immutable multimap over a {@link Map} of keys to value collections, replacing
 * upstream's commonMain {@code expect Multimap} JVM actual.
 */
public final class Multimap<K, V> {
  private final Map<K, ? extends Collection<V>> map;

  private Multimap(Map<K, ? extends Collection<V>> map) {
    this.map = map;
  }

  public int size() {
    return map.size();
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  public boolean containsValue(Object value) {
    for (Collection<V> values : map.values()) {
      if (values.contains(value)) return true;
    }
    return false;
  }

  public Collection<V> get(K key) {
    Collection<V> values = map.get(key);
    return values != null ? values : Collections.emptyList();
  }

  /**
   * All values of all keys, in key-insertion order then per-key insertion order, duplicates
   * included, matching upstream's LinkedHashMultimap-backed actual.
   */
  public Collection<V> values() {
    List<V> result = new ArrayList<>();
    for (Collection<V> values : map.values()) {
      result.addAll(values);
    }
    return result;
  }

  public Map<K, Collection<V>> asMap() {
    return (Map<K, Collection<V>>) (Map<?, ? extends Collection<V>>) map;
  }

  static <K, V> Multimap<K, V> toMultimap(Map<K, ? extends Collection<V>> map) {
    return new Multimap<>(map);
  }
}
