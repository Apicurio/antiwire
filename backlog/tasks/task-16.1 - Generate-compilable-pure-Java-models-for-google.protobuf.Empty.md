---
id: TASK-16.1
title: Generate compilable pure-Java models for google.protobuf.Empty
status: Done
assignee: []
created_date: '2026-10-06 08:29'
updated_date: '2026-10-06 11:57'
labels:
  - adversarial-audit
  - codegen
  - parity
milestone: m-12
dependencies: []
references:
  - wire-java-generator/src/main/java/com/squareup/wire/java/JavaGenerator.java
  - wire-runtime-java/src/main/java/com/squareup/wire/ProtoAdapter.java
  - >-
    wire-protoc-compat-java/src/test/java/com/squareup/wire/EmptyRoundTripTest.java
  - 'README.md:41'
documentation:
  - docs/decisions.md
  - docs/compatibility-matrix.md
  - docs/api-surface.md
parent_task_id: TASK-16
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The delivery audit at 295ef5f0e7a1e45937417b9f621044d8139df6f5 reproduced a supported Java-generation failure. A proto3 message with `google.protobuf.Empty done = 2` produces a kotlin.Unit field and references ProtoAdapter.EMPTY. Compiling the generated model against the port runtime fails with `package kotlin does not exist`. Adding Kotlin does not repair it: ProtoAdapter.EMPTY is typed Void, so compilation then fails with `Unit cannot be converted to Void`. JavaGenerator.java:156 maps ProtoType.EMPTY to kotlin.Unit. TASK-26 fixed only the dynamic model with UnitValue and WIRE_EMPTY. The remaining generated-code limitation points to TASK-16, which is already Done, leaving the defect without an open owner.

Java code generation is supported by README.md:41. DEC-6 excludes Kotlin generator products, not Java messages containing Empty. The disabled generated-model test in EmptyRoundTripTest.java:57 therefore cannot use DEC-6 as an approved exclusion. Fix the Java-generation path and restore the applicable test rather than treating disclosure of the limitation as completion. This affects consumers generating Empty-bearing Java messages; the Apicurio schema-only migration does not exercise this path.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 WireCompiler generates a Java model for a proto containing google.protobuf.Empty that compiles with javac --release 11 against the port runtime without Kotlin or upstream Wire dependencies.
- [x] #2 Generated Empty fields preserve present versus absent values and round-trip with the pinned upstream wire representation, including duplicate singular occurrences.
- [x] #3 The applicable generated-model EmptyRoundTripTest executes successfully without the unsupported DEC-6 exclusion.
- [x] #4 The Empty mapping and bounded golden adaptation are documented and automatically checked, and README plus compatibility records no longer describe the resolved generated-code gap as open.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Reproduce first with a regression test: generate Java for a proto3 message with google.protobuf.Empty and compile it against the port runtime with javac --release 11 and no Kotlin on the classpath (expected red).\n2. Fix JavaGenerator so ProtoType.EMPTY maps to the port's pure-Java unit type (UnitValue) and the adapter reference is the Unit-typed WIRE_EMPTY, following the phase-2 Bytes mapping precedent (bounded mechanical mapping, documented, deprecated bridge unchanged). Keep ProtoAdapter.EMPTY (Void, deprecated) untouched.\n3. Revive the disabled generated-model case in EmptyRoundTripTest (remove the unsupported DEC-6 label); make it execute green, covering present vs absent and duplicate singular occurrences, and byte-compare with the pinned upstream oracle.\n4. Update the bounded golden mapping check and docs (api-surface.md Empty section, compatibility-matrix.md section E, README known gap) so the gap is no longer described as open.\nVerification: new regression test red then green; module tests for wire-java-generator and wire-protoc-compat-java; full scripts/verify.sh at the end.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
The corrected audit replay is recorded at /tmp/antiwire-empty-repro/replay-transcript.txt. It generated audit.empty.Rpc successfully using the current runtime/schema classes and a generator compiled from the same HEAD sources. javac on the correctly named Rpc.java failed without Kotlin and also failed with kotlin-stdlib present due to Unit/Void type mismatches. Preserve the proto input and equivalent regression commands in the eventual test suite rather than relying on temporary audit paths.

scripts/install-protoc.sh had a pre-existing portability bug (shasum exists only on macOS) that blocked regenerating the protoc fixtures on Linux; it now falls back to sha256sum. The relocated upstream oracle is not a dependency of wire-protoc-compat-java, and adding it would put kotlin-stdlib on the compile classpath; the revived case byte-compares against the pinned protoc oracle that the upstream case itself uses, and the relocated-upstream byte parity for WIRE_EMPTY stays pinned by WireEmptyAdapterTest in wire-runtime-java.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Generated Java for google.protobuf.Empty now compiles with javac --release 11 against the port runtime with no Kotlin on the classpath. JavaGenerator maps ProtoType.EMPTY to ProtoAdapter.UnitValue and references the Unit-typed ProtoAdapter.WIRE_EMPTY, and JvmLanguages emits the matching reflection adapter string, as a bounded documented mapping that follows the phase-2 Bytes precedent; the deprecated Void-typed ProtoAdapter.EMPTY is untouched. The regression test GeneratedEmptyCompileTest failed first with the audit's exact error ("package kotlin does not exist", and with Kotlin present "Unit cannot be converted to Void"), then passed after the fix; it round-trips present versus absent values and duplicate singular occurrences. EmptyRoundTripTest.allEmpty is revived with no @Disabled and no DEC-6 label, runs green with byte parity against the pinned protoc oracle (a new AllEmptyOuterClass fixture), and keeps only the genuine DEC-6 Kotlin-model half excluded. AllTypesGoldenBytesMappingTest gained three mapping rows so the golden comparison checks exactly the documented divergence. api-surface.md, compatibility-matrix.md, README and the task16 ledger no longer describe the gap as open. I merged the branch and found a conflict the worker could not see: the parity gate still held a skipped record owned by this task for the now-running allEmpty case and failed as stale; I removed that record and refreshed the registry counts. Final scripts/verify.sh on the integrated tree, run by me: exit 0, all 12 ACTIVE suites passed (protoc 123 cases with 49 skipped, compiler 188 with 33 skipped, parity 990 reconciled). Gates: /simplify ran and its findings were applied; a high-effort /code-review ran with six findings, all fixed.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
