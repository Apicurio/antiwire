---
id: TASK-30
title: >-
  Reconcile translation-conventions 7.2 provenance headers with the shipped
  sources
status: Done
assignee: []
created_date: '2026-10-06 19:06'
updated_date: '2026-10-07 08:01'
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
- [x] #1 Section 7.2 and the code agree: headers present on every translated production file, or section 7.2 and section 8 rewritten to the mechanism actually used, with the decision recorded in docs/decisions.md.
- [x] #2 Section 6.2 and the InstantTest adaptation row match the shipped ledgers.
- [x] #3 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Decision (maintainer said "do it" on 2026-10-07 after I recommended it): the per-file "Translated from" header is not adopted, because adding it to the 161 shipped files would change class files and sources jars and re-fire the DEC-13 invalidation rule while adding no information the existing records lack. translation-conventions.md section 7.2 now describes the real provenance mechanism (retained names, pin in config/parity-pins.json, verbatim notices, license-inventory for the three shipped modules, adapted-test ledgers and case-accounting docs, upstream-case-map reconciled by parity-coverage, task and commit history); section 2.1's cross-reference and the section 8 provenance row (stating that no automated production-file provenance check exists) are corrected; the decision is recorded in docs/decisions.md; the InstantTest row in UPSTREAM-TEST-ADAPTATIONS.md now matches the 5 running cases. Gates: /code-review high ran (5 findings, all fixed); scripts/verify.sh 12/12 PASS; documentation-only (gate-exempt for /simplify).
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
