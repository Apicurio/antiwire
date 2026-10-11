---
id: TASK-37
title: Run the Apicurio integration-tests on the antiwire branch with podman
status: To Do
assignee: []
created_date: '2026-10-11 07:23'
labels:
  - adversarial-audit
  - apicurio
  - verification
milestone: m-12
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The Apicurio integration branch (TASK-33.4) only compile-verified integration-tests: there is no Docker on the Mac and the testcontainers suites need a container runtime. podman 6.1.3 is installed on the Mac (and 5.8.7 on bird) and has not been tried. Documented as an open problem in ANTIWIRE_MIGRATION.md and in TASK-33.4 but with no owner. Try TESTCONTAINERS with podman (DOCKER_HOST pointing at the podman socket, ryuk disabled if needed) on the fork branch; compare with the base commit. Use the apicurio-registry agent on the Mac.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The integration-tests module of the Apicurio branch antiwire-integration is executed (at least the suites that touch protobuf schemas) with podman as the container runtime, or the reason it cannot run is recorded with the exact error.
- [ ] #2 Pass and fail counts are compared with the same suites on the unmodified Apicurio base commit.
- [ ] #3 Any failure caused by antiwire is filed as a task with the exact stack frame.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
