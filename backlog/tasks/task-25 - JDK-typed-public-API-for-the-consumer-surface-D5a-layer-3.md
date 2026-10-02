---
id: TASK-25
title: JDK-typed public API for the consumer surface (D5a layer 3)
status: Done
assignee: []
created_date: '2026-10-01 07:30'
labels: []
milestone: m-8
dependencies:
  - TASK-12
priority: high
type: feature
ordinal: 24000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Per the reviewed scope and the merged execution ledger (the D5a stance is subsumed by DEC-2 plus the JDK-typed API decision recorded there): the API Apicurio and Kafka consume must be JDK-typed. Design and implement: SchemaLoader and schema loading on java.nio.file.Path (and a classpath-loading helper so Apicurio can drop its FakeFileSystem/setWorkingDirectory/setAllowSymlinks dance in ProtobufSchemaLoader); no okio.* types in consumer-facing signatures; byte[] or a wire-owned immutable bytes type at runtime boundaries where ByteString adds no value. okio-shaped types remain allowed internally (engine layer). Validation consumer: Apicurio's ProtobufSchemaLoader rewritten against the new API (feeds TASK-18/M4). Test deviations allowed per D5a: mechanical type substitutions only, non-mechanical deviations documented individually.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 SchemaLoader and public schema-loading entry points take java.nio.file.Path, no okio.* in their signatures
- [x] #2 Classpath-loading helper exists so descriptor.proto and well-known protos load without FakeFileSystem
- [x] #3 okio types absent from the consumer-facing API surface (wire-schema public entry points); internal use documented
- [x] #4 Adapted upstream tests pass; every non-mechanical test edit documented
<!-- AC:END -->

## Final Summary

Commit (this stretch): JdkSchemaLoader facade in com.squareup.wire.schema with zero okio.* in its public signatures (mechanically enforced by a reflection walk test), java.nio.file.Path and classpath entry points (dirs, single protos, zips/jars), the consumer knobs, AutoCloseable lifecycle, and SchemaException/IOException error surfacing. Root-cause fix in the vendored ResourceFileSystem: jars-only classpaths (uber-jar shape) report the classpath root as a directory instead of falling through to openZip and failing misleadingly - probe-verified end-to-end. Consumer/engine surface split documented in docs/loading-api-inventory.md with the Apicurio migration sketch in the facade javadoc (TASK-18 input). 9 validation tests; module 630 green; all 11 suites pass. Gates: /simplify and a 5-finding code-review ran inside the implementation flow, all findings addressed (jars-only defect, reflection scope, javadoc link, resource leaks, test-name overclaim replaced by a behavioral cycle test).
