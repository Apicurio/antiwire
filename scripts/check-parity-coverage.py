#!/usr/bin/env python3
"""TASK-14 parity coverage: reconcile pinned upstream test cases against port artifacts.

Reads config/parity-pins.json (provenance) and config/upstream-case-map.json (the
reconciliation map maintained by the case ledgers) and enforces three layers:

  source presence:  every upstream case (minus recorded missing, after renames) exists
                    in the mapped port file; port-only extra cases are reported, not
                    fatal; deferred files need an owner; excluded files need a reason;
  skip dispositions: every @Disabled/@Ignore in a mapped port file is lawful only when
                    the pinned upstream ignores the same case (a mirrored skip, read
                    from the clone) or the map records the skip with a reason and an
                    explicit disposition: exclusion naming a DEC (a declared non-ported
                    feature) or owner naming the TASK that owes the case. Anything else
                    fails with the case identity. Class-level disables need a recorded
                    skipped_class disposition. A fixture:true entry whose upstream or
                    port file carries test methods fails (an executable file cannot
                    leave the inventory through the fixture flag);
  execution (--execution, run by parity-coverage.sh after the build): per-class surefire
                    reports are reconciled by case IDENTITY, not by aggregate counts
                    (port-only extras, parameterized invocations and unmapped port-only
                    classes make count equality wrong): every declared case of a mapped
                    executable class must appear in its module's report, and every
                    runtime skip must carry a lawful disposition. Runtime skips without
                    any annotation (assumptions, environment) also require a record.

--require-complete (release validation, TASK-21) additionally fails while any deferred
file remains and while any skip disposition still carries an open owner task, so pending
required cases block release while DEC-approved feature exclusions do not.

Any upstream test file inside the in-scope modules that is absent from the map fails
the run (unaccounted drift). Output: RESULT lines for scripts/verify.sh, human summary,
and --json for machine consumers.

Method of the name extraction: a case counts when a @Test-family annotation line is
followed by a fun/void declaration; a skip counts when a @Disabled/@Ignore annotation
precedes the same declaration (or the class declaration, for class-level skips). The
line-based scan deliberately skips annotation-argument continuation lines, so a skip is
detected regardless of how long its reason string is. Fixture classes (TestAllTypesData
et al.) are carried in the map with fixture: true and validated to carry no test methods
on either side; their coverage is the executable suites' business.
"""

import argparse
import glob
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PINS_PATH = os.path.join(ROOT, "config", "parity-pins.json")
MAP_PATH = os.path.join(ROOT, "config", "upstream-case-map.json")

TEST_RE = re.compile(
    r"@(?:org\.junit\.jupiter\.api\.Test|org\.junit\.jupiter\.params\.ParameterizedTest"
    r"|org\.junit\.Test|ParameterizedTest|Test)(?=$|\s|\()")

DISABLE_RE = re.compile(
    r"@(?:org\.junit\.jupiter\.api\.Disabled|org\.junit\.Ignore|Disabled|Ignore)"
    r"(?=$|\s|\()")

DECL_RE = re.compile(r"(?:@\w+(?:\([^)]*\))?\s+)*"
                     r"(?:public\s+|protected\s+|internal\s+|private\s+)?"
                     r"(?:final\s+|open\s+|override\s+|inline\s+|suspend\s+)?"
                     r"(?:fun|void)\s+(`[^`]+`|\w+)\s*\(")

# Fallback for annotations sharing the declaration line whose argument strings contain
# nested parentheses (DECL_RE's annotation prefix cannot span those).
SAME_LINE_DECL_RE = re.compile(r"\b(?:fun|void)\s+(`[^`]+`|\w+)\s*\(")

CLASS_DECL_RE = re.compile(
    r"(?:public\s+|protected\s+|internal\s+|private\s+)?"
    r"(?:final\s+|open\s+|abstract\s+|sealed\s+)?"
    r"(?:class|interface|object)\s+\w+")

