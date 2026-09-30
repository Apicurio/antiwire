#!/usr/bin/env bash
# Java 11 bytecode gate for production jars (TASK-2 AC#3, DEC-3).
#
# Checks the module jars plus every production-scope dependency jar found in the exported
# runtime classpaths (target/classpath-runtime.txt) with scripts/Java11BytecodeCheck.java:
# every class file a Java 11 JVM would select (multi-release aware) must have major version
# <= 55. Own sources are additionally pinned by --release 11 at compile time; dependency API
# usage is exercised at runtime by scripts/consumer-check-java11.sh, which TASK-5 extends
# beyond the placeholder.
#
# Honesty rule: when the production dependency set is empty the tool says so explicitly
# instead of implying a verified set. TASK-4 brings the first dependency candidates.
#
# Contract with scripts/verify.sh: prints RESULT lines consumed by the entry point.
set -uo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
check_modules_consistency

args=()
for module in "${MODULES[@]}"; do
  jar="$(find_module_jar "$module")"
  if [ -z "$jar" ]; then
    echo "MISSING module jar for $module (run mvn verify first)"
    echo "RESULT bytecode-java11.status=NOT_RUN"
    echo "RESULT bytecode-java11.note=module jars missing; run mvn verify first"
    exit "$EXIT_NOT_RUN"
  fi
  args+=(--jar "$jar")
  cp_file="$ROOT/$module/target/classpath-runtime.txt"
  if [ ! -f "$cp_file" ]; then
    echo "MISSING runtime classpath export for $module: $cp_file (run mvn verify first)"
    echo "RESULT bytecode-java11.status=NOT_RUN"
    echo "RESULT bytecode-java11.note=classpath exports missing; run mvn verify first"
    exit "$EXIT_NOT_RUN"
  fi
  args+=(--dep-cp "$cp_file")
done

rc="$EXIT_OK"
java "$ROOT/scripts/Java11BytecodeCheck.java" "${args[@]}" || rc="$EXIT_FAIL"

if [ "$rc" -eq "$EXIT_OK" ]; then
  echo "RESULT bytecode-java11.status=PASS"
  echo "RESULT bytecode-java11.note=all classes selectable by Java 11 in module jars and production dependency jars have major <= 55"
else
  echo "RESULT bytecode-java11.status=FAIL"
  echo "RESULT bytecode-java11.note=class file above major 55 found, see violations above"
fi
exit "$rc"
