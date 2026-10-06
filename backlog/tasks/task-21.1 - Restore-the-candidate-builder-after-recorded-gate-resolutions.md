---
id: TASK-21.1
title: Restore the candidate builder after recorded gate resolutions
status: In Progress
assignee: []
created_date: '2026-10-06 08:32'
updated_date: '2026-10-06 09:25'
labels:
  - adversarial-audit
  - release-tooling
milestone: m-12
dependencies: []
references:
  - scripts/release-build.sh
  - 'README.md:45'
  - docs/footprint.md
  - docs/performance.md
  - docs/release-candidate.md
documentation:
  - docs/decisions.md
parent_task_id: TASK-21
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
At audit baseline 295ef5f0e7a1e45937417b9f621044d8139df6f5, scripts/release-build.sh stops before packaging because both expected PENDING anchors no longer match the resolved footprint and performance records. The footprint predicate returns zero matches and its guard exits with FATAL at line 84. The performance predicate also returns zero matches. A scratch execution of the unchanged guard section reproduced exit 1 without running packaging or changing outputs.

The script deliberately requires a manual anchor update when a gate changes state. That documented follow-through was not performed after the historical resolutions. This is an incomplete maintenance step, not a false approval or an unsafe fail-open gate. Restore the advertised candidate-building procedure while keeping missing, ambiguous or changed evidence visible. Preserve TASK-21 ownership of candidate freshness and explicit publication approval. Historical acceptance does not approve later runtime/schema changes, and this task does not authorize publishing.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The candidate builder recognizes the actual recorded gate states and can reach packaging for a valid candidate without requiring obsolete PENDING text.
- [ ] #2 Missing, ambiguous, changed or reopened gate records produce an explicit failure or unresolved status rather than an inferred approval.
- [ ] #3 Checks exercise pending, resolved and missing-evidence states, including the exact historical transition that currently prevents packaging.
- [ ] #4 The script-generated candidate report records accurate gate status and artifact identity, preserves TASK-21 final-candidate freshness requirements, and performs no deploy, tag or publication.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Read scripts/release-build.sh lines 60-100 and the current accepted-state rows in docs/footprint.md and docs/performance.md.\n2. Re-anchor the maintainer-gate detection to recognise the recorded states (ACCEPTED / RESOLVED rows) as well as PENDING, keeping fail-closed: missing, ambiguous, reworded or reopened gate records produce an explicit failure or unresolved status, never an inferred approval. The generated docs/release-candidate.md must record accurate gate status plus candidate revision and artifact identity, and must keep the DEC-13 freshness caveat.\n3. Add checks exercising pending, resolved and missing-evidence states, including the exact historical transition that currently aborts at line 84, using scratch docs copies and without running packaging or publication.\n4. Do not run the real release-build.sh (it rewrites docs/release-candidate.md) except in a scratch copy of the repository.\nVerification: the new state-matrix check passes; release-build.sh guard section exits 0 on current docs in a scratch copy.
<!-- SECTION:PLAN:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
