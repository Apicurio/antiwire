#!/usr/bin/env bash
# TASK-23: upstream watch for the pinned square/wire tag. Procedure: docs/upstream-sync.md.
#
# Compares the newest tag on the upstream remote (git ls-remote; no clone needed) with the
# pin in config/parity-pins.json and reports whether the port is behind. GitHub advisories
# cannot be fetched without auth, so detection is tag-based and the advisory review is the
# owned weekly manual checklist in docs/upstream-sync.md; the security inventory
# (docs/security-regression-inventory.md) deliberately includes fixes no advisory covers,
# so the watch must never assume an advisory count is exhaustive.
#
# Modes:
#   default          strict signal for humans and cron: exit 0 only when state=CURRENT and
#                    nothing about the snapshot needs manual review.
#   --security       strict plus the security posture: the report frames any new tag as a
#                    pending triage against the security inventory and prints the manual
#                    advisory checklist.
#   --informational  the verify.sh integration: prints RESULT lines where PASS means "the
#                    watch ran and reported" and the note carries state=... . BEHIND and
#                    PIN_MOVED stay PASS here, because an upstream release must not fail
#                    the build; only a watch that could not run (network, malformed pins)
#                    prints NOT_RUN, which the entry point records as MISSING and fails.
#
# States: CURRENT (no tag newer than the pin), BEHIND (at least one), PIN_MOVED (the pinned
# tag is absent from the remote or no longer resolves to the pinned tag object and commit:
# upstream history rewrite; provenance evidence is invalidated).
#
# Exit codes: 0 CURRENT with a clean snapshot (informational: any reported state); 1 BEHIND,
# or a CURRENT whose snapshot needs review (unparsed tag names or unparsable ls-remote
# lines; either could hide a newer release); 2 PIN_MOVED; 3 the watch could not run
# (ls-remote failed, empty snapshot, pins malformed); usage errors exit 1 with a usage line.
#
# Testing override: ANTIWIRE_PINS replaces the pins file (used by the failure-mode drills
# recorded in docs/upstream-sync.md); the repository URL always comes from the pins file.
set -uo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
check_modules_consistency

PINS="${ANTIWIRE_PINS:-$ROOT/config/parity-pins.json}"

INFORMATIONAL=0
SECURITY=0
for arg in "$@"; do
  case "$arg" in
    --informational) INFORMATIONAL=1 ;;
    --security) SECURITY=1 ;;
    *)
      echo "usage: $0 [--informational | --security]" >&2
      exit "$EXIT_FAIL"
      ;;
  esac
done
if [ "$INFORMATIONAL" -eq 1 ] && [ "$SECURITY" -eq 1 ]; then
  echo "usage: $0 [--informational | --security] (not both)" >&2
  exit "$EXIT_FAIL"
fi

# The pins file is the single source of upstream identity. Every parse failure is a
# could-not-run outcome (exit 3 / NOT_RUN), never a silent CURRENT.
PINS_LINE="$(python3 - "$PINS" <<'PY'
import json, sys
try:
    pins = json.load(open(sys.argv[1]))
except Exception as e:
    print("ERROR", "unreadable pins file %s: %s" % (sys.argv[1], e))
    raise SystemExit(0)
try:
    u = pins["upstream"]
    print("OK", u["repository"], u["tag"], u["annotated_tag_object"], u["resolved_commit"])
except (KeyError, TypeError) as e:
    print("ERROR", "pins file %s lacks an upstream identity field: %s" % (sys.argv[1], e))
PY
)" || PINS_LINE=""
case "$PINS_LINE" in
  "ERROR "*)
    echo "upstream watch: could not read the pin: ${PINS_LINE#ERROR }"
    if [ "$INFORMATIONAL" -eq 1 ]; then
      echo "RESULT upstream-watch.status=NOT_RUN"
      echo "RESULT upstream-watch.note=${PINS_LINE#ERROR }"
    fi
    exit "$EXIT_NOT_RUN"
    ;;
  "OK "*) read -r _ REPO PIN_TAG PIN_OBJECT PIN_COMMIT <<<"$PINS_LINE" ;;
  *)
    echo "upstream watch: could not read the pin: python3 produced no verdict for $PINS"
    if [ "$INFORMATIONAL" -eq 1 ]; then
      echo "RESULT upstream-watch.status=NOT_RUN"
      echo "RESULT upstream-watch.note=python3 produced no pins verdict for $PINS"
    fi
    exit "$EXIT_NOT_RUN"
    ;;
