---
id: TASK-17
title: Security regression corpus as permanent tests
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-9
dependencies:
  - TASK-15
  - TASK-16
documentation:
  - docs/decisions.md
priority: high
ordinal: 17000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Maintain a labeled regression inventory for all applicable security and semantic fixes in the pinned Wire 7.1.0 baseline. Include GHSA-7xpr-hc2w-34m9 negative-length group skip, GHSA-9rm7-3qhh-h2mc reader limits, duplicate singular-message merging from 7.0.0-alpha05 (#3652/#3656), java_package validation and applicable Java-generator path/literal/comment escaping fixes. Verify each item against pinned source rather than a fixed advisory count. The 7.1.0 JSON null-element fix belongs to excluded Gson/Moshi adapters and is recorded as out of scope, not silently omitted. Close required runtime/schema/oracle/generator case accounting before Apicurio integration.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Each applicable security or semantic change has a named regression case and evidence that the case detects reintroduction of the relevant defect.
- [ ] #2 The source-linked inventory records correct release attribution, affected feature, test mapping and a reason for each excluded-feature item, including JSON-only fixes.
- [ ] #3 The complete shared CI runner executes runtime, core schema, applicable protoc, Java compiler/profile and golden checks with no required case pending.
- [ ] #4 Security notes document the regression inventory and hand it to TASK-23 for changelog and advisory monitoring without assuming advisories cover every security fix.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
