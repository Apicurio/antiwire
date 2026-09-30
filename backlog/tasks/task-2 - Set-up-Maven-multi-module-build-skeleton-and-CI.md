---
id: TASK-2
title: Set up Maven multi-module build skeleton and CI
status: To Do
assignee: []
created_date: '2026-09-29 09:22'
updated_date: '2026-09-30 02:03'
labels: []
milestone: m-6
dependencies:
  - TASK-1
documentation:
  - docs/decisions.md
priority: high
ordinal: 2000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create only the minimum Maven structure needed for M0 experiments and later modules. Provide runtime, schema and optional Java-generator module shells using the provisional layout from TASK-1. Build production Java with --release 11 on JDK 17 or later. Kotlin compiler plugins and libraries may be used for test compilation or build-time fixture generation, never as production dependencies. Establish a single verification entry point that later tasks extend, with upstream implementations isolated from port-under-test classpaths. TASK-5 supplies the representative adapted-test compilation proof; empty suites are not evidence of parity.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The runtime, schema and optional Java-generator module shells build production Java with --release 11 on JDK 17; no functionality or parity is claimed from an empty build.
- [ ] #2 CI runs the build and a shared verification entry point; the entry point reports which planned suites are active and cannot label absent suites as passed.
- [ ] #3 Production dependency checks reject Kotlin and Kotlin-backed transitives and require Java 11-compatible bytecode and API usage in the classes selected on Java 11, including multi-release jars. Published metadata excludes test/build tools.
- [ ] #4 The build supports Kotlin test compilation and pinned upstream fixture generation in isolated scopes, with an executable check of where port-under-test classes load from; the check fails when the same com.squareup.wire or retained okio class name resolves from two different artifacts, reporting both origins (duplicate-class prevention rules in docs/compatibility-matrix.md).
- [ ] #5 CI provisions an actual Java 11 JVM for consumer execution checks, separate from the JDK 17+ build toolchain. TASK-5 exercises the spike and TASK-21 checks the final published artifact set; empty module checks cannot certify runtime compatibility.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
