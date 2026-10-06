---
id: doc-1
title: Adversarial delivery audit 2026-10-06
type: other
created_date: '2026-10-06 08:24'
updated_date: '2026-10-06 08:56'
---
# Adversarial delivery audit

Audit baseline: `295ef5f0e7a1e45937417b9f621044d8139df6f5`, inspected on 2026-10-06. Verdict: ISSUES. The implemented core substantially matches the evolved Apicurio-first goal, but the evidence does not justify an unqualified claim of complete applicable parity or release readiness. The follow-up tasks remain open; this assessment does not authorize release.

## Scope and rationale

The original request in `README.md:9` prioritizes functional compatibility for a pure-Java Wire port, followed by footprint and performance. The binding scope and subsequent decisions live in `docs/decisions.md`, `docs/plan-review.md`, and `docs/research-wire-java-port-2026-09-29.md`.

The project memory indexes were not found. Relevant local session records supplied historical context. Later acceptance and deferral decisions were available as repository records, not as independently recovered transcripts. Implementation claims were checked against sources, generated output, build artifacts, and executable probes rather than accepted from conversation summaries.

The recorded scope allows small reviewed Java dependencies and test adaptations, but not Kotlin production dependencies or exclusions merely because a test fails. Apicurio is the initial consumer. Kafka and publication timing are deliberately deferred. The audit does not reverse those decisions.

## Actual verification

The final `scripts/verify.sh` run started at `2026-10-06T10:27:09+02:00` and ended at `10:29:38+02:00`, with exit code 0 and `VERDICT: all 12 ACTIVE suites passed`. The build used Temurin 21.0.11. The Java 11 consumer check ran on Temurin 11.0.24.

| Suite | Reported cases, including skips | Skipped |
| --- | ---: | ---: |
| runtime-tests | 899 | 4 |
| schema-tests | 633 | 6 |
| protoc-oracle | 122 | 50 |
| compiler-tests | 179 | 33 |

These are suite-summary counts, not counts of all distinct upstream scenarios executed. The separate parity checker reconciled 967 upstream case names, reporting 963 as ported and 4 as recorded missing. Its name-based classification does not prove execution, as the fault-injection checks below demonstrate.

The successful run followed two failed attempts. The first failed on stale compiled test classes whose sources no longer existed. The build worker inspected that evidence and removed module build outputs. The second failed because the pinned upstream clone was missing. After `scripts/fetch-upstream.sh` obtained and validated the clone, the final run passed. No production-source fix was made to obtain that result.

## Requirements and evidence

| Requirement | Assessment | Evidence and limitation |
| --- | --- | --- |
| Pure-Java production dependency policy | Verified on the built candidate. | Dependency-policy and duplicate-class checks passed. Runtime and schema use the documented dependency boundary; the optional generator adds JavaPoet. |
| Java 11 baseline | Verified for the checked bytecode and smoke paths. | Bytecode checks and the real Java 11 consumer smoke passed. Full final-candidate and published-artifact consumer coverage remains TASK-21 acceptance work. |
| Applicable upstream test parity | Incomplete assurance. | Source-name reconciliation passes, but unrecorded skips and false fixture declarations evade the checked gates. TASK-14.2 owns enforcement. |
| Java generation for Empty | Broken. | Generated output imports kotlin.Unit and uses the Void-typed EMPTY adapter. TASK-16.1 owns the reproduced compilation failure. |
| Dynamic Empty model | Verified within exercised cases. | TASK-26's UnitValue and WIRE_EMPTY path preserves the exercised presence and wire representation. This does not fix generated Java. |
| ProtoTarget scope | Unresolved disposition. | Two upstream cases are disabled in the port. The case ledger calls this a scope gap, but DEC-6 has no explicit exclusion. TASK-16.2 owns the decision and resulting behavior. |
| Apicurio migration | Supported by historical integration records, not freshly rerun here. | TASK-18 and docs/footprint.md section 9.5 record migration and differential evidence. The integration clone was unavailable on this host. TASK-21 requires evidence for the final candidate. |
| Minimal footprint | Historically measured and accepted. | docs/footprint.md binds acceptance to c713e9a. Later relevant code changes require renewal, already owned by TASK-21. |
| Performance | Historically measured, with resolved findings recorded. | docs/performance.md binds its latest recorded session to 82c3624. Audit recomputation checked report arithmetic, not fresh benchmark execution. Relevant later changes require renewed evidence. |
| Pinned upstream provenance | Verified by the exercised checks. | The fetched clone and parity checks validated the pinned 7.1.0 identity. This proves provenance, not all behavior. |
| Security regression checks | Present and green in the run. | The security corpus has skipped-aware class floors. Those floors do not detect arbitrary weakening of assertions within a still-running case. |
| Upstream maintenance | Procedure and rehearsal recorded; watch executed. | docs/upstream-sync.md contains the procedure and security-fix rehearsal. The audit did not replay the rehearsal. Response targets remain a maintainer policy decision. |
| Release and licensing | Open, already release-owned. | The candidate builder needs a recorded-state maintenance update. The per-file inventory predates Bytes.java. TASK-21 owns final licensing, freshness, consumer checks and publication permission. |
| Kafka and initial exclusions | Deliberately deferred or excluded. | TASK-22 and DEC-6/7 retain their scope. No new Kafka implementation task is created. |

## Findings and backlog dispositions

All six new tasks and the revised existing TASK-27 belong to milestone `m-12`, **M6 Adversarial delivery audit 2026-10-06**.

