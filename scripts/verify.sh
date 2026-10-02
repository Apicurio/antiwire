#!/usr/bin/env bash
# Single verification entry point for antiwire (TASK-2 AC#2).
#
# This is the only command CI calls from its build job, and the place later tasks extend:
# a suite becomes ACTIVE when its owning task flips its status in config/verify-suites.json
# and, when needed, adds the step that runs it here. PENDING suites are listed but never
# run and never printed as passed. A suite is PASS only when its script printed
# result=PASS AND exited EXIT_OK; any other outcome (FAIL, MISSING, NOT_RUN) is recorded
# with the suite's exit code and fails the run. A passing entry point means exactly this:
# the ACTIVE build checks passed. Empty suites are not evidence of parity.
#
# Stale-tree guard: when mvn verify fails, the suites that consume build artifacts are
# skipped, never executed against leftovers from an earlier build; each is recorded
# MISSING with a not-attributable note (the overall verdict stays FAIL). dependency-policy
# is the one exception: when the enforcer's bannedDependencies rule is what failed, it is
# recorded FAIL with that attribution, because the enforcer verdict IS attributable.
set -uo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
check_modules_consistency

MANIFEST="$ROOT/config/verify-suites.json"
RESULTS="$(mktemp)"
LOG="$(mktemp)"
KEEP_LOG=0
trap 'rm -f "$RESULTS"; [ "$KEEP_LOG" -eq 1 ] || rm -f "$LOG"' EXIT

res() { printf 'RESULT %s\n' "$1" >> "$RESULTS"; }

# Record one suite script's verdict after reconciling its RESULT lines with its exit code:
# PASS only when it printed result=PASS AND exited EXIT_OK; a nonzero exit (including
# EXIT_NOT_RUN) is recorded as FAIL or MISSING with the exit code in the note, so output
# alone can never make a suite pass.
record_suite() { # <suite-name> <exit-code> <log-file>
  local name="$1" rc="$2" log="$3"
  local printed note
  printed="$(grep "^RESULT ${name}\.status=" "$log" | head -n 1 | sed 's/^[^=]*=//')"
  note="$(grep "^RESULT ${name}\.note=" "$log" | head -n 1 | sed 's/^[^=]*=//')"
  if [ "$rc" -eq "$EXIT_OK" ] && [ "$printed" = "PASS" ]; then
    res "${name}.status=PASS"
    res "${name}.note=${note:-passed; the suite script recorded no note}"
  elif [ -z "$printed" ]; then
    res "${name}.status=MISSING"
    res "${name}.note=no result recorded by the suite script (exit code $rc); see its output above"
  elif [ "$printed" = "NOT_RUN" ]; then
    res "${name}.status=MISSING"
    res "${name}.note=could not run (exit code $rc): ${note:-no note recorded}"
  elif [ "$printed" = "PASS" ]; then
    res "${name}.status=FAIL"
    res "${name}.note=the suite script printed PASS but exited with code $rc: ${note:-no note recorded}"
  else
    res "${name}.status=FAIL"
    res "${name}.note=${note:-failed; no note recorded} (exit code $rc)"
  fi
}

run_suite() { # <suite-name> <script-relative-path>
  local name="$1" script="$2" rc
  echo
  echo "--- suite: $name ($script) ---"
  bash "$ROOT/$script" >"$LOG" 2>&1
  rc=$?
  cat "$LOG"
  record_suite "$name" "$rc" "$LOG"
}

# Stale-tree guard: record an artifact-consuming suite as not run when the build failed,
# so no stale artifact can produce a PASS for this revision.
skip_suite() { # <suite-name> <script-relative-path>
  local name="$1" script="$2"
  echo
  echo "--- suite: $name ($script): SKIPPED, mvn verify failed (see the build log) ---"
  res "$name.status=MISSING"
  res "$name.note=build failed; artifact results not attributable to the current revision"
}

if command -v java >/dev/null 2>&1; then
  build_jvm="$(java -version 2>&1 | head -n 1)"
else
  build_jvm=""
  echo "WARNING: no java executable on PATH; mvn verify relies on JAVA_HOME alone." >&2
fi

echo "=== antiwire verification entry point: scripts/verify.sh ==="
echo "build toolchain JVM: ${build_jvm:-(none found: no java executable on PATH)}"

echo
echo "--- suite: build (mvn verify; includes the dependency-policy enforcer rules) ---"
build_ok=1
if mvn -B -ntp -f "$ROOT/pom.xml" verify >"$LOG" 2>&1; then
  tail -n 40 "$LOG"
  res "build.status=PASS"
  res "build.note=mvn verify green on: ${build_jvm:-unreported JVM}"
  res "dependency-policy.status=PASS"
  res "dependency-policy.note=enforcer rules ran inside mvn verify: Maven and JDK 17+ floors, no Kotlin or Kotlin-backed artifact in production scope"
