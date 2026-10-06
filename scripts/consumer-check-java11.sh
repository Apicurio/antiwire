#!/usr/bin/env bash
# Java 11 consumer smoke (TASK-2 AC#5, DEC-3).
#
# Compiles and runs scripts/consumer-placeholder on an ACTUAL Java 11 JVM (discovered below,
# separate from the JDK 17+ build toolchain) against the module jars. The consumer (still
# named PlaceholderConsumerMain for history) puts the jars on a Java 11 compile and runtime
# classpath and exercises ProtoWriter and the loading layer (FileSystem round trip). TASK-21
# checks the final published artifacts.
#
# JDK 11 discovery order: $JAVA11_HOME, $JAVA_HOME_11_X64 (set by a second actions/setup-java
# step in the CI build job), $JAVA_HOME when itself 11, then sdkman and common system
# locations. When no Java 11 is found the suite is reported NOT_RUN, never PASS; the CI
# build job always provisions Temurin 11 as JAVA_HOME_11_X64, so there the suite always
# runs, in the same job that runs Maven on JDK 17.
#
# Contract with scripts/verify.sh: prints RESULT lines consumed by the entry point.
set -uo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
check_modules_consistency

find_java11() {
  local candidates=()
  [ -n "${JAVA11_HOME:-}" ] && candidates+=("$JAVA11_HOME")
  [ -n "${JAVA_HOME_11_X64:-}" ] && candidates+=("$JAVA_HOME_11_X64")
  [ -n "${JAVA_HOME:-}" ] && candidates+=("$JAVA_HOME")
  local glob
  for glob in "$HOME"/.sdkman/candidates/java/11* /usr/lib/jvm/java-11* /usr/lib/jvm/*11*; do
    [ -d "$glob" ] && candidates+=("$glob")
  done
  local candidate
  for candidate in "${candidates[@]}"; do
    if [ -x "$candidate/bin/javac" ] && "$candidate/bin/javac" -version 2>&1 | grep -q 'javac 11\.'; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

JDK11="$(find_java11)" || JDK11=""
if [ -z "$JDK11" ]; then
  echo "java11-consumer NOT_RUN: no Java 11 toolchain found (set JAVA11_HOME or install one;"
  echo "the CI build job provisions Temurin 11 as JAVA_HOME_11_X64 and always runs this suite)."
  echo "RESULT java11-consumer.status=NOT_RUN"
  echo "RESULT java11-consumer.note=no local Java 11 toolchain found; CI build job provisions one"
  exit "$EXIT_NOT_RUN"
fi
JDK11_VERSION="$("$JDK11/bin/java" -version 2>&1 | head -n 1)"
echo "Java 11 toolchain: $JDK11 ($JDK11_VERSION)"

module_jars=()
missing_jar=0
for module in "${SHIPPED_MODULES[@]}"; do
  jar="$(find_module_jar "$module")"
  if [ -z "$jar" ]; then
    missing_jar=1
    break
  fi
  module_jars+=("$jar")
done

if [ "$missing_jar" -ne 0 ]; then
  echo "module jars missing; building them with the current JDK (requires 17+)"
  if ! mvn -B -ntp -f "$ROOT/pom.xml" -DskipTests package; then
    echo "java11-consumer NOT_RUN: module jars unavailable"
    echo "RESULT java11-consumer.status=NOT_RUN"
    echo "RESULT java11-consumer.note=module jars missing and fallback build failed"
    exit "$EXIT_NOT_RUN"
  fi
  module_jars=()
  for module in "${SHIPPED_MODULES[@]}"; do
    jar="$(find_module_jar "$module")"
    if [ -z "$jar" ]; then
      echo "java11-consumer NOT_RUN: module jar for $module still missing after the fallback build"
      echo "RESULT java11-consumer.status=NOT_RUN"
      echo "RESULT java11-consumer.note=module jar for $module missing even after the fallback build"
      exit "$EXIT_NOT_RUN"
    fi
    module_jars+=("$jar")
  done
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# shellcheck disable=SC2086
if ! "$JDK11/bin/javac" -d "$work" \
    "$ROOT"/scripts/consumer-placeholder/src/main/java/placeholder/*.java \
    -cp "$(IFS=:; echo "${module_jars[*]}")"; then
  echo "RESULT java11-consumer.status=FAIL"
  echo "RESULT java11-consumer.note=placeholder consumer did not compile on Java 11"
  exit "$EXIT_FAIL"
fi

cp_entries="$work$(IFS=:; echo ":${module_jars[*]}")"
output="$("$JDK11/bin/java" -cp "$cp_entries" placeholder.PlaceholderConsumerMain "${module_jars[@]}")" || {
  echo "$output"
  echo "RESULT java11-consumer.status=FAIL"
  echo "RESULT java11-consumer.note=placeholder consumer did not run on Java 11"
  exit "$EXIT_FAIL"
}
echo "$output"

if echo "$output" | grep -q '^placeholder-consumer-ok$' \
    && echo "$output" | grep -q '^spike-consumer-ok hex=960104030201$' \
    && echo "$output" | grep -q '^loading-consumer-ok existed=true read=proto$'; then
  echo "RESULT java11-consumer.status=PASS"
  echo "RESULT java11-consumer.note=consumer compiled and ran on $JDK11_VERSION against the module jars and exercised the real spike and loading surfaces (ProtoWriter deterministic bytes; FileSystem write/read/metadata/delete round trip)"
  exit "$EXIT_OK"
fi
echo "RESULT java11-consumer.status=FAIL"
echo "RESULT java11-consumer.note=placeholder consumer produced no confirmation line"
exit "$EXIT_FAIL"
