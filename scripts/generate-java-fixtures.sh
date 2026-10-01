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
fetch() {
  curl -sL -o "$2" "https://repo1.maven.org/maven2/$1"
}
fetch com/squareup/wire/wire-compiler/7.1.0/wire-compiler-7.1.0.jar wire-compiler.jar
fetch com/squareup/wire/wire-schema-jvm/7.1.0/wire-schema-jvm-7.1.0.jar wire-schema.jar
fetch com/squareup/wire/wire-runtime-jvm/7.1.0/wire-runtime-jvm-7.1.0.jar wire-runtime.jar
fetch com/squareup/wire/wire-kotlin-generator/7.1.0/wire-kotlin-generator-7.1.0.jar wire-kotlin-generator.jar
fetch com/squareup/wire/wire-java-generator/7.1.0/wire-java-generator-7.1.0.jar wire-java-generator.jar
fetch com/squareup/wire/wire-swift-generator/7.1.0/wire-swift-generator-7.1.0.jar wire-swift-generator.jar
fetch com/squareup/wire/wire-grpc-client-jvm/7.1.0/wire-grpc-client-jvm-7.1.0.jar wire-grpc-client.jar
fetch com/squareup/kotlinpoet/kotlinpoet-jvm/2.3.0/kotlinpoet-jvm-2.3.0.jar kotlinpoet.jar
fetch com/squareup/okio/okio-jvm/3.18.2/okio-jvm-3.18.2.jar okio.jar
fetch org/jetbrains/kotlin/kotlin-stdlib/2.0.21/kotlin-stdlib-2.0.21.jar kotlin-stdlib.jar
fetch com/google/guava/guava/33.7.1-jre/guava-33.7.1-jre.jar guava.jar
fetch com/palantir/javapoet/javapoet/0.19.0/javapoet-0.19.0.jar javapoet.jar
fetch com/google/guava/failureaccess/1.0.3/failureaccess-1.0.3.jar failureaccess.jar

rm -rf "$OUT_DIR"/com "$OUT_DIR"/squareup
mkdir -p "$OUT_DIR"
java -cp "wire-compiler.jar:wire-schema.jar:wire-runtime.jar:wire-kotlin-generator.jar:wire-java-generator.jar:wire-swift-generator.jar:wire-grpc-client.jar:kotlinpoet.jar:okio.jar:kotlin-stdlib.jar:guava.jar:javapoet.jar:failureaccess.jar" \
  com.squareup.wire.WireCompiler \
  --proto_path="$WIRE_CLONE/wire-tests/fixtures/proto/java" \
  --java_out="$OUT_DIR"

echo "Generated $(find "$OUT_DIR" -name '*.java' | wc -l | tr -d ' ') Java files into $OUT_DIR"
