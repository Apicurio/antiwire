#!/usr/bin/env bash
# TASK-14.2: offline regression check for the parity gates.
#
# Builds a scratch repository (synthetic pinned upstream, one mapped test file, synthetic
# surefire reports) and proves the checker's enforced guarantees by mutation:
#
#   baseline   mirrored upstream ignores, DEC-recorded exclusions and owner-recorded
#              skips all pass (source + execution); --require-complete accepts the
#              DEC exclusion and rejects exactly the owner-recorded skip;
#   delete     deleting a required case fails with its identity (lost case);
#   methodskip an unrecorded method-level @Disabled fails with the case identity;
#   classskip  an unrecorded class-level @Disabled fails;
#   fixture    flipping the executable entry to fixture:true fails (both directions
#              of the false-fixture check are validated: upstream and port carry tests);
#   badrecord  a skip record without a lawful disposition (no owner/exclusion) fails;
#   extraskip  an extra skip present only in the surefire report: the replayed
#              verify.sh module-suite parser still records PASS on the aggregate
#              summary (its note points here), and the identity reconciliation fails;
#   ghost      a report case absent from the port source (stale compiled test) fails.
#
# Everything is local git and local files: no network, no real build, no /tmp/wire.
set -euo pipefail

REAL_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d /tmp/antiwire-parity-gate-test.XXXXXX)"
FAILED=0
trap 'if [ "$FAILED" -eq 0 ]; then rm -rf "$WORK"; else echo "scratch kept at $WORK" >&2; fi' EXIT

fail() { # <probe> <message>
  echo "FAIL [$1]: $2" >&2
  FAILED=1
}

pass() { # <probe> <message>
  echo "PASS [$1]: $2"
}

# --- the synthetic pinned upstream -------------------------------------------------------
UPSTREAM="$WORK/upstream"
mkdir -p "$UPSTREAM/wire-schema/src/jvmTest/kotlin/com/squareup/wire/schema"
cat >"$UPSTREAM/wire-schema/src/jvmTest/kotlin/com/squareup/wire/schema/SampleTest.kt" <<'KT'
package com.squareup.wire.schema

import org.junit.Ignore
import org.junit.Test

class SampleTest {
  @Test fun testOne() {}
  @Test fun testTwo() {}
  @Test
  @Ignore("upstream ignores this case")
  fun testThreePortedMirror() {}
  @Test fun testFourRecordedDec6() {}
  @Test fun testFiveRecordedOwner() {}
}
KT
git -C "$UPSTREAM" init -q -b main
git -C "$UPSTREAM" config user.email test@antiwire.local
git -C "$UPSTREAM" config user.name "antiwire parity gate test"
git -C "$UPSTREAM" add -A
git -C "$UPSTREAM" commit -qm "synthetic upstream"
git -C "$UPSTREAM" tag -a 7.1.0 -m "synthetic pin"
PIN_OBJECT="$(git -C "$UPSTREAM" rev-parse "7.1.0^{tag}")"
PIN_COMMIT="$(git -C "$UPSTREAM" rev-parse "7.1.0^{commit}")"
git -C "$UPSTREAM" checkout -q --detach "$PIN_COMMIT"

# --- the scratch port repository ---------------------------------------------------------
BASE="$WORK/base"
mkdir -p "$BASE/scripts" "$BASE/config" \
  "$BASE/wire-schema-java/src/test/java/com/squareup/wire/schema" \
  "$BASE/wire-schema-java/target/surefire-reports"
cp "$REAL_ROOT/scripts/check-parity-coverage.py" "$BASE/scripts/"

cat >"$BASE/config/parity-pins.json" <<PINS
{
  "upstream": {
    "repository": "$UPSTREAM",
    "tag": "7.1.0",
    "annotated_tag_object": "$PIN_OBJECT",
    "resolved_commit": "$PIN_COMMIT"
  },
  "clone_path_default": "$UPSTREAM"
}
PINS

cat >"$BASE/config/upstream-case-map.json" <<'MAP'
{
  "modules": {
    "wire-schema/src/jvmTest/kotlin": {
      "root": "wire-schema/src/jvmTest/kotlin",
      "files": {
        "com/squareup/wire/schema/SampleTest.kt": {
          "port": "wire-schema-java/src/test/java/com/squareup/wire/schema/SampleTest.java",
          "skipped": {
            "testFourRecordedDec6": {
              "exclusion": "DEC-6",
              "reason": "declared non-ported feature (synthetic)"
            },
            "testFiveRecordedOwner": {
              "owner": "TASK-99.9",
              "reason": "pending its owning task (synthetic)"
            }
          }
        }
      }
    }
  },
  "excluded_upstream_modules": {}
}
MAP

