# TASK-9 case accounting: upstream runtime tests to port artifacts

Inventory of every wire-runtime runtime-behavior test case and where it runs against the
port, per TASK-9 AC#1/AC#2 and DEC-5. Pinned tag: 7.1.0. Ported suites run in the shared CI
entry point via mvn verify.

## wire-runtime commonTest (13 files, all applicable, all adopted)

| Upstream file | Port file | Cases | Adaptations (ledger rows in UPSTREAM-TEST-ADAPTATIONS.md) |
|---|---|---|---|
| ProtoWriterTest.kt (Kotlin, verbatim) | wire-runtime-java/src/test/kotlin/.../ProtoWriterTest.kt | utf8 | R3 utf8Size to Utf8.size; kotlin.test.Test to Jupiter (per-site) |
| ProtoReaderTest.kt | src/test/java/.../ProtoReaderTest.java | packed replay, length-delimited nesting, group recursion limit, negative-length group rejection, fixed32 limit | assertk to JUnit 5; Person fixture in TestMessages |
| ProtoReader32Test.kt | src/test/java/.../ProtoReader32Test.java | packed replay, nesting, recursion limit, negative length, Int.MAX overflow vectors, fixed32 limit | factory to ByteArrayProtoReader32 |
| ReverseProtoWriterTest.kt | src/test/java/.../ReverseProtoWriterTest.java | utf8 corpus incl. malformed surrogates, forward consistency, segment spanning, embedded message | fixtures in TestMessages |
| ProtoAdapterTest.kt | src/test/java/.../ProtoAdapterTest.java | packed/repeated combinations, Instant boundaries and rejections | assertk to JUnit 5; ofEpochSecond JVM factory |
| DurationTest.kt | src/test/java/.../DurationTest.java | ofSeconds normalization (positive, zero, negative-near-zero, negative, equality) | Duration is java.time.Duration (JVM actual) |
| InstantTest.kt (5 cases) | src/test/java/.../InstantTest.java | 5 adopted (TASK-14.2 corrected) | the 2 negative-nano cases were previously EXCLUDED on the false premise that java.time.Instant.ofEpochSecond throws for negative nano; it normalizes to exactly the upstream expected values, and the never-compiled boundary lock asserting that premise failed on first execution, so both cases are ported verbatim; this file and DurationTest.java also sat in src/test/kotlin, where .java sources never compile, so neither class ever ran until TASK-14.2 moved both to src/test/java |
| FieldMaskTest.kt | src/test/java/.../FieldMaskTest.java | storage, immutability, copy, encode/decode hex vectors, typeUrl, unknown-field discard | paths() for the Kotlin property |
| internal/InternalTest.kt | src/test/java/.../internal/InternalTest.java | countNonNull (incl. null varargs), sanitize vectors, lower/upper camelCase incl. astral code points, version exposure | camelCase explicit upperCamel arg; wireVersion maps to Internal.WIRE_VERSION |
| internal/IntArrayListTest.kt | src/test/java/.../internal/IntArrayListTest.java | truncation, toString | Java-shape port (upstream has none): assertk to JUnit 5 |
| internal/LongArrayListTest.kt | idem | 2 cases | idem |
| internal/FloatArrayListTest.kt | idem | 2 cases | idem |
| internal/DoubleArrayListTest.kt | idem | 2 cases | idem |

## wire-tests/jvm-java-kotlin (9 Java files, adopted)

| Upstream file | Port file | Status |
|---|---|---|
| WireTest.java (16 cases: oneof merge, maps, recursive maps, redaction, options, enums, extension fieldname collisions) | wire-tests-java/src/test/java/.../WireTest.java | adopted verbatim (throws declarations added where Kotlin did not need them) |
| ParseTest.java (8 cases, 2 @Ignore upstream) | wire-tests-java/src/test/java/.../ParseTest.java | adopted verbatim |
| SerializableTest.java (4) | idem | adopted verbatim |
| UnknownFieldsTest.java (6) | idem | adopted verbatim |
| OneOfTest.java (3) | idem | adopted verbatim |
| RuntimeMessageAdapterRedactTest.java (9) | idem | adopted verbatim |
| TestAllTypes.java + TestAllTypesData.java | idem | adopted verbatim (throws added) |
| ProtoAdapterTest.java (4) | idem | adopted verbatim |
| MapTest.kt (gson), MoshiRedactedTest.kt (moshi) | EXCLUDED | DEC-6 excludes JSON adapters; JSON-only cases recorded here, not silently dropped |

## wire-tests/jvm-kotlin-proto-reader-32 (2 files, ported)

| Upstream file | Port file | Notes |
|---|---|---|
| ProtoReader32AdapterTest.kt (core 3 decode cases) | wire-tests-java/src/test/java/.../ProtoReader32AllTypesTest.java | Kotlin AllTypes to Java AllTypes; array_* fields absent from the Java generator (excluded); builder copy via newBuilder()+setters (7.1.0 has no Builder copy ctor); packed consumption via commonTryDecode pattern |
| ProtoReader32OneOfTest.kt (4 cases) | wire-tests-java/src/test/java/.../ProtoReader32OneOfTest.java | Kotlin boxed Inner fixture excluded (DEC-6); flat OneOfMessage exercises the same reader merge semantics |

## Totals

commonTest port: 857 tests at TASK-9 close in wire-runtime-java (909 on 2026-10-06) (0 failures, 4 upstream skips) of which
~120 are upstream-translated runtime cases; wire-tests-java: 79 tests (0 failures) of which
~90 upstream runtime-behavior cases including the cross-checked adapters, plus the
unknown-field, redaction and serialization contract tests. Known divergences: 2 (pack error
KClass rendering; listRecursively eager snapshot), ledgered in docs/m1-ownership-map.md and
the task-9 case rows above.
