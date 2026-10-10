#!/usr/bin/env python3
"""Scans compiled consumer jars for references to Wire and reports which ones the port lacks (TASK-33.2).

A binary consumer (for example Confluent's kafka-protobuf-provider) was compiled against Wire and
links to its members by owner, name and descriptor. This script reads the constant pool of every
class in the given jars with `javap -v`, collects each Methodref, InterfaceMethodref and Fieldref
whose owner is in com.squareup.wire.*, and checks it against the port's module jars (inherited
members included). It prints the distinct references, how many resolve, and the ones that do not.

Usage: scripts/scan-consumer-jars.py --port-jar <jar> [--port-jar ...] <consumer.jar> [...]
Exit codes: 0 always when it ran (it reports, it does not judge), 3 when javap or a jar is missing.
"""
import argparse
import collections
import os
import re
import subprocess
import sys
import tempfile
import zipfile

REF = re.compile(r'^\s+#\d+ = (Methodref|InterfaceMethodref|Fieldref)\s+#\d+\.#\d+\s+// +(.*)$', re.M)
SPLIT = re.compile(r'^([\w/$]+)\.(.+?):(.*)$')
ENUM_AND_OBJECT_METHODS = {'name', 'ordinal', 'values', 'valueOf', 'compareTo', 'getDeclaringClass'}


def javap_members(jars, wanted):
    """{binary class name: (set of (name, descriptor, kind), [supertypes])} for the port classes."""
    cp = os.pathsep.join(jars)
    members, supers = {}, {}
    names = sorted(wanted)
    for i in range(0, len(names), 80):
        out = subprocess.run(['javap', '-p', '-s', '-cp', cp] + names[i:i + 80],
                             capture_output=True, text=True).stdout.split('\n')
        cur = None
        j = 0
        while j < len(out):
            line = out[j]
            m = re.search(r'(?:class|interface|enum) ([\w.$]+)', line) if line and not line.startswith(' ') \
                and '{' in line else None
            if m:
                cur = m.group(1)
                members[cur] = set()
                supers[cur] = [re.sub(r'<.*>', '', s).strip()
                               for part in re.findall(r'(?:extends|implements) ([\w.$<>, ?]+)', line)
                               for s in re.split(r',\s*(?![^<]*>)', part) if s.strip()]
            elif cur and line.startswith('  ') and not line.startswith('   ') and j + 1 < len(out) \
                    and 'descriptor:' in out[j + 1]:
                decl = line.strip().rstrip(';').split(' throws ')[0]
                desc = out[j + 1].split('descriptor:')[1].strip()
                if '(' in decl:
                    name = decl.split('(')[0].split()[-1]
                    if name == cur or name.split('.')[-1] == cur.split('.')[-1].split('$')[-1]:
                        name = '<init>'
                    members[cur].add((name, desc))
                else:
                    members[cur].add((decl.split()[-1], desc))
                j += 1
            j += 1
    return members, supers


def resolves(members, supers, owner, name, desc, seen=None):
    if (name, desc) in members.get(owner, ()):
        return True
    seen = seen or set()
    for s in supers.get(owner, ()):
        if s not in seen:
            seen.add(s)
            if resolves(members, supers, s, name, desc, seen):
                return True
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--port-jar', action='append', required=True)
    ap.add_argument('jars', nargs='+')
    a = ap.parse_args()
    for j in a.port_jar + a.jars:
        if not os.path.isfile(j):
            print('NOT_RUN: missing jar %s' % j, file=sys.stderr)
            return 3
    refs = collections.defaultdict(set)  # (owner, name, desc) -> jars
    for jar in a.jars:
        with tempfile.TemporaryDirectory() as tmp:
            with zipfile.ZipFile(jar) as z:
                classes = [n for n in z.namelist() if n.endswith('.class') and not n.startswith('META-INF')]
                z.extractall(tmp, classes)
            for i in range(0, len(classes), 100):
                out = subprocess.run(['javap', '-v', '-p', '-cp', tmp]
                                     + [c[:-6].replace('/', '.') for c in classes[i:i + 100]],
                                     capture_output=True, text=True).stdout
                for _, ref in REF.findall(out):
                    m = SPLIT.match(ref.strip())
                    if m and m.group(1).startswith('com/squareup/wire/'):
                        owner, name, desc = m.group(1).replace('/', '.'), m.group(2).strip('"'), m.group(3)
                        refs[(owner, name, desc)].add(os.path.basename(jar))
    owners = {o for o, _, _ in refs}
    port_classes = set()
    for jar in a.port_jar:
        with zipfile.ZipFile(jar) as z:
            port_classes |= {n[:-6].replace('/', '.') for n in z.namelist() if n.endswith('.class')}
    members, supers = javap_members(a.port_jar, sorted(owners & port_classes))
    # java.lang.Enum members are inherited by every enum: a port enum resolves them by construction.
    enum_owners = {c for c, sup in supers.items() if 'java.lang.Enum' in sup}
    missing = []
    for (owner, name, desc), jars in sorted(refs.items()):
        if name in ('equals', 'hashCode', 'toString'):
            continue
        if name in ENUM_AND_OBJECT_METHODS and owner in enum_owners:
            continue
        if owner not in port_classes:
            missing.append((owner, name, desc, jars, 'class absent in the port'))
        elif not resolves(members, supers, owner, name, desc):
            missing.append((owner, name, desc, jars, 'member absent'))
    total = len([k for k in refs if k[1] not in ('equals', 'hashCode', 'toString')])
    print('SCAN consumers=%d distinct wire references=%d resolved=%d unresolved=%d'
          % (len(a.jars), total, total - len(missing), len(missing)))
    for owner, name, desc, jars, why in missing:
        print('  MISSING %s.%s %s [%s] (%s)' % (owner, name, desc, why, ','.join(sorted(jars))))
    return 0


if __name__ == '__main__':
    sys.exit(main())
