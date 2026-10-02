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
package com.squareup.wire.schema;

import com.squareup.wire.WireLogger;
import com.squareup.wire.schema.internal.DagChecker;
import com.squareup.wire.schema.internal.TypeMover;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import okio.FileSystem;
import okio.Path;

/**
 * An invocation of the Wire compiler. Each invocation performs the following operations:
 *
 * <ol>
 *   <li>Read source {@code .proto} files directly from the file system or from archive files
 *       (ie. {@code .jar} and {@code .zip} files). This will also load imported {@code .proto}
 *       files from either the {@link #sourcePath} or {@link #protoPath}. The collection of loaded
 *       type declarations is called a schema.
 *   <li>Validate the schema and resolve references between types.
 *   <li>Optionally refactor the schema. This builds a new schema that is a subset of the
 *       original. The new schema contains only types that are both transitively reachable from
 *       {@link #treeShakingRoots} and not in {@link #treeShakingRubbish}. Types are moved to
 *       different files as specified by {@link #moves}.
 *   <li>Call each target. It will generate sources for protos in the {@link #sourcePath} that
 *       are in its {@link Target#includes}, that are not in its {@link Target#excludes}, and
 *       that haven't already been emitted by an earlier target.
 * </ol>
 *
 * <h3>Source Directories and Archives</h3>
 *
 * <p>The {@link #sourcePath} and {@link #protoPath} lists contain locations that are of the
 * following forms:
 *
 * <ul>
 *   <li>Locations of {@code .proto} files.
 *   <li>Locations of directories that contain a tree of {@code .proto} files. Typically this is
 *       a directory ending in {@code src/main/proto}.
 *   <li>Locations of {@code .zip} and {@code .jar} archives that contain a tree of {@code .proto}
 *       files. Typically this is a {@code .jar} file from a Maven repository.
 * </ul>
 *
 * <p>When one {@code .proto} message imports another, the import is resolved from the base of
 * each location and archive. If the build is in the unfortunate situation where an import could
 * be resolved by multiple files, whichever was listed first takes precedence.
 *
 * <p>Although the content and structure of {@link #sourcePath} and {@link #protoPath} are the
 * same, only types defined in {@link #sourcePath} are used to generate sources.
 *
 * <h3>Matching Packages, Types, and Members</h3>
 *
 * <p>The {@link #treeShakingRoots}, {@link #treeShakingRubbish}, {@link Target#includes} and
 * {@link Target#excludes} lists contain strings that select proto types and members. Strings in
 * these lists are in one of these forms:
 *
 * <ul>
 *   <li>Package names followed by {@code .*}, like {@code squareup.dinosaurs.*}. This matches
 *       types defined in the package and its descendant packages. A lone asterisk {@code *}
 *       matches all packages.
 *   <li>Fully-qualified type names like {@code squareup.dinosaurs.Dinosaur}. Types may be
 *       messages, enums, or services.
 *   <li>Fully-qualified member names like {@code squareup.dinosaurs.Dinosaur#name}. These are
 *       type names followed by {@code #} followed by a member name. Members may be message
 *       fields, enum constants, or service RPCs.
 * </ul>
 *
 * <p>It is an error to specify mutually-redundant values in any of these lists. For example, the
 * list {@code [squareup.dinosaurs, squareup.dinosaurs.Dinosaur]} is invalid because the second
 * element is already matched by the first.
 *
 * <p>Every element in each lists must apply to at least one declaration. Otherwise that option
 * is unnecessary and a possible typo.
 *
 * <h3>Composability</h3>
 *
 * <p>There are many moving parts in this system! For most applications it is safe to use {@link
 * #sourcePath} and {@link #targets} only. The other options are for the benefit of large and
 * modular applications.
 *
 * <p>Use {@link #protoPath} when one proto module depends on another proto module: these {@code
 * .proto} files are used for checking dependencies only. It is assumed that the sources for
 * these protos are generated elsewhere.
 *
 * <p>Use tree shaking to remove unwanted types: <a href="https://en.wikipedia.org/wiki/Tree_shaking">Tree
 * shaking</a> can be used to create a small-as-possible generated footprint even if the source
 * declarations are large. This works like <a href="https://en.wikipedia.org/wiki/ProGuard_(software)">ProGuard</a>
 * and other code shrinking compilers: it allows you to benefit from a shared codebase without
 * creating a large artifact.
 *
 * <p>Use multiple targets to split generated code across multiple programming languages: if your
 * project is already using generated Java, it’s difficult to switch to generated Kotlin. Instead
 * of switching everything over at once you can use multiple targets to switch over incrementally.
 * Targets consume their types; subsequent targets get whatever types are left over.
 */
