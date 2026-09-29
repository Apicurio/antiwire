# Research Report: Porting Square Wire to Pure Java (Project "antiwire")

**Date**: 2026-09-29
**Depth**: exhaustive
**Confidence**: HIGH for dependency/footprint/test-infrastructure facts (verified against the real artifacts: cloned sources, published POMs, jar sizes); MEDIUM for effort estimates and performance expectations.

## Executive Summary

Wire 7.1.0 (released 2026-09-28) is a Kotlin Multiplatform project whose JVM artifacts drag kotlin-stdlib (1.7 MB), okio-jvm (0.4 MB, itself Kotlin), kotlinpoet and guava into any consumer. Apicurio Registry consumes exactly one Wire artifact, `wire-schema`, for schema parsing and the `Schema` model, and Kafka today has no Wire dependency at all and would never accept the Kotlin chain. The pure-Java port surface is well-bounded: about 8.2k LOC for `wire-runtime` (JVM slice) plus about 11.8k LOC for `wire-schema` (JVM slice), with no unsigned types, no coroutines and no value classes in that surface. The same-test-suite requirement is achievable because Wire's own tests are Kotlin but run on the JVM, so they can be kept verbatim as the port's test scope; the external oracle is Wire's `wire-protoc-compatibility-tests` suite, which validates against protoc 4.36.1 output. The one unavoidable okio entanglement (ByteString and BufferedSource are embedded in the public API) is best solved by vendoring a small pure-Java okio subset. Estimated 4 to 6 months of focused work through six milestones, with Apicurio integration reachable after roughly three.

## Findings

### F1. Wire today: version, modules, sizes (HIGH, verified in clone)

Current release is **7.1.0, 2026-09-28** (`CHANGELOG.md` head). Wire 7.0.0 landed 2026-09-10; the 7.0 breaking change was small (refreshed bundled `descriptor.proto`/`struct.proto`). Relevant module inventory with LOC (Kotlin unless noted, excluding build outputs):

| Module | commonMain | jvmMain | other | Role |
|---|---|---|---|---|
| wire-runtime | 6,053 | 2,113 | 39 files common | Encoders, adapters, Message, reflection, well-known types |
| wire-schema | 11,023 | 832 | | Parser, linker, Schema model, SchemaEncoder |
| wire-compiler | 5,768 | | | CLI wrapping schema + generators |
| wire-java-generator | 254 Kotlin | | 3,673 **Java** | Java codegen (already mostly Java) |
| wire-kotlin-generator | 7,267 | | | Kotlin codegen |
| wire-swift-generator + wire-runtime-swift | | | 2,543 + 12,827 Swift | Swift backend |
| wire-grpc-client | 2,896 | | | gRPC (api deps: okhttp, kotlinx-coroutines, okio) |
| wire-gson-support | 508 | | | Gson adapter |
| wire-reflector | 790 | | | Schema to DescriptorProtos (gRPC reflection) |
| wire-gradle-plugin | 3,870 | | | Build-time codegen |
| wire-protoc-compatibility-tests | | | 4,131 | Interop oracle vs protoc |
| wire-golden-files | | | 16,184 + 3,167 Java | Golden generated code |
| wire-tests (fixtures) | | | 6,437 + 2,332 Java | Shared proto corpus |
| wire-test-utils / wire-schema-tests | | | 266 / 868 | Test support |

Toolchain: Kotlin 2.3.21, kotlinCoreLibraries 2.0.21 (so consumers need Kotlin 2.0.21 metadata compatibility), okio 3.18.2, palantir javapoet 0.19.0, kotlinpoet 2.3.0, protobuf-java 4.36.1 (tests only), guava 33.7.1 (`gradle/libs.versions.toml`). No documented minimum JVM; build support compiles at JVM 11, Gradle plugin at JVM 11. Wire was pure Java through 2.3.0; the Kotlin rewrite happened during the 3.0 alphas starting 2019-03-14 ("New: Kotlin Generator", CHANGELOG).

**Editions are not supported**: `ProtoParser.kt:181` throws "edition is not currently supported". Parity with Wire therefore means the port also does not support editions; adding editions would be divergence, not parity (see Risks).

