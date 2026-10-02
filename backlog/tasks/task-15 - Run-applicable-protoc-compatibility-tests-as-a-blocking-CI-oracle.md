---
id: TASK-15
title: Run applicable protoc compatibility tests as a blocking CI oracle
status: In Progress
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-9
dependencies:
  - TASK-14
documentation:
  - docs/decisions.md
priority: high
ordinal: 15000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Run all applicable cases from Wire 7.1.0 wire-protoc-compatibility-tests against the Java port and protoc-generated Java reference code. Pin protoc 4.36.1 and its matching descriptor sources. Inventory fixture generation and test dependencies individually: upstream Kotlin generators may run as isolated build-time tools, but JSON adapters, gRPC and excluded generator-product cases are not part of the initial shipped feature set. Retain every binary interoperability case relevant to the port. Use the pinned upstream generator for fixtures rather than waiting for TASK-16, avoiding a dependency cycle.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All applicable proto2/proto3 binary interoperability and roundtrip cases pass with protoc 4.36.1, including applicable struct, duration, instant and field-mask cases.
- [ ] #2 The case inventory covers every upstream oracle case and dependency; exclusions identify declared non-ported functionality, and adaptations preserve behavior rather than remove failing scenarios.
- [ ] #3 Fixture generation, descriptor sources and protoc versions are pinned and reproducible; reference implementations are isolated from port-under-test classes.
- [ ] #4 The complete applicable oracle suite is wired into TASK-14's shared CI runner as a blocking check on every change and leaves no required oracle case pending.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Comments

#1 - 2026-10-02 04:20 (UTC)
Starting. Upstream module inventory: 14 Kotlin test files, 4,131 lines; biggest are Proto3WireProtocCompatibilityTests (1,393), Proto2WireProtocCompatibilityTests (703), StructTest (554), InteropTest (553); plus helpers (InteropChecker, ProtocWrappersHelper, ProtocStructHelper, UnwantedValueStripper) and well-known-type round trips (Empty, Duration, Instant, FieldMask), LargeFieldNumberInteropTest, SchemaEncoderInteropTest. Upstream pins: protoc 4.36.1 (gradle artifact com.google.protobuf:protoc), protobuf-java 4.36.1. Fixture strategy per the task description: upstream wire-compiler 7.1.0 jars generate the JAVA models (squareup.proto2.java.*, squareup.proto3.java.*) exactly like scripts/generate-java-fixtures.sh; protoc 4.36.1 generates the reference protobuf-java models; gRPC/gson/moshi deps in the upstream module are DEC-6 exclusions; Kotlin-model cases (proto2.kotlin.* with sealed-oneof modes) split per case: wire-format cases port onto the java models where mechanical, Kotlin-model-only cases excluded with reasons. Plan: (1) scripts/install-protoc.sh downloading the pinned binary with checksum, (2) scripts/generate-protoc-compat-fixtures.sh running protoc --java_out + upstream wire-compiler java out over src/main/proto into a new wire-protoc-compat-java module, (3) translate the applicable tests, (4) wire the protoc-oracle suite ACTIVE in verify.sh + case-map section, (5) gates. Note: upstream src/main/proto/protos.jar is a protoPath fixture (period.proto) used for opaque-type handling.
