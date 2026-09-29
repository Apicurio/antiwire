---
id: TASK-19
title: Footprint and dependency before/after report
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-4
dependencies:
  - TASK-18
priority: medium
type: docs
ordinal: 19000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Measure what the port saves: dependency tree, jar count and bytes, on a minimal consumer and on apicurio-registry. Baseline: wire-schema 6.4.0 chain (wire-schema-jvm 0.5MB, wire-runtime-jvm 0.3MB, okio-jvm 0.4MB, kotlin-stdlib 1.7MB, kotlinpoet, guava).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Report with dependency-tree diff and size table, checked into claudedocs/
- [ ] #2 Numbers verified by actually resolving artifacts (mvn dependency:tree), not estimated
<!-- AC:END -->
