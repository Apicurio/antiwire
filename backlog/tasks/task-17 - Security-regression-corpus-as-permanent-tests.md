---
id: TASK-17
title: Security regression corpus as permanent tests
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-3
dependencies:
  - TASK-9
  - TASK-13
priority: high
type: task
ordinal: 17000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Encode 2026 security and semantics changes as permanent, labeled tests: GHSA-7xpr-hc2w-34m9 (negative-length group skip), 7.1.0 JSON null-element rejection, 6.4.x duplicate singular message merge (#3652/#3656), java_package option validation (7.0.3).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Each listed change has a named regression test that fails if reverted
- [ ] #2 Tests documented in SECURITY-like notes for future syncs
<!-- AC:END -->
