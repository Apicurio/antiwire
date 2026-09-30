#!/usr/bin/env bash
# Shared library for the antiwire scripts (TASK-2 build tooling).
#
# Single source of truth for the repo root, the module list, module jar discovery and the
# exit-code contract. Every consumer script sources this file and runs
# check_modules_consistency at startup, so a module added to one place but not the other
# fails loudly at the top of the run instead of silently checking the wrong set.

# Repo root, resolved from this file's own location so the scripts work from any cwd.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# The build modules, defined once here; the parent pom.xml <modules> block must match
# (enforced by check_modules_consistency, which every consumer script runs at startup).
MODULES=(wire-runtime-java wire-schema-java wire-java-generator)

# A check ran and passed.
# shellcheck disable=SC2034  # consumed by the scripts that source this file
EXIT_OK=0
# A check ran and failed, or a precondition is broken.
# shellcheck disable=SC2034  # consumed by the scripts that source this file
EXIT_FAIL=1
# A suite could not run at all: missing toolchain or missing build artifacts.
# shellcheck disable=SC2034  # consumed by the scripts that source this file
EXIT_NOT_RUN=3

# Fail loudly (clear message, nonzero exit) when the <modules> block of the parent pom.xml
# and the MODULES list above disagree.
check_modules_consistency() {
  local pom_modules expected actual
  pom_modules="$(sed -n '/<modules>/,/<\/modules>/p' "$ROOT/pom.xml" \
    | sed -n 's:.*<module>\(.*\)</module>.*:\1:p' | sed '/^[[:space:]]*$/d')"
  expected="$(printf '%s\n' "${MODULES[@]}" | sort)"
  actual="$(printf '%s\n' "$pom_modules" | sort)"
  if [ "$expected" != "$actual" ]; then
    {
      echo "FATAL: module list mismatch between scripts/lib.sh and $ROOT/pom.xml"
      echo "  scripts/lib.sh MODULES:"
      printf '    %s\n' "${MODULES[@]}"
      echo "  $ROOT/pom.xml <modules> block:"
      printf '    %s\n' "$pom_modules"
    } >&2
    exit "$EXIT_FAIL"
  fi
}

# Print the parent pom's project version, read from the <version> directly under <project>
# (the modules inherit it, so target/<module>-<version>.jar is the exact artifact name).
# Same structural read as check_modules_consistency above: a sed pass over the parent pom,
# failing closed when the element cannot be found.
project_version() {
  local version
  version="$(sed -n 's:^  <version>\(.*\)</version>[[:space:]]*$:\1:p' "$ROOT/pom.xml" \
    | sed '/^[[:space:]]*$/d' | head -n 1)"
  if [ -z "$version" ]; then
    echo "FATAL: could not read the project <version> directly under <project> in $ROOT/pom.xml" >&2
    return "$EXIT_FAIL"
  fi
  printf '%s\n' "$version"
}

# Print the module's packaged jar path, exactly target/<module>-<project version>.jar. The
# jar is resolved against the parent pom's version instead of picking the first
# target/<module>-*.jar alphabetically: after a version bump without mvn clean the old
# version's jar still sits in target/ and an alphabetical glob would hand out the stale
# artifact. Fail closed when the exact jar is absent or unexpected jars shadow it; prints
# nothing on stdout plus a stderr FATAL in those cases, and callers must handle the empty
# result explicitly (they report NOT_RUN).
find_module_jar() {
  local module="$1" version jar
  version="$(project_version)" || return "$EXIT_FAIL"
  local exact="$ROOT/$module/target/$module-$version.jar"
  local -a candidates=()
  for jar in "$ROOT/$module/target/$module"-*.jar; do
    [ -f "$jar" ] || continue
    case "$jar" in
      *-sources.jar|*-javadoc.jar) continue ;;
    esac
    candidates+=("$jar")
  done
  if [ "${#candidates[@]}" -eq 1 ] && [ "${candidates[0]}" = "$exact" ]; then
    printf '%s\n' "$exact"
    return "$EXIT_OK"
  fi
  {
    echo "FATAL: no unambiguous packaged jar for $module under $ROOT/$module/target"
    echo "  expected exactly: $exact"
    echo "  found ${#candidates[@]} non-sources/non-javadoc jar(s):"
    if [ "${#candidates[@]}" -gt 0 ]; then
      printf '    %s\n' "${candidates[@]}"
    fi
    echo "  stale or unexpected jars are present; run mvn clean (or remove the stray jars) and rebuild"
  } >&2
  return "$EXIT_FAIL"
}
