---
id: TASK-21
title: Publish 0.1.0 with attribution and docs
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-11
dependencies:
  - TASK-19
  - TASK-20
  - TASK-23
documentation:
  - docs/decisions.md
priority: high
ordinal: 21000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Publish the first release only after the approved compatibility, complete applicable test coverage, Apicurio integration, measured footprint/performance and demonstrated maintenance gates pass. Use intended groupId io.apicurio only after namespace/publication authorization is explicitly recorded; project planning is not authorization to publish or transfer. Include sources, javadoc, reproducible build instructions, pinned upstream provenance and a per-file license inventory covering headers and embedded notices. Preserve Google Nano BSD-style notices in the reader family and ProtoWriter, R8 notices, JetBrains notices, mixed Square Apache notices and notices for any vendored dependencies. Coordinate the final external publish action with the maintainer.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Explicit maintainer release approval and io.apicurio namespace/publication authorization are recorded before artifacts are published; sources, javadoc and POM metadata accompany a reproducible release build.
- [ ] #2 The final release candidate passes all applicable runtime/schema/protoc/compiler/profile/golden checks with no required case pending, and the recorded Apicurio integration evidence matches the same candidate.
- [ ] #3 Footprint/performance reports and their acceptance records identify the released candidate by build revision, artifact checksums and resolved dependency identities. Relevant changes require remeasurement and renewed acceptance; stale records block release. TASK-23's demonstrated synchronization procedure is available.
- [ ] #4 The candidate POMs and resolved consumer classpaths contain no Kotlin, Kotlin-backed production dependencies, leaked test/build tools or duplicate upstream/port classes. Production bytecode and API use selected on Java 11 satisfy that baseline, including multi-release dependencies.
- [ ] #5 License/header/in-file notice auditing covers the actual derived files and dependencies; README documents the source-migration contract, upstream pin, supported features and excluded functionality.
- [ ] #6 Using the final candidate artifacts and resolved production dependencies, actual Java 11 consumer runs exercise runtime encoding/decoding, schema parsing/loading and the optional Java generator. The same checks run against published artifacts before release completion; JDK 17-only runs cannot satisfy this gate.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