DEC_RE = re.compile(r"DEC-\d+")
TASK_RE = re.compile(r"TASK-\d+(?:\.\d+)?")

PORT_CLASS_RE = re.compile(r"src/test/(?:java|kotlin)/(.*)\.(?:java|kt)$")

# Memoization for extract_test_info: one run reads each mapped file twice (the
# source-presence pass and the execution pass) and the tree is read-only during a run.
_PARSE_CACHE = {}


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


def same_line_decl(stripped):
    m = DECL_RE.match(stripped)
    if m:
        return m.group(1)
    m = SAME_LINE_DECL_RE.search(stripped)
    return m.group(1) if m else None


def extract_test_info(path):
    """Returns (test_names, disabled_names, class_disabled) for a .kt or .java file,
    or (None, None, None) when the file cannot be read.

    test_names: methods declared under a @Test-family annotation. disabled_names: the
    subset that also carries a @Disabled/@Ignore at method level. class_disabled: a
    disable annotation sits on the class declaration itself. Backtick-named Kotlin tests
    are normalized by stripping the backticks; a method counts when a test annotation
    precedes its declaration (same line or the next non-annotation line). Annotation
    names are read only up to the first string literal, so argument text mentioning an
    annotation cannot register a phantom. Annotation continuations (multi-line
    @Disabled("..." + "...") bodies, quoted argument lines, and the bare closing
    parenthesis of a parenthesized multi-line argument) never end the pending state, so
    a skip is detected however long its reason is.

    Results are memoized per path: the source-presence pass and the execution pass both
    read the same files during one run, and the tree is read-only for that run. The
    returned sets are shared through the cache; callers must treat them as read-only.
    """
    if path in _PARSE_CACHE:
        return _PARSE_CACHE[path]
    try:
        with open(path, encoding="utf-8") as f:
            lines = f.readlines()
    except FileNotFoundError:
        result = (None, None, None)
        _PARSE_CACHE[path] = result
        return result
    names = set()
    disabled = set()
    class_disabled = False
    pending_test = False
    pending_disable = False
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("//") or stripped.startswith("*") \
                or stripped.startswith("/*"):
            continue
        if stripped.startswith("@"):
            # Annotation names are matched on the line up to its first string literal, so
            # an argument string that merely mentions "@Test" or "@Disabled" (a display
            # name, a reason quoting another case) cannot register a phantom case.
            code = stripped.split('"', 1)[0]
            if TEST_RE.search(code):
                pending_test = True
            if DISABLE_RE.search(code):
                pending_disable = True
            decl = same_line_decl(stripped)
            if decl and pending_test and not stripped.endswith(","):
                # Annotation and declaration share the line ("@Test public void x()").
                names.add(decl.strip("`"))
                if pending_disable:
                    disabled.add(decl.strip("`"))
                pending_test = pending_disable = False
            elif CLASS_DECL_RE.search(stripped) and pending_disable and not pending_test:
                # Class-level disable written on the class declaration's own line.
                class_disabled = True
                pending_test = pending_disable = False
            continue
        if stripped.startswith("+") or stripped.startswith('"') or stripped == ")":
            # Annotation-argument continuations: string concatenation lines, quoted
            # argument lines, and the bare closing parenthesis of a multi-line
            # @Disabled("..."
            # ) body.
            continue
        if pending_test or pending_disable:
            if stripped.startswith("@"):
                continue
            m = DECL_RE.match(stripped)
            if m:
                if pending_test:
                    names.add(m.group(1).strip("`"))
                    if pending_disable:
                        disabled.add(m.group(1).strip("`"))
            elif CLASS_DECL_RE.search(stripped):
                if pending_disable and not pending_test:
                    class_disabled = True
        pending_test = pending_disable = False
    result = (names, disabled, class_disabled)
    _PARSE_CACHE[path] = result
    return result


