---
id: TASK-16
title: Port the Java compiler and generator with blocking behavior and golden tests
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-9
dependencies:
  - TASK-14
documentation:
  - docs/decisions.md
priority: medium
ordinal: 16000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Port the Java-target CLI and Java generator in the optional module created by TASK-2. Complete the Java-profile and AdapterConstant support separated in TASK-12. Remove KotlinPoet, Kotlin/Swift generator and Kotlin-backed CLI dependencies from production; a reviewed pure-Java JavaPoet dependency is allowed. Preserve every applicable Java-target compiler case, including cases in mixed upstream suites and profile tests deferred from schema. The pinned Java golden corpus is limited, so golden identity alone does not establish coverage. Reconcile golden comparisons with the namespace strategy chosen in M0 and compile/run generated output.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The Java CLI, generator and profile support pass all applicable upstream Java-target compiler and profile cases; every case deferred from TASK-13 is executed or excluded solely for a declared non-ported feature.
- [ ] #2 Java generated output is byte-identical where the compatibility contract preserves output; any required mechanical namespace/API mapping is bounded, documented, reviewed and checked automatically against pinned upstream goldens.
- [ ] #3 Generated Java fixtures compile and run against the port, with positive and negative compiler cases beyond the limited golden corpus.
- [ ] #4 Compiler/profile tests and golden comparisons run as blocking jobs in TASK-14's shared CI entry point; no required generator-owned case remains pending.
- [ ] #5 The optional generator artifact and its transitives contain no Kotlin; runtime/schema remain independently consumable without generator dependencies, and all retained Java dependencies satisfy the recorded policy.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
