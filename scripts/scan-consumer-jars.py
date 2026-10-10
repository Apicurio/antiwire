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
import importlib.util
import os
import re
import subprocess
import sys
import tempfile
import zipfile


def _surface_check():
    """The javap parser and member lookup of surface-check.py are reused, not copied."""
    spec = importlib.util.spec_from_file_location(
        'surface_check', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'surface-check.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

REF = re.compile(r'^\s+#\d+ = (Methodref|InterfaceMethodref|Fieldref)\s+#\d+\.#\d+\s+// +(.*)$', re.M)
SPLIT = re.compile(r'^([\w/$]+)\.(.+?):(.*)$')
ENUM_METHODS = {'name', 'ordinal', 'values', 'valueOf', 'compareTo', 'getDeclaringClass'}


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
    sc = _surface_check()
    table = sc.parse_javap(os.pathsep.join(a.port_jar), sorted(owners & port_classes))
    missing = []
    for (owner, name, desc), jars in sorted(refs.items()):
        if name in ('equals', 'hashCode', 'toString'):
            continue
        if owner not in port_classes:
            missing.append((owner, name, desc, jars, 'class absent in the port'))
            continue
        # java.lang.Enum members are inherited by every enum: a port enum resolves them by construction.
        if name in ENUM_METHODS and 'java.lang.Enum' in table.get(owner, {}).get('supers', []):
            continue
        kind = 'F' if not desc.startswith('(') else ('C' if name == '<init>' else 'M')
        if sc.find_member(table, owner, (kind, name, desc)) is None:
            missing.append((owner, name, desc, jars, 'member absent'))
    total = len([k for k in refs if k[1] not in ('equals', 'hashCode', 'toString')])
    print('SCAN consumers=%d distinct wire references=%d resolved=%d unresolved=%d'
          % (len(a.jars), total, total - len(missing), len(missing)))
    for owner, name, desc, jars, why in missing:
        print('  MISSING %s.%s %s [%s] (%s)' % (owner, name, desc, why, ','.join(sorted(jars))))
    return 0


if __name__ == '__main__':
    sys.exit(main())