### F2. Dependency footprint (HIGH, verified against published POMs)

Published POMs on Maven Central, wire 7.1.0:

- `wire-runtime-jvm` dependencies: **okio-jvm, kotlin-stdlib**. Jar size 0.3 MB.
- `wire-schema-jvm` dependencies: **okio-jvm, guava, javapoet (com.palantir), kotlinpoet-jvm, wire-runtime-jvm, kotlin-stdlib**. Jar size 0.5 MB.

Reference jar sizes: kotlin-stdlib 2.0.21 is 1.7 MB; okio-jvm 3.18.2 is 0.4 MB; guava 33.7.1-jre is 2.9 MB; protobuf-java 3.25.5 is 1.8 MB.

The guava dependency is nearly free to remove: inside wire-schema only `Multimap.kt` touches `com.google.common` (2 imports). The kotlinpoet/javapoet `api` dependencies exist because `AdapterConstant.kt` and `Profile.kt` (jvmMain) reference poet types; these are code-generation concerns, not schema-model concerns.

A Java consumer of wire-schema today pulls a chain of roughly 3.4 MB of jars beyond JDK (schema 0.5 + runtime 0.3 + okio 0.4 + kotlin-stdlib 1.7 + kotlinpoet ~0.5 + guava if not already present). A pure-Java port that vendors the needed okio subset can ship a single jar around 1 to 1.2 MB with **zero non-JDK transitive dependencies** (if codegen-poet usage is deferred or replaced).

### F3. What "pure Java" must actually replace (HIGH for facts, from source audit)

- **okio is embedded in the public API.** `AnyMessage.value: ByteString`, `ProtoAdapter.BYTES`, and every read/write path use `BufferedSource`/`BufferedSink`. Tests exercise okio semantics. Depending on okio-jvm defeats the purpose (it is Kotlin), so the port vendors a minimal internal okio subset (ByteString, Buffer, Source/Sink, BufferedSource/Sink, Utf8; gzip only if the loader needs it). Precedent: okio was pure Java through 1.x; the API names Wire uses are stable.
- **wire-schema's JVM slice is tiny** (832 LOC) and its only heavy parts are poet references (deferrable) and guava (trivially replaceable).
- **No hard Kotlin-language blockers in the port surface**: no unsigned types, no `suspend`, no value classes in runtime/schema main sources (the `value class` grep hits are annotation definition files). Sealed oneof types are markers (`WireSealedOneof`) that map to plain Java hierarchies; a Java 11 baseline forbids `sealed` anyway.
- **Semantics to watch**: Kotlin `require` throws IllegalArgumentException (same as Java), but `checkNotNull` throws **IllegalStateException**, not NullPointerException; tests assert exception types, so translation conventions must preserve them. Data-class equals/hashCode/toString and `List` equality semantics must be reproduced exactly.
- **Provenance**: `ProtoReader.kt`, `ByteArrayProtoReader32.kt`, `ProtoReader32.kt`, `internal/MathMethods.kt` carry Google (protobuf/R8) Apache-2.0 headers; `internal/*ArrayList.kt` carry JetBrains headers. The port is a derivative work and must preserve all attribution; Wire is Apache 2.0 overall.

### F4. Test suites: what "pass the same test suite" means concretely (HIGH)

Wire has no Google-conformance-suite harness (repo-wide grep found none). Its verification stack is:

1. **Internal unit suites** (multiplatform, JVM-runnable): wire-runtime commonTest (~77 test funs) plus jvmTest; wire-schema commonTest (~283) plus jvmTest (~281). Frameworks: kotlin.test, assertk, junit 4; jvmTest uses jimfs, protobuf-java, wire-test-utils, wire-schema-tests.
2. **External oracle**: `wire-protoc-compatibility-tests` (~153 test funs; Proto2/Proto3 interop, roundtrips through protoc-generated code) built with the google protobuf Gradle plugin pinned to **protoc 4.36.1**, sourcing `descriptor.proto` from the protobuf-java jar.
3. **Golden files**: `wire-golden-files` corpus of generated code, regenerated through WireCompiler.
4. Adapter suites (gson, moshi), reflector suite (grpcurl interop), gradle-plugin tests.

