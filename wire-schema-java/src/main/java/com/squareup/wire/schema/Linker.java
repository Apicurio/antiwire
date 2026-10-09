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

import com.squareup.wire.schema.internal.SchemaUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Links local field types and option types to the corresponding declarations. */
public class Linker {
  private final Loader loader;
  private final Map<String, FileLinker> fileLinkers;
  private final ArrayDeque<FileLinker> fileOptionsQueue;
  private final Map<String, Type> protoTypeNames;
  private final List<Object> contextStack;
  private final Set<ProtoType> requestedTypes;
  private final Set<Field> requestedFields;
  private final boolean permitPackageCycles;
  private final List<ProtoType> opaqueTypes;
  final boolean loadExhaustively;

  /** Errors accumulated by this load. */
  private final ErrorCollector errors;

  public ErrorCollector getErrors() {
    return errors;
  }

  public Linker(Loader loader, ErrorCollector errors, boolean permitPackageCycles,
      boolean loadExhaustively, List<ProtoType> opaqueTypes) {
    this.loader = loader;
    this.fileLinkers = new LinkedHashMap<>();
    this.fileOptionsQueue = new ArrayDeque<>();
    this.protoTypeNames = new HashMap<>();
    this.contextStack = Collections.emptyList();
    this.requestedTypes = new LinkedHashSet<>();
    this.requestedFields = new LinkedHashSet<>();
    this.errors = errors;
    this.permitPackageCycles = permitPackageCycles;
    this.loadExhaustively = loadExhaustively;
    this.opaqueTypes = opaqueTypes;
  }

  public Linker(Loader loader, ErrorCollector errors, boolean permitPackageCycles,
      boolean loadExhaustively) {
    this(loader, errors, permitPackageCycles, loadExhaustively, Collections.emptyList());
  }

  private Linker(Linker enclosing, Object additionalContext) {
    this.loader = enclosing.loader;
    this.fileLinkers = enclosing.fileLinkers;
    this.fileOptionsQueue = enclosing.fileOptionsQueue;
    this.protoTypeNames = enclosing.protoTypeNames;
    List<Object> stack = new ArrayList<>(enclosing.contextStack);
    stack.add(additionalContext);
    this.contextStack = stack;
    this.requestedTypes = enclosing.requestedTypes;
    this.requestedFields = enclosing.requestedFields;
    this.errors = enclosing.errors.at(additionalContext);
    this.permitPackageCycles = false;
    this.loadExhaustively = enclosing.loadExhaustively;
    this.opaqueTypes = enclosing.opaqueTypes;
  }

  /** Returns a linker for {@code path}, loading the file if necessary. */
  FileLinker getFileLinker(String path) {
    FileLinker existing = fileLinkers.get(path);
    if (existing != null) return existing;

    ProtoFile protoFile = loader.withErrors(errors).load(path);
    FileLinker result = new FileLinker(protoFile, withContext(protoFile));
    fileLinkers.put(path, result);
    fileOptionsQueue.add(result);
    return result;
  }

