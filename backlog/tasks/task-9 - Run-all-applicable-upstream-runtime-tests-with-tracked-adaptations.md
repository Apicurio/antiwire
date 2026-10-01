---
id: TASK-9
title: Run all applicable upstream runtime tests with tracked adaptations
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-7
dependencies:
  - TASK-6
  - TASK-7
  - TASK-8
documentation:
  - docs/decisions.md
priority: high
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Adopt every applicable JVM runtime behavior test from Wire 7.1.0 at the pinned commit, regardless of which upstream module houses it. Inventory wire-runtime/commonTest and executable JVM suites under wire-tests, including wire-tests/jvm-java-kotlin. wire-runtime has no jvmTest source set, but this does not make commonTest the complete runtime inventory. Retain relevant serialization, unknown-field, redaction, parsing, oneof and adapter cases. Preserve pinned originals and track mechanical adaptations to Java and Kotlin tests against Java production artifacts. Generate required fixtures with the pinned upstream build-time tools rather than waiting for TASK-16. Full runtime and JVM reflection coverage stays in scope. Exclude only individual cases exclusively testing declared non-ported features, not whole mixed suites. Kotlin may exist in test/build scope only.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A source-derived case inventory covers wire-runtime/commonTest and applicable runtime behavior tests in wire-tests JVM suites, including jvm-java-kotlin. Every case maps to an executed test or a declared-feature exclusion; all applicable cases pass against port artifacts.
- [ ] #2 The inventory explicitly retains applicable SerializableTest, UnknownFieldsTest and RuntimeMessageAdapterRedactTest cases and discovers other runtime cases by behavior and build wiring, not only module name. Mixed suites retain relevant cases despite JSON or other excluded-feature dependencies.
- [ ] #3 Adaptations are reproducible and individually recorded, including fixture/setup/helper changes; expected behavior and error checks are preserved and reviewed. Pinned upstream fixture generation avoids a dependency on the later ported generator.
- [ ] #4 The suite covers JVM-relevant negative-length and reader-limit regressions, including GHSA-7xpr-hc2w-34m9 and GHSA-9rm7-3qhh-h2mc.
- [ ] #5 The shared CI entry point executes the applicable cross-module inventory and fails on test regressions, unaccounted upstream drift, lost cases or accidental upstream implementation classes on the port-under-test classpath.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
