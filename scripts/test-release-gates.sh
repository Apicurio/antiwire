#!/usr/bin/env bash
# TASK-21.1: offline state-matrix regression check for the release-build.sh maintainer
# gates.
#
# The gates must recognize the recorded terminal states, not only the historical PENDING
# wording (the footprint predicate's zero-match abort at the old line 84 is the exact
# transition this covers), and stay fail-closed everywhere else:
#
#   current   the real docs as committed: both gates CLOSED (footprint ACCEPTED rows,
#             encodeForward RESOLVED row), exit 0, read-only (no build, no doc rewrite);
#   pending   scratch docs reverted to the pre-resolution PENDING wording: exit 0 with
#             OPEN gate messages (a pending gate is a recognized state, reported open);
#   missing   scratch docs whose acceptance row is reworded beyond both anchors: FATAL;
#   reopened  scratch docs with an accepted row AND a new pending row: FATAL;
#   ambiguous scratch docs with two different accepted rows: FATAL;
#   nodoc     the footprint doc itself is absent: FATAL.
#
# Every scratch state runs scripts/release-build.sh --check-gates inside a scratch copy
# (pom.xml, scripts, config, docs); the real tree is only read, never written, and no
# packaging, deploy, tag or publication ever runs.
set -euo pipefail

REAL_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d /tmp/antiwire-release-gates-test.XXXXXX)"
FAILED=0
trap 'if [ "$FAILED" -eq 0 ]; then rm -rf "$WORK"; else echo "scratch kept at $WORK" >&2; fi' EXIT

fail() { # <state> <message>
  echo "FAIL [$1]: $2" >&2
  FAILED=1
}

pass() { # <state> <message>
  echo "PASS [$1]: $2"
}

FOOTPRINT_ACCEPTED_CELL='**ACCEPTED by the maintainer (P. Antinori, 2026-10-03, session record): section 9.1 candidate c713e9a with the guava-free marginal; bound to the recorded checksums and revision per the invalidation rule**'
PERF_RESOLVED_CELL='RESOLVED, port now 2.2x faster (mandate floor 0.95 exceeded in both repetitions)'

make_scratch() { # <name>: scratch repo copy; prints its dir
  local dir="$WORK/$1"
  mkdir -p "$dir"
  cp "$REAL_ROOT/pom.xml" "$dir/pom.xml"
  cp -r "$REAL_ROOT/scripts" "$dir/scripts"
  cp -r "$REAL_ROOT/config" "$dir/config"
  cp -r "$REAL_ROOT/docs" "$dir/docs"
  printf '%s\n' "$dir"
}

run_check() { # <dir>: runs the scratch gate check; sets GATE_RC, output in <dir>/out.log
  set +e
  bash "$1/scripts/release-build.sh" --check-gates >"$1/out.log" 2>&1
  GATE_RC=$?
  set -e
}

mutate_footprint() { # <file> <old-cell> <new-cell> [count]
  python3 - "$1" "$2" "$3" "${4:-all}" <<'PY'
import sys
path, old, new, count = sys.argv[1:5]
text = open(path).read()
assert old in text, "release-gates test: cell not found in %s" % path
if count == "all":
    open(path, "w").write(text.replace(old, new))
else:
    open(path, "w").write(text.replace(old, new, int(count)))
PY
}

echo "=== release gate state matrix (real docs, then scratch mutations) ==="

# --- current: the committed docs; read-only run against the real tree ---------------------
# The output goes to the scratch dir: nothing may be written into the real tree.
CURRENT_OUT="$WORK/current.out"
set +e
bash "$REAL_ROOT/scripts/release-build.sh" --check-gates >"$CURRENT_OUT" 2>&1
CURRENT_RC=$?
set -e
if [ "$CURRENT_RC" -ne 0 ]; then
  fail current "the gate section does not recognize the recorded states of the real docs:"
  sed 's/^/    /' "$CURRENT_OUT" >&2
elif ! grep -q "CLOSED: footprint acceptance gate recorded in docs/footprint.md" "$CURRENT_OUT" \
  || ! grep -q "CLOSED: encodeForward acceptance gate recorded in docs/performance.md" "$CURRENT_OUT" \
  || ! grep -q "no build, no packaging, no doc rewrite" "$CURRENT_OUT"; then
  fail current "gate statuses not reported CLOSED with the read-only banner"
