---
id: TASK-21
title: Publish 0.1.0 with attribution and docs
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-10-06 08:54'
labels: []
milestone: m-11
dependencies:
  - TASK-19
  - TASK-20
  - TASK-23
  - TASK-14.1
  - TASK-14.2
  - TASK-16.1
  - TASK-16.2
  - TASK-21.1
references:
  - 'https://github.com/Apicurio/antiwire/issues/3'
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
The 2026-10-06 adversarial delivery audit adds explicit prerequisite links for verified gaps in the existing release contract. TASK-14.1 restores clean-checkout verification, TASK-14.2 prevents unrecorded skipped cases from satisfying completeness, TASK-16.1 fixes generated Empty models, and TASK-21.1 restores candidate-builder operation after gate resolutions. These links do not authorize publication or replace the final-candidate freshness and maintainer-approval criteria. Historical measurement acceptances remain valid only for their recorded candidates.

The evidence audit identified a concrete renewal item under existing AC#5: docs/license-inventory.md was audited before Bytes.java was added and omits that shipped file. Bytes.java already has a source header; this is an incomplete inventory, not an observed missing-license header. Re-inventory the actual final candidate sources and resources, including Bytes.java, before release. Existing AC#2 also owns same-candidate Apicurio revalidation; its historical migration evidence is not a fresh audit execution on this host.

TASK-16.2 owns the two ProtoTarget cases and their scope disposition. Release must not infer a new exclusion from their disabled annotations. Either implement the retained scope or record an explicit maintainer decision, then reconcile execution coverage.

Historical release-groundwork comment recovered verbatim from commit 295ef5f0e7a1e45937417b9f621044d8139df6f5, backlog/tasks/task-21 - Publish-0.1.0-with-attribution-and-docs.md. This records the state on its original date, not the current approval state. The Backlog MCP rewrite omitted the original Comments section; the content is preserved here in the supported notes field.

created: 2026-10-02 17:50

Mechanical release groundwork landed (no publish, no tag, no deploy): docs/license-inventory.md (per-file notice audit, all 159 production sources plus 9 resources, zero missing notices, three working assumptions corrected: IntArrayList family is JetBrains not R8, ProtoReader32AsProtoReader carries the antiwire header not a Nano notice, okio/Base64 carries the ASF header it vendored with; open discrepancy flagged: NOTICE attributes the ProtoReader family and MathMethods to Google under Apache 2.0 while the files carry BSD-style notices, and DEC-12 says NOTICE carries the BSD texts verbatim); README release-contract section; scripts/release-build.sh producing target/release/ (9 jars + MANIFEST.sha256) and the generated docs/release-candidate.md identifying candidate a45b0f0 with the two open gates (footprint signature with the guava pin observation, encodeForward) auto-filled from the live acceptance rows plus the DEC-13 freshness caveat; pom.xml header comment corrected for the resolved DEC-8 (comment-only, effective model identical). verify.sh on JDK 17.0.12: all 12 ACTIVE suites PASS. No AC ticked: publication approval, final-candidate freshness, and the Java 11 published-artifact smoke remain.

GitHub tracking issue: https://github.com/Apicurio/antiwire/issues/3
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
