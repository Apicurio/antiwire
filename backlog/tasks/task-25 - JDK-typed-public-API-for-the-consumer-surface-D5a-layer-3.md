---
id: TASK-25
title: JDK-typed public API for the consumer surface (D5a layer 3)
status: To Do
assignee: []
created_date: '2026-10-01 07:30'
labels: []
milestone: m-2
dependencies:
  - TASK-12
priority: high
type: feature
ordinal: 24000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Per D5a: the API Apicurio and Kafka consume must be JDK-typed. Design and implement: SchemaLoader and schema loading on java.nio.file.Path (and a classpath-loading helper so Apicurio can drop its FakeFileSystem/setWorkingDirectory/setAllowSymlinks dance in ProtobufSchemaLoader); no okio.* types in consumer-facing signatures; byte[] or a wire-owned immutable bytes type at runtime boundaries where ByteString adds no value. okio-shaped types remain allowed internally (engine layer). Validation consumer: Apicurio's ProtobufSchemaLoader rewritten against the new API (feeds TASK-18/M4). Test deviations allowed per D5a: mechanical type substitutions only, non-mechanical deviations documented individually.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 SchemaLoader and public schema-loading entry points take java.nio.file.Path, no okio.* in their signatures
- [ ] #2 Classpath-loading helper exists so descriptor.proto and well-known protos load without FakeFileSystem
- [ ] #3 okio types absent from the consumer-facing API surface (wire-schema public entry points); internal use documented
- [ ] #4 Adapted upstream tests pass; every non-mechanical test edit documented
<!-- AC:END -->
