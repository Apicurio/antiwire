---
id: TASK-18
title: >-
  Integrate antiwire into Apicurio with documented migration and delta-aware
  parity
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-10
dependencies:
  - TASK-17
documentation:
  - docs/decisions.md
priority: high
ordinal: 18000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Prepare a separate Apicurio integration branch at a recorded commit using the port's approved local or authorized coordinates. Limited caller migration is approved, not a coordinate-only swap. Known sites are ProtobufSchemaLoader.java, ProtoContent.java, FileDescriptorUtils.java, ProtobufFile.java and the protobuf-schema-utilities POM; re-inventory before editing because this is an observed minimum, not an exhaustive future guarantee. Replace direct Kotlin/Okio usage per the M0 loader boundary and TASK-10 range contract. Preserve imports, bundled proto precedence, reserved/extension ranges and diagnostics. First record upstream 6.4.0 versus 7.1.0 differences, then compare antiwire with upstream 7.1.0. This task does not authorize merging or publishing Apicurio changes.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A migration record identifies the Apicurio base commit, every changed caller/dependency and its compatibility rationale, including in-memory loading, ranges and parser entry points.
- [ ] #2 Applicable protobuf tests pass for utils/protobuf-schema-utilities, schema-util/protobuf, schema-validation/protobuf, serdes/generic/serde-common-protobuf and the selected application compatibility cases.
- [ ] #3 The serde integration subset passes and covers the migrated schema-loading and descriptor-conversion paths, including reserved and extension range endpoints.
- [ ] #4 On an enumerated corpus, upstream 6.4.0 versus 7.1.0 differences are documented; antiwire results match upstream 7.1.0, including descriptor bytes where defined, and all Apicurio-visible upgrade differences receive explicit acceptance.
- [ ] #5 Resolved production protobuf classpaths contain no Kotlin, Kotlin-backed Okio or duplicate upstream/port classes; the limited migration and validation evidence are attached for review.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
