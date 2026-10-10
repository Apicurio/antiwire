# Migrating from Square Wire 7.1.0 to antiwire

Status: nothing is published. `io.apicurio:wire-schema-java:0.1.0-SNAPSHOT` exists only after you
build and install antiwire yourself (see "Installing" below); the version is a moving snapshot.

Audience: a Java project that today depends on `com.squareup.wire:wire-schema` (and
`wire-runtime`) and wants `io.apicurio:wire-schema-java` (and `wire-runtime-java`) instead.
Everything here was observed on a real migration, not guessed: the Apicurio Registry protobuf
modules, branch `antiwire-integration` of `paoloantinori/apicurio-registry` (log in its
`ANTIWIRE_MIGRATION.md`), with the numbers re-measured on 2026-10-10.

## What stays the same

- Packages and class names: everything is still `com.squareup.wire.*`. Imports do not change.
- Kotlin properties are exposed as `getX()` / `isX()` exactly as Wire exposes them (for example
  `ProtoFileElement.getPackageName()`, `Field.getName()`). The few members that Wire itself
  exposes without `get` (`Service.name()`, `Message.adapter()`, ...) are unchanged.
- The Kotlin `Companion` form exists on the classes that have it upstream:
  `ProtoParser.Companion.parse(...)` compiles, and a class already compiled against Wire that
  reads `ProtoParser.Companion` runs against antiwire. This was added because third-party jars
  compiled against Wire (Confluent `kafka-protobuf-provider` 8.0.0) read that field at runtime.
- Parsing, linking, pruning, descriptor encoding and the Java generator are covered by the
  upstream test suites: 990 upstream cases are accounted for, 988 ported and 2 recorded missing,
  of which about 89 are skipped as DEC-6 exclusions or upstream's own ignores (JSON adapters,
  the Kotlin and Swift generators, gRPC and the Gradle plugin are out of scope). See
  `docs/parity-runner.md` and `scripts/verify.sh`.
- Dependencies: `wire-schema-java` pulls only `wire-runtime-java`. There is no Kotlin, no
  Guava and no okio Maven artifact on the production classpath. A vendored subset of okio
  (package `okio`, 42 classes) is bundled inside `wire-runtime-java` as the internal engine
  (DEC-14); keeping the real `com.squareup.okio` artifact on the same classpath produces
  duplicate `okio.*` classes, so exclude it.

## What changes

### 1. Coordinates

```xml
<!-- before (Apicurio used 6.4.0; the comparison in this guide is against 7.1.0) -->
<dependency><groupId>com.squareup.wire</groupId><artifactId>wire-schema</artifactId>
  <version>7.1.0</version></dependency>
<!-- after -->
<dependency><groupId>io.apicurio</groupId><artifactId>wire-schema-java</artifactId>
  <version>0.1.0-SNAPSHOT</version></dependency>
```

Exclude `com.squareup.wire:*`, `com.squareup.okio:*` and `org.jetbrains.kotlin:*` from other
dependencies that bring them transitively, so antiwire is the single provider of
`com.squareup.wire.*`.

#### Installing

Build antiwire from a checkout with JDK 17 or newer (the artifacts target Java 11 bytecode) and
install it into your local repository: `mvn -DskipTests install -DskipITs`. Use `mvn verify`,
not `mvn test`, for the full test run (see `BUILD.md`). CI jobs of your own project must run this
install first, or resolve the artifacts from a repository you publish them to.

### 2. Members that need Kotlin or okio types are not provided

Wire members whose signature contains a Kotlin type (`KClass`, `Unit`, `Pair`, ...) are not
ported (DEC-4: no Kotlin in production). Members whose signature contains an okio type are not
the public form (DEC-14); the JDK-typed form is. The complete list, one row each with its
reason, is `config/surface-baseline.tsv` (status `EXCLUDED`). Typical rewrites:

| Wire | antiwire |
|---|---|
| `new ProtoAdapter<>(FieldEncoding, KClass)` | `new ProtoAdapter<>(FieldEncoding, Class)` |
| `okio.ByteString` in `Message`, `AnyMessage`, `ProtoAdapter` | `com.squareup.wire.Bytes` (see "Bytes" below) |
| `SchemaLoader(FileSystem)` with an okio `FileSystem` | `JdkSchemaLoader` with `java.nio.file.Path` (section 4) |

#### Bytes

Wire exposes `okio.ByteString` in `Message.encodeByteString()`, `ProtoAdapter.decode(ByteString)`
and similar members. antiwire keeps those okio forms only as `@Deprecated` bridges, for source that
still calls them, and offers the JDK-typed forms as the supported API:

```java
byte[] raw = message.encode();              // unchanged in Wire and antiwire
Bytes bytes = message.encodeToBytes();      // replaces encodeByteString()
Foo foo = Foo.ADAPTER.decode(bytes);        // decode(Bytes) or decode(byte[]); avoid decode(ByteString)
```

`ProtoAdapter.decode` has `byte[]`, `okio.ByteString` and `Bytes` overloads, so passing `null`
literally is ambiguous: cast it. okio types stay legal only inside the vendored engine (DEC-14).

