---
id: TASK-11
title: 'Port wire-schema Schema, linker, and options to Java'
status: Done
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-10-01 22:46'
labels: []
milestone: m-8
dependencies:
  - TASK-10
  - TASK-8
documentation:
  - docs/decisions.md
priority: high
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Port Schema, ProtoFile, MessageType, Type, Field, Service, Pruner, Root, loader/linker internals and option validation after the public parser model is available. Use the loader boundary selected in M0. Own core schema loading and linking here; TASK-12 completes encoding and dependency separation. Java-generator profile support belongs in the optional generator artifact and its tests in TASK-16, not an unresolved KotlinPoet dependency in core. Implement the Schema.protoAdapter dynamic-message path with its required runtime types.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Schema/linker results match upstream Wire 7.1.0 on applicable multi-file, imports, options, pruning and error cases, using TASK-10's parser and range representation.
- [x] #2 Schema.protoAdapter encodes and decodes dynamic messages against upstream reference cases without relying on future generator implementation.
- [x] #3 Core loading and linking compile on the chosen I/O boundary; every profile-related case assigned to TASK-16 is recorded in the shared applicability inventory.
- [x] #4 Relevant tests run in the shared CI entry point and production APIs/dependencies satisfy docs/decisions.md.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-01 22:15
---
#2 - 2026-10-02 00:30 (UTC) Core translation landed in e4b1c11: all linking-layer files ported (Field full, OneOf, Extend, Type hierarchy, Service, Rpc, EnumConstant, Reserved, Extensions, ProtoFile, Options, LinkedOptionEntry, Multimap, ErrorCollector, SyntaxRules, LiteralValidation, Linker, FileLinker, CycleChecker, internal DagChecker, MarkSet, SemVer, PruningRules, EmittingRules, Pruner, Schema, SchemaProtoAdapterFactory). Nine runtime .proto resources vendored under src/main/resources mirroring upstream jvmMain layout; SchemaUtil.toEnglishLowerCase aligned to upstream lowercase(Locale.US). Tests: LinkingSmokeTest (17 cases) + MapLoader; module now 66 tests green; all 5 ACTIVE verify suites pass. Learned: wire.redacted does not exist in upstream 7.1.0 extensions.proto (redaction is the .*\.redacted qualified-name heuristic); proto3 message fields without 'optional' encode OMIT_IDENTITY; 'needs to import' fires when both files are sources. Gates: /simplify (4 agents) and code-review running before Done.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Ported the wire-schema linking layer from upstream Wire 7.1.0 to pure Java in three commits (e4b1c11 translation, ad3d99a simplify+altitude fixes, c35c306 code-review parity fixes): full Field with EncodeMode, OneOf, Extend, the Type hierarchy (MessageType, EnumType, EnclosingType), Service, Rpc, EnumConstant, Reserved, Extensions, ProtoFile, Options with canonicalization/union/retainAll semantics, LinkedOptionEntry, Guava-free Multimap matching LinkedHashMultimap shape, ErrorCollector, SyntaxRules (proto2/proto3), LiteralValidation, Linker with FileLinker phases and CoreLoader (nine runtime-proto constants, isWireRuntimeProto, classpath loading; resources vendored byte-identical under src/main/resources), CycleChecker + internal DagChecker (Tarjan), MarkSet, SemVer, PruningRules, EmittingRules, Pruner, Schema, and SchemaProtoAdapterFactory implementing the dynamic protoAdapter on the runtime's MessageBinding/FieldOrOneOfBinding/RuntimeMessageAdapter. Tests: LinkingSmokeTest (17 linking/pruning/protoAdapter cases over an in-memory MapLoader) and SemVerTest (3); module 68/68 green; all 5 ACTIVE verify suites pass. Gates: /simplify ran with 4 parallel agents (14 simplification + 4 reuse findings triaged: applied or skipped-as-upstream-faithful with reasons recorded in ad3d99a); code-review ran at high effort comparing against the upstream Kotlin (5 parity findings, all fixed and pinned by tests in c35c306). Notable verified behaviors: proto3 unlabeled message fields encode OMIT_IDENTITY; redaction is the qualified-name .redacted heuristic (wire.redacted does not exist in 7.1.0); ranges compare structurally like IntRange; profile files deferred to TASK-16 (recorded on its task).
<!-- SECTION:FINAL_SUMMARY:END -->

## Comments

#1 - 2026-10-02 02:30 (UTC)
Starting after TASK-10 closed. Translation dependency graph mapped: SyntaxRules (needs EnumConstant, Field full with EncodeMode, ErrorCollector), Options (needs ProtoMember, Schema linker), the model types (Field full 316 lines, MessageType 285, EnumType 220, ProtoFile 256, Service 154, Rpc 105, EnumConstant 61, Reserved 39, Extensions, EnclosingType 62), then Schema 164 and Linker 588. This is a single coordinated batch of roughly 3.9k lines that cannot land piecemeal without placeholder risk; planned order: small leaves (Reserved, Extensions, EnclosingType, EnumConstant, Rpc, ErrorCollector) -> Field full + SyntaxRules -> Options + ProtoMember -> EnumType/MessageType/Service/ProtoFile -> Schema + Linker -> MarkSet/PruningRules/EmittingRules/Target/Pruner per the Task description.
