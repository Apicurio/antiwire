/*
 * Copyright (C) 2018 Square, Inc.
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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import okio.BufferedSink;
import okio.Okio;
import okio.Path;

/**
 * Generate {@code .proto} sources.
 *
 * <p>Port note: upstream's only construction sites are the Gradle plugin (excluded by DEC-6) and
 * API callers; the CLI of {@code WireCompiler} exposes no proto output flag, so the port mirrors
 * exactly that surface. Retained in the initial release scope per the 2026-10-06 maintainer
 * decision recorded in docs/decisions.md (DEC-6 never listed it).
 */
public final class ProtoTarget extends Target {
  private final String outDirectory;

  public ProtoTarget(String outDirectory) {
    this.outDirectory = outDirectory;
  }

  @Override public List<String> getIncludes() {
    return Collections.emptyList();
  }

  @Override public List<String> getExcludes() {
    return Collections.emptyList();
  }

  @Override public boolean getExclusive() {
    return false;
  }

  @Override public String getOutDirectory() {
    return outDirectory;
  }

  @Override public SchemaHandler newHandler() {
    return new ProtoSchemaHandler();
  }

  @Override public Target copyTarget(
      List<String> includes, List<String> excludes, boolean exclusive, String outDirectory) {
    return new ProtoTarget(outDirectory);
  }

  private static final class ProtoSchemaHandler extends SchemaHandler {
    @Override public void handle(Schema schema, Context context) {
      Path outDirectory = context.getOutDirectory();
      // The port's SchemaHandler.handle carries no checked IOException (upstream Kotlin has
      // none), so IO failures propagate unchecked like every other handler in the port.
      createOutDirectory(context);

      for (ProtoFile protoFile : schema.getProtoFiles()) {
        if (!context.inSourcePath(protoFile)
            || isEmpty(protoFile)
            // We never emit the `.proto` files we are embedded within Wire.
            || CoreLoader.isWireRuntimeProto(protoFile.getLocation().getPath())) {
          continue;
        }

        // Upstream: location.path.substringBeforeLast("/", missingDelimiterValue = ".").
        String locationPath = protoFile.getLocation().getPath();
        int lastSlash = locationPath.lastIndexOf('/');
        String relativePath = lastSlash == -1 ? "." : locationPath.substring(0, lastSlash);
        Path outputDirectory = outDirectory.div(relativePath);
        Path outputFilePath = outputDirectory.div(protoFile.name() + ".proto");
        // Deliberate hardening: upstream applies no containment check here (compatibility-matrix).
        checkPathInOutDirectory(outputFilePath, outDirectory);
        context.getLogger().artifactHandled(outputDirectory, locationPath, "Proto");

        try {
          context.getFileSystem().createDirectories(outputFilePath.parent(), false);
          try (BufferedSink sink = Okio.buffer(context.getFileSystem().sink(outputFilePath, false))) {
            sink.writeUtf8(protoFile.toSchema());
          }
        } catch (IOException e) {
          throw new RuntimeException(
              "Error emitting " + outputFilePath + " to " + outDirectory, e);
        }
      }
    }

    private static boolean isEmpty(ProtoFile protoFile) {
      return protoFile.getTypes().isEmpty()
          && protoFile.getServices().isEmpty()
          && protoFile.getExtendList().isEmpty();
    }

    @Override public Path handle(Type type, Context context) {
      return null;
    }

    @Override public List<Path> handle(Service service, Context context) {
      return Collections.emptyList();
    }

    @Override public Path handle(Extend extend, Field field, Context context) {
      return null;
    }
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof ProtoTarget)) return false;
    ProtoTarget that = (ProtoTarget) other;
    return Objects.equals(outDirectory, that.outDirectory);
  }

  @Override public int hashCode() {
    return Objects.hash(outDirectory);
  }

  @Override public String toString() {
    return "ProtoTarget(" + "outDirectory=" + outDirectory + ")";
  }
}
