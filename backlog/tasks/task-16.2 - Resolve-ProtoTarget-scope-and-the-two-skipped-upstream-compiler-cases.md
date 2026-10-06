---
id: TASK-16.2
title: Resolve ProtoTarget scope and the two skipped upstream compiler cases
status: To Do
assignee: []
created_date: '2026-10-06 08:41'
labels:
  - adversarial-audit
  - scope
  - parity
milestone: m-12
dependencies: []
references:
  - wire-java-generator/src/test/java/com/squareup/wire/schema/WireRunTest.java
  - docs/task16-case-accounting.md
  - config/upstream-case-map.json
documentation:
  - docs/decisions.md
  - docs/parity-runner.md
parent_task_id: TASK-16
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
At audit baseline 295ef5f0e7a1e45937417b9f621044d8139df6f5, WireRunTest.protoOnly and protoTargetNeverEmitsGoogleProtobufDescriptor are disabled in the port although both execute in pinned upstream Wire 7.1.0. docs/task16-case-accounting.md calls ProtoTarget an unported scope gap. The case map counts these names as ported, and TASK-16 is Done. DEC-6 does not explicitly exclude .proto emission. However, TASK-16's delivery is framed around the Java-target compiler, so this audit does not assume that .proto emission is automatically an approved new feature.

Resolve the inconsistent scope and ownership. Either deliver the intended ProtoTarget behavior and revive its cases, or obtain an explicit maintainer scope decision that excludes this product and records these cases honestly. An annotation citing TASK-16 is not itself an approved exclusion. The generic execution-accounting mechanism is owned by TASK-14.2; this task owns the specific product decision and resulting behavior.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The decision record explicitly states whether ProtoTarget .proto emission belongs to the initial release, with maintainer approval required for any new exclusion.
- [ ] #2 If retained in scope, ProtoTarget behavior executes both pinned upstream cases with preserved expectations and has a Java 11-compatible implementation.
- [ ] #3 If explicitly excluded, both cases are recorded as excluded for the named product rather than counted as executed or passed, and supported-feature documentation agrees.
- [ ] #4 TASK-16's completion record and TASK-21's release checklist reference the resolved disposition without hiding required pending work.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
