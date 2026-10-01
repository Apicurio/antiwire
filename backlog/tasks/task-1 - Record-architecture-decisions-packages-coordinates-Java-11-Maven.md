---
id: TASK-1
title: 'Record architecture decisions: packages, coordinates, Java 11, Maven'
status: Done
assignee: []
created_date: '2026-09-29 09:22'
updated_date: '2026-10-01 06:45'
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
- [x] #1 decisions.md exists and covers packages, groupId, Java baseline, build tool, poet deferral, gson timing
- [x] #2 Each decision cites its rationale from the research report
<!-- AC:END -->

## Comments

#1 - 2026-10-01 06:45 (UTC)
Done 2026-10-01, commit 26f40a1 plus review fixes. docs/decisions.md D1-D8 written; D5 expanded after the M0 okio inventory (FileSystem/Path layer vendored too); gzip omission recorded in D5 after the vendoring spike. Gates: /simplify (4 agents) and code-review ran this session on the M0 diff; documentation-only change. Status updated by direct file edit because the backlog MCP reported an ambiguous task-1 id (phantom duplicate candidates; only one task-1 file exists on disk).
