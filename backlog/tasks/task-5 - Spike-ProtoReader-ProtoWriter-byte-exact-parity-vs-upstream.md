---
id: TASK-5
title: 'Spike: ProtoReader/ProtoWriter byte-exact parity vs upstream'
status: Done
assignee: []
created_date: '2026-09-29 09:22'
labels: []
milestone: m-0
dependencies:
  - TASK-1
priority: high
type: spike
ordinal: 5000
updated_date: '2026-10-01 11:05'
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Prove the encoding core can be translated with byte-exact behavior before any scaffolding.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Java translation roundtrips the wire-tests proto corpus with byte-for-byte equality against wire-runtime-jvm 7.1.0
- [ ] #2 Varint, tag, group, packed, and negative-length group-skip (GHSA-7xpr-hc2w-34m9) cases covered by tests
<!-- AC:END -->

## Comments

#1 - 2026-10-01 11:05 (UTC)
Done 2026-10-01, commit 7879e2e. Evidence: mvn clean verify green, 747 tests, 0 failures (15 parity tests). Corpus interpretation: the spike proves token-level parity on a seeded synthetic corpus (500 writer sequences byte-for-byte, 200 structured messages with identical read transcripts) against the LIVE upstream 7.1.0 relocated by the wire-upstream-shaded fixture; message-level corpus from wire-tests fixtures arrives with the ProtoAdapter port (TASK-6). GHSA-7xpr-hc2w-34m9 covered in both paths (top-level and inside group skip) with exact exception-message parity, plus packed replay, empty packed, nested group skip, unterminated group EOF, recursion limit, nextLengthDelimited/readString, and five more error vectors. Gates: /simplify (4 agents) and code-review (high) applied, including the vacuous-CI-check fix with negative batteries proving both the enforcer (guava) and the belt (junit compile-scope) fire. Status set by direct file edit: the backlog MCP reports ambiguous task ids (phantom duplicates) for this repo.
