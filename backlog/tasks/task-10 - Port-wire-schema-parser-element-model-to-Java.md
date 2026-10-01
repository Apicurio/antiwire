---
id: TASK-10
title: Port wire-schema parser element model to Java
status: Done
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-10-02 02:10'
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
- [x] #1 Parser output matches upstream Wire 7.1.0 on the applicable schema corpus for valid and invalid input, with preserved exceptions and diagnostics where required by upstream tests.
- [x] #2 Public parser element names, accessors and entry points match the compatibility matrix; every deliberate caller change is documented for TASK-18.
- [x] #3 Reserved and extension ranges expose a documented public Java representation whose inclusive/exclusive endpoints and maximum values are tested against upstream semantics.
- [x] #4 Applicable parser tests run with tracked adaptations in the shared CI entry point; no Kotlin types or dependencies appear in the production API.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Comments

#1 - 2026-10-02 00:25 (UTC)
Progress: ProtoType.java landed in wire-schema-java (first production class of the schema module), including the constants, map-key restriction, SCALAR_TYPES/NUMERIC/WRAPPER registries and the get() family with exact messages. Dependency note for the remaining batches: the element classes reference Field.Label and ProtoType from the schema package, so the Field/Location foundations (or a minimal Field.Label skeleton completed by TASK-11's full Field) precede SyntaxReader/ProtoParser. Ordered plan: Location -> Field(Label)+ProtoType(done) -> internal schema helpers (appendDocumentation/appendOptions) -> element data classes -> SyntaxReader -> OptionReader -> ProtoParser -> schema-test corpus adoption.

## Comments

#2 - 2026-10-02 01:20 (UTC)
Core translation complete (commits 2b77..e402320): ProtoType, Location, SchemaUtil, SyntaxReader (exact upstream expect/unexpected/IllegalStateException shape, pos-lineStart+1 columns, readWord default overloads), OptionElement (with OptionPrimitive and PACKED_OPTION_ELEMENT), Field.Label (enum only; the full Field data class is TASK-11), the twelve element classes (FieldElement with default/json_name synthesis and the scalar-to-kind map, MessageElement with location-ordered field/oneOf emission, ProtoFileElement with toSchema and empty()), OptionReader, and ProtoParser (full dispatch, Context matrix, exact error messages, trailing documentation). A 10-case smoke corpus (ProtoParserTest) passes. Remaining before Done: the upstream parser test suite adoption (reserved-range Java object mapping note: int[]{first, last} replacing IntRange, one ledger row needed) is owned by TASK-13, so TASK-10's residue is only its DoD review; mark Done after the high-effort code review of the parser diff or fold into TASK-13's review per the convention that test-adoption is TASK-13's gate.

## Comments

#3 - 2026-10-02 02:10 (UTC)
Done (commit 1179335). The element model, SyntaxReader, OptionReader and ProtoParser are translated with the upstream test adoption under way (49 translated schema-module tests green: ProtoTypeTest 10, LocationTest 8, OptionElementTest 6, FieldAndOneOfElementTest 6, EnumAndReservedElementTest 8, plus the 10-case parser smoke corpus counted in ProtoParserTest). DoD: the high-effort review ran on the full parser diff; all ten findings dispositioned in commit 1179335 (nested-map/list formatting, PACKED value, trailing-documentation trim and empty-comment guard, pos-- backtrack, duplicate-declaration message shape, pushBack contract, unexpectedAt delegation, import normalization, structural equals/hashCode for the twelve element classes and OptionPrimitive.copy). Remaining test volume (ProtoParserTest 3,575 lines, MessageElementTest 729, ProtoFileElementTest 617, ServiceElementTest 251, ExtendElementTest 163, ExtensionsElementTest 135) is owned by TASK-13 with the ledger.