### 3. Reserved and extension ranges are `int[]` pairs

`ReservedElement.getValues()` and `ExtensionsElement.getValues()` return `String` names,
`Integer` tags and `int[] {start, endInclusive}` pairs. Wire returns `kotlin.ranges.IntRange`
for a range. Code that does `instanceof IntRange` must be adapted:

```java
// before
if (value instanceof IntRange r) { start = r.getStart(); end = r.getEndInclusive(); }
// after
if (value instanceof int[] r) { start = r[0]; end = r[1]; }   // end is inclusive, as before
```

Known limit (DEC-15, open to reconsideration): a library **compiled** against Wire that tests
`instanceof kotlin.ranges.IntRange` does not recognise these values. Confluent 8.0.0's
`ProtobufSchema.canonicalString()` throws `IllegalArgumentException` on a proto2 schema with
`reserved 5 to 9;` or `extensions 100 to 199;`; proto3 schemas and single reserved tags print
fine.

### 4. Loading schemas: `JdkSchemaLoader`

`SchemaLoader` takes an okio `FileSystem`. The consumer API is `JdkSchemaLoader`:

```java
try (JdkSchemaLoader loader = new JdkSchemaLoader()) {
  loader.initRoots(List.of(sourceDir), List.of(protoPathDir));   // java.nio.file.Path
  Schema schema = loader.loadSchema();
}
```

`JdkSchemaLoader.forClasspath(ClassLoader, String...)` loads from classpath resources.
There is **no in-memory route yet**: code that built an okio `FakeFileSystem` must write its
content to a temporary directory first. Two consequences observed on the Apicurio migration:
each load costs real file-system work (about 90 metadata calls on a cold temp directory), and
host file-system semantics apply (on a case-insensitive host such as macOS an import with the
wrong case resolves locally and fails on a case-sensitive CI host). TASK-33.5 tracks an
in-memory option.

`JdkSchemaLoader` itself still declares `throws IOException` (it does real file I/O); the rule
in section 5 applies to the Wire-compatible classes.

### 5. Exceptions: unchecked where Wire has none

Wire is Kotlin and has no checked exceptions; only functions annotated `@Throws` declare one.
The port matches that: methods such as `Message.encode()`, `SchemaLoader.loadSchema()` and
`AnyMessage.unpack(...)` no longer declare `IOException`; a failure surfaces as
`java.io.UncheckedIOException` with the original `IOException` as cause. Methods that upstream
declares with `@Throws` (for example `ProtoAdapter.decode(ProtoReader)` and the `ProtoReader`
read methods) keep the checked exception.

Consequence for callers: `catch (IOException e)` around a call that no longer throws it is a
**compile error** in Java ("exception IOException is never thrown"). Remove the catch, or keep it
if the same `try` also contains a call that still throws. On the Apicurio migration (the
branch's `ANTIWIRE_MIGRATION.md`, rerun section for antiwire 62c8646) no site needed a change,
because every `IOException` handler there guarded a call that still throws it (`JdkSchemaLoader`,
`java.nio`, Guava, protobuf `writeTo`). Your code may differ.

### 6. Constructors, `ProtoParser`

`ProtoParser.parse(Location, String)` is available both as the static method and through
`ProtoParser.Companion.parse`. The `ProtoParser(Location, char[])` constructor is public. The
`OneOf` constructor and `MessageType.toElement()` are public (Apicurio relied on both).

## How to check your own migration

1. Build and install antiwire, point your build at it, remove the Wire dependencies.
2. `mvn dependency:tree` must show only `io.apicurio:wire-schema-java` and
   `wire-runtime-java` for `com.squareup.wire.*`, and no `org.jetbrains.kotlin` or
   `com.squareup.okio`.
3. Run your existing tests: changes should be limited to the items above (invocations,
   imports, exception plumbing), never to test logic.
4. If you have compiled third-party jars that use Wire, scan them with
   `scripts/scan-consumer-jars.py` (it lists Wire members they reference that antiwire lacks).
5. To see exactly where antiwire differs from Wire for any member, read
   `config/surface-baseline.tsv`; `scripts/surface-check.py` enforces it in `scripts/verify.sh`.

## Evidence

The Apicurio rows are not reproducible from this repository: they live on a branch of a fork
(`github.com/paoloantinori/apicurio-registry`, branch `antiwire-integration`, file
`ANTIWIRE_MIGRATION.md`, pushed; the Wire 6.4.0 per-class baseline is recorded in that file).
The maintainer's session reproduced the four modules (142 tests) and the Confluent test classes
(77 tests) on clean clones; the 129-test regression set is the agent's report.

| Check | Result | Where |
|---|---|---|
| Apicurio protobuf modules (4), existing tests | 142 pass, same per-class counts as the Wire 6.4.0 baseline | fork branch above |
| Apicurio app regression set | 129 pass (agent report) | fork branch above |
| Confluent 8.0.0 compiled against Wire, run on antiwire | parsing and printing work; range printing is the DEC-15 limit | TASK-33.2 notes |
| Surface check against the real Wire 7.1.0 jars | 1408 members match, no open gap, 379 documented exclusions | `config/surface-baseline.tsv` |
