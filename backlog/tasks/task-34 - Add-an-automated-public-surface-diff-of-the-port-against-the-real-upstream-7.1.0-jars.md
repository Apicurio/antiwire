---
id: TASK-34
title: >-
  Add an automated public-surface diff of the port against the real upstream
  7.1.0 jars
status: To Do
assignee: []
created_date: '2026-10-09 15:01'
labels:
  - user-feedback
  - compatibility
  - verification
milestone: m-11
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Root cause of why the Companion and getter gaps (see the user-feedback task) were never quantified: the compatibility matrix used upstream .api dumps as a boundary checklist, but wire-schema.api omits internal packages (ProtoParser is not in it), and no suite compares the port's public members against the real upstream classes. On 2026-10-09 I built this comparison by hand (javap -public/-protected -s on the pinned 7.1.0 jars from Maven Central against the built port jars; per class: Companion field and class, static twins, getX versus x(), constructors, visibility, static-ness, throws clauses) and it found hundreds of differences. The tooling lives only in /tmp/cmp and /tmp/repro and will not persist. Turn it into a verify.sh suite that reports (not necessarily fails) the surface delta per category against a committed, reviewed baseline, so any change that widens the delta, and any new gap in Apicurio-referenced members, is visible in CI. Fetch the 7.1.0 jars the same way scripts/fetch-upstream.sh pins sources (checksums in config/parity-pins.json). Optionally include a bytecode scan of a supplied consumer jar directory (the 2026-10-09 scan of the compiled Apicurio classes found 90 of 120 referenced Wire members unresolved).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A registered verify.sh suite diffs the port's public and protected members against the pinned upstream 7.1.0 jars by category (Companion, static twin, getter, constructor, visibility, throws, absent class) and compares with a committed baseline; a surface change not in the baseline fails the suite.
- [ ] #2 The upstream jars are fetched and checksum-verified from the pins in config/parity-pins.json; the suite fails closed when they are unavailable.
- [ ] #3 The baseline and docs/compatibility-matrix.md record each accepted difference with its reason, so the suite doubles as the source-compatibility ledger.
- [ ] #4 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
