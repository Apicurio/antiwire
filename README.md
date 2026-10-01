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

Status first, so the questions land in context. Milestone M0 (the spikes that prove feasibility) is complete: the okio buffer layer is vendored in pure Java and passes okio's own 732-test suite, the encoding core (ProtoReader and ProtoWriter) is translated and proven byte-for-byte and exception-for-exception against a live relocated copy of upstream wire-runtime-jvm 7.1.0, and the whole build is at 747 tests, 0 failures. So "is it feasible" is answered; the questions below are about direction and boundaries.

Already settled (docs/decisions.md, with the evidence that settled them):

- okio route: vendor a pure-Java subset (based on okio 1.17.6, the last Java-era release) inside the artifact rather than depending on okio-jvm or rewriting I/O from scratch. Settled by the M0 spike (TASK-4).
- Namespaces: keep `com.squareup.wire.*` Java package names so upstream test sources and generated code stay compatible, publish under a new Maven groupId. Settled by the M0 spike (TASK-5, the parity harness depends on it).
- Module boundaries: runtime and schema stay independently consumable artifacts; code generation (with javapoet) is an optional artifact. Settled in D6.
- Functional port over signature freeze: JDK types in the consumer-facing API are a first-class deliverable; upstream-shaped signatures are kept only where they accelerate the test milestones (D5a).

Still open, with named owners:

- The public API compatibility matrix: exactly which surfaces stay upstream-shaped (porting-phase layer) and which get JDK types (consumer layer) is designed in TASK-25, before the Apicurio integration (TASK-18). Apicurio input on the loading API (java.nio.file.Path, classpath helper, no more FakeFileSystem dance) is cheapest to incorporate now.
- Golden-exactness: generated Java code must be byte-identical to upstream's golden files (TASK-16); we have not yet learned whether formatting or codegen drift makes that goal need adjustment.
- Parser fidelity details such as reserved-range handling in the element model are owned by TASK-10; upstream test material is the judge.
- Performance thresholds (regression budget versus upstream wire and protobuf-java) are owned by TASK-20, decided after the JMH baselines exist, not before.
- The fork-versus-upstream question: should a pure-Java variant exist as a fork at all, or would Square entertain Java modules upstream? Genuinely open; it shapes how we handle re-syncs.

The 4-to-6-month estimate stays provisional. M0 finished fast (three working days of agent-driven execution), which suggests the estimate is conservative for the mechanical translation, but M1 and M2 are where the verification-dominated bulk sits, and no code of wire-schema itself exists yet.

Feedback welcome as GitHub issues on this repository, or however you prefer to reach the maintainer.

## License

The research and backlog are published under Apache 2.0. The port, when written, will be Apache 2.0 like upstream Wire, preserving Square, Google, and JetBrains attribution where upstream files carry their headers.
