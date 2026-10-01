# Upstream test adaptation ledger: wire-runtime-java

Per docs/translation-conventions.md section 6.2. One row per adapted case; the pinned clone
is the source of record (TASK-14 re-extracts for drift comparison).

| Original | Adapted | Case | Rules applied | Reviewer |
|---|---|---|---|---|
| wire-runtime/src/commonTest/kotlin/com/squareup/wire/ProtoWriterTest.kt:60 | wire-runtime-java/src/test/kotlin/com/squareup/wire/ProtoWriterTest.kt:60 | utf8 | R3: the `okio.utf8Size` String extension (okio 3 Kotlin) does not exist on the vendored Java okio, where the same computation is the static `okio.Utf8.size(String)`; import and call site rewritten accordingly. Every other line is verbatim upstream. | antiwire execution session, 2026-10-01 (PR #1 review pending) |
| wire-runtime/src/commonTest/kotlin/com/squareup/wire/ProtoWriterTest.kt:20 | wire-runtime-java/src/test/kotlin/com/squareup/wire/ProtoWriterTest.kt:20 | utf8 | Per-site decision (section 6.2): `kotlin.test.Test` is not present in the Maven-resolved JVM `org.jetbrains.kotlin:kotlin-test:2.1.21` artifact (assertions only, no annotations), so the test annotation maps to `org.junit.jupiter.api.Test`, already on the module test classpath; run semantics under the JUnit platform are identical. | antiwire execution session, 2026-10-01 (PR #1 review pending) |
