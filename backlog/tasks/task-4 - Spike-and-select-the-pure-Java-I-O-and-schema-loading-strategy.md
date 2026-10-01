---
id: TASK-4
title: Spike and select the pure-Java I/O and schema-loading strategy
status: In Progress
assignee: []
created_date: '2026-09-29 09:22'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-6
dependencies:
  - TASK-2
  - TASK-3
documentation:
  - docs/decisions.md
  - docs/research-wire-java-port-2026-09-29.md
priority: high
ordinal: 4000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Evaluate the smallest maintainable I/O strategy consistent with docs/decisions.md: reviewed pure-Java dependencies, a translated Okio subset, scoped JDK I/O replacement, or a bounded combination. Do not assume modern Okio is Java or legacy Okio is a drop-in replacement. Inventory ByteString, Buffer, Source/Sink, buffered I/O, UTF-8, exceptions and helpers plus FileSystem, Path, FileHandle, in-memory sources, classpath resources and ZIP loading. The chosen public namespaces affect tests, generated code and Apicurio migration. Demonstrate a representative path and record source/test/maintenance cost before committing the architecture to M1.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A pinned-source inventory maps every I/O and loading API required by the ported runtime and schema to the selected implementation, with explicit source-compatibility and Apicurio migration consequences.
- [ ] #2 The selected approach demonstrates byte-buffer behavior and representative in-memory, filesystem, classpath-resource and ZIP schema-source access without Kotlin in production dependencies; excluded behavior has a scope justification.
- [ ] #3 Relevant upstream Okio behavior tests for reused or translated classes run with reviewed adaptations; alternative implementations demonstrate the equivalent required behaviors.
- [ ] #4 docs/decisions.md records the chosen namespaces, loader boundary, dependency licenses, duplicate-class policy and reconciliation of Java golden comparisons with any required namespace changes.
- [ ] #5 The spike records measured implementation scope and verification effort to refine the provisional schedule; no unsupported jar-size or performance promise remains.
- [ ] #6 The publication grouping left provisional by TASK-1 is finalized in docs/decisions.md using spike evidence, preserving independently consumable runtime/schema modules and an optional generator. Required shell/layout changes are applied before TASK-5 validates the combination.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Comments

#1 - 2026-10-01 13:30 (UTC)
Merged execution state (m0-spikes reconciled with the reviewed scope). Done: AC#3 (okio's own 1.17.6 tests vendored verbatim: 732 run, 0 failures); the buffer half of AC#1 and AC#2; the namespace recording required by AC#4 lives in decisions.md (M0 spike outputs) and docs/m0-execution-ledger-2026-10-01.md; AC#5 effort data is in the ledger. Remaining: the loading-API inventory map and the in-memory/filesystem/classpath/ZIP demonstration (AC#1/AC#2), the full decisions text of AC#4 (loader boundary, licenses, duplicate-class policy, golden reconciliation), AC#6 publication grouping finalization. See docs/m0-execution-ledger-2026-10-01.md for the full mapping.

#2 - 2026-10-01 15:10 (UTC)
AC map refresh (PR #1, additive commits): AC#1 done (docs/loading-api-inventory.md: pinned-source map, consequences, justified exclusions); AC#2 done (LoadingAccessTest: in-memory, filesystem, classpath, ZIP, path semantics; build suite + Java 11 consumer); AC#5 done (effort recorded in the M0 execution ledger). The loading layer is implemented: okio.Path, okio.FileSystem (SYSTEM over nio, openZip over zipfs, asResourceFileSystem for classpath), okio.FileMetadata; FakeFileSystem deferred to M2 with the verbatim schema tests (recorded in the inventory). Remaining: AC#4 final decisions fold (content exists in the inventory; lands with AC#6), AC#6 publication grouping. Evidence: mvn verify 759 tests 0 failures; scripts/verify.sh all 5 ACTIVE suites pass.
