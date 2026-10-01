---
id: TASK-13
title: Run applicable upstream schema tests with complete case accounting
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-8
dependencies:
  - TASK-12
documentation:
  - docs/decisions.md
priority: high
ordinal: 13000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Adopt Wire 7.1.0 schema commonTest and jvmTest logic plus required test support against the Java core. Preserve pinned originals and track mechanical call-form, I/O and range-type adaptations. Every upstream case must be accounted for. All core-schema cases run here; Java-profile/generator cases remain required and are assigned to TASK-16 with a blocking release path. Only cases exclusively for declared non-ported functionality may be excluded. Deferred required cases must not be counted as green or excluded. Kotlin test dependencies are allowed but cannot leak into production.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All applicable core-schema cases pass, including parser, linker, imports, resources, options, source loading, invalid input and SchemaEncoder scenarios.
- [ ] #2 A case-level inventory accounts for all pinned upstream schema tests and support fixtures, mapping adaptations, approved feature exclusions and Java-profile cases owned by TASK-16.
- [ ] #3 Adaptations preserve inputs, setup, helpers, expected outputs and exception paths, and each is reviewed against the pinned originals.
- [ ] #4 The shared CI entry point blocks core-schema regressions and reports generator-owned cases as pending until TASK-16 runs them; upstream implementation jars cannot satisfy port-under-test calls.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
