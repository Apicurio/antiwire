---
id: TASK-16
title: Port wire-compiler CLI and wire-java-generator; golden files identical
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-3
dependencies:
  - TASK-13
  - TASK-12
priority: medium
type: feature
ordinal: 16000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
wire-java-generator is already 3.7k LOC Java; port its 254 LOC Kotlin remainder plus wire-compiler CLI. javapoet (palantir, pure Java) allowed only in this artifact. Validate against wire-golden-files corpus.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Compiler regenerates the wire-golden-files Java corpus byte-identical to upstream
- [ ] #2 Generator artifact is separate from core; core keeps zero non-JDK deps
<!-- AC:END -->
