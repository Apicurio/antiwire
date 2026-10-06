---
id: TASK-14.2
title: >-
  Reject unrecorded skipped cases and false fixture classifications in parity
  gates
status: In Progress
assignee: []
created_date: '2026-10-06 08:38'
updated_date: '2026-10-06 09:24'
labels:
  - adversarial-audit
  - parity
  - verification
milestone: m-12
dependencies: []
references:
  - scripts/check-parity-coverage.py
  - scripts/verify.sh
  - config/upstream-case-map.json
  - wire-schema-java/src/test/java/com/squareup/wire/schema/PrunerTest.java
  - 'docs/team-update-2026-10-05.md:38'
documentation:
  - docs/decisions.md
  - docs/parity-runner.md
parent_task_id: TASK-14
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
At delivery-audit baseline 295ef5f0e7a1e45937417b9f621044d8139df6f5, parity completeness enforces test-name presence rather than execution. Independent scratch-copy probes demonstrated that removing PrunerTest.retainType fails coverage, but adding an unrecorded method-level or class-level @Disabled still returns PASS. Changing that executable test file's case-map entry to fixture:true also returns PASS and drops its cases from accounting. Replaying verify.sh's module-result parser with zero failures and additional skips returns PASS because skips are only reported, not reconciled.

These are directly reproduced checker and result-parser failures. A full reactor run with injected mutations was not performed during the audit. The security corpus has a separate skip-aware floor, which does not protect general applicable upstream tests. DEC-5 requires applicable cases to execute; TASK-14 requires lost and pending cases to block verification. Model executed cases and approved exclusions explicitly instead of presenting source-name reconciliation as complete execution coverage. Preserve legitimate upstream ignored cases and DEC-6 exclusions. Resolve current unrecorded skips through their actual owners, not by blanket approval.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 An unrecorded method-level or class-level skip of a required upstream case fails verification with the case identity and reason.
- [ ] #2 Executable upstream test files cannot silently leave the coverage inventory through an unchecked fixture classification; genuine fixtures remain supported.
- [ ] #3 Execution reports reconcile mapped cases and their dispositions, accounting for upstream ignores, approved exclusions, parameterized invocations and port-only tests without relying on naive aggregate count equality.
- [ ] #4 Release completeness rejects required cases that are skipped or absent from execution and distinguishes those cases from approved excluded features.
- [ ] #5 End-to-end regression probes show deletion, unrecorded method skip, class skip and false-fixture mutations fail for the intended reason while the valid baseline passes; coverage documentation accurately states the enforced guarantees.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extend scripts/check-parity-coverage.py to model skip annotations on both sides: a port case carrying method-level or class-level @Disabled with no matching upstream @Ignore and no map-recorded exclusion (with reason) fails, reporting case identity. Upstream @Ignore is read from the pinned clone.\n2. Validate fixture flags: a config/upstream-case-map.json entry marked fixture:true whose port file contains @Test methods fails; genuine fixtures stay supported.\n3. Reconcile executed cases per module in scripts/verify.sh against case dispositions using identities, not naive aggregate count equality (account for upstream ignores, approved exclusions, parameterized invocations, port-only tests).\n4. Extend --require-complete to reject required cases that are skipped or absent from execution, distinct from approved excluded features.\n5. Record current unrecorded skips (including the two ProtoTarget cases owned by TASK-16.2 and the Empty case owned by TASK-16.1) as explicit recorded dispositions naming their owning task, without blanket approval. Fix config/verify-suites.json protoc skip reason text to acknowledge the mirrored upstream ignore.\n6. Add negative regression probes (deletion, unrecorded method skip, class skip, false fixture) on scratch copies that must fail for the intended reason, plus the valid baseline passing. Update docs/parity-runner.md to state the real enforced guarantees.\nVerification: probe suite + check-parity-coverage.py on the real tree against /tmp/wire; final scripts/verify.sh.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
The evidence audit found that config/verify-suites.json describes all protoc skips as DEC-6/TASK-26 exclusions, although InteropTest includes a mirrored upstream ignore. Reconcile the registry's declared skip reasons with the actual case dispositions as part of this task. Do not remove valid upstream ignores to reach an arbitrary count.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
