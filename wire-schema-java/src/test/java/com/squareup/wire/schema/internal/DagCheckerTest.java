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
package com.squareup.wire.schema.internal;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Upstream DagCheckerTest translated (assertk to JUnit 5). */
public class DagCheckerTest {
  @Test public void emptyGraphHasNoCycles() {
    List<String> nodes = Collections.emptyList();
    List<String> edges = Collections.emptyList();
    DagChecker<String> cycleFinder = new DagChecker<>(nodes, node -> targets(node, edges));
    assertTrue(cycleFinder.check().isEmpty());
  }

  @Test public void singleNodeHasNoCycles() {
    List<String> nodes = Arrays.asList("a");
    List<String> edges = Collections.emptyList();
    DagChecker<String> cycleFinder = new DagChecker<>(nodes, node -> targets(node, edges));
    assertTrue(cycleFinder.check().isEmpty());
  }

  @Test public void singleNodePointingToItselfHasACycle() {
    List<String> nodes = Arrays.asList("a");
    List<String> edges = Arrays.asList("aa");
    DagChecker<String> cycleFinder = new DagChecker<>(nodes, node -> targets(node, edges));
    assertIterableEquals(Collections.singletonList(Arrays.asList("a")), cycleFinder.check());
  }

  @Test public void twoNodeDagHasNoCycle() {
    List<String> nodes = Arrays.asList("a", "b");
    List<String> edges = Arrays.asList("ab");
    DagChecker<String> cycleFinder = new DagChecker<>(nodes, node -> targets(node, edges));
    assertTrue(cycleFinder.check().isEmpty());
  }

  @Test public void twoNodeLoopHasACycle() {
    List<String> nodes = Arrays.asList("a", "b");
    List<String> edges = Arrays.asList("ab", "ba");
    DagChecker<String> cycleFinder = new DagChecker<>(nodes, node -> targets(node, edges));
    assertIterableEquals(Collections.singletonList(Arrays.asList("a", "b")), cycleFinder.check());
  }

  @Test public void threeNodeDagHasNoCycle() {
    List<String> nodes = Arrays.asList("a", "b", "c");
    List<String> edges = Arrays.asList("ab", "bc", "ac");
    DagChecker<String> cycleFinder = new DagChecker<>(nodes, node -> targets(node, edges));
    assertTrue(cycleFinder.check().isEmpty());
  }

  @Test public void threeNodeCycle() {
    List<String> nodes = Arrays.asList("a", "b", "c");
    List<String> edges = Arrays.asList("ab", "bc", "ca");
    DagChecker<String> cycleFinder = new DagChecker<>(nodes, node -> targets(node, edges));
    assertIterableEquals(
        Collections.singletonList(Arrays.asList("a", "b", "c")), cycleFinder.check());
  }

  @Test public void twoNodeCycleWithNotStronglyConnectedExtraNode() {
    List<String> nodes = Arrays.asList("a", "b", "c");
    List<String> edges = Arrays.asList("ab", "ac", "ca");
    DagChecker<String> cycleFinder = new DagChecker<>(nodes, node -> targets(node, edges));
    assertIterableEquals(Collections.singletonList(Arrays.asList("a", "c")), cycleFinder.check());
  }

  @Test public void threeNodeCycleWithSelfEdges() {
    List<String> nodes = Arrays.asList("a", "b", "c");
    List<String> edges = Arrays.asList("ab", "bc", "ca", "bb", "cc");
    DagChecker<String> cycleFinder = new DagChecker<>(nodes, node -> targets(node, edges));
    assertIterableEquals(
        Collections.singletonList(Arrays.asList("a", "b", "c")), cycleFinder.check());
  }

  @Test public void twoIndependentStronglyConnectedComponents() {
    List<String> nodes = Arrays.asList("a", "b", "c");
    List<String> edges = Arrays.asList("ab", "ba", "cc");
    DagChecker<String> cycleFinder = new DagChecker<>(nodes, node -> targets(node, edges));
    assertIterableEquals(
        Arrays.asList(Arrays.asList("a", "b"), Arrays.asList("c")), cycleFinder.check());
  }

  /**
   * This is the example graph from the Wikipedia page on the Tarjan algorithm:
   *
   * <pre>
   *    A &larr; B &larr; C &harr; D
   *    &darr; &nearr; &uarr;   &uarr;   &uarr;
   *    E &larr; F &harr; G &larr; H &#8630;
   * </pre>
   *
   * These are the four strongly connected components:
   *
   * <pre>
   *    A &larr; B     C &harr; D
   *    &darr; &nearr;
   *    E     F &harr; G     H &#8630;
   * </pre>
   */
  @Test public void wikipediaExample() {
    List<String> nodes = Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h");
    List<String> edges = Arrays.asList(
        "ae", "ba", "cb", "cd", "dc", "eb",
        "fb", "fe", "fg", "gc", "gf", "hd", "hg", "hh");
    DagChecker<String> cycleFinder = new DagChecker<>(nodes, node -> targets(node, edges));
    assertIterableEquals(Arrays.asList(
        Arrays.asList("a", "e", "b"),
        Arrays.asList("c", "d"),
        Arrays.asList("f", "g"),
        Arrays.asList("h")), cycleFinder.check());
  }

  /**
   * Node 'E' participates in two cycles, creating a strongly connected component of size 7. Note
   * that the returned list of nodes is not a cycle; the last element does not have an edge to the
   * first element.
   *
   * <pre>
   *    A &larr; B &larr; C
   *    &darr;   &uarr;
   *    D &rarr; E &rarr; F
   *    &darr;   &uarr;   &darr;
   *    G   H &larr; I
   * </pre>
   */
  @Test public void cojoinedCycles() {
    List<String> nodes = Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i");
    List<String> edges = Arrays.asList(
        "ad", "ba", "cb",
        "de", "dg", "eb", "ef", "fi",
        "he", "ih");
    DagChecker<String> cycleFinder = new DagChecker<>(nodes, node -> targets(node, edges));
    assertIterableEquals(
        Collections.singletonList(Arrays.asList("a", "d", "e", "b", "f", "i", "h")),
        cycleFinder.check());
  }

  private static List<String> targets(String node, List<String> allEdges) {
    List<String> result = new ArrayList<>();
    for (String edge : allEdges) {
      if (edge.startsWith(node)) result.add(edge.substring(1));
    }
    return result;
  }
}
