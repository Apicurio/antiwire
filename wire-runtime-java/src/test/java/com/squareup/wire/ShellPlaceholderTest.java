package com.squareup.wire;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Shell smoke test (TASK-2): proves the module builds and surefire executes, and that the
 * module's own main output is on the test classpath. Asserts nothing about Wire behavior.
 */
final class ShellPlaceholderTest {
  @Test
  void moduleShellMainOutputIsOnTheTestClasspath() throws Exception {
    Class<?> shellPackage = Class.forName("com.squareup.wire.package-info");
    assertNotNull(shellPackage.getProtectionDomain().getCodeSource());
  }
}
