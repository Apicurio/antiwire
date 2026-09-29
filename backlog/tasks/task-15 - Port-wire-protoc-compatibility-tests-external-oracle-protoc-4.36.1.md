---
id: TASK-15
title: 'Port wire-protoc-compatibility-tests (external oracle, protoc 4.36.1)'
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-3
dependencies:
  - TASK-9
  - TASK-6
priority: high
type: task
ordinal: 15000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The external suite: interop against protoc-generated Java code (Proto2 + Proto3 compatibility tests, roundtrips, InteropChecker, struct/duration/fieldmask/instant roundtrips). Pin protoc to 4.36.1 exactly as upstream.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All protoc-compatibility tests pass against port artifacts with protoc 4.36.1
- [ ] #2 Test sources verbatim from upstream tag
<!-- AC:END -->