esac

TAGS_FILE="$(mktemp)"
LS_ERR_FILE="$(mktemp)"
trap 'rm -f "$TAGS_FILE" "$LS_ERR_FILE"' EXIT

# One network round trip; every verdict below is derived from this snapshot. The
# lowSpeed limits bound a blackholed connection (git's HTTPS transport has no default
# stall timeout, so without them a dropped route hangs the watch, and verify.sh with it).
if ! git -c http.lowSpeedLimit=1024 -c http.lowSpeedTime=30 ls-remote --tags "$REPO" \
    >"$TAGS_FILE" 2>"$LS_ERR_FILE"; then
  echo "upstream watch: git ls-remote --tags $REPO failed:"
  sed 's/^/  /' "$LS_ERR_FILE" | tail -n 5
  if [ "$INFORMATIONAL" -eq 1 ]; then
    echo "RESULT upstream-watch.status=NOT_RUN"
    echo "RESULT upstream-watch.note=git ls-remote --tags $REPO failed; see output above"
  fi
  exit "$EXIT_NOT_RUN"
fi

# Verdict computation in one python pass over the tags snapshot: ordering is numeric per
# component with pre-release markers (alpha < beta < rc < unknown marker) sorting before
# the final release of the same core, so 7.0.0-RC01 < 7.0.0 < 7.0.1 < 7.1.0 and a
# hypothetical 7.1.0-rc1 is NOT newer than the pinned final 7.1.0. Historical parent-* tags
# are build tags, not releases, and are excluded by name; any other unparsed name is
# reported for manual review instead of being dropped silently.
VERDICT="$(python3 - "$PINS" "$TAGS_FILE" <<'PY'
import json, re, sys

pins = json.load(open(sys.argv[1]))
pin_tag = pins["upstream"]["tag"]
pin_object = pins["upstream"]["annotated_tag_object"]
pin_commit = pins["upstream"]["resolved_commit"]

tag_pat = re.compile(r"^(?:wire-)?(\d+)\.(\d+)\.(\d+)(?:-(.+))?$", re.IGNORECASE)
known_pat = re.compile(r"^(alpha|beta|rc)(\d+)$", re.IGNORECASE)

def version_key(tag):
    m = tag_pat.match(tag)
    if m is None:
        return None
    core = tuple(int(x) for x in m.group(1, 2, 3))
    suffix = m.group(4)
    if suffix is None:
        return core + (1, 0, 0, "")  # final release sorts above any pre-release
    k = known_pat.match(suffix)
    if k:
        rank = {"alpha": 0, "beta": 1, "rc": 2}[k.group(1).lower()]
        return core + (0, rank, int(k.group(2)), "")
    # Unknown marker (e.g. the historical 5.4.1-alpha.20250925... snapshot tags): order it
    # above known pre-releases of the same core so an unfamiliar scheme errs toward BEHIND.
    return core + (0, 3, 0, suffix)

remote_object = {}   # tag -> hash of refs/tags/<tag> (annotated tag object, or commit)
remote_commit = {}   # tag -> hash of refs/tags/<tag>^{} (peeled commit, annotated only)
skipped_lines = 0    # lines that are not exactly "<hash> <ref>"; reported, never silent
for line in open(sys.argv[2]):
    fields = line.split()
    if len(fields) != 2:
        if line.strip():
            skipped_lines += 1
        continue
    ref = fields[1]
    if not ref.startswith("refs/tags/"):
        continue
    name = ref[len("refs/tags/"):]
    if name.endswith("^{}"):
        remote_commit[name[:-3]] = fields[0]
    else:
        remote_object[name] = fields[0]

