---
id: TASK-10
title: Port wire-schema parser element model to Java
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-8
dependencies:
  - TASK-9
documentation:
  - docs/decisions.md
priority: high
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Translate the public parser element model and parsing behavior used by Apicurio: ProtoParser, SyntaxReader, ProtoFileElement, MessageElement, FieldElement, EnumElement, OneOfElement, OptionElement, ReservedElement, RpcElement, ServiceElement and related types. Preserve com.squareup.wire.schema.internal.parser names and Java visibility where the compatibility matrix requires them. Replace Kotlin IntRange values with a documented pure-Java range type and migration contract for reserved and extension ranges, including endpoint conventions. Track Companion/static or constructor-call changes as source migration sites.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Parser output matches upstream Wire 7.1.0 on the applicable schema corpus for valid and invalid input, with preserved exceptions and diagnostics where required by upstream tests.
- [ ] #2 Public parser element names, accessors and entry points match the compatibility matrix; every deliberate caller change is documented for TASK-18.
- [ ] #3 Reserved and extension ranges expose a documented public Java representation whose inclusive/exclusive endpoints and maximum values are tested against upstream semantics.
- [ ] #4 Applicable parser tests run with tracked adaptations in the shared CI entry point; no Kotlin types or dependencies appear in the production API.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
