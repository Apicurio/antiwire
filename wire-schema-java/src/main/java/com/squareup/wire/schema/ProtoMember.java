/*
 * Copyright (C) 2015 Square, Inc.
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

/**
 * Identifies a field, enum or RPC on a declaring type. Members are encoded as strings containing
 * a type name, a hash, and a member name, like {@code squareup.dinosaurs.Dinosaur#length_meters}.
 */
public final class ProtoMember {
  private final ProtoType type;

  public ProtoType getType() {
    return type;
  }

  private final String member;

  public String getMember() {
    return member;
  }

  private ProtoMember(ProtoType type, String member) {
    if (type.isScalar()) {
      throw new IllegalArgumentException("scalars cannot have members");
    }
    this.type = type;
    this.member = member;
  }

  public String getSimpleName() {
    // Strip the package prefix for extension fields.
    return member.substring(member.lastIndexOf('.') + 1);
  }

  @Override public boolean equals(Object other) {
    return other instanceof ProtoMember
        && type.equals(((ProtoMember) other).type)
        && member.equals(((ProtoMember) other).member);
  }

  @Override public int hashCode() {
    return type.hashCode() * 37 + member.hashCode();
  }

  @Override public String toString() {
    return type + "#" + member;
  }

  public static ProtoMember get(String typeAndMember) {
    int hash = typeAndMember.indexOf('#');
    if (hash == -1) {
      throw new IllegalArgumentException("expected a '#' in " + typeAndMember);
    }
    ProtoType type = ProtoType.get(typeAndMember.substring(0, hash));
    String member = typeAndMember.substring(hash + 1);
    return new ProtoMember(type, member);
  }

  public static ProtoMember get(ProtoType type, String member) {
    return new ProtoMember(type, member);
  }

  public static ProtoMember get(ProtoType type, Field field) {
    String member = field.isExtension() ? field.getQualifiedName() : field.getName();
    return new ProtoMember(type, member);
  }

  /** Mirror of the Kotlin companion object: lets Java callers write {@code ProtoMember.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }

    public ProtoMember get(ProtoType type, Field field) {
      return ProtoMember.get(type, field);
    }

    public ProtoMember get(ProtoType type, String member) {
      return ProtoMember.get(type, member);
    }

    public ProtoMember get(String typeAndMember) {
      return ProtoMember.get(typeAndMember);
    }
  }
}
