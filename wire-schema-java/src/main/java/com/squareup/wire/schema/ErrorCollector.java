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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects errors to be reported as a batch. Errors include both a detail message plus context of
 * where they occurred within the schema.
 */
public final class ErrorCollector {
  private final List<String> errorsBuilder;
  private final List<Object> contextStack;

  public ErrorCollector() {
    this.errorsBuilder = new ArrayList<>();
    this.contextStack = Collections.emptyList();
  }

  private ErrorCollector(ErrorCollector enclosing, List<Object> contextStack) {
    this.errorsBuilder = enclosing.errorsBuilder;
    this.contextStack = contextStack;
  }

  public List<String> errors() {
    return new ArrayList<>(errorsBuilder);
  }

  /**
   * Returns a copy of this error collector that includes {@code additionalContext} in error
   * messages reported to it. The current and returned instance both contribute errors to the same
   * list.
   */
  public ErrorCollector at(Object additionalContext) {
    List<Object> stack = new ArrayList<>(contextStack);
    stack.add(additionalContext);
    return new ErrorCollector(this, stack);
  }

  /** Add {@code message} as an error to this collector. */
  public void add(String message) {
    StringBuilder error = new StringBuilder();
    error.append(message);

    List<Object> stack = new ArrayList<>(contextStack);
    boolean anyNonFile = false;
    for (Object context : stack) {
      if (!(context instanceof ProtoFile)) anyNonFile = true;
    }
    if (anyNonFile) {
      stack.removeIf(context -> context instanceof ProtoFile);
    }
    for (int i = stack.size() - 1; i >= 0; i--) {
      Object context = stack.get(i);
      String prefix = i == stack.size() - 1 ? "\n  for" : "\n  in";

      if (context instanceof Rpc) {
        error.append(prefix).append(" rpc ").append(((Rpc) context).name())
            .append(" (").append(((Rpc) context).location()).append(")");
      } else if (context instanceof Field) {
        error.append(prefix).append(" field ").append(((Field) context).name())
            .append(" (").append(((Field) context).location()).append(")");
      } else if (context instanceof MessageType) {
        error.append(prefix).append(" message ").append(((MessageType) context).type())
            .append(" (").append(((MessageType) context).location()).append(")");
      } else if (context instanceof EnumConstant) {
        error.append(prefix).append(" constant ").append(((EnumConstant) context).name())
            .append(" (").append(((EnumConstant) context).location()).append(")");
      } else if (context instanceof EnumType) {
        error.append(prefix).append(" enum ").append(((EnumType) context).type())
            .append(" (").append(((EnumType) context).location()).append(")");
      } else if (context instanceof Service) {
        error.append(prefix).append(" service ").append(((Service) context).type())
            .append(" (").append(((Service) context).location()).append(")");
      } else if (context instanceof Extensions) {
        error.append(prefix).append(" extensions (")
            .append(((Extensions) context).location()).append(")");
      } else if (context instanceof ProtoFile) {
        error.append(prefix).append(" file ").append(((ProtoFile) context).location());
      } else if (context instanceof Extend) {
        Extend extend = (Extend) context;
        if (extend.type() != null) {
          error.append(prefix).append(" extend ").append(extend.type())
              .append(" (").append(extend.location()).append(")");
        } else {
          error.append(prefix).append(" extend (").append(extend.location()).append(")");
        }
      }
    }
    errorsBuilder.add(error.toString());
  }

  public void throwIfNonEmpty() {
    if (!errorsBuilder.isEmpty()) {
      throw new SchemaException(errorsBuilder);
    }
  }
}
