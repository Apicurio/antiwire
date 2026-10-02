---
id: TASK-20
title: Measure runtime and Apicurio schema performance before release
status: In Progress
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-11
dependencies:
  - TASK-18
documentation:
  - docs/decisions.md
priority: medium
ordinal: 20000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Benchmark antiwire against upstream Wire 7.1.0 for runtime encoding/decoding and the schema operations Apicurio uses, including parsing, linking, loading and descriptor conversion. Start baseline capture during M0 where feasible; this task performs the reproducible comparison on the integrated port. Use upstream JMH cases and representative schema corpora. Include protobuf-java only where operations are genuinely comparable, not as a substitute oracle for Wire's schema model. Record environment, forks/warmup, input sizes, allocation and variance. Define numerical thresholds only after baseline measurements. Every regression requires explicit acceptance; documentation alone is not a waiver.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 docs/performance.md provides reproducible runtime and Apicurio schema-operation results for antiwire and upstream Wire 7.1.0, with environment, input sizes, commands, forks, warmup, allocations, variance and mappings to upstream JMH cases or justified schema workloads.
- [x] #2 Baseline measurements justify the numerical thresholds or comparison criteria before the port is evaluated; results distinguish noise from measured regressions and compare protobuf-java only on equivalent operations.
- [ ] #3 Every measured regression has an explicit maintainer acceptance record or is resolved before release; missing or unaccepted results block TASK-21.
- [x] #4 The benchmark runner and reports use isolated implementations and production-policy classpaths, and do not infer performance parity from algorithm similarity.
- [x] #5 Results and regression acceptance identify the measured candidate by build revision, artifact checksums and resolved dependency identities. Relevant code, dependency, packaging or benchmark-workload changes require renewed measurements and acceptance before TASK-21; a previous candidate's records cannot approve the release.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Comments

#1 - 2026-10-02 15:00 (UTC)
Delivered: docs/performance.md (candidates 0e87bde initial, c0a2152 decode fix; re-measurement 4702ec1). Results: schema ops (the Apicurio path) FASTER (parse 1.118x, link+load 1.084x, descriptor conversion 1.160x with 22% less alloc); encode parity-or-better on 7/8 cells; decode initially weak (0.502-0.938) - root cause identified and FIXED in c0a2152 (decode(byte[])/decode(ByteString) now enter via ByteArrayProtoReader32 like upstream's commonDecode, removing the full-payload Buffer copy; byte-identical outputs verified, 869+80+122 tests green). Post-fix decode: 0.810/0.904/1.221/0.980. Regressions resolved: 3 of 4 (BytesBench.decode thin at 0.810 vs 0.800 floor, noted in doc). REMAINING PENDING (blocks TASK-21): EmailSearchBench.encodeForward 0.867 then 0.790 across two sessions, outside its own +-5% band both times; encode path is structurally identical to upstream (commonEncode = Buffer+encode+readByteArray, verified) so no decode-style structural fix exists; port-side variance wide (417-505k ops/s vs upstream stable ~607k). This is a maintainer-acceptance gate per AC#3 - not accepted here. AC#3 left unchecked pending that single acceptance; re-rolling the measurement until it passes would violate the pre-declared-band discipline.
