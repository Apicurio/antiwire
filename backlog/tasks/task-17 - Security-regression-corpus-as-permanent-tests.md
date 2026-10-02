---
id: TASK-17
title: Security regression corpus as permanent tests
status: Done
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-9
dependencies:
  - TASK-15
  - TASK-16
documentation:
  - docs/decisions.md
priority: high
ordinal: 17000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Maintain a labeled regression inventory for all applicable security and semantic fixes in the pinned Wire 7.1.0 baseline. Include GHSA-7xpr-hc2w-34m9 negative-length group skip, GHSA-9rm7-3qhh-h2mc reader limits, duplicate singular-message merging from 7.0.0-alpha05 (#3652/#3656), java_package validation and applicable Java-generator path/literal/comment escaping fixes. Verify each item against pinned source rather than a fixed advisory count. The 7.1.0 JSON null-element fix belongs to excluded Gson/Moshi adapters and is recorded as out of scope, not silently omitted. Close required runtime/schema/oracle/generator case accounting before Apicurio integration.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Each applicable security or semantic change has a named regression case and evidence that the case detects reintroduction of the relevant defect.
- [x] #2 The source-linked inventory records correct release attribution, affected feature, test mapping and a reason for each excluded-feature item, including JSON-only fixes.
- [x] #3 The complete shared CI runner executes runtime, core schema, applicable protoc, Java compiler/profile and golden checks with no required case pending.
- [x] #4 Security notes document the regression inventory and hand it to TASK-23 for changelog and advisory monitoring without assuming advisories cover every security fix.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Comments

#1 - 2026-10-02 07:20 (UTC)
Starting. Method: enumerate the security/semantic commits in the pinned clone's history (7.0.0-alpha05..7.1.0 has 193 commits; GHSA commits found by message search: e4e56fab3 skipGroup negative-length (#3595), 81ff7f24a Swift twin, plus the reader-limit items to locate; #3652/#3656 fieldmask semantics lands in JavaGenerator), verify each against the PINNED SOURCE (not advisory counts) per AC#1/AC#2, and give each a named regression case in the port. JSON null-element fix recorded out-of-scope (DEC-6 adapters). Plan: (1) agent builds the source-linked inventory docs/security-regression-inventory.md + regression tests module (wire-security-corpus cases inside existing modules, no new shipped artifacts), (2) flip security-corpus suite ACTIVE, (3) gates. Hand-off to TASK-23 recorded per AC#4.

## Final Summary

Delivered in commit 2818ead: docs/security-regression-inventory.md with 10 in-scope security/semantic items (GHSA-7xpr skipGroup negative length, GHSA-9rm7 reader limits plus 32-bit overflow, pre-window recursion limit, Javadoc delimiter and unicode escaping, #3633 literal validation, #3657 output-path containment, #3718 package validation with the tag-vs-changelog attribution conflict recorded, #3652 runtime duplicate singular-message merge, #3656 generated oneof merge) each carrying commit hash, tag attribution via git tag --contains, pinned file:line evidence, and a named regression case; 24 corpus tests across RuntimeSecurityCorpusTest (10), SchemaSecurityCorpusTest (10), JavaGeneratorSecurityCorpusTest (4), each proven to detect reintroduction (reader items via pre-fix /tmp replay showing silent decode and out-of-message reads; escaping/validation/path items by construction with accept-case guards). Exclusions recorded with reasons: three Swift GHSA fixes, the 7.1.0 JSON null-element fix (DEC-6 adapters, out of scope not omitted), Kotlin-only fixes, plugin fixes, tooling. security-corpus suite ACTIVE with a skipped-aware per-class floor (code-review found and fixed the raw-count gap); hand-off to TASK-23: the inventory doc is the changelog/advisory-monitoring input, and it does not assume advisories cover every fix (the pre-window recursion item is in exactly because of that). All 11 ACTIVE suites pass, zero PENDING.
