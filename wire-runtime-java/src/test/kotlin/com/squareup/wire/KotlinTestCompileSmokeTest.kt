package com.squareup.wire

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Shell smoke test (TASK-2): proves that Kotlin test compilation is wired and executed
 * (DEC-4 allows Kotlin for test tooling only; production scope stays pure Java and is
 * enforced by maven-enforcer). Asserts nothing about Wire behavior.
 */
class KotlinTestCompileSmokeTest {
    @Test
    fun kotlinTestSourcesCompileAndRun() {
        assertTrue(true, "Kotlin test compilation path executes")
    }
}