else
  build_ok=0
  cat "$LOG"
  # Keep the mvn log around for debugging; it is removed again on a green run.
  KEEP_LOG=1
  res "build.status=FAIL"
  res "build.note=mvn verify failed, see log above; full mvn output kept at $LOG"
  if grep -q "bannedDependencies failed" "$LOG"; then
    # The dependency policy is the one suite whose verdict is attributable from the mvn
    # log alone; toolchain rule failures (requireMavenVersion/requireJavaVersion) must
    # not claim it, so the match stays scoped to the bannedDependencies rule.
    res "dependency-policy.status=FAIL"
    res "dependency-policy.note=enforcer bannedDependencies rule tripped: a banned artifact sits in production scope (DEC-4); see log above and $LOG"
  else
    res "dependency-policy.status=MISSING"
    res "dependency-policy.note=build failed; artifact results not attributable to the current revision (log: $LOG)"
  fi
fi

# Module test suites (runtime-tests TASK-9, schema-tests TASK-13): their evidence is the
# per-module surefire summary inside the green mvn verify log above.
module_test_summary() { # <artifactId>
  awk -v prefix="] Building antiwire $1 " '
    /] Building antiwire / {
      inmod = index($0, prefix) > 0
      if (inmod) last = ""
    }
    inmod && /^\[(INFO|WARNING)\] Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+/ { last = $0 }
    END { print last }
  ' "$LOG"
}

module_tests_suite() { # <suite> <artifactId>
  local suite="$1" artifact="$2" summary count
  summary="$(module_test_summary "$artifact")"
  count="$(printf '%s\n' "$summary" | sed -n 's/^.*Tests run: \([0-9][0-9]*\),.*$/\1/p')"
  if [ -n "$count" ] && printf '%s\n' "$summary" | grep -q "Failures: 0, Errors: 0"; then
    res "$suite.status=PASS"
    res "$suite.note=$count $artifact cases green inside mvn verify (Failures: 0, Errors: 0)"
  else
    res "$suite.status=FAIL"
    res "$suite.note=missing or failing surefire summary for $artifact; see $LOG"
  fi
}

if [ "$build_ok" -eq 1 ]; then
  module_tests_suite runtime-tests wire-runtime-java
  module_tests_suite schema-tests wire-schema-java
  run_suite duplicate-class-check scripts/check-classpath.sh
  run_suite bytecode-java11 scripts/check-java11-bytecode.sh
  run_suite java11-consumer scripts/consumer-check-java11.sh
else
  skip_suite duplicate-class-check scripts/check-classpath.sh
  skip_suite bytecode-java11 scripts/check-java11-bytecode.sh
  skip_suite java11-consumer scripts/consumer-check-java11.sh
fi

echo
python3 - "$MANIFEST" "$RESULTS" <<'PY'
import json
import sys

manifest_path, results_path = sys.argv[1], sys.argv[2]
results = {}
for line in open(results_path):
    line = line.strip()
    if not line.startswith("RESULT "):
        continue
    key, _, value = line[len("RESULT "):].partition("=")
    results[key] = value

with open(manifest_path) as f:
    data = json.load(f)

names = {s["name"] for s in data["suites"]}
unknown = sorted({k.split(".")[0] for k in results} - names)
if unknown:
    print("INTERNAL ERROR: results for suites absent from the manifest: %s"
          % ", ".join(unknown))
    sys.exit(2)

rows = []
failures = []
active = 0
pending = []
for s in data["suites"]:
    name = s["name"]
    if s["status"] == "ACTIVE":
        active += 1
        status = results.get(name + ".status", "MISSING")
        note = results.get(name + ".note", "no result recorded by the entry point")
        if status != "PASS":
            failures.append(name)
    else:
        pending.append("%s (%s)" % (name, s["owner"]))
        status = "PENDING"
        note = "planned; not run and not passed"
    rows.append((name, s["status"], s["owner"], status, note))

width = max(len(r[0]) for r in rows)
print("Suite registry from %s:" % manifest_path)
for name, declared, owner, status, note in rows:
    print("  %-*s  declared=%-7s owner=%-7s result=%-7s %s"
          % (width, name, declared, owner, status, note))
print()
if failures:
    print("VERDICT: FAIL. ACTIVE suites not passed: %s" % ", ".join(failures))
    sys.exit(1)
print("VERDICT: all %d ACTIVE suites passed." % active)
if pending:
    print("PENDING suites: %s." % ", ".join(pending))
    print("They have not run. This run claims no parity, compatibility, or completeness.")
sys.exit(0)
PY
exit $?
