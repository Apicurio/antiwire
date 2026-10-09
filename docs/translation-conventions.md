# antiwire Kotlin-to-Java translation conventions

Status: TASK-3 deliverable, 2026-09-30. This document is the behavioral contract for two kinds
of work: translating upstream Kotlin production sources into the port's Java 11 production
sources, and mechanically adapting upstream Kotlin tests so they run against those Java
artifacts. It operationalizes DEC-3, DEC-4, DEC-5, and DEC-12 from
[decisions.md](decisions.md); it invents no new policy. Where this document and the decision
record disagree, the decision record wins and this document must be fixed. Companion documents:
[research-wire-java-port-2026-09-29.md](research-wire-java-port-2026-09-29.md) (evidence),
[compatibility-matrix.md](compatibility-matrix.md) (surface inventory),
[BUILD.md](../BUILD.md) (build and enforcement entry points).

## 1. Scope, authority, and evidence

**Pinned upstream identity.** All upstream citations in this document refer to square/wire at
annotated tag object `da24c33ee1fe772a7a04617018087f46f26d1708` resolving to commit
`9f62097dfe4995b5709d001ca0187e30ca0530ef` (tag 7.1.0, DEC-1), in the local clone
`/tmp/antiwire-upstream-review/wire-7.1.0`. Verified 2026-09-30 with `git rev-parse HEAD` and
`git rev-parse 7.1.0^{tag}`; both returned the pinned hashes. Upstream paths below are relative
to that clone root.

**Modules governed.** These conventions govern the production translation of the port surface
defined in [compatibility-matrix.md](compatibility-matrix.md) section A and section C:
wire-runtime commonMain plus jvmMain, and wire-schema commonMain plus jvmMain, plus the
optional Java generator module within its own task's constraints (TASK-16). They also govern
every adapted upstream test under DEC-5, regardless of which upstream module hosts it.

**Relationship to DEC-5.** DEC-5 makes every relevant upstream test case mandatory and permits
only mechanical, individually recorded adaptations. Section 6 of this document is the rulebook
those adaptations follow; sections 2 through 5 define what the Java production code must do so
that unadapted and lightly adapted tests pass for the right reasons.

**How Kotlin facts were verified.** Two evidence channels, both checked for this document:

