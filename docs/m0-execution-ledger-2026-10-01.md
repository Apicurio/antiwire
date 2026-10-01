# M0 execution ledger (2026-10-01)

Reconciliation record for the merge of the m0-spikes execution branch into the reviewed
planning baseline. It maps what the execution branch actually built onto the decision
record (DEC-x), the open technical decisions (OPEN-x), and the re-scoped task acceptance
criteria, and it states what remains open. It also supplies the measured effort that
TASK-4 AC#5 requires.

## What the execution branch built (all evidence from mvn clean verify runs)

- Vendored okio buffer layer: 24 classes copied 1:1 from okio 1.17.6 (Apache 2.0) in the
  original `okio` package inside wire-runtime-java, jsr305 and Animal Sniffer annotations
  stripped (the strips are recorded in the vendoring notes), gzip and zlib family omitted
  after import inventory. okio's own 1.17.6 JVM tests vendored verbatim: 732 run, 0
  failures, 4 upstream skips. A high-effort code review verified the vendored files
  byte-identical to the 1.17.6 sources except the recorded strips; this closes the "source
  purity unverified" caveat of OPEN-1 for the buffer layer.
- Encoding core: ProtoReader, ProtoWriter, FieldEncoding, Syntax, internal.ProtocolException
  translated from wire 7.1.0 with attribution headers preserved. Parity is enforced by a
  live oracle: the wire-upstream-shaded test fixture relocates the pinned upstream
  wire-runtime-jvm 7.1.0 jar into a private package, so both implementations run side by
  side. 15 parity tests: 500 seeded writer sequences byte-for-byte, 200 structured messages
  with identical read transcripts, packed replay, empty-packed pop, nested group skip,
  unterminated group EOF, 101-deep recursion limit, six malformed-input vectors with exact
  exception-message parity, including GHSA-7xpr-hc2w-34m9 on both paths. Total suite
  747 tests, 0 failures.
- Deferred members recorded in code: rawProtoAdapter, readUnknownField, addUnknownField,
  forEachTag await the ProtoAdapter port (TASK-6). EOFException maps to java.io (upstream
  typealias), ProtocolException extends java.net.ProtocolException.

## Mapping to the decision record

- OPEN-1 (I/O route): the translated-okio-subset route is demonstrated for the buffer
  layer, under original package names. The namespace consequence upstream wanted recorded:
  upstream test sources import `okio.*` untouched, generated code stays source-compatible,
  and no okio artifact ships. REMAINING: the loading surface (FileSystem, Path, FileHandle,
  in-memory sources, classpath resources, ZIP) is inventoried as required but NOT yet
  implemented or demonstrated; it stays open inside TASK-4/TASK-12.
- OPEN-2 (publication grouping): provisional grouping exercised as runtime + schema + the
  never-published parity fixture; the reviewed skeleton adds the optional generator module.
  Finalization remains open (TASK-4 AC#6).
- DEC-4: the execution branch's earlier zero-dependency enforcer is superseded by the
  reviewed policy (no Kotlin in production; reviewed pure-Java dependencies allowed). The
  build adopts the reviewed enforcer and its verify.sh suites; the separately-built
  dependency-list belt is dropped as redundant.
- DEC-8: coordinates switch to the provisional io.github.paoloantinori namespace
  everywhere, including the parity fixture's relocation packages; nothing may be published.
- DEC-12: NOTICE carries Square, Google (BSD-style notices for the ProtoReader family and
  ProtoWriter body notice), JetBrains, and separate Apache-2.0 attribution for the vendored
  okio code.

## Task status after the merge

- TASK-1, TASK-2, TASK-3: executed on main by the planning line; the execution branch's
  parallel artifacts (its own decisions/conventions text, skeleton, CI) are superseded and
  dropped in the merge where they conflict; unique evidence (conventions content already
  folded, gate negative-battery lessons) is preserved in the commit history.
- TASK-4: In Progress. Done: AC#3 (okio's own tests run verbatim), the buffer half of AC#1
  and AC#2, the namespace recording of AC#4 (this ledger plus decisions). Remaining: the
  loading-API inventory map and the in-memory/filesystem/classpath/ZIP demonstration, the
  full AC#4 decisions text, AC#5 recording (this document supplies the effort data), AC#6
  grouping finalization.
- TASK-5: In Progress. Done: AC#1 (byte parity including groups, packed, malformed
  lengths, via the isolated-oracle fixture); the runtime half of AC#2 (upstream
  ProtoWriterTest.kt compiles and runs against the Java slice with two individually
  ledgered adaptations, in wire-runtime-java/src/test/kotlin with its adaptation ledger);
  AC#4 (config/class-origins.txt now lists the real port classes, and the entry point
  proves their single origin; the consumer exercises the spike); AC#6 (executable
  null/exception boundary examples, BoundaryExamplesTest, run by the build suite);
  AC#7 (the shared entry point's Java 11 consumer now exercises the real spike surface,
  ProtoWriter over the vendored Buffer with deterministic bytes, on Temurin 11).
  Remaining: the parser-model half of AC#2 (waits for the TASK-10 port), AC#3 (pinned
  upstream fixture generation), AC#5 (6.4.0 vs 7.1.0 baseline corpus and go/no-go
  record).

## Measured effort (TASK-4 AC#5)

Executed between 2026-10-01 07:00 and 11:30 UTC by one agent-driven session on the
execution branch: okio vendoring plus its 732-test suite green in about 40 minutes; the
encoding-core translation (902 LOC of upstream Kotlin across five files) plus the 15-test
parity harness in about two hours including three review rounds (two /simplify passes with
four agents each, two high-effort code reviews) and the CI-gate negative batteries. No jar
size or performance promise is derived from these numbers; they only refine the schedule
estimate for the mechanical translation portion of M1 and M2.