elif [ -e "$REAL_ROOT/target/release" ]; then
  fail current "the gate check must not build anything (target/release exists)"
else
  pass current "recorded ACCEPTED/RESOLVED rows recognized as CLOSED, read-only, exit 0"
fi

# --- pending: revert both acceptance rows to the pre-resolution PENDING wording ----------
dir="$(make_scratch pending)"
mutate_footprint "$dir/docs/footprint.md" "$FOOTPRINT_ACCEPTED_CELL" '**PENDING maintainer signature**'
mutate_footprint "$dir/docs/performance.md" "$PERF_RESOLVED_CELL" 'PENDING maintainer'
run_check "$dir"
if [ "$GATE_RC" -ne 0 ]; then
  fail pending "a pending gate is a recognized state and must not abort"
elif ! grep -q "OPEN: footprint acceptance is unsigned" "$dir/out.log" \
   || ! grep -q "OPEN: perf.EmailSearchBench.encodeForward remains unaccepted" "$dir/out.log"; then
  fail pending "pending gates must be reported OPEN with their historical messages"
else
  pass pending "PENDING wording recognized and reported OPEN"
fi

# --- missing-evidence: the acceptance row is reworded beyond both anchors ----------------
dir="$(make_scratch missing)"
mutate_footprint "$dir/docs/footprint.md" "$FOOTPRINT_ACCEPTED_CELL" '**REVIEW IN PROGRESS**'
run_check "$dir"
if [ "$GATE_RC" -eq 0 ]; then
  fail missing "a reworded acceptance row must abort, not infer a verdict"
elif ! grep -q "matches neither the pending nor the accepted anchor" "$dir/out.log"; then
  fail missing "the abort must say the row matches no anchor"
else
  pass missing "reworded row aborts fail-closed"
fi

# --- reopened: an accepted row AND a new pending row --------------------------------------
dir="$(make_scratch reopened)"
printf '\n| **Acceptance of the measured footprint (AC#3, reopened after remeasurement)** | **PENDING maintainer signature** |\n' \
  >>"$dir/docs/footprint.md"
run_check "$dir"
if [ "$GATE_RC" -eq 0 ]; then
  fail reopened "an accepted row plus a pending row must abort"
elif ! grep -q "BOTH a pending and an accepted" "$dir/out.log"; then
  fail reopened "the abort must name the contradictory evidence"
else
  pass reopened "reopened (pending + accepted) aborts fail-closed"
fi

# --- ambiguous: two different accepted rows -----------------------------------------------
dir="$(make_scratch ambiguous)"
mutate_footprint "$dir/docs/footprint.md" \
  "section 9.1 candidate c713e9a with the guava-free marginal" \
  "section 4 candidate other123 with a different record" 1
run_check "$dir"
if [ "$GATE_RC" -eq 0 ]; then
  fail ambiguous "two different accepted rows must abort"
elif ! grep -q "multiple different accepted rows" "$dir/out.log"; then
  fail ambiguous "the abort must name the ambiguity"
else
  pass ambiguous "ambiguous accepted rows abort fail-closed"
fi

# --- nodoc: the footprint doc itself is missing --------------------------------------------
dir="$(make_scratch nodoc)"
rm "$dir/docs/footprint.md"
run_check "$dir"
if [ "$GATE_RC" -eq 0 ]; then
  fail nodoc "a missing gate doc must abort"
elif ! grep -q "could not read the footprint acceptance gate status" "$dir/out.log"; then
  fail nodoc "the abort must name the unreadable doc"
else
  pass nodoc "missing gate doc aborts fail-closed"
fi

# No scratch state may have produced packaging output or a doc rewrite.
for state in pending missing reopened ambiguous nodoc; do
  if [ -e "$WORK/$state/target" ] || [ -e "$WORK/$state/docs/release-candidate.md.tmp" ]; then
    fail "$state" "the gate check must not write build output or touch the candidate doc"
  fi
done

echo
if [ "$FAILED" -ne 0 ]; then
  echo "RESULT release-gates-check.status=FAIL"
  exit 1
fi
echo "RESULT release-gates-check.status=PASS"
echo "release gate state matrix: current, pending, missing, reopened, ambiguous and nodoc behaved as specified"
