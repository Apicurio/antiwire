/*
 * Copyright (C) 2018 Square, Inc.
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

import com.squareup.wire.schema.CoreLoader;
import com.squareup.wire.schema.ErrorCollector;
import com.squareup.wire.schema.Linker;
import com.squareup.wire.schema.Loader;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.Profile;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.ProtoType;
import com.squareup.wire.schema.Root;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.Type;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import okio.FileSystem;

/**
 * Load proto files and their transitive dependencies and parse them. Keep track of which files
 * were loaded from where so that we can use that information later when deciding what to
 * generate.
 */
public final class CommonSchemaLoader implements Loader {
  private final FileSystem fileSystem;

  /** Errors accumulated by this load. */
  private final ErrorCollector errors;

  /** Source path roots that need to be closed. */
  private List<Root> sourcePathRoots;

  /** Proto path roots that need to be closed. */
  private List<Root> protoPathRoots;

  /** Strict by default. Note that golang cannot build protos with package cycles. */
  private boolean permitPackageCycles;

  private List<ProtoType> opaqueTypes;

  /**
   * If true, the schema loader will load the whole graph, including files and types not used by
   * anything in the source path.
   */
  private boolean loadExhaustively;

  /** Subset of the schema that was loaded from the source path. */
  private List<ProtoFile> sourcePathFiles;

  /** Keys are a Location base; values are the roots that those locations loaded from. */
  private final Map<String, List<Root>> baseToRoots;

  public CommonSchemaLoader(FileSystem fileSystem) {
    this.fileSystem = fileSystem;
    this.errors = new ErrorCollector();
    this.sourcePathRoots = null;
    this.protoPathRoots = null;
    this.sourcePathFiles = new ArrayList<>();
    this.baseToRoots = new LinkedHashMap<>();
    this.opaqueTypes = new ArrayList<>();
  }

  public CommonSchemaLoader(CommonSchemaLoader enclosing, ErrorCollector errors) {
    this.fileSystem = enclosing.fileSystem;
    this.errors = errors;
    this.sourcePathRoots = enclosing.sourcePathRoots;
    this.protoPathRoots = enclosing.protoPathRoots;
    this.sourcePathFiles = enclosing.sourcePathFiles;
    this.baseToRoots = enclosing.baseToRoots;
    this.permitPackageCycles = enclosing.permitPackageCycles;
    this.opaqueTypes = enclosing.opaqueTypes;
    this.loadExhaustively = enclosing.loadExhaustively;
  }

  @Override public Loader withErrors(ErrorCollector errors) {
    return new CommonSchemaLoader(this, errors);
  }

  /** Initialize the source path and proto path from which files are loaded. */
  public void initRoots(List<Location> sourcePath, List<Location> protoPath) throws IOException {
    if (sourcePathRoots != null || protoPathRoots != null) {
      throw new IllegalStateException("Check failed");
    }
    sourcePathRoots = allRoots(sourcePath);
    protoPathRoots = allRoots(protoPath);
  }

  public boolean getPermitPackageCycles() {
    return permitPackageCycles;
  }

  public void setPermitPackageCycles(boolean permitPackageCycles) {
    this.permitPackageCycles = permitPackageCycles;
  }

  public List<ProtoType> getOpaqueTypes() {
    return opaqueTypes;
  }

  public void setOpaqueTypes(List<ProtoType> opaqueTypes) {
    this.opaqueTypes = opaqueTypes;
  }

  public boolean getLoadExhaustively() {
    return loadExhaustively;
  }

  public void setLoadExhaustively(boolean loadExhaustively) {
    this.loadExhaustively = loadExhaustively;
  }

  /** Subset of the schema that was loaded from the source path. */
  public List<ProtoFile> getSourcePathFiles() {
    return sourcePathFiles;
  }

