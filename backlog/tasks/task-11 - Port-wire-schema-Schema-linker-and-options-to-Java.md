---
id: TASK-11
title: 'Port wire-schema Schema, linker, and options to Java'
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-2
dependencies:
  - TASK-6
  - TASK-8
priority: high
type: feature
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Schema, SchemaLoader/Loader, ProtoFile, MessageType, Type, Field, Service, Pruner, Root, profile wiring, option validation, munger/linker internals from commonMain.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Linker output (Schema model) identical to upstream for the schema test corpus
- [ ] #2 Schema.protoAdapter path works for schema serialization
<!-- AC:END -->
