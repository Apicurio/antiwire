---
id: TASK-13
title: Adopt upstream wire-schema tests verbatim; drive to green
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-2
dependencies:
  - TASK-10
  - TASK-11
  - TASK-12
priority: high
type: task
ordinal: 13000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Copy wire-schema commonTest and jvmTest from tag 7.1.0 unmodified (incl. wire-schema-tests and wire-test-utils support modules, kept Kotlin in test scope).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All upstream wire-schema JVM tests pass, zero modifications
- [ ] #2 Parser golden expectations (multi-file, imports, options, proto3) all green
<!-- AC:END -->
