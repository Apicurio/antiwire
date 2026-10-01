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

import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import java.io.FileNotFoundException;
import java.io.IOException;
import okio.BufferedSource;
import okio.FileSystem;
import okio.Okio;
import okio.Path;

/**
 * A loader that can only load built-in {@code .proto} files:
 *
 * <ul>
 *   <li>Google's protobuf descriptor, which defines standard options like {@code default},
 *       {@code deprecated}, and {@code java_package}.
 *   <li>Wire's extensions, which defines since and until options.
 * </ul>
 *
 * <p>If the user has provided their own version of these protos, those are preferred.
 */
public final class CoreLoader implements Loader {
  /** Upstream declares CoreLoader as a Kotlin object; this is its singleton. */
  public static final CoreLoader INSTANCE = new CoreLoader();

  private final FileSystem resourceFileSystem =
      FileSystem.asResourceFileSystem(CoreLoader.class.getClassLoader());

  static final String DESCRIPTOR_PROTO = "google/protobuf/descriptor.proto";
  static final String WIRE_EXTENSIONS_PROTO = "wire/extensions.proto";

  private static final String ANY_PROTO = "google/protobuf/any.proto";
  private static final String DURATION_PROTO = "google/protobuf/duration.proto";
  private static final String EMPTY_PROTO = "google/protobuf/empty.proto";
  private static final String FIELD_MASK_PROTO = "google/protobuf/field_mask.proto";
  private static final String STRUCT_PROTO = "google/protobuf/struct.proto";
  private static final String TIMESTAMP_PROTO = "google/protobuf/timestamp.proto";
  private static final String WRAPPERS_PROTO = "google/protobuf/wrappers.proto";

  /** A special base directory used for Wire's built-in .proto files. */
  public static final String WIRE_RUNTIME_JAR = "wire-runtime.jar";

  @Override public ProtoFile load(String path) {
    if (!isWireRuntimeProto(path)) {
      throw new IllegalStateException("unexpected load: " + path);
    }
    try {
      BufferedSource source = Okio.buffer(resourceFileSystem.source(Path.get("/").div(path)));
      try {
        String data = source.readUtf8();
        Location location = Location.get(path);
        ProtoFileElement element = ProtoParser.parse(location, data);
        return ProtoFile.get(element);
      } finally {
        source.close();
      }
    } catch (FileNotFoundException e) {
      throw new IllegalStateException("unexpected load: " + path, e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override public Loader withErrors(ErrorCollector errors) {
    return this;
  }

  public static boolean isWireRuntimeProto(Location location) {
    return WIRE_RUNTIME_JAR.equals(location.base) && isWireRuntimeProto(location.path);
  }

  /** Returns true if {@code path} is bundled in the wire runtime. */
  public static boolean isWireRuntimeProto(String path) {
    return path.equals(ANY_PROTO)
        || path.equals(DESCRIPTOR_PROTO)
        || path.equals(DURATION_PROTO)
        || path.equals(EMPTY_PROTO)
        || path.equals(FIELD_MASK_PROTO)
        || path.equals(STRUCT_PROTO)
        || path.equals(TIMESTAMP_PROTO)
        || path.equals(WRAPPERS_PROTO)
        || path.equals(WIRE_EXTENSIONS_PROTO);
  }
}
