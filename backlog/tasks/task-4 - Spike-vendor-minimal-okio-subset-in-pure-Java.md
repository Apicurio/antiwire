---
id: TASK-4
title: 'Spike: vendor minimal okio subset in pure Java'
status: Done
assignee: []
created_date: '2026-09-29 09:22'
updated_date: '2026-10-01 07:05'
labels: []
milestone: m-0
dependencies: []
priority: high
type: spike
ordinal: 4000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
okio is embedded in wire's public API (ByteString, BufferedSource/Sink). okio-jvm is Kotlin so depending on it defeats the port. Vendor the needed subset translated to Java, and port okio's own JVM unit tests for the vendored classes.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 ByteString, Buffer, Source/Sink, BufferedSource/Sink, Utf8 translated to Java in an internal package
- [x] #2 Ported okio JVM unit tests for the vendored classes pass
- [x] #3 Decision recorded: whether GzipSource is needed by the schema loader, and if not, omitted
<!-- AC:END -->

## Comments

#1 - 2026-10-01 07:05 (UTC)
Done 2026-10-01, commit 54d6656. Deviation from AC wording, by decision D5: the subset is vendored in the `okio` package (not an internal one) so upstream test sources import it untouched. 24 classes vendored 1:1 from okio 1.17.6 (jsr305 + Animal Sniffer strips recorded in D5 with the re-sync note); okio's own 1.17.6 JVM tests vendored verbatim: 732 run, 0 failures, 4 upstream skips (fresh run before commit). Gzip family omitted per D5 after import-inventory verification. Base is okio 1.17.6 with okio 3.18 as semantic reference; remaining surface gaps (okio 3 members wire calls) surface compile-driven in TASK-5/M1. Gates: /simplify (4 agents) and code-review (high, ran its own scratch-build verification) on this diff; pa:reflect ALIGNED; verification-before-completion fresh. Status updated by direct file edit because the backlog MCP reported an ambiguous task-4 id (phantom duplicate candidates; only one task-4 file exists on disk).
