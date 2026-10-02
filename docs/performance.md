# Runtime and Apicurio schema performance comparison (TASK-20)

Measured on 2026-10-02 in two sessions: 12:08-12:56 CEST (initial full matrix, 15 wire
benchmarks) and 13:36-13:50 CEST (decode re-measurement after the decode entry-point
fix: the four decode cells plus three encode control cells, identical JMH configuration).
Every number below comes from a JMH run whose
command and raw output are recorded; nothing is transcribed by hand from memory. The
scratch harness lives outside this repo at `/tmp/aw-perf` (JMH never enters the antiwire
repo, and the two measured implementations never share a classpath; AC#4).

Discipline for AC#2: the noise band and the regression criterion were fixed in code
(`tools/analyze.py`, phase 1 computes the band purely from upstream-vs-upstream repeat
variance) before any port-vs-upstream number was computed. The judgment pass is a separate
phase of the same script and consumes only the band derived in phase 1.

## 0. Measured candidate identity (AC#5)

| Item | Value |
|---|---|
| antiwire revision, initial run (unmarked numbers below) | `0e87bde8081ebe4a9203effd84f584154d8b887c` (branch `m0-spikes`) |
| Tree state at initial build | only `backlog/tasks/task-20 ...md` modified (documentation; no code delta vs the committed tree) |
| Build, initial | `JAVA_HOME=~/.sdkman/candidates/java/17.0.12-tem mvn clean install -DskipTests`, BUILD SUCCESS, exit 0 |
| antiwire revision, decode re-measurement (cells marked "re-measured") | `c0a215213c85b8a75d1d046f87f894b8988d792c` (branch `m0-spikes`; the decode entry-point fix of section 6 is the only runtime code delta vs the initial candidate) |
| Tree state at re-measurement build | only `backlog/tasks/task-20 ...md` modified (documentation; no code delta vs the committed tree) |
| Build, re-measurement | `JAVA_HOME=~/.sdkman/candidates/java/17.0.12-tem mvn -pl wire-runtime-java -am install -DskipTests`, exit 0 |
| Oracle | upstream Wire 7.1.0 artifacts resolved from Maven Central (never built from source); the same oracle jar measured in both sessions |
| Verify run | `./scripts/verify.sh` at the initial revision: `VERDICT: all 11 ACTIVE suites passed.` |
| Test gate at the re-measurement revision | `mvn -pl wire-runtime-java,wire-tests-java,wire-protoc-compat-java test` green: 869 / 80 / 122 tests, 0 failures, 0 errors (4 / 3 / 50 skipped) |

Measured antiwire artifacts (`io.github.paoloantinori`, version `0.1.0-SNAPSHOT`):

| Artifact | SHA-256 initial run (`0e87bde`) | SHA-256 decode re-run (`c0a2152`) |
|---|---|---|
| `wire-runtime-java` | `cc7f4cac0a5d611f9e0f6959ba2106327124e06380532850d64030764ed897be` | `43c810fb5f970fec29bf2a2a0465e30b5ee7aa1dc1dbbf4d11c9249455c1307c` |
| `wire-schema-java` | `7bc11249a3af12457c9caeff66cc942eb5ebf8f6d0d2502cb132076015cacaf5` | same (jar not rebuilt by `-pl wire-runtime-java -am install`; verified) |
| `wire-java-generator` | `c7d9753c3a5c37728c37d86209869b039c64f8803118adf45ec2ec63685a8b6c` | same (jar not rebuilt; verified) |

Oracle jars (Maven Central, checksums of the exact files used):

| Artifact | SHA-256 |
|---|---|
| `com.squareup.wire:wire-runtime-jvm:7.1.0` | `a26cef81c2f361cec68e37cfddaaa207df568f6a72661214f4856be70940e073` |
| `com.squareup.wire:wire-schema-jvm:7.1.0` | `29761694316c1fd8b3478dff36eac19b21c37d4e00fe670eb759f2473e124075` |
| `com.squareup.wire:wire-compiler:7.1.0` | `8f4ea1f5a7f2739c49491b1cf2c4437bcb2a84866cef3b141a95a32b318ac215` |
| `org.jetbrains.kotlin:kotlin-stdlib:2.0.21` | `f31cc53f105a7e48c093683bbd5437561d1233920513774b470805641bedbc09` |
| `com.squareup.okio:okio-jvm:3.18.2` | `681fed549d1d2c63710f42c9eab433d642c71af778babd67035c50f8c425aaff` |

Resolved dependency identities of the harness profiles (from `mvn dependency:list`):

- `port` profile: `io.github.paoloantinori:wire-runtime-java` + `wire-schema-java`
  (nothing else beyond JMH 1.37 and the protobuf-java reference jar). The port has no
  runtime dependency of its own; okio is vendored inside `wire-runtime-java`.
- `upstream` profile: `wire-runtime-jvm` + `wire-schema-jvm` 7.1.0 with
  `kotlin-stdlib` 2.0.21, `kotlin-reflect` 2.0.21 (aligned to the stdlib the oracle is
  built against; the transitively resolved newer reflect needs stdlib 2.1+ symbols and
  fails at class load otherwise), `okio-jvm` 3.18.2, plus wire-schema's transitive
  `guava` 33.7.1-jre, `com.squareup:kotlinpoet-jvm` 2.3.0, `com.palantir.javapoet` 0.19.0.

Generated-model identity: the workload protos were compiled with BOTH command-line
generators (upstream `wire-compiler` 7.1.0 and the antiwire `wire-java-generator` CLI at
the revisions above; the generator jar is byte-identical in both sessions); the two
generated trees are byte-identical (`diff -rq` clean, 7 files), and the re-measurement
reused them unchanged (only the `wire-runtime-java` dependency of the port bench jar
changed).

Invalidation rule (AC#5): any change to `wire-runtime-java`, `wire-schema-java`, or
`wire-java-generator` (source, pom, or packaging), any change of the oracle versions, or
any change to the harness workloads invalidates these results and the acceptance state;
renewed measurements and renewed maintainer acceptance are required before TASK-21. A
candidate other than the revisions and checksums above cannot use this record. The rule
fired once: the decode entry-point fix (`c0a2152`, the only runtime code delta vs
`0e87bde`) invalidated the initial matrix, and the 13:36 session renewed exactly the
affected surface: all four decode cells plus, as controls, the three encode cells that
sat closest to their noise bands (email forward writer, packed and bytes reverse
writers). Encode and schema code paths are source-identical between the two candidates,
so the unmarked encode, schema, and protobuf-java numbers carry over from the initial
session under that stated equivalence.

## 1. Environment

| Item | Value |
|---|---|
| CPU | Apple M3 Pro (Mac15,6), arm64; 6 performance + 6 efficiency cores, 12 logical |
| RAM | 36 GB |
| OS | macOS 26.7.1 (25G241) |
| Benchmark JVM | OpenJDK 17.0.12, Temurin-17.0.12+7 (both profiles, same JVM) |
| Build tool | Apache Maven 3.9.12 |
| JMH | 1.37 (jmh-core + annotation processor), `-prof gc` |
| Run policy | sequential runs only (no parallel benchmark JVMs); upstream and port repetitions interleaved |

## 2. Harness, workloads, reproduction

Harness location: `/tmp/aw-perf` (scratch; not part of the repo). Layout:

- `pom.xml`: one module, two profiles (`port`, `upstream`). Each profile adds exactly one
  implementation set to the classpath; the shared benchmark sources compile against
  either. The schema operations go through a tiny SPI (`perf.schema.spi.SchemaSpi`) whose
  implementation class has the same FQCN in profile-specific source trees, so no profile
  class ever appears on the other profile's classpath.
- `protos/`: workload protos (below). `gen/port`, `gen/upstream`: the two generated model
  trees (byte-identical). `gen/pb`: protoc 4.36.1 models for the protobuf-java reference.
- `src/main/java/perf/`: shared benchmarks and sample data.
  `src-port/java` / `src-upstream/java`: the two SPI implementations.
- `run-all.sh`, `tools/analyze.py`, `tools/render_tables.py`: measurement and analysis.
  `run-decode-rerun.sh` + `tools/analyze_rerun.py`: the 13:36 decode re-measurement
  (same flags and interleave, 7 cells) and its old-vs-new pairing; `analyze.py` itself was
  not modified.
- `results/`: JMH JSON, identity fingerprints, oracle checksums, analysis output.

### Workload 1 (runtime): corpus, sizes, upstream JMH mapping

| Workload | Message | Encoded size | Upstream JMH case or justification |
|---|---|---|---|
| (a) small/medium message | `EmailSearchResponse` decoded from the upstream `medium_value.bytes` | 672 B | `SimpleMessageBenchmark` (same payload resource, same encode/decode call shapes) |
| (b) large nested message | `all_types` from `wire-tests/fixtures/proto/java/all_types.proto`, values mirroring upstream `all_types_proto2.json` (65 in-message fields set; `ext_*` omitted because the fixtures proto declares them in a separate extend block, `default_*` are field defaults, the 4 `map_*` fields exist in the fixtures proto and stay unset, and the benchmarks proto's `oneof_int32` does not exist in the fixtures proto) | 483 B | `AllTypesBenchmark` proto2 side |
| (c) repeated/packed lists | `PackedLists`: 4 repeated and 8 packed (proto2 `[packed = true]`) scalar lists, 256 elements each | 18,437 B | No upstream case isolates packed lists (`AllTypesBenchmark` carries 2-element packed lists inside all_types); this workload isolates the packed-varint loops |
| (d) bytes-heavy | `Blob`: 1 KiB head + 16 x 1 KiB chunks | 17,472 B | No upstream case isolates bytes; justified by the registry's ByteString-dominated payloads |

The two harness-authored protos, verbatim:

```proto
// protos/packed/packed_lists.proto
syntax = "proto2";
package perf.packed;
option java_outer_classname = "PackedListsProto";
option java_package = "perf.packed";
message PackedLists {
  repeated int32 rep_int32 = 1;
  repeated int64 rep_int64 = 2;
  repeated double rep_double = 3;
  repeated string rep_string = 4;
  repeated int32 pack_int32 = 11 [packed = true];
  repeated sint32 pack_sint32 = 12 [packed = true];
  repeated int64 pack_int64 = 13 [packed = true];
  repeated uint64 pack_uint64 = 14 [packed = true];
  repeated fixed64 pack_fixed64 = 15 [packed = true];
  repeated double pack_double = 16 [packed = true];
  repeated float pack_float = 17 [packed = true];
  repeated bool pack_bool = 18 [packed = true];
}
```

```proto
// protos/bytes/bytes_heavy.proto
syntax = "proto2";
package perf.bytes;
option java_outer_classname = "BlobProto";
option java_package = "perf.bytes";
message Blob {
  optional bytes head = 1;
  repeated bytes chunks = 2;
  optional string label = 3;
}
```

### Workload 2 (schema): the Apicurio operations

Not upstream JMH cases; justification is the registry hot path each operation names:

- `parse`: raw `ProtoParser` over the 20 protos of
  `/tmp/apicurio/utils/protobuf-schema-utilities/src/test/proto` (26,088 bytes), one op =
  the whole corpus. This is the entry point Apicurio's `ProtobufSchemaParser` calls.
- `link+load`: fresh `SchemaLoader`/`JdkSchemaLoader` choreography over a staged tree
  (parse + link + load; 47 files load per op: 20 corpus + 18 staged google dependencies +
  9 bundled core protos). Upstream runs `SchemaLoader(FileSystem.SYSTEM)` on the same
  staged directory the port's `JdkSchemaLoader` reads; both do real filesystem IO with a
  warm page cache, mirroring each library's production loading path.
- `descriptor`: `SchemaEncoder` conversion of the 20 corpus schemas (schema loaded once
  per trial; one op = 20 FileDescriptorProto encodings, 25,971 output bytes), the
  schema-to-descriptor step behind Apicurio's `FileDescriptorUtils`.

The staged google dependency closure (38 staged files total): `google/protobuf/{api,
source_context,type}.proto` and 15 `google/type/*.proto` taken from the archives the
Apicurio build itself stages (`protobuf-java` 4.36.2 and `proto-google-common-protos`
2.77.0 gencode archives); the remaining well-knowns resolve from each library's own
bundled core loader, as in production.

### Workload 3 (protobuf-java reference)

The same four messages through `protobuf-java` 4.36.2 with protoc 4.36.1-generated
models, in both profiles (identical code and classpath scope). Reference point only,
never a substitute oracle: it validates that both wire implementations produce
byte-identical payloads (section 3) and serves as a same-machine control for
environmental drift.

### JMH configuration

From the benchmark annotations (visible in every raw log): `-f 2` forks,
`-wi 5` warmup iterations, `-i 10` measurement iterations, `-w 1s -r 1s`, mode
`Throughput` (ops/s), `-prof gc`, JSON result per run. Two independent repetitions per
profile, interleaved `upstream, port, upstream, port` (start times 12:08, 12:20, 12:32,
12:43; last run finished 12:55). The 13:36 re-measurement used the identical
configuration and interleave (start times 13:36, 13:40, 13:43, 13:47; last run finished
13:50) with the same untouched upstream oracle jar. Medians and min-max are computed
over all 40 iterations (2 repetitions x 2 forks x 10 iterations) per profile and
benchmark; allocation is the `gc.alloc.rate.norm` median of repetition 1.

### Reproduction commands

```bash
# 1. fresh antiwire install (record revision + jar checksums)
cd /Users/pantinor/data/repo/work/antiwire
git rev-parse HEAD
JAVA_HOME=~/.sdkman/candidates/java/17.0.12-tem mvn clean install -DskipTests

# 2. oracle jars (exact set in /tmp/aw-perf/results/oracle-jar-checksums.txt), then
#    generate models with BOTH CLIs from the same protos tree:
#    upstream: java -cp <oracle jars> com.squareup.wire.WireCompiler \
#                --proto_path=/tmp/aw-perf/protos --java_out=/tmp/aw-perf/gen/upstream
#    port:     java -cp <antiwire jars + com.squareup:javapoet:1.13.0> \
#                com.squareup.wire.WireCompiler \
#                --proto_path=/tmp/aw-perf/protos --java_out=/tmp/aw-perf/gen/port
#    diff -rq /tmp/aw-perf/gen/port /tmp/aw-perf/gen/upstream   # must be identical
#    protoc (pinned by scripts/install-protoc.sh):
~/.cache/antiwire-protoc/4.36.1-osx-aarch_64/protoc --proto_path=/tmp/aw-perf/protos \
    --java_out=/tmp/aw-perf/gen/pb squareup/wire/benchmarks/*.proto \
    alltypes/all_types.proto packed/packed_lists.proto bytes/bytes_heavy.proto

# 3. build the two bench jars (one implementation per classpath)
cd /tmp/aw-perf
JAVA_HOME=~/.sdkman/candidates/java/17.0.12-tem mvn -Pport package     # target/aw-perf-port.jar
JAVA_HOME=~/.sdkman/candidates/java/17.0.12-tem mvn -Pupstream package # target/aw-perf-upstream.jar

# 4. identity fingerprints under both classpaths (must match; section 3)
~/.sdkman/candidates/java/17.0.12-tem/bin/java -cp target/aw-perf-port.jar perf.IdentityCheck
~/.sdkman/candidates/java/17.0.12-tem/bin/java -cp target/aw-perf-upstream.jar perf.IdentityCheck

# 5. measurement sequence (flags come from the annotations)
/tmp/aw-perf/run-all.sh   # writes results/{rt,pb,sc}-{upstream,port}-{1,2}.{json,txt}

# 5b. decode re-measurement after the entry-point fix (candidate c0a2152): rebuild the
#     port bench jar against the reinstalled runtime (mvn -Pport package), re-verify
#     perf.IdentityCheck against results/identity-port.txt, then
/tmp/aw-perf/run-decode-rerun.sh   # writes results/rt2-{upstream,port}-{1,2}.{json,txt}

# 6. analysis: noise band from the oracle first, then the port judgment
python3 /tmp/aw-perf/tools/analyze.py
```

## 3. Identity verification (same operation, same data on both sides)

`perf.IdentityCheck` under each classpath prints identical lines on both sides
(`/tmp/aw-perf/results/identity-port.txt` vs `identity-upstream.txt`, diff clean):

| Fingerprint | Value (both sides) |
|---|---|
| email encode | `sha256:cc02806df4e30c2ac7f67be2e4a0c4935644395cd1e358724971a71c40ff7c69`, 672 B (equals the upstream `medium_value.bytes` payload) |
| email decode-reencode | identical (round trip stable) |
| all_types encode | `sha256:ba3c8a3131ef594dab755f2f9ed79e7113f9e370b89c5ae2433eb6549ad902ae`, 483 B |
| packed encode | `sha256:a7a7e0c6956fe56cfa615bbf9fb6c906a212fdb47876bb59c73db88d782784c7`, 18,437 B |
| blob encode | `sha256:d6892b8f22104848477632a8604ac04421ea2e1a1fc926311938fb1af23c0dc7`, 17,472 B |
| every protobuf-java encode | byte-identical to the wire encodes above |
| schema canonical text (47 files, `toSchema()` ordered) | `sha256:b2c2307d112c46846dc52d824988f0bd5eb0d6c34a613a93ffa9fe7fe4ecc7e1` |
| descriptor output (20 files) | 25,971 B total, identical |
| loaded files / parsed elements | 47 / 53 |

Both profiles therefore measure the same operation on the same data (AC#4), and the
generated model classes are the same code compiled against each implementation.

## 4. Noise band derived from the oracle, before judging the port (AC#2)

Phase 1 of `tools/analyze.py` measures the oracle against itself: two independent
upstream repetitions (plus the 2 forks within each) across all 15 wire benchmarks
(12 runtime + 3 schema). Observed oracle variance:

| Statistic | Value |
|---|---|
| Max run-to-run median deviation | 24.9% (`EmailSearchBench.encodeReverse`, one repetition dropped to 2.47M from 3.17M ops/s) |
| Median run-to-run deviation | 2.7% |
| Max fork-to-fork median spread | 16.7% (`BytesBench.decode`) |
| protobuf-java control (same code in both profiles) | max 6.7% run-to-run across 8 benchmarks, all port/upstream ratios 0.981-1.013 |

Pre-declared criterion (fixed in `tools/analyze.py` before any port number was computed):
noise band = max observed oracle deviation rounded up to the next 5%, i.e. **±25%**; a
regression is a port median below `upstream median x 0.75`.

Because that global band is inflated by a single outlier repetition, the doc also reports
a stricter, still purely oracle-derived per-benchmark criterion: each benchmark's own
`max(run-to-run deviation, fork spread)` rounded up to the next 5%. Findings are listed
for maintainer review when they exceed their own per-benchmark band (section 6); the
global band alone flags exactly one of them. The 13:36 decode re-measurement is judged
with these same bands, taken unchanged from the initial session; no band was recomputed
after any port number existed.

## 5. Results

ops/s medians over 40 iterations; range = min-max over all iterations of both repetitions.
Rows marked "re-measured" come from the 13:36 session (candidate `c0a2152`, after the
decode entry-point fix); unmarked rows are the initial 12:08 session (candidate
`0e87bde`). The superseded initial values of every re-measured row are preserved in the
"initial run" note after workload (d).

### Workload (a) small/medium message: EmailSearchResponse, 672 bytes

| operation | upstream 7.1.0 ops/s median [min-max] | antiwire ops/s median [min-max] | ratio port/upstream | alloc/op upstream (B) | alloc/op port (B) |
|---|---|---|---|---|---|
| encode, forward writer (3x path), re-measured | 607,495 [580,520-616,095] | 480,113 [352,409-519,003] | 0.790 | 16 | 16 |
| encode, reverse writer (4x+ path) | 2,953,000 [1,789,809-3,230,423] | 2,973,259 [2,446,299-3,109,068] | 1.007 | 200 | 176 |
| decode, re-measured | 2,481,591 [2,302,039-2,614,432] | 2,243,927 [1,571,195-2,354,498] | 0.904 | 4,080 | 4,296 |
| protobuf-java encode (reference) | 10,830,466 [9,682,246-11,178,706] | 10,626,397 [8,579,606-10,892,147] | 0.981 | 728 | 728 |
| protobuf-java decode (reference) | 4,586,111 [4,455,404-4,750,907] | 4,608,882 [4,248,969-4,785,371] | 1.005 | 1,784 | 1,784 |

### Workload (b) all_types: fixtures proto2 message, 483 bytes

| operation | upstream 7.1.0 ops/s median [min-max] | antiwire ops/s median [min-max] | ratio port/upstream | alloc/op upstream (B) | alloc/op port (B) |
|---|---|---|---|---|---|
| encode, forward writer (3x path) | 947,404 [787,158-967,781] | 1,008,157 [886,871-1,047,331] | 1.064 | 16 | 16 |
| encode, reverse writer (4x+ path) | 1,306,884 [1,075,505-1,393,376] | 1,543,410 [1,299,563-1,565,150] | 1.181 | 200 | 176 |
| decode, re-measured | 526,562 [373,354-537,967] | 515,856 [459,413-545,063] | 0.980 | 8,184 | 8,904 |
| protobuf-java encode (reference) | 1,579,712 [1,513,665-1,623,946] | 1,578,663 [1,401,190-1,627,699] | 0.999 | 1,208 | 1,208 |
| protobuf-java decode (reference) | 821,837 [599,695-836,812] | 818,097 [783,850-836,893] | 0.995 | 5,176 | 5,176 |

### Workload (c) repeated/packed scalar lists: 256 elements per list, 18,437 bytes

| operation | upstream 7.1.0 ops/s median [min-max] | antiwire ops/s median [min-max] | ratio port/upstream | alloc/op upstream (B) | alloc/op port (B) |
|---|---|---|---|---|---|
| encode, forward writer (3x path) | 30,334 [26,330-31,695] | 30,969 [29,300-32,888] | 1.021 | 16 | 16 |
| encode, reverse writer (4x+ path), re-measured | 61,603 [51,960-62,546] | 62,429 [59,311-63,501] | 1.013 | 200 | 176 |
| decode, re-measured | 23,853 [9,365-25,122] | 29,125 [23,652-30,689] | 1.221 | 127,932 | 128,232 |
| protobuf-java encode (reference) | 83,177 [79,185-87,744] | 83,628 [81,694-86,112] | 1.005 | 10,296 | 10,296 |
| protobuf-java decode (reference) | 69,940 [56,013-71,246] | 69,681 [68,081-70,556] | 0.996 | 61,744 | 61,744 |

### Workload (d) bytes-heavy: 1 KiB head + 16 x 1 KiB chunks, 17,472 bytes

| operation | upstream 7.1.0 ops/s median [min-max] | antiwire ops/s median [min-max] | ratio port/upstream | alloc/op upstream (B) | alloc/op port (B) |
|---|---|---|---|---|---|
| encode, forward writer (3x path) | 2,091,215 [1,830,283-2,238,350] | 2,099,966 [1,828,347-2,256,359] | 1.004 | 16 | 16 |
| encode, reverse writer (4x+ path), re-measured | 1,292,647 [1,111,636-1,486,514] | 1,326,396 [1,116,746-1,486,538] | 1.026 | 200 | 176 |
| decode, re-measured | 1,459,290 [1,108,650-1,802,614] | 1,181,602 [743,400-1,348,259] | 0.810 | 19,020 | 19,064 |
| protobuf-java encode (reference) | 1,297,388 [1,251,118-1,325,824] | 1,314,852 [1,149,988-1,344,741] | 1.013 | 4,184 | 4,184 |
| protobuf-java decode (reference) | 2,100,458 [1,870,417-2,129,809] | 2,071,167 [1,281,772-2,116,236] | 0.986 | 18,472 | 18,472 |

Initial run note (2026-10-02, 12:08-12:56 CEST, candidate `0e87bde`): the honest first
measurement. Every re-measured row above supersedes a row measured then, on the candidate
whose `decode(byte[])`/`decode(ByteString)` still wrapped the payload in an okio Buffer
and entered the long `ProtoReader`. Superseded values, format: upstream median
[min-max]; port median [min-max]; ratio; alloc/op upstream / port.

| Cell | initial run values (superseded 13:36) |
|---|---|
| (a) email decode | 2,269,059 [1,620,453-2,558,472]; 1,812,167 [1,584,194-2,015,037]; 0.799; 4,104 / 4,176 |
| (b) all_types decode | 532,061 [458,162-549,670]; 498,961 [457,108-509,195]; 0.938; 8,184 / 8,824 |
| (c) packed decode | 24,854 [23,433-25,350]; 22,299 [21,192-22,877]; 0.897; 127,968 / 128,152 |
| (d) bytes decode | 1,579,290 [1,183,603-1,676,027]; 793,070 [718,169-812,528]; 0.502; 19,020 / 18,964 |
| (a) email encodeForward | 601,262 [548,075-616,892]; 521,036 [406,535-569,878]; 0.867; 16 / 16 |
| (c) packed encodeReverse | 62,399 [61,275-62,893]; 59,875 [56,213-61,361]; 0.960; 200 / 176 |
| (d) bytes encodeReverse | 1,311,620 [1,222,807-1,432,649]; 1,275,030 [1,203,631-1,428,197]; 0.972; 200 / 176 |

### Workload 2: Apicurio schema operations

| operation | upstream 7.1.0 ops/s median [min-max] | antiwire ops/s median [min-max] | ratio port/upstream | alloc/op upstream (B) | alloc/op port (B) |
|---|---|---|---|---|---|
| parse: ProtoParser, 20 files / 26,088 bytes per op | 8,044 [7,067-8,387] | 8,990 [7,596-9,182] | 1.118 | 407,443 | 422,041 |
| link+load: full schema load, 47 files per op | 358 [225-368] | 388 [336-398] | 1.084 | 8,342,591 | 8,157,656 |
| descriptor: SchemaEncoder, 20 files / 25,971 bytes out per op | 9,847 [9,637-9,946] | 11,426 [10,901-11,754] | 1.160 | 285,819 | 223,730 |

Reading notes:

- Schema operations (the Apicurio path this port exists for) are consistently FASTER on
  the port: parse 1.118x, link+load 1.084x, descriptor conversion 1.160x, the last with
  22% less allocation per op.
- Runtime encode is at parity (within per-benchmark noise) or better on 7 of 8 measured
  cells across the forward and reverse writers; the exception is the email
  forward-writer cell (0.867 initial, 0.790 re-measured, finding 3 below, still open).
  `all_types` reverse encode is 1.181x faster with 12% less allocation; the packed and
  bytes reverse-writer cells re-measured at 1.013x and 1.026x confirm their initial
  0.960/0.972 readings were noise-edge parity, not deficits.
- Decode after the entry-point fix (all four cells re-measured): packed lists 1.221x
  (the port is now faster than upstream), all_types 0.980x, email 0.904x, bytes-heavy
  0.810x. Every decode cell now sits inside its own oracle noise band, though the
  bytes-heavy margin is thin (0.810 against a 0.800 floor; see section 6). Allocations
  remain within 9% of upstream everywhere (all_types +8.8%, email +5.3%, packed +0.2%,
  bytes +0.2%), so the residual bytes-heavy deficit is CPU-path, not garbage.

## 6. Acceptance: PENDING maintainer (AC#3)

No regression is accepted by this document. The initial run flagged four findings: under
the pre-declared global band (±25%) exactly one was a regression, and the stricter
per-benchmark oracle band (section 4) added three more for review. Per AC#3 each required
an explicit maintainer acceptance record or a fix before TASK-21. Findings 1, 2, and 4
were fixed at the decode entry point (candidate `c0a2152`) and closed by re-measurement
under the identical configuration and the unchanged bands; finding 3 is outside that
fix's reach and remains open.

### Resolved by the decode entry-point fix (re-measured within their bands)

"Before" is the initial session (candidate `0e87bde`), "after" the 13:36 re-measurement
(candidate `c0a2152`), same JMH configuration, same untouched oracle jar. Per-run
medians order: up r1, up r2, port r1, port r2 (ops/s).

| # | Benchmark | before: ratio / port median / per-run medians | after: ratio / port median / per-run medians | own oracle noise (run2run / fork) | status |
|---|---|---|---|---|---|
| 1 | `perf.BytesBench.decode` | 0.502 / 793,070 / 1,537,994; 1,579,290; 769,015; 808,852 | 0.810 / 1,181,602 / 1,415,125; 1,505,123; 1,181,602; 1,234,145 | 2.6% / 16.7% | RESOLVED (within own ±20% and global ±25%; margin is thin: 0.810 vs the 0.800 own-band floor) |
| 2 | `perf.EmailSearchBench.decode` | 0.799 / 1,812,167 / 2,269,059; 2,323,509; 1,789,157; 1,812,167 | 0.904 / 2,243,927 / 2,501,968; 2,371,751; 2,256,531; 2,205,160 | 2.4% / 15.9% | RESOLVED (within own ±20%) |
| 4 | `perf.PackedBench.decode` | 0.897 / 22,299 / 25,098; 24,239; 22,318; 21,877 | 1.221 / 29,125 / 21,963; 24,802; 30,150; 27,090 | 3.5% / 2.1% | RESOLVED, now faster (upstream rep 1 wobbled, min iteration 9,365, so read 1.221 as at-or-above parity) |

### Still PENDING maintainer

| # | Benchmark | initial: ratio / per-run medians | re-measured: ratio / per-run medians | own oracle noise (run2run / fork) | band exceeded | status |
|---|---|---|---|---|---|---|
| 3 | `perf.EmailSearchBench.encodeForward` | 0.867 / 606,311; 598,849; 499,066; 531,901 | 0.790 / 607,701; 607,410; 504,921; 416,981 | 1.2% / 2.0% | own ±5%, in both sessions | PENDING maintainer |

Finding 3 doubled as the re-run's control for the fix's scope: `encodeForward` constructs
no reader, the decode entry-point change cannot reach it, and it did not move (allocation
still identical at 16 B/op; upstream stable at ~607k ops/s across both re-run repetitions
while the port read 505k/417k). The deficit reproduces in the forward-writer path and
still requires an explicit maintainer acceptance record or a fix before TASK-21.

Adjacent cells re-measured as controls, all now inside their own bands (no acceptance
needed): `AllTypesBench.decode` 0.980 (own band ±10%), `PackedBench.encodeReverse` 1.013
and `BytesBench.encodeReverse` 1.026 (own bands ±5%); these supersede the initial 0.938,
0.960, and 0.972 readings.

Mechanism evidence gathered (read, not inferred; then confirmed by intervention):

- Decode findings: upstream 7.1.0 `ProtoAdapter.kt` routes `decode(bytes: ByteArray)`
  through `ProtoReader32(bytes)` (`commonDecode`, lines 354-357 of the common source);
  the port's `ProtoAdapter.java` `decode(byte[])` built `new ProtoReader(new
  Buffer().write(bytes))` instead. The generated adapters (byte-identical trees)
  override only `decode(ProtoReader)`, so the reader the `byte[]` entry point
  constructs was the one API-level difference on this path: upstream reads varints
  directly from the input array, the port copied the input into okio buffer segments
  first. The fix (commit `c0a2152`) makes both byte-array entry points construct
  `ByteArrayProtoReader32`, matching upstream's `commonDecode`, and decode outputs are
  unchanged (`perf.IdentityCheck` fingerprints identical,
  `results/identity-port-rerun.txt` diff-clean against the initial run's). All four
  decode cells improved, which confirms the mechanism by intervention; the residual
  ordering (bytes-heavy 0.810 vs packed 1.221) shows the array-backed reader removes the
  copy penalty but leaves a bytes-heavy deficit of roughly 20%. With the entry points
  now identical, whatever remains sits downstream of them; the port's array-backed
  reader implementation is the remaining port-specific code on this path, but a profiler
  has not yet attributed it (read this as location-by-elimination, not a diagnosis).
- `EmailSearchBench.encodeForward` (0.867 initial, 0.790 re-measured): mechanism not
  identified in this task. Allocation is identical (16 B/op); the reverse-writer path on
  the same message is at parity (1.007), so the cost sits in the forward-writer path
  specifically. Unverified hypothesis, offered as a lead only: tag/varint write loops in
  the ported `ProtoWriter`.

Improvements found (no acceptance required, reported for the record): all three schema
operations faster (1.084x-1.160x, descriptor with 22% less allocation), `all_types`
reverse encode 1.181x with less allocation, packed decode 1.221x after the fix.

## 7. protobuf-java reference status (workload 3)

Reference only: the same messages, byte-identical encodings (section 3), measured to give
a wire-format baseline and a same-machine control. Its port/upstream ratios (0.981-1.013
across all 8 cells) confirm the environment stayed stable while the wire decode ratios
above were measured; protobuf-java numbers must not be read as a target for either wire
implementation (different codegen, different algorithms). These cells were not re-run at
13:36 (the decode fix cannot reach protobuf-java code); they are the initial session's.

## 8. Provenance

Raw material: `/tmp/aw-perf/results/` (12 JMH JSON files + text logs from the initial
session, plus the re-measurement's `rt2-{upstream,port}-{1,2}.{json,txt}` and
`analysis-rerun.json`; identity fingerprints including `identity-port-rerun.txt`, oracle
checksums, `analysis.txt`, `analysis.json`), `/tmp/aw-perf/logs/`. The band rule and both
criteria live in `/tmp/aw-perf/tools/analyze.py`; that script was written before the
first port result existed (file mtime 12:09:24, first port result file 12:26:29) and was
not modified afterwards. The re-measurement lives in `/tmp/aw-perf/run-decode-rerun.sh`
and `/tmp/aw-perf/tools/analyze_rerun.py`, which consumes only the bands the initial
session derived.

This document is a measurement record; it grants no acceptance. TASK-21 is blocked on
finding 3, the remaining PENDING row, per AC#3.
