/*
 * Copyright (C) 2013 Square, Inc.
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
package com.squareup.wire;

/**
 * Returns {@code value} if it is not null; {@code defaultValue} otherwise. This is used to
 * conveniently return a default value when a value is null, e.g.
 * {@code Wire.get(myProto.f, MyProto.f_default)}.
 */
public final class Wire {
  private Wire() {
  }

  public static <T> T get(T value, T defaultValue) {
    return value != null ? value : defaultValue;
  }
}
