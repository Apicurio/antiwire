---
id: TASK-33.1
title: >-
  Rename accessors to upstream getX()/isX() names and remove the plain-named
  duplicates
status: Done
assignee: []
created_date: '2026-10-09 15:48'
updated_date: '2026-10-09 17:53'
labels:
  - user-feedback
  - compatibility
  - api
milestone: m-11
dependencies: []
parent_task_id: TASK-33
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Maintainer decision 2026-10-09 (reply to TASK-33): change the library so Kotlin property accessors keep their upstream Java names. getName() must not be exposed as name(), and the port must not carry two names for one thing. The first translation pass (TASK-6/10/11) exposed Kotlin properties as plain methods x() or public final fields; upstream exposes getX()/isX(). The 2026-10-09 scan of Apicurio's protobuf classes built at 3620f08c found 96 of its 132 Wire references are getters. Scope: all missing getters across ported public classes; 278 in 50 classes in the user-facing scope (schema model, parser element model, targets, WireRun, SchemaLoader, ProtoAdapter, OneOf, AnyMessage), plus SchemaHandler.Context and other ported classes with an existing backing member; 13 members have no accessor at all and need one added (MarkSet getPruningRules/getTypes/getMembers, JavaTarget getAndroid/getAndroidAnnotations/getCompact/getEmitDeclaredOptions/getEmitAppliedOptions/getBuildersOnly, CustomTarget.getSchemaHandlerFactory, PruningRules isFieldRetainedVersion/isEnumConstantRetainedVersion, SemVer.getVersion); Field.getDefault replaces defaultValue(). A rename that removes the old names is acceptable because nothing has been published. Not covered: the Companion form (separate decision under TASK-33), checked-exception differences, missing constructors and classes. Process: compiler-driven rewrite on branch upstream-getter-names (declaration first, then every error site by exact line and column). Gates: full scripts/verify.sh, /simplify, /code-review high. Footprint, performance and release-candidate records become stale (DEC-13) and stay with TASK-21.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Every public non-static getX()/isX() of the real Wire 7.1.0 jars on a ported public class exists in the port with the same name and descriptor, and the plain-named x() or public field it replaced is removed (no two names for one thing); where the port had no accessor at all, one is added.
- [x] #2 All call sites in main and test code, including adapted upstream tests, use the new names; scripts/verify.sh passes all 12 suites with the same case counts as before (runtime 909, schema 633, protoc 123, compiler 189, parity 990 reconciled).
- [x] #3 A test compiled against the real 7.1.0 wire-schema-jvm jar (ProtoFileElement.getPackageName() plus a sample per class family) runs against the port without NoSuchMethodError, and the 96 getter references of the Apicurio classes built at 3620f08c resolve.
- [x] #4 docs/decisions.md records the decision, docs/compatibility-matrix.md section G and translation-conventions.md (accessor naming rule) are updated, and footprint, performance and release-candidate records are marked stale per DEC-13.
- [x] #5 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-09 result. Renamed 349 accessors across the three shipped modules (220 methods renamed, 112 public fields made private with getters, 17 getters added over existing state) plus interface implementers (MessageBinding, FieldOrOneOfBinding, Root, SchemaProtoAdapterFactory bindings). No old plain name turned out to be an upstream name too (checked per class with javap); ProtoFile.name(), Service.type()/name()/rpcs()/options(), MessageType.fields(), Message.adapter()/unknownFields() and WireField isRepeated/isPacked/isOneOf stay plain because upstream has them plain. ImmutableList/MutableOnWriteList keep size() (java.util.AbstractList) and gain getSize(); ProtoType.isScalar/isMap and OptionElement.isParenthesized became isX() methods over private fields; Field.getDefault() replaces defaultValue(); AnyMessage.getValue() is the deprecated okio bridge (ConsumerApiSurfaceTest updated).

Skipped with a reason each (55 recorded GAP rows in wire-java-generator/src/test/resources/upstream-7.1.0-getters.tsv): 34 WireCompiler options of features outside the port (Kotlin and Swift generators, DEC-6) or state kept package-private, 13 getters of classes that are package-private in the port (FileLinker, LinkedOptionEntry, PartitionedSchema(+Partition), SchemaProtoAdapterFactory, SemVer, ConsoleWireLogger), 6 with a Kotlin type in the descriptor (ProtoAdapter.getType returns KClass, MessageBinding.getMessageType, WireCompiler Kotlin enums), 1 OneOfBinding.getKeyAdapter (upstream returns Void), 1 RuntimeMessageAdapter.getJsonName(binding) Kotlin helper.

Proof: AccessorNameParityTest (406 EXPECTED rows must exist with identical parameter and return types; 55 GAP rows must carry a reason and not be stale) generated from the real 7.1.0 jars by scripts/gen-upstream-getter-baseline.py (jar SHA-256 recorded in config/parity-pins.json upstream_jars); negative checks: reverting JavaTarget.getEmitAppliedOptions fails the test, adding a getter for a GAP row (RuntimeMessageAdapter.getJsonName) fails it as a stale GAP. CompiledAgainstUpstreamTest compiles a consumer against the real wire-schema-jvm 7.1.0 jar (getPackageName, getTypes, getName, getFields, getTag, getLocation().getPath()) and runs it against the port (passes); a direct JVM run with getPackageName renamed gave InvocationTargetException (NoSuchMethodError cause), as built it printed p|M. Counts after: runtime 909/4 skipped, schema 633/6, protoc 123/49, compiler 192/31 (189 + the 3 new test cases), parity-coverage 990 reconciled, 12/12 suites PASS (verify.sh).

Review dispositions: first /code-review high (7 findings): 5 fixed (download via .part with checksum, stale-GAP check, gap reasons scoped by class and descriptor, getKeyAdapter completed across FieldBinding/OneOfBinding/SealedOneOfBinding/SchemaProtoAdapterFactory, baseline recounted to 406/55); 2 accepted: getters expose the same live collections the old public final fields exposed and upstream's final getters return them too, and private fields are a deliberate binary change under the maintainer decision (nothing published; wire-tests-java regenerates against the renamed runtime and builds green). Second /code-review high: 5 findings, all accepted as above (mutable getters match upstream; getAdapter/getSingleAdapter bodies unchanged, never memoized before either). /simplify: unused consumer imports and an unused hasField parameter removed, getter/field spacing normalized in 20 files. Left for TASK-33: the Companion form (ProtoParser.Companion and 44 others), missing constructors (PR #4), absent classes (Kotlin file facades, DirectoryRoot, EmptyWireLogger...), throws IOException differences (89 methods), 83 members with differing signatures, WireCompiler options.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Kotlin-property accessors now carry their upstream Java names in wire-runtime-java, wire-schema-java and wire-java-generator (getName(), getTypes(), getPackageName(), isMap(), ...), with no plain-named duplicate left next to a getter. 406 upstream getters are pinned by AccessorNameParityTest against a baseline generated from the real 7.1.0 jars; 55 recorded gaps carry reasons. A consumer compiled against the real wire-schema-jvm 7.1.0 runs against the port. Decision recorded in docs/decisions.md (maintainer, 2026-10-09); translation-conventions, compatibility-matrix, m1-ownership-map and api-surface updated; footprint, performance and release-candidate records carry dated DEC-13 stale notes (no measured value or acceptance row edited). Gates: verify.sh 12/12 PASS, /simplify applied, /code-review high twice with every finding fixed or dispositioned.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
