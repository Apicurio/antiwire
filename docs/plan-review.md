# Plan review disposition ledger

Date: 2026-09-29. This is the durable record of the external review of the antiwire plan and of how every review item was disposed. The reviewed state is commit `32971455274637fe95ba292ff527d30508fc8cb6` (working tree of that morning). Review evidence bases, all pinned: square/wire at tag 7.1.0 (annotated tag object `da24c33ee1fe772a7a04617018087f46f26d1708`, commit `9f62097dfe4995b5709d001ca0187e30ca0530ef`), Maven Central POMs and jar sizes retrieved 2026-09-29, a local apicurio-registry clone at commit `48e2742acc68fae456fe252cc8b08243e376ebfc` (2026-09-24, carrying unrelated local modifications), apache/kafka trunk build files retrieved 2026-09-29, and a mechanical recomputation of the backlog dependency graph. Single-source figures are attributed, not restated as fact.

## How to read this ledger

Status vocabulary:

- **APPLIED**: the planning documents were corrected in this reconciliation (README, research report, `decisions.md`, this file).
- **ENCODED**: the backlog task text now carries the correction or the owner (task files TASK-1 through TASK-24); the work itself is still to be done.
- **DECIDED**: the maintainer decided it; recorded in `decisions.md` (cited as DEC-n).
- **OPEN**: a genuine open decision with a named owner and milestone.
- **NOTED**: recorded as an observation; no correction required.
- **SUPERSEDED / REJECTED**: the review's original framing was replaced or its proposal declined.

Nothing below implements a feature. TASK-1 through TASK-23 remain To Do. TASK-24 tracks this planning reconciliation separately; its task record carries verification and completion status. "ENCODED" names where the future work will happen, never that it happened.

## Cross-source reconciliations

