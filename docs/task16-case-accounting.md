# TASK-16 case accounting

Status: TASK-16 batch C deliverable, 2026-10-02. Per-file adoption ledger for the upstream test
corpus translated with the TASK-16 compiler/generator batch, in the format of
[task13-case-accounting.md](task13-case-accounting.md) and
[task9-case-accounting.md](task9-case-accounting.md). The reconciliation map is
[../config/upstream-case-map.json](../config/upstream-case-map.json); this file records the
per-file adaptations, disabled cases, and port changes the adoption forced. Upstream paths are
relative to the pinned clone (square/wire tag 7.1.0, default `/tmp/wire`).

Totals: 190 upstream cases across 14 upstream test files; 157 ported and running, 33 disabled
with recorded reasons, 0 missing.

## Batch 1: wire-schema-java/src/test/java (TASK-13 deferrals adopted here)

| Upstream file | Cases | Port file | Adaptations |
|---|---|---|---|
| commonTest SchemaHandlerTest.kt | 2 ported | wire-schema-java/.../schema/SchemaHandlerTest.java | assertFailsWith to assertThrows; raw-string UNC paths to Java literals; the protected checkPathInOutDirectory is reached through the upstream TestSchemaHandler wrapper. Ran unadapted only after the vendored okio Path fix below. |
| jvmTest ManifestPartitionTest.kt | 3 ported | wire-schema-java/.../schema/ManifestPartitionTest.java | assertk to JUnit 5; buildSchema to SchemaBuilder; the internal Schema.partition extension is a direct call to the package-private PartitionedSchema.partition; expected warning text verbatim. |
| jvmTest ProfileLoaderTest.kt | 4 ported | wire-schema-java/.../schema/ProfileLoaderTest.java | FakeFileSystem to @TempDir (missingImport message interpolates the temp root); port Profile is poet-free (OPEN-2) so ClassName.OBJECT and ClassName.get(String::class.java) assert as the strings java.lang.Object and java.lang.String; AdapterConstant(String) becomes AdapterConstant.get(String). |
| commonTest internal/ProfileParserTest.kt | 4 ported | wire-schema-java/.../internal/ProfileParserTest.java | assertk to JUnit 5; named-argument element construction to positional with upstream defaults (R1); expected elements, locations, and error strings verbatim. |
| jvmTest internal/TypeMoverTest.kt | 7 ported | wire-schema-java/.../internal/TypeMoverTest.java | assertk to JUnit 5; ProtoType.get("cafe", "EspressoShot") to ProtoType.get("cafe.EspressoShot"); toSchema outputs and the moveInexistentType message verbatim. |
| jvmTest SchemaLoaderTest.kt: locationsToCheck, pathsToAttempt, pathsToAttemptMultipleRoots | 3 revived | wire-schema-java/.../schema/SchemaLoaderTest.java | The three TASK-13 deferrals run upstream's bodies verbatim now that CommonSchemaLoader.locationsToCheck is public; containsExactlyInAnyOrder maps to TestFiles.assertContainsExactlyInAnyOrder. |

## Batch 2: wire-java-generator/src/test/java