Strategy that satisfies the requirement with maximum fidelity: **keep the upstream Kotlin test sources verbatim in the port's test scope** (dev-time Kotlin only, no runtime Kotlin), run them against the Java main artifacts. "Same suite passed" then means: all upstream JVM-runnable tests for the ported modules, plus the protoc-compatibility suite, green at the pinned tag. Recent security fixes must be covered since the tests encode them: negative-length group skipping (GHSA-7xpr-hc2w-34m9, fixed 6.3.0), JSON null-element rejection (7.1.0), and the 6.4.x change merging duplicate singular message occurrences (#3652/#3656), which is a decode-semantics change the tests pin down.

### F5. Apicurio Registry consumption surface (HIGH, verified against master)

Apicurio pins `<wire-schema.version>6.4.0</wire-schema.version>` and depends on exactly `com.squareup.wire:wire-schema` plus `wire-schema-jvm`; nothing else from Wire. Used API, from imports:

- `com.squareup.wire.schema.internal.parser.*`: ProtoParser, ProtoFileElement, MessageElement, EnumElement/EnumConstantElement, FieldElement, OneOfElement, ReservedElement, RpcElement, ServiceElement, TypeElement (the parser element model; Kotlin `internal` is public in JVM bytecode, and the Java port must keep these public).
- `com.squareup.wire.schema.*`: Schema, SchemaException, Syntax, Location, ProtoFile, Type, Field, Service.

Consuming modules: `utils/protobuf-schema-utilities` (ProtobufFile, ProtobufSchema, ProtobufSchemaLoader, FileDescriptorUtils: converts Wire model to Google DescriptorProtos), `schema-util/protobuf` (canonicalizer, dereferencer, validators, compatibility checker, FQN conflict detector, structured content extractor, reference finder), `schema-validation/protobuf` (ProtobufValidator, ProtobufSchemaParser), `serdes/generic/serde-common-protobuf` (schema parser for generic serde), `app` (ccompat v7). Note this is a **schema-model** integration, not a wire-runtime message integration: Apicurio is unlocked by the port's wire-schema milestone alone, and wire-schema is the reason wire-runtime is in its tree.

### F6. Kafka constraints (HIGH for facts; adoption path inherently community-gated)

Kafka trunk (`apache/kafka`, 5.0 line): `minClientJavaVersion = 11`, `minNonClientJavaVersion = 17` (`build.gradle`). The only protobuf dependency is `com.google.protobuf:protobuf-java:3.25.5`, noted as "a dependency of opentelemetryProto"; there is no Kotlin anywhere in the dependency set and no Wire usage. Implications: (a) a library acceptable across Kafka client modules must build at Java 11 bytecode; (b) the current Wire dependency chain (kotlin-stdlib, kotlinpoet, okio, guava) is a non-starter upstream; a zero-dependency pure-Java artifact is the only realistic shape; (c) actual adoption in Apache Kafka goes through the community (KIP), so the port's job is to make the artifact KIPPable (Apache 2.0, clean provenance, zero deps, Java 11).

### F7. Prior art (MEDIUM; absence is hard to prove)

No existing pure-Java port or fork of Wire was found via web search and GitHub repository search (queries for wire/protobuf/pure-java ports). The pure-Java ancestor is Wire 2.x itself (last pure-Java release 2.3.0), which is too old to forward-port (proto3 support and the entire current schema/linker architecture postdate it) but is useful precedent that the runtime encoding layer was Java once. Adjacent pure-Java protobuf libraries (protobuf-javalite, protostuff, protobuf-dynamic) solve different problems and do not provide Wire's schema-model + linker, which is the part Apicurio depends on.

### F8. Performance (LOW; hypothesis only)