1. Direct reads of the pinned clone (file paths and line numbers cited inline).
2. Compiled probes: the data-class, null-check, and unsigned-parsing semantics below were
   verified 2026-09-30 by compiling small Kotlin programs with the locally available Kotlin
   2.3.0 compiler (closest to upstream's pinned Kotlin 2.3.21 per research F1) and inspecting
   the emitted bytecode with `javap`, and by running the resulting classes. Exception types of
   `require`, `check`, `checkNotNull`, `requireNotNull`, and `error` were additionally verified
   against the kotlin-stdlib sources (`kotlin/util/Preconditions.kt`, kotlin-stdlib 2.4.10
   sources jar on this machine). These stdlib behaviors are stable, documented Kotlin API; the
   version difference (2.3.0 and 2.4.10 versus the pinned 2.3.21) is judged immaterial and is
   disclosed here rather than hidden. Anything below not covered by one of these channels is
   labeled unverified.

## 2. Nullability and exception mapping

### 2.1 Deciding nullable versus non-null

Upstream production code is Kotlin with explicit types, so the decision is mechanical: a
declaration of type `T` (no `?`) means the Java parameter, field, or result is non-null and is
enforced at the boundary; a declaration of type `T?` means the Java side accepts null and
performs no null check of its own. Kotlin platform types (`T!`) arise only where upstream calls
Java APIs. For those sites the translator treats the value as nullable unless the upstream usage
proves non-null (an immediate non-null transfer, a `!!`, or a compiler-injected check on the
next declaration), and records the decision in the porting task's code-review disposition and, where behavior depends on it, in a test (section 7.2, item 3).

### 2.2 Null-check placement

Null checks live exactly where upstream has them, and nowhere else:

1. Every public entry point (public constructor, factory, companion-function equivalent,
   public method) and every internal declaration the port keeps public (the
   [compatibility-matrix.md](compatibility-matrix.md) internal-package row) whose Kotlin
   declaration has a non-null parameter carries the equivalent of
   the compiler-injected parameter check as its first statement, because upstream bytecode has
   `Intrinsics.checkNotNullParameter` there (verified by `javap` on compiled probes).
2. Explicit `require`, `check`, `checkNotNull`, `requireNotNull`, and `error` calls translate
   to the same position in the control flow they occupy upstream.
3. Never hoist a check earlier or add one upstream lacks: upstream tests observe the failure
   point. Example: `WireTest.java:342-357` (wire-tests/jvm-java-kotlin) builds a `Person` whose
   `phone` list is null and expects the `NullPointerException` to surface from
   `Internal.immutableCopyOf`, not from `Person.Builder.build()`. An eagerly added check in the
   Java builder would keep the type but move the site and could change the message.

### 2.3 Mapping table

The rule in one sentence: `Objects.requireNonNull` is **not** the translation of
`checkNotNull`; it is the translation of the compiler-injected non-null parameter check, and of
nothing else. `checkNotNull` and every `check`/`error` form become `IllegalStateException`
throws; `require` forms become `IllegalArgumentException` throws.

| Upstream form | Exception thrown | Verified default message | Java idiom |
|---|---|---|---|
| `require(b)` | `IllegalArgumentException` | `"Failed requirement."` | `if (!b) throw new IllegalArgumentException("Failed requirement.")` |
| `require(b) { m }` | `IllegalArgumentException` | `m.toString()` | `if (!b) throw new IllegalArgumentException(m)` |
| `requireNotNull(x)` | `IllegalArgumentException` | `"Required value was null."` | null-check then `return x` |
| `check(b)` | `IllegalStateException` | `"Check failed."` | `if (!b) throw new IllegalStateException("Check failed.")` |
| `checkNotNull(x)` | `IllegalStateException` | `"Required value was null."` | null-check then `return x`; never `Objects.requireNonNull` |
| `error(m)` | `IllegalStateException` | `m.toString()` | `throw new IllegalStateException(m)` |
| Compiler-injected non-null parameter check | `NullPointerException` | `"Parameter specified as non-null is null: method <function>, parameter <name>"` | `Objects.requireNonNull(p, message)` with the same message text |
| Explicit `throw NullPointerException(...)` in source | `NullPointerException` | the source message | `throw new NullPointerException(msg)` |

Upstream examples of the last row: `Internal.checkElementsNotNull` throws
`NullPointerException("Element at index $i is null")` for lists (Internal.kt:163-169) and
`"map.containsKey(null)"` / `"Value for key $k is null"` for maps (Internal.kt:172-181);
`Internal.immutableCopyOf` guards element nullness with `require(null !in result) {
"$name.contains(null)" }` (Internal.kt:72), an `IllegalArgumentException` whose message
`WireTest.java:370` asserts as `phone.contains(null)`.

**Message parity is mandatory where observable.** Two upstream Java tests assert the exact
compiler-injected message text, including the Kotlin multifile facade name:
`WireTest.java:342-357` expects `"...: method com.squareup.wire.internal.Internal__InternalKt.immutableCopyOf,
parameter list"` and `TestAllTypes.java:570-583` (wire-tests/jvm-java-kotlin) expects the same
shape for `Internal__InternalKt.checkElementsNotNull`. The port therefore reproduces these
strings verbatim through a shared helper (one place constructs the
`"Parameter specified as non-null is null: method ..., parameter ..."` text, parameterized by
the facade name and parameter name of the upstream declaration being mirrored), and translated
`Internal` members keep their upstream method names per
[compatibility-matrix.md](compatibility-matrix.md) section E.

**Lazy messages.** Kotlin's `{ m }` lambda evaluates the message only on failure. Java arguments
evaluate eagerly. Upstream messages are string templates with no side effects, so eager
evaluation is behaviorally identical; a translation that would make message construction
expensive is a review flag, not a rewrite.

### 2.4 Executable examples (owned by TASK-5)

The pairs below are the specification for the executable reference-versus-port examples that
TASK-5 AC#6 builds and the shared verification entry point runs. Reference side: upstream Wire
7.1.0 artifacts on an isolated classpath (TASK-5 AC#4). Port side: the translated Java classes.
Each example's verdict is the observable exception type and message, which must match.

```kotlin
// Kotlin reference behavior (upstream semantics)
fun port(target: String?): Int {
  val t = checkNotNull(target) { "target == null" }   // IllegalStateException
  require(t.isNotEmpty()) { "target is empty" }        // IllegalArgumentException
  return t.length
}
```

```java
// Java translation
static int port(String target) {
  if (target == null) throw new IllegalStateException("target == null");
  if (target.isEmpty()) throw new IllegalArgumentException("target is empty");
  return target.length();
}
```

```kotlin
// Kotlin: compiler-injected parameter check on a public function
fun repeat(s: String, n: Int): String = s.repeat(n)  // called from Java with s = null
```

```java
// Java translation: same observable failure
static String repeat(String s, int n) {
  java.util.Objects.requireNonNull(
      s, "Parameter specified as non-null is null: method <function>, parameter s");
  StringBuilder result = new StringBuilder();
  for (int i = 0; i < n; i++) result.append(s);
  return result.toString();
}
```

```kotlin
// Kotlin: requireNotNull returns the value after the check
fun element(name: String?): String = requireNotNull(name) { "name == null" }
```

```java
// Java translation
static String element(String name) {
  if (name == null) throw new IllegalArgumentException("name == null");
  return name;
}
```

The TASK-5 suite additionally exercises non-null parameter acceptance (no exception), nullable
parameter acceptance (null flows through), and the placement rule (a null list argument to a
builder surfaces from the `Internal.immutableCopyOf` boundary, matching `WireTest.java`).

## 3. Data classes, equality, hashCode, toString, and collections

### 3.1 Generated-member semantics and their Java equivalents

The facts in this subsection were verified by compiling probes with Kotlin 2.3.0 and reading
the emitted bytecode (section 1); they describe what the Kotlin compiler generates for a
`data class`, which the hand-written Java must reproduce member by member.

**equals.** Identity shortcut, type check, then one comparison per constructor property in
declaration order: primitives with `==`, reference types (including nullable and `List` types)
with null-safe equals (`Intrinsics.areEqual`, equivalent to `java.util.Objects.equals` for all
upstream types). Two lists with equal elements are equal, because Kotlin `List` is
`java.util.List` on the JVM and `List.equals` is element-wise.

**hashCode.** The first property's hash seeds the result; each following property folds with
`result = 31 * result + <property hash>`. Nullable properties contribute 0 when null and the
boxed hash otherwise, which is exactly `java.util.Objects.hashCode`.

**toString.** Format `TypeName(p1=v1, p2=v2, ...)`: no space after `=`, `, ` between entries,
`null` for null properties, strings unquoted, collections through their own `toString`.

**The array asymmetry.** Kotlin data classes compare array properties by identity in `equals`
but hash them by content in `hashCode` (`Arrays.hashCode`, verified by probe: two data class
instances with equal-content `IntArray` properties are not equal), and render them with
`Arrays.toString` in `toString`. A scan of the pinned port surface found zero data classes with
array-typed constructor properties (40 data classes in wire-runtime and wire-schema main
sources, counting generic declarations such as OneOf.kt:106 whose type-parameter list precedes
the constructor parenthesis; distinct from the 42 public `.api` entries the compatibility
matrix counts; none holding an array), so no current translation is affected; the rule is recorded
because an upstream sync could add one, and a translator who "fixes" the asymmetry with
`Arrays.equals` would silently change equality semantics.

Java shape for the canonical upstream case, `FieldElement`
(wire-schema/src/commonMain/kotlin/com/squareup/wire/schema/internal/parser/FieldElement.kt:25-34):

```java
@Override public boolean equals(Object other) {
  if (this == other) return true;
  if (!(other instanceof FieldElement)) return false;
  FieldElement o = (FieldElement) other;
  return location.equals(o.location)
      && Objects.equals(label, o.label)          // label is nullable (Field.Label?)
      && type.equals(o.type)
      && name.equals(o.name)
      && Objects.equals(defaultValue, o.defaultValue)  // nullable
      && Objects.equals(jsonName, o.jsonName)          // nullable
      && tag == o.tag
      && documentation.equals(o.documentation)
      && options.equals(o.options);              // List<OptionElement>, element-wise
}

@Override public int hashCode() {
  int result = location.hashCode();
  result = 31 * result + Objects.hashCode(label);
  result = 31 * result + type.hashCode();
  result = 31 * result + name.hashCode();
  result = 31 * result + Objects.hashCode(defaultValue);
  result = 31 * result + Objects.hashCode(jsonName);
  result = 31 * result + tag;
  result = 31 * result + documentation.hashCode();
  result = 31 * result + options.hashCode();
}

@Override public String toString() {
  return "FieldElement(location=" + location + ", label=" + label + ", type=" + type
      + ", name=" + name + ", defaultValue=" + defaultValue + ", jsonName=" + jsonName
      + ", tag=" + tag + ", documentation=" + documentation + ", options=" + options + ")";
}
```

`Internal.equals(a, b)` (Internal.kt:139, `a === b || (a != null && a == b)`) is the null-safe
field comparison used by generated messages; `java.util.Objects.equals` is an accepted Java
equivalent (same truth table for every upstream operand type).

**Property accessors.** A Kotlin property `x` is exposed to Java as `getX()`, and a Boolean
property named `isX` as `isX()`; the port declares exactly those names (decision 2026-10-09,
TASK-33.1; `AccessorNameParityTest` pins them against the real 7.1.0 jars). Never `x()`, and
no public field `x` next to the getter: a field is public only where upstream has `@JvmField`
(for example generated message fields), a property backed by a private field gets its getter. A
member upstream names with `@JvmName("x")` keeps the plain name (`Service.type()`,
`MessageType.fields()`, `Message.adapter()`). When a Kotlin interface property is implemented,
the implementation uses the same `getX()` name.

**copy() and componentN() bridges.** Kotlin generates a public all-argument `copy` (the
default-omitting `copy$default` synthetic is invisible to and unusable from Java) and public
`componentN()` accessors in declaration order. Kotlin code can call a Java method only
positionally, and can destructure against Java-declared `componentN()` methods. The port
therefore provides, explicitly and only where consumers or adapted tests need them (DEC-5):
a `copy` whose parameter list matches the upstream property list in order, narrower `copy`
overloads for the specific omitted-default patterns adapted tests use (per-site decision, rule
R2), and `componentN()` methods where a test destructures. Where no consumer needs them, the
bridge is omitted; nothing in the port may regress an adapted test for lacking a bridge that
was never required.

### 3.2 Generated message types

Concrete `Message` subclasses are generated code, not hand translation; their `equals`,
`hashCode`, and `toString` follow the upstream Java generator's emitted forms, evidenced by the
single Java golden `wire-golden-files/src/main/java/com/squareup/wire/proto3/java/all_types/AllTypes.java`:
`equals` compares `unknownFields()` first then each field via `Internal.equals` (lines
1079-1083), `hashCode` folds with multiplier 37 and caches in the superclass field (lines
1188-1191), `toString` appends `", field=value"` fragments and rewrites the opening delimiter
with `builder.replace(0, 2, "AllTypes{").append('}')` (lines 1299-1402). Golden exactness for
generated output is governed by OPEN-4 and [compatibility-matrix.md](compatibility-matrix.md)
section E; TASK-16 owns it.

### 3.3 Collection mutability

The port returns what upstream returns, with mutation behavior preserved:

1. Kotlin's read-only `List<T>` and mutable `MutableList<T>` both map to `java.util.List<T>` in
   signatures; mutability is a property of the implementation handed out, not of the static
   type, and the port must hand out an implementation with upstream's mutation semantics.
2. `Internal.ImmutableList` (ImmutableList.kt:18-35) extends Kotlin's
   `kotlin.collections.AbstractList` (the file has no imports; on the JVM it implements
   `java.util.List`) and adds no mutators, so `add`, `set`, and `remove` throw
   `UnsupportedOperationException`; it is
   `Serializable` via `writeReplace` to an unmodifiable list. The translation preserves the
   class, the unconditional rejection, and the serialization shape.
3. `Internal.MutableOnWriteList` (MutableOnWriteList.kt:19-54) is copy-on-write: reads hit the
   immutable backing list, the first mutation swaps in an `ArrayList`. Translated as-is.
4. `immutableCopyOf` (Internal.kt:61-75) unwraps `MutableOnWriteList`, returns empties and
   existing `ImmutableList`s unchanged, wraps everything else, then runs the element nullness
   `require` after the copy. Order is observable (a race-defense comment upstream); the
   translation keeps the sequence.

Parity checks: `WireTest.java` "builderListsAreAlwaysMutable" (lines 374-385) covers builder
list mutability; `SerializableTest.java` in the same suite covers list serialization shapes;
equality on decoded messages including unknown fields is asserted by
`UnknownFieldsTest.kt` (wire-tests/jvm-kotlin-interop) and the hashCode participation
assertions live in `UnknownFieldsTest.java` (wire-tests/jvm-java-kotlin, lines 77-87: unknown
fields participate in equals and hashCode). These adapted tests are the executable check that
mutation and equality semantics did not drift.

## 4. Unsigned integer parsing

**Pinned sites.** Unsigned usage in the port surface is confined to
`wire-schema/src/commonMain/kotlin/com/squareup/wire/schema/internal/SchemaEncoder.kt`, in
`toJsonSingle`: lines 459 and 460 (`FIXED32`, `FIXED64`) and lines 469 and 470 (`UINT32`,
`UINT64`), with the two forms `(value as String).toUInt().toInt()` and
`(value as String).toULong().toLong()`, converting option literals to the signed primitive with
the same binary encoding.

**Mapping.** `String.toUInt().toInt()` translates to `Integer.parseUnsignedInt(value)` and
`String.toULong().toLong()` to `Long.parseUnsignedLong(value)`. Verified equivalent by probe on
both sides (2026-09-30): accepted syntax is identical (a leading `+` is accepted; negative,
whitespace-bearing, empty, and non-numeric strings are rejected), results are identical
(`4294967295` parses to `-1`; `18446744073709551615` to `-1L`), and both sides throw
`java.lang.NumberFormatException`. The exception **message text differs** (Kotlin:
`Invalid number format: '...'`; JDK: `String value ... exceeds range of unsigned int.` and
`For input string: "..."`). No upstream test at the pin asserts these messages (repo grep found
no assertion on them), so the JDK messages are acceptable; if an upstream sync adds a test
asserting the Kotlin message, the adaptation ledger records the conflict and the site decision
(reproduce the Kotlin message in the translation, or record the delta), it is never silently
absorbed by editing the test.

**General rule.** Any other `UInt`/`ULong` site appearing in a future upstream sync applies the
same mapping, including the message caveat; per research F3 no other site exists in the current
port surface, a fact re-verified 2026-09-30 by an import-and-usage scan of wire-runtime and
wire-schema main sources (the four SchemaEncoder lines above are the complete list).

## 5. Java 11 constraints and the Kotlin boundary

### 5.1 Language constraints in production sources

DEC-3 states the rule verbatim: "Java 11 forbids records and sealed classes in production
sources; local-variable `var` is available and is not prohibited." Production sources compile
with `--release 11` (DEC-3, BUILD.md), therefore the following are unavailable in production
code: records, sealed classes and sealed interfaces, `switch` expressions, `instanceof` pattern
matching, text blocks, and any preview feature. Local-variable `var` remains available under
the quoted rule: local variables only, never fields, parameters, or return types. Lambdas,
streams, and `Optional` are Java 8 API and
unrestricted, subject to matching upstream behavior (section 2 and section 3 govern what the
code must do; style is not a parity surface). Test and build tooling is not forced to Java 11
(DEC-3); the bytecode and dependency checks below still constrain the artifacts.

### 5.2 The Kotlin boundary

| Scope | Kotlin status | Authority and enforcement |
|---|---|---|
| Test compilation (`src/test/kotlin`, test-scoped kotlin-stdlib) | Allowed | BUILD.md "Kotlin policy"; enforcer bans target only compile, runtime, and provided scopes (parent `pom.xml`, `bannedDependencies`, lines 196-210) |
| Pinned upstream generators running as isolated build-time fixture tools | Allowed | DEC-4; fixture generation uses pinned upstream tooling and avoids any dependency on the port's own generator (DEC-5) |
| Production compile, runtime, and provided scope | Forbidden for Kotlin artifacts and Kotlin-backed libraries, transitively (the banned list lives in the parent `pom.xml` enforcer and BUILD.md) | Maven enforcer `bannedDependencies` with `searchTransitive=true` (parent `pom.xml` lines 196-210, message names DEC-4); duplicate-class check `scripts/check-classpath.sh` over `config/retained-prefixes.txt` (fail-closed when missing or empty); Java 11 bytecode check `scripts/check-java11-bytecode.sh` (class file major version 55 or lower, multi-release aware); real-JVM check `scripts/consumer-check-java11.sh` on an actual JDK 11 |

All four checks run under the single entry point `scripts/verify.sh`, which is the only command
CI's build job runs (BUILD.md). A vendored okio subset chosen under OPEN-1 lands as port source
under these same rules, never as a production dependency (parent `pom.xml` enforcer comment).

## 6. Test adaptation rulebook

### 6.1 What stays Kotlin, and what is adapted

Upstream Kotlin test sources stay Kotlin in the port's test scope and run against the Java
production artifacts, with mechanical adaptations only (DEC-5). Pinned originals are preserved
for drift comparison (the pinned clone is the source of record; TASK-14's parity runner
re-extracts them). Adaptations are individually recorded and reviewed.

### 6.2 The adaptation ledger

Every adapted test file carries an adaptation record with one row per adapted case, and the
owning tasks (TASK-9 for runtime, TASK-13 for schema, TASK-14 for the reconciling inventory)
keep the ledger alongside the adapted sources. Minimum fields per row:

| Field | Content |
|---|---|
| Original | Pinned upstream `path:line` of the case (test function or method) |
| Adapted | Port `path:line` of the adapted case |
| Case | Original test function or method name |
| Rules applied | Identifiers from the rule list below (R1, R2, ...) plus any per-site decision |
| Reviewer | Who reviewed the adaptation and when |

The ledger's completeness is checkable: TASK-14 AC#6 reconciles the source-derived inventory
against executed suites and rejects missing relevant cases (DEC-5), so an adapted case without
a ledger row, or a ledger row without a running case, is a CI failure.

### 6.3 Mechanical rules

These are the enumerated adaptations DEC-5 permits, with the upstream evidence that motivates
each:

**R1, named-argument call forms rewritten.** Kotlin cannot call a Java declaration with named
arguments. Upstream tests construct model types with named arguments and omissions, for example
`MessageElement(location = ..., name = "Message1", documentation = ...)`
(wire-schema/src/commonTest/kotlin/com/squareup/wire/schema/ProtoFileTest.kt:34-47) and
`FieldElement(location = ..., label = OPTIONAL, ..., tag = 1, options = listOf(...))`
(wire-schema/src/commonTest/kotlin/com/squareup/wire/schema/internal/parser/FieldElementTest.kt:33-42).
The adaptation rewrites each call to positional form, supplying every omitted argument with the
upstream default value from the declaration (FieldElement.kt:25-34). Default values come from
the pinned source, never from the adapter's memory.

**R2, omitted-default call sites: rewrite or overload bridge.** Same trigger as R1 where the
positional rewrite is impractical (deeply nested builders, wide parameter lists). The
alternative is a Java overload bridge on the translated type whose parameter list carries the
upstream defaults, decided per site and recorded in the ledger with the bridge's signature.
Bridges are production surface additions and therefore appear in the compatibility matrix
maintenance cycle when public.

**R3, package-level and member-extension imports rewritten.** Kotlin file facades and
multifile classes become plain Java class names: `@file:JvmName("Internal")` on Internal.kt
makes the runtime class `com.squareup.wire.internal.Internal`, and `@file:JvmName("RuntimeUtils")`
on Util.kt makes `RuntimeUtils` ([compatibility-matrix.md](compatibility-matrix.md) section A).
Adapted tests rewrite `import com.squareup.wire.internal.immutableCopyOf` style package-function
imports to static imports on the class. Java-hidden dashed declarations (`@JvmName("-forEachTag")`,
ProtoReader.kt:470 and ProtoReader32.kt:156) disappear from the Java surface
([compatibility-matrix.md](compatibility-matrix.md) section A, reader/writer row), so
extension-style calls on them are rewritten as ordinary calls on the port's equivalent
entry point, per site, in the ledger.

**R4, fixtures regenerated by pinned build-time tools.** Test fixtures that upstream generates
through its own build are regenerated by the pinned upstream generators running as isolated
build-time fixture tools (DEC-4, DEC-5), never by the port's own generator, so fixture fidelity
cannot mask generator divergence. Unsupported fixture call forms are inventoried, not hidden
(TASK-5 AC#3).

**R5, mixed suites contribute their Java-target cases.** Mixed compiler suites are not excluded
wholesale (DEC-5). Verified at the pin: the shared `wire-tests` suites carry runtime behavior
cases in `wire-tests/jvm-java-kotlin` (Java cases including `WireTest.java`,
`SerializableTest.java`, `RuntimeMessageAdapterRedactTest.java`) and
`wire-tests/jvm-kotlin-interop` (Kotlin cases including `UnknownFieldsTest.kt`,
`SerializableTest.kt`, `ProtoAdapterTest.kt`); Java-target cases are retained or ported, and
only cases exclusively covering excluded features are omitted.

### 6.4 What may never change

Inputs, scenarios, expected values, and error-path assertions are preserved (DEC-5). An
adaptation that changes what is fed into the code under test, what outcome is expected, or
which exception type and message an error path must produce is not an adaptation; it is a
parity finding. Concretely: expected byte outputs, decoded values, equality and hashCode
assertions, expected exception types, and asserted message fragments (`WireTest.java:355`,
`TestAllTypes.java:579`, `WireTest.java:370` among them) cross the port untouched. If the port
cannot satisfy one, the resolution changes the port or escalates, never the assertion.

### 6.5 Exclusions

A case may be omitted only when it exclusively covers functionality declared non-ported by
DEC-6 (the authoritative exclusion list lives there), and the omission names the DEC-6
exclusion in the ledger. A failing, flaky, or inconvenient test is never grounds for exclusion
(DEC-5). Example of the distinction: the runtime's own JVM reflection machinery is mandatory
even though the gRPC reflection product is excluded (DEC-6).

## 7. Provenance and translation methodology

### 7.1 Per-file notice preservation

The port is Apache 2.0 overall and preserves upstream attribution verbatim, per file,
including notices embedded in file bodies below standard headers, not only leading headers
(DEC-12). Upstream notice inventory, verified 2026-09-30 by direct reads at the pinned commit;
the same files appear as port-surface members in [compatibility-matrix.md](compatibility-matrix.md)
section A (reader/writer family and internal package):

| Upstream file | Notice | Verified location |
|---|---|---|
| `wire-runtime/src/commonMain/kotlin/com/squareup/wire/ProtoReader.kt` | Google Nano BSD-style notice (derived from CodedInputByteBuffer) | File top, lines 1-33; no other header above it |
| `wire-runtime/src/commonMain/kotlin/com/squareup/wire/ProtoReader32.kt` | Same Google Nano BSD-style notice | File top, same shape |
| `wire-runtime/src/commonMain/kotlin/com/squareup/wire/ByteArrayProtoReader32.kt` | Same Google Nano BSD-style notice | File top, same shape |
| `wire-runtime/src/commonMain/kotlin/com/squareup/wire/internal/MathMethods.kt` | R8 project BSD-style notice (copyright 2016, the R8 project authors) | File top, lines 1 onward |
| `wire-runtime/src/commonMain/kotlin/com/squareup/wire/ProtoWriter.kt` | Square Apache-2.0 header, then Google Nano BSD-style notice embedded in the body | Apache header lines 1-15, `package` line 16, body notice lines 18-50 |
| `wire-runtime/src/commonMain/kotlin/com/squareup/wire/internal/IntArrayList.kt`, `LongArrayList.kt`, `FloatArrayList.kt`, `DoubleArrayList.kt` | JetBrains Apache-2.0 headers (copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors) | File top, 3 lines each |

The verified extent of the ProtoWriter body notice at the pin is lines 18 to 50 (the disclaimer
tail runs to line 50; imports begin at line 52); when in doubt, preserve more notice text,
never less.

Rules for the translation:

1. Every translated file carries the upstream notice of its source file, verbatim, as a comment
   block in the same structural position: leading notices remain the file's first content;
   ProtoWriter's embedded body notice stays below the package and import block at the
   corresponding position, introduced by the same derivation comment.
2. Translation does not merge files with different notices. If a refactor would merge two
   upstream files carrying different notices, either the notices are both carried or the files
   stay separate; the choice is recorded.
3. The NOTICE file carries the BSD-style notices verbatim, plus separate Apache-2.0 attribution
   for any vendored or adapted okio code and for every retained third-party dependency (DEC-12).
4. Vendored or adapted code (the okio subset if OPEN-1 selects that route, or any reviewed
   pure-Java dependency's sources) carries its own actual notices unmodified; the notice is
   read from the vendored artifact at its pinned version, never reconstructed from memory, and
   the NOTICE file gains the matching attribution. Legal approval is not claimed (DEC-12).

### 7.2 Methodology recording

Provenance is recorded by the mechanisms the repository actually uses, not by a per-file
"Translated from" header (the header proposed on 2026-09-30 was never adopted; decision recorded
2026-10-07 under TASK-30, because 310 header edits (161 in the three shipped modules) would change
the class files and sources jars of the shipped modules and re-fire the DEC-13 invalidation rule
for no information the records below do not already carry):

1. **Upstream identity of each file.** Translated classes keep their upstream package and
   simple name (`com.squareup.wire.ProtoReader` is `ProtoReader.kt` at the pin), so the source
   file is derivable from the name; the pin itself (tag 7.1.0, tag object
   `da24c33ee1fe772a7a04617018087f46f26d1708`, commit `9f62097dfe4995b5709d001ca0187e30ca0530ef`)
   is recorded once in `config/parity-pins.json` and `docs/decisions.md` (DEC-1).
2. **Upstream notices.** Each file carries the notice of its source verbatim (section 7.1),
   so the derivation is stated in the file wherever a notice exists; `docs/license-inventory.md`
   lists every production source and resource of the three shipped modules (wire-runtime-java,
   wire-schema-java, wire-java-generator) with its verified header class; the never-published
   wire-tests-java and wire-protoc-compat-java modules carry their upstream notices in the
   files and are not inventoried.
3. **Per-declaration decisions.** Section 2.1 platform-type nullability calls are recorded in
   the porting task's code-review disposition and, where behavior depends on them, pinned by a
   test (the TASK-5 boundary examples and the adapted upstream suites). The test-adaptation
   rules R1 to R5 (including R2 bridge signatures and R3 entry-point rewrites) are recorded per
   site in the adapted-test ledgers (section 6.2) and the case-accounting documents
   (`docs/task9-case-accounting.md`, `docs/task13-case-accounting.md`,
   `docs/task16-case-accounting.md`); `docs/m1-ownership-map.md` records where classes live.
   Comments inside method bodies are reserved for constraints the code cannot express, per
   repository norm.
4. **Upstream test cases** are mapped by identity to port artifacts in
   `config/upstream-case-map.json` and reconciled by the `parity-coverage` suite.
5. **Review trail and methodology narrative** live in the task records and git history of each
   porting task (every task carries its code-review disposition).

A new translated file therefore needs: the upstream package and name, the upstream notice
where one exists, an inventory row at release, and ledger rows for any adapted test. Section 6.2's
five-field ledger format is used by the runtime and protoc-compat adaptations
(`UPSTREAM-TEST-ADAPTATIONS.md`); schema, wire-tests-java and generator accounting use the
case-accounting table format of the documents named above.

## 8. Verification hooks

Where each convention is enforced. "CI" means `scripts/verify.sh`, the single entry point
(BUILD.md).

| Convention | Enforcement |
|---|---|
| Exception-type and message mapping (section 2) | TASK-5 AC#6 executable reference-versus-port examples run by the entry point, detecting changed exception or null behavior; adapted upstream suites asserting exception types and messages (`WireTest.java`, `TestAllTypes.java`, runtime commonTest) |
| Null-check placement (section 2.2) | Same TASK-5 examples (placement cases); adapted `WireTest.java` cases that observe the failure site |
| Equality, hashCode, toString (sections 3.1, 3.2) | Adapted upstream tests asserting equality, hashCode, and rendering (`UnknownFieldsTest.kt` for equality, `UnknownFieldsTest.java:77-87` for hashCode participation, schema commonTest element roundtrips); golden comparisons for generated types (TASK-16, OPEN-4) |
| copy/componentN bridges (section 3.1) | The adapted tests that need them (per-site ledger rows); compilation of adapted tests is the check that no needed bridge is missing |
| Collection mutability (section 3.3) | Adapted `WireTest.java` builder-mutability cases and `SerializableTest` |
| Unsigned parsing (section 4) | Adapted schema option-encoding cases exercising UINT32/UINT64/FIXED32/FIXED64 option literals; TASK-5 encoding-parity corpus |
| Java 11 language constraints (section 5.1) | `scripts/check-java11-bytecode.sh` (major version 55 ceiling); production compilation under `--release 11` fails records, sealed types, and preview features outright |
| Kotlin boundary (section 5.2) | Maven enforcer `bannedDependencies` (transitive), `scripts/check-classpath.sh` duplicate-class check over `config/retained-prefixes.txt`, both in CI; release-time dependency recheck by TASK-21 |
| Test adaptation rules and ledger (section 6) | Ledger review per adaptation; TASK-9 and TASK-13 case inventories; TASK-14 AC#6 source-versus-build reconciliation rejecting missing relevant cases |
| Exclusions discipline (section 6.5) | Ledger rows naming the DEC-6 exclusion; TASK-14 and TASK-17 case accounting |
| Provenance (section 7) | No automated check of production-file provenance exists: the `parity-coverage` suite reconciles upstream test-case identity only; enforcement is per-file notice review in each porting task's code review; `docs/license-inventory.md` verification at release (TASK-21); any vendored dependency's notices re-read from the pinned artifact |

## Maintenance

This document is the working contract for TASK-6 through TASK-18 translations and test
adaptations. It changes in the same turn as any decision it operationalizes: an OPEN-1
namespace landing (section 7.1's vendored-notice rules apply), a DEC-6 exclusion change, a
task acceptance-criteria renumbering that touches the AC references used here, or a new
upstream sync fact (section 4's general rule) each update the relevant section with its
authority cited.
