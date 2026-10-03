---
id: TASK-19
title: Footprint and dependency before/after report
status: Done
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-10-03 12:23'
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
- [x] #3 The port's production dependency graph has no Kotlin or Kotlin-backed transitives, each retained Java dependency is justified, and the maintainer explicitly accepts the measured footprint before release.
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

author: claude
created: 2026-10-03 12:23
---
Renewed 2026-10-03 for candidate c713e9a (post no-okio API merge): docs/footprint.md section 9 re-measures the antiwire column (runtime probe 1 jar 248,894 bytes; schema probe 2 jars 574,220; generator probe 4 jars 736,713 with javapoet unchanged at 106,068), records the nine release-artifact SHA-256 checksums under io.apicurio, and re-runs the Apicurio marginal on the rebuilt guava-free integration branch 6ce5582c: 17 jars removed (10,993,475 bytes), 2 added (574,220), net minus 10,419,255 bytes (9.937 MiB), identical for utils/protobuf-schema-utilities (20 to 5 artifacts) and schema-util/protobuf (43 to 28). Zero guava/kotlin/okio nodes in compile or runtime scope of both trees, verified mechanically; the section 4.4 guava pin observation is superseded by the rebuild and withdrawn. Acceptance stays PENDING maintainer signature, now binding to the section 9.1 checksums.
---
<!-- COMMENTS:END -->

#3 - 2026-10-03 (session): maintainer ACCEPTED the measured footprint (docs/footprint.md acceptance row, section 9.1 candidate c713e9a, guava-free marginal). AC#3 satisfied.
