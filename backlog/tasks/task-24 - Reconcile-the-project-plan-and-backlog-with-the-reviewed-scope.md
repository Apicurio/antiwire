---
id: TASK-24
title: Reconcile the project plan and backlog with the reviewed scope
status: Done
assignee:
  - assistant
created_date: '2026-09-29 16:34'
updated_date: '2026-09-29 17:53'
labels:
  - planning
  - review
dependencies: []
references:
  - docs/research-wire-java-port-2026-09-29.md
  - docs/decisions.md
  - docs/plan-review.md
priority: high
ordinal: 500
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Apply the approved planning review and maintainer decisions to README, the research and project plan, a durable decision record, and existing execution tasks. This is documentation and backlog maintenance only. Do not implement the Java port, change Apicurio sources, publish artifacts, commit, or transfer the repository. Preserve every review recommendation through an applied correction, an explicit task owner, or a documented rejection.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Project documents consistently state the agreed compatibility, dependency, test-scope, Apicurio-first, publication, performance and maintenance policies in English.
- [x] #2 Existing execution tasks have self-contained acceptance criteria and dependencies that encode the corrected scope without claiming implementation work is complete.
- [x] #3 Every recommendation in the consolidated review has a durable disposition or an existing execution-task owner.
- [x] #4 An independent final-state review approves the revised plan and mechanical checks verify dependency integrity, release prerequisites, references and absence of stale contradictory promises.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Apply the maintainer-approved planning corrections without implementing, committing, publishing or transferring the project. 1. Inspect the baseline and preserve unrelated changes. 2. Reconcile README and research inline; add docs/decisions.md and docs/plan-review.md with the agreed policies and every review disposition. 3. Update TASK-1 through TASK-23 through Backlog MCP; keep all implementation tasks To Do. Encode the compiling runtime foundation, parser-before-linker order, generator/profile case ownership, full CI case accounting, and footprint/performance/synchronization release prerequisites. 4. Replace obsolete milestones using supported MCP operations because descriptions cannot be updated in place: create corrected phases, reassign unchanged task IDs, archive unreferenced originals. Preserve historical files and logical phase membership, not internal milestone IDs. 5. Verify actual files, dependency DAG, release closure, active milestone references, documentation links, review coverage and statuses. 6. Obtain explicit independent APPROVED on the final plan, resolve every finding inline and re-review. 7. Complete only TASK-24 with evidence. This task intentionally has no implementation milestone because it reconciles all phases.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Updated TASK-1 through TASK-23 through Backlog MCP. Execution tasks remain To Do. The revised graph anchors M0 prerequisites, introduces a compiling runtime foundation, sequences parser before linker, assigns deferred Java-profile cases to TASK-16, requires complete CI case accounting and gates publication on footprint, runtime/schema benchmarks and demonstrated upstream synchronization. TASK-23 ordinal is 20500 so it precedes publication. README and durable review/decision documents are being reconciled independently; final verification and review are pending.

All initial product questions are settled. M0 still owns measured I/O and build feasibility. The deferred Kafka scenario remains deliberately unselected and does not block the initial release. Documentation-only completion gates apply; no Java implementation or application-test success is claimed.

All six milestone descriptions were replaced through MCP. Active milestone IDs m-6 through m-11 represent M0 through M5. The original m-0 through m-5 files are archived history, and no active task references them. Logical phase membership and execution task IDs are unchanged. Verification findings promoted the three-way footprint comparison, performance methodology and supported-version/response policy into explicit acceptance criteria.

Final state submitted for independent read-only planning review. Active milestone replacements m-6 through m-11 have been verified through milestone_list with no missing milestone files and no archived milestone references on tasks. TASK-1 through TASK-23 remain To Do. Final graph verification, documentation consistency review and explicit reviewer approval remain required before closing TASK-24.

Mechanical verification on the reconciled backlog passed: all 24 task IDs are unique, all 30 dependency references resolve, there are no self-edges or cycles, ordinals respect dependencies, and active milestone references resolve. TASK-21's transitive prerequisites are TASK-1 through TASK-20 plus TASK-23, excluding deferred TASK-22 and this planning task. TASK-1 through TASK-23 remain To Do. The verifier also checked the release acceptance criteria, documentation references, git diff whitespace and absence of application/build sources. Independent final-state review remains pending; these checks do not establish implementation feasibility.

Independent headless review returned ISSUES with three verified text-only findings. R1: docs/plan-review.md Consumer Q2 must say publication authorization is not recorded or assumed, not 'not granted'. R2: docs/decisions.md must explicitly name the wire-reflector gRPC reflection product and its grpcurl suite as excluded, matching the existing research scope; runtime JVM reflection remains mandatory. R3: TASK-23's upstream replay must include a security-relevant change and exercise detection, triage, owner/escalation handling and parity validation. All three will be corrected within this task and re-reviewed. No implementation-scope work is started.

