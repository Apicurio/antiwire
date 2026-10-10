#!/usr/bin/env python3
"""Source-compatibility surface check of the port against the real Wire 7.1.0 jars (TASK-34).

Compatibility target (maintainer decision 2026-10-09): SOURCE compatibility for every public
upstream member that Java can express without Kotlin types (DEC-4) and without okio in the
signature (DEC-14). Binary compatibility is not promised (DEC-2).

What it does:
  1. Fetches the four real upstream jars (wire-runtime-jvm, wire-schema-jvm, wire-java-generator,
     wire-compiler) from Maven Central, verifies each against the SHA-256 pinned in
     config/parity-pins.json and cross-checks the SHA-1 Maven Central publishes. Fails closed.
  2. Runs `javap -protected -s` over the upstream classes and over the port's module jars and
     compares every public or protected member by full signature (name, erased descriptor,
     static-ness, visibility) and, for matches, by checked exceptions (throws).
  3. Classifies every difference as GAP (to be fixed or consciously accepted, with an owner) or
     EXCLUDED (Kotlin type in the signature, okio type in the signature, Kotlin internal,
     out-of-scope feature, Kotlin data-class bridge, Java-hidden name). MATCH rows are only counted.
  4. Compiles the consumer snippets under scripts/surface-consumer/ against the real upstream jars
     (must compile) and against the port (must compile unless the snippet header records an
     expected failure with an owner).
  5. Compares the result with the committed ledger config/surface-baseline.tsv. Any difference
     fails: a new regression, or a gap that was fixed and is now stale.

Usage:
  scripts/surface-check.py --port-jar <jar> [--port-jar <jar> ...] [--update [--allow-new]]
Exit codes: 0 pass, 1 fail, 3 could not run (missing jars, no network and no cache, no javap).
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import shutil
import urllib.request
import zipfile
from typing import NoReturn

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EXIT_OK, EXIT_FAIL, EXIT_NOT_RUN = 0, 1, 3

UPSTREAM_API_JARS = ['wire-runtime-jvm.jar', 'wire-schema-jvm.jar', 'wire-java-generator.jar',
                     'wire-compiler.jar']
SNIPPET_CLASSPATH_JARS = ['wire-schema-jvm.jar', 'wire-runtime-jvm.jar', 'okio.jar',
                          'kotlin-stdlib.jar']

SYNTHETIC_CLASS = re.compile(r'\$\d|\$WhenMappings|WhenMappings$|\$\$serializer|\$DefaultImpls')
SYNTHETIC_MEMBER = re.compile(r'\$default$|^access\$|\$lambda|^lambda\$|\$annotations$|^<clinit>$'
                              r'|\$wire_')
OUT_OF_SCOPE = re.compile(
    r'Json|AndroidMessage|\.Android|ManifestModule|ManifestKt|BuildConfigKt|KotlinConstructorBuilder'
    r'|KotlinTarget|SwiftTarget|Grpc|\.kotlin\.')
DATA_BRIDGE = re.compile(r'^(copy|component\d+)$')
# Members of in-scope classes that belong to features DEC-6 leaves out: the Kotlin and Swift
# generators and Android output (WireCompiler options, Target/handler plumbing).
OUT_OF_SCOPE_MEMBER = re.compile(r'^(get|is|set)?(kotlin|swift|emitAndroid)', re.I)

OWNER_FIX = 'TASK-33.2'
OWNER_THROWS = 'TASK-33.3'


FETCH_TIMEOUT = 60  # seconds per request; a stalled connection must fail closed, not hang verify.sh


def die(code: int, msg: str) -> NoReturn:
    print(msg, file=sys.stderr)
    sys.exit(code)


def run(cmd, check=True):
    try:
        p = subprocess.run(cmd, capture_output=True, text=True)
    except FileNotFoundError:
        return die(EXIT_NOT_RUN, 'FATAL: %s not found on PATH' % cmd[0])
    if check and p.returncode != 0:
        die(EXIT_FAIL, 'FATAL: %s failed:\n%s%s' % (' '.join(cmd[:3]), p.stdout, p.stderr))
    return p


def sha(path, algo):
    h = hashlib.new(algo)
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 16), b''):
            h.update(chunk)
    return h.hexdigest()


# ---------------------------------------------------------------- upstream jar acquisition

def pinned_jars(pins):
    files = {}
    files.update(pins['upstream_jars']['files'])
    files.update(pins['upstream_surface_jars']['files'])
    return files


def ensure_jars(pins, cache):
    os.makedirs(cache, exist_ok=True)
    out = {}
    for name, spec in sorted(pinned_jars(pins).items()):
        path = os.path.join(cache, name)
        if not (os.path.exists(path) and sha(path, 'sha256') == spec['sha256']):
            part = path + '.part'
            published_sha1 = ''
            try:
                with urllib.request.urlopen(spec['url'], timeout=FETCH_TIMEOUT) as r, \
                        open(part, 'wb') as out:
                    shutil.copyfileobj(r, out)
                with urllib.request.urlopen(spec['url'] + '.sha1', timeout=FETCH_TIMEOUT) as r:
                    published_sha1 = r.read().decode().split()[0].strip()
            except Exception as e:  # network or HTTP failure: fail closed, never skip
                if os.path.exists(part):
                    os.remove(part)
                die(EXIT_NOT_RUN, 'NOT_RUN: cannot fetch %s (%s) and no verified cached copy exists'
                    % (spec['url'], e))
            if sha(part, 'sha1') != published_sha1:
                os.remove(part)
                die(EXIT_FAIL, 'FAIL: %s does not match the SHA-1 published by Maven Central' % name)
            if sha(part, 'sha256') != spec['sha256']:
                got = sha(part, 'sha256')
                os.remove(part)
                die(EXIT_FAIL, 'FAIL: %s SHA-256 %s differs from the pin %s in config/parity-pins.json'
                    % (name, got, spec['sha256']))
            os.replace(part, path)
        out[name] = path
    return out


def extract(jars, dest):
    os.makedirs(dest, exist_ok=True)
    for j in jars:
        with zipfile.ZipFile(j) as z:
            for n in z.namelist():
                if n.startswith('com/') and n.endswith('.class'):
                    z.extract(n, dest)
    return dest


def class_names(d):
    out = []
    for r, _, fs in os.walk(d):
        for f in fs:
            if f.endswith('.class'):
                out.append(os.path.relpath(os.path.join(r, f), d)[:-6].replace('/', '.'))
    return sorted(out)


# ---------------------------------------------------------------- javap parsing

def strip_generics(s):
    depth, out = 0, []
    for ch in s:
        if ch == '<':
            depth += 1
        elif ch == '>':
            depth -= 1
        elif depth == 0:
            out.append(ch)
    return ''.join(out)


HDR = re.compile(r'^((?:(?:public|protected|private|abstract|final|static|strictfp|sealed) )*)'
                 r'(class|interface|enum|@interface) ([\w.$]+)(.*)\{$')


JAVAP_BATCH = 80


def parse_javap(cp, names):
    """Returns {class: {'mods': set, 'supers': [names], 'members': {(kind,name,desc): info}}}.

    The javap batches are independent processes, so they run concurrently (the run is dominated
    by waiting on them)."""
    res = {}
    batches = [names[i:i + JAVAP_BATCH] for i in range(0, len(names), JAVAP_BATCH)]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        outputs = list(pool.map(
            lambda b: run(['javap', '-protected', '-s', '-cp', cp] + b, check=False).stdout, batches))
    for out in outputs:
        lines = out.split('\n')
        cur = None
        j = 0
        while j < len(lines):
            line = lines[j]
            m = HDR.match(line) if line and not line.startswith(' ') else None
            if m:
                rest = strip_generics(m.group(4))
                supers = []
                for part in re.split(r'\bextends\b|\bimplements\b', rest):
                    for s in part.split(','):
                        s = s.strip()
                        if s:
                            supers.append(s)
                cur = m.group(3)
                res[cur] = {'mods': set(m.group(1).split()), 'kind': m.group(2),
                            'supers': supers, 'members': {}}
            elif cur and line.startswith('  ') and not line.startswith('   ') \
                    and j + 1 < len(lines) and 'descriptor:' in lines[j + 1]:
                decl = line.strip().rstrip(';')
                throws = []
                if ' throws ' in decl:
                    decl, t = decl.split(' throws ', 1)
                    throws = [x.strip() for x in strip_generics(t).split(',') if x.strip()]
                desc = lines[j + 1].split('descriptor:')[1].strip()
                head = strip_generics(decl.split('(')[0]) if '(' in decl else strip_generics(decl)
                toks = head.split()
                mods = {t for t in toks if t in ('public', 'protected', 'static', 'final', 'abstract',
                                                 'default', 'synchronized', 'native')}
                name = toks[-1]
                if '(' in decl:
                    kind = 'C' if name == cur else 'M'
                    if kind == 'C':
                        name = '<init>'
                else:
                    kind = 'F'
                res[cur]['members'][(kind, name, desc)] = {
                    'mods': mods, 'throws': throws, 'decl': strip_generics(decl)}
                j += 1
            j += 1
    return res


def jtype(d):
    arr = 0
    while d.startswith('['):
        arr += 1
        d = d[1:]
    prim = {'B': 'byte', 'C': 'char', 'D': 'double', 'F': 'float', 'I': 'int', 'J': 'long',
            'S': 'short', 'Z': 'boolean', 'V': 'void'}
    t = prim[d] if d in prim else d[1:-1].replace('/', '.')
    return t + '[]' * arr


def pretty_desc(desc):
    if desc.startswith('('):
        dm = re.match(r'\((.*)\)(.*)', desc)
        if dm is None:
            return desc
        params, ret = dm.groups()
        out, i = [], 0
        while i < len(params):
            j = i
            while params[j] == '[':
                j += 1
            j = params.index(';', j) + 1 if params[j] == 'L' else j + 1
            out.append(jtype(params[i:j]))
            i = j
        return '(' + ','.join(out) + ')' + jtype(ret)
    return jtype(desc)


# ---------------------------------------------------------------- Kotlin source scan (internal)

KT_DECL = re.compile(r'^(\s*)((?:[\w@]+(?:\([^)]*\))?\s+)*?)(?:class|object|interface)\s+(\w+)([^\n]*)')
KT_INTERNAL_FUN = re.compile(r'^((?:[\w@]+(?:\([^)]*\))?\s+)*?)fun\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(\w+)\s*\(')


def kotlin_internal(src_root):
    """Scans the pinned upstream Kotlin sources for what Kotlin `internal` hides from users.

    Returns (classes, constructors, functions):
      classes       {package: {name, ...}} internal classes, including classes nested in them
                    (named by their JVM nesting, Outer$Inner)
      constructors  {package: {class name, ...}} classes with an `internal constructor`
      functions     {package: {file stem, ...}} files that declare an internal top-level function,
                    and {package: {function names}} under the key '#fun'
    """
    if not os.path.isdir(src_root):
        die(EXIT_NOT_RUN, 'NOT_RUN: pinned upstream sources not found at %s (scripts/fetch-upstream.sh)'
            % src_root)
    classes, ctors, funs = {}, {}, {}
    for module in ('wire-runtime', 'wire-schema', 'wire-java-generator', 'wire-compiler'):
        for root_dir, _, files in os.walk(os.path.join(src_root, module, 'src')):
            if '/commonTest' in root_dir or '/jvmTest' in root_dir or '/test' in root_dir:
                continue
            for f in files:
                if not f.endswith('.kt'):
                    continue
                pkg = ''
                stack = []  # (indent, name, internal)
                for line in open(os.path.join(root_dir, f), errors='replace'):
                    pm = re.match(r'^package\s+([\w.]+)', line)
                    if pm:
                        pkg = pm.group(1)
                        continue
                    m = KT_DECL.match(line)
                    if m:
                        indent = len(m.group(1))
                        while stack and stack[-1][0] >= indent:
                            stack.pop()
                        mods = m.group(2).split()
                        name = m.group(3)
                        inherited = bool(stack) and stack[-1][2]
                        internal = 'internal' in mods or inherited
                        stack.append((indent, name, internal))
                        jvm = '$'.join(x[1] for x in stack)
                        if internal:
                            classes.setdefault(pkg, set()).add(jvm)
                        if re.search(r'\binternal\s+constructor\b', m.group(4)):
                            ctors.setdefault(pkg, set()).add(jvm)
                        continue
                    fm = KT_INTERNAL_FUN.match(line)
                    if fm and 'internal' in fm.group(1).split():
                        funs.setdefault(pkg, set()).add(fm.group(2))
    return classes, ctors, funs


# ---------------------------------------------------------------- comparison

class Ledger:
    def __init__(self):
        self.rows = {}
        self.match = 0
        self.skipped = 0

    def add(self, kind, cls, member, desc, status, reason, owner):
        key = (kind, cls, member, desc)
        self.rows[key] = (status, reason, owner)


def pkg_of(c):
    return c.rsplit('.', 1)[0] if '.' in c else ''


def ancestors(cls, table):
    """The class and its supertypes, nearest first, in a deterministic order."""
    order, seen, queue = [], set(), [cls]
    while queue:
        c = queue.pop(0)
        if c in seen:
            continue
        seen.add(c)
        order.append(c)
        queue.extend(table.get(c, {}).get('supers', []))
    return order


def find_member(port, pcls, key):
    """Constructors are not inherited; methods, fields and statics are (callable on the subclass)."""
    chain = [pcls] if key[0] == 'C' else ancestors(pcls, port)
    for c in chain:
        info = port.get(c, {}).get('members', {}).get(key)
        if info is not None:
            return info
    return None


def is_public(mods):
    return 'public' in mods


class Checker:
    def __init__(self, upstream, port, internal, hierarchy):
        self.up, self.port = upstream, port
        self.internal_classes, self.internal_ctors, self.internal_funs = internal
        self.hier = hierarchy
        self.ledger = Ledger()
        self.checked_cache = {}

    # --- exceptions
    def checked(self, exc):
        if exc in self.checked_cache:
            return self.checked_cache[exc]
        c = exc
        verdict = None
        for _ in range(40):
            if c is None or verdict is not None:
                break
            if c in ('java.lang.RuntimeException', 'java.lang.Error'):
                verdict = False
            elif c in ('java.lang.Exception', 'java.lang.Throwable'):
                verdict = True
            else:
                sup = self.hier.get(c)
                if sup is None:
                    die(EXIT_FAIL, 'FATAL: cannot resolve exception class %s to decide if it is checked' % c)
                c = sup
        self.checked_cache[exc] = bool(verdict)
        return bool(verdict)

    def exc_ancestors(self, exc):
        out, c = [], exc
        while c:
            out.append(c)
            c = self.hier.get(c)
        return out

    def is_subclass_of_any(self, exc, others):
        ancestors = self.exc_ancestors(exc)
        return any(o in ancestors for o in others)

    def throws_rows(self, ucls, key, uinfo, pinfo):
        ut = {t for t in uinfo['throws'] if self.checked(t)}
        pt = {t for t in pinfo['throws'] if self.checked(t)}
        name = key[1]
        desc = pretty_desc(key[2])
        # added: a port exception is fine only if it is the same as, or a subclass of, one that
        # upstream declares. removed: an upstream exception is fine if the port declares it, a
        # subclass of it (a catch still compiles) or a superclass of it.
        added = sorted(t for t in pt if not self.is_subclass_of_any(t, ut))
        removed = sorted(t for t in ut if not self.is_subclass_of_any(t, pt)
                         and not any(t in self.exc_ancestors(p) for p in pt))
        if added:
            self.ledger.add('THROWS', ucls, name, desc, 'GAP',
                            'port declares checked %s that upstream does not (callers need try/catch)'
                            % ','.join(added), OWNER_THROWS)
        if removed:
            self.ledger.add('THROWS', ucls, name, desc, 'GAP',
                            'upstream declares checked %s that the port does not (a catch of it '
                            'would not compile)' % ','.join(removed), OWNER_THROWS)

    # --- main
    def run(self):
        for ucls in sorted(self.up):
            if SYNTHETIC_CLASS.search(ucls) or ucls.endswith('$Companion'):
                continue
            uc = self.up[ucls]
            if not is_public(uc['mods']) and 'protected' not in uc['mods']:
                continue
            self.compare_class(ucls, uc)
        return self.ledger

    def public_members(self, cls_info):
        for key, info in sorted(cls_info['members'].items()):
            name, desc = key[1], key[2]
            if not (is_public(info['mods']) or 'protected' in info['mods']):
                continue
            if SYNTHETIC_MEMBER.search(name) or 'DefaultConstructorMarker' in desc:
                self.ledger.skipped += 1
                continue
            yield key, info

    def relocated(self, ucls):
        simple = ucls.rsplit('.', 1)[-1].split('$')[-1]
        pkg = pkg_of(ucls)
        cands = [c for c in self.port if pkg_of(c) == pkg and c.split('$')[-1] == simple
                 and '$' in c.rsplit('.', 1)[-1]]
        if len(cands) != 1:
            return None
        # Guard against an unrelated nested class that happens to share the simple name: the port
        # class must declare the same supertypes as the upstream class, after mapping nested names.
        def norm(names):
            return sorted(n.rsplit('.', 1)[-1].split('$')[-1] for n in names)
        if norm(self.up[ucls]['supers']) != norm(self.port[cands[0]]['supers']):
            return None
        return cands[0]

    def compare_class(self, ucls, uc):
        L = self.ledger
        if OUT_OF_SCOPE.search(ucls):
            L.add('CLASS', ucls, '', '', 'EXCLUDED',
                  'feature outside the port: JSON, Android, Kotlin or Swift generator, gRPC (DEC-6)', 'DEC-6')
            return
        jvm_name = ucls.rsplit('.', 1)[-1]
        if jvm_name in self.internal_classes.get(pkg_of(ucls), set()):
            L.add('CLASS', ucls, '', '', 'EXCLUDED',
                  'Kotlin internal class: public in bytecode, not documented API; ported only where '
                  'needed', 'kotlin-internal')
            return
        pcls = ucls if ucls in self.port else None
        reloc = None
        if pcls is None:
            reloc = self.relocated(ucls)
            pcls = reloc
        if pcls is None:
            names = [k[1] for k, _ in self.public_members(uc)]
            facade_all_internal = False
            if ucls.endswith('Kt') and names:
                # Kotlin file facade: its public static members are the file's top-level functions;
                # the `internal` ones are not API.
                hidden = self.internal_funs.get(pkg_of(ucls), set())
                names = [x for x in names if x not in hidden]
                facade_all_internal = not names
            if facade_all_internal:
                L.add('CLASS', ucls, '', '', 'EXCLUDED',
                      'Kotlin file facade whose public members are all internal functions; not API',
                      'kotlin-internal')
            else:
                L.add('CLASS', ucls, '', '', 'GAP',
                      'class absent in the port (%d public members upstream)' % len(names), OWNER_FIX)
            return
        if reloc:
            L.add('CLASS', ucls, '', '', 'GAP',
                  'relocated: the port has it as %s (imports and nested references differ)' % reloc,
                  OWNER_FIX)
        if not (is_public(self.port[pcls]['mods']) or 'protected' in self.port[pcls]['mods']):
            L.add('VISIBILITY', ucls, '', '', 'GAP', 'class is not public in the port', OWNER_FIX)
            return
        self.compare_members(ucls, uc, pcls)
        self.compare_companion(ucls, uc, pcls)

    def compare_companion(self, ucls, uc, pcls):
        L = self.ledger
        comp_name = ucls + '$Companion'
        field_key = ('F', 'Companion', 'L%s;' % comp_name.replace('.', '/'))
        finfo = uc['members'].get(field_key)
        if finfo is None or not is_public(finfo['mods']):
            return
        has_field = field_key in self.port[pcls]['members']
        has_class = (pcls + '$Companion') in self.port
        if has_field and has_class:
            return self.compare_members(comp_name, self.up[comp_name], pcls + '$Companion', companion=True)
        L.add('COMPANION', ucls, 'Companion', pretty_desc(field_key[2]), 'GAP',
              'Kotlin companion object: the port has no static Companion field and nested class '
              '(Java callers write X.Companion.m(...))', OWNER_FIX)
        cinfo = self.up.get(comp_name)
        if not cinfo:
            return
        for key, info in self.public_members(cinfo):
            kind, name, desc = key
            if kind == 'C':
                continue
            if 'kotlin/' in desc or name.startswith('-') or DATA_BRIDGE.match(name):
                continue
            if 'Lokio/' in desc:
                L.add('COMPANION_METHOD', ucls, name, pretty_desc(desc), 'EXCLUDED',
                      'okio type in the signature; the consumer API is JDK-typed (DEC-14)', 'DEC-14')
                continue
            twin = ('M', name, desc) in uc['members'] and 'static' in uc['members'][('M', name, desc)]['mods']
            ptwin = ('M', name, desc) in self.port[pcls]['members'] \
                and 'static' in self.port[pcls]['members'][('M', name, desc)]['mods']
            L.add('COMPANION_METHOD', ucls, name, pretty_desc(desc), 'GAP',
                  'Companion method; upstream %s; the port %s' % (
                      'also has a static X.m twin (@JvmStatic)' if twin else 'reaches it only as X.Companion.m',
                      'has the static equivalent' if ptwin else 'has no static equivalent'),
                  OWNER_FIX)

    def compare_members(self, ucls, uc, pcls, companion=False):
        L = self.ledger
        for key, info in self.public_members(uc):
            kind, name, desc = key
            if kind == 'F' and name == 'Companion' and not companion:
                continue
            static = 'static' in info['mods']
            label = '<init>' if kind == 'C' else name
            shown = pretty_desc(desc)
            pinfo = find_member(self.port, pcls, key)
            if pinfo is not None:
                if ('static' in pinfo['mods']) != static:
                    L.add('STATIC', ucls, label, shown, 'GAP',
                          'static-ness differs (%s upstream, %s in the port)'
                          % ('static' if static else 'instance', 'instance' if static else 'static'),
                          OWNER_FIX)
                elif is_public(info['mods']) and not is_public(pinfo['mods']):
                    L.add('VISIBILITY', ucls, label, shown, 'GAP',
                          'public upstream, %s in the port' % ','.join(sorted(pinfo['mods'])) , OWNER_FIX)
                else:
                    L.match += 1
                    if kind in ('M', 'C'):
                        self.throws_rows(ucls, key, info, pinfo)
                continue
            kind_name = {'C': 'CONSTRUCTOR', 'F': 'STATIC_FIELD' if static else 'FIELD',
                         'M': 'STATIC_METHOD' if static else 'METHOD'}[kind]
            jvm_name = ucls.rsplit('.', 1)[-1]
            if kind == 'C' and jvm_name in self.internal_ctors.get(pkg_of(ucls), set()):
                L.add(kind_name, ucls, label, shown, 'EXCLUDED',
                      'Kotlin internal constructor: public in bytecode, not callable from Kotlin '
                      'outside the module, not documented API', 'kotlin-internal')
            elif kind == 'M' and static and ucls.endswith('Kt') \
                    and name in self.internal_funs.get(pkg_of(ucls), set()):
                L.add(kind_name, ucls, label, shown, 'EXCLUDED',
                      'Kotlin internal top-level function: public in bytecode, not documented API',
                      'kotlin-internal')
            elif 'kotlin/' in desc:
                L.add(kind_name, ucls, label, shown, 'EXCLUDED',
                      'Kotlin type in the signature (KClass, Unit, Pair, ...); the port has no Kotlin '
                      'in production (DEC-4)', 'DEC-4')
            elif name.startswith('-'):
                L.add(kind_name, ucls, label, shown, 'EXCLUDED',
                      'Java-hidden Kotlin name (@JvmName with a dash); not callable from Java source',
                      'java-hidden')
            elif DATA_BRIDGE.match(name):
                L.add(kind_name, ucls, label, shown, 'EXCLUDED',
                      'Kotlin data-class bridge; ported only where adapted tests need it (DEC-5)', 'DEC-5')
            elif 'Lokio/' in desc:
                L.add(kind_name, ucls, label, shown, 'EXCLUDED',
                      'okio type in the signature; the consumer API is JDK-typed (DEC-14)', 'DEC-14')
            elif 'Lcom/palantir/javapoet/' in desc or 'Lcom/squareup/kotlinpoet/' in desc:
                L.add(kind_name, ucls, label, shown, 'EXCLUDED',
                      'poet type in the signature: upstream uses the Palantir fork of JavaPoet (Java 17 '
                      'class files); the port uses Square JavaPoet 1.13.0 for the Java 11 floor (DEC-3, '
                      'parent pom javapoet.version)', 'DEC-3')
            elif 'Lcom/google/common/' in desc:
                L.add(kind_name, ucls, label, shown, 'EXCLUDED',
                      'Guava type in the signature; the port has no Guava (pure-Java dependency '
                      'policy, DEC-4)', 'DEC-4')
            elif OUT_OF_SCOPE_MEMBER.match(name):
                L.add(kind_name, ucls, label, shown, 'EXCLUDED',
                      'member of a feature outside the port: Kotlin or Swift generator, Android output '
                      '(DEC-6)', 'DEC-6')
            else:
                same = [k for k in self.port[pcls]['members'] if k[0] == kind and k[1] == name]
                if same:
                    L.add(kind_name, ucls, label, shown, 'GAP',
                          'same name, other signature in the port: ' + '; '.join(
                              sorted(pretty_desc(k[2]) for k in same)), OWNER_FIX)
                else:
                    L.add(kind_name, ucls, label, shown, 'GAP', 'member absent in the port', OWNER_FIX)


# ---------------------------------------------------------------- consumer snippets

EXPECT = re.compile(
    r'^//\s*surface-expect:\s*port=(pass|fail)(?:\s+owner=(\S+))?(?:\s+symbol=(\S+))?'
    r'(?:\s+reason=(.*))?$', re.M)


def javac(path, cp):
    with tempfile.TemporaryDirectory() as out:
        return run(['javac', '-proc:none', '-Xlint:none', '-d', out, '-cp', cp, path], check=False)


def compile_both(path, up_cp, port_cp):
    """Compile one snippet against the upstream jars and the port jars, concurrently."""
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        up = pool.submit(javac, path, up_cp)
        port = pool.submit(javac, path, port_cp)
        return {'upstream': up.result(), 'port': port.result()}


def compile_snippets(snippets_dir, up_cp, port_cp, ledger, problems):
    if not os.path.isdir(snippets_dir):
        problems.append('snippet directory %s is missing' % snippets_dir)
        return 0, 0
    passed_port = failed_port = 0
    for f in sorted(os.listdir(snippets_dir)):
        if not f.endswith('.java'):
            continue
        path = os.path.join(snippets_dir, f)
        src = open(path).read()
        m = EXPECT.search(src)
        if not m:
            problems.append('%s has no "// surface-expect: port=pass|fail" header' % f)
            continue
        expect_fail = m.group(1) == 'fail'
        owner, symbol, reason = m.group(2), m.group(3), (m.group(4) or '').strip()
        if expect_fail and not (owner and symbol and reason):
            problems.append('%s expects a failure on the port but records no owner, symbol and reason'
                            % f)
        res = compile_both(path, up_cp, port_cp)
        if res['upstream'].returncode != 0:
            problems.append('%s does not compile against the real upstream jars (the snippet is invalid):\n%s'
                            % (f, res['upstream'].stderr.strip()))
            continue
        port_ok = res['port'].returncode == 0
        if port_ok:
            passed_port += 1
        else:
            failed_port += 1
        if expect_fail and port_ok:
            problems.append('%s is recorded as an expected port failure (%s) but now compiles: '
                            'remove the marker' % (f, owner))
        elif expect_fail and not port_ok and symbol and symbol not in res['port'].stderr:
            problems.append('%s fails on the port, but not for the recorded symbol %r (the failure '
                            'is vacuous or has another cause):\n%s'
                            % (f, symbol, res['port'].stderr.strip()))
        elif not expect_fail and not port_ok:
            problems.append('%s compiles against upstream but not against the port and has no '
                            'expected-failure marker:\n%s' % (f, res['port'].stderr.strip()))
        if expect_fail and not port_ok:
            ledger.add('SNIPPET', f, '', '', 'GAP', reason, owner)
    return passed_port, failed_port


# ---------------------------------------------------------------- baseline I/O

HEADER = ('# Generated by scripts/surface-check.py from the real Wire 7.1.0 jars and the port jars. '
          'Review every change.\n'
          '# Compatibility target: source compatibility for members expressible without Kotlin (DEC-4) '
          'and without okio (DEC-14); binary compatibility is not promised (DEC-2).\n'
          '# kind\tclass\tmember\tdescriptor\tstatus\treason\towner\n')


def read_baseline(path):
    rows = {}
    if not os.path.exists(path):
        return rows
    for line in open(path):
        if line.startswith('#') or not line.strip():
            continue
        parts = line.rstrip('\n').split('\t')
        if len(parts) != 7:
            die(EXIT_FAIL, 'FAIL: malformed baseline row: %r' % line)
        rows[tuple(parts[:4])] = tuple(parts[4:])
    return rows


def write_baseline(path, rows, match):
    with open(path, 'w') as f:
        f.write(HEADER)
        f.write('# MATCH rows are not stored: %d public or protected upstream members matched exactly\n'
                % match)
        for key in sorted(rows):
            f.write('\t'.join(list(key) + list(rows[key])) + '\n')


def summarize(rows):
    """{(status, owner): {kind: count}} for the --report output."""
    by = {}
    for key, (status, _reason, owner) in rows.items():
        kinds = by.setdefault((status, owner), {})
        kinds[key[0]] = kinds.get(key[0], 0) + 1
    return by


# ---------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--port-jar', action='append', required=True)
    ap.add_argument('--pins', default=os.path.join(ROOT, 'config/parity-pins.json'))
    ap.add_argument('--baseline', default=os.path.join(ROOT, 'config/surface-baseline.tsv'))
    ap.add_argument('--cache', default=os.path.join(ROOT, 'target/surface-check/jars'))
    ap.add_argument('--upstream-src', default=os.environ.get('ANTIWIRE_UPSTREAM', ''))
    ap.add_argument('--snippets', default=os.path.join(ROOT, 'scripts/surface-consumer'))
    ap.add_argument('--update', action='store_true')
    ap.add_argument('--allow-new', action='store_true',
                    help='with --update: accept rows that are not in the current baseline')
    ap.add_argument('--report', action='store_true')
    a = ap.parse_args()

    pins = json.load(open(a.pins))
    src = a.upstream_src or pins['clone_path_default']
    for j in a.port_jar:
        if not os.path.isfile(j):
            die(EXIT_NOT_RUN, 'NOT_RUN: port jar %s is missing (run mvn verify first)' % j)
    jars = ensure_jars(pins, a.cache)

    with tempfile.TemporaryDirectory() as tmp:
        up_dir = extract([jars[n] for n in UPSTREAM_API_JARS], os.path.join(tmp, 'up'))
        port_dir = extract(a.port_jar, os.path.join(tmp, 'port'))
        up = parse_javap(up_dir, class_names(up_dir))
        port = parse_javap(port_dir, class_names(port_dir))

        # Exception hierarchy: JDK + port + upstream + okio, resolved through javap on demand.
        hier = {}
        full_cp = os.pathsep.join([up_dir, port_dir, jars['okio.jar'], jars['kotlin-stdlib.jar']])
        wanted = set()
        for table in (up, port):
            for c in table.values():
                for info in c['members'].values():
                    wanted.update(info['throws'])
        todo = sorted(wanted)
        while todo:
            batch, todo = todo[:60], todo[60:]
            parsed = parse_javap(full_cp, batch)
            for name in batch:
                if name in parsed:
                    sup = [s for s in parsed[name]['supers']]
                    hier[name] = sup[0] if sup else None
                    if sup and sup[0] not in hier and sup[0] not in todo \
                            and sup[0] not in ('java.lang.Throwable', 'java.lang.Exception',
                                               'java.lang.RuntimeException', 'java.lang.Error'):
                        todo.append(sup[0])

        internal = kotlin_internal(src)
        checker = Checker(up, port, internal, hier)
        ledger = checker.run()

        problems = []
        up_cp = os.pathsep.join(jars[n] for n in SNIPPET_CLASSPATH_JARS)
        port_cp = os.pathsep.join(os.path.abspath(j) for j in a.port_jar)
        sp, sf = compile_snippets(a.snippets, up_cp, port_cp, ledger, problems)

    rows = ledger.rows
    baseline = read_baseline(a.baseline)
    new = sorted(k for k in rows if k not in baseline)
    stale = sorted(k for k in baseline if k not in rows)
    changed = sorted(k for k in rows if k in baseline and rows[k] != baseline[k])

    gaps = sum(1 for v in rows.values() if v[0] == 'GAP')
    excl = sum(1 for v in rows.values() if v[0] == 'EXCLUDED')
    summary = ('MATCH=%d GAP=%d EXCLUDED=%d skipped-synthetic=%d snippets(port ok/failing)=%d/%d'
               % (ledger.match, gaps, excl, ledger.skipped, sp, sf))

    if a.report or a.update:
        print('Surface report (%s)' % summary)
        for (status, owner), kinds in sorted(summarize(rows).items()):
            print('  %-8s %-18s %s' % (status, owner, ', '.join('%s=%d' % kv for kv in sorted(kinds.items()))))

    if a.update:
        if problems:
            print('REFUSED: %d snippet problem(s) must be fixed before the baseline is written:'
                  % len(problems))
            for p in problems:
                print('  ', p)
            sys.exit(EXIT_FAIL)
        if (new or changed or stale) and not a.allow_new and baseline:
            print('REFUSED: the ledger would change (%d new, %d changed, %d removed); review the '
                  'differences below and re-run with --allow-new:' % (len(new), len(changed), len(stale)))
            for k in new[:40]:
                print('  NEW     ', '\t'.join(k), '|', rows[k][0], rows[k][2])
            for k in changed[:40]:
                print('  CHANGED ', '\t'.join(k), '|', baseline[k], '->', rows[k])
            for k in stale[:40]:
                print('  REMOVED ', '\t'.join(k), '|', baseline[k])
            sys.exit(EXIT_FAIL)
        write_baseline(a.baseline, rows, ledger.match)
        print('baseline written: %d rows (%d new, %d removed, %d changed)'
              % (len(rows), len(new), len(stale), len(changed)))
        sys.exit(EXIT_OK)

    fail = bool(problems or new or stale or changed)
    for p in problems:
        print('SNIPPET PROBLEM:', p)
    for k in new[:40]:
        print('UNEXPECTED (not in the baseline): %s | %s | %s' % ('\t'.join(k), rows[k][0], rows[k][1]))
    for k in stale[:40]:
        print('STALE baseline row (no longer a difference, remove it): %s' % '\t'.join(k))
    for k in changed[:40]:
        print('CHANGED row: %s\n  baseline: %s\n  now:      %s' % ('\t'.join(k), baseline[k], rows[k]))
    if len(new) > 40 or len(stale) > 40 or len(changed) > 40:
        print('(output truncated)')
    print('SUMMARY %s; baseline rows=%d; unexpected=%d stale=%d changed=%d snippet-problems=%d'
          % (summary, len(baseline), len(new), len(stale), len(changed), len(problems)))
    sys.exit(EXIT_FAIL if fail else EXIT_OK)


if __name__ == '__main__':
    main()
