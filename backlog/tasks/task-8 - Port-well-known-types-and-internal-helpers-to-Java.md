---
id: TASK-8
title: Port well-known types and internal helpers to Java
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-1
dependencies:
  - TASK-4
priority: medium
type: feature
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
AnyMessage, Duration, Instant, FieldMask, OneOf, internal list types (JetBrains headers), camelCase, Util, Serializable marker, MathMethods, MutableOnWriteList.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Well-known types behave identically incl. java.time mapping on JVM
- [ ] #2 internal/Internal.java surface matches what generated Java code imports
<!-- AC:END -->