  /**
   * Link all features of all files in {@code sourceProtoFiles} to create a schema. This will
   * also partially link any imported files necessary.
   */
  public Schema link(Iterable<ProtoFile> sourceProtoFiles) {
    List<FileLinker> sourceFiles = new ArrayList<>();
    for (ProtoFile sourceFile : sourceProtoFiles) {
      FileLinker fileLinker = new FileLinker(sourceFile, withContext(sourceFile));
      fileLinkers.put(sourceFile.location.getPath(), fileLinker);
      sourceFiles.add(fileLinker);
    }

    // Ensure linking the descriptor.proto and wire_options.proto, if not provided. This ensures
    // we can resolve our java_package and wire_package options.
    if (fileLinkers.get(CoreLoader.DESCRIPTOR_PROTO) == null) {
      sourceFiles.add(getFileLinker(CoreLoader.DESCRIPTOR_PROTO));
    }
    if (fileLinkers.get(CoreLoader.WIRE_EXTENSIONS_PROTO) == null) {
      sourceFiles.add(getFileLinker(CoreLoader.WIRE_EXTENSIONS_PROTO));
    }

    // When loading exhaustively, every import (and transitive import!) is a source file.
    if (loadExhaustively) {
      ArrayDeque<FileLinker> queue = new ArrayDeque<>(fileLinkers.values());
      while (true) {
        FileLinker fileLinker = queue.poll();
        if (fileLinker == null) break;
        for (String importPath : concat(fileLinker.protoFile.imports,
            fileLinker.protoFile.publicImports)) {
          if (!fileLinkers.containsKey(importPath)) {
            FileLinker imported = withContext(fileLinker.protoFile).getFileLinker(importPath);
            sourceFiles.add(imported);
            queue.add(imported);
          }
        }
      }
    }

    // The order of the input files shows up in the order of extension fields in the output
    // files. Sort the inputs to get consistent output even when the order of input files is
    // inconsistent.
    sourceFiles.sort(Comparator.comparing(fileLinker -> fileLinker.protoFile.location.getPath()));

    for (FileLinker fileLinker : sourceFiles) {
      fileLinker.requireTypesRegistered();
    }

    for (FileLinker fileLinker : sourceFiles) {
      fileLinker.requireExtensionsLinked();
    }

    for (FileLinker fileLinker : sourceFiles) {
      fileLinker.requireImportedExtensionsRegistered();
    }

    for (FileLinker fileLinker : sourceFiles) {
      fileLinker.linkMembers();
    }

    for (FileLinker fileLinker : sourceFiles) {
      SyntaxRules syntaxRules = SyntaxRules.get(fileLinker.protoFile.syntax);
      fileLinker.linkOptions(syntaxRules, true);
    }

    for (FileLinker fileLinker : sourceFiles) {
      fileLinker.requireImportedExtensionOptionsLinked(false);
    }

    // For compactness we'd prefer to link the options of source files only. But we link file
    // options on referenced files to make sure that java_package is populated.
    while (!fileOptionsQueue.isEmpty()) {
      FileLinker fileLinker = fileOptionsQueue.poll();
      fileLinker.requireFileOptionsLinked(false);
    }

    validatePackages();

    for (FileLinker fileLinker : sourceFiles) {
      SyntaxRules syntaxRules = SyntaxRules.get(fileLinker.protoFile.syntax);
      fileLinker.validate(syntaxRules);
    }

    CycleChecker cycleChecker = new CycleChecker(fileLinkers, errors);
    cycleChecker.checkForImportCycles();
    if (!permitPackageCycles) {
      cycleChecker.checkForPackageCycles();
    }

    errors.throwIfNonEmpty();

    List<ProtoFile> result = new ArrayList<>();
    for (FileLinker fileLinker : fileLinkers.values()) {
      if (sourceFiles.contains(fileLinker)) {
        result.add(fileLinker.protoFile);
        continue;
      }

      // Retain this type if it's used by anything in the source path.
      boolean anyTypeIsUsed = false;
      for (Type type : fileLinker.protoFile.typesAndNestedTypes()) {
        if (requestedTypes.contains(type.getType())) {
          anyTypeIsUsed = true;
          break;
        }
      }
      boolean anyFieldIsUsed = false;
      for (Extend extend : fileLinker.protoFile.extendList) {
        for (Field field : extend.getFields()) {
          if (requestedFields.contains(field)) {
            anyFieldIsUsed = true;
            break;
          }
        }
      }
      if (anyTypeIsUsed || anyFieldIsUsed) {
        result.add(fileLinker.protoFile.retainLinked(requestedTypes, requestedFields));
      }
    }

    return new Schema(result);
  }

  private static List<String> concat(List<String> a, List<String> b) {
    List<String> result = new ArrayList<>(a);
    result.addAll(b);
    return result;
  }

  /** Returns the type name for the scalar, relative or fully-qualified name {@code name}. */
  public ProtoType resolveType(String name) {
    return resolveType(name, false);
  }

  /** Returns the type name for the relative or fully-qualified name {@code name}. */
  public ProtoType resolveMessageType(String name) {
    return resolveType(name, true);
  }

