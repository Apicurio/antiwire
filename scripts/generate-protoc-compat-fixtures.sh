#!/usr/bin/env bash
# TASK-15: regenerate wire-protoc-compat-java's fixtures with the PINNED tools:
#   - protoc 4.36.1 (com.google.protobuf:protoc, checksummed by scripts/install-protoc.sh)
#     generates the reference protobuf-java models;
#   - the pinned upstream wire-compiler 7.1.0 generates the wire JAVA models
#     (squareup.proto2.java.*, squareup.proto3.java.*) from the same protos.
# Kotlin-model fixture packages (squareup.*.kotlin.*) and the upstream module's
# gRPC/gson/moshi surfaces are DEC-6 exclusions and are not generated here.
#
# Usage: scripts/generate-protoc-compat-fixtures.sh [path-to-wire-clone]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WIRE_CLONE="${1:-/tmp/wire}"
SRC="$WIRE_CLONE/wire-protoc-compatibility-tests/src/main/proto"
MODULE="$ROOT/wire-protoc-compat-java"
WIRE_OUT="$MODULE/src/main/java"

PROTEXE="$("$ROOT/scripts/install-protoc.sh")"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

cd "$WORK"
# shellcheck source=scripts/lib.sh
. "$ROOT/scripts/lib.sh"
WIRE_CP="$(fetch_wire_compiler_jars "$WORK")"

# The well-known protos (descriptor.proto et al.) ride in the wire-schema jar's resources,
# exactly where upstream's CoreLoader serves them from.
unzip -o -q wire-schema.jar "google/protobuf/*.proto" -d well-known

# all_empty.proto is excluded: upstream's wire Java generator models google.protobuf.Empty
# as kotlin.Unit, which would drag kotlin-stdlib into this module's compile scope (banned by
# DEC-4/enforcer); the Empty divergence itself is TASK-26. 1 proto, applied to both sides.
JAVA_PROTOS="$(find "$SRC/squareup/proto2/java" "$SRC/squareup/proto3/java" \
    -name '*.proto' ! -name 'all_empty.proto' | tr '\n' ' ')"

rm -rf "$MODULE/src/main/java"
mkdir -p "$MODULE/src/main/java"

# Reference models: protoc 4.36.1.
"$PROTEXE" --proto_path="$SRC" --proto_path="$WORK/well-known" \
  --java_out="$MODULE/src/main/java" $JAVA_PROTOS

# Wire models: the pinned upstream compiler, java generator, java-package includes only.
java -cp "$WIRE_CP" com.squareup.wire.WireCompiler \
  --proto_path="$SRC" \
  --proto_path="$WORK/well-known" \
  --proto_path="$WIRE_CLONE/wire-protoc-compatibility-tests/src/main/proto/protos.jar" \
  --includes="squareup.proto2.java.*,squareup.proto3.java.*" \
  --excludes="squareup.proto3.java.alltypes.AllEmpty" \
  --java_out="$WIRE_OUT"

echo "Generated $(find "$MODULE/src/main/java" -name '*.java' | wc -l | tr -d ' ') fixture classes into $MODULE/src/main/java"