public final class WireRun {
  /** Source {@code .proto} files for this task to generate from. */
  private final List<Location> sourcePath;

  /** Sources {@code .proto} files for this task to use when resolving references. */
  private final List<Location> protoPath;

  /**
   * The roots of the schema model. Wire will prune the schema model to only include types in
   * this list and the types transitively required by them.
   *
   * <p>If a member is included in this list then the enclosing type is included but its other
   * members are not. For example, if {@code squareup.dinosaurs.Dinosaur#name} is in this list
   * then the emitted source of the {@code Dinosaur} message will have the {@code name} field,
   * but not the {@code length_meters} or {@code mass_kilograms} fields.
   */
  private final List<String> treeShakingRoots;

  /**
   * Types and members that will be stripped from the schema model. Wire will remove the elements
   * themselves and also all references to them.
   */
  private final List<String> treeShakingRubbish;

  /**
   * Types to move before generating code or producing other output. Use this with ProtoTarget to
   * refactor proto schemas safely.
   */
  private final List<TypeMover.Move> moves;

  /**
   * The exclusive lower bound of the version range. Fields with {@code until} values greater
   * than this are retained.
   */
  private final String sinceVersion;

  /**
   * The inclusive upper bound of the version range. Fields with {@code since} values less than or
   * equal to this are retained.
   */
  private final String untilVersion;

  /**
   * The only version of the version range. Fields with {@code until} values greater than this,
   * as well as fields with {@code since} values less than or equal to this, are retained. This
   * field is mutually exclusive with {@link #sinceVersion} and {@link #untilVersion}.
   */
  private final String onlyVersion;

  /** Action to take with the loaded, resolved, and possibly-pruned schema. */
  private final List<Target> targets;

  /**
   * A map from module dir to module info which dictates how the loaded types are partitioned and
   * generated.
   *
   * <p>When empty everything is generated in the root output directory. If desired, multiple
   * modules can be specified along with dependencies between them. Types which appear in
   * dependencies will not be re-generated.
   */
  private final Map<String, Module> modules;

  /** If true, no validation will be executed to check package cycles. */
  private final boolean permitPackageCycles;

  /**
   * If true, the schema loader will load the whole graph, including files and types not used by
   * anything in the source path.
   */
  private final boolean loadExhaustively;

  /**
   * If true, Kotlin keywords are escaped with backticks. If false, an underscore is added as a
   * suffix.
   */
  private final boolean escapeKotlinKeywords;

  private final List<EventListener> eventListeners;

  /**
   * If true, Wire will fail if not all {@link #treeShakingRoots} and {@link #treeShakingRubbish}
   * are used when tree-shaking the schema. This can help discover incorrect configurations early
   * and avoid misexpectations about the built schema.
   *
   * <p>If false, unused {@link #treeShakingRoots} and {@link #treeShakingRubbish} will be
   * printed as warnings.
   */
  private final boolean rejectUnusedRootsOrPrunes;

  /**
   * All qualified named Protobuf types in {@code opaqueTypes} will be evaluated as being of type
   * {@code bytes}. On code generation, the fields of such types will be using the platform
   * equivalent of {@code bytes}, like {@code ByteString} for the JVM. Note that scalar types
   * cannot be opaqued. The opaque step will happen before the tree shaking one.
   */
  private final List<String> opaqueTypes;

