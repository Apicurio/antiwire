# Footprint and dependency before/after report (TASK-19)

Measured on 2026-10-02. Every number below comes from a command run for this report; the
commands are recorded inline so a reviewer can reproduce them. Units discipline (AC#2):
every size labeled "bytes" is a decimal byte count exactly as reported by `stat -f %z`;
every size labeled "MiB" is bytes divided by 1,048,576, rounded to three decimals. The two
are never mixed in one figure.

## 0. Measured candidate identity (AC#4)

| Item | Value |
|---|---|
| antiwire build revision | `63e98b70e235ba0b92119272c7a6c6f9411141d8` (branch `m0-spikes`) |
| Build tool | Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1) |
| Build JDK | OpenJDK 17.0.12, Temurin-17.0.12+7 (`~/.sdkman/candidates/java/17.0.12-tem`) |
| OS | macOS 26.7.1, aarch64 |
| Checksum tool | `shasum -a 256` (SHA-256) |
| Upstream 7.1.0 pin | tag 7.1.0 at `9f62097dfe4995b5709d001ca0187e30ca0530ef` (clone `/tmp/wire`); upstream artifacts were resolved from Maven Central, not built from this source |
| Upstream 6.4.0 pin | tag 6.4.0 at `d980880b830b6f84c28e81eb98c74a17fe7dfd0e` (clone `/tmp/wire-6.4.0`); upstream artifacts resolved from Maven Central |
| Apicurio base | `448f845c9791b960cec3f0bb9a491ac5cd90e785` (tip of `main` at measurement time) |
| Apicurio integration | `a11d7cfaf8f97546689d12e9e9b9768fa0dd6a09` (branch `antiwire-integration`) |

Measured antiwire artifacts, built fresh from the revision above by
`mvn -pl wire-java-generator -am clean install -DskipTests` (BUILD SUCCESS; the reactor
also builds the never-published `wire-upstream-shaded` test fixture):

| Artifact (0.1.0-SNAPSHOT, groupId `io.github.paoloantinori`) | Bytes | MiB | SHA-256 |
|---|---|---|---|
| `wire-runtime-java` | 243,806 | 0.233 | `d41c2440a48ed6a464bfc70a97ac97487cafeaf70a2c88c1eb23c499577a3e1c` |
| `wire-schema-java` | 325,076 | 0.310 | `4d8a7cd1b9a32d279139eb1ab120f6cd6dee1e3c39fe64e0987df1fc3ebf51f5` |
| `wire-java-generator` | 56,302 | 0.054 | `6b9522e5dc88f896ab3ca5d5c90bb009e031cb70f8adee70703dfd5ff7f0e298` |

Resolved dependency identities of the measured candidate (from the clean-consumer trees in
sections 2 and 3): `wire-runtime-java` resolves to itself and nothing else; `wire-schema-java`
resolves to itself plus `wire-runtime-java`; `wire-java-generator` resolves to itself plus
`wire-schema-java`, `wire-runtime-java`, and `com.squareup:javapoet:1.13.0`.

Jar reproducibility note: the jars carry build-time ZIP entry timestamps
(`unzip -l` shows 10-02-2026 11:27), so a clean rebuild at a later time produces
byte-different jars even from an unchanged tree. A same-tree rebuild without recompilation
reproduced all three checksums byte-for-byte during the `verify.sh` run in section 7. The
acceptance therefore binds to the recorded checksums plus the revision, not to "any jar
built from this revision".

