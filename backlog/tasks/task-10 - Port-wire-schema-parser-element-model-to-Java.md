---
id: TASK-10
title: Port wire-schema parser element model to Java
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-2
dependencies:
  - TASK-6
priority: high
type: feature
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
internal.parser package: ProtoParser, SyntaxReader, ProtoFileElement, MessageElement, FieldElement, EnumElement, OneOfElement, OptionElement, ReservedElement, RpcElement, ServiceElement. This is the exact surface Apicurio imports; keep classes public (Kotlin internal is public in bytecode).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ProtoParser produces element trees identical to upstream for the wire-schema test corpus
- [ ] #2 All element classes public with same names in com.squareup.wire.schema.internal.parser
<!-- AC:END -->
