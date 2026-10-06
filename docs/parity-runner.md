# Parity runner (TASK-14)

One reproducible command verifies the port against the pinned upstream: `./scripts/verify.sh`.
Its first step bootstraps the pinned upstream clone (before the build: the clone-dependent
tests initialize inside `mvn verify`), and its `parity-coverage` suite proves provenance and
reconciles the upstream test inventory against the port artifacts, blocking on lost cases
and unrecorded drift.

## Pins and provenance

`config/parity-pins.json` stores the upstream identity: repository, tag `7.1.0`, the
annotated tag object (`da24c33ee1fe772a7a04617018087f46f26d1708`), and the commit it must
resolve to (`9f62097dfe4995b5709d001ca0187e30ca0530ef`), plus the build-time fixture pins
(wire-compiler jars from Maven Central, protobuf-java test oracle 4.36.1).
`scripts/fetch-upstream.sh` retrieves the clone (default `/tmp/wire`, override with
`$ANTIWIRE_UPSTREAM` or the first argument), fetches the tag into an existing clone when
missing, and refuses to proceed when the resolved identity differs from the pins or when
the worktree carries local modifications: reconciliation reads the working tree, so a
modified clone is not the pinned sources. `scripts/verify.sh` runs this fetch as a
prerequisite before `mvn verify` (fail fast with an explicit message, no build), and
`scripts/test-bootstrap.sh` is the offline regression check covering absent, valid,
modified, wrong-pin and unreachable clone states with a stubbed mvn.

## Reconciliation

`scripts/check-parity-coverage.py` walks the in-scope upstream modules
(wire-runtime commonTest; wire-schema commonTest + jvmTest; wire-tests jvm-java-kotlin and
jvm-kotlin-proto-reader-32), extracts `@Test` method names from every upstream file and from
each mapped port file, and enforces per `config/upstream-case-map.json`:

- `adopted`/`partial`: every upstream case must exist in the mapped port file, after the
  file's `renames` map and minus its `missing` entries (each of which carries a reason
  naming the non-ported feature). Port-only extra cases are reported, not fatal.
- `deferred`: an owning task must be recorded (currently TASK-16: profile layer and
  compiler machinery; see the deferred table in
  [task13-case-accounting.md](task13-case-accounting.md)).
- `excluded`: a reason naming the excluded feature must be recorded (DEC-6 JSON adapters,
  the manual ParsingTester harness, the shared Assertions helper).

Fixture directories and non-executable upstream modules are distinguished in the map's
`excluded_upstream_modules` (fixtures feed `scripts/generate-java-fixtures.sh` and
`wire-tests-java`; they are not suites). An upstream test file inside the in-scope modules
that is absent from the map fails the run, as does a map entry whose upstream file
disappeared: both directions of drift are caught.

Case ledgers (the human-readable counterparts of the map):
[task9-case-accounting.md](task9-case-accounting.md) for the runtime suites,
[task13-case-accounting.md](task13-case-accounting.md) for the schema suites.

## Release validation

`python3 scripts/check-parity-coverage.py --require-complete` additionally fails while any
deferred case remains. TASK-21's release procedure must run it; partial M2/M3 coverage is
never release parity.

## Extension contract

- **TASK-15 (protoc oracle)** adds a `protoc-oracle` entry to `config/verify-suites.json`
  and a blocking job in `.github/workflows/ci.yml`; its cases extend the map with a new
  in-scope module section pointing at the protoc corpus, same statuses and reasons.
- **TASK-16 (compiler, generator, profiles)** closes the deferred entries by porting their
  suites (flipping map entries to `adopted`) and adds blocking compiler/profile and
  generated-code comparison jobs. The `compiler-tests` suite flips ACTIVE the way
  `schema-tests` did.
- **TASK-17 (security corpus)** verifies the final required suite and may add its own map
  section for corpus cases.

## Bump procedure

1. Update `upstream` in `config/parity-pins.json` to the new tag, annotated tag object,
   and resolved commit (`git rev-parse <tag>^{tag}` and `git rev-parse <tag>^{commit}`
   inside a fresh clone).
2. Run `./scripts/fetch-upstream.sh` with a fresh clone path; the pin check must pass.
3. Run `python3 scripts/check-parity-coverage.py`. Expect failures where the new upstream
   tag added, renamed, or removed cases; update `config/upstream-case-map.json` and the
   two case ledgers to account for every delta (a case is ported, recorded missing with a
   reason, deferred with an owner, or excluded with a reason; nothing else).
4. Port or disposition the new cases, then run `./scripts/verify.sh`; all ACTIVE suites
   must pass.
