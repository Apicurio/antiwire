---
id: TASK-30
title: >-
  Reconcile translation-conventions 7.2 provenance headers with the shipped
  sources
status: To Do
assignee: []
created_date: '2026-10-06 19:06'
labels:
  - adversarial-audit
  - docs
milestone: m-12
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
2026-10-06 audit: docs/translation-conventions.md section 7.2 mandates a per-file provenance header (source path, tag 7.1.0, commit 9f62097) and section 8 claims per-file review enforcement, but 0 of 310 production Java files carry it (grep for 'Translated from' and 9f62097 returns none). Decide and record: either add the headers (mechanically, preserving the upstream notices of section 7.1) or replace section 7.2 with the actual provenance mechanism and record the decision. Also annotate section 6.2: the five-field ledger is used only by the runtime and protoc-compat UPSTREAM-TEST-ADAPTATIONS.md files, while schema, wire-tests-java and generator accounting lives in the task9/13/16 case-accounting docs. Update the InstantTest row in wire-runtime-java/src/test/kotlin/.../UPSTREAM-TEST-ADAPTATIONS.md (still says negative cases EXCLUDED; InstantTest.java runs all 5, per TASK-14.2).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Section 7.2 and the code agree: headers present on every translated production file, or section 7.2 and section 8 rewritten to the mechanism actually used, with the decision recorded in docs/decisions.md.
- [ ] #2 Section 6.2 and the InstantTest adaptation row match the shipped ledgers.
- [ ] #3 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
