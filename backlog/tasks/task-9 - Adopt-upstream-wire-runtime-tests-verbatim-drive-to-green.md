---
id: TASK-9
title: Adopt upstream wire-runtime tests verbatim; drive to green
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-1
dependencies:
  - TASK-6
  - TASK-7
  - TASK-8
priority: high
type: task
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Copy wire-runtime commonTest (JVM-runnable) and jvmTest from square/wire at the pinned tag 7.1.0 into the port test scope, unmodified. Kotlin allowed in test scope only. Fix port bugs, never tests, unless upstream test uses a Kotlin feature that cannot see Java code (document any such deviation).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All upstream wire-runtime JVM tests pass against the Java artifacts, zero modifications
- [ ] #2 Any test deviation is individually documented with justification
- [ ] #3 Includes security regression: negative-length group skip (GHSA-7xpr-hc2w-34m9)
<!-- AC:END -->