  private ProtoType resolveType(String name, boolean messageOnly) {
    ProtoType type = ProtoType.get(name);

    if (type.isScalar()) {
      if (messageOnly) {
        errors.add("expected a message but was " + name);
      }
      if (opaqueTypes.contains(type)) {
        errors.add("Scalar types like " + type + " cannot be opaqued");
      }
      return type;
    }

    if (type.isMap()) {
      if (messageOnly) {
        errors.add("expected a message but was " + name);
      }
      ProtoType keyType = resolveType(type.getKeyType().toString(), false);
      ProtoType valueType = resolveType(type.getValueType().toString(), false);
      return ProtoType.get(keyType, valueType, name);
    }

    Type resolved = resolve(name, protoTypeNames);
    // If no type could be resolved, load imported files and try again.
    if (resolved == null) {
      for (FileLinker fileLinker : contextImportedTypes()) {
        fileLinker.requireTypesRegistered();
      }
      resolved = resolve(name, protoTypeNames);
    }

    if (resolved == null) {
      errors.add("unable to resolve " + name);
      return ProtoType.BYTES; // Just return any placeholder.
    }

    if (messageOnly && !(resolved instanceof MessageType)) {
      errors.add("expected a message but was " + name);
      return ProtoType.BYTES; // Just return any placeholder.
    }

    if (opaqueTypes.contains(resolved.getType())) {
      if (resolved instanceof EnumType) {
        errors.add("Enums like " + resolved.getType() + " cannot be opaqued");
        return resolved.getType();
      }
      return ProtoType.BYTES;
    }
    requestedTypes.add(resolved.getType());
    return resolved.getType();
  }

  public <T> T resolve(String name, Map<String, T> map) {
    if (name.startsWith(".")) {
      // If name starts with a '.', the rest of it is fully qualified.
      T result = map.get(name.substring(1));
      if (result != null) return result;
    } else {
      // We've got a name suffix, like 'Person' or 'protos.Person'. Start the search from with
      // the longest prefix like foo.bar.Baz.Quux, shortening the prefix until we find a match.
      String prefix = resolveContext();
      while (!prefix.isEmpty()) {
        T result = map.get(prefix + "." + name);
        if (result != null) return result;

        // Strip the last nested class name or package name from the end and try again.
        int dot = prefix.lastIndexOf('.');
        prefix = dot == -1 ? "" : prefix.substring(0, dot);
      }
      T result = map.get(name);
      if (result != null) return result;
    }
    return null;
  }

  public String resolveContext() {
    for (int i = contextStack.size() - 1; i >= 0; i--) {
      Object context = contextStack.get(i);
      if (context instanceof Type) {
        return ((Type) context).getType().toString();
      }
      if (context instanceof ProtoFile) {
        String packageName = ((ProtoFile) context).packageName;
        return packageName != null ? packageName : "";
      }
    }
    throw new IllegalStateException();
  }

  /**
   * Returns the files imported in the current context. These files declare the types that may
   * be resolved.
   */
  List<FileLinker> contextImportedTypes() {
    List<FileLinker> result = new ArrayList<>();
    for (int i = contextStack.size() - 1; i >= 0; i--) {
      Object context = contextStack.get(i);

      Location location = null;
      if (context instanceof ProtoFile) {
        location = ((ProtoFile) context).location;
      } else if (context instanceof Field && ((Field) context).isExtension()) {
        location = ((Field) context).getLocation();
      }

      if (location != null) {
        String path = location.getPath();
        FileLinker fileLinker = getFileLinker(path);
        for (String effectiveImport : fileLinker.effectiveImports()) {
          result.add(getFileLinker(effectiveImport));
        }
      }
    }
    return result;
  }

  /** Adds {@code type}. */
  void addType(ProtoType protoType, Type type) {
    protoTypeNames.put(protoType.toString(), type);
  }

  /** Returns the type or null if it doesn't exist. */
  public Type get(ProtoType protoType) {
    Type result = protoTypeNames.get(protoType.toString());

    // If no type could be resolved, load imported files and try again.
    if (result == null) {
      for (FileLinker fileLinker : contextImportedTypes()) {
        fileLinker.requireTypesRegistered();
      }
      result = protoTypeNames.get(protoType.toString());
    }

    if (result != null) {
      requestedTypes.add(protoType);
    }

    return result;
  }

