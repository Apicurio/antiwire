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
package com.squareup.wire.testing;

import com.squareup.wire.ProtoAdapter;
import java.lang.reflect.Method;

/**
 * Reflection helpers for tests that drive generated model classes loaded at test time (see
 * {@link TestCompilers}): the generated classes are compiled into child classloaders, so tests
 * reach their fields, adapters, and builder setters reflectively.
 */
public final class GeneratedMessages {
  private GeneratedMessages() {
  }

  /** The adapter the generated class declares; fails if the class does not declare one. */
  @SuppressWarnings("unchecked")
  public static ProtoAdapter<Object> adapterOf(Class<?> messageClass) throws Exception {
    return (ProtoAdapter<Object>) messageClass.getField("ADAPTER").get(null);
  }

  /** The value of the generated public field {@code name} on {@code message}. */
  public static Object field(Class<?> messageClass, Object message, String name)
      throws Exception {
    return messageClass.getField(name).get(message);
  }

  /** Invokes the one-argument builder setter {@code setter} with {@code value}. */
  public static void invoke(Object builder, String setter, Object value) throws Exception {
    for (Method method : builder.getClass().getMethods()) {
      if (method.getName().equals(setter) && method.getParameterCount() == 1) {
        method.invoke(builder, value);
        return;
      }
    }
    throw new IllegalStateException("no setter " + setter + " on " + builder.getClass());
  }
}
