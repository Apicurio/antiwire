/*
 * Copyright (C) 2016 Square, Inc.
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

import java.util.Objects;

/**
 * A constant field that identifies a {@code ProtoAdapter}. This should be a string like like
 * {@code com.squareup.dinosaurs.Dinosaur#ADAPTER} with a fully qualified class name, a {@code
 * #}, and a field name.
 *
 * <p>Upstream (jvmMain) carries this name as both a JavaPoet and a KotlinPoet {@code ClassName};
 * the port stores the class name as a string and drops the KotlinPoet duplicate, which is
 * always the same string, so the schema module stays free of poet dependencies (OPEN-2).
 */
public final class AdapterConstant {
  public final String javaClassName;
  public final String memberName;

  public AdapterConstant(String javaClassName, String memberName) {
    this.javaClassName = javaClassName;
    this.memberName = memberName;
  }

  /**
   * Returns an adapter constant for a string like {@code
   * com.squareup.dinosaurs.Dinosaur#ADAPTER}.
   */
  public static AdapterConstant get(String adapter) {
    String[] names = adapter.split("#", -1);
    if (names.length != 2) {
      throw new IllegalArgumentException("Illegally formatted adapter: " + adapter + ".");
    }
    return new AdapterConstant(names[0], names[1]);
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof AdapterConstant)) return false;
    AdapterConstant that = (AdapterConstant) other;
    return Objects.equals(javaClassName, that.javaClassName)
        && Objects.equals(memberName, that.memberName);
  }

  @Override public int hashCode() {
    int result = Objects.hashCode(javaClassName);
    result = 31 * result + Objects.hashCode(memberName);
    return result;
  }

  @Override public String toString() {
    return "AdapterConstant(javaClassName=" + javaClassName + ", memberName=" + memberName + ")";
  }

  /** Mirror of the Kotlin companion object: lets Java callers write {@code AdapterConstant.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }

    public AdapterConstant invoke(String adapter) {
      return AdapterConstant.get(adapter);
    }
  }
}
