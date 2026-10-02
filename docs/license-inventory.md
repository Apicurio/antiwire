# License inventory (TASK-21, per-file notice audit)

Audited at revision `a45b0f0` (2026-10-02). Scope: every production source file of the
three shipped modules, `wire-runtime-java`, `wire-schema-java`, `wire-java-generator`
(`src/main/java`, 159 files) plus every embedded resource (`src/main/resources`, 9 proto
files). Test modules (`wire-tests-java`, `wire-protoc-compat-java`) and the never-published
parity fixture (`wire-upstream-shaded`) are not shipped and are out of scope. Method: each
file's leading 4 KB was scanned for every known notice form, and every class with no
same-named upstream counterpart at the pinned tag (`9f62097d`, checkout at `/tmp/wire`) was
grepped in the upstream tree to separate translated files from antiwire originals. No
vendored notice was altered.

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
| wire-runtime-java | 54 | 5 | 3 | 1 | 1 | 4 | 1 | 69 |
| wire-schema-java | 79 | 2 | 0 | 0 | 0 | 0 | 0 | 81 |
| wire-java-generator | 8 | 1 | 0 | 0 | 0 | 0 | 0 | 9 |
| **Total** | **141** | **8** | **3** | **1** | **1** | **4** | **1** | **159** |

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
2. `ProtoReader32AsProtoReader.java` does **not** carry a Google Nano notice; it is an
   antiwire original carrying the antiwire header. The Nano-derived family is `ProtoReader`,
   `ProtoReader32`, and `ByteArrayProtoReader32` (leading Nano notice) plus `ProtoWriter`
   (Square header, Nano notice in body), which matches DEC-12's five BSD-carrying files.
3. `okio/Base64.java` is not missing a header: it carries the ASF contributor-license
   header it arrived with from the vendored okio 1.17.6 source (the M0 ledger records the
   vendored buffer layer as byte-identical to 1.17.6 except two recorded annotation
   strips), so it is neither Square-headed nor unlicensed.

## antiwire-authored files

Eight files are antiwire originals (no upstream counterpart at the pinned tag; the okio
loading surface is confirmed original by the OPEN-1 resolution and
docs/loading-api-inventory.md). All eight already carry the standard antiwire header, so
no header fixes were needed in this audit:

| File | Role |
|---|---|
| `wire-runtime-java/src/main/java/com/squareup/wire/package-info.java` | package documentation |
| `wire-runtime-java/src/main/java/com/squareup/wire/ProtoReader32AsProtoReader.java` | reader adapter (original) |
| `wire-runtime-java/src/main/java/okio/Path.java` | original loading layer over `java.nio` |
| `wire-runtime-java/src/main/java/okio/FileSystem.java` | original loading layer over `java.nio` |
| `wire-runtime-java/src/main/java/okio/FileMetadata.java` | original loading layer over `java.nio` |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/JdkSchemaLoader.java` | JDK-typed loading facade (TASK-25) |
| `wire-schema-java/src/main/java/com/squareup/wire/schema/package-info.java` | package documentation |
| `wire-java-generator/src/main/java/com/squareup/wire/java/package-info.java` | package documentation |

Split files translated out of larger upstream files (for example `Reflection` from the
upstream reflection machinery, `FileSystems`/`SchemaUtil`/`NameFactory` from upstream
schema internals, `CustomTarget`/`JavaTarget` from upstream `Target.kt`,
`ProtocolException`/`Serializable` from upstream `-Platform.kt`) carry the Square
Apache-2.0 header of their upstream sources, which is correct attribution for translated
material.

## Vendored layers

- okio buffer layer (23 files with preserved notices: 22 Square Apache 2.0 plus the
  ASF-headed `Base64.java`), vendored verbatim from okio 1.17.6 under the original `okio`
  package names per OPEN-1; no okio artifact ships.
- The 8 `google/protobuf/*.proto` resources carry Google protobuf 2008 BSD notices;
  `wire/extensions.proto` carries a Square 2019 Apache-2.0 header. These are shipped
  inside the wire-schema-java jar with their notices intact.

## Open discrepancy flagged for the maintainer

The root `NOTICE` file states Google LLC licenses "the protobuf-derived code in the
ProtoReader family, and R8-derived code in MathMethods" under the Apache License 2.0, but
those files carry BSD-style 3-clause notices (see the table above). DEC-12 also says the
NOTICE file "carries the BSD-style notices verbatim", which it currently does not. This
audit does not edit NOTICE (its correction is a maintainer decision about attribution
wording, not a missing-header fix); the discrepancy is recorded here so it is not lost.

## Per-file listing

### wire-runtime-java (69 Java files)

| File | Notice |
|---|---|
| `wire-runtime-java/src/main/java/com/squareup/wire/AnyMessage.java` | Square Apache 2.0 |
| `wire-runtime-java/src/main/java/com/squareup/wire/ByteArrayProtoReader32.java` | Google Nano BSD |
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
| `wire-runtime-java/src/main/java/com/squareup/wire/ProtoReader32AsProtoReader.java` | antiwire Apache 2.0 |
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

### wire-java-generator (9 Java files)

| File | Notice |
|---|---|
| `wire-java-generator/src/main/java/com/squareup/wire/ConsoleWireLogger.java` | Square Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/DryRunFileSystem.java` | Square Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/java/JavaGenerator.java` | Square Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/java/JavaSchemaHandler.java` | Square Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/java/package-info.java` | antiwire Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/schema/CustomTarget.java` | Square Apache 2.0 |
| `wire-java-generator/src/main/java/com/squareup/wire/schema/JavaTarget.java` | Square Apache 2.0 |
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
