---
id: TASK-16.2.1
title: Apply the output-directory containment guard to ProtoTarget
status: To Do
assignee: []
created_date: '2026-10-06 15:33'
labels:
  - adversarial-audit
  - codegen
  - hardening
milestone: m-12
dependencies: []
references:
  - wire-java-generator/src/main/java/com/squareup/wire/schema/ProtoTarget.java
  - >-
    wire-java-generator/src/main/java/com/squareup/wire/java/JavaSchemaHandler.java
  - wire-schema-java/src/main/java/com/squareup/wire/schema/SchemaHandler.java
documentation:
  - docs/compatibility-matrix.md
  - docs/security-regression-inventory.md
parent_task_id: TASK-16.2
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The high-effort review of the TASK-16.2 commit found that the new ProtoTarget handler never calls SchemaHandler.checkPathInOutDirectory, the guard that every other emitting handler in the port applies (JavaSchemaHandler calls it at line 154). Upstream's ProtoTarget does not call it either, so parity is not at risk, but the port's own hardening is bypassed: a proto whose recorded location path is absolute or contains a parent traversal makes the handler write outside the output directory. I confirmed in the pinned upstream clone that its ProtoTarget has no such check and in the port that only JavaSchemaHandler uses the guard. The same review noted that the IOException to RuntimeException wrapping for directory creation is duplicated verbatim between ProtoTarget and JavaSchemaHandler.

Apply the existing guard to the proto output path and remove the duplicated wrapping. This is a small, low-risk hardening; it is a deliberate and documented divergence from upstream, not a behavior change for ordinary relative paths. Found by the 2026-10-06 delivery audit follow-up work.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A proto file whose recorded location path is absolute or traverses upward is rejected by ProtoTarget with the same refusal message the other handlers use, and nothing is written outside the output directory.
- [ ] #2 A regression test covers an in-directory path (accepted) and an escaping path (rejected), and the revived upstream cases protoOnly and protoTargetNeverEmitsGoogleProtobufDescriptor still pass unchanged.
- [ ] #3 The directory-creation error wrapping shared by ProtoTarget and JavaSchemaHandler lives in one place instead of being duplicated, with no change in behavior or messages.
- [ ] #4 The divergence from upstream (a guard upstream does not apply) is recorded in the compatibility matrix as a deliberate hardening, with the reason.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
