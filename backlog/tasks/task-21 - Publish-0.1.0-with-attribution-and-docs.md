---
id: TASK-21
title: Publish 0.1.0 with attribution and docs
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-5
dependencies:
  - TASK-16
  - TASK-17
  - TASK-18
priority: high
type: chore
ordinal: 21000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Maven Central release: sources, javadoc, POM metadata, NOTICE carrying Square, Google (ProtoReader family), and JetBrains (ArrayList) attribution; README stating it is a community pure-Java port of square/wire at tag 7.1.0.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Artifacts on Maven Central under the chosen groupId, reproducible build
- [ ] #2 NOTICE and headers audited against upstream attribution
- [ ] #3 README documents scope: proto2/proto3, no editions, no gRPC client, no Kotlin/Swift codegen
<!-- AC:END -->
