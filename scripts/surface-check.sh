#!/usr/bin/env bash
# TASK-34: source-compatibility surface check against the real Wire 7.1.0 jars.
# Wraps scripts/surface-check.py for scripts/verify.sh (verdict = printed RESULT status AND exit
# code). Runs after a green mvn verify: it reads the packaged module jars.
#
# Compatibility target: source compatibility for members Java can express without Kotlin types
# (DEC-4) and without okio in the signature (DEC-14); binary compatibility is not promised (DEC-2).
# Ledger: config/surface-baseline.tsv. Regenerate after a reviewed change with
#   scripts/surface-check.sh --update [--allow-new]
set -uo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
check_modules_consistency

update_args=()
for a in "$@"; do
  case "$a" in
    --update|--allow-new|--report) update_args+=("$a") ;;
    *) echo "usage: scripts/surface-check.sh [--update [--allow-new]] [--report]" >&2; exit "$EXIT_FAIL" ;;
  esac
done

jar_args=()
for module in "${SHIPPED_MODULES[@]}"; do
  jar="$(find_module_jar "$module")"
  if [ -z "$jar" ]; then
    echo "MISSING module jar for $module (run mvn verify first)"
    echo "RESULT surface-check.status=NOT_RUN"
    echo "RESULT surface-check.note=module jars missing; run mvn verify first"
    exit "$EXIT_NOT_RUN"
  fi
  jar_args+=(--port-jar "$jar")
done

# The Kotlin `internal` classes are read from the pinned upstream sources.
"$ROOT/scripts/fetch-upstream.sh" >/dev/null 2>&1 || {
  echo "RESULT surface-check.status=NOT_RUN"
  echo "RESULT surface-check.note=the pinned upstream sources are unavailable (scripts/fetch-upstream.sh failed)"
  exit "$EXIT_NOT_RUN"
}

out="$(python3 "$ROOT/scripts/surface-check.py" "${jar_args[@]}" ${update_args[@]+"${update_args[@]}"} 2>&1)"
rc=$?
printf '%s\n' "$out"
summary="$(printf '%s\n' "$out" | grep -E '^(SUMMARY|Surface report)' | head -n 1)"

case "$rc" in
  "$EXIT_OK")
    echo "RESULT surface-check.status=PASS"
    echo "RESULT surface-check.note=${summary:-surface unchanged}; ledger config/surface-baseline.tsv"
    ;;
  "$EXIT_NOT_RUN")
    echo "RESULT surface-check.status=NOT_RUN"
    echo "RESULT surface-check.note=could not run (upstream jars unavailable or tool missing); see output above"
    ;;
  *)
    echo "RESULT surface-check.status=FAIL"
    echo "RESULT surface-check.note=${summary:-surface differs from the baseline}; see output above"
    ;;
esac
exit "$rc"
