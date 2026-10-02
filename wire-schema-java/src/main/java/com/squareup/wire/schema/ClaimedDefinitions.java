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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link ClaimedDefinitions} tracks handled objects: {@link Type}s, {@link Service}s, and {@link
 * Field}s. A {@link SchemaHandler} is to first check if an object has already been claimed; if
 * yes, it is not to handle it. Otherwise, the {@link SchemaHandler} is to handle the object and
 * {@link #claim} it. It is an error for a {@link SchemaHandler} to handle an object which has
 * already been claimed.
 */
public final class ClaimedDefinitions {
  private final Set<ProtoType> types = new LinkedHashSet<>();
  private final Set<ProtoMember> members = new LinkedHashSet<>();

  /** Tracks that {@code type} has been handled. */
  public void claim(ProtoType type) {
    if (!types.add(type)) {
      throw new IllegalStateException("Type " + type + " already claimed");
    }
  }

  /** Tracks that {@code member} has been handled. */
  public void claim(ProtoMember member) {
    if (!members.add(member)) {
      throw new IllegalStateException("member " + member + " already claimed");
    }
  }

  /** Tracks that {@code type} has been handled. */
  public void claim(Type type) {
    claim(type.type());
  }

  /** Tracks that {@code service} has been handled. */
  public void claim(Service service) {
    claim(service.type());
  }

  /** Returns true if {@code type} has already been handled. */
  public boolean contains(ProtoType type) {
    return types.contains(type);
  }

  /** Returns true if {@code member} has already been handled. */
  public boolean contains(ProtoMember member) {
    return members.contains(member);
  }

  /** Returns true if {@code type} has already been handled. */
  public boolean contains(Type type) {
    return contains(type.type());
  }

  /** Returns true if {@code service} has already been handled. */
  public boolean contains(Service service) {
    return contains(service.type());
  }
}