Persistent reviewer session: b6f561a2-6ddb-47a4-aa80-ef4fdfb8d948. Initial raw review: /tmp/antiwire-final-review-stdout.json; extracted result: /tmp/antiwire-final-review-result.txt. Resume this reviewer for the final verdict rather than replacing it.

Applied and directly checked all three round-one findings. Consumer Q2 now uses the canonical absence-of-recorded-authorization wording. DEC-6 explicitly excludes the wire-reflector gRPC product and grpcurl suite while retaining mandatory JVM runtime reflection. TASK-23 and DEC-11 require a locally recorded security-fix rehearsal including detection, triage, owner routing, simulated escalation, adaptation, regression detection and full isolated parity without real incident notifications. The same reviewer session is running a delta re-review. Approval remains pending.

The persistent independent reviewer returned APPROVED in round two; all three findings were verified resolved in actual files. Evidence: /tmp/antiwire-final-review-round2-result.txt. Approval covers planning consistency only, not implementation feasibility.

Completion bookkeeping was blocked twice by the local hook requiring /simplify and code-review markers. The explicit documentation-only exemption did not satisfy the hook. No bypass or hook/configuration change was attempted. The required skills are now running against the planning-only diff; TASK-24 remains In Progress until their results are assessed.

The required high-effort code-review gate found six additional planning gaps after the earlier peer approval. Tracked here for resolution before closure: R4 runtime cases in upstream wire-tests may be omitted by TASK-9's module-based inventory (independent source check pending); R5 TASK-2 does not enforce Java 11 compatibility of allowed production dependencies; R6 TASK-21 does not bind footprint/performance evidence and acceptance to released artifact identities; R7 OPEN-2 assigns final publication layout to TASK-1 before its experiments; R8 research F2's marginal total still omits JavaPoet and includes unmeasured sizes; R9 TASK-5 lacks the executable nullability/exception examples assigned by TASK-3. R5-R9 were confirmed by reading the current task and document text. Earlier APPROVED does not cover these unresolved findings. All fixes remain planning-only and will be re-reviewed in the same peer session.

R4 is confirmed against the pinned upstream source: wire-tests/jvm-java-kotlin contains executable runtime tests, including UnknownFieldsTest, SerializableTest and RuntimeMessageAdapterRedactTest, rather than fixtures alone. TASK-9 now inventories applicable runtime behavior across upstream modules, and TASK-14 reconciles extracted cases with upstream build/source-set wiring. Fixtures use pinned upstream build tools, so runtime tests do not depend on the later ported generator. R5-R9 task corrections are also applied. Document alignment and same-session final re-review remain pending; no implementation task status changed.

Final independent review: APPROVED in round three of persistent session b6f561a2-6ddb-47a4-aa80-ef4fdfb8d948. The reviewer checked all R4-R9 fixes and their cross-document impacts, found no regressions and confirmed the earlier R1-R3 corrections remain intact. Evidence: /tmp/antiwire-final-review-round3-result.txt and its raw stdout JSON. Approval covers planning consistency only; implementation feasibility remains an M0 outcome.

Final mechanical verification passed with zero failures: unique task IDs, resolving dependencies, acyclic graph, ordinal ordering, active milestone assignments, release prerequisite closure, documentation references, corrected acceptance clauses, footprint subtotal arithmetic and git diff --check. Evidence: /tmp/antiwire-final-mechanical-round3.md. The four /simplify checks returned PASS or code-specific NOT_APPLICABLE; high-effort code-review ran and all six findings were resolved and independently re-reviewed. Application tests are not applicable because the changes are Markdown only and no implementation/build exists. No gate bypass or configuration changes were used.

Only TASK-24 is closed. TASK-1 through TASK-23 remain To Do. No implementation, commit, publication or repository transfer was performed.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Reconciled the project plan and execution backlog with the agreed scope, entirely in English.

Updated README.md and the research report inline. Added docs/decisions.md as the policy baseline and docs/plan-review.md as the complete disposition ledger. Revised all 23 execution tasks with explicit ownership, acceptance criteria and dependencies. Replaced stale milestone descriptions through supported Backlog operations and preserved the original milestones in the archive.

The plan now covers tracked test adaptations and cross-module runtime tests, a compiling runtime foundation, generator/profile case accounting, limited Apicurio migration, upstream-version comparisons, Java 11 dependency and consumer checks, corrected licensing/security inventories, candidate-bound footprint/performance acceptance, and a demonstrated security-relevant upstream synchronization procedure. Kafka remains deferred; io.apicurio publication requires authorization.

Verification: /simplify checks completed without required changes. High-effort code-review findings were corrected. The same independent reviewer returned APPROVED on the final revision, with R1-R9 resolved and no regressions. Mechanical graph, release-closure, milestone, reference, arithmetic and whitespace checks passed.

Gate-exempt for application tests: Markdown-only planning changes; no application implementation or build exists. No code/configuration changes, commits, publication or repository transfer occurred. Only this planning task is Done; every implementation task remains To Do, and feasibility must still be demonstrated by M0.
<!-- SECTION:FINAL_SUMMARY:END -->