def canonical_renames(entry):
    """The entry's renames in canonical form, shared by both reconciliation passes."""
    return {canonical(k): canonical(v) for k, v in entry.get("renames", {}).items()}


def mirrored_skips(entry, upstream_names, upstream_disabled, upstream_class_disabled):
    """Canonical port-side names of cases the pinned upstream itself ignores: every
    case when the upstream class is ignored wholesale, else the renamed identities of
    the upstream method-level ignores. A port skip of exactly these cases is lawful."""
    renames = canonical_renames(entry)
    if upstream_class_disabled:
        return {renames.get(canonical(n), canonical(n)) for n in upstream_names}
    return {renames.get(canonical(n), canonical(n)) for n in upstream_disabled}


def upstream_test_files(clone, module_root):
    """Yields relative test-file paths under the module root."""
    base = os.path.join(clone, module_root)
    for dirpath, _dirnames, filenames in os.walk(base):
        for filename in filenames:
            if filename.endswith((".kt", ".java")):
                yield os.path.relpath(os.path.join(dirpath, filename), base)


def strip_invocation_suffix(name):
    """Reduces a surefire testcase name to its method name.

    JUnit5 reports "methodName" or "methodName()"; parameterized runs append "[1]",
    "[Buffer]" and similar per-invocation suffixes. Identity reconciliation needs the
    method identity, so bracketed suffixes (and parens) are stripped before
    canonicalization; counting invocations would break on exactly the parameterized
    expansions that make aggregate count equality unusable.
    """
    name = name.strip()
    while name.endswith("]"):
        open_idx = name.rfind("[")
        if open_idx < 0:
            break
        name = name[:open_idx].strip()
    if name.endswith("()"):
        name = name[:-2]
    return name


def read_surefire_reports(root, modules, problems):
    """Returns {(module, classname): {"tests", "skipped"}} from the per-class surefire
    XML reports of the given modules. Canonicalized identities, so a class missing
    entirely from its module's reports is distinguishable from a class whose cases did
    not run. Keyed by module too: com.squareup.wire.ProtoAdapterTest exists in both
    wire-runtime-java and wire-tests-java with different case sets, so the bare class
    name would merge two different classes. Failures and errors are deliberately not
    collected: mvn verify is the failure authority; this reconciliation is about which
    identities ran at all.
    """
    reports = {}
    for module in sorted(modules):
        pattern = os.path.join(root, module, "target", "surefire-reports", "TEST-*.xml")
        files = sorted(glob.glob(pattern))
        if not files:
            problems.append("execution: no surefire reports under %s/%s/target/"
                            "surefire-reports; run the build before execution reconciliation"
                            % (root, module))
            continue
        for path in files:
            try:
                suite = ET.parse(path).getroot()
            except ET.ParseError as e:
                problems.append("execution: cannot parse surefire report %s: %s" % (path, e))
                continue
            record = reports.setdefault(
                (module, suite.get("name") or ""),
                {"tests": set(), "skipped": set()})
            for testcase in suite.iter("testcase"):
                name = canonical(strip_invocation_suffix(testcase.get("name") or ""))
                if not name:
                    continue
                record["tests"].add(name)
                if testcase.find("skipped") is not None:
                    record["skipped"].add(name)
    return reports


