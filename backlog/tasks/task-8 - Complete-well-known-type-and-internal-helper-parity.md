---
id: TASK-8
title: Complete well-known-type and internal-helper parity
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-7
dependencies:
  - TASK-6
documentation:
  - docs/decisions.md
priority: medium
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Complete well-known-type and helper behaviors not already implemented in TASK-6's compiling foundation. Use its symbol-ownership map, extending rather than redefining Duration, Instant, FieldMask, AnyMessage, OneOf, Internal and supporting types. Cover collections, copy-on-write behavior, numeric helpers, Java time mapping and generated Java code imports. Preserve JetBrains and R8 notices. This task is a behavioral completion step after the encoding foundation, not a prerequisite that leaves TASK-6 unable to compile.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All remaining applicable well-known-type and helper scenarios match upstream Wire 7.1.0, including Java time edge cases, equality, collection mutability and malformed values.
- [ ] #2 Internal APIs required by generated Java code compile and execute against the port, with any bounded namespace mapping recorded.
- [ ] #3 The ownership map accounts for every required type and helper exactly once across TASK-6 and this task; no foundational placeholder remains.
- [ ] #4 Relevant tests run in the shared CI entry point and preserve the production dependency and provenance policies.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
