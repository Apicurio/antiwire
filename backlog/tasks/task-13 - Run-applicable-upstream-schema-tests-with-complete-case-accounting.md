---
id: TASK-13
title: Run applicable upstream schema tests with complete case accounting
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-8
dependencies:
  - TASK-12
documentation:
  - docs/decisions.md
priority: high
ordinal: 13000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Adopt Wire 7.1.0 schema commonTest and jvmTest logic plus required test support against the Java core. Preserve pinned originals and track mechanical call-form, I/O and range-type adaptations. Every upstream case must be accounted for. All core-schema cases run here; Java-profile/generator cases remain required and are assigned to TASK-16 with a blocking release path. Only cases exclusively for declared non-ported functionality may be excluded. Deferred required cases must not be counted as green or excluded. Kotlin test dependencies are allowed but cannot leak into production.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 All applicable core-schema cases pass, including parser, linker, imports, resources, options, source loading, invalid input and SchemaEncoder scenarios.
- [x] #2 A case-level inventory accounts for all pinned upstream schema tests and support fixtures, mapping adaptations, approved feature exclusions and Java-profile cases owned by TASK-16.
- [x] #3 Adaptations preserve inputs, setup, helpers, expected outputs and exception paths, and each is reviewed against the pinned originals.
- [x] #4 The shared CI entry point blocks core-schema regressions and reports generator-owned cases as pending until TASK-16 runs them; upstream implementation jars cannot satisfy port-under-test calls.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Final Summary

Adopted the applicable upstream wire-schema suite at tag 7.1.0 in five commits (7d8533f batches 1, eb3f51f batch 2, 62b1512 batch 3, 5e85ccf simplify pass, and the code-review correction): every parser/element corpus (ProtoParserFullTest 88, MessageElement 25, ProtoFileElement 19, Service 12, Enum 10, Field 6, Extend 6, Extensions 5, Option 6, OneOf 1), the rules corpus (Emitting 15, Pruning 31, SemVer 8, MavenVersions 5, DagChecker 12, Util 2, DirectedAcyclicGraph 6), the linked-model corpus (SchemaFullTest 102, Options 13, Pruner 99, SchemaLoader 16, Root 6, ProtoFile 1, Location 12, ProtoType 16), and the dynamic/encoder corpus (SchemaProtoAdapter 11, DynamicSerialization 6, SchemaEncoderFullTest 5 with protobuf-java oracle at upstream's pin). Upstream @Ignore cases stay @Disabled with their reasons; 3 profile cases defer to TASK-16 with bodies preserved. Faithful expected values forced real main-source fixes: SyntaxReader error-location and trailing-comment extraction, MessageElement toSchema newline, OptionElement data-class members, structural int[] range equality (now one shared SchemaUtil home after the code-review caught a claimed-but-unwired fix), DirectedAcyclicGraph port, SemVer/CommonSchemaLoader visibility per a recorded internal-mapping rule. Ledger docs/task13-case-accounting.md accounts every upstream file (adopted/deferred/excluded); TASK-26 filed for the Empty/Void divergence. CI: schema-tests ACTIVE (591 cases, 9 skipped reported), TASK-9-owed runtime-tests flipped ACTIVE (859, 4 skipped), verify.sh derives both from surefire summaries and keeps logs on failure. All 7 ACTIVE suites pass. Gates: /simplify 4 angles applied or skipped-as-upstream-faithful; code-review high effort, all 5 findings fixed including the correction commit.
