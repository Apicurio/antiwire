---
id: TASK-33.4
title: >-
  Redo the Apicurio integration on a pushed branch and publish the migration
  guide
status: Done
assignee: []
created_date: '2026-10-09 20:54'
updated_date: '2026-10-10 12:31'
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
- [x] #1 A persistent, pushed branch of the Apicurio code at apicurio-registry 3620f08c (or the current origin/main) builds the four protobuf modules against the antiwire jars (io.apicurio coordinates) with Wire removed, and the module tests pass; the branch and its URL are recorded here.
- [x] #2 ANTIWIRE_MIGRATION.md and the parity drivers live in a tracked path on that branch, never in /tmp, and list every changed caller with the compatibility reason.
- [x] #3 The migration guide in this repository (docs/) lists the source-level differences a consumer meets: Kotlin-typed members, okio-typed members, JdkSchemaLoader, unchecked exceptions, with before/after snippets.
- [x] #4 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-10 (result from the apicurio-registry agent on the Mac, verified by me through the GitHub API): branch antiwire-integration on paoloantinori/apicurio-registry at 00ce12ada, 6 commits on base 25c8b5ce0 (origin/main of Apicurio at 2026-10-09 16:57), 20 files, log in ANTIWIRE_MIGRATION.md; the branch does not exist on Apicurio/apicurio-registry (404), no PR opened. Built against antiwire 9d6423f. The four protobuf modules (utils/protobuf-schema-utilities, schema-util/protobuf, schema-validation/protobuf, serdes/generic/serde-common-protobuf) compile against io.apicurio:wire-schema-java and their tests match the Wire 6.4.0 baseline exactly: 141 tests, 0 failures, per-class counts identical (baseline run on the unmodified base commit in a separate worktree). App module: 129 tests run in the Confluent-touching suites, 3 errors, all one cause (Confluent kafka-schema-registry-client 8.0.0, ProtobufSchema.toProtoFile line 681, reads the Kotlin ProtoParser$Companion field at runtime: NoSuchFieldError). Classpath proof: only io.apicurio:wire-schema-java and wire-runtime-java at compile scope, zero Square or Kotlin artifacts in the four modules. integration-tests was compile-verified but not run (no Docker on the Mac). Open: the branch cannot build from a clean checkout because io.apicurio:wire-schema-java 0.1.0-SNAPSHOT exists only in the local ~/.m2 (nothing is published, by design). I have not run the branch myself, re-run the tests or reviewed the diff; the numbers are the agent's log, checked only for the branch and commit existing on the fork.

Findings for antiwire from that log (each gets an owner): (1) OneOf constructor is package-private; Apicurio bridges it with an OneOfFactory class in package com.squareup.wire.schema. Needed: a public constructor or factory (String name, String documentation, List<Field> fields, Location location, Options options): TASK-33.2 (the TASK-34 baseline lists OneOf.<init> as GAP-fixable). (2) MessageType.toElement() package-private: TASK-33.2. (3) ReservedElement.getValues()/ExtensionsElement.getValues() lack the javadoc that Reserved.getValues() has: docs only, TASK-33.2. (5) the Kotlin Companion is load-bearing for BINARY consumers, not only a source convenience: Confluent's compiled jar calls ProtoParser$Companion; this contradicts the 'binary compatibility is not promised' framing for this one class family and raises the priority of the Companion shims in TASK-33.2 (see the decision note on TASK-33.2). (6) no consumer-legal in-memory loading route: the facade offers only real files, so Apicurio stages into a temp directory per call (about 90 metadata syscalls per call on a cold tmpdir) and host filesystem semantics leak into schema resolution (macOS APFS is case-insensitive: an import with mismatched case resolves locally and fails on case-sensitive CI). Proposed: a JdkSchemaLoader factory over a caller-supplied java.nio.file.FileSystem (Jimfs restores the old FakeFileSystem semantics one for one): new task TASK-33.5.

2026-10-10 rerun against antiwire 98e1460 (apicurio-registry agent on the Mac, report; the branch and its commits verified by me through the GitHub API, test results NOT yet independently reproduced): branch antiwire-integration on paoloantinori/apicurio-registry at 12c307173 (fork only, no PR, not on origin). Reported: ProtobufSerdeTest.testSerdeMix [1] and [2] and ConfluentClientTest.testSerdeProtobufSchema now PASS (they used the Companion path); app regression set 129/129 (was 126/129); four protobuf modules 142 green; no remaining exception. The OneOfFactory bridge was removed in 5c879c506 (separate commit) now that the OneOf constructor is public. The agent confirms by a plain probe on the branch classpath that Confluent ProtobufSchema.canonicalString() throws IllegalArgumentException for 'reserved 5 to 9;' (ProtobufSchemaUtils.java:726) and for 'extensions 100 to 199;' (:761), while 'reserved 4;' and proto3 print fine; the three tests do not exercise ranges (proto3, one message), so they pass; no suite covers ranges; filed on the Apicurio side as REG-447. This matches my own measurement on bird and is the documented DEC-15 limit. I am re-running the build and the three tests on a clean clone of the fork with a separate Maven repository built from antiwire 98e1460 as an independent check.