def validate_disposition(where, record, problems):
    """A skip disposition must carry a reason and exactly one classification: an
    exclusion naming a DEC (a declared non-ported feature) or an owner naming the TASK
    that owes the case. Returns 'exclusion', 'owner', or None when invalid."""
    reason = record.get("reason")
    if not isinstance(reason, str) or not reason.strip():
        problems.append("%s: skip disposition without a reason" % where)
        return None
    has_exclusion = "exclusion" in record
    has_owner = "owner" in record
    if has_exclusion == has_owner:
        problems.append("%s: skip disposition needs exactly one of exclusion (a DEC "
                        "reference) or owner (a TASK reference)" % where)
        return None
    if has_exclusion:
        if not isinstance(record["exclusion"], str) \
                or not DEC_RE.fullmatch(record["exclusion"]):
            problems.append("%s: exclusion %r is not a DEC reference" % (where, record["exclusion"]))
            return None
        return "exclusion"
    if not isinstance(record["owner"], str) or not TASK_RE.fullmatch(record["owner"]):
        problems.append("%s: owner %r is not a TASK reference" % (where, record["owner"]))
        return None
    return "owner"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", help="write the machine-readable report to this path")
    parser.add_argument("--require-complete", action="store_true",
                        help="release validation: fail while any file is deferred or any "
                             "skip disposition still has an open owner task")
    parser.add_argument("--execution", action="store_true",
                        help="reconcile per-class surefire reports by case identity "
                             "(parity-coverage.sh runs this after the build; the reports "
                             "must exist or the check fails)")
    args = parser.parse_args()

    with open(PINS_PATH) as f:
        pins = json.load(f)
    with open(MAP_PATH) as f:
        case_map = json.load(f)

    clone = os.environ.get("ANTIWIRE_UPSTREAM", pins["clone_path_default"])
    problems = []
    report = {"upstream": pins["upstream"], "clone": clone, "modules": {},
              "totals": {"adopted_files": 0, "partial_files": 0, "deferred_files": 0,
                         "excluded_files": 0, "fixture_files": 0,
                         "upstream_cases": 0, "ported_cases": 0, "missing_cases": 0,
                         "extra_port_cases": 0, "skipped_cases": 0,
                         "mirrored_skipped_cases": 0, "open_owner_skipped_cases": 0}}

    # Provenance: verify the clone matches the pin.
    if not os.path.isdir(os.path.join(clone, ".git")):
        problems.append("upstream clone missing at %s; run scripts/fetch-upstream.sh" % clone)
    else:
        report["pin_verified"] = False
        try:
            tag = pins["upstream"]["tag"]
            tag_object = git_rev_parse(clone, "%s^{tag}" % tag)
            commit = git_rev_parse(clone, "%s^{commit}" % tag)
            head = git_rev_parse(clone, "HEAD")
            if tag_object != pins["upstream"]["annotated_tag_object"] \
                    or commit != pins["upstream"]["resolved_commit"]:
                problems.append(
                    "upstream clone at %s does not match the pin (tag %s -> %s, object %s)"
                    % (clone, tag, commit, tag_object))
            elif head != pins["upstream"]["resolved_commit"]:
                # fetch-upstream.sh must leave the worktree at the pin; reconciliation
                # reads files, not refs.
                problems.append(
                    "upstream worktree at %s is not checked out at the pinned commit "
                    "(HEAD %s); run scripts/fetch-upstream.sh" % (clone, head))
            else:
                report["pin_verified"] = True
        except subprocess.CalledProcessError as e:
            problems.append("cannot resolve pinned tag in %s: %s" % (clone, e))

    open_owner_skips = []
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
            if status not in ("adopted", "partial", "deferred", "excluded"):
                problems.append("%s/%s has an unrecognized status %r"
                                % (module, relative, status))
                continue

            if entry.get("fixture"):
                # A fixture entry is exempt from case reconciliation, so the flag must
                # not be able to remove executable files from the inventory: both sides
                # must genuinely carry no test methods.
                up_names, _up_disabled, _up_class = extract_test_info(
                    os.path.join(clone, module_entry["root"], relative))
                if up_names is None:
                    problems.append("cannot read upstream file %s/%s" % (module, relative))
                elif up_names:
                    problems.append("%s/%s is marked fixture but its upstream file "
                                    "carries %d test method(s); an executable upstream "
                                    "file cannot leave the inventory through the fixture "
                                    "flag" % (module, relative, len(up_names)))
                if "port" not in entry:
                    problems.append("%s/%s is marked fixture but maps no port file"
                                    % (module, relative))
                else:
                    port_names, _port_disabled, _port_class = extract_test_info(
                        os.path.join(ROOT, entry["port"]))
                    if port_names is None:
                        problems.append("mapped port file missing: %s" % entry["port"])
                    elif port_names:
                        problems.append("%s/%s is marked fixture but its port file %s "
                                        "carries %d test method(s); an executable file "
                                        "cannot leave the inventory through the fixture "
                                        "flag" % (module, relative, entry["port"],
                                                  len(port_names)))
                report["totals"]["fixture_files"] += 1
                continue

            if status in ("deferred", "excluded"):
                if status == "deferred" and not entry.get("owner"):
                    problems.append("%s/%s deferred without an owner" % (module, relative))
                if not entry.get("reason"):
                    problems.append("%s/%s excluded/deferred without a reason" % (module, relative))
                if "port" in entry:
                    problems.append("%s/%s is %s but still claims a port file"
                                    % (module, relative, status))
                report["totals"]["%s_files" % status] += 1
                continue

            upstream_names, upstream_disabled, upstream_class_disabled = extract_test_info(
                os.path.join(clone, module_entry["root"], relative))
            port_names, port_disabled, port_class_disabled = extract_test_info(
                os.path.join(ROOT, entry["port"]))
            if upstream_names is None:
                problems.append("cannot read upstream file %s/%s" % (module, relative))
                continue
            if port_names is None:
                problems.append("mapped port file missing: %s" % entry["port"])
                continue

            renames = canonical_renames(entry)
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

            # Skip reconciliation (TASK-14.2): a port case may be @Disabled/@Ignore only
            # when the pinned upstream ignores the same case (a mirrored skip), or the
            # map records the skip with a lawful disposition. The annotation's reason
            # string alone is never a disposition.
            skipped_records = entry.get("skipped", {})
            if not isinstance(skipped_records, dict):
                problems.append("%s/%s: skipped must map case names to disposition "
                                "records" % (module, relative))
                skipped_records = {}
            mirrored = mirrored_skips(entry, upstream_names, upstream_disabled,
                                      upstream_class_disabled)
            class_record = entry.get("skipped_class")
            if port_class_disabled and class_record is None and not upstream_class_disabled:
                problems.append("%s/%s is disabled at class level without a recorded "
                                "skipped_class disposition" % (module, relative))
            if class_record is not None:
                if port_class_disabled or upstream_class_disabled:
                    validate_disposition("%s/%s skipped_class" % (module, relative),
                                         class_record, problems)
                    if class_record.get("owner"):
                        open_owner_skips.append(
                            ("%s/%s (class-level)" % (module, relative), class_record["owner"]))
                else:
                    problems.append("%s/%s records a skipped_class disposition but "
                                    "neither side disables the class" % (module, relative))
            record_skips = {}
            for key, value in skipped_records.items():
                if not isinstance(value, dict):
                    problems.append("%s/%s case %s: skip disposition must be a record "
                                    "with reason and exclusion/owner" % (module, relative, key))
                    continue
                record_skips[canonical(key)] = (key, value)
            disabled_canonical = {canonical(name): name for name in port_disabled}
            for case, name in sorted(disabled_canonical.items()):
                if case in mirrored:
                    report["totals"]["mirrored_skipped_cases"] += 1
                    continue
                if case in record_skips:
                    key, value = record_skips[case]
                    kind = validate_disposition(
                        "%s/%s case %s" % (module, relative, key), value, problems)
                    if kind == "owner":
                        open_owner_skips.append(("%s/%s case %s" % (module, relative, key),
                                                 value["owner"]))
                    continue
                problems.append("%s/%s case %s is skipped (@Disabled/@Ignore) without a "
                                "mirrored upstream ignore or a recorded disposition; add a "
                                "'skipped' entry in config/upstream-case-map.json or remove "
                                "the skip" % (module, relative, name))
            for case, (key, _value) in sorted(record_skips.items()):
                if case not in disabled_canonical:
                    problems.append("%s/%s records a skip for case %s, which is not "
                                    "skipped in the port file (stale record)"
                                    % (module, relative, key))
            report["totals"]["skipped_cases"] += len(disabled_canonical)
            report["totals"]["upstream_cases"] += len(upstream_names)
            report["totals"]["ported_cases"] += len(expected) - len(missing)
            report["totals"]["missing_cases"] += len(missing)
            report["totals"]["extra_port_cases"] += len(extra)
            report["totals"]["%s_files" % status] += 1

        stale = sorted(set(files_map) - seen)
        for relative in stale:
            problems.append("map entry for a file absent upstream: %s/%s" % (module, relative))
        report["modules"][module] = module_report

    # Module-level drift: every upstream top-level module must be claimed by the map,
    # either as an in-scope root or as an excluded module with a reason.
    in_scope_modules = {m.split("/")[0] for m in case_map["modules"]}
    for top in sorted(os.listdir(clone)):
        if not os.path.isdir(os.path.join(clone, top)) or top.startswith("."):
            continue
        if top in in_scope_modules or top in case_map["excluded_upstream_modules"]:
            continue
        problems.append("unaccounted upstream module: %s (add it to the case map or to "
                        "excluded_upstream_modules with a reason)" % top)
    for top in sorted(case_map["excluded_upstream_modules"]):
        if not os.path.isdir(os.path.join(clone, top)):
            problems.append("excluded_upstream_modules names a module absent upstream: %s" % top)

    fixtures_script = os.path.join(ROOT, "scripts", "generate-java-fixtures.sh")
    if os.path.exists(fixtures_script):
        with open(fixtures_script) as f:
            fixtures_text = f.read()
        if pins["upstream"]["tag"] not in fixtures_text:
            problems.append("scripts/generate-java-fixtures.sh does not reference the pinned "
                            "tag %s; the fixture corpus and this reconciliation would diverge"
                            % pins["upstream"]["tag"])

    # Execution reconciliation (TASK-14.2): identities, not aggregate counts. The
    # per-class surefire reports are the execution evidence; every declared case of a
    # mapped executable class must appear (executed or skipped), every skipped case must
    # carry the lawful disposition validated above, and a report case absent from the
    # port source means stale compiled tests ran.
    report["totals"]["open_owner_skipped_cases"] = len(open_owner_skips)
    if args.execution:
        execution = {"reconciled_classes": 0, "executed_cases": 0, "skipped_cases": 0}
        modules = sorted({entry["port"].split("/")[0]
                          for module_entry in case_map["modules"].values()
                          for entry in module_entry["files"].values()
                          if entry.get("port") and not entry.get("fixture")
                          and entry.get("status", "adopted") in ("adopted", "partial")})
        reports = read_surefire_reports(ROOT, modules, problems)
        for module, module_entry in case_map["modules"].items():
            for relative, entry in sorted(module_entry["files"].items()):
                if not entry.get("port") or entry.get("fixture") \
                        or entry.get("status", "adopted") not in ("adopted", "partial"):
                    continue
                port_names, _unused_disabled, _unused_class = extract_test_info(
                    os.path.join(ROOT, entry["port"]))
                if not port_names:
                    continue
                class_match = PORT_CLASS_RE.search(entry["port"])
                if class_match is None:
                    problems.append("execution: cannot derive a class name from port path "
                                    "%s" % entry["port"])
                    continue
                classname = class_match.group(1).replace("/", ".")
                build_module = entry["port"].split("/")[0]
                suite = reports.get((build_module, classname))
                if suite is None:
                    problems.append("execution: %s (%s in %s) has no surefire report; the "
                                    "class did not run in the build"
                                    % (entry["port"], classname, build_module))
                    continue
                skipped_records = {canonical(k) for k in entry.get("skipped", {})
                                   if isinstance(entry["skipped"][k], dict)}
                upstream_names, upstream_disabled, upstream_class_disabled = extract_test_info(
                    os.path.join(clone, module_entry["root"], relative))
                mirrored = mirrored_skips(entry, upstream_names, upstream_disabled,
                                          upstream_class_disabled)
                if entry.get("skipped_class"):
                    mirrored |= {canonical(n) for n in port_names}
                execution["reconciled_classes"] += 1
                for name in sorted(port_names):
                    case = canonical(name)
                    if case in suite["skipped"] and case not in mirrored \
                            and case not in skipped_records:
                        problems.append(
                            "execution: %s.%s skipped at runtime without a lawful "
                            "disposition (mirrored upstream ignore or recorded skip); an "
                            "annotation-free skip (assumption, environment) needs a record "
                            "too" % (classname, name))
                    elif case not in suite["tests"]:
                        problems.append("execution: %s.%s did not run: absent from the "
                                        "surefire report for %s" % (classname, name, build_module))
                    else:
                        execution["executed_cases"] += 1
                        if case in suite["skipped"]:
                            execution["skipped_cases"] += 1
                for case in sorted(suite["tests"] - {canonical(n) for n in port_names}):
                    problems.append("execution: %s ran a case (%s) absent from the port "
                                    "source %s; stale target/test-classes? run a clean "
                                    "build" % (classname, case, entry["port"]))
        report["execution"] = execution

    deferred_total = report["totals"]["deferred_files"]
    if args.require_complete and deferred_total:
        problems.append("release validation: %d deferred upstream files still have open owners"
                        % deferred_total)
    if args.require_complete and open_owner_skips:
        problems.append("release validation: %d skipped case(s) still carry an open owner "
                        "task: %s" % (len(open_owner_skips),
                                      "; ".join("%s -> %s" % (where, owner)
                                                for where, owner in open_owner_skips)))

    report["problems"] = problems
    report["verdict"] = "PASS" if not problems else "FAIL"

    if args.json:
        with open(args.json, "w") as f:
            json.dump(report, f, indent=2)

    totals = report["totals"]
    print("parity-coverage: reconciled %d module(s); adopted %d, partial %d, deferred %d, "
          "excluded %d, fixture %d upstream files; %d upstream cases (%d ported, %d "
          "recorded missing, %d port-only extras), %d skipped (%d mirrored upstream "
          "ignores, %d with open owners)"
          % (len(report["modules"]), totals["adopted_files"], totals["partial_files"],
             totals["deferred_files"], totals["excluded_files"], totals["fixture_files"],
             totals["upstream_cases"], totals["ported_cases"], totals["missing_cases"],
             totals["extra_port_cases"], totals["skipped_cases"],
             totals["mirrored_skipped_cases"], totals["open_owner_skipped_cases"]))
    if args.execution:
        execution = report["execution"]
        print("parity-coverage: execution reconciled %d class(es) by identity; %d cases "
              "executed, %d lawfully skipped"
              % (execution["reconciled_classes"], execution["executed_cases"],
                 execution["skipped_cases"]))
    if deferred_total:
        print("parity-coverage: %d file(s) deferred with owners (release validation will "
              "reject until closed)" % deferred_total)
    if open_owner_skips:
        print("parity-coverage: %d skipped case(s) with open owner tasks (release "
              "validation will reject until closed)" % len(open_owner_skips))
    for problem in problems:
        print("parity-coverage PROBLEM: %s" % problem)
    print("RESULT parity-coverage.status=%s" % report["verdict"])
    note = ("pins verified, %d upstream cases reconciled against port artifacts"
            % totals["upstream_cases"]) if not problems else "%d problem(s); see above" % len(problems)
    print("RESULT parity-coverage.note=%s" % note)
    sys.exit(0 if not problems else 1)


if __name__ == "__main__":
    main()
