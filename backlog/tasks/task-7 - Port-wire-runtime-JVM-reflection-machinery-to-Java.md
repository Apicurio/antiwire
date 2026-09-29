---
id: TASK-7
title: Port wire-runtime JVM reflection machinery to Java
status: To Do
assignee: []
created_date: '2026-09-29 09:23'
labels: []
milestone: m-1
dependencies:
  - TASK-4
priority: high
type: feature
ordinal: 7000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Translate jvmMain: ProtoAdapter reflection path, Wire registry, RuntimeEnumAdapter, EnumAdapter, Message, MessageSerializedForm, AndroidMessage (compileOnly android stub), JsonIntegration and the Duration/Instant/Enum/FieldMask formatters. KotlinConstructorBuilder can delegate to the Java reflection path.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Reflection-driven encode/decode of @WireField-annotated Java classes works
- [ ] #2 Android annotations remain compileOnly, no new runtime deps
<!-- AC:END -->