cat >"$BASE/wire-schema-java/src/test/java/com/squareup/wire/schema/SampleTest.java" <<'JAVA'
package com.squareup.wire.schema;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class SampleTest {
  @Test
  public void testOne() {}

  @Test
  public void testTwo() {}

  @Test
  @Disabled("mirrors the upstream @Ignore")
  public void testThreePortedMirror() {}

  // Parenthesized multi-line disable body: the annotation's argument spans lines with a
  // bare closing parenthesis, a form the skip detection must still recognize.
  @Test
  @Disabled(
      "DEC-6: declared non-ported feature"
  )
  public void testFourRecordedDec6() {}

  @Test
  @Disabled("pending its owning task")
  public void testFiveRecordedOwner() {}

  // The display name quotes an annotation name: it must not register a phantom case.
  @DisplayName("checks the @Test scanner, not a case")
  private void helperNotACase() {}
}
JAVA

surefire_report() { # <file> <extra-testcase-xml>: writes the synthetic report
  cat >"$1" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.squareup.wire.schema.SampleTest" time="0.01" tests="5" errors="0" skipped="3" failures="0">
  <testcase name="testOne" classname="com.squareup.wire.schema.SampleTest" time="0.001"/>
  <testcase name="testTwo" classname="com.squareup.wire.schema.SampleTest" time="0.001"/>
  <testcase name="testThreePortedMirror" classname="com.squareup.wire.schema.SampleTest" time="0.0"><skipped message="mirrors the upstream @Ignore"/></testcase>
  <testcase name="testFourRecordedDec6" classname="com.squareup.wire.schema.SampleTest" time="0.0"><skipped message="DEC-6"/></testcase>
  <testcase name="testFiveRecordedOwner" classname="com.squareup.wire.schema.SampleTest" time="0.0"><skipped message="owner"/></testcase>
$2
</testsuite>
XML
}
surefire_report "$BASE/wire-schema-java/target/surefire-reports/TEST-com.squareup.wire.schema.SampleTest.xml" ""

# Runs the checker inside a scratch copy and prints its combined output.
# check_parity <dir> [extra args...]; sets CPC_RC.
check_parity() {
  local dir="$1"
  shift
  set +e
  (cd "$dir" && ANTIWIRE_UPSTREAM="$UPSTREAM" python3 scripts/check-parity-coverage.py "$@") \
    >"$dir/check.out" 2>&1
  CPC_RC=$?
  set -e
}

echo "=== parity gate check (synthetic upstream $PIN_COMMIT) ==="

# --- baseline: every lawful disposition passes; release validation splits owner/exclusion -
check_parity "$BASE"
if [ "$CPC_RC" -ne 0 ] || ! grep -q "RESULT parity-coverage.status=PASS" "$BASE/check.out"; then
  fail baseline "source reconciliation did not pass on the valid baseline:"
  sed 's/^/    /' "$BASE/check.out" >&2
else
  pass baseline "mirrored ignore + DEC-recorded + owner-recorded skips all pass"
fi
check_parity "$BASE" --execution
if [ "$CPC_RC" -ne 0 ] || ! grep -q "RESULT parity-coverage.status=PASS" "$BASE/check.out"; then
  fail baseline "execution reconciliation did not pass on the valid baseline:"
  sed 's/^/    /' "$BASE/check.out" >&2
else
  pass baseline "execution identities reconciled (5 cases, 3 lawful skips)"
fi
check_parity "$BASE" --require-complete
if [ "$CPC_RC" -eq 0 ] || ! grep -q "testFiveRecordedOwner -> TASK-99.9" "$BASE/check.out"; then
  fail baseline "--require-complete must reject the owner-recorded skip by name and task"
else
  pass baseline "release validation rejects the owner-recorded skip, not the DEC exclusion"
fi

# --- mutate: each probe starts from a fresh copy of the valid baseline -------------------
mutate() { # <name>: prints a fresh copy dir of BASE
  local dir="$WORK/$1"
  cp -r "$BASE" "$dir"
  printf '%s\n' "$dir"
}

