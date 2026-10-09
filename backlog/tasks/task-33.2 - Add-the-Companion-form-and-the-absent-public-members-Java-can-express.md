---
id: TASK-33.2
title: Add the Companion form and the absent public members Java can express
status: To Do
assignee: []
created_date: '2026-10-09 20:54'
labels:
  - user-feedback
  - compatibility
  - api
milestone: m-11
dependencies:
  - TASK-34
parent_task_id: TASK-33
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Maintainer decision 2026-10-09: close the source-compatibility gaps found by TASK-34 for members Java can express: add the Companion form (nested class plus static field) on all 45 upstream classes, not only those Apicurio uses, because the library must serve every consumer; add the absent constructors, methods, statics and the two public classes EmptyWireLogger and EmptyWireLoggerFactory. Binary compatibility is not a goal (DEC-2); Kotlin-typed (DEC-4) and okio-typed (DEC-14) members stay out and are listed with reasons. Depends on TASK-34 (which produces the gap list).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Every public upstream Companion (45 classes) exists as a public static final nested class Companion with a public static final field Companion on the outer class, and each Companion method that Java can express is callable as X.Companion.m(...) with the upstream parameter types, delegating to the existing static.
- [ ] #2 Absent constructors, methods, statics and public classes that the TASK-34 check lists as GAP-fixable are added (EmptyWireLogger, EmptyWireLoggerFactory, ProtoParser constructor, OneOf constructor, MessageType.toElement, Schema.protoFile(Path), WireCompiler.forArgs, EnumType/MessageType.fromElement, etc.).
- [ ] #3 Each remaining difference is in the TASK-34 baseline with its reason (Kotlin type, okio type, Kotlin internal, deliberate); scripts/verify.sh passes with the check active.
- [ ] #4 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
