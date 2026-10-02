/*
 * Copyright (C) 2022 Square, Inc.
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

import com.squareup.wire.WireLogger;
import com.squareup.wire.internal.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import okio.FileSystem;
import okio.Path;

/** A {@link SchemaHandler} {@link #handle handles} {@link Schema}! */
public abstract class SchemaHandler {
  /**
   * This will handle all {@link ProtoFile}s which are part of the {@code sourcePath}. If a {@link
   * Module} is set in the {@link Context}, it will handle only {@link Type}s and {@link Service}s
   * the module defines respecting the {@link Context} rules. Override this method if you have
   * specific needs the default implementation doesn't address.
   */
  public void handle(Schema schema, Context context) {
    Set<ProtoType> moduleTypes = context.module() != null ? context.module().types() : null;
    for (ProtoFile protoFile : schema.protoFiles()) {
      if (!context.inSourcePath(protoFile)) continue;

      // Remove types from the file which are not owned by this partition.
      List<Type> filteredTypes = new ArrayList<>();
      for (Type type : protoFile.types()) {
        if (moduleTypes == null || moduleTypes.contains(type.type())) filteredTypes.add(type);
      }
      List<Service> filteredServices = new ArrayList<>();
      for (Service service : protoFile.services()) {
        if (moduleTypes == null || moduleTypes.contains(service.type())) {
          filteredServices.add(service);
        }
      }
      ProtoFile filteredProtoFile = protoFile.copy(
          protoFile.location(), protoFile.imports(), protoFile.publicImports(),
          protoFile.weakImports(), protoFile.packageName(), filteredTypes, filteredServices,
          protoFile.extendList(), protoFile.options(), protoFile.syntax());

      handle(filteredProtoFile, context);
    }
  }

  /**
   * Returns the {@link Path} of the file which {@code type} will have been generated into. Null
   * if nothing has been generated.
   */
  public abstract Path handle(Type type, Context context);

  /**
   * Returns the {@link Path}s of the files which {@code service} will have been generated into.
   * Empty if nothing has been generated.
   */
  public abstract List<Path> handle(Service service, Context context);

  /**
   * Returns the {@link Path} of the files which {@code field} will have been generated into.
   * Null if nothing has been generated.
   */
  public abstract Path handle(Extend extend, Field field, Context context);

  /**
   * A {@link Context} holds the information necessary for a {@link SchemaHandler} to do its job.
   * It contains both helping objects such as {@link #logger}, and constraining objects such as
   * {@link #emittingRules}.
   */
  public static final class Context {
    /** To be used by the {@link SchemaHandler} for reading/writing operations on disk. */
    private final FileSystem fileSystem;
    /** Location on {@link #fileSystem} where the {@link SchemaHandler} is to write files, if it
     * needs to. */
    private final Path outDirectory;
    /** Event-listener like logger with which {@link SchemaHandler} can notify handled
     * artifacts. */
    private final WireLogger logger;
    /**
     * Object to be used by the {@link SchemaHandler} to store errors. After all {@link
     * SchemaHandler}s are finished, Wire will throw an exception if any error are present inside
     * the collector.
     */
    private final ErrorCollector errorCollector;
    /**
     * Set of rules letting the {@link SchemaHandler} know what {@link ProtoType} to include or
     * exclude in its logic. This object represents the {@code includes} and {@code excludes}
     * values which were associated with its {@link Target}.
     */
    private final EmittingRules emittingRules;
    /**
     * If set, the {@link SchemaHandler} is to handle only types which are not claimed yet, and
     * claim itself types it has handled. If null, the {@link SchemaHandler} is to handle all
     * types.
     */
    private final ClaimedDefinitions claimedDefinitions;
    /** If the {@link SchemaHandler} writes files, it is to claim {@link Path}s of files it
     * created. */
    private final ClaimedPaths claimedPaths;
    /**
     * A {@link Module} dictates how the loaded types are partitioned and how they are to be
     * handled. If null, there are no partition and all types are to be handled.
     */
    private final Module module;
    /**
     * Contains {@code Location.path} values of all {@code sourcePath} roots. The {@link
     * SchemaHandler} is to ignore {@link ProtoFile}s not part of this set; this verification can
     * be executed via the {@link #inSourcePath} method.
     */
    private final Set<String> sourcePathPaths;
    /**
     * To be used by the {@link SchemaHandler} if it supports {@link Profile} files. Please note
     * that this API is unstable and can change at anytime.
     */
    private final ProfileLoader profileLoader;

    /** The full refactored {@link Schema} prior partitioning. See WireRun.modules(). */
    private final Schema fullSchema;

