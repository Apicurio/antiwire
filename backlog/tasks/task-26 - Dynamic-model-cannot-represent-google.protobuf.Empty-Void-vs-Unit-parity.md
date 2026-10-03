---
id: TASK-26
title: Dynamic model cannot represent google.protobuf.Empty (Void vs Unit parity)
status: Done
assignee: []
created_date: '2026-10-02 00:36'
updated_date: '2026-10-03 17:28'
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
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Resolved 2026-10-03. Design: option B from the dispatch, refined through /simplify (4 angles) and /code-review (high effort, 6 findings all resolved or explicitly dispositioned). The runtime keeps ProtoAdapter.EMPTY as ProtoAdapter<Void> under the upstream name, now @Deprecated per the port's BYTES/WIRE_BYTES twin convention, and adds ProtoAdapter.UnitValue (public single-constant enum, the dynamic model's stand-in for kotlin.Unit) plus ProtoAdapter.WIRE_EMPTY (ProtoAdapter<UnitValue>). SchemaProtoAdapterFactory maps ProtoType.EMPTY to WIRE_EMPTY; RuntimeMessageAdapter registers WIRE_EMPTY beside EMPTY in MESSAGE_BACKED_BUILT_IN_ADAPTERS. Byte semantics match upstream commonEmpty (ProtoAdapter.kt:1313): adapter writes nothing, decode consumes all tags as unknown fields and returns the unit; the field framing (tag 3 + zero length, bytes 1a 00) comes from the generic field machinery and is pinned both by hand-checked bytes in DynamicSerializationTest and by a direct byte-parity test against the relocated upstream 7.1.0 oracle (WireEmptyAdapterTest.wireEmptyMatchesTheUpstreamUnitOracle, kotlin.Unit on their side, UnitValue on ours). Null still means absent, including on the proto3 optional path (message-typed proto3 fields are NULL_IF_ABSENT either way). Tests: DynamicSerializationTest.proto3TypesTest runs the upstream empty_field expectation again against UnitValue (TASK-13 adaptation comment removed), plus three dedicated cases (present-field round-trip with hand-checked bytes, null-absent with optional spelling, duplicate singular occurrences merge); WireEmptyAdapterTest (5 cases) added in wire-runtime-java. Generated code deliberately untouched: JavaGenerator still emits kotlin.Unit fields and ProtoAdapter#EMPTY (DEC-6, owner TASK-16), and the wire-protoc-compat AllEmpty fixture stays disabled for that generated-code reason, its attribution corrected from TASK-26 to TASK-16/DEC-6 in the javadoc and UPSTREAM-TEST-ADAPTATIONS.md. Docs: new api-surface.md section "The Empty unit value (TASK-26)" incl. the canonical-form table row, compatibility-matrix section E live-gap paragraph split (dynamic half resolved 2026-10-03 with pointer, generated half still open), README known-gap updated, task13-case-accounting row updated. Review dispositions: WireEmptyAdapter/EmptyAdapter kept as parallel twins (file convention, BytesAdapter/WireBytesAdapter precedent, byte-parity tests as the lockstep guard); fixed from review: merge-set comment accuracy, suppression comment extension, stale port source pointer refresh, upstream-oracle test added, EMPTY deprecated. Battery scripts/verify.sh: all 12 ACTIVE suites PASS (runtime 899 cases, schema 633, protoc-oracle 122, compiler 179).
<!-- SECTION:FINAL_SUMMARY:END -->
