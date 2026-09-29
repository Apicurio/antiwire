---
id: TASK-5
title: 'Spike: ProtoReader/ProtoWriter byte-exact parity vs upstream'
status: To Do
assignee: []
created_date: '2026-09-29 09:22'
labels: []
milestone: m-0
dependencies:
  - TASK-1
priority: high
type: spike
ordinal: 5000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Prove the encoding core can be translated with byte-exact behavior before any scaffolding.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Java translation roundtrips the wire-tests proto corpus with byte-for-byte equality against wire-runtime-jvm 7.1.0
- [ ] #2 Varint, tag, group, packed, and negative-length group-skip (GHSA-7xpr-hc2w-34m9) cases covered by tests
<!-- AC:END -->