    public Context(
        FileSystem fileSystem,
        Path outDirectory,
        WireLogger logger,
        ErrorCollector errorCollector,
        EmittingRules emittingRules,
        ClaimedDefinitions claimedDefinitions,
        ClaimedPaths claimedPaths,
        Set<String> sourcePathPaths,
        Module module,
        ProfileLoader profileLoader,
        Schema fullSchema) {
      this.fileSystem = fileSystem;
      this.outDirectory = outDirectory;
      this.logger = logger;
      this.errorCollector = errorCollector;
      this.emittingRules = emittingRules;
      this.claimedDefinitions = claimedDefinitions;
      this.claimedPaths = claimedPaths;
      this.sourcePathPaths = sourcePathPaths;
      this.module = module;
      this.profileLoader = profileLoader;
      this.fullSchema = fullSchema;
    }

    public Context(
        FileSystem fileSystem,
        Path outDirectory,
        WireLogger logger,
        Schema fullSchema) {
      this(fileSystem, outDirectory, logger, new ErrorCollector(),
          new EmittingRules.Builder().build(), null, new ClaimedPaths(), null, null, null,
          fullSchema);
    }

    /** To be used by the {@link SchemaHandler} for reading/writing operations on disk. */
    public FileSystem fileSystem() {
      return fileSystem;
    }

    /** Location on {@link #fileSystem()} where the {@link SchemaHandler} is to write files, if
     * it needs to. */
    public Path outDirectory() {
      return outDirectory;
    }

    /** Event-listener like logger with which {@link SchemaHandler} can notify handled
     * artifacts. */
    public WireLogger logger() {
      return logger;
    }

    /** Errors collected so far; see {@link ErrorCollector}. */
    public ErrorCollector errorCollector() {
      return errorCollector;
    }

    /** Include/exclude rules; see {@link EmittingRules}. */
    public EmittingRules emittingRules() {
      return emittingRules;
    }

    /** Claims on already-handled definitions, or null when the handler handles everything. */
    public ClaimedDefinitions claimedDefinitions() {
      return claimedDefinitions;
    }

    /** Claims on paths of generated files. */
    public ClaimedPaths claimedPaths() {
      return claimedPaths;
    }

    /** The partition this handler is generating for, or null when there are no partitions. */
    public Module module() {
      return module;
    }

    /** Paths of the {@code sourcePath} roots, or null when everything is in scope. */
    public Set<String> sourcePathPaths() {
      return sourcePathPaths;
    }

    /** Loader for {@link Profile} files, or null when unsupported. */
    public ProfileLoader profileLoader() {
      return profileLoader;
    }

    /** The full refactored {@link Schema} prior partitioning. */
    public Schema fullSchema() {
      return fullSchema;
    }

    /** True if this {@code protoFile} ia part of a {@code sourcePath} root. */
    public boolean inSourcePath(ProtoFile protoFile) {
      return inSourcePath(protoFile.location());
    }

    /** True if this {@code location} ia part of a {@code sourcePath} root. */
    public boolean inSourcePath(Location location) {
      return sourcePathPaths == null || sourcePathPaths.contains(location.path);
    }
  }

  /**
   * A {@link Module} dictates how the loaded types are to be partitioned and handled.
   */
  public static final class Module {
    /** The name of the {@link Module}. */
    private final String name;
    /** The types that this module is to handle. */
    private final Set<ProtoType> types;
    /** These are the types depended upon by {@link #types} associated with their module name. */
    private final Map<ProtoType, String> upstreamTypes;

    public Module(String name, Set<ProtoType> types, Map<ProtoType, String> upstreamTypes) {
      this.name = name;
      this.types = types;
      this.upstreamTypes = upstreamTypes;
    }

    public Module(String name, Set<ProtoType> types) {
      this(name, types, Collections.emptyMap());
    }

    /** The name of the {@link Module}. */
    public String name() {
      return name;
    }

    /** The types that this module is to handle. */
    public Set<ProtoType> types() {
      return types;
    }

    /** These are the types depended upon by {@link #types} associated with their module name. */
    public Map<ProtoType, String> upstreamTypes() {
      return upstreamTypes;
    }
  }

