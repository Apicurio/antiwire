---
id: TASK-1
title: 'Record architecture decisions: packages, coordinates, Java 11, Maven'
status: To Do
assignee: []
created_date: '2026-09-29 09:22'
labels: []
milestone: m-0
dependencies: []
references:
  - docs/research-wire-java-port-2026-09-29.md
priority: high
type: task
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Decide and write decisions.md: keep Java packages com.squareup.wire.* (drop-in, upstream tests run unmodified); publish under new Maven groupId; Java 11 bytecode built on JDK 17+ (--release 11); Maven build, Kotlin only in test scope; kotlinpoet/javapoet deferred out of core into the generator artifact; gson-support deferred. Rationale in the research report.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 decisions.md exists and covers packages, groupId, Java baseline, build tool, poet deferral, gson timing
- [ ] #2 Each decision cites its rationale from the research report
<!-- AC:END -->
