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

import com.squareup.wire.schema.internal.ProfileFileElement;
import com.squareup.wire.schema.internal.TypeConfigElement;
import java.util.Collections;
import java.util.List;

/**
 * Describes how to map {@code .proto} to {@code .java}. A single repository of {@code .proto}
 * files may have multiple profiles; for example a project may target both Android and Java.
 *
 * <p>Upstream is an {@code expect class} whose JVM {@code actual} exposes poet-typed
 * {@code javaTarget} and {@code kotlinTarget} accessors; this port returns the configured target
 * string, keeping the schema module free of poet dependencies (OPEN-2). {@code kotlinTarget} is
 * dropped with the excluded Kotlin generator (DEC-6).
 */
public final class Profile {
  private final List<ProfileFileElement> profileFiles;

  public Profile(List<ProfileFileElement> profileFiles) {
    this.profileFiles = profileFiles;
  }

  public Profile() {
    this(Collections.emptyList());
  }

  /** Returns the configured Java type name for {@code type}, or null if it is not configured. */
  public String javaTarget(ProtoType type) {
    TypeConfigElement typeConfig = typeConfig(type);
    return typeConfig != null ? typeConfig.target : null;
  }

  /** Returns the adapter constant for {@code type}, or null if it is not configured. */
  public AdapterConstant getAdapter(ProtoType type) {
    TypeConfigElement typeConfig = typeConfig(type);
    return typeConfig != null ? AdapterConstant.get(typeConfig.adapter) : null;
  }

  /** Returns the config for {@code type}, or null if it is not configured. */
  private TypeConfigElement typeConfig(ProtoType type) {
    for (ProfileFileElement profileFile : profileFiles) {
      for (TypeConfigElement typeConfig : profileFile.typeConfigs) {
        if (type.toString().equals(typeConfig.type)) {
          return typeConfig;
        }
      }
    }
    return null;
  }
}
