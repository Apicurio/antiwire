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
package com.squareup.wire;

/** Syntax version. */
public enum Syntax {
  PROTO_2("proto2"),
  PROTO_3("proto3");

  private final String string;

  Syntax(String string) {
    this.string = string;
  }

  @Override public String toString() {
    return string;
  }

  public static Syntax get(String string) {
    // Non-null parameter upstream (Kotlin checkNotNullParameter guard: NPE, not IAE).
    java.util.Objects.requireNonNull(string);
    for (Syntax syntax : values()) {
      if (syntax.string.equals(string)) return syntax;
    }
    throw new IllegalArgumentException("unexpected syntax: " + string);
  }

  /** Kotlin companion mirror: Java may write {@code Syntax.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }

    public Syntax get(String string) {
      return Syntax.get(string);
    }
  }
}
