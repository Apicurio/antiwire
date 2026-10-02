---
id: TASK-22
title: Define a deferred Kafka use case and validate an adoption proposal
status: In Progress
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-11
dependencies:
  - TASK-21
documentation:
  - docs/decisions.md
priority: medium
ordinal: 22000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Kafka is a later adoption target and does not block the Apicurio-first 0.1.0 release. Before implementation, agree one concrete Kafka scenario, its target module, users and acceptance evidence with the maintainer. Then prepare a bounded POC and a community-facing motivation document. Recheck Kafka's actual Java/dependency requirements at the chosen commit instead of treating an inferred dependency policy or KIP acceptance as fact. Use the released port with its no-Kotlin production policy and measured, justified Java dependencies. Do not assume authorization to post proposals or modify upstream Kafka.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A named Kafka use case and target module have maintainer-approved scope and end-to-end acceptance criteria before POC implementation begins.
- [ ] #2 The POC compiles and runs at the Java baseline required by the pinned Kafka target; client use retains the project's Java 11 compatibility requirement.
- [ ] #3 The resolved POC production classpath is recorded and contains no Kotlin or Kotlin-backed dependencies introduced by the port; retained Java dependencies and footprint are justified.
- [ ] #4 An English adoption document describes motivation, measured results, licensing/provenance, AI-assisted translation methodology, compatibility and exclusions without claiming Kafka community approval or an unverified written policy.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