  public Schema loadSchema() throws IOException {
    sourcePathFiles = loadSourcePathFiles();
    Linker linker = new Linker(this, errors, permitPackageCycles, loadExhaustively, opaqueTypes);
    Schema result = linker.link(sourcePathFiles);
    errors.throwIfNonEmpty();
    return result;
  }

  /** Returns the files in the source path. */
  public List<ProtoFile> loadSourcePathFiles() throws IOException {
    if (sourcePathRoots == null || protoPathRoots == null) {
      throw new IllegalStateException("call initRoots() before calling loadSourcePathFiles()");
    }

    List<ProtoFile> result = new ArrayList<>();
    for (Root sourceRoot : sourcePathRoots) {
      for (Root.ProtoFilePath locationAndPath : sourceRoot.allProtoFiles()) {
        result.add(load(locationAndPath));
      }
    }

    if (result.isEmpty()) {
      errors.add("no sources");
    }

    errors.throwIfNonEmpty();

    return result;
  }

  @Override public ProtoFile load(String path) {
    // Traverse roots in search of the one that has this path.
    Root.ProtoFilePath loadFrom = null;
    try {
      for (Root protoPathRoot : protoPathRoots) {
        Root.ProtoFilePath locationAndPath = protoPathRoot.resolve(path);
        if (locationAndPath == null) continue;
        if (loadFrom != null) {
          errors.add(path + " is ambiguous:\n  " + locationAndPath + "\n  " + loadFrom);
          continue;
        }
        loadFrom = locationAndPath;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (loadFrom != null) {
      try {
        return load(loadFrom);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    if (CoreLoader.isWireRuntimeProto(path)) {
      return CoreLoader.INSTANCE.load(path);
    }

    List<String> searchPaths = new ArrayList<>();
    for (Root protoPathRoot : protoPathRoots) {
      searchPaths.add(String.valueOf(protoPathRoot));
    }
    errors.add("unable to find " + path
        + "\n  searching " + protoPathRoots.size() + " proto paths:"
        + "\n    " + String.join("\n    ", searchPaths));
    return ProtoFile.get(ProtoFileElement.empty(path));
  }

  private ProtoFile load(Root.ProtoFilePath protoFilePath) throws IOException {
    if (CoreLoader.isWireRuntimeProto(protoFilePath.getLocation())) {
      return CoreLoader.INSTANCE.load(protoFilePath.getLocation().getPath());
    }

    ProtoFile protoFile = protoFilePath.parse();
    String importPath = importPath(protoFile, protoFilePath.getLocation());

    // If the .proto was specified as a full path without a separate base directory that it's
    // relative to, confirm that the import path and file system path agree.
    if (protoFilePath.getLocation().getBase().isEmpty()
        && !protoFilePath.getLocation().getPath().equals(importPath)
        && !protoFilePath.getLocation().getPath().endsWith("/" + importPath)) {
      errors.add("expected " + protoFilePath.getLocation().getPath()
          + " to have a path ending with " + importPath);
    }

    return protoFile;
  }

  /** Convert {@code locations} into roots that can be searched. */
  private List<Root> allRoots(List<Location> locations) {
    List<Root> result = new ArrayList<>();
    for (Location location : locations) {
      try {
        result.addAll(Root.roots(location, fileSystem, baseToRoots));
      } catch (IllegalArgumentException e) {
        errors.add(e.getMessage());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return result;
  }

  /** Releases the roots opened by {@link #initRoots}, including ZIP file systems. */
  public void close() throws IOException {
    if (sourcePathRoots != null) {
      for (Root root : sourcePathRoots) {
        root.close();
      }
    }
    if (protoPathRoots != null) {
      for (Root root : protoPathRoots) {
        root.close();
      }
    }
  }

  /**
   * Reports the errors accumulated by direct {@link #load} calls, without the file context the
   * linker attaches. Upstream's jvmTest drives this member module-internally.
   */
  public void reportLoadingErrors() {
    errors.throwIfNonEmpty();
  }

  public Profile loadProfile(String name, Schema schema) throws IOException {
    List<Location> allLocations = new ArrayList<>();
    for (ProtoFile protoFile : schema.getProtoFiles()) {
      allLocations.add(protoFile.getLocation());
    }
    Set<Location> locationsToCheck = locationsToCheck(name, allLocations);

    List<ProfileFileElement> profileElements = new ArrayList<>();
    for (Location location : locationsToCheck) {
      List<Root> roots = baseToRoots.get(location.getBase());
      if (roots == null) continue;
      for (Root root : roots) {
        Root.ProtoFilePath resolved = root.resolve(location.getPath());
        if (resolved == null) continue;
        profileElements.add(resolved.parseProfile());
      }
    }

    Profile profile = new Profile(profileElements);
    validate(schema, profileElements);
    return profile;
  }

  /** Confirms that {@code profileFiles} link correctly against {@code schema}. */
  private void validate(Schema schema, List<ProfileFileElement> profileFiles) {
    for (ProfileFileElement profileFile : profileFiles) {
      for (TypeConfigElement typeConfig : profileFile.getTypeConfigs()) {
        ProtoType imported = importedType(ProtoType.get(typeConfig.getType()));
        if (imported == null) continue;

        Type resolvedType = schema.getType(imported);
        if (resolvedType == null) {
          // This type is either absent from .proto files, or merely not loaded because our schema
          // is incomplete. Unfortunately we can't tell the difference! Assume that this type is
          // just absent from the schema-as-loaded and therefore irrelevant to the current project.
          // Ignore it!
          //
          // (A fancier implementation would load the schema and profile in one step and they would
          // be mutually complete. We aren't bothering with this correctness at this phase.)
          continue;
        }

        String requiredImport = resolvedType.getLocation().getPath();
        if (!profileFile.getImports().contains(requiredImport)) {
          errors.add(typeConfig.getLocation().getPath() + " needs to import " + requiredImport
              + " (" + typeConfig.getLocation() + ")");
        }
      }
    }

    errors.throwIfNonEmpty();
  }

  /** Returns the type to import for {@code type}. */
  private ProtoType importedType(ProtoType type) {
    // Map key type is always scalar.
    if (type.isMap()) type = type.getValueType();
    return type.isScalar() ? null : type;
  }

  /**
   * Returns a list of locations to check for profile files. This is the profile file name (like
   * "java.wire") in the same directory, and in all parent directories up to the base.
   */
  public Set<Location> locationsToCheck(String name, List<Location> input) {
    Deque<Location> queue = new ArrayDeque<>(input);

    Set<Location> result = new LinkedHashSet<>();
    while (true) {
      Location protoLocation = queue.pollFirst();
      if (protoLocation == null) break;
      int lastSlash = protoLocation.getPath().lastIndexOf("/");
      String parentPath = protoLocation.getPath().substring(0, lastSlash + 1);
      Location profileLocation = new Location(
          protoLocation.getBase(), parentPath + name + ".wire", protoLocation.getLine(),
          protoLocation.getColumn());

      if (!result.add(profileLocation)) continue; // Already added.
      if (parentPath.isEmpty()) continue; // No more parents to enqueue.
      queue.add(new Location(protoLocation.getBase(), parentPath.substring(0, parentPath.length() - 1),
          protoLocation.getLine(), protoLocation.getColumn())); // Drop trailing '/'.
    }
    return result;
  }

  static String importPath(ProtoFile protoFile, Location location) {
    return location.getBase().isEmpty()
        ? canonicalImportPath(protoFile, location)
        : location.getPath();
  }

  private static String canonicalImportPath(ProtoFile protoFile, Location location) {
    String filename = location.getPath().substring(location.getPath().lastIndexOf('/') + 1);
    String packageName = protoFile.getPackageName();
    return packageName == null
        ? filename
        : packageName.replace('.', '/') + "/" + filename;
  }
}
