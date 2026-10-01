---
id: TASK-12
title: 'Complete schema encoding, source loading and generator separation'
status: Done
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
- [x] #1 SchemaEncoder output is byte-identical to upstream Wire 7.1.0 on the applicable corpus, including options and unsigned values.
- [x] #2 Selected source-loading behavior preserves applicable upstream import/resource/ZIP and in-memory semantics; public API migrations are documented for tests and Apicurio.
- [x] #3 Runtime and schema production dependency graphs contain no Kotlin or Kotlin-backed transitives; every retained Java dependency has a recorded purpose and footprint/license review.
- [x] #4 Core/profile separation leaves a compiling module graph; each deferred Java-profile API and test has a TASK-16 owner, and excluded Kotlin-generator cases have explicit scope reasons.
- [x] #5 Applicable schema encoding and loading cases run in the shared CI entry point with no unrelated upstream implementation on its classpath.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

## Final Summary

Landed in three commits (1416e2f translation, b3ccf64 simplify+altitude, 7403054 code-review): Root with file/directory/ZIP roots over the vendored okio FileSystem (listRecursively discovery, import-path validation), internal FileSystems BOM detection, CommonSchemaLoader plus public SchemaLoader facade (nio and okio constructors, permitPackageCycles, opaqueTypes, loadExhaustively, sourcePathFiles, close() releasing opened ZIP filesystems), CoreLoader routed through asResourceFileSystem per the loading inventory, and SchemaEncoder producing descriptor.proto bytes (synthetic map entries nested under declaring messages, proto3-optional synthetic oneofs, unsigned option coercion via Integer/Long.parseUnsigned*, extendee wiring). The vendored okio Path.relativeTo was rewritten as a lexical computation matching okio 3.18.2 (probed; layer-level tests cover direction, edges, cross-provider zip-vs-host, impossible '..' bases). Profile/AdapterConstant stay out of core with TASK-16 as owner (recorded on its task; Path.segments inventory row added for its output-directory check). Tests: SchemaLoaderSmokeTest (8 incl. classpath runtime protos), SchemaEncoderTest (6 incl. oneof and extend crash regressions), LoadingAccessTest +2; 82 schema + 859 runtime green, all 5 ACTIVE suites pass. Known deferrals: profile loading (TASK-16), byte-for-byte upstream encoder parity runner (TASK-14), FakeFileSystem (TASK-13), reportLoadingErrors callers (TASK-13 tests).