| ID | Disagreement | Resolution |
|---|---|---|
| X1 | Two reviews cited different hashes for Wire 7.1.0 | RESOLVED. Both were correct: `da24c33...` is the annotated tag object, `9f62097d...` the commit it points at. Both are pinned in DEC-1 and the research report. |
| X2 | Whether the 2026 GHSAs were Swift-only | RESOLVED. GHSA-9rm7-3qhh-h2mc is JVM-relevant (with GHSA-7xpr-hc2w-34m9); three further 2026 advisories are Swift-scoped. Corpus and wording corrected (TASK-9 AC#4, TASK-17, research F4). No advisory-count claim is treated as exhaustive, since JVM-relevant fixes also ship without advisory ids. |
| X3 | Byte-identical Apicurio output before and after the swap | REJECTED as written and REPLACED: first record upstream 6.4.0 versus upstream 7.1.0 differences, then require the port to match upstream 7.1.0, with every Apicurio-visible difference explicitly accepted. ENCODED in TASK-5 AC#5 (baseline captured with upstream artifacts, not an unbuilt port) and TASK-18 AC#4. |
| X4 | License attribution labels | RESOLVED by direct reads at the pinned tag. Five files carry BSD-style notices (ProtoReader family, MathMethods, and ProtoWriter, whose notice sits beneath a Square Apache-2.0 header). Notices are retained per file, bodies included. DEC-12; research F3; TASK-3 AC#5; TASK-21 AC#5. |

## Plan slice (execution-plan correctness)

| ID | Observation (abridged) | Disposition |
|---|---|---|
| F1, F2 | TASK-7 and TASK-8 could be declared done before TASK-6 exists; their acceptance criteria consume TASK-6 classes | APPLIED and ENCODED. TASK-6 is now the compiling runtime foundation including the Message, well-known-type, and Internal implementations the slice needs, with a symbol-ownership map; TASK-7 and TASK-8 each depend on TASK-6 and extend it without duplicate ownership. This supersedes the review's earlier "add a dependency edge" prescription, which would have misrepresented a cycle-shaped coupling inside one source set as layering. |
| F3 | Null-handling parity absent from the conventions task | ENCODED. TASK-3 AC#1 covers nullable and non-null parameters and results, null-check placement, and exception types; the executable reference-versus-port examples (require to IllegalArgumentException, checkNotNull to IllegalStateException) run in the shared verification entry point under TASK-5 AC#6 (see R9). |
| F4 | Open product decisions had no owner | APPLIED. All settled decisions are in `decisions.md`; the remaining technical decisions have named owners (OPEN-1 through OPEN-4 and the later-milestone list). ENCODED in TASK-1 AC#1. |
| F5 | The parity harness covered only two of four promised functions; golden and protoc checks were not wired into CI | ENCODED. TASK-2 creates the shared verification entry point; TASK-14 completes it with coverage enforcement and an extension contract; TASK-15 AC#4 and TASK-16 AC#4 add their suites as blocking jobs. Partial M2 coverage is never reported as full release parity (TASK-14 AC#3). |
| F6 | Generator-module ownership inverted: the move target did not exist when the move was scheduled | ENCODED. TASK-2 creates runtime, schema, and optional-generator shells; TASK-12 moves Java-profile support there without a KotlinPoet production dependency; TASK-16 completes and verifies it, owning the deferred profile tests. |
| F7 | Report directory naming inconsistency (`claudedocs/`) | APPLIED. `docs/` is the single documentation root; TASK-19 AC#1 writes `docs/footprint.md`. |
| F8 | The publish task's closure excluded the benchmark task, so publishing could precede measurement | APPLIED and ENCODED. TASK-21 now depends on TASK-19, TASK-20, and TASK-23; DEC-9 and DEC-13 make measured runtime and Apicurio schema performance release prerequisites. |
| F9 | Two M0 tasks were skippable prerequisites outside every closure | APPLIED. TASK-2 and TASK-3 each depend on TASK-1, TASK-4 requires both, TASK-5 requires TASK-4, and TASK-6 requires TASK-5, so no downstream task can start without the skeleton, runner, and conventions. |
| F10 | The encoding spike could prove parity on a stub substrate | APPLIED. TASK-5 depends on TASK-4 and uses its selected I/O approach. |
| F11 | One task lacked a description section | ENCODED. TASK-2's description now states the build/test tooling split and the isolation requirement. |
| F12 | The strongest Kafka guarantee sat in a description, not an acceptance criterion | ENCODED. TASK-22 AC#3 records the resolved classpath with no Kotlin or Kotlin-backed dependencies. |
| F13 | Per-file provenance methodology had no criterion | ENCODED. TASK-3 AC#5 (provenance policy) and TASK-22 AC#4 (AI-assisted translation methodology disclosure). |
| F14 | The blanket promise to adopt compiler and generator tests was unverified against upstream | RESOLVED with scope precision and ENCODED. Upstream `wire-java-generator` has no standalone suite; the compiler suite mixes Java-target cases with excluded features. TASK-16 AC#1 and AC#3 retain or port the Java-target cases, exclude only excluded-feature cases, and add compensating cases beyond the single-file Java golden corpus. |
| F15 | Redundant direct dependency edges | APPLIED and NOTED. The dependency rewrite intentionally simplified the graph and removed the previously redundant direct edges (TASK-13 to TASK-11, TASK-15 to TASK-6, TASK-16 to TASK-12). Redundant direct edges that remain, such as TASK-9 to TASK-6 and TASK-11 to TASK-8, are deliberate gate emphasis. |
| F16 | M1 gate named one security regression while the plan said "regressions" | ENCODED. TASK-9 AC#4 names both JVM-relevant advisories; TASK-17 carries the labeled corpus. |
| F17 | Footprint was measured but never gated | DECIDED. DEC-10 and DEC-9: measurement with maintainer acceptance before release (TASK-19 AC#3, TASK-20 AC#3); no threshold invented in advance. |
| F18 | Dependency-policy verification stopped at skeleton time | ENCODED. TASK-21 AC#4 rechecks published POMs and resolved consumer classpaths at release. |
| F19 | The sync task's criterion was unfinishable before a future upstream release | APPLIED and ENCODED. TASK-23 AC#2 replays an already-published applicable change that includes at least one security fix, recording detection, applicability triage, owner routing with simulated escalation, source diff, adaptation, regression detection, and a full parity rerun on isolated classpaths; AC#4 makes the next actual release future maintenance, not a prerequisite, with no real incident notifications; TASK-21 depends on TASK-23. DEC-11. |
| Q1 | Performance budget and release policy | DECIDED. DEC-9: measured benchmarks gate the first release; thresholds post-baseline; regressions need explicit acceptance. |
| Q2 | Harness CI scope | APPLIED. Full runner with extension contract; see F5. |
| Q3 | Footprint threshold value | DECIDED. No arbitrary numeric threshold is mandated: DEC-10 requires measurement with explicit maintainer acceptance of the measured footprint before release (TASK-19 AC#3). Performance thresholds, a separate question, are set only after baseline measurement by TASK-20. |
| Q4 | Same-suite boundary for compiler and generator tests | RESOLVED. See F14 and B5. |
| Q5 | Open decisions 3 and 4 (Apicurio semantics target, packaging) | DECIDED. DEC-1 (7.1.0 semantics with delta-aware acceptance) and OPEN-2 (layout recorded as an M0 result; provisional shells in TASK-2). |
| Q6 | Report location | APPLIED. `docs/`. |
| Q7 | Whether the sync policy must precede the first release | DECIDED. DEC-11 and DEC-13: yes, demonstrated before release. |

## Architecture slice

| ID | Observation (abridged) | Disposition |
|---|---|---|
| A1 | Three mutually incompatible okio strategies were specified simultaneously; the forcing constraint was weakened by the single-file Java golden corpus | APPLIED. The strategy conflict is removed from the documents; the route is an M0-owned evaluation (OPEN-1) over the full inventory, with consequences for tests, generated code, and Apicurio migration recorded with the choice. ENCODED in TASK-4; validated by TASK-5. |
| A2 | The zero-modification test clause conflicted with the poet deferral and with Kotlin language reality | APPLIED and ENCODED. DEC-5 defines the adapted-test model; TASK-3 is the rulebook; TASK-13 accounts for every case; Profile and AdapterConstant support and their tests move to the generator module (TASK-12, TASK-16). |
| A3 | The Apicurio swap is not coordinate-only; FakeFileSystem needs a decision | DECIDED as scope (DEC-2) and OPEN as mechanism: patch Apicurio's usage or vendor a Java equivalent, decided at integration; owners TASK-12 (boundary) and TASK-18 (site handling). |
| A4 | The compatibility contract was never written as one artifact; upstream `.api` dumps were unused | APPLIED. DEC-2 states the tiers; TASK-1 AC#2 builds the matrix using the dumps as a boundary checklist rather than a full-ABI gate. |
| A5 | The 6.4.0-to-7.1.0 semantics delta was ungated | SUPERSEDED by X3 and ENCODED there (TASK-5, TASK-18). |
| A6 | No spike proved Maven can compile the upstream Kotlin test sources | ENCODED, now uncontingent. TASK-5 AC#2 and AC#3 compile representative adapted tests and pinned fixtures; TASK-2 AC#4 provides the toolchain. |
| A7 | The no-Kotlin invariant was unenforced for the runtime module | ENCODED. TASK-2 AC#3 (build-level rejection of Kotlin and Kotlin-backed transitives), TASK-6 AC#5, TASK-12 AC#3, and the release-time recheck in TASK-21 AC#4. |
| A8 | Reflection work gated the Apicurio milestone | DECIDED. The full runtime suite including reflection stays in scope (no assumed cut); sequencing is now explicit: TASK-18 follows TASK-17, which closes the required-case accounting. The option to split the harness was not adopted; the single shared runner is the conscious choice. |
| A9 | Packaging narrative was inconsistent across documents | APPLIED. OPEN-2 records the layout as an M0 result with the duplicate-class rules; TASK-1 AC#3 and TASK-2's provisional shells align the documents. |
| A10 | The namespace decision spans two Square projects (wire and okio) | APPLIED, with the two halves separated. The I/O and public package-name choice is an M0 technical decision (OPEN-1, TASK-4), not gated on organization authorization; what waits for authorization is the publication namespace (`io.apicurio`, DEC-8), while attribution obligations apply regardless (DEC-12). Neither blocks starting M0. |
| A11 | Sync burden lacked a lag bound; bus factor unstated | NOTED and ENCODED. TASK-23 AC#1 records maintenance owners, triage ownership, response expectations, and escalation; the supported upstream range and lag bound are recorded there when the policy is written. |
| A12 | The largest unestimated component was the okio subset; no trial translation existed | ENCODED. TASK-4 AC#5 and TASK-5 AC#5 output measured scope, verification effort, and remaining uncertainty; the estimate is re-examined at M0 (research report, estimate status). |
| A13 | Pull harness scaffolding and an Apicurio oracle capture into M0 | ENCODED. TASK-2 creates the early runner; TASK-5 AC#5 captures the upstream 6.4.0 versus 7.1.0 baseline using upstream artifacts. The main translation order stays runtime-before-schema, as the review itself concluded. |
| A14 | The 6/7/8 split was a work partition, not a compile partition | APPLIED and ENCODED. TASK-6 owns the compiling foundation with the required implementations; no cross edges were added between 6, 7, and 8 beyond their common root, matching the review's own correction. |
| Arch Q1 | okio route | OPEN. TASK-4 (OPEN-1). |
| Arch Q2 | FakeFileSystem handling | OPEN. TASK-18 (with TASK-12's boundary). |
| Arch Q3 | Test-scope definition | DECIDED. DEC-5. |
| Arch Q4 | Scope cut of runtime tests or JMH under Apicurio-first | DECIDED. No assumed cut: full runtime tests stay; performance gates the release (DEC-9). |
| Arch Q5 | Estimate envelope | OPEN. Re-examined at M0 with measured scope. |

## Consumer slice (Apicurio and Kafka)

| ID | Observation (abridged) | Disposition |
|---|---|---|
| F-1 | The swap is not coordinate-only: verified okio and Kotlin usage in four Apicurio files plus the module POM | APPLIED and ENCODED. Limited migration approved as scope (DEC-2); TASK-18 re-inventories at a recorded base commit, documents every changed caller, and does not treat the four-file list as exhaustive. The review's unsupported effort estimate for that migration was rejected; none is recorded. |
| F-2 | Reserved ranges need a public, documented range type | ENCODED. TASK-10 AC#3: documented public Java representation with endpoint and maximum-value semantics tested, and the consumer migration contract recorded for TASK-18. |
| F-3 | Attribution mislabeled BSD-style notices as Apache-2.0 and omitted ProtoWriter and vendored okio | APPLIED. DEC-12; research F3; ENCODED in TASK-3 AC#5 and TASK-21 AC#5. |
| F-4 | Behavioral delta ungated; before/after framing wrong under a 7.1.0 target | SUPERSEDED by X3 and ENCODED there. |
| F-5 | Security lists undercounted; changelog watch needed | APPLIED and ENCODED. GHSA-9rm7 enters the corpus (TASK-9 AC#4, TASK-17); TASK-17 AC#2 verifies attribution against pinned source; TASK-23 AC#3 watches tags and non-advisory changes. |
| F-6 | The Apicurio surface census missed SchemaLoader, the okio imports, IntRange, and two importers | APPLIED. Research F5 rewritten with the observed-minimum caveat and the pin of the audited clone. |
| F-7 | The Kafka use case is undefined | NOTED and ENCODED. Deferred by DEC-7; TASK-22 AC#1 requires a maintainer-approved scenario and acceptance criteria before any POC; AC#4 forbids claiming community approval or an unverified written policy. |
| Consumer D3 | Keep the zero-dependency requirement | SUPERSEDED. DEC-4 allows reviewed pure-Java dependencies with measured footprint; no-Kotlin remains the hard rule. |
| Consumer Q1 | Apicurio adaptation direction | DECIDED. DEC-2 (scope approval; implementation still gated). |
| Consumer Q2 | Publication groupId | DECIDED. DEC-8: `io.apicurio` namespace authorized by the maintainer (2026-10-02, reconfirmed 2026-10-06); publication itself stays gated by TASK-21 AC#1. |
| Consumer Q3 | Comparison design | SUPERSEDED by X3. |

## Upstream slice

| ID | Observation (abridged) | Disposition |
|---|---|---|
| B1 | "Verbatim, zero modifications" is impossible against Java production: named arguments, omitted defaults, member extensions, package-level internals | APPLIED and ENCODED. DEC-5; TASK-3 AC#4 (rulebook and case mapping covering fixtures, setup, helpers, expected values, error paths); TASK-9 AC#3 and TASK-13 AC#3 (reviewed, reproducible adaptation records). The demonstrably forced adaptations are named-argument call forms and package-level imports; omitted-default call sites are handled per site, since Java overload bridges can serve some of them, and hand-written `copy()`/`componentN()` bridges are possible where needed. The blanket "copy and default methods are impossible" claim is therefore not repeated. |
| B2 | The duplicate-singular-message merge is a 7.0.0-alpha05 change, not 6.4.x | APPLIED. Research F4 corrected; ENCODED in TASK-17 AC#2 (source-linked attribution) and the delta-aware gates. |
| B3 | The vendoring inventory and the gzip question were wrong | APPLIED and ENCODED. Full inventory in research F3 and OPEN-1; ZIP, not gzip; TASK-4 rewritten accordingly. |
| B4 | Public okio relocation versus compatibility claims | APPLIED. DEC-2 withdraws the precompiled-ABI promise and states the source-compatibility consequence; the census in research F5 supersedes the single-file check; mechanism pending OPEN-1. |
| B5 | Compiler-suite scope and the thin Java golden corpus | APPLIED and ENCODED. See F14: Java-target cases retained, single golden file recorded, compensating cases added; TASK-15 AC#2 triages the oracle suite's dev-time dependencies per case. |
| B6 | The 7.1.0 JSON null-element fix lives in excluded adapters | APPLIED and ENCODED. DEC-6; TASK-17 records it as out of scope with a reason, not silently dropped. |
| B7 | GHSA inventory and footprint framing corrections | APPLIED. See X2 and DEC-10. |
| Upstream Q1 | Test translation mandate | DECIDED. None: Kotlin stays in test scope (DEC-4, DEC-5). |
| Upstream Q2 | okio route | OPEN. TASK-4. |
| Upstream Q3 | Adapter tests | DECIDED. Excluded with the JSON adapters and the gRPC reflection product `wire-reflector` with its grpcurl interop suite (DEC-6); the runtime's JVM reflection machinery is not gRPC reflection and remains mandatory under DEC-5. |
| Upstream Q4 | Semantics target | DECIDED. DEC-1; the delta gate remains (X3). |

## Other review rows

Rows R4 through R9 come from the required code review of the reconciled planning state (main session, after the external review); the external-review dispositions do not cover them, and no prior approval is claimed for that state.

| ID | Item | Disposition |
|---|---|---|
| RG | Release-gate closure verification | ADOPTED. TASK-21 depends on TASK-19, TASK-20, TASK-23; DEC-13 records the prerequisites and that the Kafka task sits outside the release closure. |
| C1 | okio types confirmed in Wire 6.4.0 public signatures (javap) | ADOPTED as evidence for DEC-2 and OPEN-1; the bridge mechanism is decided by TASK-4. |
| License skeptic | Per-file notice audit must scan file bodies | ADOPTED. DEC-12; ProtoWriter's embedded notice verified by direct read at the pinned tag; TASK-21 AC#5 audits headers and in-file notices. |
| D1, D2 | Compatibility tiers; Apicurio-first | ADOPTED. DEC-2, DEC-7. |
| Final user answers | Test scope, dependency policy, performance, groupId, sync prerequisite | ADOPTED. DEC-4, DEC-5, DEC-8, DEC-9, DEC-11. |
| Fabrication correction | An earlier relayed framing of the dependency decision as "no Kotlin anywhere" misread the maintainer's decision | RECORDED. The corrected interpretation is DEC-4: the no-Kotlin restriction covers production scope only, with test and build tooling excluded from it. No document restates the misreading, and the question is not re-asked. |
| Org-transfer guidance | Transfer on plan definition; adoption requires evidence | ADOPTED. DEC-8. |
| Silent-drop caution | Wrong range types fail loudly in FileDescriptorUtils; the one silent path is ProtobufFile's final else-branch | ADOPTED. Recorded in research F5 and covered by TASK-18 AC#3 (range endpoints exercised) and AC#4 (delta-aware corpus). |
| AC-count correction | A per-task acceptance-criteria recount was not relayed | NOTED. Only "no task lacks acceptance criteria" was verified and used; no count-dependent planning claim rests on the unrelayed recount. |
| R4 | The mandatory runtime test scope missed executable behavior tests hosted outside the wire-runtime module, in the shared wire-tests JVM suites | CONFIRMED and ENCODED. Direct reads at the pinned commit show UnknownFieldsTest (encode/decode with unknown-field retention and equality/hashCode assertions), SerializableTest, and RuntimeMessageAdapterRedactTest under the wire-tests jvm-java-kotlin suite: executable runtime coverage, not fixture code. DEC-5 now defines relevance by covered behavior independent of module; TASK-9 AC#1 and AC#2 inventory wire-runtime commonTest plus the applicable cross-module suites with the named tests retained, AC#3 through AC#5 carry adaptations, security coverage, and CI; TASK-14 AC#1 runs the cross-module runtime inventory and its AC#6 reconciles source and build inventories, rejecting missing relevant cases. Research F4 scopes the no-jvmTest statement to the wire-runtime module and labels existing counts as source-set subsets, with no full count asserted. Same behavior policy; no product feature added. |
| R5 | Java 11 enforcement stopped at `--release 11` for the port's own compilation; dependency bytecode/API compatibility and a real Java 11 consumer run were unowned | APPLIED and ENCODED. DEC-3 and DEC-4 require Java 11-compatible bytecode and API usage for production dependencies (including multi-release jars) plus a real Java 11 consumer run over runtime and schema, and the optional generator when published at the same baseline: TASK-2 AC#3 and AC#5 (automated checks and a Java 11 JVM separate from the JDK 17+ build toolchain), TASK-5 AC#7 (spike-level consumer), TASK-21 AC#6 (final candidate and published artifacts). JDK 17+ remains the build baseline; test and build tooling is not forced to Java 11. |
| R6 | Measurement and acceptance records lacked candidate identity and staleness rules | APPLIED and ENCODED. DEC-9, DEC-10, and DEC-13 require every measurement and acceptance record to identify artifact checksums, build revision, and resolved dependency identities, with relevant code, dependency, or packaging changes requiring remeasurement and renewed acceptance: TASK-19 AC#4 and TASK-20 AC#5 own the evidence; TASK-21 AC#3 refuses stale records. |
| R7 | OPEN-2 assigned the final publishing layout to TASK-1, which precedes the M0 experiments that should inform it | APPLIED. decisions.md OPEN-2 now records TASK-1's grouping as provisional (AC#3), TASK-4 finalizing it alongside the I/O route on spike evidence (AC#6) within the fixed runtime, schema, and optional-generator boundaries, and TASK-5 validating the finalized combination (AC#7). No dependency edge or grouping choice was made now. |
| R8 | The research footprint paragraph omitted JavaPoet from the marginal chain and quoted a 3.4 MB total while poet sizes are unmeasured | APPLIED. Research F2 now sums only measured components (2,937,763 bytes marginal subset; 5,995,370 bytes clean subset, both explicitly excluding the unmeasured poet jars) and leaves complete totals unmeasured pending full dependency resolution and each consumer's existing-dependency inventory; no savings figure is stated. |
| R9 | TASK-3's executable null and exception conventions examples had no acceptance hook in the spike that runs them | APPLIED and ENCODED. TASK-5 AC#6 exercises nullable and non-null boundaries, null-check placement, and the require/checkNotNull exception mapping as executable reference-versus-port checks; ledger F3 aligns. |

## Invariant verdicts (post-decisions)

| Invariant | Verdict and record |
|---|---|
| Production code translatable | No identified language blocker (unsigned usage is two parse sites); feasibility unproven until M0 (TASK-4, TASK-5). |
| Upstream tests verbatim, zero modifications | Broken as written; replaced by DEC-5 adapted-test model. |
| Same-suite goal under the adaptation model | Decided in policy; demonstrated compatibility awaits the M0 compile spike (TASK-5). |
| Apicurio swap coordinate-only | Broken (four files plus POM observed); limited migration approved as scope (DEC-2, TASK-18). |
| Original okio subset list sufficient | Broken; full inventory recorded; spike-owned (OPEN-1, TASK-4). |
| Binary compatibility with precompiled Wire 6/7 code | Withdrawn by decision (DEC-2). |
| Core dependency policy enforceable | Re-gated: no Kotlin in production, Java dependencies allowed, tests may use Kotlin (DEC-4; TASK-2, TASK-12, TASK-21). |
| Performance gating of the first release | Settled (DEC-9; TASK-20, TASK-21). |
| Java 11 fits Kafka client modules and Apicurio | Holds (two independent verifications; research F5, F6). |
| Editions parity | Holds at the pinned tag; the sync policy watches the upstream hook (TASK-23). |
| Provenance and license labels | Corrected (DEC-12; X4). |
| Dependency-graph integrity | Holds by recomputation; producer and CI coverage are now encoded separately (F1/F2, F5). |
| Effort estimate reliability | Unverified, MEDIUM; measured upgrades named and owned (A12). |

## Implementation status and scope of this reconciliation

This reconciliation changed planning documents only: `README.md`, `docs/research-wire-java-port-2026-09-29.md`, `docs/decisions.md`, and this file, plus the backlog task texts edited by the main session. Milestone bookkeeping: the original milestone files m-0 through m-5 were archived (their descriptions could not be updated in place) and replaced by the active m-6 through m-11, which carry the same phases M0 through M5 with task IDs and phase membership unchanged. No source, build, or configuration file was touched; no code exists in this repository yet; TASK-1 through TASK-23 remain To Do. TASK-24 tracks this planning reconciliation separately; its task record carries verification and completion status, and no final approval is claimed here. Code execution and test runs are not applicable to Markdown-only changes; the checks that do apply were run: planning review of the reconciled state, cleanup and code-review passes over the changed documents (the code review produced findings R4 through R9, dispositioned in the ledger above), and mechanical verification that internal document links resolve and that the edited files contain no stale claims of verbatim test adoption, zero dependencies, a coordinate-only Apicurio swap, a port size promise, or binary compatibility.

Known residual gaps, recorded rather than hidden: the Apicurio census is an observed minimum at the audited clone and is re-inventoried by TASK-18; javapoet and kotlinpoet-jvm jar sizes are unmeasured; okio 1.x source purity is unverified; test-suite counts await the M0 mechanical recount; the Kafka dependency-policy statement is an inference from trunk practice; performance, footprint, and feasibility are unmeasured until their owning tasks run.
