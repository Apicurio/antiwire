#!/usr/bin/env bash
# Release candidate builder (TASK-21 mechanical release groundwork).
#
# What this run does:
#   1. clean-packages the three shipped modules (wire-runtime-java, wire-schema-java,
#      wire-java-generator) with their -sources and -javadoc jars attached at package,
#   2. assembles target/release/ holding every release artifact plus MANIFEST.sha256,
#   3. rewrites docs/release-candidate.md identifying the candidate (git revision, build
#      date, wire pin, artifact checksums) with the two OPEN maintainer gates auto-filled
#      from the live text of docs/footprint.md and docs/performance.md.
#
# What this never does: deploy, tag, publish. The DEC-8 guard maven.deploy.skip stays true
# in the parent pom and this script invokes no deploy, sign, or scm goal.
#
# Tests are deliberately not run here (-DskipTests): the test authority is
# scripts/verify.sh, which the release gate runs against the same revision. This script
# proves the packaging and produces the candidate record; it is not parity evidence.
set -euo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
check_modules_consistency
# Run from the repo root so the relative paths below (parity pin, candidate doc, mvn pom)
# hold regardless of the invoking cwd.
cd "$ROOT"

# Toolchain: JAVA_HOME from the environment, defaulting to the build JDK the footprint
# and performance reports were measured with (Temurin 17.0.12). On a host without that
# sdkman path, fall back to the java discovered on PATH; DEC-3's JDK 17+ floor is
# enforced by the maven-enforcer rule during the build either way.
JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/17.0.12-tem}"
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  if command -v java >/dev/null 2>&1; then
    JAVA_HOME="$(cd "$(dirname "$(command -v java)")/.." && pwd)"
    echo "==> default JDK path absent; using PATH java under $JAVA_HOME (enforcer checks the 17+ floor)" >&2
  else
    echo "FATAL: no runnable JDK: $JAVA_HOME/bin/java is absent and no java is on PATH; set JAVA_HOME to a JDK 17+ toolchain" >&2
    exit "$EXIT_FAIL"
  fi
fi
export JAVA_HOME

VERSION="$(project_version)"
RELEASE_DIR="$ROOT/target/release"
CANDIDATE_DOC="$ROOT/docs/release-candidate.md"

# Candidate identity, recorded before any tree change this script makes (it rewrites only
# the candidate doc, listed in the dirty set below when the tree is not clean).
REV="$(git -C "$ROOT" rev-parse HEAD)"
SHORT_REV="$(git -C "$ROOT" rev-parse --short HEAD)"
BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
JAVA_VERSION="$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
PORCELAIN="$(git -C "$ROOT" status --porcelain)"

# Wire pin, read from the same file the parity runner verifies against: one guarded
# parse producing both values, so a missing key fails with the house FATAL, not a traceback.
PINS="$(python3 -c 'import json; u = json.load(open("config/parity-pins.json"))["upstream"]; print(u["tag"], u["resolved_commit"])')" || {
  echo "FATAL: could not read the upstream tag and commit from config/parity-pins.json" >&2
  exit "$EXIT_FAIL"
}
WIRE_TAG="${PINS%% *}"
WIRE_COMMIT="${PINS##* }"

# The two OPEN maintainer gates, auto-filled from the live acceptance rows so the
# generated doc cannot drift from the decision docs. The grep anchors on each
# acceptance row (not the bare word PENDING, which appears in prose too); a grep error
# (unreadable doc) and an unclassifiable row (the anchored PENDING wording gone, which
# means reworded, restructured, or resolved) are both fatal: a resolved gate must be
# recorded in its doc AND re-anchored here by hand, because this script cannot tell a
# signature from a rewording, and a false "closed" is the dangerous direction.
maintainer_gate() { # <doc> <anchored-row-pattern> <open-message> <gate-name>
  local doc="$1" pattern="$2" rc=0
  grep -Eq "$pattern" "$doc" || rc=$?
  if [ "$rc" -gt 1 ]; then
    echo "FATAL: could not read the gate status from $doc (grep exit $rc)" >&2
    exit "$EXIT_FAIL"
  fi
  if [ "$rc" -eq 0 ]; then
    printf '%s\n' "$3"
  else
    echo "FATAL: the $4 acceptance row in $doc no longer matches the expected PENDING wording; if the gate was resolved, record the signature there and update this script's anchor; if it was reworded, re-anchor the pattern" >&2
    exit "$EXIT_FAIL"
  fi
}
FOOTPRINT_GATE="$(maintainer_gate "$ROOT/docs/footprint.md" \
  'Acceptance of the measured footprint.*PENDING maintainer signature' \
  'OPEN: footprint acceptance is unsigned (docs/footprint.md section 8); the maintainer should weigh it together with the guava pin observation of section 4.4.' \
  'footprint')"
ENCODEFORWARD_GATE="$(maintainer_gate "$ROOT/docs/performance.md" \
  'EmailSearchBench\.encodeForward.*PENDING maintainer' \
  'OPEN: perf.EmailSearchBench.encodeForward remains unaccepted (docs/performance.md section 6, finding 3); an explicit maintainer acceptance record or a fix is still required before publish.' \
  'encodeForward')"

echo "==> release candidate from $SHORT_REV (wire pin $WIRE_TAG @ $WIRE_COMMIT)"
echo "==> toolchain: $JAVA_VERSION"

# Clean package of the shipped modules and their reactor dependencies. -am pulls in
# wire-upstream-shaded because the shipped modules' test compilation resolves it; BUILD.md
# documents that package-phase (and later) reactor runs are the supported form. Sources
# and javadoc jars are attached at package by each shipped module's pom.
SHIPPED_PL="$(IFS=,; printf '%s' "${SHIPPED_MODULES[*]}")"
mvn -B -ntp -f "$ROOT/pom.xml" -pl "$SHIPPED_PL" -am clean package -DskipTests

