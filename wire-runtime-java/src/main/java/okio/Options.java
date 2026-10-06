/*
 * Translated from okio/src/commonMain/kotlin/okio/Options.kt at square/okio tag parent-3.18.2,
 * commit 9403a094ac98d5a6ba8bad4ec3301399796fad67. Derivative work under the notices below.
 * Translation note: the TypedOptions generic variant is excluded from the M0 subset
 * (no wire main or wire test use at the pin); the ByteString variant is the one the
 * wire-schema loader's BOM detection uses.
 *
 * Copyright (C) 2016 Square, Inc.
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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;

/**
 * An immutable set of options that are indexed according to a prefix. This is used for
 * constructing matchers.
 */
public final class Options extends AbstractList<ByteString> implements RandomAccess {

  final ByteString[] byteStrings;
  final int[] trie;

  private Options(ByteString[] byteStrings, int[] trie) {
    this.byteStrings = byteStrings;
    this.trie = trie;
  }

  public static Options of(ByteString... byteStrings) throws java.io.IOException {
    if (byteStrings.length == 0) {
      // With no choices we must always return -1. Create a trie that selects from an empty set.
      return new Options(new ByteString[0], new int[] {0, -1});
    }

    // Sort the byte strings which is required when recursively building the trie. Map the sorted
    // indexes to the caller's indexes.
    List<ByteString> list = new ArrayList<>(List.of(byteStrings));
    Collections.sort(list);
    int[] indexes = new int[list.size()];
    for (int callerIndex = 0; callerIndex < byteStrings.length; callerIndex++) {
      ByteString byteString = byteStrings[callerIndex];
      int sortedIndex = Collections.binarySearch(list, byteString);
      indexes[sortedIndex] = callerIndex;
    }
    if (list.get(0).size() <= 0) {
      throw new IllegalArgumentException("the empty byte string is not a supported option");
    }

    // Strip elements that will never be returned because they follow their own prefixes. For
    // example, if the caller provides ["abc", "abcde"] we will never return "abcde" because we
    // return as soon as we encounter "abc".
    int a = 0;
    while (a < list.size()) {
      ByteString prefix = list.get(a);
      int b = a + 1;
      while (b < list.size()) {
        ByteString byteString = list.get(b);
        if (!byteString.startsWith(prefix)) {
          break;
        }
        if (byteString.size() == prefix.size()) {
          throw new IllegalArgumentException("duplicate option: " + byteString);
        }
        if (indexes[b] > indexes[a]) {
          list.remove(b);
          indexes = removeIndex(indexes, b);
        } else {
          b++;
        }
      }
      a++;
    }

    Buffer trieBytes = new Buffer();
    buildTrieRecursive(0L, trieBytes, 0, list, 0, list.size(), indexes);

    int[] trie = new int[(int) (trieBytes.size() / 4)];
    for (int i = 0; i < trie.length; i++) {
      trie[i] = trieBytes.readInt();
    }

    return new Options(byteStrings.clone() /* Defensive copy. */, trie);
  }

  private static int[] removeIndex(int[] array, int index) {
    int[] result = new int[array.length - 1];
    System.arraycopy(array, 0, result, 0, index);
    System.arraycopy(array, index + 1, result, index, result.length - index);
    return result;
  }

