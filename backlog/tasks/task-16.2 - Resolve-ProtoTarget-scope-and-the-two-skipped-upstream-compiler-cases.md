---
id: TASK-16.2
title: Resolve ProtoTarget scope and the two skipped upstream compiler cases
status: Done
assignee: []
created_date: '2026-10-06 08:41'
updated_date: '2026-10-06 13:00'
labels:
  - adversarial-audit
  - scope
  - parity
milestone: m-12
dependencies: []
references:
  - wire-java-generator/src/test/java/com/squareup/wire/schema/WireRunTest.java
  - docs/task16-case-accounting.md
  - config/upstream-case-map.json
documentation:
  - docs/decisions.md
  - docs/parity-runner.md
parent_task_id: TASK-16
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
At audit baseline 295ef5f0e7a1e45937417b9f621044d8139df6f5, WireRunTest.protoOnly and protoTargetNeverEmitsGoogleProtobufDescriptor are disabled in the port although both execute in pinned upstream Wire 7.1.0. docs/task16-case-accounting.md calls ProtoTarget an unported scope gap. The case map counts these names as ported, and TASK-16 is Done. DEC-6 does not explicitly exclude .proto emission. However, TASK-16's delivery is framed around the Java-target compiler, so this audit does not assume that .proto emission is automatically an approved new feature.

Resolve the inconsistent scope and ownership. Either deliver the intended ProtoTarget behavior and revive its cases, or obtain an explicit maintainer scope decision that excludes this product and records these cases honestly. An annotation citing TASK-16 is not itself an approved exclusion. The generic execution-accounting mechanism is owned by TASK-14.2; this task owns the specific product decision and resulting behavior.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The decision record explicitly states whether ProtoTarget .proto emission belongs to the initial release, with maintainer approval required for any new exclusion.
- [x] #2 If retained in scope, ProtoTarget behavior executes both pinned upstream cases with preserved expectations and has a Java 11-compatible implementation.
- [x] #3 If explicitly excluded, both cases are recorded as excluded for the named product rather than counted as executed or passed, and supported-feature documentation agrees.
- [x] #4 TASK-16's completion record and TASK-21's release checklist reference the resolved disposition without hiding required pending work.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Decision (maintainer, 2026-10-06, "Fallo" after the recommendation): ProtoTarget .proto emission is IN scope for the initial release; it is implemented, not excluded. DEC-6 does not list it.
1. Record the decision in docs/decisions.md (a short entry stating ProtoTarget is retained in scope, with the date and the reason) and keep DEC-6 unchanged.
2. Port upstream wire-compiler ProtoTarget (Target.kt lines 222-277) to Java in the schema/generator modules following the existing Target/SchemaHandler/WireRun conventions: for each ProtoFile in the schema that is in the source path, not empty (no types, services or extends) and not an embedded Wire runtime proto, write protoFile.toSchema() to outDirectory/<relative path>/<name>.proto, log artifactHandled(..., "Proto"), wrap IOExceptions with the upstream message "Error emitting <path> to <dir>". handle(Type/Service/Extend) return nothing. Java 11 only, no Kotlin.
3. Wire it into WireRun (the Target dispatch and the proto target in manifest/CLI handling if upstream does) wherever Target subclasses are enumerated; grep ALL references to the other targets (JavaTarget, CustomTarget) and confirm every dispatch site.
4. Revive WireRunTest.protoOnly and protoTargetNeverEmitsGoogleProtobufDescriptor (remove the @Disabled) with the upstream expectations preserved; remove their two skipped records (owner TASK-16.2) from config/upstream-case-map.json; update docs/task16-case-accounting.md, README supported-features text and docs/api-surface.md if they mention the gap.
Verification: the two revived cases pass; module tests; scripts/check-parity-coverage.py (including --require-complete should now report no open-owner skips); full scripts/verify.sh. Gates: /simplify and /code-review at high effort on the final diff.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-06: not started. This task requires a maintainer scope decision (port ProtoTarget .proto emission or explicitly exclude it by a new decision) which cannot be inferred. The code-side recorded disposition of the two cases is handled by TASK-14.2 pointing here as owner. Decision needed from the maintainer before implementation.

Acceptance criterion 3 (the exclusion branch) does not apply: the maintainer chose to retain ProtoTarget in scope on 2026-10-06, so it is checked as satisfied by not being needed, not by an exclusion record.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Maintainer decision on 2026-10-06: ProtoTarget .proto emission stays in the initial release scope and is implemented. docs/decisions.md records it as a dated scope note under DEC-6, which never listed it. The port gains wire-java-generator/.../schema/ProtoTarget.java, a Java 11 translation of upstream Target.kt lines 222 to 277: for every proto file in the source path that is non-empty and not an embedded Wire runtime proto it writes protoFile.toSchema() under the file's relative directory, logs artifactHandled with "Proto", and produces nothing for types, services or extends. One recorded divergence: the per-file IOException travels as a RuntimeException with upstream's message, because the port's SchemaHandler declares no checked IOException (the JavaSchemaHandler precedent). Upstream builds ProtoTarget only in the Gradle plugin and tests, and WireCompiler has no proto output flag, so the port adds no CLI or manifest surface and documents that. WireRunTest.protoOnly and protoTargetNeverEmitsGoogleProtobufDescriptor are revived with upstream bodies and expectations; I confirmed both appear as executed, not skipped, in the surefire report. The two owner records were removed from config/upstream-case-map.json and the ledger, registry and compatibility notes updated. Verified by me: scripts/check-parity-coverage.py --require-complete passes with 0 skips owned by open tasks (990 cases, 89 skipped: 10 mirrored upstream ignores, the rest recorded DEC-6 exclusions), and scripts/test-parity-gate.sh passes its baseline plus ten mutation probes. The worker's full scripts/verify.sh passed all 12 ACTIVE suites. Also fixed here: the README open-items paragraph still named three already closed audit tasks. Gates: /simplify on four angles with no findings left, a high-effort /code-review whose six findings concerned plan text and repository hygiene and were dispositioned, tests re-run.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
