---
id: TASK-1
title: Finalize the compatibility contract and M0 architecture decisions
status: In Progress
assignee:
  - assistant
created_date: '2026-09-29 09:22'
updated_date: '2026-09-30 01:19'
labels: []
milestone: m-6
dependencies: []
references:
  - docs/research-wire-java-port-2026-09-29.md
documentation:
  - docs/decisions.md
  - docs/research-wire-java-port-2026-09-29.md
priority: high
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Use docs/decisions.md as the approved policy baseline, not a request to reopen settled product decisions. Target Wire 7.1.0 semantics, Java 11 bytecode, Maven on JDK 17+, and Apicurio first. Production artifacts and transitive dependencies contain no Kotlin; pure-Java dependencies are allowed with measured footprint. Kotlin is allowed for build and test tooling. Preserve functional and source compatibility except documented migration sites; do not promise precompiled Wire 6/7 ABI compatibility. Keep com.squareup.wire public names where feasible, with explicit exclusions and coexistence rules. The intended groupId is io.apicurio subject to authorization. Record initial module/API contracts here; TASK-4 selects the I/O route and TASK-5 validates it before M1. Kafka is deferred.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 docs/decisions.md records all agreed policies, the Wire 7.1.0 tag and resolved commit, release prerequisites, authorization boundaries and owners of the remaining M0 technical decisions.
- [ ] #2 A compatibility matrix inventories public Wire and Okio types, parser ranges, profile APIs, generated-code APIs and observed Apicurio call sites; every deliberate source change has a named task owner and migration requirement.
- [ ] #3 Fixed independent runtime and schema boundaries, optional Java-generator support and duplicate-class prevention rules are recorded. The publication grouping is explicitly provisional; TASK-4 finalizes it after experimentation and TASK-5 validates it before M1.
- [ ] #4 All technical assumptions still awaiting TASK-4 or TASK-5 evidence are marked provisional; no I/O choice, footprint figure or delivery estimate is presented as proven.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
