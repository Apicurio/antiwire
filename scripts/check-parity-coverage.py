#!/usr/bin/env python3
"""TASK-14 parity coverage: reconcile pinned upstream test cases against port artifacts.

Reads config/parity-pins.json (provenance) and config/upstream-case-map.json (the
reconciliation map maintained by the case ledgers), extracts @Test method names from
both sides, and enforces:

  adopted/partial: every upstream case (minus recorded missing, after renames) exists
                   in the mapped port file; port-only extra cases are reported, not fatal;
  deferred:        an owning task is recorded and no port file is claimed;
  excluded:        a reason naming the non-ported feature is recorded.

Any upstream test file inside the in-scope modules that is absent from the map fails
the run (unaccounted drift). Output: RESULT lines for scripts/verify.sh, human summary,
and --json for machine consumers. --require-complete (release validation, TASK-21)
additionally fails while any deferred case remains.

Method of the name extraction: a method counts when an @Test (or @ParameterizedTest,
@org.junit.Test) annotation line is followed by a fun/void declaration. Fixture classes
(TestAllTypes et al.) are carried in the map with fixture: true and skipped here; their
coverage is the executable suites' business.
"""

import argparse
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PINS_PATH = os.path.join(ROOT, "config", "parity-pins.json")
MAP_PATH = os.path.join(ROOT, "config", "upstream-case-map.json")

TEST_RE = re.compile(r"@(?:ParameterizedTest|org\.junit\.(?:jupiter\.)?Test|Test)(?=$|\s|\()")

DECL_RE = re.compile(r"(?:@\w+(?:\([^)]*\))?\s+)*"
                     r"(?:public\s+|internal\s+|private\s+)?(?:final\s+)?"
                     r"(?:fun|void)\s+(`[^`]+`|\w+)\s*\(")


def canonical(name):
    """Canonical comparison form: lowercase alphanumerics only, backticks stripped.

    Upstream Kotlin test names are frequently backticked sentences ("link message") while
    the port uses their camelCase form (linkMessage); canonicalizing both sides reconciles
    the mechanical renames without a hand-maintained table. Deliberate renames that change
    words still need explicit entries in the map's renames.
    """
    return re.sub(r"[^a-z0-9]", "", name.lower())


def git_rev_parse(repo, rev):
    return subprocess.run(
        ["git", "-C", repo, "rev-parse", rev],
        capture_output=True, text=True, check=True).stdout.strip()


def extract_test_names(path):
    """Returns the set of test method names in a .kt or .java file.

    Backtick-named Kotlin tests are normalized by stripping the backticks; a method counts
    when a test annotation precedes its declaration (same line or the next non-annotation
    line). The pending flag resets on the first line that is neither blank, comment, nor
    annotation, so class-level annotations and multi-line argument lists cannot leak.
    """
    try:
        with open(path, encoding="utf-8") as f:
            lines = f.readlines()
    except FileNotFoundError:
        return None
    names = set()
    pending = False
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("//"):
            continue
        if stripped.startswith("@"):
            m = DECL_RE.match(stripped)
            if m and TEST_RE.search(stripped):
                # Annotation and declaration share the line ("@Test public void x()").
                names.add(m.group(1).strip("`"))
                pending = False
                continue
            if TEST_RE.search(stripped):
                pending = True
            continue
        if pending:
            m = DECL_RE.match(stripped)
            if m:
                names.add(m.group(1).strip("`"))
        pending = False
    return names