  /**
   * This will handle all {@link Type}s and {@link Service}s of the {@code protoFile} in respect
   * to the emitting rules defined by the {@code context}. If exclusive, the handled {@link Type}s
   * and {@link Service}s should be added to the {@link ClaimedDefinitions}. Already consumed
   * types and services themselves will be omitted by this handler.
   */
  protected void handle(
      ProtoFile protoFile,
      Context context) {
    ClaimedDefinitions claimedDefinitions = context.claimedDefinitions();
    EmittingRules emittingRules = context.emittingRules();
    ClaimedPaths claimedPaths = context.claimedPaths();
    List<Type> types = new ArrayList<>();
    for (Type type : protoFile.types()) {
      if (claimedDefinitions != null && claimedDefinitions.contains(type)) continue;
      if (!emittingRules.includes(type.type())) continue;
      types.add(type);
    }

    for (Type type : types) {
      Path generatedFilePath = handle(type, context);

      if (generatedFilePath != null) {
        claimedPaths.claim(generatedFilePath, type);
        // We don't let other targets handle this one.
        if (claimedDefinitions != null) claimedDefinitions.claim(type);
      }
    }

    List<Service> services = new ArrayList<>();
    for (Service service : protoFile.services()) {
      if (claimedDefinitions != null && claimedDefinitions.contains(service)) continue;
      if (!emittingRules.includes(service.type())) continue;
      services.add(service);
    }

    for (Service service : services) {
      List<Path> generatedFilePaths = handle(service, context);

      if (!generatedFilePaths.isEmpty()) {
        for (Path generatedFilePath : generatedFilePaths) {
          claimedPaths.claim(generatedFilePath, service);
        }
        // We don't let other targets handle this one.
        if (claimedDefinitions != null) claimedDefinitions.claim(service);
      }
    }

    for (Extend extend : protoFile.extendList()) {
      for (Field field : extend.fields()) {
        if (claimedDefinitions != null
            && claimedDefinitions.contains(extend.member(field))) {
          continue;
        }
        // We append `.*` to the field's package name so that it matches rules defined as
        // `package.*`.
        if (!emittingRules.includes(ProtoType.get(field.packageName() + ".*"))) {
          continue;
        }

        Path generatedFilePath = handle(extend, field, context);

        if (generatedFilePath != null) {
          claimedPaths.claim(generatedFilePath, extend);
          // We don't let other targets handle this one.
          if (claimedDefinitions != null) claimedDefinitions.claim(extend.member(field));
        }
      }
    }
  }

  /**
   * Confirms that {@code filePath} resolves to a location inside {@code outDirectory}, throwing
   * otherwise.
   *
   * <p>Some segments of a generated file's path are derived from proto-controlled input, such as
   * the {@code java_package}, {@code kotlin_package}, and {@code wire_package} options. Without
   * this check, a value that is an absolute path or that traverses upward with {@code ..} would
   * redirect the generated file outside the configured {@code outDirectory} and could overwrite
   * arbitrary files on the build host. This is possible because {@code okio.Path.div} replaces
   * the left-hand side entirely when the right-hand side is absolute, and normalizes {@code ..}
   * upward otherwise.
   */
  protected void checkPathInOutDirectory(Path filePath, Path outDirectory) {
    Path normalizedOut = outDirectory.normalized();
    Path normalizedPath = filePath.normalized();
    List<String> outSegments = normalizedOut.segments();
    List<String> pathSegments = normalizedPath.segments();
    boolean escapes = !Objects.equals(normalizedPath.root(), normalizedOut.root())
        || !Objects.equals(normalizedPath.volumeLetter(), normalizedOut.volumeLetter())
        || pathSegments.size() <= outSegments.size()
        || !pathSegments.subList(0, outSegments.size()).equals(outSegments);
    if (escapes) {
      throw new IllegalArgumentException(
          "Refusing to write a generated file outside the output directory. A package option "
              + "(such as java_package, kotlin_package, or wire_package) resolved to an absolute "
              + "path or one that traverses outside the output directory.\n"
              + "  output directory: " + normalizedOut + "\n"
              + "  resolved path:    " + normalizedPath);
    }
  }

  /** Implementations of this interface must have a no-arguments public constructor. */
  public interface Factory extends Serializable {
    /**
     * @param includes set of rules letting the handler know what {@link ProtoType} to include in
     *     its logic.
     * @param excludes set of rules letting the handler know what {@link ProtoType} to exclude in
     *     its logic.
     * @param exclusive if true, the schema handler is to claim types and services it handled.
     * @param outDirectory location on the fileSystem where the schema handler is to write files,
     *     if it needs to.
     * @param options arbitrary list of options to be used for the caller to pass extract data to
     *     the Schema Handler if needed.
     */
    SchemaHandler create(
        List<String> includes,
        List<String> excludes,
        boolean exclusive,
        String outDirectory,
        Map<String, String> options
    );
  }
}
