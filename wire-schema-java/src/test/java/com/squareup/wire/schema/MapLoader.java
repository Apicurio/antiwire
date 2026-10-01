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

import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory Loader for linking tests: serves test-declared .proto sources, delegating the nine
 * wire-runtime protos to {@link CoreLoader} exactly as TASK-12's real loader must.
 */
final class MapLoader implements Loader {
  private final Map<String, String> sources = new LinkedHashMap<>();

  void add(String path, String source) {
    sources.put(path, source);
  }

  @Override public ProtoFile load(String path) {
    String source = sources.get(path);
    if (source == null) {
      if (CoreLoader.isWireRuntimeProto(path)) {
        return CoreLoader.INSTANCE.load(path);
      }
      throw new IllegalArgumentException("unexpected path: " + path);
    }
    ProtoFileElement element = ProtoParser.parse(Location.get(path), source);
    return ProtoFile.get(element);
  }

  @Override public Loader withErrors(ErrorCollector errors) {
    // Like CoreLoader, this loader never fails through the error collector.
    return this;
  }
}
