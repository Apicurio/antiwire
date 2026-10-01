---
id: TASK-12
title: 'Complete schema encoding, source loading and generator separation'
status: In Progress
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-09-30 01:18'
labels: []
milestone: m-8
dependencies:
  - TASK-11
documentation:
  - docs/decisions.md
priority: high
ordinal: 12000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Translate SchemaEncoder and complete the source-loading implementation selected by the M0 spike, including filesystem, in-memory, classpath-resource and ZIP inputs. Do not impose java.nio or relocated Okio before that decision. Remove unnecessary Guava coupling where JDK collections preserve semantics; pure-Java dependencies are permitted if justified. Move Java-specific Profile/AdapterConstant support to the optional generator module shell from TASK-2 with explicit APIs and test ownership. KotlinPoet-backed generator functionality is excluded from production scope, not relocated as a hidden Kotlin dependency. TASK-16 completes and verifies Java-generator profile behavior.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 SchemaEncoder output is byte-identical to upstream Wire 7.1.0 on the applicable corpus, including options and unsigned values.
- [ ] #2 Selected source-loading behavior preserves applicable upstream import/resource/ZIP and in-memory semantics; public API migrations are documented for tests and Apicurio.
- [ ] #3 Runtime and schema production dependency graphs contain no Kotlin or Kotlin-backed transitives; every retained Java dependency has a recorded purpose and footprint/license review.
- [ ] #4 Core/profile separation leaves a compiling module graph; each deferred Java-profile API and test has a TASK-16 owner, and excluded Kotlin-generator cases have explicit scope reasons.
- [ ] #5 Applicable schema encoding and loading cases run in the shared CI entry point with no unrelated upstream implementation on its classpath.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
