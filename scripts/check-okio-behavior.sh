#!/usr/bin/env bash
# Okio behavior suite check (TASK-4 AC#3, DEC-5).
#
# Consumes the surefire XML reports produced by the mvn verify run of the entry point and
# verifies three things:
#  1. the port-side adapted okio behavior tests (shared files plus the port-only
#     construction and internals files and the loading demonstration) all passed in
#     wire-runtime-java;
#  2. the SAME shared files ran and passed against upstream okio-jvm 3.18.2 in the
#     isolated oracle module okio-behavior-oracle (test scope only, never a
#     port-under-test classpath);
#  3. both sides ran the same number of shared cases (ByteStringTest + BufferTest +
#     PathTest + FileSystemTest), so
#     the oracle is not silently running fewer behaviors than the port.
#
# Contract with scripts/verify.sh: prints RESULT lines consumed by the entry point.
set -uo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
check_modules_consistency

count_report() { # <module> <test-class-simple-name>
  local module="$1" class="$2" file
  file="$ROOT/$module/target/surefire-reports/TEST-okio.$class.xml"
  if [ ! -f "$file" ]; then
    echo "FATAL: missing surefire report $file"
    return "$EXIT_FAIL"
  fi
  python3 - "$file" <<'PY'
import sys
import xml.etree.ElementTree as ElementTree

testsuite = ElementTree.parse(sys.argv[1]).getroot()
failures = int(testsuite.get("failures", "0"))
errors = int(testsuite.get("errors", "0"))
skipped = int(testsuite.get("skipped", "0"))
tests = int(testsuite.get("tests", "0"))
if failures or errors or tests == 0:
    print("BAD %d %d" % (failures, errors))
else:
    print("OK %d %d" % (tests, skipped))
PY
}

rc="$EXIT_OK"
port_shared_total=0
oracle_shared_total=0

for class in ByteStringTest BufferTest PathTest FileSystemTest; do
  port="$(count_report wire-runtime-java "$class")"
  oracle="$(count_report okio-behavior-oracle "$class")"
  if [[ "$port" != OK* || "$oracle" != OK* ]]; then
    echo "FAIL: okio.$class not green on the port ($port) or the oracle ($oracle)"
    rc="$EXIT_FAIL"
    continue
  fi
  port_count="${port#OK }"; port_count="${port_count%% *}"
  oracle_count="${oracle#OK }"; oracle_count="${oracle_count%% *}"
  port_shared_total=$((port_shared_total + port_count))
  oracle_shared_total=$((oracle_shared_total + oracle_count))
  echo "okio.$class: port $port_count cases, oracle $oracle_count cases"
done

for class in ByteStringConstructionTest BufferInternalsTest SchemaSourceLoadingTest; do
  port="$(count_report wire-runtime-java "$class")"
  if [[ "$port" != OK* ]]; then
    echo "FAIL: okio.$class not green on the port ($port)"
    rc="$EXIT_FAIL"
  else
    port_count="${port#OK }"; port_count="${port_count%% *}"
    echo "okio.$class (port-only): port $port_count cases"
  fi
done

if [ "$rc" -eq "$EXIT_OK" ]; then
  if [ "$port_shared_total" -ne "$oracle_shared_total" ]; then
    echo "FAIL: shared case counts differ: port=$port_shared_total oracle=$oracle_shared_total"
    echo "RESULT okio-behavior.status=FAIL"
    echo "RESULT okio-behavior.note=shared okio behavior cases differ between port and oracle"
    exit "$EXIT_FAIL"
  fi
  echo "RESULT okio-behavior.status=PASS"
  echo "RESULT okio-behavior.note=$port_shared_total shared okio behavior cases green on the port AND on upstream okio-jvm 3.18.2 in the isolated oracle; port-only construction/internals/loading cases green"
  exit "$EXIT_OK"
fi
echo "RESULT okio-behavior.status=FAIL"
echo "RESULT okio-behavior.note=okio behavior tests failed or missing, see output above"
exit "$rc"