`wire-benchmarks` is a JMH module (AllTypesBenchmark, SimpleMessageBenchmark) usable as the port's comparison harness. The port translates the same algorithms (hand-rolled varint loops; the newer ProtoReader32/ByteArrayProtoReader32 fast paths), so parity is the expectation, but this is unverified and secondary per the stated goals. The gate should be "no regression beyond an agreed budget vs upstream wire-jvm on the ported JMH suite", measured, not assumed.

## Project Plan Analysis

### Goals and non-goals

Goals: (1) pure-Java (Java 11 bytecode) Wire artifacts with zero non-JDK runtime dependencies; (2) pass Wire's own JVM test suites and the protoc-compatibility oracle at a pinned upstream tag; (3) swap into Apicurio Registry with no functional change; (4) become KIPPable for Kafka. Non-goals (initial): Kotlin/Swift codegen, gRPC client, moshi adapter, Gradle plugin, editions support (upstream lacks it; diverging is out), JS/native/wasm targets.

### Recommended architecture decisions

- **Keep Java packages identical (`com.squareup.wire.*`)**. This lets upstream test sources run unmodified, keeps pre-generated code (from Wire 6/7 generators) binary-compatible with the ported runtime, and makes the Apicurio swap a dependency-coordinate change. Publish under a new Maven groupId, not `com.squareup.wire`, with clear "community port of square/wire at tag X" labeling.
- **Java 11 bytecode, build on JDK 17+** (`--release 11`). Covers Kafka client modules and Apicurio alike; forbids sealed/records in main sources.
- **Vendor the okio subset as `com.squareup.wire.internal.okio` (or an internal package)**, translated from okio's JVM sources, with okio's own Apache-2.0 JVM unit tests for the vendored classes ported as well; that is the only credible way to hold Buffer/ByteString semantics steady.
- **Defer poet usage out of the core artifact**: wire-schema ships without kotlinpoet/javapoet; profile/AdapterConstant codegen-touching pieces move to an optional `wire-java-generator` artifact (which may keep palantir javapoet, itself pure Java) together with the already-mostly-Java java generator and the compiler CLI.
- **Build tool: Maven**, matching Apicurio's consumption model and keeping the port buildable without any Kotlin plugin; Kotlin appears only in test compilation.
- **Parity harness as a first-class module**: a script/CI job that (a) re-extracts upstream test sources at the pinned tag, (b) runs them against the port jars, (c) diffs golden generated files, and (d) runs the protoc-compat suite. This is the instrument that turns "same test suite" into evidence.

### Phased milestones (with gates)

- **M0, Spikes and conventions (1 to 2 weeks).** Repo skeleton, decisions above recorded, translation conventions doc (exception-type parity, null handling, header preservation), okio-subset spike with ported okio tests, ProtoReader/ProtoWriter byte-for-byte roundtrip on the wire-tests corpus. Gate: spikes green.
- **M1, wire-runtime in Java (4 to 6 weeks).** Port commonMain+jvmMain (8.2k LOC): reader/writer, adapters, reflection (ProtoAdapter, RuntimeMessageAdapter, KotlinConstructorBuilder can be stubbed to the Java path), Message, well-known types, Internal. Gate: upstream wire-runtime JVM tests green, including the security regressions.
- **M2, wire-schema in Java (4 to 6 weeks).** Port commonMain+jvmMain minus poets (11.8k LOC), replace guava, replace okio FileSystem with java.nio.file. Gate: upstream wire-schema commonTest+jvmTest green; SchemaEncoder output identical to upstream on the test corpus.
- **M3, External oracle and codegen (2 to 3 weeks).** Port wire-protoc-compatibility-tests (protoc 4.36.1), wire-compiler CLI and wire-java-generator; golden corpus byte-identical. Gate: protoc suite green, goldens identical.
- **M4, Apicurio integration (1 to 2 weeks).** Branch of apicurio-registry swapping `wire-schema` 6.4.0 for the port artifact; run the protobuf-carrying module tests and a serde integration subset; produce a before/after dependency and jar-size report. Gate: Apicurio tests green on the branch.
- **M5, Performance, release, Kafka path (2 to 3 weeks).** JMH comparison vs upstream and protobuf-java with an agreed budget; publish 0.1.0 (Maven Central, sources/javadoc, NOTICE with Square/Google/JetBrains attribution); Kafka POC module plus a KIP-grade motivation document; upstream-tracking policy (pin 7.1.0, define re-sync procedure for future tags).