| Task | Priority | Finding and required outcome |
| --- | --- | --- |
| TASK-14.2 | High | Adding an unrecorded method or class @Disabled to PrunerTest leaves parity coverage green. Declaring the executable file fixture:true also passes and removes its cases from reconciliation. Require execution-aware case dispositions and end-to-end negative probes. |
| TASK-14.1 | Medium | verify.sh invokes Maven before fetching sources that SchemaEncoderInteropTest requires. Fetch and validate the pinned clone before dependent tests, while preserving fail-closed behavior. |
| TASK-16.1 | Medium | Empty-bearing generated Java fails compilation both without Kotlin and with Kotlin added. Produce Java 11-compatible pure-Java models and revive the generated-model Empty test. |
| TASK-16.2 | Medium | protoOnly and protoTargetNeverEmitsGoogleProtobufDescriptor execute upstream but are disabled in the port without a declared product exclusion. Deliver retained scope or obtain an explicit maintainer exclusion, then account for the cases honestly. |
| TASK-21.1 | Medium | release-build.sh still requires obsolete PENDING phrases after the recorded gates were resolved. Restore its documented operation without inventing approval or bypassing candidate freshness. |
| TASK-24.1 | Medium | README, the team update and TASK-19 bookkeeping contradict the implementation or overstate proof. Correct current status, mapping-aware golden wording, execution counts and stale references while preserving historical identities. |
| TASK-27, revised | Low | The original Bytes collision premise was refuted for the tested top-level shapes. Add regression coverage and correct the unsupported limitation; investigate remaining variants only with bounded reproducers, not speculative production changes. |

TASK-21 retains its original release responsibility and now explicitly depends on the verified bootstrap, execution-accounting, Empty, ProtoTarget-disposition and candidate-builder follow-ups. Its notes identify the missing Bytes.java license-inventory entry and same-candidate integration renewal. No release permission is inferred. TASK-22 remains deferred and unchanged.

## Reproductions and refutations

The Empty reproducer uses a proto3 message with `google.protobuf.Empty done = 2`. `JavaGenerator.java:156` selects kotlin.Unit; generated code refers to ProtoAdapter.EMPTY, which is Void-typed. Compilation reports `package kotlin does not exist` without Kotlin and a Unit/Void mismatch when Kotlin is supplied. DEC-6 excludes Kotlin generator products, not Empty fields in Java output.

The parity probes used scratch copies. Removing a required method failed with a lost-case diagnostic. Keeping its name but adding @Disabled passed. Applying @Disabled to its class also passed. Setting fixture:true passed and removed that file's cases from the inventory. A separate replay of verify.sh's result parser accepted a summary with zero failures and increased skips. A full reactor run with these injected mutations was NOT performed. The confirmed result is the checker/parser blind spot, not an observed mutated full-build success.

The builder probe executed only its unchanged guard section against the current measurement documents. Both expected PENDING predicates had no match; the guard exited 1 before packaging. This is intentional fail-closed behavior whose documented manual follow-through was missed, not a fail-open publication defect.

The Bytes counterexamples generated same-package and cross-package models, a Bytes message with its own bytes field, repeated and map bytes, repeated message values, and an enum named Bytes. Compilation succeeded, and a mixed-field round-trip preserved the intended types. This refutes the original concrete claim for those variants only. Nested types, proto2, oneof and extension variants remain unverified.

A suspected top-level scalar-decode divergence was also refuted against the upstream runtime binary. The alleged zero-copy aliasing hazard was not reproduced. No behavioral fix is filed for that hypothesis.

## Boundaries and minor dispositions

No JMH benchmark rerun, fresh Apicurio integration run, GitHub CI-history review, publication, tag, commit or production-code change was performed. Historical report arithmetic and provenance are not fresh performance measurements. Current passing suites are not proof that every relevant upstream test executes.

The earlier stale compiled-test failure is a local build-output condition, not a HEAD production bug. TASK-14.1 includes a documented clean-build recovery instruction. Adding exclusions for those old test names is explicitly rejected.

Python bytecode-cache noise and an inactive M0 build-output directory do not establish product defects. No cleanup project or new release blocker is created for them. The audit refreshed build outputs and produced local scratch probes.

A Backlog MCP milestone-filter response returned unrelated historical tasks, contradicting task_view and on-disk frontmatter. The audit validates persisted task membership directly rather than rewriting correct files to match that response. This is a tooling observation, not an antiwire implementation finding.

## Evidence locations

Durable requirement and implementation references are in the linked tasks and the repository files named above. Local scratch evidence includes `/tmp/antiwire-audit-intent.md`, `/tmp/antiwire-audit-evidence.md`, `/tmp/antiwire-audit-behavior.md`, `/tmp/antiwire-audit-parity-verdict.md`, `/tmp/antiwire-audit-bootstrap-verdict.md`, `/tmp/antiwire-audit-release-verdict.md`, `/tmp/antiwire-audit-bytes-verdict.md`, `/tmp/antiwire-audit-build-result.txt`, `/tmp/antiwire-empty-repro/`, and `/tmp/aw-verify/`. These scratch paths may disappear and are not substitutes for preserving regression tests when the tasks are implemented.

The three full verification logs are `/tmp/antiwire-verify-run.log`, `/tmp/antiwire-verify-clean.log`, and `/tmp/antiwire-verify-final.log`. The last log is the successful baseline run. Follow-up tasks were To Do at audit time. Their implementation status lives in the tracker; the audit conclusions above describe the baseline commit only.