# delete: a required case disappears from the port file.
dir="$(mutate delete)"
python3 - "$dir/wire-schema-java/src/test/java/com/squareup/wire/schema/SampleTest.java" <<'PY'
import sys
path = sys.argv[1]
text = open(path).read()
removed = "  @Test\n  public void testOne() {}\n\n"
assert removed in text, "delete probe: block not found"
open(path, "w").write(text.replace(removed, "", 1))
PY
check_parity "$dir"
if [ "$CPC_RC" -eq 0 ] || ! grep -q "lost cases: testone" "$dir/check.out"; then
  fail delete "deleting a required case must fail naming testone"
else
  pass delete "lost case testone reported with its identity"
fi

# methodskip: an unrecorded method-level @Disabled on a required case.
dir="$(mutate methodskip)"
sed -i 's/  public void testTwo() {}/  @Disabled("temporary local disable: no recorded reason anywhere")\n  public void testTwo() {}/' \
  "$dir/wire-schema-java/src/test/java/com/squareup/wire/schema/SampleTest.java"
check_parity "$dir"
if [ "$CPC_RC" -eq 0 ] || ! grep -q "case testTwo is skipped" "$dir/check.out"; then
  fail methodskip "an unrecorded method-level skip must fail naming testTwo"
else
  pass methodskip "unrecorded method-level skip on testTwo reported with its identity"
fi

# classskip: a class-level @Disabled without a skipped_class record (annotation on its
# own line above the class declaration).
dir="$(mutate classskip)"
sed -i 's/^public class SampleTest {/@Disabled("whole class disabled: no record")\npublic class SampleTest {/' \
  "$dir/wire-schema-java/src/test/java/com/squareup/wire/schema/SampleTest.java"
check_parity "$dir"
if [ "$CPC_RC" -eq 0 ] || ! grep -q "disabled at class level without a recorded" "$dir/check.out"; then
  fail classskip "an unrecorded class-level skip must fail"
else
  pass classskip "unrecorded class-level skip rejected"
fi

# classskipsameline: the same, with the disable annotation on the class declaration's
# own line (a form whose misparse would blame an individual case instead).
dir="$(mutate classskipsameline)"
sed -i 's/^public class SampleTest {/@Disabled("whole class disabled: no record") public class SampleTest {/' \
  "$dir/wire-schema-java/src/test/java/com/squareup/wire/schema/SampleTest.java"
check_parity "$dir"
if [ "$CPC_RC" -eq 0 ] || ! grep -q "disabled at class level without a recorded" "$dir/check.out"; then
  fail classskipsameline "a same-line class-level skip must fail as class-level"
else
  pass classskipsameline "same-line class-level skip reported at class level"
fi

# bareparen: the disable annotation's argument is parenthesized across lines and its
# map record is removed; the skip must still be detected and reported by identity.
dir="$(mutate bareparen)"
python3 - "$dir/config/upstream-case-map.json" <<'PY'
import json, sys
path = sys.argv[1]
cm = json.load(open(path))
entry = cm["modules"]["wire-schema/src/jvmTest/kotlin"]["files"]["com/squareup/wire/schema/SampleTest.kt"]
del entry["skipped"]["testFourRecordedDec6"]
json.dump(cm, open(path, "w"), indent=2)
PY
check_parity "$dir"
if [ "$CPC_RC" -eq 0 ] || ! grep -q "case testFourRecordedDec6 is skipped" "$dir/check.out"; then
  fail bareparen "a parenthesized multi-line disable must be detected without a record"
else
  pass bareparen "parenthesized multi-line disable detected and reported"
fi

# fixture: the executable entry is flipped to fixture:true.
dir="$(mutate fixture)"
python3 - "$dir/config/upstream-case-map.json" <<'PY'
import json, sys
path = sys.argv[1]
cm = json.load(open(path))
entry = cm["modules"]["wire-schema/src/jvmTest/kotlin"]["files"]["com/squareup/wire/schema/SampleTest.kt"]
entry["fixture"] = True
json.dump(cm, open(path, "w"), indent=2)
PY
check_parity "$dir"
if [ "$CPC_RC" -eq 0 ] || ! grep -q "is marked fixture but" "$dir/check.out" \
  || ! grep -q "carries 5 test method" "$dir/check.out"; then
  fail fixture "a false fixture classification must fail on both sides"
else
  pass fixture "fixture:true on an executable file rejected (upstream and port carry tests)"
