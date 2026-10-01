---
id: TASK-3
title: Write Kotlin-to-Java translation conventions doc
status: In Progress
assignee:
  - assistant
created_date: '2026-09-29 09:22'
updated_date: '2026-09-30 03:28'
labels: []
milestone: m-6
dependencies:
  - TASK-1
documentation:
  - docs/decisions.md
  - docs/research-wire-java-port-2026-09-29.md
priority: high
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Write docs/translation-conventions.md as the behavioral contract for Java production translation and mechanical adaptation of upstream Kotlin tests. Keep original pinned tests identifiable and review all changed inputs, helpers, expectations and error paths. Describe Java 11 limitations accurately: records and sealed classes are unavailable; local-variable var is available and is not a Java 11 prohibition. Preserve all upstream per-file notices, including notices embedded below standard headers. Record source tag/path and translation methodology without mandatory repetitive comments in every method.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Conventions cover nullable and non-null parameters/results, null-check placement and exception types, including require versus checkNotNull, with small executable examples owned by TASK-5.
- [ ] #2 Data-class and collection equality, hashCode, toString, collection mutability and unsigned integer parsing have documented Java equivalents and parity checks.
- [ ] #3 Java 11 constraints are accurate; test-only Kotlin and permitted build-time generators are distinguished from forbidden Kotlin production dependencies.
- [ ] #4 The test-adaptation record maps original cases to adapted cases and covers fixtures, setup, helpers, expected values and error paths; exclusions require declared non-ported functionality, never merely a failing test.
- [ ] #5 The provenance policy preserves Google Nano BSD-style notices in the reader family and ProtoWriter, R8 notices in MathMethods, JetBrains Apache notices and mixed notices, plus the actual notices of any vendored dependency.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
