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

import com.squareup.wire.ProtoAdapter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A collection of .proto files that describe a set of messages. A schema is <em>linked</em>:
 * each field's type name is resolved to the corresponding type definition.
 *
 * <p>Use a schema loader to load a schema from source files.
 */
public final class Schema {
  private final List<ProtoFile> protoFiles;
  private final Map<ProtoType, ProtoFile> protoFilesIndex;
  private final Map<String, Type> typesIndex;
  private final Map<String, Service> servicesIndex;

  /**
   * Upstream declares this constructor {@code internal}; it is public here because the port's
   * {@code com.squareup.wire.schema.internal} package (TypeMover, withStubs) constructs schemas,
   * and upstream JVM bytecode exposes the internal constructor as public anyway.
   */
  public Schema(Iterable<ProtoFile> protoFiles) {
    List<ProtoFile> sorted = new ArrayList<>();
    for (ProtoFile protoFile : protoFiles) {
      sorted.add(protoFile);
    }
    sorted.sort(Comparator.comparing(protoFile -> protoFile.location.getPath()));
    this.protoFiles = sorted;

    // Insertion-ordered like upstream's mutableMapOf: types() iterates protoFilesIndex.keySet()
    // and Apicurio's descriptor conversion emits entries in that order (TASK-18 Oracle A).
    Map<ProtoType, ProtoFile> index = new LinkedHashMap<>();
    this.typesIndex = buildTypesIndex(this.protoFiles, index);
    this.servicesIndex = buildServicesIndex(this.protoFiles, index);
    this.protoFilesIndex = index;
  }

  public List<ProtoFile> getProtoFiles() {
    return protoFiles;
  }

  public Set<ProtoType> getTypes() {
    return protoFilesIndex.keySet();
  }

  /** Returns the proto file at {@code path}, or null if this schema has no such file. */
  public ProtoFile protoFile(String path) {
    for (ProtoFile protoFile : protoFiles) {
      if (protoFile.location.getPath().equals(path)) return protoFile;
    }
    return null;
  }

  /** Returns the proto file containing this {@code protoType}, or null if there isn't one. */
  public ProtoFile protoFile(ProtoType protoType) {
    return protoFilesIndex.get(protoType);
  }

  /**
   * Returns a copy of this schema that retains only the types and services selected by
   * {@code pruningRules}, plus their transitive dependencies.
   */
  public Schema prune(PruningRules pruningRules) {
    return new Pruner(this, pruningRules).prune();
  }

  /**
   * Returns the service with the fully qualified name {@code name}, or null if this schema
   * defines no such service.
   */
  public Service getService(String name) {
    return servicesIndex.get(name);
  }

  /** Returns the service for {@code protoType}, or null if this schema defines no such service. */
  public Service getService(ProtoType protoType) {
    return getService(protoType.toString());
  }

  /**
   * Returns the type with the fully qualified name {@code name}, or null if this schema defines
   * no such type.
   */
  public Type getType(String name) {
    return typesIndex.get(name);
  }

  /** Returns the type for {@code protoType}, or null if this schema defines no such type. */
  public Type getType(ProtoType protoType) {
    return getType(protoType.toString());
  }

  /** Returns the field for {@code protoMember}, or null if this schema defines no such field. */
  public Field getField(ProtoMember protoMember) {
    Type type = getType(protoMember.getType());
    if (!(type instanceof MessageType)) return null;
    MessageType messageType = (MessageType) type;
    Field field = messageType.field(protoMember.getMember());
    if (field != null) return field;
    return messageType.extensionField(protoMember.getMember());
  }

  /**
   * Returns the field with the fully qualified {@code typeName} and {@code memberName}, or null
   * if this schema defines no such field.
   */
  public Field getField(String typeName, String memberName) {
    return getField(ProtoType.get(typeName), memberName);
  }

  /**
   * Returns the field for {@code protoType} and {@code memberName}, or null if this schema
   * defines no such field.
   */
  public Field getField(ProtoType protoType, String memberName) {
    return getField(ProtoMember.get(protoType, memberName));
  }

  /**
   * Returns a wire adapter for the message or enum type named {@code typeName}. The returned
   * type adapter doesn't have model classes to encode and decode from, so instead it uses scalar
   * types ({@link String}, {@link com.squareup.wire.Bytes}, {@link Integer}, etc.), maps, lists,
   * and corresponding classes to proto3 types (e.g. {@link java.time.Instant} for
   * {@code google.protobuf.Timestamp}.) It can both encode and decode these objects. Map keys
   * are field names.
   *
   * <p>{@code google.protobuf.Empty} fields decode to {@link ProtoAdapter.UnitValue#INSTANCE}
   * and encode from it, standing in for upstream's {@code kotlin.Unit}; a {@code null} value
   * means the field is absent (docs/api-surface.md).
   *
   * @param includeUnknown true to include values for unknown tags in the returned model. Map
   *     keys for such values is the unknown value's tag name as a string. Unknown values are
   *     decoded to {@link Long}, {@link Long}, {@link Integer}, or {@link com.squareup.wire.Bytes}
   *     for {@link com.squareup.wire.FieldEncoding#VARINT}, {@link
   *     com.squareup.wire.FieldEncoding#FIXED64}, {@link com.squareup.wire.FieldEncoding#FIXED32},
   *     or {@link com.squareup.wire.FieldEncoding#LENGTH_DELIMITED}, respectively.
   */
  public ProtoAdapter<Object> protoAdapter(String typeName, boolean includeUnknown) {
    Type type = getType(typeName);
    if (type == null) {
      throw new IllegalArgumentException("unexpected type " + typeName);
    }
    return new SchemaProtoAdapterFactory(this, includeUnknown).get(type.getType());
  }

  public boolean isExtensionField(ProtoMember protoMember) {
    Type type = getType(protoMember.getType());
    return type instanceof MessageType
        && ((MessageType) type).extensionField(protoMember.getMember()) != null;
  }

  private static Map<String, Type> buildTypesIndex(List<ProtoFile> protoFiles,
      Map<ProtoType, ProtoFile> protoFilesIndex) {
    Map<String, Type> typesByName = new LinkedHashMap<>();

    for (ProtoFile protoFile : protoFiles) {
      for (Type type : protoFile.types) {
        index(type, protoFile, protoFilesIndex, typesByName);
      }
    }
    return typesByName;
  }

  private static void index(Type type, ProtoFile protoFile,
      Map<ProtoType, ProtoFile> protoFilesIndex, Map<String, Type> typesByName) {
    ProtoType protoType = type.getType();
    if (!protoFilesIndex.containsKey(protoType)) {
      protoFilesIndex.put(protoType, protoFile);
    }
    typesByName.put(protoType.toString(), type);
    for (Type nested : type.getNestedTypes()) {
      index(nested, protoFile, protoFilesIndex, typesByName);
    }
  }

  private static Map<String, Service> buildServicesIndex(List<ProtoFile> protoFiles,
      Map<ProtoType, ProtoFile> protoFilesIndex) {
    Map<String, Service> result = new LinkedHashMap<>();
    for (ProtoFile protoFile : protoFiles) {
      for (Service service : protoFile.services) {
        result.put(service.type().toString(), service);
        protoFilesIndex.put(service.type(), protoFile);
      }
    }
    return result;
  }

  /** Kotlin companion mirror: Java may write {@code Schema.Companion.m(...)}. */
  public static final Companion Companion = new Companion();

  public static final class Companion {
    private Companion() {
    }
  }
}
