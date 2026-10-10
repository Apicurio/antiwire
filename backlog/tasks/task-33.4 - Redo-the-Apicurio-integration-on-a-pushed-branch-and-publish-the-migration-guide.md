---
id: TASK-33.4
title: >-
  Redo the Apicurio integration on a pushed branch and publish the migration
  guide
status: To Do
assignee: []
created_date: '2026-10-09 20:54'
updated_date: '2026-10-09 23:13'
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-10 (result from the apicurio-registry agent on the Mac, verified by me through the GitHub API): branch antiwire-integration on paoloantinori/apicurio-registry at 00ce12ada, 6 commits on base 25c8b5ce0 (origin/main of Apicurio at 2026-10-09 16:57), 20 files, log in ANTIWIRE_MIGRATION.md; the branch does not exist on Apicurio/apicurio-registry (404), no PR opened. Built against antiwire 9d6423f. The four protobuf modules (utils/protobuf-schema-utilities, schema-util/protobuf, schema-validation/protobuf, serdes/generic/serde-common-protobuf) compile against io.apicurio:wire-schema-java and their tests match the Wire 6.4.0 baseline exactly: 141 tests, 0 failures, per-class counts identical (baseline run on the unmodified base commit in a separate worktree). App module: 129 tests run in the Confluent-touching suites, 3 errors, all one cause (Confluent kafka-schema-registry-client 8.0.0, ProtobufSchema.toProtoFile line 681, reads the Kotlin ProtoParser$Companion field at runtime: NoSuchFieldError). Classpath proof: only io.apicurio:wire-schema-java and wire-runtime-java at compile scope, zero Square or Kotlin artifacts in the four modules. integration-tests was compile-verified but not run (no Docker on the Mac). Open: the branch cannot build from a clean checkout because io.apicurio:wire-schema-java 0.1.0-SNAPSHOT exists only in the local ~/.m2 (nothing is published, by design). I have not run the branch myself, re-run the tests or reviewed the diff; the numbers are the agent's log, checked only for the branch and commit existing on the fork.

Findings for antiwire from that log (each gets an owner): (1) OneOf constructor is package-private; Apicurio bridges it with an OneOfFactory class in package com.squareup.wire.schema. Needed: a public constructor or factory (String name, String documentation, List<Field> fields, Location location, Options options): TASK-33.2 (the TASK-34 baseline lists OneOf.<init> as GAP-fixable). (2) MessageType.toElement() package-private: TASK-33.2. (3) ReservedElement.getValues()/ExtensionsElement.getValues() lack the javadoc that Reserved.getValues() has: docs only, TASK-33.2. (5) the Kotlin Companion is load-bearing for BINARY consumers, not only a source convenience: Confluent's compiled jar calls ProtoParser$Companion; this contradicts the 'binary compatibility is not promised' framing for this one class family and raises the priority of the Companion shims in TASK-33.2 (see the decision note on TASK-33.2). (6) no consumer-legal in-memory loading route: the facade offers only real files, so Apicurio stages into a temp directory per call (about 90 metadata syscalls per call on a cold tmpdir) and host filesystem semantics leak into schema resolution (macOS APFS is case-insensitive: an import with mismatched case resolves locally and fails on case-sensitive CI). Proposed: a JdkSchemaLoader factory over a caller-supplied java.nio.file.FileSystem (Jimfs restores the old FakeFileSystem semantics one for one): new task TASK-33.5.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