# Assemble the release directory: exactly the main, -sources, and -javadoc jar of each
# shipped module. The main jar goes through lib.sh's find_module_jar (the single source
# of truth for that path, fail-closed on missing or ambiguous jars); the -sources and
# -javadoc companions are release-specific paths find_module_jar deliberately excludes,
# so they are checked directly. Test-classifier jars (for example wire-schema-java's
# shared test-utils) are test artifacts and are never release artifacts.
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"
for module in "${SHIPPED_MODULES[@]}"; do
  main_jar="$(find_module_jar "$module")" || exit "$EXIT_FAIL"
  cp "$main_jar" "$RELEASE_DIR/"
  for suffix in "-sources" "-javadoc"; do
    jar="$ROOT/$module/target/$module-$VERSION$suffix.jar"
    if [ ! -f "$jar" ]; then
      echo "FATAL: expected release artifact missing after the build: $jar" >&2
      exit "$EXIT_FAIL"
    fi
    cp "$jar" "$RELEASE_DIR/"
  done
done

(cd "$RELEASE_DIR" && shasum -a 256 -- *.jar | LC_ALL=C sort -k 2 > MANIFEST.sha256)

# Manifest summary lines for the candidate doc are built by the python step below, which
# reads MANIFEST.sha256 and sizes each jar in one pass (a missing file there raises, so a
# malformed manifest fails the run instead of producing a wrong doc).
PORCELAIN_PRESENT="${PORCELAIN:-(clean)}"
ARTIFACT_COUNT="$(( ${#SHIPPED_MODULES[@]} * 3 ))"

# The candidate doc is generated: do not hand-edit it; rerun this script instead.
export REV SHORT_REV BUILD_DATE JAVA_VERSION PORCELAIN_PRESENT WIRE_TAG WIRE_COMMIT
export FOOTPRINT_GATE ENCODEFORWARD_GATE VERSION ARTIFACT_COUNT CANDIDATE_DOC
python3 - <<'PYEOF'
import os
summary = []
for line in open("target/release/MANIFEST.sha256"):
    sha, name = line.split()
    summary.append("- `%s` (%d bytes): `%s`" % (name, os.path.getsize("target/release/" + name), sha))
doc = """# Release candidate (generated)

Generated by `scripts/release-build.sh` on __BUILD_DATE__ from revision `__REV__`
(`__SHORT_REV__`). This file is a build artifact: do not edit it by hand; rerun the
script. The build performed no deploy, no tag, and no publish (DEC-8 guard;
`maven.deploy.skip` is true in the parent pom).

## Candidate identity

| Field | Value |
|---|---|
| Git revision | `__REV__` (`__SHORT_REV__`) |
| Build date (UTC) | __BUILD_DATE__ |
| Toolchain | __JAVA_VERSION__ |
| Upstream wire pin | tag `__WIRE_TAG__` at `__WIRE_COMMIT__` (config/parity-pins.json) |
| Version | __VERSION__ |

Working tree at build time (beyond the recorded revision):

```
__PORCELAIN_PRESENT__
```

## Artifacts and checksums

Each shipped module contributes its main, `-sources`, and `-javadoc` jar; __ARTIFACT_COUNT__
artifacts total. Full SHA-256 manifest (also at `target/release/MANIFEST.sha256`):

__MANIFEST_SUMMARY__

Open maintainer gates (auto-filled from the current docs; both must be closed before any
publish):

1. __FOOTPRINT_GATE__
2. __ENCODEFORWARD_GATE__

Freshness (DEC-13): the footprint and performance reports were measured on earlier
revisions and their acceptance records bind to those candidates' checksums. Signing them
against this candidate requires the records to identify this build (revision and checksums
above); a relevant change since measurement means remeasurement and renewed acceptance,
and stale records are refused. The full release gate set, including the Java 11 consumer
smoke on the final candidate and the release-time no-Kotlin recheck of published metadata,
is in docs/decisions.md DEC-13.
""".replace("__BUILD_DATE__", os.environ["BUILD_DATE"]) \
   .replace("__ARTIFACT_COUNT__", os.environ["ARTIFACT_COUNT"]) \
   .replace("__REV__", os.environ["REV"]) \
   .replace("__SHORT_REV__", os.environ["SHORT_REV"]) \
   .replace("__JAVA_VERSION__", os.environ["JAVA_VERSION"]) \
   .replace("__WIRE_TAG__", os.environ["WIRE_TAG"]) \
   .replace("__WIRE_COMMIT__", os.environ["WIRE_COMMIT"]) \
   .replace("__VERSION__", os.environ["VERSION"]) \
   .replace("__PORCELAIN_PRESENT__", os.environ["PORCELAIN_PRESENT"]) \
   .replace("__MANIFEST_SUMMARY__", "\n".join(summary)) \
   .replace("__FOOTPRINT_GATE__", os.environ["FOOTPRINT_GATE"]) \
   .replace("__ENCODEFORWARD_GATE__", os.environ["ENCODEFORWARD_GATE"])
# Write through a temp file and os.replace so the tree only ever holds the old or the
# new doc, never a truncated one.
path = os.environ["CANDIDATE_DOC"]
with open(path + ".tmp", "w") as f:
    f.write(doc)
os.replace(path + ".tmp", path)
PYEOF

echo "==> candidate record written to $CANDIDATE_DOC"
echo "==> artifacts in target/release/:"
sed 's/^/    /' "$RELEASE_DIR/MANIFEST.sha256"
echo "==> open maintainer gates:"
echo "    [1] $FOOTPRINT_GATE"
echo "    [2] $ENCODEFORWARD_GATE"
echo "==> done. No deploy, no tag, no publish was performed."
