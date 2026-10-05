# antiwire status update, 2026-10-05

For context: antiwire is a pure-Java port of Square Wire (Square's protobuf library), with no Kotlin anywhere, built for Apicurio Registry first and Kafka later. The original is written in Kotlin and drags kotlin-stdlib, okio, and other heavy dependencies into every consumer.

## What exists on main today (110 commits)

The library works and is complete: runtime, .proto parser, schema linker, loader (files, directories, zip/jar archives), command-line compiler, and the Java code generator. All of it on Java 11.

### Footprint vs upstream

| Surface | Upstream 6.4.0 | Upstream 7.1.0 | antiwire | Reduction |
|---|---|---|---|---|
| Runtime jars | 4 jars, 2.30 MiB | 4 jars, 2.33 MiB | **1 jar, 0.24 MiB** | 90% |
| Schema jars | 15 jars, 9.33 MiB | 14 jars, 9.70 MiB | **2 jars, 0.55 MiB** | 94% |
| Optional generator | 15 jars, 10.2 MiB | 15 jars, 10.2 MiB | **4 jars, 0.70 MiB** | 93% |
| Apicurio marginal | 10.5 MiB of jars removed by the migration (Kotlin, okio, kotlinpoet, guava), nothing comparable added | | | |

### Speed vs upstream 7.1.0

| Operation (the Apicurio path) | Result |
|---|---|
| Parse .proto files | 1.12x faster |
| Link + load schemas | 1.08x faster |
| Descriptor conversion | 1.16x faster, 22% fewer allocations |
| Message encode (8 workloads) | Parity or better; best cell 2.2x faster (UTF-8 fast path) |
| Message decode (8 workloads) | Parity or better; bytes fields now zero-extra-copy |

### Evidence

| Proof | Scale |
|---|---|
| Upstream test suite adopted, reconciled case by case against the pinned 7.1.0 sources | 967 cases |
| Interoperability tests against protoc 4.36.1 (Google's compiler), byte-level | 122 cases |
| Security regression tests covering the library's historical advisories | 24 cases |
| Generated code byte-identical to the original compiler | 13 reference files |
| CI suites, all green on every push (tests, parity reconciliation, bytecode, classpath, upstream watch) | 12 suites |

An automated check verifies at every build that no upstream test was lost, no expected value was edited, and nothing was forgotten. The build also enforces the zero-Kotlin rule in production scope on every run.

### Apicurio integration

| Aspect | State |
|---|---|
| Migration surface | 23 files across 4 modules, on a local branch (never pushed, by design) |
| Tests | Same counts as the pre-migration baseline, zero failures |
| Descriptors produced | Byte-identical to the wire 6.4.0 baseline, 20/20 corpus files |
| Dependency graph | Zero Kotlin, okio, or guava attributable to the migration |

### Maintenance and API

| Aspect | State |
|---|---|
| Upstream release tracking | Documented procedure plus an automated tag watch, rehearsed on a real advisory |
| Public API | No okio types in public signatures; our own `Bytes` type end-to-end, old forms kept as deprecated bridges so original-compiler output still compiles |

## What is still open

| Item | Status | Blocking on |
|---|---|---|
| [0.1.0 release](https://github.com/Apicurio/antiwire/issues/3) | All measurements signed off; release build ready | Maintainer's final go, and who uploads the artifacts. Deferred as not urgent |
| [Kafka adoption](https://github.com/Apicurio/antiwire/issues) (TASK-22) | Proposal C1 written up, waiting | Deferred by the maintainer |
| [`Bytes` name-collision bug](https://github.com/Apicurio/antiwire/issues/2) | Documented, two fix directions written | No urgency; upstream shares this class of problem |
| Apicurio PR | Branch ready locally | Maintainer's call on whether and when |

In short: the technical work is done and verified. What remains are three maintainer decisions (publish, Kafka, PR) and one documented bug nobody is forced to rush on.
