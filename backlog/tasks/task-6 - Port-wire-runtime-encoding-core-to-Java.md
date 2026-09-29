---
id: TASK-6
title: Port wire-runtime encoding core to Java
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-1
dependencies:
  - TASK-4
  - TASK-5
priority: high
type: feature
ordinal: 6000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Translate ProtoReader, ProtoReader32, ByteArrayProtoReader32, ProtoWriter, ReverseProtoWriter, FieldEncoding, Syntax, adapters (ProtoAdapter and friends) from commonMain onto the vendored okio subset. Preserve Google headers.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All wire-runtime commonMain encoding classes compile in Java with byte-exact behavior
- [ ] #2 ReverseProtoWriter included (required by generated Java code from Wire 6/7)
<!-- AC:END -->
