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
package com.squareup.wire.schema.internal;

import com.squareup.wire.schema.Field;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.MessageType;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.ProtoType;
import com.squareup.wire.schema.Rpc;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.Service;
import com.squareup.wire.schema.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Refactor a schema by moving a proto type declaration.
 *
 * <p>This class attempts to avoid making unnecessary changes to the target schema. For example,
 * it won't remove unused imports if they are unrelated to the types being moved.
 */
public final class TypeMover {
  private final Schema oldSchema;
  private final List<Move> moves;

  /** The working copy of proto files. This is mutated as we perform the moves. */
  private final Map<String, ProtoFile> pathToFile;

  /** Paths that have had types added or removed. */
  private final Set<String> sourceAndTargetPaths = new LinkedHashSet<>();

  /** Indexes for import updates. */
  private final Map<ProtoType, String> typeToPath = new LinkedHashMap<>();
  private final Map<String, Set<ProtoType>> pathToTypes = new LinkedHashMap<>();

  /** Errors accumulated by this move. */
  private final List<String> errors = new ArrayList<>();

  public TypeMover(Schema oldSchema, List<Move> moves) {
    this.oldSchema = oldSchema;
    this.moves = moves;
    this.pathToFile = new LinkedHashMap<>();
    for (ProtoFile protoFile : oldSchema.protoFiles()) {
      pathToFile.put(protoFile.location().path, protoFile);
    }
  }

  public Schema move() {
    // Short circuit on zero moves.
    if (moves.isEmpty()) return oldSchema;

    for (Move move : moves) {
      if (oldSchema.protoFile(move.type) == null) {
        errors.add("cannot move " + move.type + ", it isn't in this schema");
      }
    }
    checkForErrors();

    // Move the types.
    rebuildIndexes();
    for (Move move : moves) {
      String sourcePath = typeToPath.remove(move.type);
      String targetPath = move.targetPath;
      ProtoFile oldSourceProtoFile = pathToFile.get(sourcePath);

      List<Type> sourceTypes = new ArrayList<>(oldSourceProtoFile.types());
      int typeIndex = -1;
      for (int i = 0; i < sourceTypes.size(); i++) {
        if (sourceTypes.get(i).type().equals(move.type)) {
          typeIndex = i;
          break;
        }
      }
      Type movedType = sourceTypes.remove(typeIndex);

      pathToFile.put(sourcePath, oldSourceProtoFile.copy(
          oldSourceProtoFile.location(), oldSourceProtoFile.imports(),
          oldSourceProtoFile.publicImports(), oldSourceProtoFile.weakImports(),
          oldSourceProtoFile.packageName(), sourceTypes, oldSourceProtoFile.services(),
          oldSourceProtoFile.extendList(), oldSourceProtoFile.options(),
          oldSourceProtoFile.syntax()));

      ProtoFile targetProtoFile = pathToFile.containsKey(targetPath)
          ? pathToFile.get(targetPath)
          : emptyCopy(oldSourceProtoFile, targetPath);
      List<Type> targetTypes = new ArrayList<>(targetProtoFile.types());
      targetTypes.add(movedType);
      pathToFile.put(targetPath, targetProtoFile.copy(
          targetProtoFile.location(), targetProtoFile.imports(),
          targetProtoFile.publicImports(), targetProtoFile.weakImports(),
          targetProtoFile.packageName(), targetTypes, targetProtoFile.services(),
          targetProtoFile.extendList(), targetProtoFile.options(), targetProtoFile.syntax()));

      sourceAndTargetPaths.add(sourcePath);
      sourceAndTargetPaths.add(targetPath);
    }

    // Fix imports.
    rebuildIndexes();
    List<ProtoFile> updatedProtoFiles = new ArrayList<>();
    for (ProtoFile protoFile : pathToFile.values()) {
      updatedProtoFiles.add(fixImports(protoFile));
    }

    checkForErrors();

    return new Schema(updatedProtoFiles);
  }

  /** Build an index of types and paths so we know what's where. */
  private void rebuildIndexes() {
    pathToTypes.clear();
    typeToPath.clear();

    for (Map.Entry<String, ProtoFile> entry : pathToFile.entrySet()) {
      String path = entry.getKey();
      ProtoFile protoFile = entry.getValue();
      Set<ProtoType> declaredTypes = new LinkedHashSet<>();
      collectDeclaredTypes(protoFile, declaredTypes);
      pathToTypes.put(path, declaredTypes);
      for (ProtoType protoType : declaredTypes) {
        typeToPath.put(protoType, path);
      }
    }
  }