def upstream_test_files(clone, module_root):
    """Yields relative test-file paths under the module root."""
    base = os.path.join(clone, module_root)
    for dirpath, _dirnames, filenames in os.walk(base):
        for filename in filenames:
            if filename.endswith((".kt", ".java")):
                yield os.path.relpath(os.path.join(dirpath, filename), base)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", help="write the machine-readable report to this path")
    parser.add_argument("--require-complete", action="store_true",
                        help="release validation: fail while any file is deferred")
    args = parser.parse_args()

    with open(PINS_PATH) as f:
        pins = json.load(f)
    with open(MAP_PATH) as f:
        case_map = json.load(f)

    clone = os.environ.get("ANTIWIRE_UPSTREAM", pins["clone_path_default"])
    problems = []
    report = {"upstream": pins["upstream"], "clone": clone, "modules": {},
              "totals": {"adopted_files": 0, "partial_files": 0, "deferred_files": 0,
                         "excluded_files": 0, "upstream_cases": 0, "ported_cases": 0,
                         "missing_cases": 0, "extra_port_cases": 0}}

    # Provenance: verify the clone matches the pin.
    if not os.path.isdir(os.path.join(clone, ".git")):
        problems.append("upstream clone missing at %s; run scripts/fetch-upstream.sh" % clone)
    else:
        try:
            tag = pins["upstream"]["tag"]
            tag_object = git_rev_parse(clone, "%s^{tag}" % tag)
            commit = git_rev_parse(clone, "%s^{commit}" % tag)
            if tag_object != pins["upstream"]["annotated_tag_object"] \
                    or commit != pins["upstream"]["resolved_commit"]:
                problems.append(
                    "upstream clone at %s does not match the pin (tag %s -> %s, object %s)"
                    % (clone, tag, commit, tag_object))
            report["pin_verified"] = True
        except subprocess.CalledProcessError as e:
            problems.append("cannot resolve pinned tag in %s: %s" % (clone, e))

    for module, module_entry in case_map["modules"].items():
        module_report = {"files": {}}
        files_map = module_entry["files"]
        seen = set()
        for relative in sorted(upstream_test_files(clone, module_entry["root"])):
            seen.add(relative)
            entry = files_map.get(relative)
            if entry is None:
                problems.append("unaccounted upstream test file: %s/%s" % (module, relative))
                continue
            status = entry.get("status", "adopted")
            record = {"status": status}
            module_report["files"][relative] = record

            if entry.get("fixture"):
                record["note"] = "fixture class carried by an executable suite"
                continue

            if status in ("deferred", "excluded"):
                if status == "deferred" and not entry.get("owner"):
                    problems.append("%s/%s deferred without an owner" % (module, relative))
                if not entry.get("reason"):
                    problems.append("%s/%s excluded/deferred without a reason" % (module, relative))
                report["totals"]["%s_files" % status] += 1
                continue

            upstream_names = extract_test_names(os.path.join(clone, module_entry["root"], relative))
            port_names = extract_test_names(os.path.join(ROOT, entry["port"]))
            if upstream_names is None:
                problems.append("cannot read upstream file %s/%s" % (module, relative))
                continue
            if port_names is None:
                problems.append("mapped port file missing: %s" % entry["port"])
                continue

            renames = {canonical(k): canonical(v) for k, v in entry.get("renames", {}).items()}
            missing = {canonical(k) for k in entry.get("missing", {})}
            upstream_canonical = {canonical(name) for name in upstream_names}
            port_canonical = {canonical(name) for name in port_names}
            expected = {renames.get(name, name) for name in upstream_canonical}
            unported = sorted(expected - port_canonical - missing)
            if unported:
                problems.append("%s/%s lost cases: %s" % (module, relative, ", ".join(unported)))
            unrecorded_missing = sorted(missing - expected)
            if unrecorded_missing:
                problems.append("%s/%s records missing cases that do not exist upstream: %s"
                                % (module, relative, ", ".join(unrecorded_missing)))
            extra = sorted(port_canonical - expected)
            record["upstream_cases"] = len(upstream_names)
            record["extra_port_cases"] = extra
            report["totals"]["upstream_cases"] += len(upstream_names)
            report["totals"]["ported_cases"] += len(expected) - len(missing)
            report["totals"]["missing_cases"] += len(missing)
            report["totals"]["extra_port_cases"] += len(extra)
            report["totals"]["%s_files" % status] += 1

        stale = sorted(set(files_map) - seen)
        for relative in stale:
            problems.append("map entry for a file absent upstream: %s/%s" % (module, relative))
        report["modules"][module] = module_report

    deferred_total = report["totals"]["deferred_files"]
    if args.require_complete and deferred_total:
        problems.append("release validation: %d deferred upstream files still have open owners"
                        % deferred_total)

    report["problems"] = problems
    report["verdict"] = "PASS" if not problems else "FAIL"

    if args.json:
        with open(args.json, "w") as f:
            json.dump(report, f, indent=2)

    totals = report["totals"]
    print("parity-coverage: reconciled %d module(s); adopted %d, partial %d, deferred %d, "
          "excluded %d upstream files; %d upstream cases (%d ported, %d recorded missing, "
          "%d port-only extras)"
          % (len(report["modules"]), totals["adopted_files"], totals["partial_files"],
             totals["deferred_files"], totals["excluded_files"], totals["upstream_cases"],
             totals["ported_cases"], totals["missing_cases"], totals["extra_port_cases"]))
    if deferred_total:
        print("parity-coverage: %d file(s) deferred with owners (release validation will "
              "reject until closed)" % deferred_total)
    for problem in problems:
        print("parity-coverage PROBLEM: %s" % problem)
    print("RESULT parity-coverage.status=%s" % report["verdict"])
    note = ("pins verified, %d upstream cases reconciled against port artifacts"
            % totals["upstream_cases"]) if not problems else "%d problem(s); see above" % len(problems)
    print("RESULT parity-coverage.note=%s" % note)
    sys.exit(0 if not problems else 1)


if __name__ == "__main__":
    main()
