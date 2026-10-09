---
id: TASK-18
title: >-
  Integrate antiwire into Apicurio with documented migration and delta-aware
  parity
status: Done
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-10-09 18:21'
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
- [x] #1 A migration record identifies the Apicurio base commit, every changed caller/dependency and its compatibility rationale, including in-memory loading, ranges and parser entry points.
- [x] #2 Applicable protobuf tests pass for utils/protobuf-schema-utilities, schema-util/protobuf, schema-validation/protobuf, serdes/generic/serde-common-protobuf and the selected application compatibility cases.
- [x] #3 The serde integration subset passes and covers the migrated schema-loading and descriptor-conversion paths, including reserved and extension range endpoints.
- [x] #4 On an enumerated corpus, upstream 6.4.0 versus 7.1.0 differences are documented; antiwire results match upstream 7.1.0, including descriptor bytes where defined, and all Apicurio-visible upgrade differences receive explicit acceptance.
- [x] #5 Resolved production protobuf classpaths contain no Kotlin, Kotlin-backed Okio or duplicate upstream/port classes; the limited migration and validation evidence are attached for review.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-06 audit note: the integration branch cited in the Final Summary (a11d7cfa, later 6ce5582c) is Mac-hosted and absent from this host's Apicurio clones and /tmp/apicurio (footprint.md 10.1 records the same). Same-candidate revalidation is owned by TASK-21.

2026-10-09 correction to the 2026-10-06 note above: the claim that the integration branch (a11d7cfa, later 6ce5582c) is Mac-hosted was taken from the repo's own records and is not supported. The apicurio-registry agent found neither commit as a valid object in the Apicurio clone on bird or on the Mac, no antiwire-integration branch in either, and no Apicurio pom that references antiwire; antiwire 0.1.0-SNAPSHOT artifacts are installed in bird's ~/.m2 (2026-10-06) but nothing resolves them. The migration work (ANTIWIRE_MIGRATION.md, 23 migrated files, antiwire-parity/ drivers) is therefore not located. See TASK-33.

2026-10-09 verified finding (supersedes the 'not located' wording of the earlier 2026-10-09 note): the integration branch did exist on the Mac. /tmp/apicurio on the Mac is the exact path the Final Summary names, and it is now an empty skeleton: 4058 directories, 0 regular files, .git/objects with 96 empty subdirectories, so a11d7cfa and 6ce5582c are gone. Directory timestamps are Oct 7-8, and the Mac was rebooted on 2026-10-02 16:51, after the 11:23 closing commit 63e98b7; footprint.md names macOS 26.7.1 as the measuring host. The most likely cause is the automatic cleanup of /tmp on macOS, which removes old files and leaves empty directories; I did not confirm the mechanism. Not on bird either: no clone there contains either commit, no ref or reflog or stash mentions antiwire, and no ANTIWIRE_MIGRATION.md or antiwire-parity file exists on bird. What survives as evidence: this Final Summary, footprint.md sections 4 and 10 (which cite the branch), and the port fix fb044bc in this repo (the insertion-ordered Schema indexes found while running Apicurio against the port). The 23-file migration, ANTIWIRE_MIGRATION.md and the antiwire-parity drivers and outputs must be redone (TASK-33 AC#1). Process lesson: the 2026-10-06 audit note called the branch 'Mac-hosted' without checking the Mac; it was one ssh command away.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Executed on the local branch only (antiwire-integration at /tmp/apicurio, head a11d7cfaf8f97546689d12e9e9b9768fa0dd6a09, three commits, never pushed, no merge, no publish) cut from the recorded base 448f845c9791b960cec3f0bb9a491ac5cd90e785. Migration record ANTIWIRE_MIGRATION.md on the branch: wire 6.4.0->7.1.0 has exactly two behavior changes on Apicurio's surface (default-literal validation, accepted; CoreLoader's extra field_mask bundle, moot due to root precedence) and everything else formatting-only; antiwire differences recorded per site, with the load-bearing one - Schema.types() hash-order iteration - FOUND AND FIXED in the port (antiwire fb044bc: both Schema indexes insertion-ordered; verified in the installed jar's bytecode). 23 files migrated across utils/protobuf-schema-utilities, schema-util/protobuf, schema-validation/protobuf, app, serdes (four files beyond the observed minimum, per re-inventory); ProtobufSchemaLoader rewritten onto JdkSchemaLoader (no FakeFileSystem/setWorkingDirectory/setAllowSymlinks); behavior preserved for imports, bundled-proto precedence, reserved/extension ranges (5 to 9 -> 5..10 exclusive end, 100 to max -> 100..200 verified), and diagnostics. AC#5: dependency trees show only io.github.paoloantinori wire artifacts in production scope, zero kotlin/okio/squareup-wire entries; the pre-existing test-scoped dual-wire classpath (kafka-protobuf-serializer) existed on the base commit and is recorded. Tests: all four AC#2/AC#3 modules BUILD SUCCESS with counts identical to the base-commit baseline and zero failures (66 utils + 40 schema-util + 4 validation + 28 serdes). Parity: Oracle A 20/20 descriptor-bytes byte-identical after the port fix (re-run twice); Oracle B 49/49 canonical toSchema byte-identical vs upstream 7.1.0; drivers and outputs committed under antiwire-parity/. Gates: high-effort code review over the branch diff, 9 findings dispositioned (5 fixed: resource leak, dead catch, dead properties, guava pin to root, driver cleanup with reproduction re-run; 4 structural accepted-differences documented: per-parse disk staging with successor condition TASK-25, platform case-insensitivity, bundled-path collision truncation, and the port-commit ordering caveat now satisfied by fb044bc). Accepted differences that need maintainer awareness: read-only/exhausted-tmpdir staging fails where the in-memory baseline could not (documented; successor is the port-side in-memory FS).
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
