# Building antiwire (module shells)

The repository currently contains the TASK-2 build skeleton only: empty module shells that
reserve names. Nothing built here is evidence of Wire parity or compatibility. The research
and plan are in `docs/`, the decision record in [docs/decisions.md](docs/decisions.md).

## Toolchain

- JDK 17 or later to build (Maven enforcer rejects older). Production compilation targets
  Java 11 via `--release 11` (DEC-3).
- Maven 3.6.3 or later. No wrapper is committed; the version floor is enforced instead.
- A JDK 11 for the consumer smoke is optional locally (see below) and always provisioned in CI.

## Known invocation constraint: `mvn test` with the shaded fixture in the reactor

`mvn test -pl <module> -am` fails in `wire-runtime-java` with `TestEngine with ID
'junit-vintage' failed to discover tests` (root cause: `NoClassDefFoundError` on a relocated
`io.github.paoloantinori.antiwire.parity.*` class). The shaded fixture's relocation output is
produced by the shade plugin in the `package` phase, so a `test`-scoped reactor run resolves
the `wire-upstream-shaded` dependency to its empty `target/classes` instead of a jar, and the
JUnit 4 parity tests cannot link. This predates the generator work (surefire dumps from
2026-10-02 00:07 onward) and does not affect the entry point: `mvn verify` and
`scripts/verify.sh` run the `package` phase and are green. Until the fixture owns a fix (for
example a test-phase-aware jar or surefire exclusion), use `mvn verify`, not `mvn test`, for
any reactor-scoped run.

## Verification entry point

`scripts/verify.sh` is the single entry point and the only command CI's build job runs. It:

1. runs `mvn verify` (build, enforcer dependency policy, module smoke tests, classpath exports);
2. runs `scripts/check-classpath.sh`: per module, reports where classes under the retained
   prefixes load from and fails if one class name resolves from two artifacts. The retained
   prefixes live in `config/retained-prefixes.txt` (one per line), the single source for the
   check and the provisional OPEN-1-era list from the compatibility matrix; TASK-4 updates
   it if namespaces move. The check fails closed when that file is missing or empty;
3. runs `scripts/check-java11-bytecode.sh`: module jars and production dependency jars must
   expose only class files Java 11 can select (major version 55 or lower, multi-release aware);
4. runs `scripts/consumer-check-java11.sh`: compiles and runs the named placeholder consumer
   on an actual Java 11 JVM against the module jars.

The suite registry it prints comes from `config/verify-suites.json`. Each suite carries a
declared status, `ACTIVE` or `PENDING`; only the owning task flips one. PENDING suites are
never run and never printed as passed. The entry point records one result per suite:

- `PASS`: the suite printed `result=PASS` and exited `EXIT_OK`;
- `FAIL`: the suite ran and failed, or printed `PASS` while exiting nonzero (the exit code
  is part of the recorded note);
- `MISSING`: the suite could not produce a verdict; recorded with the suite script's exit
  code (a `NOT_RUN` reported by the suite script lands here), and synthesized by the entry
  point when a suite records no result at all;
- `NOT_RUN`: the status a suite script prints when it cannot run, for example when no Java
  11 toolchain exists locally.

Any ACTIVE suite whose result is not `PASS` fails the whole run. A green `verify.sh` means
exactly: every ACTIVE suite recorded `PASS`, nothing more.

Exit codes are defined once in `scripts/lib.sh`: `EXIT_OK=0` (a check ran and passed),
`EXIT_FAIL=1` (a check ran and failed, or a precondition is broken), `EXIT_NOT_RUN=3` (a
suite could not run at all). Every script sources `lib.sh`, which fails loudly at startup
when the module list there and the parent `pom.xml` `<modules>` block disagree.

`scripts/verify.sh` requires `python3` on PATH: the final registry reconciliation is an
embedded Python script.

The Java 11 toolchain for step 4 is found via `$JAVA11_HOME`, `$JAVA_HOME_11_X64` (set by
CI), `$JAVA_HOME` when itself 11, or sdkman and common system locations. Without one
locally, the suite reports `NOT_RUN` and the run fails. CI has a single job: Maven runs on
Temurin 17 while Temurin 11 is provisioned as `JAVA_HOME_11_X64`, and step 4 executes on
that real JDK 11 in the same job; there is no second consumer job and no artifact
upload or download.

## Coordinates

The groupId `io.github.paoloantinori` is a provisional placeholder. The intended groupId is
`io.apicurio`, not authorized yet (DEC-8); deployment is disabled (`maven.deploy.skip`) and
these coordinates must never be published.

## Kotlin policy

Kotlin is wired for test compilation only (`src/test/kotlin`, test-scoped stdlib, no
production execution of the Kotlin plugin). Maven enforcer bans `org.jetbrains.kotlin:*`,
`org.jetbrains.kotlinx:*` and `com.squareup.okio:*` from production scope, including
transitives (DEC-4).
