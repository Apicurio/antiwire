---
id: TASK-7
title: Port wire-runtime JVM reflection machinery to Java
status: Done
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-10-01 22:40'
labels: []
milestone: m-7
dependencies:
  - TASK-6
documentation:
  - docs/decisions.md
priority: high
ordinal: 7000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Complete runtime JVM reflection and serialization behavior on TASK-6's compiling foundation. Own the remaining ProtoAdapter reflection path, Wire registry, RuntimeMessageAdapter, enum reflection, Message serialization and Java-facing formatter behavior identified by the symbol-ownership map. Do not recreate Message or adapter types already owned by TASK-6. Inventory Kotlin-constructor fixture behavior: adapt test-only fixtures where required without weakening relevant runtime scenarios. Android-specific APIs require an explicit applicability decision; do not accidentally introduce Android or Kotlin runtime dependencies.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Reflection-driven encode/decode and serialization of annotated Java classes match Wire 7.1.0 on applicable upstream scenarios, including errors and unknown fields.
- [x] #2 The implementation extends TASK-6's ownership map without duplicate production classes; required tests run in the shared CI entry point.
- [x] #3 Kotlin fixture interoperability and any Android-specific exclusions are recorded against the approved scope; no relevant runtime case is silently omitted.
- [x] #4 Production dependencies remain pure Java with no Kotlin; Android annotations or stubs, if needed for retained APIs, remain compile-only.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Comments

#1 - 2026-10-01 22:40 (UTC)
Done in batch 6 plus the DoD disposition. AC#1: reflection-driven encode/decode round-trips, unknown-field collection, required-redaction refusal and toString verified by ReflectionParityTest (8 tests) against hand-built wire bytes; the oracle comparison is structurally impossible for this layer (the relocated upstream reads its own annotation FQCN), recorded in the test javadoc and the ownership map. AC#2: the ownership map was extended, no duplicate classes, the suite runs in the shared CI entry point. AC#3: AndroidMessage deferred with cause (no consumer in scope; provided-scope android dependency required) and JsonIntegration deferred with cause (its only callers are the DEC-6-excluded gson/moshi adapters; writeAllFields, their integration point, is translated); neither omission is silent. AC#4: dependency-policy suite green, no Kotlin in production scope, no Android stubs needed. DoD: the high-effort review ran on the batch; of its seven findings, five were fixed (null WireField passed to KotlinConstructorBuilder.get, the isXxx getter-name construction, SealedOneOfBinding adapter memoization, LinkageError fallback in getBuilderType, dead nested instanceof) with regression tests covering both confirmed bug paths, one was resolved by restoring upstream's copy-on-refusal mutability semantics whose earlier flattening the review itself had suggested (the reviewer's equivalence claim was wrong; the red test proved it and the fallback now matches upstream intent), and one (cachedSerializedSize encapsulation) was resolved by reverting the field to package-private with a documented do-not-use accessor pair that the reflection bridge uses, matching upstream's bytecode shape where Kotlin internal compiles public. Evidence: mvn verify 802 tests, 0 failures; scripts/verify.sh all 5 ACTIVE suites pass.
