# Loading API inventory (TASK-4 AC#1)

Pinned-source map of every I/O and loading API the ported runtime and schema need, to the
selected implementation, with source-compatibility and Apicurio-migration consequences.
Evidence sources: import and call-site inventory over wire 7.1.0 wire-runtime and wire-schema
main sources (2026-10-01), and the okio 3.18.2 API as the semantic reference.

## Selected route

Vendored pure-Java okio subset in package `okio` inside wire-runtime-java: the buffer layer
copied 1:1 from okio 1.17.6, plus an original `Path`, `FileSystem`, `FileMetadata` layer
written for this port on top of `java.nio.file` (API modeled on okio 3, implementation
original). The buffer-half evidence (byte-identical verification, okio's own 732-test suite)
is recorded once, in docs/decisions.md under OPEN-1. No okio artifact and no Kotlin ships;
the DEC-4 production-Kotlin ban holds trivially.

## Map: buffer layer (okio 1.17.6, vendored verbatim)

| Required API | Implementation | Notes |
|---|---|---|
| ByteString and companion operations (encodeUtf8, decodeHex, decodeBase64, of, md5, sha256) | `okio.ByteString` | 1.17.6 surface covers all wire call sites; okio 3 companion extensions become Java statics when tests need them |
| Buffer, Buffer.clone, snapshot/readByteString | `okio.Buffer` | incl. SegmentedByteString |
| Source, Sink, BufferedSource, BufferedSink, PeekSource, Forwarding* | vendored | gzip family omitted, see exclusions |
| Utf8.size | `okio.Utf8` | upstream test adaptation R3 maps the okio 3 `utf8Size` extension here |
| IOException, EOFException | `java.io` | okio 3 JVM typealiases collapse to the JDK classes |
| Closeable | `java.io.Closeable` | upstream `okio.Closeable` typealias |

## Map: loading layer (original, on java.nio)

| Required API (wire call site) | Implementation | Consequences |
|---|---|---|
| `Path.Companion.toPath()` (11 sites) | `Path.get(String)`; the Kotlin extension is re-declared for verbatim tests in the test-scoped Kotlin adapter when M2 adopts them | source-compatible for Java callers; Kotlin tests get the extension back via test tooling |
| `Path.normalized()`, `.root`, `.volumeLetter`, `.div`, `.relativeTo`, `.parent` (SchemaHandler, Roots, ClaimedPaths, internal.Util) | `okio.Path` wrapping `java.nio.file.Path`; nio supplies separator, root, drive-letter semantics | `div` keeps okio's replace-when-absolute semantics, which wire's handler output sanitization depends on |
| `FileSystem` as SchemaLoader constructor parameter and WireRun field | abstract `okio.FileSystem` | the JDK-typed consumer API (TASK-25) layers on this without okio types in signatures |
| `FileSystem.Companion.asOkioFileSystem(java.nio.file.FileSystem)` (SchemaLoader jvmMain) | `FileSystem.asOkioFileSystem(nio)` static | SchemaLoader's existing nio constructor keeps working |
| `FileSystem.metadataOrNull` (Roots: isDirectory, symlinkTarget) | `FileSystem.metadataOrNull` returning `FileMetadata` (isRegularFile, isDirectory, byteSize, symlinkTarget) | attributes read with NOFOLLOW so a symlink reports as itself and symlinkTarget is populated; Roots' symlink following survives |
| `FileSystem.metadata` strict variant | final `metadata(Path)` throwing when absent | okio-shape convenience; the verbatim suites exercise it |
| `FileSystem.canonicalize` | over `toRealPath` | okio core member, mapped for completeness |
| `FileSystem.openZip` (Roots: protoPath entries that are jars; WireCompiler) | `FileSystem.openZip(Path)` over `java.nio.file` zipfs, entries rooted at `/` | ZIP is the loader's compression concern; gzip stays excluded |
| `FileSystem.read(path) { }` extension (Roots) | composed as `Okio.buffer(fs.source(path))` at the Java call site; mechanical mapping recorded for M2 | no lambda form in Java; behavior identical |
| `FileSystem.list`, `listOrNull`, `exists`, `source`, `sink`, `createDirectories`, `createDirectory`, `atomicMove`, `delete` (tests and compiler; FakeFileSystem parity) | implemented on the abstract class, nio-backed SYSTEM; `createDirectory` keeps okio's single-level semantics (no parent creation), `createDirectories` builds the chain | FakeFileSystem (okio's in-memory FS) is deferred to M2 where the verbatim schema tests need it; the classpath route below already covers Apicurio's descriptor loading |
| `FileSystem.appendingSink` | implemented over the vendored `Okio.sink` nio overload | owner: the deferred M2 FakeFileSystem parity row |
| `FileSystem` closeability (openZip handle) | `FileSystem implements Closeable`; closing a zip-backed system releases the archive, closing SYSTEM is a no-op; callers own the handle openZip returns | the lifecycle okio 3 gives the same operation; a long-running consumer (Apicurio) can reload roots without leaking descriptors |
| `ClassLoader.asResourceFileSystem()` (CoreLoader) | `FileSystem.asResourceFileSystem(loader)` read-only, known-resource reads; list is unsupported across providers and throws with guidance; directories are reported (URL trailing-slash convention) but byteSize is not probed (one connection per call, and the loader never reads it here) | replaces Apicurio's FakeFileSystem/setWorkingDirectory choreography (TASK-25/TASK-18) |
| `Path.isRelative`, `Path.isEmpty`, `Path.resolve` | trivial derivations kept for okio API shape; `resolve` is the Java-idiomatic alias of `div` used by translated call sites | mapped here so the surface stays traceable |
| `FileHandle` | not implemented | zero call sites in wire 7.1.0 runtime and schema main; it appears only in Apicurio's own loader; revisit if TASK-18's re-inventory finds otherwise |

## Excluded, with scope justification

- GzipSource/GzipSink, DeflaterSink, InflaterSource, Pipe, hashing wrappers, PushableTimeout,
  okio package-info: zero imports in the ported slice (verified by inventory); ZIP loading
  covers the loader's archive need.
- FileHandle, openReadOnly/openReadWrite, createSymlink, copy, listRecursively: zero call
  sites in the ported slice; each is a compatibility-matrix row away if M1/M2 evidence
  demands it.

## Source-compatibility and migration consequences

- Java callers of the port see the same `okio.Path`/`FileSystem` names as upstream, so
  translated wire-schema code compiles unchanged; the names live in the port's own jar
  (duplicate-class policy: the origin check proves no coexistence with a real okio artifact).
- Apicurio's migration sites (DEC-2) gain a simpler route: classpath descriptor loading via
  `asResourceFileSystem`, no FakeFileSystem; TASK-18 re-inventories the site list before
  editing.

## Demonstrated (TASK-4 AC#2, LoadingAccessTest, runs in the build suite)

In-memory buffer round trip; host-filesystem write, read, metadata, list, atomic move,
delete; classpath resource read plus absent-resource failure; ZIP archive open, entry read,
entry metadata, list; path semantics including absolute-div replacement and normalization.
All on JDK 17 build with `--release 11` bytecode, re-verified by the java11-consumer suite on
a real Temurin 11.