if not remote_object:
    # A successful ls-remote that lists zero tags is a broken fetch (proxy, redirect),
    # not an upstream that deleted every release; refuse to turn it into a verdict.
    print("state=EMPTY")
    print("detail=ls-remote succeeded but returned no tags for this repository")
    raise SystemExit(0)

unparsed = sorted(t for t in remote_object
                  if not t.startswith("parent-") and version_key(t) is None)
parsed = sorted((version_key(t), t) for t in remote_object if version_key(t) is not None)

pin_key = version_key(pin_tag)
if pin_key is None:
    print("state=ERROR")
    print("detail=the pinned tag %s does not parse as a release tag" % pin_tag)
    raise SystemExit(0)

newer = [(k, t) for k, t in parsed if k > pin_key]
newer.sort(reverse=True)
newest = parsed[-1][1] if parsed else "-"

integrity = "ok"
if pin_tag not in remote_object:
    integrity = "absent: the pinned tag %s is not on the remote" % pin_tag
elif remote_object[pin_tag] != pin_object:
    integrity = ("tag-object mismatch: remote refs/tags/%s is %s, the pin expects %s"
                 % (pin_tag, remote_object[pin_tag], pin_object))
elif pin_tag in remote_commit and remote_commit[pin_tag] != pin_commit:
    integrity = ("commit mismatch: remote refs/tags/%s^{} resolves to %s, the pin expects %s"
                 % (pin_tag, remote_commit[pin_tag], pin_commit))

if integrity != "ok":
    state = "PIN_MOVED"
elif newer:
    state = "BEHIND"
else:
    state = "CURRENT"

def out(key, value):
    print("%s=%s" % (key, value))

out("state", state)
out("pin", pin_tag)
out("newest", newest)
out("newer_count", str(len(newer)))
out("newer", " ".join(t for _, t in newer))
out("tag_total", str(len(parsed)))
out("parent_tags_ignored", str(sum(1 for t in remote_object if t.startswith("parent-"))))
out("unparsed", " ".join(unparsed))
out("skipped_lines", str(skipped_lines))
out("integrity", integrity)
PY
)" || VERDICT=""
if [ -z "$VERDICT" ] || ! grep -q "^state=" <<<"$VERDICT"; then
  echo "upstream watch: could not compute a verdict from the ls-remote snapshot"
  if [ "$INFORMATIONAL" -eq 1 ]; then
    echo "RESULT upstream-watch.status=NOT_RUN"
    echo "RESULT upstream-watch.note=verdict computation failed on the ls-remote snapshot for $REPO"
  fi
  exit "$EXIT_NOT_RUN"
fi
get() { grep -m1 "^$1=" <<<"$VERDICT" | cut -d= -f2-; }

STATE="$(get state)"
if [ "$STATE" = "ERROR" ] || [ "$STATE" = "EMPTY" ]; then
  DETAIL="$(get detail)"
  echo "upstream watch: could not compute a verdict: ${DETAIL:-no detail recorded}"
  if [ "$INFORMATIONAL" -eq 1 ]; then
    echo "RESULT upstream-watch.status=NOT_RUN"
    echo "RESULT upstream-watch.note=${DETAIL:-verdict computation failed}"
  fi
  exit "$EXIT_NOT_RUN"
fi
PIN="$(get pin)"
NEWEST="$(get newest)"
NEWER_COUNT="$(get newer_count)"
NEWER="$(get newer)"
TAG_TOTAL="$(get tag_total)"
PARENT_IGNORED="$(get parent_tags_ignored)"
UNPARSED="$(get unparsed)"
SKIPPED="$(get skipped_lines)"
INTEGRITY="$(get integrity)"

echo "upstream watch: $REPO (pin from $PINS)"
echo "  pin: $PIN (tag object $PIN_OBJECT, commit $PIN_COMMIT)"
echo "  remote tags parsed: $TAG_TOTAL (${PARENT_IGNORED} non-release parent-* tags ignored)"
echo "  newest upstream tag: $NEWEST"
if [ "$NEWER_COUNT" -gt 0 ]; then
  echo "  tags newer than the pin ($NEWER_COUNT): ${NEWER// /,}"
