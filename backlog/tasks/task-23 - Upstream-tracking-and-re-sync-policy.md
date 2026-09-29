---
id: TASK-23
title: Upstream tracking and re-sync policy
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-5
dependencies:
  - TASK-17
priority: medium
type: docs
ordinal: 23000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Wire releases security fixes (two GHSAs in 2026); define how the port follows: tag pinning, changelog watch, re-sync procedure (extract diff, translate, re-run parity suite), and a policy for CVE response SLA.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 docs/upstream-sync.md with the procedure and a worked example for the first post-7.1.0 release
- [ ] #2 Changelog-watch automation or manual checklist defined
<!-- AC:END -->
