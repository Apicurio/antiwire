---
id: TASK-7
title: Port wire-runtime JVM reflection machinery to Java
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-7
dependencies:
  - TASK-6
documentation:
  - docs/decisions.md
priority: high
ordinal: 7000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Complete runtime JVM reflection and serialization behavior on TASK-6's compiling foundation. Own the remaining ProtoAdapter reflection path, Wire registry, RuntimeMessageAdapter, enum reflection, Message serialization and Java-facing formatter behavior identified by the symbol-ownership map. Do not recreate Message or adapter types already owned by TASK-6. Inventory Kotlin-constructor fixture behavior: adapt test-only fixtures where required without weakening relevant runtime scenarios. Android-specific APIs require an explicit applicability decision; do not accidentally introduce Android or Kotlin runtime dependencies.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Reflection-driven encode/decode and serialization of annotated Java classes match Wire 7.1.0 on applicable upstream scenarios, including errors and unknown fields.
- [ ] #2 The implementation extends TASK-6's ownership map without duplicate production classes; required tests run in the shared CI entry point.
- [ ] #3 Kotlin fixture interoperability and any Android-specific exclusions are recorded against the approved scope; no relevant runtime case is silently omitted.
- [ ] #4 Production dependencies remain pure Java with no Kotlin; Android annotations or stubs, if needed for retained APIs, remain compile-only.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
