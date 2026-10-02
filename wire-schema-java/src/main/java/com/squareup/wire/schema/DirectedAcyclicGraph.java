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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A directed graph that is expected to be acyclic, supporting topological ordering.
 *
 * <p>Upstream's only production user is {@code PartitionedSchema}, which the port has not yet
 * translated; the class lands now because its upstream test is mandatory scope (DEC-5).
 *
 * @param <N> node type
 */
final class DirectedAcyclicGraph<N> {
  private final Iterable<N> nodes;
  private final Function<N, Iterable<N>> edges;

  /** Nodes which have no outgoing edges. */
  private final List<N> seeds;

  DirectedAcyclicGraph(Iterable<N> nodes, Function<N, Iterable<N>> edges) {
    this.nodes = nodes;
    this.edges = edges;
    this.seeds = new ArrayList<>();
    for (N node : nodes) {
      if (!edges.apply(node).iterator().hasNext()) seeds.add(node);
    }
  }

  private List<N> incomingEdges(N node) {
    List<N> result = new ArrayList<>();
    for (N other : nodes) {
      for (N edge : edges.apply(other)) {
        if (edge.equals(node)) {
          result.add(other);
          break;
        }
      }
    }
    return result;
  }

  public Set<Set<N>> disjointGraphs() {
    Set<Set<N>> graphs = new LinkedHashSet<>();
    for (N root : seeds) {
      Set<N> reachableNames = new LinkedHashSet<>();
      Deque<N> visitQueue = new ArrayDeque<>();
      visitQueue.add(root);
      while (!visitQueue.isEmpty()) {
        N visitName = visitQueue.removeFirst();
        reachableNames.add(visitName);

        List<N> dependencies = new ArrayList<>();
        for (N edge : edges.apply(visitName)) dependencies.add(edge);
        dependencies.addAll(incomingEdges(visitName));
        for (N dependency : dependencies) {
          if (!reachableNames.contains(dependency) && !visitQueue.contains(dependency)) {
            visitQueue.add(dependency);
          }
        }
      }
      graphs.add(reachableNames);
    }
    return graphs;
  }

  public List<N> topologicalOrder() {
    // LinkedHashMap: upstream's mutableMapOf; queue seeding follows insertion order.
    Map<N, Integer> incomingEdges = new LinkedHashMap<>();
    for (N vertex : nodes) {
      if (!incomingEdges.containsKey(vertex)) incomingEdges.put(vertex, 0);
      for (N edge : edges.apply(vertex)) {
        Integer count = incomingEdges.get(edge);
        incomingEdges.put(edge, count == null ? 1 : count + 1);
      }
    }

    Deque<N> queue = new ArrayDeque<>();
    for (Map.Entry<N, Integer> entry : incomingEdges.entrySet()) {
      if (entry.getValue() == 0) queue.add(entry.getKey());
    }

    List<N> result = new ArrayList<>();

    while (!queue.isEmpty()) {
      N vertex = queue.removeFirst();
      result.add(vertex);

      for (N edge : edges.apply(vertex)) {
        Integer count = incomingEdges.get(edge);
        int newCount = (count == null ? 0 : count) - 1;
        incomingEdges.put(edge, newCount);
        if (newCount == 0) {
          queue.add(edge);
        }
      }
    }

    if (result.size() != incomingEdges.size()) {
      throw new IllegalStateException("Graph contains a cycle, topological sort not possible!");
    }

    return result;
  }

  public Set<N> transitiveNodes(N node) {
    Set<N> result = new LinkedHashSet<>();
    Deque<N> queue = new ArrayDeque<>();
    queue.add(node);
    while (!queue.isEmpty()) {
      List<N> elements = new ArrayList<>();
      for (N element : edges.apply(queue.removeFirst())) elements.add(element);
      result.addAll(elements);
      queue.addAll(elements);
    }
    return result;
  }
}
