#!/usr/bin/env bash
# Regenerates wire-tests-java's generated Java protos with the PINNED upstream compiler
# (com.squareup.wire:wire-compiler 7.1.0) as an isolated build-time fixture tool (DEC-5;
# TASK-9 AC#3: fixture generation avoids any dependency on the later ported generator).
#
# Usage: scripts/generate-java-fixtures.sh [path-to-wire-clone] [output-dir]
# The wire clone defaults to a checkout of square/wire at tag 7.1.0. Nothing in this
# script's output enters production scope: the generated sources compile in the
# wire-tests-java module only, against the port's runtime, with enforcer enforcement on.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WIRE_CLONE="${1:-/tmp/wire}"
OUT_DIR="${2:-$ROOT/wire-tests-java/src/main/java}"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

cd "$WORK"
# shellcheck source=scripts/lib.sh
. "$ROOT/scripts/lib.sh"
WIRE_CP="$(fetch_wire_compiler_jars "$WORK")"

rm -rf "$OUT_DIR"/com "$OUT_DIR"/squareup
mkdir -p "$OUT_DIR"
java -cp "$WIRE_CP" com.squareup.wire.WireCompiler \
  --proto_path="$WIRE_CLONE/wire-tests/fixtures/proto/java" \
  --java_out="$OUT_DIR"

echo "Generated $(find "$OUT_DIR" -name '*.java' | wc -l | tr -d ' ') Java files into $OUT_DIR"
