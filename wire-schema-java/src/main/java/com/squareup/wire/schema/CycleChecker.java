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

import com.squareup.wire.schema.internal.DagChecker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Check for forbidden cycles in proto schemas.
 *
 * <p>Wire doesn't care, but protoc and some of the languages it targets care, and it's good if
 * the schemas created with Wire also work on those tools.
 *
 * <p>This class doesn't do any graph traversal; that's delegated to {@link DagChecker}. Instead
 * this class is concerned with turning our schemas into graphs that {@link DagChecker} can
 * understand, and formatting errors if it finds problems.
 */
class CycleChecker {
  private final Map<String, FileLinker> fileLinkers;
  private final ErrorCollector errors;

  private final ProtoMember goPackageOption =
      ProtoMember.get("google.protobuf.FileOptions#go_package");

  CycleChecker(Map<String, FileLinker> fileLinkers, ErrorCollector errors) {
    this.fileLinkers = fileLinkers;
    this.errors = errors;
  }

  private List<String> importsAndPublicImports(FileLinker fileLinker) {
    List<String> result = new ArrayList<>(fileLinker.protoFile.imports);
    result.addAll(fileLinker.protoFile.publicImports);
    return result;
  }

  private String cycleCheckPackageName(FileLinker fileLinker) {
    Object goPackage = fileLinker.protoFile.options.get(goPackageOption);
    if (goPackage != null) return goPackage.toString();
    return fileLinker.protoFile.packageName != null
        ? fileLinker.protoFile.packageName
        : "<default>";
  }

  void checkForImportCycles() {
    DagChecker<String> dagChecker = new DagChecker<>(fileLinkers.keySet(), path -> {
      FileLinker fileLinker = fileLinkers.get(path);
      if (fileLinker == null) return new ArrayList<String>();
      return importsAndPublicImports(fileLinker);
    });

    Set<List<String>> cycles = dagChecker.check();

    for (List<String> cycle : cycles) {
      errors.add(importCycleMessageError(cycle));
    }
  }

  /** Returns an error message that describes cyclic imports in {@code files}. */
  private String importCycleMessageError(List<String> files) {
    StringBuilder error = new StringBuilder();
    error.append("imports form a cycle:");

    for (String file : files) {
      FileLinker fileLinker = fileLinkers.get(file);
      if (fileLinker == null) continue;

      error.append("\n  ").append(file).append(":");
      for (String importPath : fileLinker.protoFile.imports) {
        if (files.contains(importPath)) {
          error.append("\n    import \"").append(importPath).append("\";");
        }
      }
      for (String importPath : fileLinker.protoFile.publicImports) {
        if (files.contains(importPath)) {
          error.append("\n    import public \"").append(importPath).append("\";");
        }
      }
    }
    return error.toString();
  }

  /**
   * In good programming languages like Java and Kotlin it's completely fine to have dependency
   * cycles between packages. But Go forbids cycles between packages and that effectively means
   * that proto must as well when it targets Go.
   *
   * <p>This checks for cycles between packages that would offend the Go compiler if these
   * .proto sources were ever generated with it.
   */
  void checkForPackageCycles() {
    Map<String, Set<String>> packageDag = new HashMap<>();
    for (FileLinker fileLinker : fileLinkers.values()) {
      Set<String> targets = packageDag.computeIfAbsent(cycleCheckPackageName(fileLinker),
          k -> new HashSet<>());
      for (String path : importsAndPublicImports(fileLinker)) {
        FileLinker imported = fileLinkers.get(path);
        if (imported == null) continue;
        targets.add(cycleCheckPackageName(imported));
      }
      targets.remove(cycleCheckPackageName(fileLinker));
    }

    DagChecker<String> dagChecker = new DagChecker<>(packageDag.keySet(),
        packageName -> packageDag.get(packageName));

    Set<List<String>> cycles = dagChecker.check();

    for (List<String> cycle : cycles) {
      errors.add(packagesCycleMessageError(cycle));
    }
  }

  /**
   * Returns an error message that describes cyclic imports in {@code packages}. The error
   * attempts to show which imports are responsible for the package cycles and looks like this:
   *
   * <pre>{@code
   * packages form a cycle:
   *   locations imports people
   *     locations/office.proto:
   *       import "people/office_manager.proto";
   *   people imports locations
   *     people/employee.proto:
   *       import "locations/office.proto";
   *       import "locations/residence.proto";
   * }</pre>
   */
  private String packagesCycleMessageError(List<String> packages) {
    StringBuilder error = new StringBuilder();
    error.append("packages form a cycle:");

    List<Map.Entry<String, FileLinker>> sortedFileLinkers = new ArrayList<>(
        fileLinkers.entrySet());
    sortedFileLinkers.sort((a, b) -> cycleCheckPackageName(a.getValue())
        .compareTo(cycleCheckPackageName(b.getValue())));

    String lastSourcePackage = null;
    String lastTargetPackage = null;
    String lastSourcePath = null;

    for (Map.Entry<String, FileLinker> entry : sortedFileLinkers) {
      String sourcePath = entry.getKey();
      FileLinker sourceFileLinker = entry.getValue();
      String sourcePackage = cycleCheckPackageName(sourceFileLinker);
      if (!packages.contains(sourcePackage)) continue;

      for (String targetPath : importsAndPublicImports(sourceFileLinker)) {
        FileLinker targetFileLinker = fileLinkers.get(targetPath);
        if (targetFileLinker == null) continue;
        String targetPackage = cycleCheckPackageName(targetFileLinker);
        if (targetPackage.equals(sourcePackage) || !packages.contains(targetPackage)) continue;

        if (!sourcePackage.equals(lastSourcePackage) || !targetPackage.equals(lastTargetPackage)) {
          error.append("\n  ").append(sourcePackage).append(" imports ").append(targetPackage);
          lastSourcePackage = sourcePackage;
          lastTargetPackage = targetPackage;
          lastSourcePath = null;
        }

        if (!sourcePath.equals(lastSourcePath)) {
          error.append("\n    ").append(sourcePath).append(":");
          lastSourcePath = sourcePath;
        }

        error.append("\n      import \"").append(targetPath).append("\";");
      }
    }
    return error.toString();
  }
}
