---
id: TASK-19
title: Footprint and dependency before/after report
status: In Progress
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-10-02 09:43'
labels: []
milestone: m-10
dependencies:
  - TASK-18
documentation:
  - docs/decisions.md
priority: medium
ordinal: 19000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Measure resolved dependency and artifact footprint for a clean minimal consumer and the recorded Apicurio integration branch. Compare the actual upstream 6.4.0 baseline, upstream 7.1.0 target and antiwire, using their real coordinates and POMs rather than reusing 7.1.0 size estimates for 6.4.0. Distinguish full clean-consumer footprint from marginal dependencies already present in Apicurio. Include required runtime, schema, Kotlin/Okio, Guava and poet artifacts where each baseline resolves them. Report optional generator costs separately. Java dependencies are allowed, but each must earn its footprint.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 docs/footprint.md contains reproducible dependency-tree diffs, resolved artifact versions, jar counts and byte totals comparing upstream 6.4.0, upstream 7.1.0 and antiwire for both clean and Apicurio consumers, with optional generator costs separated.
- [ ] #2 Measurements come from actual artifact resolution and file sizes, include tool versions and base commits, and clearly distinguish decimal bytes from MiB and clean from marginal totals.
- [ ] #3 The port's production dependency graph has no Kotlin or Kotlin-backed transitives, each retained Java dependency is justified, and the maintainer explicitly accepts the measured footprint before release.
- [ ] #4 The report and footprint acceptance identify the measured candidate by build revision, artifact checksums and resolved dependency identities. Code, dependency or packaging changes invalidate the affected evidence and require renewed measurements and acceptance before TASK-21.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-02 09:43
---
Report produced at docs/footprint.md (commit follows). All four AC measurement requirements are covered: clean-consumer trees, versions, jar counts and byte totals for 6.4.0 / 7.1.0 / antiwire on runtime, schema and optional generator surfaces; Apicurio marginal delta on utils/protobuf-schema-utilities and schema-util/protobuf at base 448f845c versus integration a11d7cfa; decimal bytes and MiB kept distinct throughout; identity block with revision 63e98b7, jar SHA-256 checksums and the invalidation rule. AC#3's maintainer acceptance is deliberately NOT ticked: the doc carries an 'Acceptance: PENDING maintainer signature' row and this task stays open until that signature lands. One finding the maintainer should weigh: the integration branch's explicit guava pin (32.1.3-jre) overrides the quarkus-bom's 33.5.0-jre, so the switch also pins guava two versions below the baseline's effective resolution (footprint.md section 4.4).
---
<!-- COMMENTS:END -->
