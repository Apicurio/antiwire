# TASK-13 case accounting: upstream wire-schema test adoption

Status: TASK-13 deliverable, 2026-10-02. Complete case accounting for every test file in
upstream wire-schema at the pinned tag (7.1.0), per DEC-5: every relevant upstream test case
is adopted or has an explicit, owned disposition. Companion documents:
[translation-conventions.md](translation-conventions.md) (adaptation rulebook, section 6),
[task9-case-accounting.md](task9-case-accounting.md) (the runtime suites' ledger).

## Adopted in full (this task)

Upstream counts are `@Test` methods; port counts include merged non-upstream cases where noted.

| Upstream file | Tests | Port file | Notes |
|---|---|---|---|
| commonTest internal/parser/ProtoParserTest.kt | 88 | ProtoParserFullTest (88) | Full corpus; existing partial ProtoParserTest kept as TASK-10 smoke. Two SyntaxReader fidelity fixes landed (error location before backtrack; inclusive-end trailing-comment extraction). |
| commonTest internal/parser/MessageElementTest.kt | 25 | MessageElementTest (25) | toSchema newline-before-extensions-loop fix landed. |
| commonTest internal/parser/ProtoFileElementTest.kt | 19 | ProtoFileElementTest (19) | PACKED.copy replaced by an equal construction (no OptionElement.copy in the port). |
| commonTest internal/parser/EnumElementTest.kt | 10 | EnumElementTest (10) | Partial file's cases subsumed. |
| commonTest internal/parser/ServiceElementTest.kt | 12 | ServiceElementTest (12) | |
| commonTest internal/parser/FieldElementTest.kt | 5 | FieldElementTest (6) | +1 non-upstream case carried from the deleted partial. OptionElement data-class equals/hashCode landed. |
| commonTest internal/parser/ExtendElementTest.kt | 6 | ExtendElementTest (6) | |
| commonTest internal/parser/ExtensionsElementTest.kt | 5 | ExtensionsElementTest (5) | int[] ranges; structural element equality landed. |
| commonTest internal/parser/OneOfElementTest.kt | 1 | OneOfElementTest (1) | Whole-element equality + round trip. |
| commonTest internal/parser/OptionElementTest.kt | 5 | OptionElementTest (6) | Overwrites the TASK-10 partial; 1 non-upstream case kept. |
| commonTest EmittingRulesTest.kt | 15 | EmittingRulesTest (15) | |
| commonTest PruningRulesTest.kt | 31 | PruningRulesTest (31) | Upstream Policy enum translated. |
| commonTest SemVerTest.kt | 8 | SemVerFullTest (8) | Port-authored SemVerTest (3) kept. Constructor widened to package-private (upstream module-internal). |
| commonTest MavenVersionsTest.kt | 5 | MavenVersionsTest (5) | Assessed as SemVer-owned (no maven-metadata anywhere upstream); adopted against the ported SemVer. |
| commonTest LocationTest.kt | 6 | LocationTest (12) | +6 merged non-upstream cases; 2 partial cases dropped as subsumed (upstream-identical coverage). |
| commonTest ProtoTypeTest.kt | 12 | ProtoTypeTest (15) | +3 merged; 7 partial cases dropped as subsumed. |
| commonTest ProtoFileTest.kt | 1 | ProtoFileTest (1) | No MockLoader exists upstream at the pin; direct ProtoFile.get path. |
| commonTest internal/DagCheckerTest.kt | 12 | internal/DagCheckerTest (12) | |
| commonTest internal/UtilTest.kt | 2 | internal/UtilTest (2) | SchemaUtil statics. |
| jvmTest SchemaTest.kt | 102 | SchemaFullTest (102, 1 @Disabled mirroring upstream @Ignore) | Exact SchemaException messages with temp-root normalization. |
| jvmTest OptionsTest.kt | 13 | OptionsTest (13) | |
| jvmTest PrunerTest.kt | 99 | PrunerTest (99, 4 @Disabled mirroring upstream @Ignore) | Byte-exact toSchema expectations. |
| jvmTest SchemaLoaderTest.kt | 16 | SchemaLoaderTest (16, 3 @Disabled) | 3 profile cases deferred with TASK-16 (below). loadSourcePathFiles/reportLoadingErrors widened to public (pre-annotated). |
| jvmTest RootTest.kt | 6 | RootTest (6) | Symlink targets absolute (port resolves them itself); skip when the host refuses symlinks, mirroring upstream's Windows guard. |
| jvmTest SchemaProtoAdapterTest.kt | 11 | SchemaProtoAdapterTest (11) | |
| jvmTest DynamicSerializationTest.kt | 6 | DynamicSerializationTest (9, 1 @Disabled mirroring upstream @Ignore) | JDK time types; EOF/Protocol exception relocations. The Empty/Unit divergence this row carried was resolved by TASK-26 (2026-10-03): the `empty_field` expectation runs again against `UnitValue` (docs/api-surface.md), plus three port-added Empty cases. |
| jvmTest internal/SchemaEncoderTest.kt | 5 | internal/SchemaEncoderFullTest (5) | protobuf-java test-scoped (upstream's own jvmTest pin 4.36.1); FileDescriptorProto oracle comparison. |
| jvmTest DirectedAcyclicGraphTest.kt | 6 | DirectedAcyclicGraphTest (6) | DirectedAcyclicGraph ported to main (its production user PartitionedSchema is TASK-16 scope). |

Port-only suites retained alongside the adoptions: LinkingSmokeTest (17), SchemaLoaderSmokeTest (8),
internal/SchemaEncoderTest (6), ProtoParserTest (10), SemVerTest (3), EnumAndReservedElementTest
(3), ShellPlaceholderTest (1). Module total: 590 tests, 0 failures, 9 skipped.

## Deferred with an owner

Resolved 2026-10-02 by TASK-16 batch C: every row below is adopted and running in the port; see
[task16-case-accounting.md](task16-case-accounting.md) for the per-file adaptation records and
the case map (config/upstream-case-map.json) for the current reconciliation.

| Upstream file | Cases | Owner | Reason |
|---|---|---|---|
| SchemaLoaderTest locationsToCheck, pathsToAttempt, pathsToAttemptMultipleRoots | 3 | TASK-16 | CommonSchemaLoader.locationsToCheck sits in the unported profile layer; bodies preserved verbatim as comments in the ported test. |
| commonTest SchemaHandlerTest.kt | all | TASK-16 | SchemaHandler is compiler machinery. |
| jvmTest ManifestPartitionTest.kt | all | TASK-16 | PartitionedSchema not ported. |
| jvmTest internal/TypeMoverTest.kt | all | TASK-16 | TypeMover is compiler machinery. |
| commonTest internal/ProfileParserTest.kt, jvmTest ProfileLoaderTest.kt | all | TASK-16 | Profile layer, per the TASK-11/12 separation decision. |

## Excluded

| Upstream file | Reason |
|---|---|
| jvmTest internal/parser/ParsingTester.kt | A manual main() harness with zero assertions and a placeholder filesystem root; it is not a test case. Equivalent capability is covered by the adopted parser suites. |

## Structural adaptations (blanket, recorded once here)

- FakeFileSystem is not ported: linking suites build schemas through real temp directories
  (SchemaBuilder, or JUnit @TempDir for multi-root layouts). Exact-message assertions normalize
  the temp root via SchemaBuilder.normalizeLocations or interpolate the prefix.
- assertk to JUnit 5; assertFailsWith mapped by Kotlin construct (IllegalArgumentException for
  require(), IllegalStateException for check()/error()).
- Kotlin named-argument element construction to positional with upstream's declared defaults.
- Kotlin IntRange to the port's int[] range representation; Reserved/Extensions equality is
  structural at both the element and schema levels (IntRange semantics).
- okio.EOFException to java.io.EOFException and okio.ProtocolException to the internal
  ProtocolException (the vendored subset keeps JDK/package-internal exception types).
- Kotlin `internal` maps to public inside `.internal` packages (the compatibility-matrix rule)
  and to package-private elsewhere; both sites carry in-code justification comments.
- Full corpora are named `*FullTest` (ProtoParserFullTest, SchemaFullTest, SemVerFullTest,
  SchemaEncoderFullTest) when the port-authored partial keeps the upstream-colliding name;
  ProtoParserFullTest lives in package com.squareup.wire (upstream's is internal.parser). The
  per-file rows above are TASK-14's reconciliation input.
- Ranges ride int[] (upstream IntRange); equality is structural at both element and schema
  layers via shared valuesEqual/valuesHashCode helpers. Data-class toString renders ranges as
  array text, a latent divergence no test asserts today; revisit on an upstream sync.
