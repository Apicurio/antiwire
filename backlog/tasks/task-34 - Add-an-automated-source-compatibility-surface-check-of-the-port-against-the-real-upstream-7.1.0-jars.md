---
id: TASK-34
title: >-
  Add an automated source-compatibility surface check of the port against the
  real upstream 7.1.0 jars
status: Done
assignee: []
created_date: '2026-10-09 15:01'
updated_date: '2026-10-09 22:12'
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
Root cause of why the Companion, getter and constructor gaps (see TASK-33) were never quantified: the compatibility matrix used upstream .api dumps as a boundary checklist, but wire-schema.api omits internal packages (ProtoParser is not in it), and no suite compares the port's public members against the real upstream classes. On 2026-10-09 I built this comparison by hand (javap -public/-protected -s on the pinned 7.1.0 jars from Maven Central against the built port jars; per class: Companion field and class, static twins, getX versus x(), constructors, visibility, static-ness, throws clauses) and it found hundreds of differences, but it still missed the two ProtoAdapter(FieldEncoding, Class) constructors that PR #4 found, because I matched members by name; the suite must compare by full signature. The tooling lives only in /tmp/cmp and /tmp/repro and will not persist. PR #4's ProtoAdapterConstructorParityTest (constructor parity against wire-upstream-shaded, with a check that the upstream count is pinned) is a good model to extend to every public class. Turn the comparison into a verify.sh suite that reports the surface delta per category against a committed, reviewed baseline, so any change that widens the delta, and any new gap in Apicurio-referenced members, is visible in CI. Fetch the 7.1.0 jars the same way scripts/fetch-upstream.sh pins sources (checksums in config/parity-pins.json). Optionally include a bytecode scan of a supplied directory of consumer jars: the 2026-10-09 scan of the Apicurio protobuf classes built at 3620f08c found 102 of 132 referenced Wire members unresolved (see TASK-33).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A registered verify.sh suite diffs the port's public and protected members against the pinned upstream 7.1.0 jars by category (Companion, static twin, getter, constructor, visibility, throws, absent class) and compares with a committed baseline; a surface change not in the baseline fails the suite.
- [x] #2 The upstream jars are fetched and checksum-verified from the pins in config/parity-pins.json; the suite fails closed when they are unavailable.
- [x] #3 The baseline and docs/compatibility-matrix.md record each accepted difference with its reason, so the suite doubles as the source-compatibility ledger.
- [x] #4 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-09 follow-up from the second /code-review of TASK-33.1: AccessorNameParityTest.noPlainNameNextToAPinnedGetter reads only EXPECTED rows with no parameters, so it cannot see nested classes that are top-level upstream (Root.ProtoFilePath, Root.DirectoryRoot were found by hand, now fixed) or the plain twins of GAP getters (MessageBinding.messageType() is allow-listed by hand). The surface-diff suite must cover nested public classes with their upstream names, GAP rows, and fields as well as methods, comparing by full signature.

2026-10-09 decision (maintainer approved the plan): the compatibility target is SOURCE compatibility for every public upstream member that Java can express without Kotlin types (DEC-4) and without okio in the signature (DEC-14); binary compatibility is not promised (DEC-2). The check therefore classifies every public upstream member (class, constructor, method, static, field, Companion, getter) as MATCH (same name and parameter types, compatible return type, same static-ness, public), GAP (not ported, with a reason), or EXCLUDED (Kotlin type in the signature, okio type in the signature, Kotlin internal visibility, JVM-synthetic), compares with a committed baseline and fails on an unexplained difference. It must also compile a consumer snippet against the real wire-schema-jvm 7.1.0 jar and recompile the same source against the port, because compiling is the user-visible effect. Measured on 2026-10-09 after TASK-33.1: 1627 public upstream members compared, 77.1% exact name and descriptor match; 67 Kotlin-typed (excluded by DEC-4), 6 okio-typed (DEC-14), 44 Companion fields, 182 absent methods/getters, 26 absent constructors, 40 same name with another signature, 12 absent statics, 18 absent public classes. Nested and GAP members, fields and constructors must be covered (the accessor test misses them).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
scripts/surface-check.py (and the registered suite surface-check, scripts/surface-check.sh, 13th ACTIVE suite) compares every public and protected member of the real Wire 7.1.0 jars (wire-runtime-jvm, wire-schema-jvm, wire-java-generator, wire-compiler) with the three shipped module jars by full signature. Upstream jars are fetched from Maven Central with a 60 s timeout, verified against the SHA-256 pinned in config/parity-pins.json (new upstream_surface_jars block) and the SHA-1 Maven Central publishes, and the suite is NOT_RUN (never skipped) without a verified copy. Result on this commit: MATCH 1156, GAP 367 (304 TASK-33.2, 63 TASK-33.3), EXCLUDED 355 (DEC-5 data bridges 248, Kotlin internal 44, DEC-6 28, DEC-4 25, DEC-14 8, java-hidden 2), 256 synthetic members skipped; committed ledger config/surface-baseline.tsv has 722 rows and must equal the computed result (unexpected, stale and changed rows all fail). Checked-exception differences are a THROWS category (62 rows, owner TASK-33.3). Five consumer snippets under scripts/surface-consumer are compiled against the real jars and the port: three pass, two are recorded expected failures with owner and the compiler symbol that must appear (CompanionParse: Companion, TASK-33.2; EncodeWithoutTryCatch: IOException, TASK-33.3). Proven negative checks: renaming a MATCH method, removing a GAP row, adding a stale GAP row, a snippet failing for the wrong symbol, --update refusing snippet problems even with --allow-new, empty cache offline and a corrupted cached jar. Docs: decisions.md (compatibility target), compatibility-matrix.md (ledger section), BUILD.md (suite description). Gates: scripts/verify.sh 13/13 PASS with unchanged counts (913/633/123/193/990); /simplify applied (dead code, parallel javap and javac, throws-rule helper); /code-review high ran (7 findings: DEC-6 member exclusion, okio Companion methods, Kotlin internal constructors/functions/nested classes, --update gate, vacuous expected failure, relocation guard, download timeout, all fixed). Known limits: GAP rows describe absence by signature, not by upstream documentation status; the Kotlin-internal detection reads the pinned sources with a regex scan, so a declaration style it does not recognize would fall back to GAP (visible, not silent); only public and protected members are compared, not annotations or generics.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
