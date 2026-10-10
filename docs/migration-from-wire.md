# Migrating from Square Wire 7.1.0 to antiwire

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
  upstream test suites (`scripts/verify.sh`, 990 reconciled upstream cases).
- Dependencies: `wire-schema-java` pulls only `wire-runtime-java`. No Kotlin, no okio, no Guava
  on the production classpath of your application.

## What changes

### 1. Coordinates

```xml
<!-- before -->
<dependency><groupId>com.squareup.wire</groupId><artifactId>wire-schema</artifactId>
  <version>6.4.0</version></dependency>
<!-- after -->
<dependency><groupId>io.apicurio</groupId><artifactId>wire-schema-java</artifactId>
  <version>0.1.0-SNAPSHOT</version></dependency>
```

Exclude `com.squareup.wire:*`, `com.squareup.okio:*` and `org.jetbrains.kotlin:*` from other
dependencies that bring them transitively, so antiwire is the single provider of
`com.squareup.wire.*`. Nothing is published yet: build antiwire locally and install it
(`mvn -DskipTests install`).

### 2. Members that need Kotlin or okio types are not provided

Wire members whose signature contains a Kotlin type (`KClass`, `Unit`, `Pair`, ...) are not
ported (DEC-4: no Kotlin in production). Members whose signature contains an okio type are not
the public form (DEC-14); the JDK-typed form is. The complete list, one row each with its
reason, is `config/surface-baseline.tsv` (status `EXCLUDED`). Typical rewrites:

| Wire | antiwire |
|---|---|
| `new ProtoAdapter<>(FieldEncoding, KClass)` | `new ProtoAdapter<>(FieldEncoding, Class)` |
| `okio.ByteString` in `Message`, `AnyMessage`, `ProtoAdapter` | `com.squareup.wire.Bytes` (okio forms remain as `@Deprecated` bridges) |
| `SchemaLoader(FileSystem)` with an okio `FileSystem` | `JdkSchemaLoader` with `java.nio.file.Path` (section 4) |

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
if the same `try` also contains a call that still throws. On the Apicurio migration no site
needed a change, because every handler guarded a call that still throws.

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

| Check | Result | Where |
|---|---|---|
| Apicurio protobuf modules (4), existing tests | 142 pass, same per-class counts as the Wire 6.4.0 baseline | `antiwire-integration` branch, `ANTIWIRE_MIGRATION.md` |
| Apicurio app regression set | 129 pass | same |
| Confluent 8.0.0 compiled against Wire, run on antiwire | parsing and printing work; range printing is the DEC-15 limit | TASK-33.2 notes |
| Surface check against the real Wire 7.1.0 jars | 1408 members match, no open gap, 379 documented exclusions | `config/surface-baseline.tsv` |
