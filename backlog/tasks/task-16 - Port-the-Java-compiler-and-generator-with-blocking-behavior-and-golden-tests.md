---
id: TASK-16
title: Port the Java compiler and generator with blocking behavior and golden tests
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
priority: medium
ordinal: 16000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Port the Java-target CLI and Java generator in the optional module created by TASK-2. Complete the Java-profile and AdapterConstant support separated in TASK-12. Remove KotlinPoet, Kotlin/Swift generator and Kotlin-backed CLI dependencies from production; a reviewed pure-Java JavaPoet dependency is allowed. Preserve every applicable Java-target compiler case, including cases in mixed upstream suites and profile tests deferred from schema. The pinned Java golden corpus is limited, so golden identity alone does not establish coverage. Reconcile golden comparisons with the namespace strategy chosen in M0 and compile/run generated output.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The Java CLI, generator and profile support pass all applicable upstream Java-target compiler and profile cases; every case deferred from TASK-13 is executed or excluded solely for a declared non-ported feature.
- [ ] #2 Java generated output is byte-identical where the compatibility contract preserves output; any required mechanical namespace/API mapping is bounded, documented, reviewed and checked automatically against pinned upstream goldens.
- [ ] #3 Generated Java fixtures compile and run against the port, with positive and negative compiler cases beyond the limited golden corpus.
- [ ] #4 Compiler/profile tests and golden comparisons run as blocking jobs in TASK-14's shared CI entry point; no required generator-owned case remains pending.
- [ ] #5 The optional generator artifact and its transitives contain no Kotlin; runtime/schema remain independently consumable without generator dependencies, and all retained Java dependencies satisfy the recorded policy.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Comments

#1 - 2026-10-02 01:10 (UTC)
From TASK-11 close-out: the profile layer was deliberately not ported with the schema linking core. Waiting for this task: upstream wire-schema Profile.kt, ProfileLoader.kt, and internal/{ProfileFileElement, ProfileParser, TypeConfigElement}.kt, plus their tests. CoreLoader's classpath loading and the runtime-proto resource layout landed in TASK-11 (wire-schema-java/src/main/resources); the general file-system loading (SchemaLoader/CoreLoader fs wiring/Root) is TASK-12's.

#2 - 2026-10-02 03:10 (UTC)
TASK-13 closed with deferrals owned here. Beyond the profile files below: upstream SchemaHandlerTest, ManifestPartitionTest, TypeMoverTest (PartitionedSchema's DirectedAcyclicGraph is already ported to main), ProfileParserTest/ProfileLoaderTest, and the 3 @Disabled profile cases inside wire-schema-java SchemaLoaderTest (bodies preserved verbatim as comments there). The complete table is docs/task13-case-accounting.md "Deferred with an owner".

#2 - 2026-10-02 05:35 (UTC)
Scope inventory: upstream JavaGenerator is ALREADY JAVA (wire-java-generator/src/main/java/com/squareup/wire/java/JavaGenerator.java, 2,489 lines; deps: palantir javapoet, guava [banned in compile scope -> de-guava during adaptation], wire-schema internal JvmLanguages helpers). Kotlin surface to port: JavaSchemaHandler.kt (127), JvmLanguages.kt, WireCompiler.kt (577), wire-schema's WireRun.kt (416), SchemaHandler.kt (245), PartitionedSchema.kt (134), EventListener.kt (112), EventListeners.kt (60), Profile.kt (22), ProfileLoader.kt (21), AdapterConstant.kt (42), internal/{ProfileFileElement 80, ProfileParser 122, TypeConfigElement 42, TypeMover 233}; plus Target.kt already noted. Tests to adopt: wire-java-generator tests (JavaGeneratorTest, goldens in wire-golden-files), wire-compiler WireCompilerTest, and the TASK-13 deferrals (SchemaHandlerTest, ManifestPartitionTest, TypeMoverTest, ProfileParserTest, ProfileLoaderTest, 3 SchemaLoaderTest profile cases). Plan: (A) schema-side machinery batch, (B) generator+CLI batch, (C) test adoption incl. deferrals + compiler-tests suite ACTIVE, then gates.
