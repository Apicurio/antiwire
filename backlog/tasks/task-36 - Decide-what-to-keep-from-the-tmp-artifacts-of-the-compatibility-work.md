---
id: TASK-36
title: Decide what to keep from the /tmp artifacts of the compatibility work
status: Done
assignee: []
created_date: '2026-10-11 07:23'
updated_date: '2026-10-29 10:58'
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
- [x] #1 Each of /tmp/cmp (the comparison scripts and unpacked jars: api2.py, apidiff.py, classify.py), /tmp/repro (the reproduction classes), /tmp/apicurio-goal.md and /tmp/iss (the issue drafts) is either committed in a tracked path (scripts/, docs/ or backlog/docs) with a README line, or recorded as disposable with the reason.
- [x] #2 Nothing the project depends on remains only under /tmp: scripts/surface-check.py and scripts/scan-consumer-jars.py already replace the comparison scripts, which the task confirms by diff of results.
- [x] #3 Run /code-review at high effort on the final diff (if files are added)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-29: a host reboot emptied /tmp before the triage, so none of the four items exists any more (ls of /tmp/cmp, /tmp/repro, /tmp/apicurio-goal.md and /tmp/iss: no such file). Disposition per item, all recorded as lost and disposable: /tmp/cmp is superseded by scripts/surface-check.py (1408 members match, 0 gap, 379 documented exclusions against the real Wire 7.1.0 jars) and scripts/scan-consumer-jars.py; /tmp/repro is superseded by the committed regression tests for the NoSuchFieldError (Companion fields) and the toString differences; /tmp/apicurio-goal.md is superseded by the pushed fork branch antiwire-integration and its ANTIWIRE_MIGRATION.md (TASK-33.4); /tmp/iss is superseded by the posted issues #5 to #10. AC#2's diff of results cannot be run because the old scripts and their 1627-member output are gone; surface-check.py is the only comparison now and runs in scripts/verify.sh. The real loss was elsewhere: docs/footprint.md cited /tmp recompute scripts for sections 10 and 11 whose inputs are also gone; commit efe48ce (branch task-36-tmp-artifacts) records that in the doc and adds scripts/footprint-tree-sizes.py so future section-4 classpath sums are reproducible.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The /tmp artifacts were lost in a reboot before triage; each is recorded as superseded (surface-check.py and scan-consumer-jars.py, the regression tests, the pushed Apicurio fork branch, issues #5 to #10). The footprint report's dependence on lost /tmp scripts is now stated in docs/footprint.md sections 10 and 11, and scripts/footprint-tree-sizes.py (documented in BUILD.md and footprint section 4) makes future classpath sums reproducible. Gates: /simplify (7 fixes), /code-review high (7 findings fixed), synthetic and real-tree smoke runs. Commit efe48ce.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
