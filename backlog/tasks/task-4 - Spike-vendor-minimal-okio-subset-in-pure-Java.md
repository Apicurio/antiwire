---
id: TASK-4
title: 'Spike: vendor minimal okio subset in pure Java'
status: To Do
assignee: []
created_date: '2026-09-29 09:22'
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
- [ ] #1 ByteString, Buffer, Source/Sink, BufferedSource/Sink, Utf8 translated to Java in an internal package
- [ ] #2 Ported okio JVM unit tests for the vendored classes pass
- [ ] #3 Decision recorded: whether GzipSource is needed by the schema loader, and if not, omitted
<!-- AC:END -->
