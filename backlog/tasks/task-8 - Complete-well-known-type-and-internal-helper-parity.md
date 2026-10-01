---
id: TASK-8
title: Complete well-known-type and internal-helper parity
status: Done
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-10-01 23:20'
labels: []
milestone: m-7
dependencies:
  - TASK-6
documentation:
  - docs/decisions.md
priority: medium
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Complete well-known-type and helper behaviors not already implemented in TASK-6's compiling foundation. Use its symbol-ownership map, extending rather than redefining Duration, Instant, FieldMask, AnyMessage, OneOf, Internal and supporting types. Cover collections, copy-on-write behavior, numeric helpers, Java time mapping and generated Java code imports. Preserve JetBrains and R8 notices. This task is a behavioral completion step after the encoding foundation, not a prerequisite that leaves TASK-6 unable to compile.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 All remaining applicable well-known-type and helper scenarios match upstream Wire 7.1.0, including Java time edge cases, equality, collection mutability and malformed values.
- [x] #2 Internal APIs required by generated Java code compile and execute against the port, with any bounded namespace mapping recorded.
- [x] #3 The ownership map accounts for every required type and helper exactly once across TASK-6 and this task; no foundational placeholder remains.
- [x] #4 Relevant tests run in the shared CI entry point and preserve the production dependency and provenance policies.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Comments

#1 - 2026-10-01 23:20 (UTC)
Done. The well-known types and their adapters, Internal, ImmutableList/MutableOnWriteList and FieldMask landed with TASK-6's batches (its parity tests cover the Duration/Instant edge semantics, FieldMask, structs and wrappers); this task completed the residual helpers: the primitive ArrayList family, MathMethods, and the code-point-accurate camelCase. DoD: the high-effort review reported six findings, all fixed: the R8 BSD notice restored verbatim as leading content in MathMethods; the JetBrains headers restored verbatim on the four ArrayList files (my Square-2019 headers misattributed them); the forDecoding companion factories translated for all four classes including the Int.MAX_VALUE clamp (the generator emits these for every packed repeated primitive field, and the reader side was already ported), with coverage; the AbstractList inheritance dropped, restoring upstream's plain final-class shape, identity equals, and the original toArray() name (which also retired the toPrimitiveArray rename and its ledger ripples); the invented assertEquals(3, 3) tautology and unused imports removed from the four tests. The MathMethods Kotlin-xor precedence note from the review: translations verified precedence-correct. Evidence: mvn verify 811 tests, 0 failures; scripts/verify.sh all 5 ACTIVE suites pass.