  public WireRun(
      List<Location> sourcePath,
      List<Location> protoPath,
      List<String> treeShakingRoots,
      List<String> treeShakingRubbish,
      List<TypeMover.Move> moves,
      String sinceVersion,
      String untilVersion,
      String onlyVersion,
      List<Target> targets,
      Map<String, Module> modules,
      boolean permitPackageCycles,
      boolean loadExhaustively,
      boolean escapeKotlinKeywords,
      List<EventListener> eventListeners,
      boolean rejectUnusedRootsOrPrunes,
      List<String> opaqueTypes) {
    this.sourcePath = sourcePath;
    this.protoPath = protoPath;
    this.treeShakingRoots = treeShakingRoots;
    this.treeShakingRubbish = treeShakingRubbish;
    this.moves = moves;
    this.sinceVersion = sinceVersion;
    this.untilVersion = untilVersion;
    this.onlyVersion = onlyVersion;
    this.targets = targets;
    this.modules = modules;
    this.permitPackageCycles = permitPackageCycles;
    this.loadExhaustively = loadExhaustively;
    this.escapeKotlinKeywords = escapeKotlinKeywords;
    this.eventListeners = eventListeners;
    this.rejectUnusedRootsOrPrunes = rejectUnusedRootsOrPrunes;
    this.opaqueTypes = opaqueTypes;
  }

  /** Source {@code .proto} files for this task to generate from. */
  public List<Location> sourcePath() {
    return sourcePath;
  }

  /** Sources {@code .proto} files for this task to use when resolving references. */
  public List<Location> protoPath() {
    return protoPath;
  }

  public List<String> treeShakingRoots() {
    return treeShakingRoots;
  }

  public List<String> treeShakingRubbish() {
    return treeShakingRubbish;
  }

  public List<TypeMover.Move> moves() {
    return moves;
  }

  public String sinceVersion() {
    return sinceVersion;
  }

  public String untilVersion() {
    return untilVersion;
  }

  public String onlyVersion() {
    return onlyVersion;
  }

  public List<Target> targets() {
    return targets;
  }

  public Map<String, Module> modules() {
    return modules;
  }

  public boolean permitPackageCycles() {
    return permitPackageCycles;
  }

  public boolean loadExhaustively() {
    return loadExhaustively;
  }

  public boolean escapeKotlinKeywords() {
    return escapeKotlinKeywords;
  }

  public List<EventListener> eventListeners() {
    return eventListeners;
  }

  public boolean rejectUnusedRootsOrPrunes() {
    return rejectUnusedRootsOrPrunes;
  }

  public List<String> opaqueTypes() {
    return opaqueTypes;
  }

  public static final class Module {
    private final Set<String> dependencies;
    private final PruningRules pruningRules;

    public Module(Set<String> dependencies, PruningRules pruningRules) {
      this.dependencies = dependencies;
      this.pruningRules = pruningRules;
    }

    public Module() {
      this(Collections.emptySet(), null);
    }

    public Module(Set<String> dependencies) {
      this(dependencies, null);
    }

    public Set<String> dependencies() {
      return dependencies;
    }

    public PruningRules pruningRules() {
      return pruningRules;
    }
  }

  private void checkForModuleCycles() {
    DagChecker<String> dagChecker = new DagChecker<>(
        modules.keySet(), moduleName -> modules.get(moduleName).dependencies());
    Set<List<String>> cycles = dagChecker.check();
    if (cycles.isEmpty()) return;
    StringBuilder message = new StringBuilder();
    message.append("ERROR: Modules contain dependency cycle(s):\n");
    for (List<String> cycle : cycles) {
      message.append(" - ");
      message.append(cycle);
      message.append('\n');
    }
    throw new IllegalArgumentException(message.toString());
  }

