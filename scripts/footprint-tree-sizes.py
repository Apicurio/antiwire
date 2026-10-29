#!/usr/bin/env python3
"""Sums a module's runtime classpath from `mvn dependency:tree` output (docs/footprint.md, section 4).

Usage:
  scripts/footprint-tree-sizes.py --repo <m2-root> [--repo ...] --module <artifactId> \
      <base-tree.txt> [<after-tree.txt>]
Exit codes: 0 when it ran, 3 when a tree, the module, or a jar file cannot be found.
"""
import argparse
import os
import re
import sys

ROOT = re.compile(r'^[\w.-]+:[\w.-]+:[\w.-]+:')
NODE = re.compile(r'^((?:[| ]  )*)[+\\]- (\S+)')
NOT_ON_CLASSPATH = ('test', 'provided', 'system')
JAR_TYPES = ('jar', 'test-jar', 'bundle', 'ejb', 'maven-plugin')


def module_blocks(path):
    """Splits an appended tree file into {artifactId: [lines]}, one block per module root."""
    blocks, current = {}, None
    with open(path) as f:
        for line in f.read().splitlines():
            if ROOT.match(line):
                current = line.split(':')[1]
                blocks[current] = [line]
            elif current is not None:
                blocks[current].append(line)
    return blocks


def runtime_jars(block):
    """Jars of a module's tree: test, provided and system subtrees and the module itself left out."""
    own = block[0].split(':')[1]
    jars, pruned_depth = set(), None
    for line in block[1:]:
        m = NODE.match(line)
        if not m:
            continue
        depth = len(m.group(1)) // 3
        if pruned_depth is not None and depth > pruned_depth:
            continue
        pruned_depth = None
        if m.group(2).startswith('('):
            continue  # verbose mode: '(g:a:...:scope - omitted for duplicate)' is not on the classpath
        parts = m.group(2).split(':')
        if len(parts) not in (5, 6):
            print(f'unrecognised tree node, not counted: {m.group(2)}', file=sys.stderr)
            continue
        group, artifact, packaging, version, scope = parts[0], parts[1], parts[2], parts[-2], parts[-1]
        classifier = parts[3] if len(parts) == 6 else ''
        if scope in NOT_ON_CLASSPATH:
            pruned_depth = depth
            continue
        if artifact == own:
            continue
        if packaging not in JAR_TYPES:
            print(f'non-jar dependency type {packaging}, not counted: {m.group(2)}', file=sys.stderr)
            continue
        jars.add((group, artifact, version, classifier))
    return jars


def jar_size(repos, group, artifact, version, classifier):
    suffix = f'-{classifier}' if classifier else ''
    rel = os.path.join(group.replace('.', '/'), artifact, version, f'{artifact}-{version}{suffix}.jar')
    for repo in repos:
        path = os.path.join(repo, rel)
        if os.path.isfile(path):
            return os.path.getsize(path)
    return None


def label(jar):
    group, artifact, version, classifier = jar
    return ':'.join(p for p in (group, artifact, version, classifier) if p)


def measure(path, module, repos):
    blocks = module_blocks(path)
    if module not in blocks:
        print(f'module {module} not found in {path} (modules: {", ".join(blocks)})', file=sys.stderr)
        sys.exit(3)
    jars = runtime_jars(blocks[module])
    sizes = {}
    for jar in sorted(jars):
        size = jar_size(repos, *jar)
        if size is None:
            print(f'MISSING jar file for {label(jar)}', file=sys.stderr)
            sys.exit(3)
        sizes[jar] = size
    return sizes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--repo', action='append', required=True)
    ap.add_argument('--module', required=True)
    ap.add_argument('base')
    ap.add_argument('after', nargs='?')
    args = ap.parse_args()
    for tree in (args.base, args.after):
        if tree and not os.path.isfile(tree):
            print(f'tree file not found: {tree}', file=sys.stderr)
            sys.exit(3)
    base = measure(args.base, args.module, args.repo)
    before_total = sum(base.values())
    print(f'base: {len(base)} jars, {before_total:,} bytes')
    if not args.after:
        return
    after = measure(args.after, args.module, args.repo)
    after_total = sum(after.values())
    net = before_total - after_total
    print(f'after: {len(after)} jars, {after_total:,} bytes')
    share = f' ({100 * abs(net) / before_total:.1f}%)' if before_total else ''
    print(f'net: {"minus" if net >= 0 else "plus"} {abs(net):,} bytes{share}')
    # A version change is one changed jar, not a removal plus an addition (the report's tables
    # list version moves separately).
    key = lambda jar: (jar[0], jar[1], jar[3])
    base_by_key = {key(j): j for j in base}
    after_by_key = {key(j): j for j in after}
    removed = sorted(base_by_key[k] for k in base_by_key.keys() - after_by_key.keys())
    added = sorted(after_by_key[k] for k in after_by_key.keys() - base_by_key.keys())
    changed = sorted(k for k in base_by_key.keys() & after_by_key.keys()
                     if base_by_key[k] != after_by_key[k])
    print(f'removed: {len(removed)} jars, {sum(base[j] for j in removed):,} bytes')
    for jar in removed:
        print(f'  - {label(jar)} {base[jar]:,}')
    print(f'added: {len(added)} jars, {sum(after[j] for j in added):,} bytes')
    for jar in added:
        print(f'  + {label(jar)} {after[jar]:,}')
    print(f'version changed: {len(changed)} jars')
    for k in changed:
        old, new = base_by_key[k], after_by_key[k]
        print(f'  ~ {label(old)} {base[old]:,} -> {new[2]} {after[new]:,}')


if __name__ == '__main__':
    main()
