---
id: TASK-37
title: Run the Apicurio integration-tests on the antiwire branch with podman
status: Done
assignee: []
created_date: '2026-10-11 07:23'
updated_date: '2026-10-11 08:27'
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
- [x] #1 The integration-tests module of the Apicurio branch antiwire-integration is executed (at least the suites that touch protobuf schemas) with podman as the container runtime, or the reason it cannot run is recorded with the exact error.
- [x] #2 Pass and fail counts are compared with the same suites on the unmodified Apicurio base commit.
- [x] #3 Any failure caused by antiwire is filed as a task with the exact stack frame.
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Done 2026-10-11 by the apicurio-registry agent on the Mac; the result is the agent's report, not reproduced by me (no podman machine of that kind on bird). Commit aec50760d on paoloantinori/apicurio-registry branch antiwire-integration (verified by me through the GitHub API: one file, ANTIWIRE_MIGRATION.md, +102 lines, new section 'Integration tests under podman (TASK-37)'; no source or test changed; nothing pushed to origin). Reported: the Apicurio integration tests run under podman 6.1.3 (engine 6.0.0, machine podman-machine-default, Docker-compatible socket already exposed; DOCKER_HOST set to the host-side socket from podman machine inspect, TESTCONTAINERS_RYUK_DISABLED=true), with no blocker. Three protobuf-related classes, ProtobufSerdeIT (17), SerdesPerformanceIT (6) and AllArtifactTypesIT (11): the branch (antiwire 62c8646 on the classpath) passes 34/34 and the base commit 25c8b5ce0 (Wire 6.4.0) passes 34/34, with 0 failures, errors and skips on both. From artifacts the agent verified that the Kafka container ran in podman (strimzi test container 0.117.0 with Kafka 4.3.1), that the branch app carries only antiwire jars and no Square Wire, and that Confluent's protobuf serializer worked at runtime with the exclusions (no NoSuchFieldError). Limits: only three classes ran, not the whole integration-tests module; none of the integration schemas has reserved or extension ranges, so the DEC-15 IntRange limit is not exercised; flags needed on that Maven setup: -am, -Dmdep.skip=true, -Dtest=NoSuchUnitTest. No failure was caused by antiwire, so no follow-up task was needed. Gates: no code changed in this repository (backlog record only).
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
