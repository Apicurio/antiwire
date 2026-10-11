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
package com.squareup.wire.schema.internal.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.SchemaUtil;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * ReservedElement formatting cases; upstream has no standalone ReservedElementTest at 7.1.0, so
 * these stay here. The former EnumElement and ExtensionsElement cases moved to the full
 * EnumElementTest and ExtensionsElementTest translations (TASK-13).
 */
public class EnumAndReservedElementTest {
  private final Location location = Location.get("file.proto");

  @Test public void reservedWithMax() {
    ReservedElement element = new ReservedElement(location, "",
        Arrays.asList(10, new int[] {12, SchemaUtil.MAX_TAG_VALUE}));
    assertEquals("reserved 10, 12 to max;\n", element.toSchema());
  }

  @Test public void reservedWithRange() {
    ReservedElement element = new ReservedElement(location, "",
        Collections.singletonList(new int[] {5, 8}));
    assertEquals("reserved 5 to 8;\n", element.toSchema());
  }

  @Test public void reservedWithName() {
    ReservedElement element = new ReservedElement(location, "",
        Arrays.<Object>asList("OLD", "NEW"));
    assertEquals("reserved \"OLD\", \"NEW\";\n", element.toSchema());
  }

  /**
   * toString must read like upstream's data-class output (measured on the real Wire 7.1.0 jar,
   * proto2 {@code reserved 5 to 9, 3, "gone"; extensions 100 to 199;}): a range prints as
   * {@code 5..9}, never as an int[] identity hash. DEC-15 keeps ranges as int[] pairs, so the
   * rendering is where the difference would leak.
   */
  @Test public void reservedAndExtensionsToStringRenderRangesLikeUpstream() {
    Location location = Location.get("x.proto");
    ReservedElement reserved = new ReservedElement(location, "",
        Arrays.<Object>asList(new int[] {5, 9}, 3, "gone"));
    assertEquals("ReservedElement(location=x.proto, documentation=, values=[5..9, 3, gone])",
        reserved.toString());
    ExtensionsElement extensions = new ExtensionsElement(location, "",
        Collections.<Object>singletonList(new int[] {100, 199}), Collections.emptyList());
    assertEquals("ExtensionsElement(location=x.proto, documentation=, values=[100..199], options=[])",
        extensions.toString());
  }

  /** Upstream's EnumElement data class has no nestedTypes in its constructor, so none is printed. */
  @Test public void enumElementToStringHasNoNestedTypes() {
    EnumElement element = new EnumElement(Location.get("x.proto"), "E", "", Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());
    assertEquals("EnumElement(location=x.proto, name=E, documentation=, options=[], constants=[], "
        + "reserveds=[])", element.toString());
  }
}
