---
id: TASK-26
title: Dynamic model cannot represent google.protobuf.Empty (Void vs Unit parity)
status: To Do
assignee: []
created_date: '2026-10-02 00:36'
labels:
  - parity
  - dynamic-model
milestone: m-8
dependencies: []
priority: medium
type: bug
ordinal: 25000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The dynamic (schema.protoAdapter) Map model cannot represent google.protobuf.Empty fields: upstream Kotlin's ProtoAdapter.EMPTY is ProtoAdapter&lt;Unit&gt;, so a proto3 Empty field round-trips as the key mapped to Unit; the port's EMPTY is ProtoAdapter&lt;Void&gt;, whose decode returns null (RuntimeMessageAdapter then drops the key from the decoded Map) and whose encode path cannot accept any value (ClassCastException for non-null, field skipped for null). Found by the TASK-13 adoption of upstream DynamicSerializationTest.proto3TypesTest, which omits the empty_field entry with an in-file adaptation comment. Wire-level impact is limited (an empty message field encodes as a zero-length value; skipping it makes the field absent, which decodes back to the same null), but the model-level parity diverges: upstream's decoded map contains the key. Candidate directions: a unit-like singleton marker for Empty in the Java runtime's dynamic path, or documenting the divergence in compatibility-matrix.md and the TASK-25 JDK-typed API surface where the Unit question lands anyway. Coordinate with TASK-25 (D5a layer 3) before choosing.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
