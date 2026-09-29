---
id: TASK-12
title: Port SchemaEncoder; remove guava and poet usage; java.nio.file loading
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-2
dependencies:
  - TASK-11
priority: high
type: feature
ordinal: 12000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Translate SchemaEncoder (schema self-encoding via ProtoWriter). Replace Multimap.kt guava usage with java.util collections. Move AdapterConstant/Profile poet references out of core into the generator artifact (M3). Replace okio FileSystem source loading with java.nio.file behind a small interface so tests can use in-memory FS (jimfs is pure Java and stays test-scoped).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 SchemaEncoder output byte-identical to upstream on the test corpus
- [ ] #2 wire-schema-java has zero non-JDK compile dependencies
- [ ] #3 Source loading via java.nio.file with injectable FileSystem for tests
<!-- AC:END -->