  /**
   * Builds a trie encoded as an int array. Nodes in the trie are of two types: SELECT and SCAN.
   *
   * <p>SELECT nodes are encoded as:
   *
   * <ul>
   *   <li>selectChoiceCount: the number of bytes to choose between (a positive int)
   *   <li>prefixIndex: the result index at the current position or -1 if the current position is
   *       not a result on its own
   *   <li>a sorted list of selectChoiceCount bytes to match against the input string
   *   <li>a heterogeneous list of selectChoiceCount result indexes (&gt;= 0) or offsets (&lt; 0)
   *       of the next node to follow. Elements in this list correspond to elements in the
   *       preceding list. Offsets are negative and must be multiplied by -1 before being used.
   * </ul>
   *
   * <p>SCAN nodes are encoded as:
   *
   * <ul>
   *   <li>scanByteCount: the number of bytes to match in sequence. This count is negative and
   *       must be multiplied by -1 before being used.
   *   <li>prefixIndex: the result index at the current position or -1 if the current position is
   *       not a result on its own
   *   <li>a list of scanByteCount bytes to match
   *   <li>nextStep: the result index (&gt;= 0) or offset (&lt; 0) of the next node to follow.
   *       Offsets are negative and must be multiplied by -1 before being used.
   * </ul>
   *
   * <p>This structure is used to improve locality and performance when selecting from a list of
   * options.
   */
  private static void buildTrieRecursive(
      long nodeOffset,
      Buffer node,
      int byteStringOffset,
      List<ByteString> byteStrings,
      int fromIndex,
      int toIndex,
      int[] indexes) throws java.io.IOException {
    if (fromIndex >= toIndex) {
      throw new IllegalArgumentException("fromIndex >= toIndex required but was "
          + fromIndex + " >= " + toIndex);
    }
    for (int i = fromIndex; i < toIndex; i++) {
      if (byteStrings.get(i).size() < byteStringOffset) {
        throw new IllegalArgumentException("byteStrings[" + i + "].size() < byteStringOffset");
      }
    }

    int fromIndexLocal = fromIndex;
    ByteString from = byteStrings.get(fromIndexLocal);
    ByteString to = byteStrings.get(toIndex - 1);
    int prefixIndex = -1;

    // If the first element is already matched, that's our prefix.
    if (byteStringOffset == from.size()) {
      prefixIndex = indexes[fromIndexLocal];
      fromIndexLocal++;
      from = byteStrings.get(fromIndexLocal);
    }

    if (from.getByte(byteStringOffset) != to.getByte(byteStringOffset)) {
      // If we have multiple bytes to choose from, encode a SELECT node.
      int selectChoiceCount = 1;
      for (int i = fromIndexLocal + 1; i < toIndex; i++) {
        if (byteStrings.get(i - 1).getByte(byteStringOffset)
            != byteStrings.get(i).getByte(byteStringOffset)) {
          selectChoiceCount++;
        }
      }

      // Compute the offset that childNodes will get when we append it to node.
      long childNodesOffset = nodeOffset + node.size() / 4 + 2 + (selectChoiceCount * 2);

      node.writeInt(selectChoiceCount);
      node.writeInt(prefixIndex);

      for (int i = fromIndexLocal; i < toIndex; i++) {
        byte rangeByte = byteStrings.get(i).getByte(byteStringOffset);
        if (i == fromIndexLocal
            || rangeByte != byteStrings.get(i - 1).getByte(byteStringOffset)) {
          node.writeInt(rangeByte & 0xff);
        }
      }

      Buffer childNodes = new Buffer();
      int rangeStart = fromIndexLocal;
      while (rangeStart < toIndex) {
        byte rangeByte = byteStrings.get(rangeStart).getByte(byteStringOffset);
        int rangeEnd = toIndex;
        for (int i = rangeStart + 1; i < toIndex; i++) {
          if (rangeByte != byteStrings.get(i).getByte(byteStringOffset)) {
            rangeEnd = i;
            break;
          }
        }

        if (rangeStart + 1 == rangeEnd
            && byteStringOffset + 1 == byteStrings.get(rangeStart).size()) {
          // The result is a single index.
          node.writeInt(indexes[rangeStart]);
        } else {
          // The result is another node.
          node.writeInt((int) (-1 * (childNodesOffset + childNodes.size() / 4)));
          buildTrieRecursive(
              childNodesOffset,
              childNodes,
              byteStringOffset + 1,
              byteStrings,
              rangeStart,
              rangeEnd,
              indexes);
        }

        rangeStart = rangeEnd;
      }

      node.writeAll(childNodes);
    } else {
      // If all of the bytes are the same, encode a SCAN node.
      int scanByteCount = 0;
      for (int i = byteStringOffset; i < Math.min(from.size(), to.size()); i++) {
        if (from.getByte(i) == to.getByte(i)) {
          scanByteCount++;
        } else {
          break;
        }
      }

      // Compute the offset that childNodes will get when we append it to node.
      long childNodesOffset = nodeOffset + node.size() / 4 + 2 + scanByteCount + 1;

      node.writeInt(-scanByteCount);
      node.writeInt(prefixIndex);

      for (int i = byteStringOffset; i < byteStringOffset + scanByteCount; i++) {
        node.writeInt(from.getByte(i) & 0xff);
      }

      if (fromIndexLocal + 1 == toIndex) {
        // The result is a single index.
        if (byteStringOffset + scanByteCount != byteStrings.get(fromIndexLocal).size()) {
          throw new IllegalStateException(
              "byteStringOffset + scanByteCount == byteStrings[fromIndex].size() required");
        }
        node.writeInt(indexes[fromIndexLocal]);
      } else {
        // The result is another node.
        Buffer childNodes = new Buffer();
        node.writeInt((int) (-1 * (childNodesOffset + childNodes.size() / 4)));
        buildTrieRecursive(
            childNodesOffset,
            childNodes,
            byteStringOffset + scanByteCount,
            byteStrings,
            fromIndexLocal,
            toIndex,
            indexes);
        node.writeAll(childNodes);
      }
    }
  }

  @Override public ByteString get(int index) {
    return byteStrings[index];
  }

  @Override public int size() {
    return byteStrings.length;
  }
}
