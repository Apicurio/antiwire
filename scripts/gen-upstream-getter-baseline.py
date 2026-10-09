#!/usr/bin/env python3
"""Regenerates the upstream-getter baseline used by AccessorNameParityTest.

Reads the four real Wire 7.1.0 jars (wire-schema-jvm, wire-runtime-jvm, wire-java-generator,
wire-compiler) from <jar-dir> and the built port classes from the three module target/classes
directories, runs javap on both, and writes one TSV row per public non-static upstream
getX()/isX() method that exists on a ported public class:

  EXPECTED  <binary class name>  <method>  <param types>  <return type>   (must exist in the port)
  GAP       <binary class name>  <method>  <param types>  <return type>  <reason>   (known absent)

Usage: scripts/gen-upstream-getter-baseline.py <jar-dir> [output.tsv]
Requires: javap on PATH, the port built (mvn package). The jars come from Maven Central
(com.squareup.wire:*:7.1.0); their SHA-256 values are printed so the run can be checked.
"""
import hashlib, os, re, subprocess, sys, tempfile, zipfile

JARS = ['wire-schema-jvm', 'wire-runtime-jvm', 'wire-java-generator', 'wire-compiler']
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
    ROOT, 'wire-java-generator/src/test/resources/upstream-7.1.0-getters.tsv')
SKIP = re.compile(r'Json|Android|Manifest|KotlinConstructor|\$Companion|KotlinTarget|SwiftTarget'
                  r'|WhenMappings|\$DefaultImpls|\$\d')
ALLOWED_GAP = [
    (re.compile(r'kotlin/'), 'Kotlin type in the descriptor (no Java equivalent in the port)'),
    (re.compile(r'\.WireCompiler$'), 'WireCompiler option of a feature outside the port (Kotlin and '
     'Swift generators, DEC-6) or state the port keeps package-private'),
]

def run(cmd):
    return subprocess.run(cmd, capture_output=True, text=True, check=True).stdout

def classes(d):
    for r, _, fs in os.walk(d):
        for f in fs:
            if f.endswith('.class'):
                yield os.path.relpath(os.path.join(r, f), d)[:-6].replace('/', '.')

def members(cp, names):
    res = {}
    for i in range(0, len(names), 50):
        lines = run(['javap', '-protected', '-s', '-cp', cp] + names[i:i + 50]).split('\n')
        cur = None
        for j, l in enumerate(lines):
            m = re.match(r'^(?:public |protected |abstract |final |static )*(?:class|interface|enum) ([\w.$]+)', l)
            if m:
                cur = m.group(1); res[cur] = {'hdr': l, 'm': {}}
            elif cur and l.startswith('  ') and not l.startswith('   ') and j + 1 < len(lines) \
                    and 'descriptor:' in lines[j + 1] and '(' in l:
                decl = l.strip().rstrip(';').split(' throws ')[0]
                name = decl.split('(')[0].split()[-1]
                mods = decl.split('(')[0].split()
                res[cur]['m'][(name, lines[j + 1].split('descriptor:')[1].strip())] = mods
    return res

def jtype(d):
    arr = 0
    while d.startswith('['): arr += 1; d = d[1:]
    prim = {'B': 'byte', 'C': 'char', 'D': 'double', 'F': 'float', 'I': 'int', 'J': 'long',
            'S': 'short', 'Z': 'boolean', 'V': 'void'}
    if d in prim: t = prim[d]
    else: t = d[1:-1].replace('/', '.')
    return t + '[]' * arr

def split_desc(desc):
    params, ret = re.match(r'\((.*)\)(.*)', desc).groups()
    out = []; i = 0
    while i < len(params):
        j = i
        while params[j] == '[': j += 1
        j = params.index(';', j) + 1 if params[j] == 'L' else j + 1
        out.append(jtype(params[i:j])); i = j
    return out, jtype(ret)

with tempfile.TemporaryDirectory() as tmp:
    for jar in JARS:
        p = os.path.join(sys.argv[1], jar + '.jar')
        print('sha256', hashlib.sha256(open(p, 'rb').read()).hexdigest(), jar + '.jar', file=sys.stderr)
        zipfile.ZipFile(p).extractall(os.path.join(tmp, 'up'))
    port = os.path.join(tmp, 'port'); os.makedirs(port)
    for m in ('wire-runtime-java', 'wire-schema-java', 'wire-java-generator'):
        subprocess.run(['cp', '-r', os.path.join(ROOT, m, 'target/classes') + '/.', port], check=True)
    up_names = sorted(c for c in classes(os.path.join(tmp, 'up')) if c.startswith('com.squareup.wire') and not re.search(r'\$\d|WhenMappings', c))
    U = members(os.path.join(tmp, 'up'), up_names)
    P = members(port, sorted(classes(port)))
    rows = []
    for c, info in sorted(U.items()):
        pre = re.split(r'\b(?:class|interface|enum)\b', info['hdr'])[0]
        if SKIP.search(c) or 'public' not in pre or c not in P: continue
        for (name, desc), mods in sorted(info['m'].items()):
            if not re.match(r'^(get|is)[A-Z]', name) or 'public' not in mods or 'static' in mods or '$' in name: continue
            params, ret = split_desc(desc)
            key = (name, desc)
            if key in P[c]['m'] and 'public' in P[c]['m'][key]:
                rows.append(('EXPECTED', c, name, ','.join(params), ret, ''))
            else:
                reason = next((r for pat, r in ALLOWED_GAP if (pat.search(desc) if pat.pattern.startswith('kotlin') else pat.search(c))), None)
                if reason is None and name == 'getKeyAdapter' and desc.endswith(')Ljava/lang/Void;'):
                    reason = 'upstream returns Void, the port returns the real ProtoAdapter (return type differs)'
                if reason is None and name == 'getJsonName' and c.endswith('.RuntimeMessageAdapter'):
                    reason = 'Kotlin extension-style helper taking a binding, no port member'
                if reason is None:
                    pcls = re.split(r'\b(?:class|interface|enum)\b', P[c]['hdr'])[0]
                    if 'public' not in pcls:
                        reason = 'class is package-private in the port (upstream: Kotlin internal visibility)'
                if reason is None:
                    reason = 'UNEXPLAINED'
                rows.append(('GAP', c, name, ','.join(params), ret, reason))
unexplained = [r for r in rows if r[5] == 'UNEXPLAINED']
with open(OUT, 'w') as f:
    f.write('# Generated by scripts/gen-upstream-getter-baseline.py from the Wire 7.1.0 jars; do not edit.\n')
    f.write('# kind\tclass\tmethod\tparams\treturn\treason\n')
    for r in rows: f.write('\t'.join(r) + '\n')
print('expected', sum(r[0] == 'EXPECTED' for r in rows), 'gap', sum(r[0] == 'GAP' for r in rows),
      'unexplained', len(unexplained), file=sys.stderr)
for r in unexplained: print('UNEXPLAINED', r[1], r[2], file=sys.stderr)
sys.exit(1 if unexplained else 0)