2026-10-10 independent reproduction by me (bird, JDK 21, clean clone of paoloantinori/apicurio-registry at 12c307173, Maven repository /tmp/m2-98 built from antiwire 98e1460, nothing from the Mac): ./mvnw -pl app test -Dtest=ProtobufSerdeTest,ConfluentClientTest -> ConfluentClientTest 65 tests, ProtobufSerdeTest 12 tests, 77 run, 0 failures, 0 errors, BUILD SUCCESS. Earlier in the night I also reran the four protobuf modules on a clean clone at 4d25079 with antiwire 9d6423f: 67 + 43 + 4 + 28 = 142 tests, 0 failures, no Square, Kotlin or okio artifact in the dependency tree of protobuf-schema-utilities. Not reproduced by me: the full 129-test app regression set, the Wire 6.4.0 baseline comparison, integration-tests (no Docker). Remaining before AC#3: the migration guide in docs/ (TASK-33.4 AC#3), and the proto2 range limit stays documented as DEC-15 (REG-447 on the Apicurio side).

2026-10-10 rerun against antiwire 62c8646 (TASK-33.3, unchecked exceptions) by the apicurio-registry agent on the Mac; branch antiwire-integration at 2e5158fed on the fork (verified by me through the GitHub API). Reported: no 'exception IOException is never thrown' compile error and no call site or test changed (docs-only commit); four protobuf modules 142/142, app regression set 129/129, checkstyle clean, integration-tests test-compiles; the Confluent range gap re-probed unchanged (DEC-15). Why no breakage: every remaining IOException handler on the branch guards a call that still throws it (JdkSchemaLoader.initRoots/loadSchema/close/forClasspath, java.nio Files, Guava MoreFiles, protobuf writeTo); the antiwire calls that 33.3 touched (ProtoParser.parse, element constructors, getters) never declared IOException. My check: JdkSchemaLoader keeps `throws IOException` on forClasspath, initRoots, loadSchema and close (javap of the built class). It is an antiwire-only class with no Wire counterpart, so the TASK-33.3 rule (match upstream's throws) does not apply to it; Wire's own SchemaLoader.initRoots and loadSchema declare no throws and are covered by the surface baseline. Open question for the maintainer: make JdkSchemaLoader consistent with the unchecked policy (UncheckedIOException, no throws) or keep the checked exception on a class that does real file I/O; not decided, no task filed yet beyond TASK-33.5, which touches the same class.

Remaining for TASK-33.4: AC#3, the migration guide in docs/ of this repository (Kotlin-typed members, okio-typed members, JdkSchemaLoader and its staging cost, unchecked exceptions, the IntRange limit DEC-15), written from ANTIWIRE_MIGRATION.md on the branch.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Branch antiwire-integration on paoloantinori/apicurio-registry (fork only, no PR, not on origin) at 2e5158fed, cut from apicurio-registry origin/main 25c8b5ce0 (includes the user's 3620f08c), built against antiwire 62c8646. ANTIWIRE_MIGRATION.md is tracked on the branch with every changed caller and its reason. Evidence: the four protobuf modules' existing tests pass (142), reproduced by me on a clean clone (against 9d6423f; the agent reran them on 98e1460 and 62c8646); Confluent client/serde test classes 77 tests pass, reproduced by me on a clean clone with a separate Maven repository built from 98e1460; the 129-test app regression set and the Wire 6.4.0 baseline comparison are the agent's reports, not reproduced by me; integration-tests only compile-verified (no Docker); no Square, Kotlin or okio artifact in the dependency tree of protobuf-schema-utilities. Migration guide: docs/migration-from-wire.md (coordinates, Kotlin and okio typed members, Bytes, int[] ranges and the DEC-15 Confluent limit, JdkSchemaLoader and its staging cost, unchecked exceptions, how to check). Gates: /code-review high ran on the guide (7 points, all addressed); /simplify ran on the guide (structure: Bytes into its own section, one status paragraph, duplicated Companion facts removed). Open items filed elsewhere: TASK-33.5 (in-memory loading), the JdkSchemaLoader checked-versus-unchecked decision, REG-447 on the Apicurio side (range coverage).
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
