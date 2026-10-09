---
id: TASK-33.4
title: >-
  Redo the Apicurio integration on a pushed branch and publish the migration
  guide
status: To Do
assignee: []
created_date: '2026-10-09 20:54'
labels:
  - user-feedback
  - compatibility
  - apicurio
milestone: m-11
dependencies: []
parent_task_id: TASK-33
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The TASK-18 integration branch was erased (see the verified notes on TASK-18/21/33: /tmp/apicurio on the Mac was wiped). Redo it on a new branch cut from apicurio-registry 3620f08c in a persistent location (a fork or branch pushed to a remote), using the apicurio-registry agent on this machine for the Apicurio side. This time the proof is behavioral: build the four protobuf modules against the port and run their existing tests (utils/protobuf-schema-utilities, schema-util/protobuf, schema-validation/protobuf, serdes/generic/serde-common-protobuf), plus the descriptor-bytes parity drivers. Depends on TASK-33.2 and TASK-34 so the rewrite is minimal.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A persistent, pushed branch of the Apicurio code at apicurio-registry 3620f08c (or the current origin/main) builds the four protobuf modules against the antiwire jars (io.apicurio coordinates) with Wire removed, and the module tests pass; the branch and its URL are recorded here.
- [ ] #2 ANTIWIRE_MIGRATION.md and the parity drivers live in a tracked path on that branch, never in /tmp, and list every changed caller with the compatibility reason.
- [ ] #3 The migration guide in this repository (docs/) lists the source-level differences a consumer meets: Kotlin-typed members, okio-typed members, JdkSchemaLoader, unchecked exceptions, with before/after snippets.
- [ ] #4 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
