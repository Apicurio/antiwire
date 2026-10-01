# Kotlin-to-Java translation conventions

The parity contract for all translation work in this port. Upstream tests assert the semantics
listed here; a translation that breaks one of these rules fails the suite in ways that are easy
to misread as test bugs. When in doubt, the upstream Kotlin source at tag 7.1.0 is the
specification.

## Exception parity

- Kotlin `require(...)` throws `IllegalArgumentException`. Translate to explicit
  `if (!cond) throw new IllegalArgumentException(message)`. Keep the message string identical.
- Kotlin `check(...)` and `checkNotNull(...)` throw `IllegalStateException` (not
  NullPointerException). Translate accordingly.
- Kotlin `error(...)` throws `IllegalStateException`. Kotlin `TODO()` throws
  `NotImplementedError`, which is an `Error`; in Java throw `new UnsupportedOperationException()`
  only where upstream tests do not assert the type, otherwise keep an `Error` subclass.
- Never let a translation convert an exception the tests catch into a different type
  (`assertFailsWith<...>` in tests pins the exact class).
- okio's `IOException`, `EOFException`, `ProtocolException`, `FileNotFoundException`, and
  `Closeable` map to the vendored `okio` classes (they are checked like upstream).

## Equality, hashCode, toString

- Kotlin data classes generate `equals`/`hashCode` over all constructor properties and
  `toString` as `ClassName(prop=value, ...)`. Reproduce the exact `toString` format; several
  golden tests assert on rendered strings.
- Kotlin `List`/`Map`/`Set` equality is `java.util.AbstractList`-style element-wise equality.
  Use `java.util.List` everywhere; never compare with reference equality. Upstream model
  classes frequently hold read-only lists whose equality semantics tests rely on.
- Enum equality is identity in both languages; do not introduce value-based comparison.
- Where upstream hand-writes `equals`/`hashCode` (Message subclasses, Schema model classes),
  translate the implementation literally, including null handling and double/long handling.

## Null handling

- Non-null Kotlin parameters become `Objects.requireNonNull(x, "message")` at the top of
  constructors and setters, with the same message text as upstream `requireNotNull`.
- Nullable properties become `@Nullable`-annotated fields (use `javax.annotation`-style via our
  own small annotations or plain Javadoc; do not add a dependency). Prefer plain Javadoc plus
  explicit checks until a dependency-free annotation story is chosen.
- Default parameter values become overloads or builder defaults; upstream Wire model classes
  already use builders for messages, so extend builders rather than telescoping constructors.

## Language constructs

- `object` declarations become a class with a private constructor and a `public static final
  INSTANCE` field, or static members directly when the object has no state.
- `companion object` members become static members. `const val` becomes `static final` with the
  literal inlined where upstream relies on inlining (annotation parameters).
- `internal` visibility becomes `public` (Kotlin `internal` is public in JVM bytecode; Apicurio
  consumes `internal.parser` classes directly). Add Javadoc noting upstream visibility.
- Sealed hierarchies (oneof wrappers) become abstract base classes with package-visible or
  public constructors as upstream requires. Java 11 forbids `sealed`; do not emulate it with
  tricks beyond what upstream needs.
- `lateinit` becomes a nullable field with a use-site `checkNotNull`-style guard.
- Extension functions become static utility methods in the class they extend or in an `Internal`
  helper, named identically to the extension.
- Delegates (`by lazy`) become double-checked or plain memoization matching upstream thread
  safety (upstream uses `lazy` without synchronization in most model classes; match that).
- String templates translate to concatenation or `String.format` only when upstream formats
  numbers; keep decimal formatting identical (`toString()` of Long/Int/Double).
- `CharSequence` APIs: `substring`, `indexOf`, `split` behave like `java.lang.String` here;
  watch `String.substring(beginIndex)` off-by-one translations.

## Java 11 constraints (D3)

No `sealed`, records, `var`, text blocks, `List.of` varargs order caveats (Java 11 has
`List.of`; fine), `Optional.isEmpty` (Java 11), `String.isBlank` (Java 11 has it), or JDK 17
APIs anywhere in main sources. CI compiles with `--release 11`, which enforces this.

## Attribution

Files whose upstream originals carry third-party Apache 2.0 headers keep them, adapted:
- Google (protobuf-derived): `ProtoReader`, `ByteArrayProtoReader32`, `ProtoReader32`,
  `internal/MathMethods`.
- JetBrains: `internal/IntArrayList`, `LongArrayList`, `FloatArrayList`, `DoubleArrayList`.
- Square: every other upstream file keeps its original copyright line.
The NOTICE lists Square, Google, and JetBrains as required.

## Process rules

- Translate one upstream file at a time and keep the class name, package, and public member
  order close to upstream so future re-syncs diff cleanly.
- Never fix an upstream bug during translation; translate the behavior, file the observation in
  the task notes, and let upstream-parity tests decide.
- Every translated cluster lands with its upstream test counterpart in the same commit, so the
  suite never goes long without evidence.
