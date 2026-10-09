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
package com.squareup.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the Java accessor names of the port to the real Wire 7.1.0 jars.
 *
 * <p>Kotlin exposes every property as {@code getX()} or {@code isX()}, and Java and Kotlin
 * consumers compiled against Wire call those names; a port that exposes {@code x()} instead
 * fails with {@code NoSuchMethodError} (maintainer decision 2026-10-09, TASK-33.1). The
 * resource {@code upstream-7.1.0-getters.tsv} is generated from the upstream jars by
 * {@code scripts/gen-upstream-getter-baseline.py}: an {@code EXPECTED} row must exist in the
 * port as a public method with identical parameter and return types, and every {@code GAP} row
 * carries the reason it is not ported. The test fails when a port member is renamed or removed,
 * and when the generated file no longer matches the number of rows it was built with.
 */
public class AccessorNameParityTest {
  private static final int EXPECTED_ROWS = 406;
  private static final int GAP_ROWS = 55;

  @Test
  public void everyUpstreamGetterExistsWithTheSameSignature() throws Exception {
    List<String> missing = new ArrayList<>();
    int expected = 0;
    int gaps = 0;
    for (String[] row : rows()) {
      if (row[0].equals("GAP")) {
        gaps++;
        assertTrue(row.length >= 6 && !row[5].isEmpty(), "gap without a reason: " + row[1] + "." + row[2]);
        // A gap is a known absence: when the port starts exposing the member, the row is stale.
        Class<?> gapType = tryLoad(row[1]);
        if (gapType != null && !row[5].startsWith("Kotlin type") && !row[5].startsWith("upstream returns Void")) {
          try {
            Class<?>[] gapParams = parameterTypes(row[3]);
            if (find(gapType, row[2], gapParams) != null && Modifier.isPublic(gapType.getModifiers())) {
              missing.add("stale GAP row, the port now has " + row[1] + "." + row[2]);
            }
          } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            // parameter type not loadable in the port: the member cannot exist with that signature
          }
        }
        continue;
      }
      expected++;
      Class<?> type;
      try {
        type = Class.forName(row[1], false, getClass().getClassLoader());
      } catch (ClassNotFoundException e) {
        missing.add(row[1] + " (class missing)");
        continue;
      }
      Class<?>[] params = parameterTypes(row[3]);
      Method m = find(type, row[2], params);
      if (m == null) {
        missing.add(row[1] + "." + row[2] + "(" + row[3] + ")");
      } else if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) {
        missing.add(row[1] + "." + row[2] + " is not a public instance method");
      } else if (!m.getReturnType().getName().equals(row[4])
          && !m.getReturnType().getCanonicalName().equals(row[4])) {
        missing.add(row[1] + "." + row[2] + " returns " + m.getReturnType().getName()
            + " but upstream returns " + row[4]);
      }
    }
    assertEquals(EXPECTED_ROWS, expected, "baseline row count changed; regenerate and review it");
    assertEquals(GAP_ROWS, gaps, "baseline gap count changed; regenerate and review it");
    assertTrue(missing.isEmpty(), missing.size() + " upstream getters missing or different: " + missing);
  }

  /** The original complaint: Kotlin callers use getName(), not name(). */
  @Test
  public void representativeAccessorsUseUpstreamNames() throws Exception {
    assertTrue(find(com.squareup.wire.schema.ProtoFile.class, "getTypes") != null);
    assertTrue(find(com.squareup.wire.schema.Field.class, "getName") != null);
    assertTrue(find(com.squareup.wire.schema.internal.parser.ProtoFileElement.class, "getPackageName") != null);
    assertTrue(find(com.squareup.wire.schema.Location.class, "getPath") != null);
    assertTrue(find(com.squareup.wire.schema.ProtoType.class, "isMap") != null);
    // Plain names the real 7.1.0 classes do not have must not survive next to the getters.
    // (ProtoFile.name() is a real upstream function, so it is deliberately not listed.)
    String[][] gone = {
        {"com.squareup.wire.schema.Field", "name"},
        {"com.squareup.wire.schema.Field", "tag"},
        {"com.squareup.wire.schema.ProtoFile", "types"},
        {"com.squareup.wire.schema.ProtoFile", "packageName"},
        {"com.squareup.wire.schema.internal.parser.ProtoFileElement", "packageName"},
        {"com.squareup.wire.schema.Location", "path"},
        {"com.squareup.wire.schema.Location", "base"},
    };
    for (String[] g : gone) {
      Class<?> c = Class.forName(g[0]);
      assertTrue(find(c, g[1]) == null && !hasField(c, g[1]),
          c.getSimpleName() + " still exposes the plain name " + g[1]);
    }
  }

  /**
   * The rule "no two names for one thing", applied to every pinned getter: for each EXPECTED row
   * getX()/isX(), the port must not also expose a public instance field or a no-argument public
   * method named x, unless the real upstream class itself has that plain member (a
   * {@code @JvmName} or {@code @JvmField} in the Kotlin source) or the exception is listed here.
   */
  @Test
  public void noPlainNameNextToAPinnedGetter() throws Exception {
    // Members that legitimately keep a plain name although upstream has getX():
    // - ProtoAdapter.type: the port's type differs from upstream's (Class instead of
    //   kotlin.reflect.KClass), so the getter is not pinned and the field stays;
    // - ImmutableList/MutableOnWriteList.size(): java.util.List requires size(), and upstream's
    //   Kotlin collections additionally expose getSize().
    java.util.Set<String> allowed = new java.util.HashSet<>(java.util.Arrays.asList(
        "com.squareup.wire.ProtoAdapter.type",
        "com.squareup.wire.internal.ImmutableList.size",
        "com.squareup.wire.internal.MutableOnWriteList.size",
        // MessageBinding.getMessageType() is a GAP row (upstream returns kotlin.reflect.KClass,
        // the port returns Class), so messageType() stays and is recorded here deliberately.
        "com.squareup.wire.internal.MessageBinding.messageType"));
    List<String> duplicates = new ArrayList<>();
    for (String[] row : rows()) {
      if (!row[0].equals("EXPECTED") || !row[3].isEmpty()) continue;
      String getter = row[2];
      String plain;
      if (getter.startsWith("get") && getter.length() > 3) {
        plain = Character.toLowerCase(getter.charAt(3)) + getter.substring(4);
      } else if (getter.startsWith("is") && getter.length() > 2) {
        plain = Character.toLowerCase(getter.charAt(2)) + getter.substring(3);
      } else {
        continue;
      }
      if (allowed.contains(row[1] + "." + plain)) continue;
      Class<?> type = tryLoad(row[1]);
      if (type == null) continue;
      boolean plainField = false;
      for (java.lang.reflect.Field f : type.getFields()) {
        if (f.getName().equals(plain) && !Modifier.isStatic(f.getModifiers())) plainField = true;
      }
      Method plainMethod = find(type, plain);
      boolean plainMethodPublic = plainMethod != null && Modifier.isPublic(plainMethod.getModifiers())
          && !Modifier.isStatic(plainMethod.getModifiers());
      if (plainField || plainMethodPublic) {
        duplicates.add(row[1] + " exposes " + plain + (plainField ? " (field)" : "()")
            + " next to " + getter + "()");
      }
    }
    assertTrue(duplicates.isEmpty(), duplicates.size() + " duplicated names: " + duplicates);
  }

  private static Class<?> tryLoad(String name) {
    try {
      return Class.forName(name, false, AccessorNameParityTest.class.getClassLoader());
    } catch (ClassNotFoundException | LinkageError e) {
      return null;
    }
  }

  private static boolean hasField(Class<?> c, String name) {
    for (java.lang.reflect.Field f : c.getFields()) if (f.getName().equals(name)) return true;
    return false;
  }

  private static Method find(Class<?> type, String name, Class<?>... params) {
    try {
      return type.getMethod(name, params);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private static Class<?>[] parameterTypes(String csv) throws ClassNotFoundException {
    if (csv.isEmpty()) return new Class<?>[0];
    String[] parts = csv.split(",");
    Class<?>[] out = new Class<?>[parts.length];
    for (int i = 0; i < parts.length; i++) out[i] = typeOf(parts[i]);
    return out;
  }

  private static Class<?> typeOf(String n) throws ClassNotFoundException {
    switch (n) {
      case "int": return int.class;
      case "long": return long.class;
      case "boolean": return boolean.class;
      case "byte": return byte.class;
      case "char": return char.class;
      case "short": return short.class;
      case "float": return float.class;
      case "double": return double.class;
      default:
        if (n.endsWith("[]")) {
          return java.lang.reflect.Array.newInstance(typeOf(n.substring(0, n.length() - 2)), 0).getClass();
        }
        return Class.forName(n);
    }
  }

  private List<String[]> rows() throws IOException {
    List<String[]> out = new ArrayList<>();
    try (InputStream in = getClass().getResourceAsStream("/upstream-7.1.0-getters.tsv")) {
      assertTrue(in != null, "baseline resource missing");
      BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      for (String line; (line = r.readLine()) != null; ) {
        if (line.startsWith("#") || line.isEmpty()) continue;
        out.add(line.split("\t", -1));
      }
    }
    return out;
  }
}
