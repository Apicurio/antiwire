---
id: TASK-3
title: Write Kotlin-to-Java translation conventions doc
status: To Do
assignee: []
created_date: '2026-09-29 09:22'
labels: []
milestone: m-0
dependencies: []
priority: high
type: docs
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The parity contract for all translation work. Semantics that upstream tests assert must survive translation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Documents exception parity: kotlin require -> IllegalArgumentException, checkNotNull -> IllegalStateException (not NPE)
- [ ] #2 Documents equals/hashCode/toString parity requirements (data-class and List equality)
- [ ] #3 Documents Java 11 constraints: no sealed, records, or var in main sources
- [ ] #4 Requires preservation of Google (ProtoReader family, MathMethods) and JetBrains (ArrayList files) Apache-2.0 headers
<!-- AC:END -->