  public void execute(FileSystem fs, WireLogger logger) throws IOException {
    execute(fs, logger, new SchemaLoader(fs));
  }

  void execute(FileSystem fs, WireLogger logger, SchemaLoader schemaLoader) throws IOException {
    for (EventListener eventListener : eventListeners) {
      eventListener.runStart(this);
    }

    checkForModuleCycles();

    schemaLoader.setPermitPackageCycles(permitPackageCycles);
    List<ProtoType> opaqueProtoTypes = new ArrayList<>();
    for (String opaqueType : opaqueTypes) {
      opaqueProtoTypes.add(ProtoType.get(opaqueType));
    }
    schemaLoader.setOpaqueTypes(opaqueProtoTypes);
    schemaLoader.setLoadExhaustively(loadExhaustively);
    schemaLoader.initRoots(sourcePath, protoPath);

    // Validate the schema and resolve references
    for (EventListener eventListener : eventListeners) {
      eventListener.loadSchemaStart();
    }
    Schema fullSchema = schemaLoader.loadSchema();
    for (EventListener eventListener : eventListeners) {
      eventListener.loadSchemaSuccess(fullSchema);
    }

    // Refactor the schema.
    Schema schema = refactorSchema(
        fullSchema,
        logger,
        eventListeners,
        rejectUnusedRootsOrPrunes);

    List<Target> targetsExclusiveLast = new ArrayList<>(targets);
    targetsExclusiveLast.sort(Comparator.comparing(Target::exclusive));
    Set<String> sourcePathPaths = new LinkedHashSet<>();
    for (ProtoFile protoFile : schemaLoader.sourcePathFiles()) {
      sourcePathPaths.add(protoFile.location().path);
    }
    for (TypeMover.Move move : moves) {
      sourcePathPaths.add(move.targetPath);
    }
    ClaimedPaths claimedPaths = new ClaimedPaths();
    ErrorCollector errorCollector = new ErrorCollector();
    // We keep a reference to all rules so that we can log unused elements later.
    Map<Target, EmittingRules> targetToEmittingRules = new LinkedHashMap<>();
    for (Target target : targets) {
      targetToEmittingRules.put(target, new EmittingRules.Builder()
          .include(target.includes())
          .exclude(target.excludes())
          .build());
    }

    Map<String, PartitionedSchema.Partition> partitions;
    if (!modules.isEmpty()) {
      PartitionedSchema partitionedSchema = PartitionedSchema.partition(schema, modules);
      // TODO handle errors and warnings. Errors could be added to ErrorCollector but need a test.
      partitions = partitionedSchema.partitions;
    } else {
      // Synthesize a single partition that includes everything from the schema.
      partitions = new LinkedHashMap<>();
      partitions.put(null, new PartitionedSchema.Partition(schema));
    }

    for (EventListener eventListener : eventListeners) {
      eventListener.schemaHandlersStart();
    }
    for (Map.Entry<String, PartitionedSchema.Partition> entry : partitions.entrySet()) {
      String moduleName = entry.getKey();
      PartitionedSchema.Partition partition = entry.getValue();
      ClaimedDefinitions claimedDefinitions = new ClaimedDefinitions();
      claimedDefinitions.claim(ProtoType.ANY);

      for (Target target : targetsExclusiveLast) {
        SchemaHandler handler = target.newHandler();
        SchemaHandler.Module module = moduleName == null
            ? null
            : new SchemaHandler.Module(moduleName, partition.types,
                partition.transitiveUpstreamTypes);
        Path outDirectory = moduleName == null
            ? Path.get(target.outDirectory())
            : Path.get(target.outDirectory()).div(moduleName);
        SchemaHandler.Context context = new SchemaHandler.Context(
            fs,
            outDirectory,
            logger,
            errorCollector,
            targetToEmittingRules.get(target),
            target.exclusive() ? claimedDefinitions : null,
            claimedPaths,
            sourcePathPaths,
            module,
            schemaLoader,
            schema);

        for (EventListener eventListener : eventListeners) {
          eventListener.schemaHandlerStart(handler, targetToEmittingRules.get(target));
        }
        handler.handle(partition.schema, context);
        for (EventListener eventListener : eventListeners) {
          // TODO(Benoit) Pass the definitions claimed by this target?
          eventListener.schemaHandlerEnd(handler, targetToEmittingRules.get(target));
        }
      }
    }
    for (EventListener eventListener : eventListeners) {
      eventListener.schemaHandlersEnd();
    }

    List<String> errors = errorCollector.errors();
    if (!errors.isEmpty()) {
      for (EventListener eventListener : eventListeners) {
        eventListener.runFailed(errorCollector.errors());
      }
      throw new SchemaException(errors);
    }

    for (EmittingRules emittingRules : targetToEmittingRules.values()) {
      Set<String> unusedIncludes = emittingRules.unusedIncludes();
      // The '*' here is the default includes rule. It's okay if this is unused.
      if (!unusedIncludes.isEmpty() && !unusedIncludes.equals(Collections.singleton("*"))) {
        logger.unusedIncludesInTarget(unusedIncludes);
      }

      Set<String> unusedExcludes = emittingRules.unusedExcludes();
      if (!unusedExcludes.isEmpty()) {
        logger.unusedExcludesInTarget(unusedExcludes);
      }
    }

    for (EventListener eventListener : eventListeners) {
      eventListener.runSuccess(this);
    }
  }

