---
id: TASK-3
title: Write Kotlin-to-Java translation conventions doc
status: Done
assignee: []
created_date: '2026-09-29 09:22'
updated_date: '2026-10-01 06:42'
labels: []
milestone: m-0
dependencies: []
priority: high
type: docs
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The parity contract for all translation work. Semantics that upstream tests assert must survive translation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Documents exception parity: kotlin require -> IllegalArgumentException, checkNotNull -> IllegalStateException (not NPE)
- [ ] #2 Documents equals/hashCode/toString parity requirements (data-class and List equality)
- [ ] #3 Documents Java 11 constraints: no sealed, records, or var in main sources
- [ ] #4 Requires preservation of Google (ProtoReader family, MathMethods) and JetBrains (ArrayList files) Apache-2.0 headers
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-01 06:42
---
Done 2026-10-01. docs/translation-conventions.md; the review round caught a factual error in the Java 11 constraints list (var/Optional.isEmpty/String.isBlank are available at 11) and it was fixed; the banned list is now records(16)/text blocks(15)/switch expressions(14)/sealed(17). Gates: /simplify and code-review ran this session.
---
<!-- COMMENTS:END -->
