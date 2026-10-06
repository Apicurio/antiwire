---
id: TASK-21.2
title: Release-record hygiene found by the 2026-10-06 outdated-records audit
status: To Do
assignee: []
created_date: '2026-10-06 19:06'
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
- [ ] #1 scripts/release-build.sh --check-gates runs without the pending-and-accepted FATAL and still fails closed on contradictory evidence.
- [ ] #2 performance.md and release-candidate.md carry dated stale markers until regenerated or remeasured for the final candidate.
- [ ] #3 NOTICE and license-inventory.md agree with DEC-12 (javapoet attributed, no Apache clause over BSD-only files).
- [ ] #4 Line citations in the security and sync docs resolve or are symbol references.
- [ ] #5 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