| Upstream file | Cases | Port file | Adaptations |
|---|---|---|---|
| wire-java-generator JavaGeneratorTest.java | 40 ported | wire-java-generator/.../java/JavaGeneratorTest.java | The upstream file is already Java; JUnit 4/Truth to JUnit 5; SchemaBuilder.add(Path, String) to the port builder's add(String, String); palantir javapoet to Square javapoet 1.13.0. Every expected string verbatim, including the two full abstract-adapter files. |
| wire-java-generator JavaGeneratorHelper.kt, JavaWithProfilesGenerator.kt | helpers | .../java/JavaGeneratorHelper.java, JavaWithProfilesGenerator.java | Kotlin helpers to Java with @JvmOverloads defaults as explicit overloads. |
| wire-compiler WireCompilerTest.kt | 10 ported, 3 disabled | wire-java-generator/.../WireCompilerTest.java | FakeFileSystem to @TempDir (locations interpolate the temp root where upstream asserts its "/" root: Location.get("/").path becomes the temp root); data-class target equality to per-property assertions (port targets are plain classes); `default` renamed `default_` (Java keyword). Disabled (DEC-6): allFlags, treeShaking, allTargetsAndAllOptions (Kotlin/Swift flags this port rejects). |
| wire-compiler WireCompilerErrorTest.kt | 7 ported | .../WireCompilerErrorTest.java | FakeFileSystem to @TempDir; the exact messages interpolate the absolute source root where upstream writes /source. Messages verbatim otherwise. |
| wire-compiler CommandLineOptionsTest.kt | 8 ported, 1 disabled | .../CommandLineOptionsTest.java | assertk to JUnit 5; package-visible compiler fields read directly. Disabled (DEC-6): kotlinEnumMode. |
| wire-compiler ManifestParseTest.kt | 3 ported | .../ManifestParseTest.java | The internal top-level parseManifestModules is the port's package-private WireCompiler.parseManifestModules; unknown-key assertions keep upstream kaml wording. |
| wire-compiler StringWireLogger.kt | helper | .../StringWireLogger.java | Kotlin string templates to concatenation; trimMargin log shapes probed against the real Kotlin stdlib (no trailing newline in the unused* blocks). |
| wire-compiler schema/WireRunTest.kt | 44 ported, 29 disabled | .../schema/WireRunTest.java | FakeFileSystem to @TempDir: every target out directory is rooted at the temp directory (upstream's FakeFileSystem roots them at "/"), so the crashWhenTypeGenerationConflicts message interpolates the absolute out directory; exact messages otherwise verbatim. KotlinTarget substituted with JavaTarget only where no assertion mentions Kotlin output (myEventListenerSuccess, unusedTreeShakingRoots, unusedTreeShakingPrunes, unusedTreeShakingRootsAndPrunes, skipDeclaredOptions, importNotFoundIncludesReferencingFile); javaPackageForJvmLanguages keeps its Java half and drops the Kotlin half (TASK-15 mixed-case convention). Disabled: 27 DEC-6 (Kotlin generator expectations) and 2 for the unported ProtoTarget (protoOnly, protoTargetNeverEmitsGoogleProtobufDescriptor; a TASK-16 scope gap, recorded below). |
| wire-compiler schema/CycleCheckerTest.kt | 6 ported | .../schema/CycleCheckerTest.java | FakeFileSystem to @TempDir; cycle messages print import paths only, so they run verbatim. |
| wire-compiler schema/LinkerTest.kt | 14 ported | .../schema/LinkerTest.java | FakeFileSystem to @TempDir (location lists and opaque-type messages interpolate the temp roots); the proto-path root is materialized because the port loader rejects missing roots; schemaIsDeterministicEvenIfProtoPathOrderIsNot reverses list() per directory because the vendored okio keeps listRecursively final. |
| wire-compiler schema/OptionsLinkingTest.kt | 5 ported | .../schema/OptionsLinkingTest.java | FakeFileSystem to @TempDir; Kotlin mapOf to an insertion-ordered LinkedHashMap helper; option maps and messages verbatim. |
| wire-compiler schema/MarkdownHandler.kt | helper | .../schema/MarkdownHandlerFactory.java | Kotlin handler to Java; checked IOException leaves the SchemaHandler overrides via RuntimeException wrapping. |

## Port changes forced by adoption (divergences fixed, not papered over)

1. `wire-runtime-java/src/main/java/okio/Path.java`: `root()`, `volumeLetter()`, `segments()`,
   and `isAbsolute()` are now computed lexically from the path string exactly like okio 3.18.2's
   commonMain `internal/Path.kt` (verified against that tag's source, 2026-10-02), instead of
   delegating to the nio provider. Before the fix, `SchemaHandlerTest.generatedPathCanUseSameUncRoot`
   failed on macOS: the nio wrapper saw `\\trusted\generated\Message.java` as a one-segment
   relative path and rejected it, while okio parses the `\\trusted` UNC root on every platform
   and accepts it. Probe-confirmed before and after.
2. `wire-java-generator/src/main/java/com/squareup/wire/WireCompiler.java`
   (`parseManifestModules`): the hand-rolled YAML parser now accepts the inline empty-module
   form `name: {}` that upstream kaml accepts (four upstream cases construct it) and reports
   unknown keys with kaml's `Unknown property 'x'` wording (asserted by ManifestParseTest);
   visibility widened from private to package-private, matching upstream's `internal` mapped
   per the TASK-13 blanket rule.

## Reported divergences (no port change; recorded, not hidden)

- ProtoTarget is not part of the ported compiler surface, so `protoOnly` and
  `protoTargetNeverEmitsGoogleProtobufDescriptor` cannot run. Upstream's ProtoTarget emits
  `.proto` files and lives in wire-schema; TASK-16's production batch did not port it. If proto
  emission becomes in scope, both cases revive verbatim against @TempDir trees.
- The port's `Profile` returns target names as strings and `AdapterConstant` without poet
  classes (OPEN-2 decision, recorded in Profile's javadoc); ProfileLoaderTest asserts the same
  values in string form.
- The port's targets (JavaTarget, CustomTarget) have no data-class equals, so WireCompilerTest's
  upstream `containsExactly(JavaTarget(...))` assertions became per-property assertions of the
  same field values.
- `google.protobuf.Empty` representation (OPEN-4): no adopted case in this corpus compiles
  generated output against the runtime, so no OPEN-4 disables were needed here; the gap remains
  recorded in compatibility-matrix section E.
- Full-reactor `mvn test` fails at the pinned baseline independently of this batch: with this
  change present, surefire's fork in wire-runtime-java dies on `TestEngine with ID
  'junit-vintage' failed to discover tests` (junit-vintage arrives transitively with
  wire-upstream-shaded in the reactor); on a clean checkout of e7b93da the same reactor build
  fails earlier at wire-runtime-java testCompile (`package
  io.github.paoloantinori.antiwire.parity.wire does not exist`). Module-scoped runs
  (`mvn -pl <module> test`) are green for every module, including both gates.

## Gates

- `JAVA_HOME=~/.sdkman/candidates/java/17.0.12-tem mvn -pl wire-schema-java test`: green,
  611 tests (591 before this batch plus 20 new cases: 2 SchemaHandlerTest, 3
  ManifestPartitionTest, 4 ProfileLoaderTest, 4 ProfileParserTest, 7 TypeMoverTest; the 3
  revived SchemaLoaderTest cases were already present as skipped and now execute).
- `JAVA_HOME=~/.sdkman/candidates/java/17.0.12-tem mvn -pl wire-java-generator test`: green,
  171 tests, 33 skipped (all disabled with reasons above).
- `python3 scripts/check-parity-coverage.py`: PASS, 0 deferred files.
