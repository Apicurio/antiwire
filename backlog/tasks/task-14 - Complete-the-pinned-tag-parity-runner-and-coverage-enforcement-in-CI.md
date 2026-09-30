---
id: TASK-14
title: Complete the pinned-tag parity runner and coverage enforcement in CI
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-8
dependencies:
  - TASK-9
  - TASK-13
documentation:
  - docs/decisions.md
priority: high
ordinal: 14000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Extend the verification entry point introduced in TASK-2 and exercised by TASK-5. Pin Wire 7.1.0 by tag and commit, preserve original test sources, apply reviewed mechanical mappings and run tests against port artifacts. Maintain a complete case inventory with executed, approved excluded-feature and required-pending states. TASK-15 adds protoc cases; TASK-16 adds Java compiler/profile cases and golden comparisons; TASK-17 verifies the final required suite. Upstream implementations run only as isolated reference oracles. Partial M2 coverage must not be reported as full release parity.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 One reproducible command retrieves the pinned sources, verifies provenance and adaptations, and runs all implemented runtime/core-schema suites against the port, including TASK-9's applicable runtime cases under wire-tests JVM suites.
- [ ] #2 Every CI change runs this command and blocks on test failures, unrecorded source drift, lost cases or classpath contamination; approved exclusions name the non-ported feature.
- [ ] #3 The coverage report identifies pending required M3 cases with TASK-15/TASK-16 owners and cannot label them passed; release validation rejects any pending required case.
- [ ] #4 The documented extension contract requires TASK-15 to add blocking protoc jobs and TASK-16 to add blocking compiler/profile and generated-code comparison jobs.
- [ ] #5 The tag, resolved commit, tool/fixture pins, isolation checks and adaptation-review procedure are stored in the repository with a repeatable bump procedure.
- [ ] #6 Source extraction reconciles test cases with the pinned upstream build/source-set inventory across modules. Fixture directories are distinguished from executable suites; an unaccounted relevant wire-tests case fails coverage validation.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
