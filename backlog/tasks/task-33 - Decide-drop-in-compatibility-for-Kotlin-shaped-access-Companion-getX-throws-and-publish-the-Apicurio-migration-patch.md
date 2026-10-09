---
id: TASK-33
title: >-
  Decide drop-in compatibility for Kotlin-shaped access (Companion, getX,
  throws) and publish the Apicurio migration patch
status: To Do
assignee: []
created_date: '2026-10-09 15:01'
updated_date: '2026-10-09 18:21'
labels:
  - user-feedback
  - compatibility
milestone: m-11
dependencies: []
references:
  - docs/compatibility-matrix.md
  - docs/decisions.md
  - >-
    https://github.com/Apicurio/apicurio-registry/blob/3620f08c2efa432dd93604bb413888337ce2321f/serdes/generic/serde-common-protobuf/src/main/java/io/apicurio/registry/serde/protobuf/ProtobufSchemaParser.java
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
First real user feedback (2026-10-09): Apicurio code compiled against Wire 7.1.0 (ProtobufSchemaParser at apicurio-registry 3620f08c; the call is at serde-common-protobuf ProtobufSchemaParser.java:46 and schema-validation-protobuf ProtobufSchemaParser.java:34) fails against the antiwire jars with java.lang.NoSuchFieldError: ProtoParser does not have member field 'ProtoParser$Companion Companion'. Reproduced exactly: a one-line class compiled against the real wire-schema-jvm 7.1.0 jar from Maven Central runs against upstream and throws that error against antiwire; compiling its source against antiwire gives 'cannot find symbol Companion'. This was a recorded decision (compatibility-matrix section G: ProtoParser.Companion.parse becomes a static form, owners TASK-10/TASK-18; DEC-2: no precompiled ABI promise), but the measurements below show the cost is much larger than one migration site, and a second user hit the same class of problem independently (PR #4, ProtoAdapter(FieldEncoding, Class) constructors, found by swapping Wire for antiwire in Retrofit's retrofit-converters:wire).

Evidence at the user's commit (2026-10-09 rescan): jars built from apicurio-registry 3620f08c (3.4.0-SNAPSHOT, clean detached worktree /home/pantinor/data/repo/work/wt-protobuf-3620f08c, four protobuf modules). 66 Apicurio classes reference 132 distinct com.squareup.wire members; 30 resolve in the port and 102 do not: 96 Kotlin property getters (for example ProtoFileElement.getPackageName(), reproduced as NoSuchMethodError and as a compile error), 2 Companion form (the ProtoParser.Companion field and ProtoParser$Companion.parse), 2 constructors (ProtoParser(Location, char[]) is private in the port; the OneOf constructor descriptor differs, not run down), 2 other (MessageType.toElement(), Schema.protoFile(okio.Path)). All 132 members exist in the upstream 7.1.0 model, so the scan is sound. The build pins wire-schema 6.4.0; which Wire jar it resolved was not verified, and references were scanned, not behavior.

Evidence across the whole upstream surface (javap of the real 7.1.0 jars wire-schema-jvm, wire-runtime-jvm, wire-java-generator, wire-compiler against the built antiwire jars): (1) Companion: 45 upstream public classes have a static Companion field, the port has 0. Of 67 public Companion methods (excluding AndroidMessage and ManifestModule), 24 are reachable upstream only as X.Companion.m(); the port has a same-descriptor static for 18 and nothing for 6 (WireLogger.NONE getter, AdapterConstant.invoke, Options.GOOGLE_PROTOBUF_OPTION_TYPES, ProtoFile.JAVA_PACKAGE, ProtoFile.WIRE_PACKAGE, SemVer.toLowerCaseSemVer; only same-name static methods were searched, not fields). The other 43 have an @JvmStatic twin upstream; 7 are missing in the port (WireCompiler.forArgs x4, EnumType.fromElement, MessageType.fromElement, ProtoMember.get(ProtoType, Field)). (2) Getters: the port exposes x() or public final fields where Kotlin exposes getX(). (3) Checked exceptions: the port declares throws IOException on 89 methods where upstream declares none (Message.encode(), SchemaLoader.loadSchema(), AnyMessage.unpack, Internal.*, the reader family); binary-safe, but it breaks the source of callers without try/catch; one reverse case (WireCompiler). Not triaged: 83 members whose signatures differ, 18 absent Kotlin file-facade classes, 13 absent public non-Companion classes (DoubleProtoAdapter etc., DirectoryRoot, EmptyWireLogger), and public constructor signatures (PR #4 found two missing ProtoAdapter constructors this way).

Delivery gap: the TASK-18 migration (23 Apicurio files rewritten onto the port, ANTIWIRE_MIGRATION.md, antiwire-parity/ drivers) is not located. Commits a11d7cfa and 6ce5582c are not valid objects in the Apicurio clones on bird or on the Mac, and no antiwire-integration branch exists in either Apicurio clone or either antiwire repo (see the notes). A consumer cannot follow the 'limited documented source migration' DEC-2 promises until it is located or redone. The scan history, including the superseded 3.3.4-SNAPSHOT counts, is in the notes.

Options for the maintainer (policy call under DEC-2): (a) keep the current API shape and publish the migration patch plus a migration guide listing the three classes of change; (b) additionally add mechanical shims: a nested Companion class plus static Companion field per upstream class (legal in Java, matches the Kotlin descriptors, so source and binary callers both work), getX() bridges next to x()/fields, and missing constructors; a plain jar swap then works, at the price of two names on the public API; (c) a subset, for example Companion shims only. Recommendation: do (a) now, decide (b) with the maintainer.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The Apicurio migration (TASK-18 branch diff and ANTIWIRE_MIGRATION.md) is available from this repository or a published artifact, with a migration guide listing the Companion, getter and checked-exception changes.
- [ ] #2 The maintainer's decision on shims (a, b or c) is recorded in docs/decisions.md with the measured counts above.
- [ ] #3 If shims are chosen, a test proves a class compiled against the real Wire 7.1.0 jar for ProtoParser.Companion.parse (and the chosen getters) runs against the port, and docs/compatibility-matrix.md section G is updated.
- [ ] #4 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-09 correction (from the apicurio-registry agent, checked against its clone): the Apicurio target jars I scanned are 3.3.4-SNAPSHOT builds dated 2026-09-24 14:27-14:29, built from ce003bf8e plus an unproven working tree. 48e2742ac has identical sources for the four protobuf modules but is not an ancestor of 3620f08c, and those modules changed in 9 files between them (MessageIndexesUtil, ProtobufFile, two poms among them). So the scan counts in the description (120 referenced members, 30 resolved, 84 getters) describe the 3.3.4-SNAPSHOT builds, not 3620f08c. The ProtoParser.Companion.parse call is confirmed at 3620f08c in two files: serdes/generic/serde-common-protobuf/.../ProtobufSchemaParser.java:46 and schema-validation/protobuf/.../ProtobufSchemaParser.java:34. Both poms pin wire-schema 6.4.0. A rescan against jars built at 3620f08c is pending (the apicurio-registry agent is building them in a worktree).

Delivery gap is worse than stated: commits a11d7cfa and 6ce5582c are not valid objects in the Apicurio clones on bird or on the Mac, and no antiwire-integration branch exists in the Apicurio clone or in the antiwire repos on either host. The TASK-18 migration (23 files, ANTIWIRE_MIGRATION.md) may therefore be lost; it must be located or redone before AC#1 can be met.

2026-10-09 rescan at the user's commit (supersedes the pending-rescan remark and the 3.3.4-SNAPSHOT counts above): jars built by the apicurio-registry agent from apicurio-registry 3620f08c2efa432dd93604bb413888337ce2321f (clean detached worktree /home/pantinor/data/repo/work/wt-protobuf-3620f08c, 3.4.0-SNAPSHOT, mvnw package -DskipTests, BUILD SUCCESS; not installed to ~/.m2): utils/protobuf-schema-utilities, schema-util/protobuf, serdes/generic/serde-common-protobuf, schema-validation/protobuf. 66 Apicurio classes reference 132 distinct com.squareup.wire members; 30 resolve in the port and 102 do not: 96 Kotlin property getters, 2 Companion form (ProtoParser.Companion field and ProtoParser$Companion.parse), 2 constructors (ProtoParser(Location, char[]) is private in the port, OneOf constructor descriptor differs), 2 other (MessageType.toElement(), Schema.protoFile(okio.Path)). Sanity check: all 132 referenced members exist in the upstream 7.1.0 model, so the scan itself is sound. Both ProtobufSchemaParser classes (serde-common-protobuf and schema-validation-protobuf) start with getstatic ProtoParser.Companion, which is exactly the user's NoSuchFieldError. The build pins wire-schema 6.4.0 (property wire-schema.version); I did not verify which Wire jar the build resolved, and I scanned references, not behavior, so a member-level match against 7.1.0 is the evidence, not a run against upstream 6.4.0.

2026-10-09 history note: the first scan of the Apicurio target jars (3.3.4-SNAPSHOT, built 2026-09-24 from ce003bf8e plus an unproven tree, not from 48e2742 or 3620f08c) gave 120 referenced members, 30 resolved, 84 getters and 6 other (Companion form, private ProtoParser constructor, MessageType.toElement(), Schema.protoFile(okio.Path), OneOf constructor). The rescan at 3620f08c splits the same kinds into 2 Companion form, 2 constructors and 2 other, so the 'other' counts of the two scans are not comparable. The description now quotes only the 3620f08c numbers; AC#2's 'measured counts above' means those.

2026-10-09: PR #4 (EricWittmann, open, unreviewed) adds the two missing ProtoAdapter(FieldEncoding, Class) and (FieldEncoding, Class, String) constructors, found by swapping Wire for antiwire in Retrofit; it is further evidence for option (b) and for TASK-34. My javap comparison missed this class (constructors with the same name and different signatures).

2026-10-09 verified (supersedes 'may be lost'): the TASK-18 branch existed on the Mac at /tmp/apicurio and was erased (4058 directories, 0 files, empty .git/objects; Mac rebooted 2026-10-02 16:51; likely macOS /tmp cleanup, unconfirmed); it is not on bird. AC#1 therefore means redoing the migration, not locating it. Redo it on a new branch cut from apicurio-registry 3620f08c (the user's commit) in a persistent location, push it, and keep ANTIWIRE_MIGRATION.md and the parity drivers in a tracked path, never /tmp. With the 2026-10-09 getter rename (TASK-33.1) done, the migration no longer needs getter rewrites; what remains for the four protobuf modules at 3620f08c is the ProtoParser.Companion form, the ProtoParser constructor, the OneOf constructor, MessageType.toElement() and Schema.protoFile(okio.Path), plus the loader rewrite onto JdkSchemaLoader.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
