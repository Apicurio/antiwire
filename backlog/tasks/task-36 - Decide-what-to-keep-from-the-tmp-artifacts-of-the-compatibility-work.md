---
id: TASK-36
title: Decide what to keep from the /tmp artifacts of the compatibility work
status: To Do
assignee: []
created_date: '2026-10-11 07:23'
labels:
  - adversarial-audit
  - hygiene
milestone: m-12
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Found by the 2026-10-11 adversarial audit. TASK-18's integration branch was lost when /tmp was cleaned; the same risk applies to artifacts that still exist only under /tmp on bird: /tmp/cmp (hand-written api-diff scripts and unpacked jars that produced the 1627-member comparison before scripts/surface-check.py existed), /tmp/repro (the classes that reproduced the user's NoSuchFieldError and the toString differences), /tmp/apicurio-goal.md (the brief given to the apicurio-registry agent: the only copy on bird) and /tmp/iss (the GitHub issue drafts, now posted as issues #5 to #10). Decide per item: keep (commit it) or discard (say why).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Each of /tmp/cmp (the comparison scripts and unpacked jars: api2.py, apidiff.py, classify.py), /tmp/repro (the reproduction classes), /tmp/apicurio-goal.md and /tmp/iss (the issue drafts) is either committed in a tracked path (scripts/, docs/ or backlog/docs) with a README line, or recorded as disposable with the reason.
- [ ] #2 Nothing the project depends on remains only under /tmp: scripts/surface-check.py and scripts/scan-consumer-jars.py already replace the comparison scripts, which the task confirms by diff of results.
- [ ] #3 Run /code-review at high effort on the final diff (if files are added)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
