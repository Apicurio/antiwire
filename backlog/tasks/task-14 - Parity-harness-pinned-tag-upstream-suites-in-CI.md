---
id: TASK-14
title: 'Parity harness: pinned-tag upstream suites in CI'
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-2
dependencies:
  - TASK-9
  - TASK-13
priority: high
type: task
ordinal: 14000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Instrument that makes 'same test suite' auditable: script re-extracts upstream test sources at pinned tag from square/wire, runs them against the port artifacts, and fails on drift. Wire into CI.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Single command re-runs the full parity suite from the pinned upstream tag
- [ ] #2 CI job runs it on every change and blocks merge on failure
- [ ] #3 Pinned tag recorded in repo (7.1.0) with bump procedure
<!-- AC:END -->
