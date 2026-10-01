---
id: TASK-11
title: 'Port wire-schema Schema, linker, and options to Java'
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
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
- [ ] #1 Schema/linker results match upstream Wire 7.1.0 on applicable multi-file, imports, options, pruning and error cases, using TASK-10's parser and range representation.
- [ ] #2 Schema.protoAdapter encodes and decodes dynamic messages against upstream reference cases without relying on future generator implementation.
- [ ] #3 Core loading and linking compile on the chosen I/O boundary; every profile-related case assigned to TASK-16 is recorded in the shared applicability inventory.
- [ ] #4 Relevant tests run in the shared CI entry point and production APIs/dependencies satisfy docs/decisions.md.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