fi

# badrecord: a skip record without a lawful disposition.
dir="$(mutate badrecord)"
python3 - "$dir/config/upstream-case-map.json" <<'PY'
import json, sys
path = sys.argv[1]
cm = json.load(open(path))
entry = cm["modules"]["wire-schema/src/jvmTest/kotlin"]["files"]["com/squareup/wire/schema/SampleTest.kt"]
entry["skipped"]["testTwo"] = {"reason": "a reason with neither exclusion nor owner"}
json.dump(cm, open(path, "w"), indent=2)
PY
sed -i 's/  public void testTwo() {}/  @Disabled("recorded without classification")\n  public void testTwo() {}/' \
  "$dir/wire-schema-java/src/test/java/com/squareup/wire/schema/SampleTest.java"
check_parity "$dir"
if [ "$CPC_RC" -eq 0 ] || ! grep -q "needs exactly one of exclusion" "$dir/check.out"; then
  fail badrecord "a skip record without exclusion/owner must fail"
else
  pass badrecord "unclassified skip disposition rejected"
fi

# extraskip: an extra skip that exists only in the surefire report. The replayed
# verify.sh module-suite parser (the real functions, extracted from verify.sh) records
# PASS on the aggregate summary, which is exactly why the identity reconciliation here
# is the gate: it must fail on the unrecorded runtime skip.
dir="$(mutate extraskip)"
surefire_report "$dir/wire-schema-java/target/surefire-reports/TEST-com.squareup.wire.schema.SampleTest.xml" \
  '  <testcase name="testOne" classname="com.squareup.wire.schema.SampleTest" time="0.0"><skipped message="runtime-only skip"/></testcase>'
# Replay the real module-suite parser on a synthetic mvn log with the same shape. The
# extraction pulls the exact function bodies out of verify.sh (one-line and multi-line
# definitions), so the replay runs the shipping parser, not a copy of it.
extract_fn() {
  awk -v fn="$1" '
    !done && $0 ~ "^" fn "\\(\\)" {
      print
      if ($0 ~ /\}/) { done = 1 } else { inc = 1 }
      next
    }
    inc {
      print
      if ($0 ~ /^}/) { inc = 0; done = 1 }
    }
  ' "$REAL_ROOT/scripts/verify.sh"
}
eval "$(extract_fn res)"$'\n'"$(extract_fn module_test_summary)"$'\n'"$(extract_fn surefire_count)"$'\n'"$(extract_fn module_tests_suite)"
RESULTS="$dir/module-suite.results"
LOG="$dir/mvn.log"
KEEP_LOG=0
printf '[INFO] Building antiwire wire-schema-java 0.1.0-SNAPSHOT\n[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 4\n' >"$LOG"
module_tests_suite schema-tests wire-schema-java
if ! grep -q "schema-tests.status=PASS" "$RESULTS" \
   || ! grep -q "skips reconciled by identity in the parity-coverage suite" "$RESULTS"; then
  fail extraskip "could not replay the verify.sh module-suite parser on the synthetic log"
else
  check_parity "$dir" --execution
  if [ "$CPC_RC" -eq 0 ] || ! grep -q "testOne skipped at runtime without a lawful disposition" "$dir/check.out"; then
    fail extraskip "the identity reconciliation must catch the runtime-only skip of testOne"
  else
    pass extraskip "aggregate parser passes (as designed), identity reconciliation fails the unrecorded skip"
  fi
fi

# ghost: a report case absent from the port source (stale compiled test).
dir="$(mutate ghost)"
surefire_report "$dir/wire-schema-java/target/surefire-reports/TEST-com.squareup.wire.schema.SampleTest.xml" \
  '  <testcase name="ghostMethod" classname="com.squareup.wire.schema.SampleTest" time="0.001"/>'
check_parity "$dir" --execution
if [ "$CPC_RC" -eq 0 ] || ! grep -q "ran a case (ghostmethod) absent from the port source" "$dir/check.out"; then
  fail ghost "a stale compiled case in the report must fail"
else
  pass ghost "stale compiled case reported against the port source"
fi

echo
if [ "$FAILED" -ne 0 ]; then
  echo "RESULT parity-gate-check.status=FAIL"
  exit 1
fi
echo "RESULT parity-gate-check.status=PASS"
echo "parity gate check: baseline plus ten mutation probes behaved as specified"
