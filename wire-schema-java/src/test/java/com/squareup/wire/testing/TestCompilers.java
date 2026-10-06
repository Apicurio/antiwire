/*
 * Copyright (C) 2026 the antiwire authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.wire.testing;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Compilation helpers for tests that must compile generated sources against the port artifacts
 * with the JDK compiler at Java 11 source level (DEC-3). The compile classpath is derived from
 * the running test's classloader so it works under launchers that pass a manifest-jar
 * {@code java.class.path} (the property is the fallback), and Kotlin artifact jars are
 * stripped: test scope may carry kotlin-stdlib transitively (permitted for test tooling),
 * while the contracts under test, like the generated Empty mapping, require a compile with no
 * Kotlin at all.
 */
public final class TestCompilers {
  private TestCompilers() {
  }

  /**
   * Compiles {@code sourceFile} with {@code javac --release 11} against the test classpath of
   * {@code context} minus Kotlin jars, then loads {@code className} from the output directory
   * in a child classloader.
   *
   * <p>The child loader is deliberately left open: generated models load sibling classes (a
   * {@code Builder}, an adapter) lazily through it after this method returns, and closing the
   * loader would make those later loads fail. The loader's lifetime is the short-lived test
   * JVM, so the leak is bounded.
   */
  public static Class<?> compileRelease11(
      Path sourceFile, String className, Path classesDir, Class<?> context) throws Exception {
    ClassLoader loader = compileRelease11(new Path[] { sourceFile }, classesDir, context);
    return Class.forName(className, true, loader);
  }

  /**
   * The multi-source form of {@link #compileRelease11(Path, String, Path, Class)}, returning
   * the loader instead of one class so tests can load a whole generated tree from it.
   */
  public static ClassLoader compileRelease11(
      Path[] sourceFiles, Path classesDir, Class<?> context) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("tests must run on a JDK: no system java compiler");
    }
    String[] options = new String[] {
        "--release", "11",
        "-classpath", classpathWithoutKotlin(context),
        "-d", classesDir.toString(),
    };
    String[] sources = new String[sourceFiles.length];
    for (int i = 0; i < sourceFiles.length; i++) {
      sources[i] = sourceFiles[i].toString();
    }
    String[] args = new String[options.length + sources.length];
    System.arraycopy(options, 0, args, 0, options.length);
    System.arraycopy(sources, 0, args, options.length, sources.length);
    int exit = compiler.run(null, null, System.err, args);
    if (exit != 0) {
      throw new IllegalStateException(
          (sourceFiles.length == 1
              ? "generated source " + sourceFiles[0]
              : sourceFiles.length + " generated sources (first: "
                  + (sourceFiles.length == 0 ? "<none>" : sourceFiles[0]) + ")")
              + " did not compile under --release 11 (exit " + exit + ")");
    }
    return new URLClassLoader(
        new URL[] { classesDir.toUri().toURL() }, context.getClassLoader());
  }

  /** The test classpath of {@code context} with every Kotlin artifact jar removed. */
  public static String classpathWithoutKotlin(Class<?> context) {
    ClassLoader loader = context.getClassLoader();
    if (loader instanceof URLClassLoader) {
      StringBuilder classpath = new StringBuilder();
      boolean allFiles = true;
      for (URL url : ((URLClassLoader) loader).getURLs()) {
        if (!"file".equals(url.getProtocol())) {
          allFiles = false;
          break;
        }
        String entry = Path.of(java.net.URI.create(url.toString())).toString();
        if (isKotlinJar(entry)) continue;
        if (classpath.length() > 0) classpath.append(java.io.File.pathSeparatorChar);
        classpath.append(entry);
      }
      if (allFiles && classpath.length() > 0) return classpath.toString();
    }
    return withoutKotlinJars(System.getProperty("java.class.path"));
  }

  private static String withoutKotlinJars(String classpath) {
    if (classpath == null) return "";
    StringBuilder result = new StringBuilder();
    for (String entry : classpath.split(Pattern.quote(java.io.File.pathSeparator), -1)) {
      if (isKotlinJar(entry)) continue;
      if (result.length() > 0) result.append(java.io.File.pathSeparatorChar);
      result.append(entry);
    }
    return result.toString();
  }

  /**
   * True for Kotlin artifact jars (kotlin-stdlib, kotlin-reflect, and siblings): every Kotlin
   * artifact's file name starts with {@code kotlin-}. Narrower name parts are not matched, so
   * a non-Kotlin jar whose path merely contains the word kotlin is kept.
   */
  private static boolean isKotlinJar(String classpathEntry) {
    String name = classpathEntry;
    int lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf(java.io.File.separatorChar));
    if (lastSeparator != -1) name = name.substring(lastSeparator + 1);
    if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
    return name.startsWith("kotlin-") && name.endsWith(".jar");
  }
}
