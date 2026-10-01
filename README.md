# antiwire

A community port of [Square Wire](https://github.com/square/wire) to pure Java, with no Kotlin dependency. Execution started: milestone M0 (spikes and conventions) is in progress on this repository. The research, plan, and backlog below are complete; feedback on them is still welcome while the port is built.

## The original request

Verbatim from the project initiator:

> I would like to start a porting of https://square.github.io/wire/ project to pure java, to avoid depending on kotlin. The goal is to start using it in the projects apicurio-registry and apache-kafka. We would like to keep the footprint minimal and it's of utttermost importance to pass the same test suite that the project passes, including an internal or an external one. Performance are another concern, but functional compatibility is the main one. Prepare an analysis for a project plan and backlog.

## The idea in short

Wire 7.1.0 is a Kotlin Multiplatform project. Its JVM artifacts pull kotlin-stdlib (1.7 MB), okio (0.4 MB, itself Kotlin), kotlinpoet, and guava into every consumer. [Apicurio Registry](https://github.com/Apicurio/apicurio-registry) consumes exactly one Wire artifact, `wire-schema`, for schema parsing and its Schema model, pinned at 6.4.0. Apache Kafka has no Wire dependency today and its dependency policy makes the current Kotlin chain a non-starter. The port would deliver the JVM-relevant slice of `wire-runtime` (about 8.2k lines) and `wire-schema` (about 11.8k lines) as pure Java at Java 11 bytecode, with zero dependencies beyond the JDK. It must pass Wire's own test suites plus its protoc-compatibility suite at a pinned upstream tag. Full evidence and reasoning are in the [research report](docs/research-wire-java-port-2026-09-29.md).

## What is in this repository

- A Maven build (`io.apicurio:antiwire` parent, `wire-runtime-java` and `wire-schema-java` modules, bytecode target Java 11, CI on Temurin 17) with the zero-dependency rule enforced by the Maven enforcer plugin. The reactor also contains `wire-upstream-shaded`, a never-published test fixture holding a relocated copy of upstream wire-runtime-jvm 7.1.0 used by the parity tests as a live oracle. The canonical build command is `mvn verify` (plain `mvn test` fails: the parity fixture is packaged at the package phase, before which the parity classes do not exist).
- `docs/research-wire-java-port-2026-09-29.md`: the full research report (exhaustive depth, confidence-rated, with sources). It covers the current Wire module inventory, the verified dependency chains, the exact Wire APIs Apicurio imports, Kafka constraints, prior art, test-suite strategy, the phased project plan with gates, the risk register, and the open decisions.
- `docs/decisions.md` and `docs/translation-conventions.md`: the adopted architecture decisions (D1 to D8) and the parity contract used by every translation.
- `backlog/`: the execution backlog in [Backlog.md](https://backlog.md) format. Six milestones (M0 spikes through M5 release), 23 tasks, each with acceptance criteria and dependencies. The same-test-suite requirement becomes concrete gates: upstream test sources adopted verbatim (TASK-9, TASK-13, TASK-15) and a pinned-tag parity harness in CI (TASK-14).

## Where we want a second opinion

1. Scope: we plan to exclude the Kotlin and Swift code generators, the gRPC client (okhttp plus coroutines), the moshi adapter, the Gradle plugin, and editions support (upstream Wire itself rejects editions today). Is that the right scope for the Apicurio and Kafka use cases?
2. okio strategy: Wire's public API embeds okio types (ByteString, BufferedSource, BufferedSink), so we plan to vendor a minimal pure-Java okio subset inside the library rather than rewrite I/O on java.nio. Is that sound?
3. Java baseline: Java 11 bytecode so the artifact is usable across Kafka client modules; Java 17 would be simpler. Right call?
4. Naming: keep the `com.squareup.wire.*` Java packages for drop-in compatibility and test-suite reuse, but publish under a new Maven groupId. Is reusing Square's package namespace acceptable in practice?
5. Parity harness: keep the upstream Kotlin test sources verbatim as our test scope (Kotlin as a dev-time only dependency), rather than translating tests to Java. Is that an acceptable trade?
6. Effort: we estimate 4 to 6 months of focused single-engineer work, verification-dominated. Does that match anyone's experience with ports of this size?
7. Alternatives: should this be a fork at all, or would a pure-Java module contributed upstream to square/wire, or a different approach entirely, serve the Apicurio and Kafka goals better?

Feedback welcome as GitHub issues on this repository, or however you prefer to reach the maintainer.

## License

The research and backlog are published under Apache 2.0. The port, when written, will be Apache 2.0 like upstream Wire, preserving Square, Google, and JetBrains attribution where upstream files carry their headers.