  private ProtoFile fixImports(ProtoFile protoFile) {
    boolean impacted = sourceAndTargetPaths.contains(protoFile.location().path);
    if (!impacted) {
      for (String path : sourceAndTargetPaths) {
        if (protoFile.imports().contains(path) || protoFile.publicImports().contains(path)) {
          impacted = true;
          break;
        }
      }
    }
    if (!impacted) return protoFile; // This file isn't impacted. Skip it.

    Set<ProtoType> referencedTypes = new LinkedHashSet<>();
    collectReferencedTypes(protoFile, referencedTypes);

    Set<ProtoType> definitelyNeed = new LinkedHashSet<>();
    Set<ProtoType> possiblyDrop = new LinkedHashSet<>();

    for (Move move : moves) {
      if (referencedTypes.contains(move.type)) {
        definitelyNeed.add(move.type);
      } else {
        possiblyDrop.add(move.type);
      }

      // If this file is where the type moved from, we might not need imports for the type's use.
      ProtoFile oldSchemaFile = oldSchema.protoFile(move.type);
      if (oldSchemaFile == null) {
        throw new IllegalStateException("no source file for " + move.type);
      }
      if (oldSchemaFile.location().path.equals(protoFile.location().path)) {
        collectReferencedTypes(getType(move), possiblyDrop);
      }

      // If this file is where the type moved to, we'll need imports for the type's use.
      if (protoFile.location().path.equals(move.targetPath)) {
        collectReferencedTypes(getType(move), definitelyNeed);
      }
    }

    // Promote the possible drop list into a definite drop list.
    Set<String> obsoleteImports = new LinkedHashSet<>();
    for (ProtoType type : possiblyDrop) {
      String path = typeToPath.get(type);
      if (path == null) continue; // Probably a built-in type like string.
      Set<ProtoType> otherTypesInFile = pathToTypes.get(path);
      boolean stillNeeded = false;
      for (ProtoType other : otherTypesInFile) {
        if (referencedTypes.contains(other)) {
          stillNeeded = true;
          break;
        }
      }
      if (stillNeeded) continue;
      obsoleteImports.add(path);
    }

    // Rewrite the imports.
    List<String> newImports = new ArrayList<>(protoFile.imports());
    List<String> newPublicImports = new ArrayList<>(protoFile.publicImports());
    for (ProtoType requiredType : definitelyNeed) {
      String path = typeToPath.get(requiredType);
      if (path == null) continue; // Built-in type like string or int32.
      if (path.equals(protoFile.location().path)) continue; // Don't import self!
      if (newImports.contains(path) || newPublicImports.contains(path)) continue; // Already imported.
      newImports.add(path);
    }
    newImports.removeAll(obsoleteImports);
    newPublicImports.removeAll(obsoleteImports);

    return protoFile.copy(
        protoFile.location(), newImports, newPublicImports, protoFile.weakImports(),
        protoFile.packageName(), protoFile.types(), protoFile.services(),
        protoFile.extendList(), protoFile.options(), protoFile.syntax());
  }

  /** Returns the type that moved. */
  private Type getType(Move move) {
    for (Type type : pathToFile.get(move.targetPath).types()) {
      if (type.type().equals(move.type)) return type;
    }
    throw new NoSuchElementException();
  }

  private static void collectReferencedTypes(ProtoFile protoFile, Set<ProtoType> sink) {
    for (Type type : protoFile.types()) {
      collectReferencedTypes(type, sink);
    }
    for (Service service : protoFile.services()) {
      collectReferencedTypes(service, sink);
    }
  }

  private static void collectReferencedTypes(Type type, Set<ProtoType> sink) {
    for (Type nestedType : type.nestedTypes()) {
      collectReferencedTypes(nestedType, sink);
    }
    if (type instanceof MessageType) {
      for (Field field : ((MessageType) type).fieldsAndOneOfFields()) {
        collectReferencedTypes(field, sink);
      }
    }
  }

  private static void collectReferencedTypes(Service service, Set<ProtoType> sink) {
    for (Rpc rpc : service.rpcs()) {
      collectReferencedTypes(rpc, sink);
    }
  }

  private static void collectReferencedTypes(Rpc rpc, Set<ProtoType> sink) {
    sink.add(rpc.requestType());
    sink.add(rpc.responseType());
  }

  private static void collectReferencedTypes(Field field, Set<ProtoType> sink) {
    sink.add(field.type());
  }

  private static void collectDeclaredTypes(ProtoFile protoFile, Set<ProtoType> sink) {
    for (Type type : protoFile.types()) {
      collectDeclaredTypes(type, sink);
    }
  }

  private static void collectDeclaredTypes(Type type, Set<ProtoType> sink) {
    sink.add(type.type());
    for (Type nestedType : type.nestedTypes()) {
      collectDeclaredTypes(nestedType, sink);
    }
  }

  private static ProtoFile emptyCopy(ProtoFile protoFile, String path) {
    Location location = protoFile.location();
    return protoFile.copy(
        new Location(location.base, path, location.line, location.column),
        Collections.emptyList(), Collections.emptyList(), protoFile.weakImports(),
        protoFile.packageName(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), protoFile.options(), protoFile.syntax());
  }

  private void checkForErrors() {
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(String.join("\n", errors));
    }
  }

  public static final class Move {
    public final ProtoType type;
    public final String targetPath;

    public Move(ProtoType type, String targetPath) {
      this.type = type;
      this.targetPath = targetPath;
    }

    @Override public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof Move)) return false;
      Move that = (Move) other;
      return Objects.equals(type, that.type) && Objects.equals(targetPath, that.targetPath);
    }

    @Override public int hashCode() {
      int result = Objects.hashCode(type);
      result = 31 * result + Objects.hashCode(targetPath);
      return result;
    }

    @Override public String toString() {
      return "Move(type=" + type + ", targetPath=" + targetPath + ")";
    }
  }
}
