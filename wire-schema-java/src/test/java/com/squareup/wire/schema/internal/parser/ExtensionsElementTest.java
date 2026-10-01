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

import static com.squareup.wire.schema.internal.parser.OptionElement.Kind.MAP;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.SchemaUtil;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Upstream ExtensionsElementTest translated (assertk to JUnit 5). */
public class ExtensionsElementTest {
  private final Location location = Location.get("file.proto");

  @Test public void singleValueToSchema() {
    ExtensionsElement actual = new ExtensionsElement(location, "",
        Collections.singletonList(500), Collections.emptyList());
    assertEquals("extensions 500;\n", actual.toSchema());
  }

  @Test public void rangeToSchema() {
    ExtensionsElement actual = new ExtensionsElement(location, "",
        Collections.singletonList(new int[] {500, 505}), Collections.emptyList());
    assertEquals("extensions 500 to 505;\n", actual.toSchema());
  }

  @Test public void maxRangeToSchema() {
    ExtensionsElement actual = new ExtensionsElement(location, "",
        Collections.singletonList(new int[] {500, SchemaUtil.MAX_TAG_VALUE}),
        Collections.emptyList());
    assertEquals("extensions 500 to max;\n", actual.toSchema());
  }

  @Test public void withDocumentationToSchema() {
    ExtensionsElement actual = new ExtensionsElement(location, "Hello",
        Collections.singletonList(500), Collections.emptyList());
    assertEquals("// Hello\n"
        + "extensions 500;\n", actual.toSchema());
  }

  @Test public void withOptions() {
    ExtensionsElement actual = new ExtensionsElement(location.at(3, 3), "",
        Collections.singletonList(new int[] {1000, 9994}),
        Arrays.asList(
            OptionElement.create("declaration", MAP, features(".pb.cpp", ".pb.CppFeatures")),
            OptionElement.create("declaration", MAP, features(".pb.java", ".pb.JavaFeatures")),
            OptionElement.create("declaration", MAP, features(".pb.go", ".pb.GoFeatures")),
            OptionElement.create("declaration", MAP,
                features(".pb.proto1", ".pb.Proto1Features"))));
    assertEquals("extensions 1000 to 9994 [\n"
        + "  declaration = {\n"
        + "    full_name: \".pb.cpp\",\n"
        + "    type: \".pb.CppFeatures\"\n"
        + "  },\n"
        + "  declaration = {\n"
        + "    full_name: \".pb.java\",\n"
        + "    type: \".pb.JavaFeatures\"\n"
        + "  },\n"
        + "  declaration = {\n"
        + "    full_name: \".pb.go\",\n"
        + "    type: \".pb.GoFeatures\"\n"
        + "  },\n"
        + "  declaration = {\n"
        + "    full_name: \".pb.proto1\",\n"
        + "    type: \".pb.Proto1Features\"\n"
        + "  }\n"
        + "];\n", actual.toSchema());
  }

  private static Map<String, Object> features(String fullName, String type) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("full_name", fullName);
    map.put("type", type);
    return map;
  }
}
