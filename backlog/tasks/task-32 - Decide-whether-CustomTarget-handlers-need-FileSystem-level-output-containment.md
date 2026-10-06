---
id: TASK-32
title: Decide whether CustomTarget handlers need FileSystem-level output containment
status: To Do
assignee: []
created_date: '2026-10-06 19:46'
labels:
  - adversarial-audit
  - codegen
  - hardening
milestone: m-12
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Residual from the TASK-16.2.1 /simplify altitude review (2026-10-06): the containment guard is applied at each port-internal emitting handler (JavaSchemaHandler, ProtoTarget), but a reflective CustomTarget handler runs arbitrary user code against the unconstrained per-target FileSystem and is not covered. The deeper fix is wrapping the FileSystem handed to each handler (in WireRun or SchemaHandler.Context) so writes outside the output directory are refused. It diverges structurally from upstream for an extension point that already runs arbitrary user code, so the current site-by-site hardening is a defensible parity-weighted boundary; this task records that decision explicitly or implements the wrapper.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Decision recorded: either WireRun/Context constrains the per-target FileSystem to the output directory for CustomTarget handlers, or the site-by-site hardening is documented as the accepted boundary with the reason.
- [ ] #2 If implemented, a test shows a CustomTarget handler writing outside the output directory is refused and ordinary relative writes still work; the divergence from upstream is recorded in docs/compatibility-matrix.md.
- [ ] #3 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
