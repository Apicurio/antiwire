---
id: TASK-20
title: Measure runtime and Apicurio schema performance before release
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-11
dependencies:
  - TASK-18
documentation:
  - docs/decisions.md
priority: medium
ordinal: 20000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Benchmark antiwire against upstream Wire 7.1.0 for runtime encoding/decoding and the schema operations Apicurio uses, including parsing, linking, loading and descriptor conversion. Start baseline capture during M0 where feasible; this task performs the reproducible comparison on the integrated port. Use upstream JMH cases and representative schema corpora. Include protobuf-java only where operations are genuinely comparable, not as a substitute oracle for Wire's schema model. Record environment, forks/warmup, input sizes, allocation and variance. Define numerical thresholds only after baseline measurements. Every regression requires explicit acceptance; documentation alone is not a waiver.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 docs/performance.md provides reproducible runtime and Apicurio schema-operation results for antiwire and upstream Wire 7.1.0, with environment, input sizes, commands, forks, warmup, allocations, variance and mappings to upstream JMH cases or justified schema workloads.
- [ ] #2 Baseline measurements justify the numerical thresholds or comparison criteria before the port is evaluated; results distinguish noise from measured regressions and compare protobuf-java only on equivalent operations.
- [ ] #3 Every measured regression has an explicit maintainer acceptance record or is resolved before release; missing or unaccepted results block TASK-21.
- [ ] #4 The benchmark runner and reports use isolated implementations and production-policy classpaths, and do not infer performance parity from algorithm similarity.
- [ ] #5 Results and regression acceptance identify the measured candidate by build revision, artifact checksums and resolved dependency identities. Relevant code, dependency, packaging or benchmark-workload changes require renewed measurements and acceptance before TASK-21; a previous candidate's records cannot approve the release.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
