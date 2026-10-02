/*
 * Copyright (C) 2026 the antiwire authors
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Upstream DirectedAcyclicGraphTest translated (assertk to JUnit 5). */
public class DirectedAcyclicGraphTest {

  @Test
  public void singleNode() {
    List<String> nodes = Arrays.asList("A");
    DirectedAcyclicGraph<String> graph =
        new DirectedAcyclicGraph<>(nodes, node -> Collections.emptyList());
    List<String> order = graph.topologicalOrder();
    assertEquals(Arrays.asList("A"), order);
  }

  @Test
  public void simpleLinearDag() {
    // A -> B -> C
    List<String> nodes = Arrays.asList("A", "B", "C");
    Map<String, List<String>> edges = new LinkedHashMap<>();
    edges.put("A", Arrays.asList("B"));
    edges.put("B", Arrays.asList("C"));
    edges.put("C", Collections.emptyList());
    DirectedAcyclicGraph<String> graph =
        new DirectedAcyclicGraph<>(nodes, node -> edges.getOrDefault(node, Collections.emptyList()));
    List<String> order = graph.topologicalOrder();
    assertEquals(Arrays.asList("A", "B", "C"), order);
  }

  @Test
  public void simpleNonLinearDag() {
    // A
    // C -> B
    List<String> nodes = Arrays.asList("A", "B", "C");
    Map<String, List<String>> edges = new LinkedHashMap<>();
    edges.put("A", Collections.emptyList());
    edges.put("B", Collections.emptyList());
    edges.put("C", Arrays.asList("B"));
    DirectedAcyclicGraph<String> graph =
        new DirectedAcyclicGraph<>(nodes, node -> edges.getOrDefault(node, Collections.emptyList()));
    List<String> order = graph.topologicalOrder();
    assertEquals(Arrays.asList("A", "C", "B"), order);
  }

  @Test
  public void branchingDag() {
    //   A
    //  / \
    // B   C
    //  \ /
    //   D
    List<String> nodes = Arrays.asList("A", "B", "C", "D");
    Map<String, List<String>> edges = new LinkedHashMap<>();
    edges.put("A", Arrays.asList("B", "C"));
    edges.put("B", Arrays.asList("D"));
    edges.put("C", Arrays.asList("D"));
    edges.put("D", Collections.emptyList());
    DirectedAcyclicGraph<String> graph =
        new DirectedAcyclicGraph<>(nodes, node -> edges.getOrDefault(node, Collections.emptyList()));
    List<String> order = graph.topologicalOrder();
    assertEquals(Arrays.asList("A", "B", "C", "D"), order);
  }

  @Test
  public void multipleRoots() {
    // A -> C
    // B -> C
    List<String> nodes = Arrays.asList("A", "B", "C");
    Map<String, List<String>> edges = new LinkedHashMap<>();
    edges.put("A", Arrays.asList("C"));
    edges.put("B", Arrays.asList("C"));
    edges.put("C", Collections.emptyList());
    DirectedAcyclicGraph<String> graph =
        new DirectedAcyclicGraph<>(nodes, node -> edges.getOrDefault(node, Collections.emptyList()));
    List<String> order = graph.topologicalOrder();
    assertEquals(Arrays.asList("A", "B", "C"), order);
  }

  @Test
  public void cycleThrowsError() {
    // A -> B -> C -> A
    List<String> nodes = Arrays.asList("A", "B", "C");
    Map<String, List<String>> edges = new LinkedHashMap<>();
    edges.put("A", Arrays.asList("B"));
    edges.put("B", Arrays.asList("C"));
    edges.put("C", Arrays.asList("A"));
    DirectedAcyclicGraph<String> graph =
        new DirectedAcyclicGraph<>(nodes, node -> edges.getOrDefault(node, Collections.emptyList()));

    IllegalStateException expected = assertThrows(IllegalStateException.class,
        graph::topologicalOrder);
    assertEquals("Graph contains a cycle, topological sort not possible!", expected.getMessage());
  }
}
