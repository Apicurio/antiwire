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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory Loader for linking tests: serves test-declared .proto sources, falling back to the
 * runtime protos bundled on the classpath (mirroring upstream's CoreLoader behavior for
 * descriptor.proto and wire/extensions.proto).
 */
final class MapLoader implements Loader {
  private final Map<String, String> sources = new LinkedHashMap<>();
  private ErrorCollector errors = new ErrorCollector();

  void add(String path, String source) {
    sources.put(path, source);
  }

  @Override public ProtoFile load(String path) {
    String source = sources.get(path);
    if (source == null) {
      source = loadRuntimeResource(path);
    }
    if (source == null) {
      throw new IllegalArgumentException("unexpected path: " + path);
    }
    ProtoFileElement element = ProtoParser.parse(Location.get(path), source);
    return ProtoFile.get(element);
  }

  private static String loadRuntimeResource(String path) {
    InputStream stream = MapLoader.class.getClassLoader().getResourceAsStream(path);
    if (stream == null) return null;
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int read;
      while ((read = stream.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        stream.close();
      } catch (IOException ignored) {
      }
    }
  }

  @Override public Loader withErrors(ErrorCollector errors) {
    MapLoader result = new MapLoader();
    result.sources.putAll(sources);
    result.errors = errors;
    return result;
  }
}
