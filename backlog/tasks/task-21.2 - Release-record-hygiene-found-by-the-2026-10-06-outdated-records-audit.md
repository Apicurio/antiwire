---
id: TASK-21.2
title: Release-record hygiene found by the 2026-10-06 outdated-records audit
status: Done
assignee: []
created_date: '2026-10-06 19:06'
updated_date: '2026-10-07 05:59'
labels:
  - adversarial-audit
  - release-tooling
milestone: m-11
dependencies: []
parent_task_id: TASK-21
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Items from the audit that touch maintainer-owned acceptance records or release evidence and were therefore not edited in the audit commit: (1) scripts/release-build.sh --check-gates exits 1 (FATAL: pending and accepted footprint rows both present) because docs/footprint.md sections 8 (line 553) and 9.6 (line 813) keep identical ACCEPTED rows for c713e9a while 10.6 holds the PENDING row for 0f860ff; reword the superseded historical rows so they no longer match the live accepted anchor (for example 'signed 2026-10-03 for c713e9a, superseded by section 10') and delete the two 'deliberately unsigned' sentences that contradict them (requires the maintainer to confirm the wording change to an acceptance record); (2) docs/performance.md: add a dated STALE banner (275dcc2, 84a6a9f, ec15e18, 7297b66 changed measured modules after session 5 at 82c3624), fix the 'final release candidate' wording, replace the bytes-decode confirmation figure 0.880 (an upstream-move control column) with the pooled 1.156 and worst repetition 1.026, add a supersession line under the coordinate note; (3) docs/release-candidate.md: interim STALE banner, regenerate after (1); (4) docs/license-inventory.md arithmetic line should read 55+5+3+1+1+4+1=70 and 143+8+3+1+1+4+1=161; (5) NOTICE and DEC-12: javapoet (production dependency) has no NOTICE entry although DEC-12 says every retained third-party dependency is attributed; and the sentence 'licensed by Google LLC under the Apache License, Version 2.0' still sits over files that carry only BSD notices (commit c419e8f kept it); (6) line-number drift in docs/security-regression-inventory.md items 1/2/4/9 and docs/upstream-sync.md step 2 (ProtoReader 285->295, 546-550->565-571; ByteArray body 537-541->549-555; JavaGenerator 607-608->613; RuntimeMessageAdapter 222-228->~234), or convert them to symbol references; (7) TASK-21 AC#7 wording 'after the 0f860ff audit' should read 'after the 2026-10-06 audit (baseline 295ef5f)'; (8) milestone descriptions: m-8 add TASK-25/26/28, m-11 list the five added audit dependencies, m-12 reword 'does not authorize implementation'.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 scripts/release-build.sh --check-gates runs without the pending-and-accepted FATAL and still fails closed on contradictory evidence.
- [x] #2 performance.md and release-candidate.md carry dated stale markers until regenerated or remeasured for the final candidate.
- [x] #3 NOTICE and license-inventory.md agree with DEC-12 (javapoet attributed, no Apache clause over BSD-only files).
- [x] #4 Line citations in the security and sync docs resolve or are symbol references.
- [x] #5 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Maintainer approved the fixes on 2026-10-07 ("fix all the incorrect information, sure"). Footprint sections 8 and 9.6 historical acceptance rows reworded as signed-history so they no longer match the live accepted anchor; section 10.6 now documents how to sign; release-build.sh --check-gates exits 0 (footprint OPEN, encodeForward CLOSED) and scripts/test-release-gates.sh passes all 8 states including a new accepted state. performance.md and release-candidate.md carry dated STALE banners (regeneration and remeasurement on the final candidate remain TASK-21 items); performance.md wording, bytes-decode figure and checksum supersession corrected. license-inventory sums fixed; NOTICE drops the Apache sentence over BSD-only files and adds JavaPoet 1.13.0. Drifted line citations fixed in security-regression-inventory.md, upstream-sync.md and the TASK-23 note; TASK-21 AC#7 and milestones m-8, m-11, m-12 reworded. The footprint signature itself was not recorded (maintainer-owned). Gates: /code-review high ran (5 findings, all fixed); scripts/verify.sh 12/12 PASS; docs, comments and test-script changes only.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