Total: roughly **4 to 6 months** of focused single-engineer effort, verification-dominated. Translation itself is heavily automatable; the schedule driver is the parity harness and the edge-case suites.

### Risk register

1. **okio semantic drift** (Buffer segment pooling, ByteString interning/utf8): mitigated by porting okio's own tests; residual risk MEDIUM.
2. **Exception-type and equality semantics drift** vs Kotlin originals: conventions plus verbatim upstream tests keep it visible; MEDIUM.
3. **Upstream drift**: Wire releases security fixes (two GHSAs in 2026); the port must define its sync cadence or it silently rots; process risk, MEDIUM.
4. **Editions gap**: both Wire and the port reject editions; proto-only (proto2/proto3) scope must be stated loudly to Apicurio/Kafka consumers; LOW for parity, HIGH as a market fact.
5. **Kotlin-in-tests**: acceptable (test scope), but if "no Kotlin at all, even at build time" is later required, test translation is a sizeable follow-up; flagged as decision, LOW now.
6. **Provenance for Apache donation**: AI-assisted translation provenance may need disclosure for a KIP; keep the methodology documented per-file; MEDIUM.
7. **wire-schema API includes `internal` packages** that Apicurio consumes: the port must keep them public and stable; LOW.

### Open decisions for the maintainer

1. Maven groupId/artifactId for publication (proposal: new group, e.g. `io.apicurio` or personal namespace initially; `wire-java` artifact names).
2. gson-support timing (Apicurio does not use it; recommend defer).
3. Whether M4 targets apicurio-registry master at wire 6.4.0 semantics or first bumps upstream to 7.x (the 6.4 to 7.0 delta is small; recommend porting 7.1.0 and testing Apicurio against it directly).
4. Single fat jar vs modular artifacts (recommend one core jar + optional generator jar).

## Confidence Assessment

HIGH: current Wire version and module inventory (clone), published dependency chains and jar sizes (Maven Central POMs and HEAD requests), Apicurio's exact Wire surface (source imports), Kafka Java baselines and protobuf status (trunk build files), absence of editions support (source), test-suite inventory and frameworks (source), absence of a conformance harness (repo grep). MEDIUM: effort estimates (sizing by LOC and suite counts, no trial translation yet), prior-art absence (searches negative, not proof). LOW/unverified: performance parity (hypothesis; JMH gate will decide), Wire's own minimum supported JVM (undocumented in the repo read; irrelevant to the port's Java 11 decision).

## Sources

1. square/wire clone at master (7.1.0), 2026-09-29: `CHANGELOG.md`, `gradle/libs.versions.toml`, `wire-runtime/build.gradle.kts`, `wire-schema/build.gradle.kts`, `wire-protoc-compatibility-tests/build.gradle.kts`, module sources and tests, `wire-schema/.../ProtoParser.kt:181`, `docs/wire_vs_protoc.md`.
2. Maven Central POMs/jars: `repo1.maven.org/maven2/com/squareup/wire/wire-runtime-jvm/7.1.0/`, `.../wire-schema-jvm/7.1.0/`, kotlin-stdlib 2.0.21, okio-jvm 3.18.2, guava 33.7.1-jre, protobuf-java 3.25.5 (Content-Length probes).
3. Apicurio Registry master: root `pom.xml` (wire-schema 6.4.0), `utils/protobuf-schema-utilities/.../ProtobufFile.java`, `FileDescriptorUtils.java`, `schema-validation/protobuf/.../ProtobufSchemaParser.java`, `serdes/generic/serde-common-protobuf/.../ProtobufSchemaParser.java` (GitHub code search + raw fetches).
4. Apache Kafka trunk: `build.gradle` (Java 11/17 split), `gradle/dependencies.gradle` (protobuf 3.25.5 via opentelemetryProto; no Kotlin).
5. Web searches (tavily) for prior pure-Java Wire ports; GitHub repository searches via `gh api`.