  /** Returns a transformed schema with unwanted elements removed and moves applied. */
  private Schema refactorSchema(
      Schema schema,
      WireLogger logger,
      List<EventListener> eventListeners,
      boolean rejectUnusedRootsOrPrunes) {
    if (treeShakingRoots.equals(Collections.singletonList("*"))
        && treeShakingRubbish.isEmpty()
        && sinceVersion == null
        && untilVersion == null
        && moves.isEmpty()) {
      return schema;
    }

    PruningRules pruningRules = new PruningRules.Builder()
        .addRoot(treeShakingRoots)
        .prune(treeShakingRubbish)
        .since(sinceVersion)
        .until(untilVersion)
        .only(onlyVersion)
        .build();

    for (EventListener eventListener : eventListeners) {
      eventListener.treeShakeStart(schema, pruningRules);
    }
    Schema prunedSchema = schema.prune(pruningRules);
    for (EventListener eventListener : eventListeners) {
      eventListener.treeShakeEnd(prunedSchema, pruningRules);
    }

    boolean hasUnusedRoots = !pruningRules.unusedRoots().isEmpty();
    boolean hasUnusedPrunes = !pruningRules.unusedPrunes().isEmpty();
    if (hasUnusedRoots || hasUnusedPrunes) {
      if (rejectUnusedRootsOrPrunes) {
        List<String> messages = new ArrayList<>();
        if (hasUnusedRoots) {
          messages.add("Unused element(s) in roots:\n  "
              + String.join("\n  ", pruningRules.unusedRoots()));
        }
        if (hasUnusedPrunes) {
          messages.add("Unused element(s) in prunes:\n  "
              + String.join("\n  ", pruningRules.unusedPrunes()));
        }
        throw new IllegalStateException(String.join("\n", messages));
      } else {
        if (hasUnusedRoots) {
          logger.unusedRoots(pruningRules.unusedRoots());
        }
        if (hasUnusedPrunes) {
          logger.unusedPrunes(pruningRules.unusedPrunes());
        }
      }
    }

    for (EventListener eventListener : eventListeners) {
      eventListener.moveTypesStart(prunedSchema, moves);
    }
    Schema movedSchema = new TypeMover(prunedSchema, moves).move();
    for (EventListener eventListener : eventListeners) {
      eventListener.moveTypesEnd(movedSchema, moves);
    }
    return movedSchema;
  }
}