  /**
   * Returns the type or null if it doesn't exist. Before this returns it ensures members are
   * linked so that options may dereference them.
   */
  public Type getForOptions(ProtoType protoType) {
    Type result = get(protoType);
    if (result == null) return null;

    FileLinker fileLinker = getFileLinker(result.getLocation().getPath());
    fileLinker.requireMembersLinked(result);
    return result;
  }

  /** Mark a field as used in an option so its file is retained in the schema. */
  void request(Field field) {
    requestedFields.add(field);
  }

  /** Returns the field named {@code field} on the message type of {@code protoType}. */
  public Field dereference(ProtoType protoType, String field) {
    if (field.startsWith("[") && field.endsWith("]")) {
      field = field.substring(1, field.length() - 1);
    }

    Type type = getForOptions(protoType);
    if (type instanceof MessageType) {
      MessageType messageType = (MessageType) type;
      Field messageField = messageType.field(field);
      if (messageField != null) return messageField;

      Map<String, Field> typeExtensions = messageType.extensionFieldsMap();
      Field extensionField = resolve(field, typeExtensions);
      if (extensionField != null) return extensionField;
    }

    return null; // Unable to traverse this field path.
  }

  /**
   * Validate that the tags of {@code fields} are unique and in range, that proto3 message cannot
   * reference proto2 enums.
   */
  void validateFields(Iterable<Field> fields, List<Reserved> reserveds,
      SyntaxRules syntaxRules) {
    Map<Integer, Set<Field>> tagToField = new LinkedHashMap<>();
    Map<String, Set<Field>> nameToField = new LinkedHashMap<>();
    Map<String, Set<Field>> jsonNameToField = new LinkedHashMap<>();

    for (Field field : fields) {
      int tag = field.getTag();
      if (!SchemaUtil.isValidTag(tag)) {
        errors.at(field).add("tag is out of range: " + tag);
      }

      for (Reserved reserved : reserveds) {
        if (reserved.matchesTag(tag)) {
          errors.at(field).add("tag " + tag + " is reserved (" + reserved.getLocation() + ")");
        }
        if (reserved.matchesName(field.getName())) {
          errors.at(field).add(
              "name '" + field.getName() + "' is reserved (" + reserved.getLocation() + ")");
        }
      }

      tagToField.computeIfAbsent(tag, k -> new LinkedHashSet<>()).add(field);
      nameToField.computeIfAbsent(field.getQualifiedName(), k -> new LinkedHashSet<>()).add(field);
      // We allow JSON collisions for extensions.
      if (!field.isExtension()) {
        jsonNameToField
            .computeIfAbsent(syntaxRules.jsonName(field.getName(), field.getDeclaredJsonName()),
                k -> new LinkedHashSet<>())
            .add(field);
      }

      syntaxRules.validateTypeReference(get(field.getType()), errors.at(field));
    }

    for (Map.Entry<Integer, Set<Field>> entry : tagToField.entrySet()) {
      Set<Field> values = entry.getValue();
      if (values.size() > 1) {
        StringBuilder error = new StringBuilder();
        error.append("multiple fields share tag ").append(entry.getKey()).append(":");
        int index = 1;
        for (Field field : values) {
          error.append("\n  ").append(index++).append(". ").append(field.getName())
              .append(" (").append(field.getLocation()).append(")");
        }
        errors.add(error.toString());
      }
    }

    boolean hasCollidingFields = false;
    for (Set<Field> collidingFields : nameToField.values()) {
      if (collidingFields.size() > 1) {
        hasCollidingFields = true;
        Field first = collidingFields.iterator().next();
        StringBuilder error = new StringBuilder();
        error.append("multiple fields share name ").append(first.getName()).append(":");
        int index = 1;
        for (Field field : collidingFields) {
          error.append("\n  ").append(index++).append(". ").append(field.getName())
              .append(" (").append(field.getLocation()).append(")");
        }
        errors.add(error.toString());
      }
    }

    if (!hasCollidingFields) {
      for (Map.Entry<String, Set<Field>> entry : jsonNameToField.entrySet()) {
        Set<Field> collidingJsonFields = entry.getValue();
        if (collidingJsonFields.size() > 1) {
          StringBuilder error = new StringBuilder();
          error.append("multiple fields share same JSON name '").append(entry.getKey())
              .append("':");
          int index = 1;
          for (Field field : collidingJsonFields) {
            error.append("\n  ").append(index++).append(". ").append(field.getName())
                .append(" (").append(field.getLocation()).append(")");
          }
          errors.add(error.toString());
        }
      }
    }
  }

