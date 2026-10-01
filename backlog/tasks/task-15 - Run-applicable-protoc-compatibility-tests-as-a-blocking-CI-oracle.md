---
id: TASK-15
title: Run applicable protoc compatibility tests as a blocking CI oracle
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
priority: high
ordinal: 15000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Run all applicable cases from Wire 7.1.0 wire-protoc-compatibility-tests against the Java port and protoc-generated Java reference code. Pin protoc 4.36.1 and its matching descriptor sources. Inventory fixture generation and test dependencies individually: upstream Kotlin generators may run as isolated build-time tools, but JSON adapters, gRPC and excluded generator-product cases are not part of the initial shipped feature set. Retain every binary interoperability case relevant to the port. Use the pinned upstream generator for fixtures rather than waiting for TASK-16, avoiding a dependency cycle.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All applicable proto2/proto3 binary interoperability and roundtrip cases pass with protoc 4.36.1, including applicable struct, duration, instant and field-mask cases.
- [ ] #2 The case inventory covers every upstream oracle case and dependency; exclusions identify declared non-ported functionality, and adaptations preserve behavior rather than remove failing scenarios.
- [ ] #3 Fixture generation, descriptor sources and protoc versions are pinned and reproducible; reference implementations are isolated from port-under-test classes.
- [ ] #4 The complete applicable oracle suite is wired into TASK-14's shared CI runner as a blocking check on every change and leaves no required oracle case pending.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