Invalidation rule (restated from AC#4): code, dependency, or packaging changes invalidate
the affected evidence and require renewed measurements and renewed acceptance before
TASK-21. Concretely, any commit that changes the content of `wire-runtime-java`,
`wire-schema-java`, or `wire-java-generator` (source, pom, or plugin configuration)
invalidates the corresponding rows in sections 2, 3, and 5 and the checksum block above.

## 1. Method

### 1.1 Clean minimal consumer probes

Each probe is a scratch Maven project in `/tmp/fp-consumers/<probe>` with exactly one
declared dependency at runtime scope. Template `pom.xml` (probe `rt-640` shown; other
probes differ only in groupId, artifactId, version):

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>fp.probe</groupId>
  <artifactId>rt-640</artifactId>
  <version>1.0</version>
  <packaging>pom</packaging>
  <dependencies>
    <dependency>
      <groupId>com.squareup.wire</groupId>
      <artifactId>wire-runtime-jvm</artifactId>
      <version>6.4.0</version>
      <scope>runtime</scope>
    </dependency>
  </dependencies>
</project>
```

Commands run in each probe directory (Maven 3.9.12 on Temurin 17.0.12; the CLI plugin
version resolved was `maven-dependency-plugin:3.7.0`):

```bash
mvn -q dependency:tree -DoutputFile=tree.txt
mvn -q dependency:list -DincludeScope=runtime -DoutputFile=list.txt
mvn -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/deps
stat -f '%z %N' target/deps/*.jar        # per-jar decimal bytes
```

Jar counts and totals come from `stat -f '%z %N' target/deps/*.jar` summed over the
directory; nothing is estimated. All commands exited 0.

### 1.2 Re-download cross-check

One measured jar was re-downloaded into a completely fresh local repository and compared
against the copy the probe measured:

```bash
mvn -q org.apache.maven.plugins:maven-dependency-plugin:3.7.0:get \
    -Dartifact=com.squareup.wire:wire-runtime-jvm:6.4.0:jar -Dtransitive=false \
    -Dmaven.repo.local=/tmp/fp-redownload-m2
shasum -a 256 /tmp/fp-redownload-m2/com/squareup/wire/wire-runtime-jvm/6.4.0/wire-runtime-jvm-6.4.0.jar \
              /tmp/fp-consumers/rt-640/target/deps/wire-runtime-jvm-6.4.0.jar
```

Both files: SHA-256 `8c0a450299e546f896ce9b40d6f1b83850d9e6b219664301b5a441b548d4ab84`,
262,388 bytes (0.250 MiB). The measured copy and the independent download are identical.

## 2. Clean minimal consumer: runtime surface

What a fresh consumer resolves when it declares exactly one runtime dependency on the
runtime artifact of each baseline. "Resolved graph" is `dependency:tree` output; "bytes"
is the sum of the copied jars.

### 2.1 Resolved graphs

Upstream `com.squareup.wire:wire-runtime-jvm:6.4.0` (probe `rt-640`):

```
fp.probe:rt-640:pom:1.0
\- com.squareup.wire:wire-runtime-jvm:jar:6.4.0:runtime
   +- com.squareup.okio:okio-jvm:jar:3.17.0:runtime
   \- org.jetbrains.kotlin:kotlin-stdlib:jar:2.0.21:runtime
      \- org.jetbrains:annotations:jar:13.0:runtime
```

Upstream `com.squareup.wire:wire-runtime-jvm:7.1.0` (probe `rt-710`):

```
fp.probe:rt-710:pom:1.0
\- com.squareup.wire:wire-runtime-jvm:jar:7.1.0:runtime
   +- com.squareup.okio:okio-jvm:jar:3.18.2:runtime
   \- org.jetbrains.kotlin:kotlin-stdlib:jar:2.0.21:runtime
      \- org.jetbrains:annotations:jar:13.0:runtime
```

antiwire `io.github.paoloantinori:wire-runtime-java:0.1.0-SNAPSHOT` (probe `rt-aw`):

```
fp.probe:rt-aw:pom:1.0
\- io.github.paoloantinori:wire-runtime-java:jar:0.1.0-SNAPSHOT:runtime
```

### 2.2 Resolved artifacts with versions and sizes

| Baseline | Resolved artifact (version) | Bytes |
|---|---|---|
| upstream 6.4.0 | `com.squareup.wire:wire-runtime-jvm` (6.4.0) | 262,388 |
| upstream 6.4.0 | `com.squareup.okio:okio-jvm` (3.17.0) | 381,985 |
| upstream 6.4.0 | `org.jetbrains.kotlin:kotlin-stdlib` (2.0.21) | 1,747,660 |
| upstream 6.4.0 | `org.jetbrains:annotations` (13.0) | 17,536 |
| upstream 7.1.0 | `com.squareup.wire:wire-runtime-jvm` (7.1.0) | 283,191 |
| upstream 7.1.0 | `com.squareup.okio:okio-jvm` (3.18.2) | 392,462 |
| upstream 7.1.0 | `org.jetbrains.kotlin:kotlin-stdlib` (2.0.21) | 1,747,660 |
| upstream 7.1.0 | `org.jetbrains:annotations` (13.0) | 17,536 |
| antiwire | `io.github.paoloantinori:wire-runtime-java` (0.1.0-SNAPSHOT) | 243,806 |

### 2.3 Totals

| Baseline | Jars | Bytes | MiB | Change vs antiwire |
|---|---|---|---|---|
| upstream wire-runtime-jvm 6.4.0 | 4 | 2,409,569 | 2.298 | antiwire removes 2,165,763 bytes (89.9%) |
| upstream wire-runtime-jvm 7.1.0 | 4 | 2,440,849 | 2.328 | antiwire removes 2,197,043 bytes (90.0%) |
| antiwire wire-runtime-java | 1 | 243,806 | 0.233 | baseline for the two rows above |

What disappears in the antiwire column is exactly the Kotlin stack a clean consumer
previously shipped: `kotlin-stdlib` 1,747,660 bytes, `okio-jvm` 381,985 to 392,462 bytes,
and `org.jetbrains:annotations` 17,536 bytes.

## 3. Clean minimal consumer: schema surface

Same method, one runtime dependency on the schema artifact of each baseline.

### 3.1 Resolved graphs

Upstream `com.squareup.wire:wire-schema-jvm:6.4.0` (probe `sch-640`):

```
fp.probe:sch-640:pom:1.0
\- com.squareup.wire:wire-schema-jvm:jar:6.4.0:runtime
   +- com.squareup.okio:okio-jvm:jar:3.17.0:runtime
   +- com.google.guava:guava:jar:32.1.3-jre:runtime
   |  +- com.google.guava:failureaccess:jar:1.0.1:runtime
   |  +- com.google.guava:listenablefuture:jar:9999.0-empty-to-avoid-conflict-with-guava:runtime
   |  +- com.google.code.findbugs:jsr305:jar:3.0.2:runtime
   |  +- org.checkerframework:checker-qual:jar:3.37.0:runtime
   |  +- com.google.errorprone:error_prone_annotations:jar:2.21.1:runtime
   |  \- com.google.j2objc:j2objc-annotations:jar:2.8:runtime
   +- com.palantir.javapoet:javapoet:jar:0.12.0:runtime
   +- com.squareup:kotlinpoet-jvm:jar:2.2.0:runtime
   |  \- org.jetbrains.kotlin:kotlin-reflect:jar:2.1.21:runtime
   +- com.squareup.wire:wire-runtime-jvm:jar:6.4.0:runtime
   \- org.jetbrains.kotlin:kotlin-stdlib:jar:2.0.21:runtime
      \- org.jetbrains:annotations:jar:13.0:runtime
```

Upstream `com.squareup.wire:wire-schema-jvm:7.1.0` (probe `sch-710`):

```
fp.probe:sch-710:pom:1.0
\- com.squareup.wire:wire-schema-jvm:jar:7.1.0:runtime
   +- com.squareup.okio:okio-jvm:jar:3.18.2:runtime
   +- com.google.guava:guava:jar:33.7.1-jre:runtime
   |  +- com.google.guava:failureaccess:jar:1.0.3:runtime
   |  +- com.google.guava:listenablefuture:jar:9999.0-empty-to-avoid-conflict-with-guava:runtime
   |  +- org.jspecify:jspecify:jar:1.0.1:runtime
   |  +- com.google.errorprone:error_prone_annotations:jar:2.50.0:runtime
   |  \- com.google.j2objc:j2objc-annotations:jar:3.1:runtime
   +- com.palantir.javapoet:javapoet:jar:0.19.0:runtime
   +- com.squareup:kotlinpoet-jvm:jar:2.3.0:runtime
   |  \- org.jetbrains.kotlin:kotlin-reflect:jar:2.3.20:runtime
   +- com.squareup.wire:wire-runtime-jvm:jar:7.1.0:runtime
   \- org.jetbrains.kotlin:kotlin-stdlib:jar:2.0.21:runtime
      \- org.jetbrains:annotations:jar:13.0:runtime
```

antiwire `io.github.paoloantinori:wire-schema-java:0.1.0-SNAPSHOT` (probe `sch-aw`):

```
fp.probe:sch-aw:pom:1.0
\- io.github.paoloantinori:wire-schema-java:jar:0.1.0-SNAPSHOT:runtime
   \- io.github.paoloantinori:wire-runtime-java:jar:0.1.0-SNAPSHOT:runtime
```

The two upstream baselines pull, transitively and unconditionally: `kotlin-stdlib`,
`okio-jvm`, `kotlin-reflect` (via `kotlinpoet-jvm`), `guava` with five to six of its own
transitives, the Palantir `javapoet` fork, and `kotlinpoet-jvm`. The antiwire column pulls
`wire-runtime-java` alone.

### 3.2 Resolved artifacts with versions and sizes

| Baseline | Resolved artifact (version) | Bytes |
|---|---|---|
| upstream 6.4.0 | `com.squareup.wire:wire-schema-jvm` (6.4.0) | 502,581 |
| upstream 6.4.0 | `com.squareup.wire:wire-runtime-jvm` (6.4.0) | 262,388 |
| upstream 6.4.0 | `org.jetbrains.kotlin:kotlin-stdlib` (2.0.21) | 1,747,660 |
| upstream 6.4.0 | `org.jetbrains.kotlin:kotlin-reflect` (2.1.21) | 3,081,305 |
| upstream 6.4.0 | `com.squareup.okio:okio-jvm` (3.17.0) | 381,985 |
| upstream 6.4.0 | `com.google.guava:guava` (32.1.3-jre) | 3,043,932 |
| upstream 6.4.0 | `com.google.guava:failureaccess` (1.0.1) | 4,617 |
| upstream 6.4.0 | `com.google.guava:listenablefuture` (9999.0-empty-to-avoid-conflict-with-guava) | 2,199 |
| upstream 6.4.0 | `com.google.code.findbugs:jsr305` (3.0.2) | 19,936 |
| upstream 6.4.0 | `org.checkerframework:checker-qual` (3.37.0) | 224,460 |
| upstream 6.4.0 | `com.google.errorprone:error_prone_annotations` (2.21.1) | 16,830 |
| upstream 6.4.0 | `com.google.j2objc:j2objc-annotations` (2.8) | 9,301 |
| upstream 6.4.0 | `com.palantir.javapoet:javapoet` (0.12.0) | 107,896 |
| upstream 6.4.0 | `com.squareup:kotlinpoet-jvm` (2.2.0) | 357,262 |
| upstream 6.4.0 | `org.jetbrains:annotations` (13.0) | 17,536 |
| upstream 7.1.0 | `com.squareup.wire:wire-schema-jvm` (7.1.0) | 514,450 |
| upstream 7.1.0 | `com.squareup.wire:wire-runtime-jvm` (7.1.0) | 283,191 |
| upstream 7.1.0 | `org.jetbrains.kotlin:kotlin-stdlib` (2.0.21) | 1,747,660 |
| upstream 7.1.0 | `org.jetbrains.kotlin:kotlin-reflect` (2.3.20) | 3,631,428 |
| upstream 7.1.0 | `com.squareup.okio:okio-jvm` (3.18.2) | 392,462 |
| upstream 7.1.0 | `com.google.guava:guava` (33.7.1-jre) | 3,057,607 |
| upstream 7.1.0 | `com.google.guava:failureaccess` (1.0.3) | 10,763 |
| upstream 7.1.0 | `com.google.guava:listenablefuture` (9999.0-empty-to-avoid-conflict-with-guava) | 2,199 |
| upstream 7.1.0 | `org.jspecify:jspecify` (1.0.1) | 3,064 |
| upstream 7.1.0 | `com.google.errorprone:error_prone_annotations` (2.50.0) | 20,252 |
| upstream 7.1.0 | `com.google.j2objc:j2objc-annotations` (3.1) | 16,762 |
| upstream 7.1.0 | `com.palantir.javapoet:javapoet` (0.19.0) | 108,001 |
| upstream 7.1.0 | `com.squareup:kotlinpoet-jvm` (2.3.0) | 361,440 |
| upstream 7.1.0 | `org.jetbrains:annotations` (13.0) | 17,536 |
| antiwire | `io.github.paoloantinori:wire-schema-java` (0.1.0-SNAPSHOT) | 325,076 |
| antiwire | `io.github.paoloantinori:wire-runtime-java` (0.1.0-SNAPSHOT) | 243,806 |

### 3.3 Totals

| Baseline | Jars | Bytes | MiB | Change vs antiwire |
|---|---|---|---|---|
| upstream wire-schema-jvm 6.4.0 | 15 | 9,779,888 | 9.327 | antiwire removes 9,211,006 bytes (94.2%) |
| upstream wire-schema-jvm 7.1.0 | 14 | 10,166,815 | 9.696 | antiwire removes 9,597,933 bytes (94.4%) |
| antiwire wire-schema-java | 2 | 568,882 | 0.543 | baseline for the two rows above |

## 4. Apicurio consumer: marginal delta

Clean-consumer numbers answer "what does a fresh consumer ship". They overstate the change
for Apicurio, which already resolves guava, kotlin, and okio from other parts of its graph.
This section reports the marginal delta on the two modules the recorded integration branch
migrates, measured on the Apicurio clone at `/tmp/apicurio`:

- base: `main` at `448f845c9791b960cec3f0bb9a491ac5cd90e785` (uses
  `com.squareup.wire:wire-schema` plus `wire-schema-jvm`, both 6.4.0, pinned by
  `<wire-schema.version>6.4.0</wire-schema.version>`)
- integration: branch `antiwire-integration` at
  `a11d7cfaf8f97546689d12e9e9b9768fa0dd6a09` (uses
  `io.github.paoloantinori:wire-schema-java` 0.1.0-SNAPSHOT)

Command run at each commit (Maven 3.9.12, Temurin 17.0.12, plugin
`maven-dependency-plugin:3.8.1` as bound by Apicurio's build; both runs ended BUILD
SUCCESS):

```bash
git checkout --detach 448f845c    # then, after measuring, git checkout antiwire-integration
mvn -pl utils/protobuf-schema-utilities,schema-util/protobuf -am dependency:tree
```

`dependency:list` cannot be used on this reactor: `apicurio-registry-common` declares a
`provided` in-reactor dependency that is not installed in the local repository, and the
list goal then tries to download it and fails. The tree is the resolved graph, so the
module dependency sets below were parsed from the tree output with test and provided
subtrees pruned (a node with scope test or provided removes its whole subtree from the
runtime-classpath set). This is the standard runtime-classpath semantics `dependency:list
-DincludeScope=runtime` would produce.

### 4.1 What each module resolves for wire-related artifacts

`utils/protobuf-schema-utilities` at base resolves, for the wire path: `wire-schema`
6.4.0 (the multiplatform root, which drags `wire-runtime` 6.4.0 and `okio` 3.18.2 at
runtime scope plus `kotlin-stdlib` 2.4.20), `wire-schema-jvm` 6.4.0 (which drags `guava`
33.5.0-jre, Palantir `javapoet` 0.12.0, `kotlinpoet-jvm` 2.2.0 with `kotlin-reflect`
2.3.10, and `wire-runtime-jvm` 6.4.0), plus Apicurio's own direct `okio-jvm` and
`okio-fakefilesystem` 3.18.2 (which drags `okio-fakefilesystem-jvm` and
`kotlinx-datetime-jvm` 0.8.0-0.6.x-compat). At integration the same module resolves
`wire-schema-java` 0.1.0-SNAPSHOT with its single transitive `wire-runtime-java`
0.1.0-SNAPSHOT, plus the now-explicit `guava` 32.1.3-jre; nothing Kotlin, nothing okio.

`schema-util/protobuf` (which consumes the utilities module transitively) shows the same
wire path inside the utilities subtree.

Base tree of `utils/protobuf-schema-utilities`, compile and runtime rows only (test rows
omitted here for brevity; they are in the raw log):

```
io.apicurio:apicurio-registry-protobuf-schema-utilities:jar:3.4.0-SNAPSHOT
+- com.google.protobuf:protobuf-java:jar:4.36.2:compile
+- com.google.api.grpc:proto-google-common-protos:jar:2.77.0:compile
+- com.squareup.wire:wire-schema:jar:6.4.0:compile
|  +- com.squareup.wire:wire-runtime:jar:6.4.0:runtime
|  |  \- com.squareup.okio:okio:jar:3.18.2:runtime
|  \- org.jetbrains.kotlin:kotlin-stdlib:jar:2.4.20:compile
|     \- org.jetbrains:annotations:jar:26.0.2:compile
+- com.squareup.wire:wire-schema-jvm:jar:6.4.0:compile
|  +- com.google.guava:guava:jar:33.5.0-jre:compile
|  |  +- com.google.guava:failureaccess:jar:1.0.1:compile
|  |  \- com.google.j2objc:j2objc-annotations:jar:2.8:compile
|  +- com.palantir.javapoet:javapoet:jar:0.12.0:compile
|  +- com.squareup:kotlinpoet-jvm:jar:2.2.0:compile
|  |  \- org.jetbrains.kotlin:kotlin-reflect:jar:2.3.10:runtime
|  \- com.squareup.wire:wire-runtime-jvm:jar:6.4.0:compile
+- com.ibm.icu:icu4j:jar:78.3:compile
+- com.squareup.okio:okio-jvm:jar:3.18.2:compile
+- com.squareup.okio:okio-fakefilesystem:jar:3.18.2:compile
|  \- com.squareup.okio:okio-fakefilesystem-jvm:jar:3.18.2:compile
|     \- org.jetbrains.kotlinx:kotlinx-datetime-jvm:jar:0.8.0-0.6.x-compat:compile
```

Integration tree of the same module, compile and runtime rows only:

```
io.apicurio:apicurio-registry-protobuf-schema-utilities:jar:3.4.0-SNAPSHOT
+- com.google.protobuf:protobuf-java:jar:4.36.2:compile
+- com.google.api.grpc:proto-google-common-protos:jar:2.77.0:compile
+- io.github.paoloantinori:wire-schema-java:jar:0.1.0-SNAPSHOT:compile
|  \- io.github.paoloantinori:wire-runtime-java:jar:0.1.0-SNAPSHOT:compile
+- com.google.guava:guava:jar:32.1.3-jre:compile
|  +- com.google.guava:failureaccess:jar:1.0.1:compile
|  +- com.google.guava:listenablefuture:jar:9999.0-empty-to-avoid-conflict-with-guava:compile
|  +- com.google.code.findbugs:jsr305:jar:3.0.2:compile
|  +- org.checkerframework:checker-qual:jar:3.53.1:compile
|  +- com.google.errorprone:error_prone_annotations:jar:2.47.0:compile
|  \- com.google.j2objc:j2objc-annotations:jar:2.8:compile
+- com.ibm.icu:icu4j:jar:78.3:compile
```

### 4.2 Marginal delta, `schema-util/protobuf` (compile and runtime resolution)

Resolved artifact count on the runtime classpath goes from 43 at base to 34 at integration.

Removed with the switch (13 jars):

| Artifact (version at base) | Bytes |
|---|---|
| `com.squareup.wire:wire-schema-jvm` (6.4.0) | 502,581 |
| `com.squareup.wire:wire-schema` (6.4.0, multiplatform root) | 72,907 |
| `com.squareup.wire:wire-runtime-jvm` (6.4.0) | 262,388 |
| `com.squareup.wire:wire-runtime` (6.4.0, multiplatform root) | 39,618 |
| `org.jetbrains.kotlin:kotlin-stdlib` (2.4.20) | 1,853,314 |
| `org.jetbrains.kotlin:kotlin-reflect` (2.3.10) | 3,467,844 |
| `com.squareup.okio:okio-jvm` (3.18.2) | 392,462 |
| `com.squareup.okio:okio` (3.18.2, multiplatform root) | 85,247 |
| `com.squareup.okio:okio-fakefilesystem` (3.18.2) | 6,387 |
| `com.squareup.okio:okio-fakefilesystem-jvm` (3.18.2) | 29,329 |
| `org.jetbrains.kotlinx:kotlinx-datetime-jvm` (0.8.0-0.6.x-compat) | 753,869 |
| `com.squareup:kotlinpoet-jvm` (2.2.0) | 357,262 |
| `com.palantir.javapoet:javapoet` (0.12.0) | 107,896 |
| Total removed | 7,931,104 bytes (7.564 MiB) |

Added with the switch (4 jars):

| Artifact (version at integration) | Bytes |
|---|---|
| `io.github.paoloantinori:wire-schema-java` (0.1.0-SNAPSHOT) | 325,076 |
| `io.github.paoloantinori:wire-runtime-java` (0.1.0-SNAPSHOT) | 243,806 |
| `org.checkerframework:checker-qual` (3.53.1, guava transitive restored) | 241,633 |
| `com.google.guava:listenablefuture` (9999.0-empty-to-avoid-conflict-with-guava) | 2,199 |
| Total added | 812,714 bytes (0.775 MiB) |

Version changes on artifacts present in both: `com.google.guava:guava` 33.5.0-jre
(3,017,283 bytes) becomes 32.1.3-jre (3,043,932 bytes), a 26,649-byte increase;
`org.jetbrains:annotations` 26.0.2 (31,170 bytes) becomes the explicitly declared 13.0
(17,536 bytes), a 13,634-byte decrease.

Net marginal for this module: minus 7,105,375 bytes (6.776 MiB) on the runtime classpath.

### 4.3 Marginal delta, `utils/protobuf-schema-utilities`

Resolved artifact count goes from 20 at base to 12 at integration. The removed set is the
same 13 jars as above plus `org.jetbrains:annotations` 26.0.2 (31,170 bytes), so
7,962,274 bytes (7.594 MiB) removed. The added set is the 4 jars above plus
`com.google.code.findbugs:jsr305` 3.0.2 (19,936 bytes) and
`com.google.errorprone:error_prone_annotations` 2.47.0 (20,254 bytes), both guava
transitives restored by the version change, so 852,904 bytes (0.813 MiB) added. With the
same guava version change of 26,649 bytes, the net marginal is minus 7,082,721 bytes
(6.754 MiB).

### 4.4 The explicit guava, precisely

Guava does not disappear from Apicurio; it moves from transitive to explicit. At base,
`wire-schema-jvm` 6.4.0 declares guava 32.1.3-jre, but Apicurio's imported
`io.quarkus:quarkus-bom:3.33.3.1` manages guava at 33.5.0-jre (verified in the downloaded
BOM pom) and also excludes most guava transitives, so the base modules resolved
33.5.0-jre with only `failureaccess` and `j2objc-annotations`. The integration branch adds
Apicurio's own `dependencyManagement` entry for guava at 32.1.3-jre, which wins over the
imported BOM and restores the transitives that the BOM's exclusions had suppressed
(`jsr305`, `listenablefuture`, `checker-qual` 3.53.1, `error_prone_annotations` 2.47.0;
those two versions are still constrained by Apicurio's effective dependency management,
which the integration branch's effective pom for the utilities module pins at 3.53.1 and
2.47.0 respectively, while guava's own pom declares 3.37.0 and 2.21.1).

One consequence worth the maintainer's attention: the integration pom's comment says the
32.1.3-jre pin is "exactly what the baseline resolved (wire-schema-jvm-6.4.0.pom)". That is
what the wire pom declares, but it is not what Apicurio's base effectively resolved, which
was 33.5.0-jre through the Quarkus BOM. The switch therefore also pins guava two versions
below what the BOM provided, for every module that resolves guava under this
`dependencyManagement`, not just the wire path. Whether that is acceptable is part of the
pending footprint acceptance; it is recorded here rather than adjusted, because changing
the pin is an Apicurio-side decision.

### 4.5 Scope of the marginal statement

These deltas are module-level: they describe what changes in the resolved graphs of
`utils/protobuf-schema-utilities` and `schema-util/protobuf`. The whole-application
runtime classpath of a full Registry deployment may still contain kotlin-stdlib, okio, or
guava from other dependency paths outside these two modules; the measurement above does
not claim their removal from the application, only from the wire path.

## 5. Optional generator cost

The generator is a separate optional artifact; no runtime or schema consumer pulls it
(OPEN-2, verified by the trees in sections 2 and 3).

antiwire `io.github.paoloantinori:wire-java-generator:0.1.0-SNAPSHOT` (probe `gen-aw`):

```
fp.probe:gen-aw:pom:1.0
\- io.github.paoloantinori:wire-java-generator:jar:0.1.0-SNAPSHOT:runtime
   +- io.github.paoloantinori:wire-schema-java:jar:0.1.0-SNAPSHOT:runtime
   +- io.github.paoloantinori:wire-runtime-java:jar:0.1.0-SNAPSHOT:runtime
   \- com.squareup:javapoet:jar:1.13.0:runtime
```

| Resolved artifact | Bytes |
|---|---|
| `io.github.paoloantinori:wire-java-generator` (0.1.0-SNAPSHOT) | 56,302 |
| `io.github.paoloantinori:wire-schema-java` (0.1.0-SNAPSHOT) | 325,076 |
| `io.github.paoloantinori:wire-runtime-java` (0.1.0-SNAPSHOT) | 243,806 |
| `com.squareup:javapoet` (1.13.0) | 106,068 |
| Total | 731,252 bytes (0.697 MiB), 4 jars |

For context, the upstream generator baseline was measured the same way. Note the upstream
coordinate: `com.squareup.wire:wire-java-generator-jvm:7.1.0` has no pom on Maven Central
(a probe against it resolves with "The POM ... is missing, no dependency information
available" and an empty-looking tree, which is a trap, not a small footprint); the real
coordinate is `com.squareup.wire:wire-java-generator:7.1.0`. That baseline resolves 15
jars totaling 10,208,654 bytes (9.736 MiB), including the full wire-schema graph of
section 3 plus Palantir `javapoet` 0.19.0, `kotlin-stdlib`, and `okio-jvm`. The antiwire
generator removes 9,477,402 bytes (92.8%) against that baseline, and its only third-party
byte cost beyond the port itself is `com.squareup:javapoet` 1.13.0 at 106,068 bytes
(0.101 MiB).

Why Square javapoet 1.13.0 and not upstream's Palantir 0.19.0 pin (DEC-3): the Palantir
fork ships Java 17 class files, which the Java 11 floor forbids for production
dependencies, while Square javapoet 1.13.0 is pure Java on Java 8+. This was re-measured
on the artifacts this report resolved: every class in Palantir javapoet 0.19.0 and in
0.12.0 has class-file major version 61 (35 classes each), and every class in Square
javapoet 1.13.0 has major version 52 (43 classes). The port's
`WireCompiler` output for the `wire-golden-files` `all_types_proto3.proto` golden is
byte-identical with this artifact. The `bytecode-java11` suite in section 7 confirms the
measured candidate's production dependency bytecode: across module jars and production
dependency jars it checked 323 classes and saw only major versions 52 (43 classes, the
javapoet classes) and 55 (280 classes, the port's own Java 11 bytecode).

## 6. Dependency justification (AC#3)

Every production dependency each shipped module retains, one row each. Test-scoped
tooling (junit-jupiter, kotlin-stdlib as test compilation, assertk, junit 4 vintage,
protobuf-java in schema tests, protobuf-java-util in the protoc module's tests) is not
transitive and not part of any footprint above.

| Module | Production dependency | Footprint cost | Why it earns its place |
|---|---|---|---|
| `wire-runtime-java` | none | 0 bytes | Zero production dependencies: the okio surface the runtime needs (ByteString, Buffer, Source and Sink, FileSystem, Path, ZIP loading) is vendored as port source inside this jar, under the upstream `okio` package names for namespace continuity (OPEN-1 resolution; the jar contains `okio/` entries), so nothing Kotlin-backed rides the artifact and the duplicate-class rules of DEC-2 stay enforced. This is the clean-consumer result of section 2: one jar, 243,806 bytes (0.233 MiB), total. |
| `wire-schema-java` | `io.github.paoloantinori:wire-runtime-java` (in-family) | 243,806 bytes (0.233 MiB) | Mirrors upstream's own schema-on-runtime api edge (OPEN-2): schema types build on runtime types, and consumers of schema already need runtime. Independently consumable runtime stays intact. No third-party byte is added: the module's own jar plus runtime is the entire footprint (568,882 bytes, 0.543 MiB). |
| `wire-java-generator` (optional) | `com.squareup:javapoet` 1.13.0 | 106,068 bytes (0.101 MiB) | The only third-party production dependency in the port. Code generation needs a source-emission API; writing a third one (upstream uses Palantir javapoet and kotlinpoet) would add port surface with no footprint saving worth the risk. The Square artifact is the pure-Java upstream of the fork upstream pins, runs on Java 8+ (DEC-3 floor), reproduces the pinned golden byte-for-byte, and is Apache-2.0. 43 of its classes are the major-52 entries the bytecode suite verifies. |
| `wire-java-generator` (optional) | `wire-schema-java` and `wire-runtime-java` (in-family) | 568,882 bytes (0.543 MiB) | The generator reads schemas and emits runtime types; these are the port's own artifacts, already counted, and the module is optional so no runtime or schema consumer pulls them. |

Kotlin and Okio check result: no Kotlin or Kotlin-backed artifact appears in any production
scope of the measured candidate. Three independent pieces of evidence: the clean-consumer
trees in sections 2, 3, and 5 contain no `org.jetbrains.kotlin*` and no
`com.squareup.okio*` node for the antiwire coordinates; the parent pom's
`bannedDependencies` enforcer rule bans exactly those groups in compile, runtime, and
provided scopes with `searchTransitive=true` (wire-upstream-shaded, the test fixture that
does carry Kotlin classes, is scope-isolated and never published); and the `verify.sh`
`dependency-policy` suite, which runs the enforcer inside `mvn verify`, reports PASS with
the note "no Kotlin or Kotlin-backed artifact in production scope" (section 7).

## 7. Verification run

`./scripts/verify.sh` was run once on the measured revision after the measurements (build
JVM Temurin 17.0.12; consumer smoke on Temurin 11.0.24). Exit code 0. Registry output:

```
Suite registry from /Users/pantinor/data/repo/work/antiwire/config/verify-suites.json:
  build                  declared=ACTIVE  owner=TASK-2  result=PASS    mvn verify green on: openjdk version "17.0.12" 2024-07-16
  dependency-policy      declared=ACTIVE  owner=TASK-2  result=PASS    enforcer rules ran inside mvn verify: Maven and JDK 17+ floors, no Kotlin or Kotlin-backed artifact in production scope
  bytecode-java11        declared=ACTIVE  owner=TASK-2  result=PASS    all classes selectable by Java 11 in module jars and production dependency jars have major <= 55
  runtime-tests          declared=ACTIVE  owner=TASK-9  result=PASS    869 wire-runtime-java cases inside mvn verify, 4 skipped (Failures: 0, Errors: 0)
  schema-tests           declared=ACTIVE  owner=TASK-13 result=PASS    630 wire-schema-java cases inside mvn verify, 6 skipped (Failures: 0, Errors: 0)
  parity-coverage        declared=ACTIVE  owner=TASK-14 result=PASS    pins verified, 967 upstream cases reconciled against port artifacts
  protoc-oracle          declared=ACTIVE  owner=TASK-15 result=PASS    122 wire-protoc-compat-java cases inside mvn verify, 50 skipped (Failures: 0, Errors: 0)
  compiler-tests         declared=ACTIVE  owner=TASK-16 result=PASS    175 wire-java-generator cases inside mvn verify, 33 skipped (Failures: 0, Errors: 0)
  security-corpus        declared=ACTIVE  owner=TASK-17 result=PASS    SecurityCorpusTest classes green inside mvn verify (runtime, schema, generator); registry docs/security-regression-inventory.md
  java11-consumer        declared=ACTIVE  owner=TASK-2  result=PASS    consumer compiled and ran on openjdk version "11.0.24" 2024-07-16 against the module jars and exercised the real spike and loading surfaces (ProtoWriter deterministic bytes; FileSystem write/read/metadata/delete round trip)
  duplicate-class-check declared=ACTIVE  owner=TASK-2  result=PASS    no com.squareup.wire or okio class name resolves from two artifacts on any module test classpath; origins printed above

VERDICT: all 11 ACTIVE suites passed.
```

Nothing in the port changed during the report: the working tree diff at the time of
writing contains this document and the TASK-19 task file only, and the verify run was the
final build action.

## 8. Acceptance

| Item | Status |
|---|---|
| Clean-consumer measurements (sections 2, 3) | Measured and reproducible |
| Apicurio marginal measurements (section 4) | Measured and reproducible |
| Generator cost (section 5) | Measured and reproducible |
| Kotlin and Okio absence in production scope (section 6) | Verified, three-way evidence |
| **Acceptance of the measured footprint (AC#3, "the maintainer explicitly accepts the measured footprint before release")** | **PENDING maintainer signature** |

The acceptance row is deliberately unsigned. The measured candidate to accept is the one
identified in section 0 (revision, checksums, resolved identities), under the invalidation
rule stated there. The open point the maintainer should weigh with it is the guava pin
observation in section 4.4.

### Coordinate update 2026-10-02

The maintainer resolved DEC-8: coordinates are now `io.apicurio` (was `io.github.paoloantinori`). A coordinate switch changes no class or resource byte (diff -rq of unpacked old/new jars differs only in the embedded META-INF/maven groupId directory), but whole-jar SHA-256 values therefore change. Current checksums under io.apicurio: wire-schema-java ac9a36f2..., wire-runtime-java 129a25cb... (old values 7bc11249.../43c810fb... superseded). The measured artifact set, sizes, and dependency graph are unchanged; resolution re-verified on the Apicurio integration branch (dependency:tree shows io.apicurio nodes, zero stale coordinates, Oracle A still 20/20 byte-identical).

Partly superseded on 2026-10-03 by the renewal in section 9: the candidate the acceptance
should bind to is now the c713e9a build of section 9.1, and the guava observation of
section 4.4 no longer applies (section 9.5).

## 9. Candidate c713e9a (post no-okio API merge), measured 2026-10-03

The invalidation rule of section 0 fired twice since the 2026-10-02 report, so this section
renews the affected evidence; everything above stays as history (DEC-13). First, the no-okio
public API merge (commits 3416581 and 9f6d7b8, merge c713e9a) changed the content of all
three shipped jars: the runtime gains the wire-owned `com.squareup.wire.Bytes` type end to
end and the generator emits it. Second, the recorded Apicurio integration branch was rebuilt
guava-free: branch `antiwire-integration` at `6ce5582c40e0918b27acde0a1ec51ddb0731b548`
replaces the previously measured `a11d7cfaf8f97546689d12e9e9b9768fa0dd6a09`, so the marginal
tables of section 4 are renewed below against the same base and the guava observation of
section 4.4 is withdrawn (9.5). Upstream 6.4.0 and 7.1.0 resolve to the same Maven Central
artifacts as before, so their columns are cited unchanged from sections 2 and 3 and were not
re-measured; only the antiwire column and the Apicurio marginal were.

### 9.1 Measured candidate identity

| Item | Value |
|---|---|
| antiwire build revision | `c713e9ad4f5ca30775dbd3884b5f1d6563552b51` (branch `main`) |
| Build command | `mvn -B -ntp -pl wire-runtime-java,wire-schema-java,wire-java-generator -am clean install -DskipTests` (BUILD SUCCESS, 12.3 s, 2026-10-03T14:10:01+02:00) |
| Build tool | Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1) |
| Build JDK | OpenJDK 17.0.12, Temurin-17.0.12+7 (`~/.sdkman/candidates/java/17.0.12-tem`) |
| OS | macOS 26.7.1 (25G241), aarch64 |
| Checksum tool | `shasum -a 256` (SHA-256) |
| Coordinates | `io.apicurio`, version 0.1.0-SNAPSHOT (DEC-8) |
| Apicurio base | `448f845c9791b960cec3f0bb9a491ac5cd90e785` (unchanged from section 4) |
| Apicurio integration | `6ce5582c40e0918b27acde0a1ec51ddb0731b548` (branch `antiwire-integration`, rebuilt guava-free; local only, never push) |

All nine release artifacts (main, `-sources`, `-javadoc` of each shipped module), built fresh
by the command above; the sources and javadoc jars are attached at package by each module's
pom:

| Artifact (0.1.0-SNAPSHOT, `io.apicurio`) | Bytes | MiB | SHA-256 |
|---|---|---|---|
| `wire-runtime-java` | 248,894 | 0.237 | `e04db4de1e42d123657ac669670adf11ba85e0b1e76340c246966852f85b1e0c` |
| `wire-runtime-java` (`-sources`) | 163,477 | 0.156 | `0086637ff86225c72f3e4a089d3aeaafc133af825257e1a41b4df7f9a4ac3f6f` |
| `wire-runtime-java` (`-javadoc`) | 553,266 | 0.528 | `51702b2ac664a9677da9b671ebad70a3df9f88ed440e1a31268c90c92c430762` |
| `wire-schema-java` | 325,326 | 0.310 | `9d3ed5f2bce0a75d550702ce99b6008ab354b0d4e3ac5bd9bde05a21e409fa65` |
| `wire-schema-java` (`-sources`) | 207,887 | 0.198 | `3c8874f7ae0c9d40cca3f7cf183a684125a17c24537f3ea73399dbb2925578c4` |
| `wire-schema-java` (`-javadoc`) | 622,754 | 0.594 | `2bd2efb0d31366b5d550e5962cb46a702eaa4387402f1de3f2f5ed8ab17fc2b0` |
| `wire-java-generator` | 56,425 | 0.054 | `dbb2afd41dea44a420dc5919c8bed4286ac5eb853a31914f2f4f4eddec9310a5` |
| `wire-java-generator` (`-sources`) | 36,084 | 0.034 | `edc7594b534488b286cddd07f1668be66d3d39bfee5239c54273a0503f0a81ce` |
| `wire-java-generator` (`-javadoc`) | 123,233 | 0.118 | `d7c473c9b6927a69b85ba26d745a0437f51f778ce87fb1995fcfa5cfbf7839e0` |

Growth against the last footprint-measured jars of section 0 (bytes compare across the
coordinate switch because it changed no class or resource byte): `wire-runtime-java`
243,806 to 248,894 (+5,088, the new `com.squareup.wire.Bytes` surface), `wire-schema-java`
325,076 to 325,326 (+250), `wire-java-generator` 56,302 to 56,425 (+123). The
jar-reproducibility caveat of section 0 (build-time ZIP entry timestamps) applies unchanged:
acceptance binds to these checksums plus the revision. Resolved dependency identities are
unchanged apart from coordinates and were re-verified by the probe trees below:
`wire-runtime-java` resolves to itself and nothing else; `wire-schema-java` to itself plus
`wire-runtime-java`; `wire-java-generator` to itself plus both and
`com.squareup:javapoet:1.13.0`.

### 9.2 Clean minimal consumer: runtime surface (renewed antiwire column)

Probe `rt-aw` at `/tmp/fp2-consumers/rt-aw`, same pom template and commands as section 1.1,
run on 2026-10-03 with Maven 3.9.12 on Temurin 17.0.12 against the freshly installed jars of
9.1 (all commands exited 0):

```
fp.probe:rt-aw:pom:1.0
\- io.apicurio:wire-runtime-java:jar:0.1.0-SNAPSHOT:runtime
```

| Baseline | Jars | Bytes | MiB | Change vs antiwire |
|---|---|---|---|---|
| upstream wire-runtime-jvm 6.4.0 (cited from section 2.3) | 4 | 2,409,569 | 2.298 | antiwire removes 2,160,675 bytes (89.7%) |
| upstream wire-runtime-jvm 7.1.0 (cited from section 2.3) | 4 | 2,440,849 | 2.328 | antiwire removes 2,191,955 bytes (89.8%) |
| antiwire wire-runtime-java (this section) | 1 | 248,894 | 0.237 | baseline for the two rows above |

The removal share is a tenth of a point below section 2.3 (89.9% and 90.0%) only because the
antiwire jar itself grew; what disappears is still exactly the Kotlin stack of section 2.

### 9.3 Clean minimal consumer: schema surface (renewed antiwire column)

Probe `sch-aw` at `/tmp/fp2-consumers/sch-aw`, same method:

```
fp.probe:sch-aw:pom:1.0
\- io.apicurio:wire-schema-java:jar:0.1.0-SNAPSHOT:runtime
   \- io.apicurio:wire-runtime-java:jar:0.1.0-SNAPSHOT:runtime
```

| Baseline | Jars | Bytes | MiB | Change vs antiwire |
|---|---|---|---|---|
| upstream wire-schema-jvm 6.4.0 (cited from section 3.3) | 15 | 9,779,888 | 9.327 | antiwire removes 9,205,668 bytes (94.1%) |
| upstream wire-schema-jvm 7.1.0 (cited from section 3.3) | 14 | 10,166,815 | 9.696 | antiwire removes 9,592,595 bytes (94.4%) |
| antiwire wire-schema-java (this section) | 2 | 574,220 | 0.548 | baseline for the two rows above |

### 9.4 Optional generator cost (renewed)

Probe `gen-aw` at `/tmp/fp2-consumers/gen-aw`, same method:

```
fp.probe:gen-aw:pom:1.0
\- io.apicurio:wire-java-generator:jar:0.1.0-SNAPSHOT:runtime
   +- io.apicurio:wire-schema-java:jar:0.1.0-SNAPSHOT:runtime
   +- io.apicurio:wire-runtime-java:jar:0.1.0-SNAPSHOT:runtime
   \- com.squareup:javapoet:jar:1.13.0:runtime
```

| Resolved artifact | Bytes |
|---|---|
| `io.apicurio:wire-java-generator` (0.1.0-SNAPSHOT) | 56,425 |
| `io.apicurio:wire-schema-java` (0.1.0-SNAPSHOT) | 325,326 |
| `io.apicurio:wire-runtime-java` (0.1.0-SNAPSHOT) | 248,894 |
| `com.squareup:javapoet` (1.13.0) | 106,068 |
| Total | 736,713 bytes (0.703 MiB), 4 jars |

`javapoet` 1.13.0 is the same artifact as in section 5 (106,068 bytes), so the DEC-3
bytecode statement of section 5 carries over unchanged. Against the cited upstream
generator baseline of section 5 (15 jars, 10,208,654 bytes, 9.736 MiB) the antiwire
generator removes 9,471,941 bytes (92.8%), and its only third-party byte cost beyond the
port itself is still javapoet at 106,068 bytes (0.101 MiB).

### 9.5 Apicurio marginal delta on the rebuilt guava-free branch

Measured on the Apicurio clone at `/tmp/apicurio` on 2026-10-03. The base tree was re-run
for this renewal rather than cited, and it is identical to the one recorded in section 4.1;
both commands ended BUILD SUCCESS (Maven 3.9.12, Temurin 17.0.12, plugin
`maven-dependency-plugin:3.8.1` as bound by Apicurio's build). The test and provided
subtree pruning of section 4 applies. Marginal jar sizes were taken with
`stat -f '%z'` from the local repository copies of the exact resolved artifacts, which for
every base-side jar equal the sizes already recorded in section 4.

```bash
cd /tmp/apicurio
git checkout --detach 448f845c
mvn -B -ntp -pl utils/protobuf-schema-utilities,schema-util/protobuf -am dependency:tree
git checkout antiwire-integration    # head 6ce5582c
mvn -B -ntp -pl utils/protobuf-schema-utilities,schema-util/protobuf -am dependency:tree
```

Integration tree of `utils/protobuf-schema-utilities`, compile and runtime rows only (the
test subtree is omitted for brevity; it holds junit-jupiter and truth-proto-extension, the
latter still pulling guava at test scope):

```
io.apicurio:apicurio-registry-protobuf-schema-utilities:jar:3.4.0-SNAPSHOT
+- com.google.protobuf:protobuf-java:jar:4.36.2:compile
+- com.google.api.grpc:proto-google-common-protos:jar:2.77.0:compile
+- io.apicurio:wire-schema-java:jar:0.1.0-SNAPSHOT:compile
|  \- io.apicurio:wire-runtime-java:jar:0.1.0-SNAPSHOT:compile
+- com.ibm.icu:icu4j:jar:78.3:compile
```

`schema-util/protobuf` shows the same wire path inside the utilities subtree; its own
protobuf-java-util rows (`jsr305`, `gson`, `error_prone_annotations`, all runtime) are
unchanged from base.

Resolved artifact counts on the runtime classpath, pruning rule as in section 4:

| Module | Base 448f845c | Integration 6ce5582c | Old integration a11d7cfa |
|---|---|---|---|
| `utils/protobuf-schema-utilities` | 20 | 5 | 12 |
| `schema-util/protobuf` | 43 | 28 | 34 |

(The counts prune `org.jspecify:jspecify` at both ends of the second module: it sits inside
the junit-jupiter-api test subtree on both sides, whatever scope label the tree prints on
its edge.)

Removed with the switch; the set is identical for both modules (17 jars):

| Artifact (version at base) | Bytes |
|---|---|
| `com.squareup.wire:wire-schema-jvm` (6.4.0) | 502,581 |
| `com.squareup.wire:wire-schema` (6.4.0, multiplatform root) | 72,907 |
| `com.squareup.wire:wire-runtime-jvm` (6.4.0) | 262,388 |
| `com.squareup.wire:wire-runtime` (6.4.0, multiplatform root) | 39,618 |
| `org.jetbrains.kotlin:kotlin-stdlib` (2.4.20) | 1,853,314 |
| `org.jetbrains.kotlin:kotlin-reflect` (2.3.10) | 3,467,844 |
| `com.squareup.okio:okio-jvm` (3.18.2) | 392,462 |
| `com.squareup.okio:okio` (3.18.2, multiplatform root) | 85,247 |
| `com.squareup.okio:okio-fakefilesystem` (3.18.2) | 6,387 |
| `com.squareup.okio:okio-fakefilesystem-jvm` (3.18.2) | 29,329 |
| `org.jetbrains.kotlinx:kotlinx-datetime-jvm` (0.8.0-0.6.x-compat) | 753,869 |
| `com.squareup:kotlinpoet-jvm` (2.2.0) | 357,262 |
| `com.palantir.javapoet:javapoet` (0.12.0) | 107,896 |
| `org.jetbrains:annotations` (26.0.2) | 31,170 |
| `com.google.guava:guava` (33.5.0-jre) | 3,017,283 |
| `com.google.guava:failureaccess` (1.0.1) | 4,617 |
| `com.google.j2objc:j2objc-annotations` (2.8) | 9,301 |
| Total removed | 10,993,475 bytes (10.484 MiB) |

The first 13 rows are exactly the removed set section 4.2 recorded for the base wire path.
`org.jetbrains:annotations` 26.0.2 was already removed on the old branch for the utilities
module and re-pinned to 13.0 there for `schema-util/protobuf`; it is now removed outright
for both. The three guava-side rows (guava, `failureaccess`, `j2objc-annotations`) are new
to the removed set: the previous integration branch re-declared guava explicitly to keep
them resolving, and the rebuilt branch does not.

Added with the switch (2 jars):

| Artifact (version at integration) | Bytes |
|---|---|
| `io.apicurio:wire-schema-java` (0.1.0-SNAPSHOT) | 325,326 |
| `io.apicurio:wire-runtime-java` (0.1.0-SNAPSHOT) | 248,894 |
| Total added | 574,220 bytes (0.548 MiB) |

No version-change rows remain: guava and `org.jetbrains:annotations` are absent outright
rather than re-pinned, so the version deltas recorded in section 4.2 (guava 33.5.0-jre to
32.1.3-jre, +26,649 bytes; annotations 26.0.2 to 13.0, minus 13,634 bytes) have no
counterpart here. One scope shift, recorded for precision and not a delta:
`com.google.errorprone:error_prone_annotations` 2.47.0 under `protobuf-java-util` resolves
compile at base and runtime at integration, because at base guava 33.5.0-jre (which
declares it compile) provided a second, compile-scope path while `protobuf-java-util`
itself declares it runtime; it stays on the runtime classpath on both sides, so it enters
neither table.

Net marginal, identical for both modules: minus 10,419,255 bytes (9.937 MiB) on the runtime
classpath. On the a11d7cfa branch of section 4 the same statement was minus 7,105,375 bytes
(6.776 MiB) for `schema-util/protobuf` and minus 7,082,721 bytes (6.754 MiB) for the
utilities module; the guava-free rebuild removes a further 3,313,880 and 3,336,534 bytes
respectively.

Guava, Kotlin, Okio check on this branch, verified mechanically from the two trees rather
than taken from the migration notes: grepping the compile and runtime rows of both module
trees at 6ce5582c for `guava`, `kotlin`, and `okio` returns nothing. Zero nodes in compile
or runtime scope. The only guava nodes anywhere in either tree are test-scoped under
truth-proto-extension in the utilities module, which is not shipped. `grep -c guava pom.xml`
on the integration root pom is 0 and none of the migrated module poms mention guava, so
nothing re-pins it for other consumers of the tree.

Section 4.4 is therefore superseded and its concern withdrawn: the rebuilt branch adds no
dependencyManagement entry for guava and re-declares it nowhere, so the quarkus-bom
management of guava is untouched and there is no two-versions-below pin to weigh anymore.
The scope statement of section 4.5 still applies to these numbers: they are module-level
deltas for the two migrated modules measured here. The wider branch also migrates
schema-validation/protobuf, serdes/generic/serde-common-protobuf and app, whose trees this
report does not re-measure; the branch's own ANTIWIRE_MIGRATION.md records the guava-free
source audit for all migrated modules.

### 9.6 Acceptance (renewed)

| Item | Status |
|---|---|
| Clean-consumer measurements (9.2, 9.3) | Measured and reproducible |
| Apicurio marginal measurements (9.5, guava-free branch 6ce5582c) | Measured and reproducible |
| Generator cost (9.4) | Measured and reproducible |
| Kotlin and Okio absence in production scope (section 6 evidence, plus the trees of 9.5) | Verified, three-way evidence |
| **Acceptance of the measured footprint (AC#3, "the maintainer explicitly accepts the measured footprint before release")** | **PENDING maintainer signature** |

The row is again deliberately unsigned. The candidate to accept is the one identified in
9.1 (revision, nine checksums, resolved identities), with the marginal evidence of 9.5 on
the guava-free branch; the open point previously attached to acceptance (the guava pin of
section 4.4) is gone with the rebuild, so what remains for the maintainer to weigh is the
footprint itself. The invalidation rule of section 0 continues to bind.

### 9.7 Verification run

`./scripts/verify.sh` was run once on c713e9a after these measurements (build JVM Temurin
17.0.12; consumer smoke on Temurin 11.0.24). Exit code 0. The battery has grown one suite
since section 7 (upstream-watch, TASK-23), and the no-okio merge added runtime and compiler
cases. Registry output:

```
Suite registry from /Users/pantinor/data/repo/work/antiwire/config/verify-suites.json:
  build                  declared=ACTIVE  owner=TASK-2  result=PASS    mvn verify green on: openjdk version "17.0.12" 2024-07-16
  dependency-policy      declared=ACTIVE  owner=TASK-2  result=PASS    enforcer rules ran inside mvn verify: Maven and JDK 17+ floors, no Kotlin or Kotlin-backed artifact in production scope
  bytecode-java11        declared=ACTIVE  owner=TASK-2  result=PASS    all classes selectable by Java 11 in module jars and production dependency jars have major <= 55
  runtime-tests          declared=ACTIVE  owner=TASK-9  result=PASS    894 wire-runtime-java cases inside mvn verify, 4 skipped (Failures: 0, Errors: 0)
  schema-tests           declared=ACTIVE  owner=TASK-13 result=PASS    630 wire-schema-java cases inside mvn verify, 6 skipped (Failures: 0, Errors: 0)
  parity-coverage        declared=ACTIVE  owner=TASK-14 result=PASS    pins verified, 967 upstream cases reconciled against port artifacts
  protoc-oracle          declared=ACTIVE  owner=TASK-15 result=PASS    122 wire-protoc-compat-java cases inside mvn verify, 50 skipped (Failures: 0, Errors: 0)
  compiler-tests         declared=ACTIVE  owner=TASK-16 result=PASS    179 wire-java-generator cases inside mvn verify, 33 skipped (Failures: 0, Errors: 0)
  security-corpus        declared=ACTIVE  owner=TASK-17 result=PASS    SecurityCorpusTest classes green inside mvn verify (runtime, schema, generator); registry docs/security-regression-inventory.md
  java11-consumer        declared=ACTIVE  owner=TASK-2  result=PASS    consumer compiled and ran on openjdk version "11.0.24" 2024-07-16 against the module jars and exercised the real spike and loading surfaces (ProtoWriter deterministic bytes; FileSystem write/read/metadata/delete round trip)
  duplicate-class-check declared=ACTIVE  owner=TASK-2  result=PASS    no com.squareup.wire or okio class name resolves from two artifacts on any module test classpath; origins printed above
  upstream-watch         declared=ACTIVE  owner=TASK-23 result=PASS    state=CURRENT: pin 7.1.0 is the newest upstream tag (203 tags parsed); procedure docs/upstream-sync.md; this PASS records that the watch ran, not that the port is current

VERDICT: all 12 ACTIVE suites passed.
```

Reproducibility after the verify run, checked by re-checksumming the nine artifacts of 9.1:
the three main jars and the three `-sources` jars reproduced their checksums byte-for-byte;
the three `-javadoc` jars rebuilt byte-different at identical sizes, because javadoc
generation embeds generation timestamps. This is the section 0 caveat in its precise form:
the acceptance binds to the recorded checksums of the 9.1 build plus the revision, and the
javadoc jars are the one family a later rebuild will not reproduce bitwise. Nothing in the
port changed during the renewal: the working-tree diff at commit time contains this
document and the TASK-19 task file only, and the verify run was the final build action.
