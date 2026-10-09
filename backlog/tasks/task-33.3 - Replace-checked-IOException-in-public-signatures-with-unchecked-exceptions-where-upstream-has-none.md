---
id: TASK-33.3
title: >-
  Replace checked IOException in public signatures with unchecked exceptions
  where upstream has none
status: To Do
assignee: []
created_date: '2026-10-09 20:54'
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
- [ ] #1 Every public method of the ported classes that upstream Kotlin declares without @Throws no longer declares a checked exception in its throws clause; IOException from the underlying engine is wrapped in java.io.UncheckedIOException (or the existing runtime exception where one is already used), with the original as cause.
- [ ] #2 Where upstream declares @Throws (45 functions), the port keeps the matching throws clause.
- [ ] #3 No behavior change besides the exception type; scripts/verify.sh passes with unchanged case counts, tests that asserted IOException are adapted with the reason recorded, and the TASK-34 baseline pins the throws clauses.
- [ ] #4 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
