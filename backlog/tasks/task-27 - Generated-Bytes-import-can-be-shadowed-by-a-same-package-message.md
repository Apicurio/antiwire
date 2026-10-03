---
id: TASK-27
title: Generated `import com.squareup.wire.Bytes` can be shadowed by a same-package message named Bytes
status: To Do
assignee: []
created_date: '2026-10-03 00:00'
labels:
  - codegen
  - api-surface
milestone: m-8
dependencies: []
priority: low
type: bug
ordinal: 27000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Found by the high-effort code review of the no-okio phase 2 diff (2026-10-03). The port's
generator emits `import com.squareup.wire.Bytes;` in every file that declares bytes-typed
fields. JLS 6.4.1 makes a single-type-import shadow same-package top-level types declared in
other compilation units, so a proto that declares `message Bytes` (or an enum) in a package
whose other messages also use bytes fields generates code that cannot compile: in the
sibling files every bare `Bytes` binds to the runtime type instead of the message (wrong
field/adapter types), and in the message's own file the import collides with its own
declaration. Upstream shares this hazard class for every name it imports (`okio.ByteString`,
`java.time.Duration`, `kotlin.Unit`: a same-package `message Duration` breaks upstream output
the same way), so this is inherited emission-model behavior, not a phase-2 regression in
kind; phase 2 only makes it likelier because `Bytes` is a plausible message name.

Why it is not fixed in phase 2: the clean mechanism is per-file always-qualified emission
(`alwaysQualify`), which exists in the palantir javapoet fork but not in Square JavaPoet
1.13.0 (last Square release), and the fork ships Java 17 bytecode that DEC-3 forbids. A
post-emission textual rewrite cannot distinguish runtime `Bytes` tokens from message `Bytes`
tokens. Candidate directions when this is picked up: qualify per-file at the CodeBlock level
with a generator-owned mechanism (emit fully-qualified runtime references only in colliding
packages, using the schema's declared type names to detect collisions), or revisit the
javapoet pin if a Java-11-compatible fork with `alwaysQualify` appears. The hazard is
documented in docs/api-surface.md (phase 2, known limitation) and docs/compatibility-matrix.md
section E.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Generated output for a schema declaring `message Bytes` next to bytes-field users compiles against the port runtime, or the limitation is re-documented with the chosen rejection rationale
- [ ] #2 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
