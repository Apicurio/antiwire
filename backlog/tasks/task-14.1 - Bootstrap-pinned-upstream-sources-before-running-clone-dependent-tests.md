---
id: TASK-14.1
title: Bootstrap pinned upstream sources before running clone-dependent tests
status: In Progress
assignee: []
created_date: '2026-10-06 08:37'
updated_date: '2026-10-06 09:24'
labels:
  - adversarial-audit
  - build
  - verification
milestone: m-12
dependencies: []
references:
  - scripts/verify.sh
  - scripts/parity-coverage.sh
  - scripts/fetch-upstream.sh
  - wire-schema-java/src/test/java/com/squareup/wire/testing/TestFiles.java
  - >-
    wire-protoc-compat-java/src/test/java/com/squareup/wire/SchemaEncoderInteropTest.java
  - BUILD.md
documentation:
  - docs/parity-runner.md
  - docs/decisions.md
parent_task_id: TASK-14
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
At audit baseline 295ef5f0e7a1e45937417b9f621044d8139df6f5, scripts/verify.sh fails on a checkout without the pinned upstream clone. It starts Maven at line 90, but SchemaEncoderInteropTest initializes TestFiles.upstreamClone(), which throws when /tmp/wire or the configured clone path is absent. The fetch occurs only in parity-coverage.sh after a successful build, so the missing prerequisite prevents reaching the fetch. The clean audit run ended with six SchemaEncoderInteropTest errors and VERIFY_EXIT=1. Scratch command-order probes independently confirmed Maven executes before the fetch. CI masks the local failure by fetching first.

Restore the documented single-command verification contract. Respect ANTIWIRE_UPSTREAM and the pinned tag and commit. Do not weaken clone-dependent tests, skip them or accept an unverified checkout. The earlier audit failure caused by stale compiled test classes is a separate local build-output condition and is not this defect.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 With no upstream clone at the default or configured location, the documented verification command obtains and validates the pinned sources before clone-dependent tests run.
- [ ] #2 An existing correct clone is reused without destructive replacement, and a wrong or modified clone is rejected with an actionable error.
- [ ] #3 Unavailable upstream sources produce an explicit prerequisite failure before the long build rather than a static-initializer test failure or a silent skip.
- [ ] #4 An isolated bootstrap regression check covers absent, valid and invalid clone states, and BUILD.md plus CI instructions agree on the required setup.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. In scripts/verify.sh, run a prerequisite step before the mvn build suite: call scripts/fetch-upstream.sh (honouring ANTIWIRE_UPSTREAM) so the pinned clone exists and is validated against config/parity-pins.json before any clone-dependent test runs. Reuse an existing correct clone without replacement; reject a wrong or modified clone with an actionable error. If sources cannot be obtained, fail fast with an explicit prerequisite message before the long build.\n2. Document in BUILD.md the stale target/test-classes recovery (clean build) and the clone prerequisite, consistent with ci.yml.\n3. Add an isolated bootstrap regression check covering absent, valid and invalid clone states (stubbed mvn, no real build), wired into the existing script test conventions if any.\n4. Verify: run the regression check; run scripts/verify.sh on a worktree with the clone removed to confirm the clone is obtained before mvn.\nRisk: shared verify.sh with TASK-14.2; both are done by one worker in sequence, 14.1 first.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Document the distinct stale-build-output condition encountered during the audit: deleted source tests can survive in target/test-classes after switching revisions. A supported clean-build recovery instruction is appropriate. Do not add exclusions for those test names or weaken tests to hide stale outputs.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
