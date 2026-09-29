---
id: TASK-20
title: JMH benchmarks ported; performance budget gate
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-5
dependencies:
  - TASK-9
priority: medium
type: task
ordinal: 20000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Port wire-benchmarks (AllTypesBenchmark, SimpleMessageBenchmark) to run against port, upstream wire-jvm 7.1.0, and protobuf-java. Define an agreed regression budget and gate.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Benchmark results published for port vs upstream vs protobuf-java
- [ ] #2 Port within the agreed budget of upstream on all benchmarks; regressions documented if any
<!-- AC:END -->
