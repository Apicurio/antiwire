---
id: TASK-2
title: Set up Maven multi-module build skeleton and CI
status: Done
assignee: []
created_date: '2026-09-29 09:22'
updated_date: '2026-10-01 06:49'
labels: []
milestone: m-0
dependencies: []
priority: high
type: chore
ordinal: 2000
---

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Maven multi-module repo builds empty wire-runtime-java and wire-schema-java modules with --release 11
- [ ] #2 CI runs build and tests on JDK 17
- [ ] #3 No Kotlin plugin or kotlin-stdlib in compile scope of main artifacts
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-01 06:42
---
Done 2026-10-01. Evidence: mvn verify exits 0 on JDK 17; CI on Temurin 17 with concurrency and paths-ignore added from review; zero-dependency rule now mechanically enforced by maven-enforcer bannedDependencies in the parent. Gates: /simplify and code-review ran this session on the M0 diff.
---

created: 2026-10-01 06:49
---
Correction and verification 2026-10-01: the first enforcer declaration sat in pluginManagement and never executed (caught by review); it now runs from the parent build/plugins with searchTransitive=false, direct *:* ban, includes io.apicurio:* and junit:junit. Negative test proven: adding guava compile-scope to wire-schema-java fails the build with 'com.google.guava:guava <--- banned via the exclude/include list'; clean build passes.
---
<!-- COMMENTS:END -->
