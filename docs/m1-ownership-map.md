# M1 symbol ownership map (TASK-6 AC#1)

Every wire-runtime foundation symbol mapped to its owning task, with the re-slice decisions
the compilation order forced. Rule: a symbol lands in the earliest task whose symbols
reference it, so TASK-6 compiles with no placeholders (AC#2); later tasks own refinements and
their tests. Source: wire 7.1.0 wire-runtime commonMain/jvmMain inventory.

## TASK-6 (compiling runtime foundation and encoding core) owns

- Annotations: WireField (with Label and its isRepeated/isPacked/isOneOf accessors),
  WireOneofField, WireEnum, WireEnumConstant, WireRpc, WireSealedOneof, WireEnclosingType.
- ReverseProtoWriter (required by generated Java code and by adapter encode overloads).
- internal/Internal.kt foundation helpers (camelCase variants, sanitize/unescape,
  identityOrNull equivalents, immutableList/copyOf) as needed by Message/OneOf/ProtoAdapter;
  the remaining Internal surface stays with TASK-8 which owns its tests.
- Well-known types AnyMessage, Duration, Instant, FieldMask: RE-SLICE from TASK-8 to TASK-6,
  because ProtoAdapter.kt declares their adapters (AnyAdapter, DurationProtoAdapter, ...) as
  companions of the types themselves and AC#2 forbids stubs. TASK-8 keeps their tests and any
  residual helpers (ArrayList utilities, MathMethods, MutableOnWriteList, Util).
- ProtoAdapter and every adapter it declares: primitives (BOOL..DOUBLE, SINT/FIXED variants,
  STRING, BYTES), OBJECT/ONE_OF constants, Uint32/Int32 sizes, the box/encodeWithTag family,
  MapAdapter, EnumConstantNotFoundException, newMapAdapter, decodeByteArray helpers.
- FieldEncoding.rawProtoAdapter() (deferred from M0; owned here once ProtoAdapter exists).
- Message and Message.Builder, plus a minimal MessageSerializedForm RE-SLICE from TASK-7
  (writeReplace is part of Message's contract; it only needs encode plus a Class reference;
  the reflection registry stays TASK-7).
- EnumAdapter (with the KClass-to-Class API mapping below).
- OneOf and OneOf.Key.

## TASK-7 (reflection machinery) owns

ProtoAdapter's reflection paths (createRuntimeMessageAdapter, the @WireField field binding
machinery, RuntimeMessageAdapter, MessageBinding/FieldOrOneOfBinding), the Wire.get default
helper, RuntimeEnumAdapter, KotlinConstructorBuilder, the ADAPTER-string and Class lookups on
ProtoAdapter's companion (newMessageAdapter, newEnumAdapter, get overloads; landed 2026-10-01
batch 6). Deferred with cause: AndroidMessage (Android-only, no consumer in scope until an
Android consumer exists; needs a provided-scope android dependency to compile) and
JsonIntegration with the JSON formatters (their only callers are the gson/moshi adapters that
DEC-6 excludes from the initial release; RuntimeMessageAdapter.writeAllFields, their
integration point, is translated). MessageSerializedForm's minimal direct-ADAPTER lookup
landed in TASK-6 and needs no change when TASK-7's get() arrived (both read the same public
static field).

## TASK-8 (well-known parity and internal helpers) owns

Tests for the well-known types moved into TASK-6, the internal ArrayList family,
MathMethods, MutableOnWriteList, Util, and any Internal helpers TASK-6 did not need.

## TASK-9 owns the upstream test adoption (ProtoAdapterTest and the rest of the suites).

## API mapping decisions recorded for the compatibility matrix

- Kotlin `KClass<E>` constructor parameters (EnumAdapter, ProtoAdapter.create) become
  `Class<E>`; Kotlin callers pass `E::class.java` (mechanical adaptation, to be ledgered per
  site when the upstream tests are adopted).
- AnyMessage.pack(adapter, value) with no type URL: upstream renders the Kotlin KClass and its
  message mentions Kotlin reflection ("cannot pack class X (Kotlin reflection is not
  available): ..."); the Java port renders the plain class name and omits the clause, because
  KClass rendering is a Kotlin-runtime facility. Recorded as an accepted, documented
  exception-text divergence (TASK-9 will keep any upstream assertion on the Kotlin form
  adapted per site).
- StructListAdapter passes Map.class as its type, matching upstream's commonStructList which
  uses Map::class for a List adapter; a deliberate upstream quirk preserved so adapter.type
  is reflection-identical.
- Kotlin property accessors with @JvmName (Label.isRepeated etc.) become plain methods.
- internal visibility becomes public per the translation conventions.
- expect/actual pairs fold into a single Java class carrying the JVM actual's behavior.

## Excluded here (already owned elsewhere)

ProtoReader/ProtoWriter/FieldEncoding/Syntax/ProtocolException and the okio layer: M0/TASK-4.
ProtoReader32/ByteArrayProtoReader32/ProtoReader32AsProtoReader: translated in TASK-6 batch 5,
with the adapter-side decode(ProtoReader32) as base-class defaults routing through the reader
wrapper (byte-identical to upstream's JVM default); the per-adapter direct overloads are
TASK-20 performance work.
