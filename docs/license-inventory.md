# License inventory (TASK-21, per-file notice audit)

Audited at revision `0f860ff` (2026-10-06), refreshing the first audit at `a45b0f0`
(2026-10-02). Scope: every production source file of the three shipped modules,
`wire-runtime-java`, `wire-schema-java`, `wire-java-generator` (`src/main/java`, 161 files)
plus every embedded resource (`src/main/resources`, 9 proto files). The test module
`wire-tests-java`, the protoc-interop oracle module `wire-protoc-compat-java`, and the
parity fixture `wire-upstream-shaded` are outside the shipped inventory. All three are
test-only and never published (`maven.deploy.skip` set or inherited, per the DEC-8 guard),
so their notices have no redistribution effect. Method: each
file's leading 4 KB was scanned for every known notice form, and every class with no
same-named upstream counterpart at the pinned tag (`9f62097d`, checkout at `/tmp/wire`) was
grepped in the upstream tree to separate translated files from antiwire originals. No
vendored notice was altered. The 2026-10-06 refresh enumerated the tree at `0f860ff`
(`git ls-files` over the three modules' `src/main`), diffed it against the first audit
(`git diff --name-status a45b0f0 0f860ff`), and classified every file the diff surfaced
against its upstream counterpart. It also re-verified that the 159 first-audit files
still carry their documented notices; that re-verification corrected one provenance
classification from the first audit and corrected the header of the same file
(ProtoReader32AsProtoReader, detailed below). Every table below was recomputed
mechanically with a script; the column sums were checked against the file totals. This
inventory binds to
`0f860ff`. If any production source or resource of the three shipped modules changes after
this revision, the audit must be re-run on the final release candidate.

## Notice forms found

| Type | Form |
|---|---|
| Square Apache 2.0 | `Copyright (C) <year> Square, Inc.` (variants: `Copyright <year> Square Inc.` in `okio/ByteString.java`), followed by the Apache-2.0 grant block |
| antiwire Apache 2.0 | `Copyright (C) 2026 the antiwire authors`, followed by the Apache-2.0 grant block |
| Google Nano BSD | Leading notice `This class is derived from the CodedInputByteBuffer class in Google's "Nano" Protocol Buffer implementation`, then the Google 2013 3-clause BSD notice |
| Square + Google Nano | `ProtoWriter.java` only: Square Apache-2.0 header, then the Nano-derived notice in the class body |
| R8 BSD | `Copyright (c) 2016, the R8 project authors.`, 3-clause BSD |
| JetBrains Apache 2.0 | `Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors. Use of this source code is governed by the Apache 2.0 license.` |
| ASF header | `Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements...`, no copyright line (`okio/Base64.java` only; arrived verbatim from the vendored okio source) |
| Google protobuf BSD (resources) | `Protocol Buffers - Google's data interchange format`, `Copyright 2008 Google Inc.` with the full 3-clause conditions (7 files); `descriptor.proto` carries the short form: `Copyright 2008 Google LLC`, `Use of this source code is governed by a BSD-style license` |

## Summary counts

Java production sources per module (files; a file carrying two notices appears once per
notice in the rows below, and the per-module totals count files):

| Module | Square Apache 2.0 | antiwire Apache 2.0 | Google Nano BSD | Square + Nano | R8 BSD | JetBrains Apache 2.0 | ASF header | Total files |
|---|---|---|---|---|---|---|---|---|
| wire-runtime-java | 55 | 5 | 3 | 1 | 1 | 4 | 1 | 70 |
| wire-schema-java | 79 | 2 | 0 | 0 | 0 | 0 | 0 | 81 |
| wire-java-generator | 9 | 1 | 0 | 0 | 0 | 0 | 0 | 10 |
| **Total** | **143** | **8** | **3** | **1** | **1** | **4** | **1** | **161** |

Arithmetic check, recomputed mechanically by script: 54+6+3+1+1+4+1 = 70,
79+2 = 81, 9+1 = 10, and the total row 142+9+3+1+1+4+1 = 161; every row's cells sum to
its file total, and 70+81+10 = 161 files matches `git ls-files` over the three modules.
The columns count the notices the files carry. One translated file
(`ProtoReader32AsProtoReader.java`) carried the antiwire header where translated material
carries the Square header; the 2026-10-06 refresh corrected it to the upstream Square 2024
header (see the correction below), and the census counts the corrected notice.

Embedded resources (wire-schema-java `src/main/resources`):

| Module | Square Apache 2.0 | Google protobuf BSD | Total files |
|---|---|---|---|
| wire-schema-java | 1 (`wire/extensions.proto`) | 8 (`google/protobuf/*.proto`) | 9 |

Files with no notice at all: **0**.

## Corrections to the working assumptions

Three assumptions in circulation before this audit are corrected by reading the files:

1. The `internal/IntArrayList.java` family does **not** carry R8 notices. `IntArrayList`,
   `DoubleArrayList`, `FloatArrayList`, and `LongArrayList` carry JetBrains Apache-2.0
   notices. The only R8-notice file is `internal/MathMethods.java`, matching DEC-12.
2. `ProtoReader32AsProtoReader.java` does **not** carry a Google Nano notice; it carries
   the Square Apache-2.0 header of its upstream source (corrected 2026-10-06, below). The Nano-derived family is `ProtoReader`,
   `ProtoReader32`, and `ByteArrayProtoReader32` (leading Nano notice) plus `ProtoWriter`
   (Square header, Nano notice in body), which matches DEC-12's five BSD-carrying files.
   The first audit also called this file an antiwire original; the 2026-10-06 refresh
   corrects that below, because the upstream tree has a same-named Kotlin file at the
   pinned tag.
3. `okio/Base64.java` is not missing a header: it carries the ASF contributor-license
   header it arrived with from the vendored okio 1.17.6 source (the M0 ledger records the
   vendored buffer layer as byte-identical to 1.17.6 except two recorded annotation
   strips), so it is neither Square-headed nor unlicensed.

## antiwire-authored files

Eight files are antiwire-authored, with no same-named upstream counterpart at the pinned
tag (`Bytes.java`, added after the first audit, has no class named `Bytes` anywhere in the
upstream tree). The okio loading surface (`Path`, `FileSystem`, `FileMetadata`) is an
antiwire reimplementation
of the okio 3 API over `java.nio` per the OPEN-1 resolution and
docs/loading-api-inventory.md; `Path.java` reproduces the lexical path algorithms of okio
3.18.2's internal `Path.kt` exactly, as its javadoc records. All eight carry the
standard antiwire header:

| File | Role |
|---|---|
| `wire-runtime-java/src/main/java/com/squareup/wire/Bytes.java` | wire-owned bytes value for the JDK-typed consumer API (added after the first audit) |
| `wire-runtime-java/src/main/java/com/squareup/wire/package-info.java` | package documentation |
| `wire-runtime-java/src/main/java/okio/Path.java` | loading layer over `java.nio`; lexical algorithms reproduced from okio 3.18.2 (see above) |
| `wire-runtime-java/src/main/java/okio/FileSystem.java` | loading layer over `java.nio` |
| `wire-runtime-java/src/main/java/okio/FileMetadata.java` | loading layer over `java.nio` |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/JdkSchemaLoader.java` | JDK-typed loading facade (TASK-25) |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/package-info.java` | package documentation |
| `wire-java-generator/src/main/java/com/squareup/wire/java/package-info.java` | package documentation |

Split files translated out of larger upstream files (for example `Reflection` from the
upstream reflection machinery, `FileSystems`/`SchemaUtil`/`NameFactory` from upstream
schema internals, `CustomTarget`/`JavaTarget`/`ProtoTarget` from upstream `Target.kt`,
`ProtocolException`/`Serializable` from upstream `-Platform.kt`) carry the Square
Apache-2.0 header of their upstream sources, which is correct attribution for translated
material. `ProtoTarget.java`, added after the first audit, is a Java translation of the
`ProtoTarget` data class declared in upstream `Target.kt` at the pinned tag, and its
header is byte-identical to that upstream file's header.

## Header correction made during the 2026-10-06 refresh

`ProtoReader32AsProtoReader.java` is translated material, not an antiwire original. The
pinned upstream tree carries `wire-runtime/src/commonMain/kotlin/com/squareup/wire/ProtoReader32AsProtoReader.kt`
with a Square 2024 Apache-2.0 header; the port file mirrors its methods with the upstream
javadoc copied nearly verbatim, and docs/m1-ownership-map.md records it as translated in
TASK-6 batch 5. The first audit missed the same-named upstream file, classified the port
file as an antiwire original, and the file carried the antiwire header, which contradicted
the port's convention that translated material carries the Square header of its upstream
source (and DEC-12's verbatim per-file attribution). The copyright line of the port file
was changed from `Copyright (C) 2026 the antiwire authors` to `Copyright (C) 2024 Square,
Inc.`; the 15 header lines are now identical to the upstream file's header (checked with
`diff`). It is the only source header changed by the refresh. The change is
comment-only, so the compiled classes are unchanged in behavior, but the jar bytes and
checksums of any candidate built before this correction differ from one built after it.

## Vendored layers

- okio buffer layer (23 files with preserved notices: 22 Square Apache 2.0 plus the
  ASF-headed `Base64.java`), vendored verbatim from okio 1.17.6 under the original `okio`
  package names per OPEN-1; no okio artifact ships.
- The 8 `google/protobuf/*.proto` resources carry Google protobuf 2008 BSD notices;
  `wire/extensions.proto` carries a Square 2019 Apache-2.0 header. These are shipped
  inside the wire-schema-java jar with their notices intact.

## NOTICE and LICENSE verification (DEC-12)

The first audit flagged a discrepancy. The root `NOTICE` file stated Google LLC licenses
"the protobuf-derived code in the ProtoReader family and the R8-derived code in
internal/MathMethods" under the Apache License 2.0, while those files carry BSD-style
3-clause notices (see the table above). DEC-12 also says the
NOTICE file "carries the BSD-style notices verbatim": resolved 2026-10-02, NOTICE now
reproduces both notices verbatim. The 2026-10-06 refresh re-verified both files at
`0f860ff`. `LICENSE` is the standard Apache-2.0 text (byte-identical to the pinned
upstream checkout's `LICENSE.txt`), and both BSD blocks in `NOTICE` match their source
files word for word, with the R8 block re-wrapped exactly as NOTICE itself discloses
("line breaks as wrapped in the source file"). Scope note: the verbatim requirement was
applied to the five translated source files; the eight google/protobuf/*.proto resources
shipped in the wire-schema-java jar keep their intact in-jar Google 2008 BSD notices
(7 full 3-clause + descriptor.proto short form), covered by their presence rather than by
NOTICE reproduction.

## Per-file listing

### wire-runtime-java (70 Java files)

| File | Notice |
|---|---|
| `wire-runtime-java/src/main/java/com/squareup/wire/AnyMessage.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/ByteArrayProtoReader32.java` | Google Nano BSD |
| `wire-runtime-java/src/main/java/com/squareup/wire/Bytes.java` | antiwire Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/EnumAdapter.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/FieldEncoding.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/FieldMask.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/DoubleArrayList.java` | JetBrains Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/FieldBinding.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/FieldOrOneOfBinding.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/FloatArrayList.java` | JetBrains Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/ImmutableList.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/IntArrayList.java` | JetBrains Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/Internal.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/LongArrayList.java` | JetBrains Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/MathMethods.java` | R8 BSD |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/MessageBinding.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/MutableOnWriteList.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/OneOfBinding.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/ProtocolException.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/Reflection.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/RuntimeMessageAdapter.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/SealedOneOfBinding.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/internal/Serializable.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/KotlinConstructorBuilder.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/Message.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/MessageSerializedForm.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/OneOf.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/package-info.java` | antiwire Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/ProtoAdapter.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/ProtoReader.java` | Google Nano BSD |
| `wire-runtime-java/src/main/java/com/squareup/wire/ProtoReader32.java` | Google Nano BSD |
| `wire-runtime-java/src/main/java/com/squareup/wire/ProtoReader32AsProtoReader.java` | Square Apache 2.0 (translated file; header corrected 2026-10-06) |
| `wire-runtime-java/src/main/java/com/squareup/wire/ProtoWriter.java` | Square Apache 2.0 + Google Nano BSD (in body) |
| `wire-runtime-java/src/main/java/com/squareup/wire/ReverseProtoWriter.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/RuntimeEnumAdapter.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/Syntax.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/Wire.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/WireEnclosingType.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/WireEnum.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/WireEnumConstant.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/WireField.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/WireOneofField.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/WireRpc.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/WireSealedOneof.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/AsyncTimeout.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/Base64.java` | ASF header, no copyright line |
| `wire-runtime-java/src/main/java/okio/Buffer.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/BufferedSink.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/BufferedSource.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/ByteString.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/FileMetadata.java` | antiwire Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/FileSystem.java` | antiwire Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/ForwardingFileSystem.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/ForwardingSink.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/ForwardingSource.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/ForwardingTimeout.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/Okio.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/Options.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/Path.java` | antiwire Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/PeekSource.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/RealBufferedSink.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/RealBufferedSource.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/Segment.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/SegmentedByteString.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/SegmentPool.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/Sink.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/Source.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/Timeout.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/Utf8.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/okio/Util.java` | Square Apache 2.0 |

### wire-schema-java (81 Java files)

| File | Notice |
|---|---|
| `wire-schema-java/src/main/java/com/squareup/wire/schema/AdapterConstant.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/ClaimedDefinitions.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/ClaimedPaths.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/CoreLoader.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/CycleChecker.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/DirectedAcyclicGraph.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/EmittingRules.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/EnclosingType.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/EnumConstant.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/EnumType.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/ErrorCollector.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/EventListener.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/EventListeners.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Extend.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Extensions.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Field.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/FileLinker.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/CommonSchemaLoader.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/DagChecker.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/FileSystems.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/JvmLanguages.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/NameFactory.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/EnumConstantElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/EnumElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/ExtendElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/ExtensionsElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/FieldElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/GroupElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/MessageElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/OneOfElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/OptionElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/OptionReader.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/ProtoFileElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/ProtoParser.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/ReservedElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/RpcElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/ServiceElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/SyntaxReader.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/parser/TypeElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/ProfileFileElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/ProfileParser.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/SchemaEncoder.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/SchemaUtil.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/TypeConfigElement.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/internal/TypeMover.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/JdkSchemaLoader.java` | antiwire Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/LinkedOptionEntry.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Linker.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/LiteralValidation.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Loader.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Location.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/MarkSet.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/MessageType.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Multimap.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/OneOf.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Options.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/package-info.java` | antiwire Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/PartitionedSchema.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Profile.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/ProfileLoader.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/ProtoFile.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/ProtoMember.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/ProtoType.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Pruner.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/PruningRules.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Reserved.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Root.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Rpc.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Schema.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/SchemaException.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/SchemaHandler.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/SchemaLoader.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/SchemaProtoAdapterFactory.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/SemVer.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Service.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/SyntaxRules.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Target.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/Type.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/WireLoggers.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/WireRun.java` | Square Apache 2.0 |
| `wire-schema-java/src/main/java/com/squareup/wire/WireLogger.java` | Square Apache 2.0 |

### wire-java-generator (10 Java files)

| File | Notice |
|---|---|
| `wire-java-generator/src/main/java/com/squareup/wire/ConsoleWireLogger.java` | Square Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/DryRunFileSystem.java` | Square Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/java/JavaGenerator.java` | Square Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/java/JavaSchemaHandler.java` | Square Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/java/package-info.java` | antiwire Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/schema/CustomTarget.java` | Square Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/schema/JavaTarget.java` | Square Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/schema/ProtoTarget.java` | Square Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/WireCompiler.java` | Square Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/WireException.java` | Square Apache 2.0 |

### wire-schema-java resources (9 files)

| File | Notice |
|---|---|
| `wire-schema-java/src/main/resources/google/protobuf/any.proto` | Google protobuf BSD |
| `wire-schema-java/src/main/resources/google/protobuf/descriptor.proto` | Google protobuf BSD |
| `wire-schema-java/src/main/resources/google/protobuf/duration.proto` | Google protobuf BSD |
| `wire-schema-java/src/main/resources/google/protobuf/empty.proto` | Google protobuf BSD |
| `wire-schema-java/src/main/resources/google/protobuf/field_mask.proto` | Google protobuf BSD |
| `wire-schema-java/src/main/resources/google/protobuf/struct.proto` | Google protobuf BSD |
| `wire-schema-java/src/main/resources/google/protobuf/timestamp.proto` | Google protobuf BSD |
| `wire-schema-java/src/main/resources/google/protobuf/wrappers.proto` | Google protobuf BSD |
| `wire-schema-java/src/main/resources/wire/extensions.proto` | Square Apache 2.0 |
