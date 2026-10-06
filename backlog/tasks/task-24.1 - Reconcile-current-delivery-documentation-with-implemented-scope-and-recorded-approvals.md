---
id: TASK-24.1
title: >-
  Reconcile current delivery documentation with implemented scope and recorded
  approvals
status: To Do
assignee: []
created_date: '2026-10-06 08:20'
updated_date: '2026-10-06 08:41'
labels:
  - adversarial-audit
  - documentation
  - release-readiness
milestone: m-12
dependencies: []
references:
  - 'README.md:29'
  - 'README.md:45'
  - 'README.md:49'
  - docs/footprint.md
  - docs/performance.md
  - backlog/tasks/task-19 - Footprint-and-dependency-before-after-report.md
  - 'docs/team-update-2026-10-05.md:35'
  - 'docs/api-surface.md:184'
documentation:
  - docs/decisions.md
parent_task_id: TASK-24
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The 2026-10-06 delivery audit at 295ef5f0e7a1e45937417b9f621044d8139df6f5 confirmed contradictory current-state documentation. README.md:29 describes TASK-4 and TASK-5 as in progress and loading/API decisions as open after their implementation. README.md:45 still calls footprint acceptance and encodeForward open gates, while docs/footprint.md section 9.6 records acceptance on 2026-10-03 and docs/performance.md records the encodeForward fix and acceptance history. README.md:49 describes the port as not yet written. TASK-19 is Done with acceptance criteria 1, 2 and 4 and its Definition of Done unchecked. These inconsistencies prevent a reader from distinguishing completed work from genuine release blockers.

Update the reader-facing current status and reconcile task bookkeeping with primary evidence. Preserve historical measurements and their exact candidate identities. Do not infer that historical footprint/performance acceptance authorizes a newer candidate or publication. Final-candidate remeasurement, approval and publication remain owned by TASK-21. This follow-up does not reopen the completed planning reconciliation or authorize a release.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 README and current status documents consistently describe the implemented runtime, schema loading, consumer API and optional Java generator rather than the M0 snapshot.
- [ ] #2 The current release summary distinguishes resolved performance findings and recorded historical acceptance from final-candidate freshness and explicit publication approval still required by TASK-21.
- [ ] #3 TASK-19 status, checklist and evidence references agree after each unchecked item is checked against the actual measurement and review records; unsupported items remain explicitly open.
- [ ] #4 Historical reports retain their original revisions and dates, and current summaries link to those records without presenting old measurements as proof for a newer candidate.
- [ ] #5 The generated-output summary distinguishes raw byte identity from identity after approved API mappings, and its corpus size is derived from the actual comparison inputs.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
The audit also verified a golden-output overstatement in docs/team-update-2026-10-05.md:35. It says generated output is byte-identical to the original compiler. docs/api-surface.md:184-191 instead defines identity after a bounded upstream-to-Bytes mapping. Correct the current summary within this task without weakening the mapping checks. The update's '13 reference files' figure needs reconciliation with the actual compared corpus rather than repetition.

Additional 2026-10-06 evidence-lane recommendations belong to this documentation task. Reconcile team-update commit count with its own revision, correct the decode workload count against docs/performance.md, distinguish total protoc cases from executed and skipped cases, and update stale pre-DEC-8 relocation names in docs/security-regression-inventory.md and docs/upstream-sync.md. Do not describe a recorded case count as an executed-case count. Check current values rather than copying audit-era numbers. The suite registry's claim that every protoc skip names DEC-6/TASK-26 also needs correction to acknowledge the mirrored upstream ignore; this executable verification-metadata correction is owned by TASK-14.2.

The team-update statement at line 38 that an automated check detects every edited expected value is unsupported by the current parity checker, which compares names rather than test bodies. Replace that overclaim with the actual boundary between automated checks and individual adaptation review. Do not promise automatic semantic equivalence of arbitrary tests.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
