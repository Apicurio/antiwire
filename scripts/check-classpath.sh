#!/usr/bin/env bash
# Class-origin and duplicate-class check (TASK-2 AC#4).
#
# For every module, builds the port-under-test TEST classpath (the module's own
# target/classes and target/test-classes first, then the dependency classpath exported by
# maven-dependency-plugin to target/classpath-test.txt during mvn verify) and runs
# scripts/ClassOriginCheck.java over it: it reports where each retained-prefix class
# (config/retained-prefixes.txt, the single source; retained-name policy in
# docs/compatibility-matrix.md section B and G) loads from and fails when the same class
# name resolves from two different artifacts, printing both origins.
#
# Upstream isolation contract: pinned upstream jars used for fixture generation (TASK-5,
# TASK-6) must never sit on a port-under-test classpath. If one leaks in, the duplicate rule
# below is what catches it, because the port ships com.squareup.wire and retained okio names
# under its own coordinates only.
#
# Contract with scripts/verify.sh: prints RESULT lines consumed by the entry point.
set -uo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
check_modules_consistency

LOAD_LIST="$ROOT/config/class-origins.txt"
PREFIX_FILE="$ROOT/config/retained-prefixes.txt"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# The retained prefixes come from the single source config/retained-prefixes.txt; fail
# closed when the file is missing or yields no prefix, never scan with an empty policy.
if [ ! -f "$PREFIX_FILE" ]; then
  echo "FATAL: retained-prefix list missing: $PREFIX_FILE"
  exit "$EXIT_FAIL"
fi
prefix_args=()
while IFS= read -r line || [ -n "$line" ]; do
  line="${line#"${line%%[![:space:]]*}"}"
  line="${line%"${line##*[![:space:]]}"}"
  [ -z "$line" ] && continue
  case "$line" in \#*) continue ;; esac
  prefix_args+=(--prefix "$line")
done < "$PREFIX_FILE"
if [ "${#prefix_args[@]}" -eq 0 ]; then
  echo "FATAL: retained-prefix list empty after stripping comments: $PREFIX_FILE"
  exit "$EXIT_FAIL"
fi

rc="$EXIT_OK"
missing=0
for module in "${SHIPPED_MODULES[@]}"; do
  cp_file="$ROOT/$module/target/classpath-test.txt"
  if [ ! -f "$cp_file" ]; then
    echo "MISSING classpath export for $module: $cp_file (run mvn verify first)"
    missing=1
    continue
  fi
  # Own output first, matching surefire's classpath order, so the port under test wins.
  printf '%s:%s:%s\n' \
    "$ROOT/$module/target/classes" \
    "$ROOT/$module/target/test-classes" \
    "$(cat "$cp_file")" > "$WORK/$module.cp"
  java "$ROOT/scripts/ClassOriginCheck.java" \
    --label "$module" \
    --classpath-file "$WORK/$module.cp" \
    "${prefix_args[@]}" \
    --load-list "$LOAD_LIST" || rc="$EXIT_FAIL"
done

if [ "$missing" -ne 0 ]; then
  echo "RESULT duplicate-class-check.status=NOT_RUN"
  echo "RESULT duplicate-class-check.note=classpath exports missing; run mvn verify first"
  exit "$EXIT_NOT_RUN"
elif [ "$rc" -eq "$EXIT_OK" ]; then
  echo "RESULT duplicate-class-check.status=PASS"
  echo "RESULT duplicate-class-check.note=no com.squareup.wire or okio class name resolves from two artifacts on any module test classpath; origins printed above"
else
  echo "RESULT duplicate-class-check.status=FAIL"
  echo "RESULT duplicate-class-check.note=duplicate class name or origin failure, see output above"
fi
exit "$rc"