  private void validatePackages() {
    Map<String, List<FileLinker>> filesByPackageName = new LinkedHashMap<>();
    for (FileLinker fileLinker : fileLinkers.values()) {
      filesByPackageName
          .computeIfAbsent(fileLinker.protoFile.packageName, k -> new ArrayList<>())
          .add(fileLinker);
    }

    for (List<FileLinker> packageFileLinkers : filesByPackageName.values()) {
      validateTypeUniqueness(packageFileLinkers);

      // Enum constants must be unique within each package.
      List<Type> types = new ArrayList<>();
      for (FileLinker fileLinker : packageFileLinkers) {
        types.addAll(fileLinker.protoFile.types);
      }
      withContext(packageFileLinkers.get(0).protoFile).validateEnumConstantNameUniqueness(types);
    }
  }

  private void validateTypeUniqueness(List<FileLinker> fileLinkers) {
    // Group types across files in this package by (type, location) identity.
    Map<String, List<Type>> conflicting = new LinkedHashMap<>();
    for (FileLinker fileLinker : fileLinkers) {
      for (Type type : fileLinker.protoFile.types) {
        String key = type.getType() + "->" + type.getLocation();
        conflicting.computeIfAbsent(key, k -> new ArrayList<>()).add(type);
      }
    }

    for (List<Type> typesAndLocations : conflicting.values()) {
      if (typesAndLocations.size() <= 1) continue;
      ProtoType type = typesAndLocations.get(0).getType();
      StringBuilder error = new StringBuilder();
      error.append("same type '").append(type)
          .append("' from the same file loaded from different paths:");
      int index = 1;
      for (Type entry : typesAndLocations) {
        error.append("\n  ").append(index++).append(". base:").append(entry.getLocation().getBase())
            .append(", path:").append(entry.getLocation().withoutBase());
      }
      errors.add(error.toString());
    }
  }

  void validateEnumConstantNameUniqueness(Iterable<Type> nestedTypes) {
    Map<String, Set<EnumType>> nameToType = new LinkedHashMap<>();
    for (Type type : nestedTypes) {
      if (type instanceof EnumType) {
        for (EnumConstant enumConstant : ((EnumType) type).getConstants()) {
          nameToType.computeIfAbsent(enumConstant.getName(), k -> new LinkedHashSet<>()).add(
              (EnumType) type);
        }
      }
    }

    for (Map.Entry<String, Set<EnumType>> entry : nameToType.entrySet()) {
      Set<EnumType> values = entry.getValue();
      if (values.size() > 1) {
        String constant = entry.getKey();
        StringBuilder error = new StringBuilder();
        error.append("multiple enums share constant ").append(constant).append(":");
        int index = 1;
        for (EnumType enumType : values) {
          error.append("\n  ").append(index++).append(". ").append(enumType.getType())
              .append(".").append(constant)
              .append(" (").append(enumType.constant(constant).getLocation()).append(")");
        }
        errors.add(error.toString());
      }
    }
  }

  public void validateImportForType(Location location, ProtoType type) {
    // Map key type is always scalar. No need to validate it.
    if (type.isMap()) type = type.getValueType();

    if (type.isScalar()) return;

    String path = location.getPath();
    String requiredImport = get(type).getLocation().getPath();
    FileLinker fileLinker = getFileLinker(path);
    if (!path.equals(requiredImport) && !fileLinker.effectiveImports().contains(requiredImport)) {
      errors.add(path + " needs to import " + requiredImport);
    }
  }

  public void validateImportForPath(Location location, String requiredImport) {
    String path = location.getPath();
    FileLinker fileLinker = getFileLinker(path);
    if (!path.equals(requiredImport) && !fileLinker.effectiveImports().contains(requiredImport)) {
      errors.add(path + " needs to import " + requiredImport);
    }
  }

  /** Returns a new linker that uses {@code context} to resolve type names and report errors. */
  public Linker withContext(Object context) {
    return new Linker(this, context);
  }

  public boolean getLoadExhaustively() {
    return loadExhaustively;
  }
}
