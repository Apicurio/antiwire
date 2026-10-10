---
id: TASK-33.5
title: >-
  Offer an in-memory loading route for consumers (JdkSchemaLoader over a
  caller-supplied java.nio FileSystem)
status: To Do
assignee: []
created_date: '2026-10-09 23:13'
labels:
  - user-feedback
  - compatibility
  - api
milestone: m-11
dependencies: []
parent_task_id: TASK-33
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Finding 6 of the Apicurio integration (branch antiwire-integration, ANTIWIRE_MIGRATION.md): Apicurio's old ProtobufSchemaLoader used an in-memory okio FakeFileSystem; the only consumer-legal replacement (JdkSchemaLoader) works on real files, so each schema operation stages content into a per-call temp directory (about 90 metadata syscalls per call on a cold tmpdir) and host filesystem semantics leak into schema resolution: on a case-insensitive host (macOS APFS) an import with mismatched case resolves locally and fails on a case-sensitive CI or production host. An engine SchemaLoader constructor over a java.nio FileSystem exists but loading-api-inventory scopes consumers to JdkSchemaLoader and DEC-14 keeps okio out of the consumer API. Proposed by the Apicurio agent: a JdkSchemaLoader factory over a caller-supplied java.nio.file.FileSystem (Jimfs restores the old semantics one for one) or a documented blessed in-memory route. Decide and implement; the Apicurio branch then drops the staging code.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 JdkSchemaLoader has a factory or setter that accepts a caller-supplied java.nio.file.FileSystem (for example Jimfs) as the root of the source path, so a consumer can load schemas held in memory without touching the host filesystem.
- [ ] #2 A test loads a schema and its imports from an in-memory java.nio FileSystem and checks case-sensitive resolution independent of the host filesystem.
- [ ] #3 The API is documented in docs/loading-api-inventory.md and docs/api-surface.md; ConsumerApiSurfaceTest or JdkSchemaLoaderValidationTest keeps okio out of the public signature (DEC-14).
- [ ] #4 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
