---
id: TASK-28
title: >-
  Zero-copy bytes decode: adoptable byte[] reader so WireBytesAdapter.decode
  pays one copy, not two
status: In Progress
assignee: []
created_date: '2026-10-03 13:00'
labels:
  - performance
  - no-okio
dependencies: []
references:
  - docs/performance.md
  - docs/api-surface.md
type: enhancement
ordinal: 28000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Session-4 performance renewal (candidate c713e9a, docs/performance.md) confirmed the phase-2 pre-declared cost as a real regression: BytesBench.decode reads 0.524 port/upstream (session 3: 0.797) with gc.alloc.rate.norm up 93.2% (19,020 -> 36,752 B/op). Mechanism, closed by allocation arithmetic: WireBytesAdapter.decode (ProtoAdapter.java, WireBytesAdapter) does Bytes.fromByteString(reader.readBytes()) — the reader's okio read copy plus the cross-package fromByteString clone, two copies per bytes value; endMessageAndGetUnknownFieldsBytes has the same shape. docs/api-surface.md phase-2 section pre-declared exactly this: "a reader that hands out an adoptable byte[] would pay one" copy. Fix shape: a ProtoReader32 read path that hands the freshly-read byte[] (or a slice view) to Bytes.takeOwnership(byte[]) without cloning, for both known bytes fields and unknown-fields collection, preserving the byte-identical wire contract (IdentityCheck fingerprints must not move). Acceptance: bytes decode back inside its oracle band with allocation at or below the 19,064 B/op session-3 level, renewed measurements recorded per the docs/performance.md invalidation rule.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
