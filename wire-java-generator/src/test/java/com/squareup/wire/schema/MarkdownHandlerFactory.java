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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okio.BufferedSink;
import okio.Okio;

/** Sample handler factory for WireRunTest, from upstream wire-compiler's MarkdownHandler.kt. */
public class MarkdownHandlerFactory implements SchemaHandler.Factory {
  @Override public SchemaHandler create(
      List<String> includes,
      List<String> excludes,
      boolean exclusive,
      String outDirectory,
      java.util.Map<String, String> options) {
    return new MarkdownHandler();
  }
}

/** This is a sample handler that writes text files that describe types. */
final class MarkdownHandler extends SchemaHandler {
  @Override public okio.Path handle(Type type, Context context) {
    try {
      return writeMarkdownFile(type.type(), toMarkdown(type), context);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override public List<okio.Path> handle(Service service, Context context) {
    List<okio.Path> result = new ArrayList<>();
    try {
      result.add(writeMarkdownFile(service.type(), toMarkdown(service), context));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return result;
  }

  @Override public okio.Path handle(Extend extend, Field field, Context context) {
    return null;
  }

  private okio.Path writeMarkdownFile(
      ProtoType protoType, String markdown, Context context) throws IOException {
    okio.Path outDirectory = context.outDirectory();
    okio.FileSystem fileSystem = context.fileSystem();
    okio.Path path = outDirectory;
    for (String part : toPath(protoType)) {
      path = path.div(part);
    }
    fileSystem.createDirectories(path.parent(), false);
    try (okio.Sink sink = fileSystem.sink(path, false);
        BufferedSink bufferedSink = Okio.buffer(sink)) {
      bufferedSink.writeUtf8(markdown);
    }
    return path;
  }

  /** Returns a path like {@code squareup/colors/Blue.md}. */
  private static List<String> toPath(ProtoType protoType) {
    List<String> result = new ArrayList<>();
    for (String part : protoType.toString().split("\\.")) {
      result.add(part);
    }
    result.set(result.size() - 1, result.get(result.size() - 1) + ".md");
    return result;
  }

  private static String toMarkdown(Type type) {
    return "#" + " " + type.type().simpleName() + "\n\n" + type.documentation() + "\n";
  }

  private static String toMarkdown(Service service) {
    return "#" + " " + service.type().simpleName() + "\n\n" + service.documentation() + "\n";
  }
}
