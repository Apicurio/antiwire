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
package com.squareup.wire.schema.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Checks whether a graph is a directed acyclic graph using Tarjan's algorithm.
 *
 * <p>Note that all cycles are strongly connected components, but a strongly connected component
 * is not strictly a cycle. In particular it may contain nodes that are mutually reachable from
 * each other through multiple paths.
 *
 * @param <N> node type
 */
public final class DagChecker<N> {
  private final Iterable<N> nodes;
  private final Function<N, Iterable<N>> edges;

  private int nextDiscoveryId = 0;
  private final Map<N, Tag<N>> tags = new HashMap<>();
  private final List<Tag<N>> stack = new ArrayList<>();
  private final Set<List<N>> result = new HashSet<>();

  public DagChecker(Iterable<N> nodes, Function<N, Iterable<N>> edges) {
    this.nodes = nodes;
    this.edges = edges;
    for (N node : nodes) {
      tags.put(node, new Tag<>(node));
    }
  }

  /**
   * Returns a set of strongly connected components. Each strongly connected component is a list
   * of nodes that are mutually reachable to each other.
   *
   * <p>If the graph contains nodes that have self edges but are not strongly connected to any
   * other nodes, those nodes will be single-element lists in the result.
   *
   * <p>If the result is empty the graph is acyclic.
   */
  public Set<List<N>> check() {
    if (nextDiscoveryId != 0) {
      throw new IllegalStateException("Check failed.");
    }

    for (N node : nodes) {
      Tag<N> tag = tags.get(node);
      if (tag.discoveryId == -1) {
        discoverDepthFirst(tag);
      }
    }

    return result;
  }

  /**
   * Traverse this node and all of the nodes it can reach. This returns the lowest discovery ID
   * of the set of nodes strongly connected to this node.
   */
  private int discoverDepthFirst(Tag<N> tag) {
    tag.discoveryId = nextDiscoveryId;
    tag.lowestConnectedDiscoveryId = nextDiscoveryId;
    nextDiscoveryId++;

    int stackIndex = stack.size();
    stack.add(tag);
    tag.onStack = true;

    for (N target : edges.apply(tag.node)) {
      Tag<N> t = tags.get(target);
      if (t == null) continue;

      if (t.discoveryId == -1) {
        // Traverse a new node. If in the process it received a lower discovery ID, it must be
        // strongly connected to this node! Take that lower discovery ID.
        tag.lowestConnectedDiscoveryId =
            Math.min(tag.lowestConnectedDiscoveryId, discoverDepthFirst(t));
      } else if (t.onStack) {
        // Node a new node, but one we're in a cycle with. Take its discover ID if it's lower.
        if (t == tag) tag.selfEdge = true;
        tag.lowestConnectedDiscoveryId =
            Math.min(tag.lowestConnectedDiscoveryId, t.discoveryId);
      }
    }

    // We've traversed all the edges. If our discovery ID is the lowest then we're the root of
    // our strongly connected component. Include it in the result.
    if (tag.discoveryId == tag.lowestConnectedDiscoveryId) {
      List<Tag<N>> slice = new ArrayList<>(stack.subList(stackIndex, stack.size()));
      stack.subList(stackIndex, stack.size()).clear();

      List<N> component = new ArrayList<>();
      for (Tag<N> componentTag : slice) {
        componentTag.onStack = false;
        component.add(componentTag.node);
      }

      if (component.size() > 1 || slice.get(0).selfEdge) {
        result.add(component);
      }
    }

    return tag.lowestConnectedDiscoveryId;
  }

  private static final class Tag<N> {
    final N node;
    int discoveryId = -1;
    int lowestConnectedDiscoveryId = -1;
    boolean onStack = false;
    boolean selfEdge = false;

    Tag(N node) {
      this.node = node;
    }
  }
}
