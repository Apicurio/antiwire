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

package com.squareup.wire.internal;

import com.squareup.wire.Message;
import com.squareup.wire.Syntax;

/** Java name of upstream's {@code reflection.kt} file facade (TASK-33.2). */
public final class ReflectionKt {
  private ReflectionKt() {
  }

  public static <M extends Message<M, B>, B extends Message.Builder<M, B>>
      RuntimeMessageAdapter<M, B> createRuntimeMessageAdapter(
          Class<M> messageType, String typeUrl, Syntax syntax, ClassLoader classLoader,
          boolean writeIdentityValues, boolean preservingProtoFieldNames) {
    return Reflection.createRuntimeMessageAdapter(messageType, typeUrl, syntax, classLoader,
        writeIdentityValues, preservingProtoFieldNames);
  }

  public static <M extends Message<M, B>, B extends Message.Builder<M, B>>
      RuntimeMessageAdapter<M, B> createRuntimeMessageAdapter(
          Class<M> messageType, boolean writeIdentityValues, boolean preservingProtoFieldNames,
          ClassLoader classLoader) {
    return Reflection.createRuntimeMessageAdapter(messageType, writeIdentityValues,
        preservingProtoFieldNames, classLoader);
  }
}
