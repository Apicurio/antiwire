/*
 * Copyright (C) 2019 Square, Inc.
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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

class FileLinker {
  final ProtoFile protoFile;
  private final Linker linker;

  /** Lazily computed set of files used to reference other types and options. */
  private Set<String> effectiveImports;

  /** True once this linker has registered its types with the enclosing linker. */
  private boolean typesRegistered;
  private boolean extensionsLinked;
  private boolean importedExtensionsRegistered;
  private boolean extensionOptionsLinked;
  private boolean importedExtensionOptionsLinked;
  private boolean fileOptionsLinked;

  /** The set of types defined in this file whose members have been linked. */
  private final Set<ProtoType> typesWithMembersLinked = new LinkedHashSet<>();

  FileLinker(ProtoFile protoFile, Linker linker) {
    this.protoFile = protoFile;
    this.linker = linker;
  }

  /**
   * Returns all effective imports. This is computed on-demand by unioning all direct imports
   * plus the recursive set of all public imports.
   */
  Set<String> effectiveImports() {
    if (effectiveImports == null) {
      Set<String> sink = new LinkedHashSet<>();
      addImportsRecursive(sink, protoFile.imports);
      addImportsRecursive(sink, protoFile.publicImports);
      effectiveImports = new LinkedHashSet<>(sink);
    }
    return effectiveImports;
  }

  private void addImportsRecursive(Set<String> sink, Collection<String> paths) {
    for (String path : paths) {
      if (sink.add(path)) {
        FileLinker fileLinker = linker.getFileLinker(path);
        addImportsRecursive(sink, fileLinker.protoFile.publicImports);

        if (linker.loadExhaustively) {
          addImportsRecursive(sink, fileLinker.protoFile.imports);
        }
      }
    }
  }

  void requireTypesRegistered() {
    if (typesRegistered) return;
    typesRegistered = true;

    for (Type type : protoFile.types) {
      addTypes(type);
    }
  }

  private void addTypes(Type type) {
    linker.addType(type.getType(), type);
    for (Type nestedType : type.getNestedTypes()) {
      addTypes(nestedType);
    }
  }

  void requireExtensionsLinked() {
    if (extensionsLinked) return;
    extensionsLinked = true;

    requireTypesRegistered();
    for (Extend extend : protoFile.extendList) {
      extend.link(linker);
    }
    for (Type type : protoFile.types) {
      linkNestedExtensions(type, linker);
    }
  }

  private void linkNestedExtensions(Type type, Linker linker) {
    Linker typeLinker = linker.withContext(type);
    for (Extend extend : type.getNestedExtendList()) {
      extend.link(typeLinker);
    }
    for (Type nested : type.getNestedTypes()) {
      linkNestedExtensions(nested, typeLinker);
    }
  }

  /**
   * This file might use extensions defined on one of the files we import. Make sure those
   * extensions are registered before we try to use our extensions.
   */
  void requireImportedExtensionsRegistered() {
    if (importedExtensionsRegistered) return;
    importedExtensionsRegistered = true;

    for (FileLinker importedFileLinker : linker.contextImportedTypes()) {
      importedFileLinker.requireExtensionsLinked();
    }
  }

  void requireExtensionOptionsLinked(boolean validate) {
    if (extensionOptionsLinked) return;
    extensionOptionsLinked = true;

    SyntaxRules syntaxRules = SyntaxRules.get(protoFile.syntax);
    for (Extend extend : protoFile.extendList) {
      extend.linkOptions(linker, syntaxRules, validate);
    }
    for (Type type : protoFile.types) {
      linkNestedExtensionOptions(type, linker, syntaxRules, validate);
    }
  }

  private void linkNestedExtensionOptions(Type type, Linker linker, SyntaxRules syntaxRules,
      boolean validate) {
    Linker typeLinker = linker.withContext(type);
    for (Extend extend : type.getNestedExtendList()) {
      extend.linkOptions(typeLinker, syntaxRules, validate);
    }
    for (Type nested : type.getNestedTypes()) {
      linkNestedExtensionOptions(nested, typeLinker, syntaxRules, validate);
    }
  }

  void requireImportedExtensionOptionsLinked(boolean validate) {
    if (importedExtensionOptionsLinked) return;
    importedExtensionOptionsLinked = true;

    for (FileLinker importedFileLinker : linker.contextImportedTypes()) {
      importedFileLinker.requireExtensionOptionsLinked(validate);
    }
  }

  void linkMembers() {
    linkMembersRecursive(protoFile.types);
    for (Service service : protoFile.services) {
      service.link(linker);
    }
  }

  /** Link the members of [types] and their nested types. */
  private void linkMembersRecursive(java.util.List<Type> types) {
    for (Type type : types) {
      requireMembersLinked(type);
      linkMembersRecursive(type.getNestedTypes());
    }
  }

  /** Link the members of [type] that haven't been linked already. */
  void requireMembersLinked(Type type) {
    if (typesWithMembersLinked.add(type.getType())) {
      type.linkMembers(linker);
    }
  }

  /**
   * This requires traversal of members of imported types! This may potentially include
   * non-direct dependencies!
   */
  void linkOptions(SyntaxRules syntaxRules, boolean validate) {
    requireFileOptionsLinked(validate);
    requireExtensionOptionsLinked(validate);
    for (Type type : protoFile.types) {
      type.linkOptions(linker, syntaxRules, validate);
    }
    for (Service service : protoFile.services) {
      service.linkOptions(linker, validate);
    }
  }

  void requireFileOptionsLinked(boolean validate) {
    if (fileOptionsLinked) return;
    fileOptionsLinked = true;

    protoFile.linkOptions(linker, validate);
  }

  void validate(SyntaxRules syntaxRules) {
    for (Type type : protoFile.types) {
      type.validate(linker, syntaxRules);
    }
    for (Service service : protoFile.services) {
      service.validate(linker);
    }
    for (Extend extend : protoFile.extendList) {
      extend.validate(linker, syntaxRules);
    }
  }
}
