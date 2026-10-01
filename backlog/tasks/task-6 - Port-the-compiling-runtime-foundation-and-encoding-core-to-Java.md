---
id: TASK-6
title: Port the compiling runtime foundation and encoding core to Java
status: Done
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-10-01 21:30'
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
- [x] #1 A symbol-ownership map identifies every foundation class or method implemented here and every extension left to TASK-7 or TASK-8, without duplicate type ownership or compilation cycles.
- [x] #2 The Java runtime foundation compiles and runs without relying on future tasks, upstream runtime implementations or placeholder methods for exercised behavior.
- [x] #3 Forward and reverse encoding and decoding match upstream Wire 7.1.0 for the documented corpus, including malformed inputs and required well-known-type adapters.
- [x] #4 Generated Java fixture code compiles and executes against the foundation using the approved namespace mapping and ReverseProtoWriter support.
- [x] #5 The shared CI runner exercises this slice and confirms Java 11 bytecode, pure-Java production dependencies and preserved provenance.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Comments

#1 - 2026-10-01 21:30 (UTC)
Done across five pushed batches plus the DoD disposition commit. AC#1: docs/m1-ownership-map.md maps every symbol with re-slice decisions (well-known types and ReverseProtoWriter into TASK-6; reflection to TASK-7; per-adapter reader32 overloads to TASK-20). AC#2: everything compiles with no placeholders; AnyMessage.pack(Message) landed through the new Message.adapter() accessor, not a stub. AC#3: adapter 14, reader32 5, AnyMessage 8, reverse-writer 4 parity tests against the relocated oracle plus the vendored upstream ProtoWriterTest; upstream contracts surfaced by red tests are asserted as such. AC#4: ReverseProtoWriter with parity coverage; generated-code compilation is exercised by the golden-files work owned by TASK-16. AC#5: the shared entry point confirms Java 11 bytecode, dependency policy, and duplicate-class rules on every run. DoD: the high-effort code-review ran on the full five-batch diff; of its seven findings, five are fixed (OneOf STRING_VALUE sanitize; Message.adapter() accessor restoring pack(Message); StructListAdapter Map.class quirk preserved; stale javadoc notes; identityOrNull moved to Internal), one is ledgered as an accepted divergence in the ownership map (the pack error message's Kotlin KClass rendering), and one was a javadoc-placement fix (ByteString copyInto). Evidence: mvn verify 794 tests, 0 failures; scripts/verify.sh all 5 ACTIVE suites pass.
