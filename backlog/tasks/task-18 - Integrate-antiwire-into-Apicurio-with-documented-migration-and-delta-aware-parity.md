---
id: TASK-18
title: >-
  Integrate antiwire into Apicurio with documented migration and delta-aware
  parity
status: Done
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
- [x] #1 A migration record identifies the Apicurio base commit, every changed caller/dependency and its compatibility rationale, including in-memory loading, ranges and parser entry points.
- [x] #2 Applicable protobuf tests pass for utils/protobuf-schema-utilities, schema-util/protobuf, schema-validation/protobuf, serdes/generic/serde-common-protobuf and the selected application compatibility cases.
- [x] #3 The serde integration subset passes and covers the migrated schema-loading and descriptor-conversion paths, including reserved and extension range endpoints.
- [x] #4 On an enumerated corpus, upstream 6.4.0 versus 7.1.0 differences are documented; antiwire results match upstream 7.1.0, including descriptor bytes where defined, and all Apicurio-visible upgrade differences receive explicit acceptance.
- [x] #5 Resolved production protobuf classpaths contain no Kotlin, Kotlin-backed Okio or duplicate upstream/port classes; the limited migration and validation evidence are attached for review.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Comments

#1 - 2026-10-02 09:55 (UTC)
Base recorded: Apicurio apicurio-registry cloned at 448f845c9791b960cec3f0bb9a491ac5cd90e785 (/tmp/apicurio, depth-50), wire-schema 6.4.0 pinned in the root pom (<wire-schema.version>), kotlin-stdlib 2.4.20 and okio 3.18.2 alongside. Inventory beyond the four named files (grep com.squareup.wire --include=*.java): utils/protobuf-schema-utilities (ProtobufFile, ProtobufSchemaLoader, FileDescriptorUtils, ProtoContent), schema-util/protobuf (ProtobufContentCanonicalizer, ProtobufReferenceFinder, ProtobufDereferencer, ProtobufStructuredContentExtractor, ProtobufCompatibilityCheckerLibrary, ProtobufContentValidator, ProtobufFqnConflictDetector), app (ProtobufTagExtractor, ccompat AbstractResource, SchemaFormatService), serdes/generic/serde-common-protobuf (+ its test). So the observed minimum was indeed not exhaustive: ~16 files across four modules. Plan: (1) record wire 6.4.0 vs 7.1.0 differences on an enumerated corpus (delta doc), (2) local-only integration branch in /tmp/apicurio (git checkout -b antiwire-integration; NO pushes to Apicurio, per this task's no-merge/no-publish rule), coordinates io.github.paoloantinori installed to the local ~/.m2 which the Apicurio build resolves, (3) migrate callers per the M0 loader boundary (JdkSchemaLoader where loading; keep imports/precedence/ranges/diagnostics), (4) run the AC#2/AC#3 test modules, (5) delta-aware parity per AC#4, gates, ledger. TASK-25's facade javadoc carries the migration sketch for ProtobufSchemaLoader.

## Final Summary

Executed on the local branch only (antiwire-integration at /tmp/apicurio, head a11d7cfaf8f97546689d12e9e9b9768fa0dd6a09, three commits, never pushed, no merge, no publish) cut from the recorded base 448f845c9791b960cec3f0bb9a491ac5cd90e785. Migration record ANTIWIRE_MIGRATION.md on the branch: wire 6.4.0->7.1.0 has exactly two behavior changes on Apicurio's surface (default-literal validation, accepted; CoreLoader's extra field_mask bundle, moot due to root precedence) and everything else formatting-only; antiwire differences recorded per site, with the load-bearing one - Schema.types() hash-order iteration - FOUND AND FIXED in the port (antiwire fb044bc: both Schema indexes insertion-ordered; verified in the installed jar's bytecode). 23 files migrated across utils/protobuf-schema-utilities, schema-util/protobuf, schema-validation/protobuf, app, serdes (four files beyond the observed minimum, per re-inventory); ProtobufSchemaLoader rewritten onto JdkSchemaLoader (no FakeFileSystem/setWorkingDirectory/setAllowSymlinks); behavior preserved for imports, bundled-proto precedence, reserved/extension ranges (5 to 9 -> 5..10 exclusive end, 100 to max -> 100..200 verified), and diagnostics. AC#5: dependency trees show only io.github.paoloantinori wire artifacts in production scope, zero kotlin/okio/squareup-wire entries; the pre-existing test-scoped dual-wire classpath (kafka-protobuf-serializer) existed on the base commit and is recorded. Tests: all four AC#2/AC#3 modules BUILD SUCCESS with counts identical to the base-commit baseline and zero failures (66 utils + 40 schema-util + 4 validation + 28 serdes). Parity: Oracle A 20/20 descriptor-bytes byte-identical after the port fix (re-run twice); Oracle B 49/49 canonical toSchema byte-identical vs upstream 7.1.0; drivers and outputs committed under antiwire-parity/. Gates: high-effort code review over the branch diff, 9 findings dispositioned (5 fixed: resource leak, dead catch, dead properties, guava pin to root, driver cleanup with reproduction re-run; 4 structural accepted-differences documented: per-parse disk staging with successor condition TASK-25, platform case-insensitivity, bundled-path collision truncation, and the port-commit ordering caveat now satisfied by fb044bc). Accepted differences that need maintainer awareness: read-only/exhausted-tmpdir staging fails where the in-memory baseline could not (documented; successor is the port-side in-memory FS).
