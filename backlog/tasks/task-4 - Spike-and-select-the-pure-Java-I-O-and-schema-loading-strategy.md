---
id: TASK-4
title: Spike and select the pure-Java I/O and schema-loading strategy
status: To Do
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
