---
id: TASK-3
title: Write Kotlin-to-Java translation conventions doc
status: Done
assignee:
  - assistant
created_date: '2026-09-29 09:22'
updated_date: '2026-09-30 04:36'
labels: []
milestone: m-6
dependencies:
  - TASK-1
documentation:
  - docs/decisions.md
  - docs/research-wire-java-port-2026-09-29.md
priority: high
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Write docs/translation-conventions.md as the behavioral contract for Java production translation and mechanical adaptation of upstream Kotlin tests. Keep original pinned tests identifiable and review all changed inputs, helpers, expectations and error paths. Describe Java 11 limitations accurately: records and sealed classes are unavailable; local-variable var is available and is not a Java 11 prohibition. Preserve all upstream per-file notices, including notices embedded below standard headers. Record source tag/path and translation methodology without mandatory repetitive comments in every method.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Conventions cover nullable and non-null parameters/results, null-check placement and exception types, including require versus checkNotNull, with small executable examples owned by TASK-5.
- [x] #2 Data-class and collection equality, hashCode, toString, collection mutability and unsigned integer parsing have documented Java equivalents and parity checks.
- [x] #3 Java 11 constraints are accurate; test-only Kotlin and permitted build-time generators are distinguished from forbidden Kotlin production dependencies.
- [x] #4 The test-adaptation record maps original cases to adapted cases and covers fixtures, setup, helpers, expected values and error paths; exclusions require declared non-ported functionality, never merely a failing test.
- [x] #5 The provenance policy preserves Google Nano BSD-style notices in the reader family and ProtoWriter, R8 notices in MathMethods, JetBrains Apache notices and mixed notices, plus the actual notices of any vendored dependency.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implementation delegated to a subagent; gate corrections applied by the orchestrator with direct source verification. Commit 3fe1ffa pushed. Lesson recorded: the ProtoReader.kt:469 correction was applied on a reviewer's claim without personal verification and was wrong; reverted after independent grep. Reviewer claims about checkable facts get verified before applying from now on.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Wrote the Kotlin-to-Java translation conventions, the behavioral contract for all production translation and test adaptation (docs/translation-conventions.md, 540+ lines).

Nullability and exceptions (AC#1): platform-type decision rules, null-check placement including internal declarations the port keeps public, and the require/checkNotNull mapping table with probe-verified default messages; the section 2.4 code pairs are the spec for TASK-5 AC#6's executable reference-versus-port examples, including the compiler-injected message strings two upstream tests assert verbatim. Equality and collections (AC#2): data-class equals/hashCode/toString member semantics verified by compiled probes (including the array asymmetry and the corrected 40-class count), copy/componentN bridge policy, collection mutability with the ImmutableList/MutableOnWriteList behavior and the UnknownFieldsTest equality-versus-hashCode attribution split, unsigned parsing with the message-difference caveat. Java 11 constraints (AC#3): accurate record/sealed/var rules and the three-row Kotlin boundary table distinguishing test/build Kotlin from forbidden production Kotlin with its enforcement hooks. Test adaptation (AC#4): rules R1-R5 with the ledger format TASK-9/13/14 keep, the never-change list, and the DEC-6-citing exclusion discipline. Provenance (AC#5): the verified notice inventory (ProtoWriter body notice corrected to lines 18-50, propagated to research F3), vendored-code notice rules, and the per-file provenance header with the translation-notes home.

Gates: pa:simplify (3 checks; findings fixed: stale correction paragraph, DEC-6/BUILD.md citation dedup, line-citation corrections); code-review high (6 findings resolved; notably, an earlier reviewer-driven citation 'fix' to ProtoReader.kt:469 was reverted after direct grep verification showed 470 was correct; UnknownFieldsTest hashCode attribution corrected to the Java suite; internal declarations added to the null-check rule; data-class count corrected to 40; section 7.2 translation-notes home defined). Mechanical link/citation/diff checks pass. Docs-only: application tests not applicable. Kotlin facts verified by compiled probes with the compiler-version gap disclosed in the document.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