else
  echo "  tags newer than the pin: none"
fi
echo "  pin integrity: $INTEGRITY"
if [ -n "$UNPARSED" ]; then
  echo "  WARNING: tag names that did not parse (review manually; a new naming scheme could"
  echo "           hide a newer release): ${UNPARSED// /,}"
fi
if [ "$SKIPPED" != "0" ]; then
  echo "  WARNING: $SKIPPED ls-remote line(s) were not <hash> <ref> pairs and were ignored;"
  echo "           review the snapshot manually"
fi
echo "  state: $STATE"

if [ "$SECURITY" -eq 1 ]; then
  echo
  if [ "$NEWER_COUNT" -gt 0 ]; then
    echo "  security posture: any tag newer than the pin is a pending security triage until"
    echo "  proven otherwise (docs/security-regression-inventory.md includes fixes no advisory"
    echo "  covers). Triage the new log range:"
    echo "    git -C <clone> log --oneline $PIN..$NEWEST"
    echo "    git -C <clone> log $PIN..$NEWEST --grep='negative\|skipGroup\|limit\|recursion\|recursive\|overflow\|escape\|valid\|sanitiz\|merge\|GHSA\|CVE' -i"
  fi
  echo "  Manual advisory checklist (weekly, owner and cadence in docs/upstream-sync.md):"
  echo "    https://github.com/square/wire/security/advisories"
fi

case "$STATE" in
  CURRENT)
    NOTE="state=CURRENT: pin $PIN is the newest upstream tag ($TAG_TOTAL tags parsed); procedure docs/upstream-sync.md"
    EXIT_CODE="$EXIT_OK"
    ;;
  BEHIND)
    NOTE="state=BEHIND: newest upstream tag $NEWEST is newer than pin $PIN ($NEWER_COUNT newer tag(s): ${NEWER// /,}); start the docs/upstream-sync.md triage"
    EXIT_CODE=1
    ;;
  PIN_MOVED)
    NOTE="state=PIN_MOVED: pin integrity check failed ($INTEGRITY); upstream provenance for the pin is invalidated, see docs/upstream-sync.md"
    EXIT_CODE=2
    ;;
  *)
    # Defensive: every state the python side can emit is handled above. An unknown state
    # is a watch failure, not a reported state, so informational mode must not claim PASS.
    echo "upstream watch: unrecognized state from the verdict computation: $STATE"
    if [ "$INFORMATIONAL" -eq 1 ]; then
      echo "RESULT upstream-watch.status=NOT_RUN"
      echo "RESULT upstream-watch.note=unrecognized state from the verdict computation: $STATE"
    fi
    exit "$EXIT_NOT_RUN"
    ;;
esac
if [ -n "$UNPARSED" ]; then
  NOTE="$NOTE; unparsed tag names present (review manually)"
fi
if [ "$SKIPPED" != "0" ]; then
  NOTE="$NOTE; $SKIPPED unparsable ls-remote line(s) ignored"
fi

if [ "$INFORMATIONAL" -eq 1 ]; then
  # The suite's gate is "the watch ran and reported", never "upstream did not release":
  # every state the watch could report is a PASS with the state in the note. Only the
  # could-not-run paths above (NOT_RUN + exit 3) fail, as MISSING, like any other suite.
  echo "RESULT upstream-watch.status=PASS"
  echo "RESULT upstream-watch.note=$NOTE; this PASS records that the watch ran, not that the port is current"
  exit "$EXIT_OK"
fi

[ "$STATE" = "CURRENT" ] || echo "$NOTE"
# Conservative in every strict mode (default cron run or --security): an unparsed tag
# name could hide a newer release, so it must never read as a clean CURRENT.
if [ -n "$UNPARSED" ] || [ "$SKIPPED" != "0" ]; then
  if [ "$EXIT_CODE" -eq 0 ]; then
    EXIT_CODE=1
  fi
fi
exit "$EXIT_CODE"
