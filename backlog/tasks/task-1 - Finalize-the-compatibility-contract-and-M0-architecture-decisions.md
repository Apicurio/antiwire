---
id: TASK-1
title: Finalize the compatibility contract and M0 architecture decisions
status: Done
assignee:
  - assistant
created_date: '2026-09-29 09:22'
updated_date: '2026-09-30 02:04'
labels: []
milestone: m-6
dependencies: []
references:
  - docs/research-wire-java-port-2026-09-29.md
documentation:
  - docs/decisions.md
  - docs/research-wire-java-port-2026-09-29.md
priority: high
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Use docs/decisions.md as the approved policy baseline, not a request to reopen settled product decisions. Target Wire 7.1.0 semantics, Java 11 bytecode, Maven on JDK 17+, and Apicurio first. Production artifacts and transitive dependencies contain no Kotlin; pure-Java dependencies are allowed with measured footprint. Kotlin is allowed for build and test tooling. Preserve functional and source compatibility except documented migration sites; do not promise precompiled Wire 6/7 ABI compatibility. Keep com.squareup.wire public names where feasible, with explicit exclusions and coexistence rules. The intended groupId is io.apicurio subject to authorization. Record initial module/API contracts here; TASK-4 selects the I/O route and TASK-5 validates it before M1. Kafka is deferred.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 docs/decisions.md records all agreed policies, the Wire 7.1.0 tag and resolved commit, release prerequisites, authorization boundaries and owners of the remaining M0 technical decisions.
- [x] #2 A compatibility matrix inventories public Wire and Okio types, parser ranges, profile APIs, generated-code APIs and observed Apicurio call sites; every deliberate source change has a named task owner and migration requirement.
- [x] #3 Fixed independent runtime and schema boundaries, optional Java-generator support and duplicate-class prevention rules are recorded. The publication grouping is explicitly provisional; TASK-4 finalizes it after experimentation and TASK-5 validates it before M1.
- [x] #4 All technical assumptions still awaiting TASK-4 or TASK-5 evidence are marked provisional; no I/O choice, footprint figure or delivery estimate is presented as proven.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implementation delegated to a subagent; verification and gate fixes applied by the orchestrator. Commit 67bde64 pushed to origin/main (repo redirects to Apicurio/antiwire).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Finalized the compatibility contract and M0 architecture decisions.

Created docs/compatibility-matrix.md from the pinned Wire 7.1.0 clone and the Apicurio census at 48e2742: public runtime/okio/parser/generator surface inventory with keep-name intents and Java-signature consequences, the 24-file Apicurio call-site census with line-exact citations, a migration-site register with owner tasks, duplicate-class prevention rules, and the provisional-items index for AC#4. OPEN-3 now points at the matrix.

Verified AC#1/#3/#4 against docs/decisions.md text (quotes recorded in the task notes): tag/commit pins, release prerequisites, authorization boundaries, provisional publication grouping with the TASK-4/TASK-5 lifecycle, and no proven I/O choice, footprint figure or estimate. Precision fixes landed during review: ProtocolException is test-scope only (F3, OPEN-1), Syntax is the census's single wire-runtime type (F5), SchemaLoader citations disambiguated by source set with the NioFileSystem bridge recorded as OPEN-1 evidence. TASK-2 AC#4 now encodes the duplicate-class detection check the register promises.

Gates: pa:simplify (3 parallel checks; findings fixed), code-review high (5 findings: 4 fixed, 1 disambiguated after partial refutation), mechanical link/em-dash/whitespace checks pass. Docs-only: application tests not applicable. Evidence pins verified by git rev-parse in both clones.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
