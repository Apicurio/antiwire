---
id: TASK-31
title: >-
  Settle remaining pending wording in compatibility-matrix F/G/H and stale
  module descriptions
status: Done
assignee: []
created_date: '2026-10-06 19:06'
updated_date: '2026-10-06 19:36'
labels:
  - adversarial-audit
  - docs
milestone: m-12
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
2026-10-06 audit leftovers of the docs pass: compatibility-matrix.md sections F, G and H still read as pending ('TASK-18 re-inventories', 'TASK-4 selects', items awaiting tasks that are Done); convert to dated resolved records (item 6 AndroidMessage: deferred with cause per m1-ownership-map). decisions.md OPEN-1/OPEN-2 headings should carry '(resolved 2026-10-01, see M0 spike outputs)'. consumer-check-java11.sh header and PlaceholderConsumerMain javadoc still call the consumer a placeholder/empty shell; wire-runtime-java and wire-generator poms and config/retained-prefixes.txt comments ('provisional OPEN-1-era') should be checked for the same; verify-suites.json 'interim key' wording on three module-total suites; research report, plan-review.md, m0-execution-ledger and team-update-2026-10-05 need dated status banners (original plan, executed differently in about a week; TASK-16.1 and 21.1 closed). Pure documentation, no maintainer-owned records.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Sections F, G and H of compatibility-matrix.md and the OPEN-1/OPEN-2 headings state resolved outcomes with dates.
- [x] #2 Historical planning artifacts carry a dated status banner.
- [x] #3 Placeholder and shell wording is gone from scripts, poms and config comments.
- [x] #4 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Matrix sections F/G/H and the register rows state resolved outcomes with dates; OPEN-1/OPEN-2 headings carry resolution pointers; dated status banners added to the research report, plan-review, M0 ledger and the 2026-10-05 team update; placeholder/shell wording removed from consumer-check-java11.sh, PlaceholderConsumerMain javadoc, retained-prefixes.txt and verify-suites.json. Gates: /code-review high ran (4 findings: 3 fixed, 1 cosmetic fixed), scripts/verify.sh 12/12 PASS before the review fixes (docs-only follow-up edits).
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
