---
id: TASK-33.3
title: >-
  Replace checked IOException in public signatures with unchecked exceptions
  where upstream has none
status: Done
assignee: []
created_date: '2026-10-09 20:54'
updated_date: '2026-10-11 07:23'
labels:
  - user-feedback
  - compatibility
  - api
milestone: m-11
dependencies:
  - TASK-34
parent_task_id: TASK-33
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Maintainer decision 2026-10-09 ('Unchecked'): upstream Wire is Kotlin and has no checked exceptions; only 45 functions carry @Throws. The port declares throws IOException on 89 methods where upstream declares none (Message.encode(), SchemaLoader.loadSchema(), AnyMessage.unpack, Internal.*, the reader family), so Java callers that compile against Wire without try/catch fail to compile against the port. Remove the throws and surface failures as UncheckedIOException. Accepted cost: a caller that catches IOException on a method that no longer throws it gets a compile error (rare). Depends on TASK-34 for the throws baseline.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Every public method of the ported classes that upstream Kotlin declares without @Throws no longer declares a checked exception in its throws clause; IOException from the underlying engine is wrapped in java.io.UncheckedIOException (or the existing runtime exception where one is already used), with the original as cause.
- [x] #2 Where upstream declares @Throws (45 functions), the port keeps the matching throws clause.
- [x] #3 No behavior change besides the exception type; scripts/verify.sh passes with unchanged case counts, tests that asserted IOException are adapted with the reason recorded, and the TASK-34 baseline pins the throws clauses.
- [x] #4 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Done 2026-10-10 (record closed 2026-10-11 after the adversarial audit found it still marked To Do). Commits de353e9 and ae3c4d0, on origin/main at 62c8646. 79 public methods that declared a checked IOException where real Wire declares none now throw java.io.UncheckedIOException with the original as cause (internal Rethrow helper); the 45 upstream @Throws members keep the checked exception, pinned by the surface check and UncheckedExceptionContractTest. Methods whose signatures declare a different checked exception for their contract keep it (Message.writeReplace still surfaces InvalidObjectException; the file-writing handlers still add their 'Error emitting ...' context to a failure from DryRunFileSystem). The decision and its two surfacing forms are DEC-16 in docs/decisions.md. Surface baseline: the 79 TASK-33.3 gap rows became MATCH, GAP rows are now 0 (re-run on 2026-10-11: surface-check PASS, MATCH 1408, EXCLUDED 379, consumer snippets 7/0). Apicurio impact (reported by the apicurio-registry agent on antiwire 62c8646, not reproduced by me): no 'exception IOException is never thrown' compile error and no call site changed, because every IOException handler on the branch guards a call that still throws it. Gates: /simplify applied by the implementing agent; /code-review high ran by me (3 findings: writeReplace conversion, DryRunFileSystem error context, a near-vacuous assertion), all fixed with new tests and negative-checked; scripts/verify.sh 13/13 PASS at 62c8646 (runtime 923, schema 633, protoc 123, compiler 196, parity 990 reconciled). Open follow-up: whether JdkSchemaLoader (an antiwire-only class that still declares throws IOException) should follow the policy: GitHub issue #8.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
