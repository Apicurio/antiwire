---
id: TASK-6
title: Port the compiling runtime foundation and encoding core to Java
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-7
dependencies:
  - TASK-5
documentation:
  - docs/decisions.md
  - docs/research-wire-java-port-2026-09-29.md
priority: high
ordinal: 6000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Port the encoding foundation on the I/O strategy proven in M0. Own ProtoReader, ProtoReader32, ByteArrayProtoReader32, ProtoWriter, ReverseProtoWriter, FieldEncoding, Syntax and ProtoAdapter plus the real Message, well-known-type and Internal implementations required for this slice to compile and run. ProtoAdapter references Duration, Instant and FieldMask, so these cannot be postponed as undefined classes to TASK-8. Record symbol ownership before translation. TASK-7 extends the reflection path and TASK-8 finishes remaining helpers and edge behavior; neither duplicates foundational types. Preserve source notices and production dependency policy.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A symbol-ownership map identifies every foundation class or method implemented here and every extension left to TASK-7 or TASK-8, without duplicate type ownership or compilation cycles.
- [ ] #2 The Java runtime foundation compiles and runs without relying on future tasks, upstream runtime implementations or placeholder methods for exercised behavior.
- [ ] #3 Forward and reverse encoding and decoding match upstream Wire 7.1.0 for the documented corpus, including malformed inputs and required well-known-type adapters.
- [ ] #4 Generated Java fixture code compiles and executes against the foundation using the approved namespace mapping and ReverseProtoWriter support.
- [ ] #5 The shared CI runner exercises this slice and confirms Java 11 bytecode, pure-Java production dependencies and preserved provenance.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
