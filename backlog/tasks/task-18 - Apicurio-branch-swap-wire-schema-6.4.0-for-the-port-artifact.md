---
id: TASK-18
title: 'Apicurio branch: swap wire-schema 6.4.0 for the port artifact'
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-4
dependencies:
  - TASK-14
  - TASK-15
priority: high
type: task
ordinal: 18000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Branch of Apicurio/apicurio-registry replacing com.squareup.wire:wire-schema (+jvm) with the port coordinates. Same packages, so only the dependency changes. Run protobuf-carrying module tests: utils/protobuf-schema-utilities, schema-util/protobuf, schema-validation/protobuf, serdes/generic/serde-common-protobuf, plus a ccompat subset.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Apicurio protobuf module tests green on the branch
- [ ] #2 Serde integration test subset green
- [ ] #3 No Kotlin artifacts remain on the Apicurio protobuf classpath
<!-- AC:END -->
