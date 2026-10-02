/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.squareup.wire.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * This is Maven's {@code ComparableVersionTest} ported to our {@link SemVer} class and with
 * several tests omitted or commented out because of different behavior.
 *
 * <p>https://github.com/apache/maven/blob/master/maven-artifact/src/test/java/org/apache/maven/artifact/versioning/ComparableVersionTest.java
 */
public class MavenVersionsTest {
  /** Commented out tests are excluded because they disagree with the semver.org spec! */
  @Test public void testVersionComparing() {
    checkVersionsOrder("1", "2");
    checkVersionsOrder("1.5", "2");
    checkVersionsOrder("1", "2.5");
    checkVersionsOrder("1.0", "1.1");
    checkVersionsOrder("1.1", "1.2");
    checkVersionsOrder("1.0.0", "1.1");
    checkVersionsOrder("1.0.1", "1.1");
    checkVersionsOrder("1.1", "1.2.0");

    checkVersionsOrder("1.0-alpha-1", "1.0");
    checkVersionsOrder("1.0-alpha-1", "1.0-alpha-2");
    checkVersionsOrder("1.0-alpha-1", "1.0-beta-1");

    checkVersionsOrder("1.0-beta-1", "1.0-SNAPSHOT");
    checkVersionsOrder("1.0-SNAPSHOT", "1.0");
    // checkVersionsOrder("1.0-alpha-1-SNAPSHOT", "1.0-alpha-1");

    // checkVersionsOrder("1.0", "1.0-1");
    checkVersionsOrder("1.0-1", "1.0-2");
    // checkVersionsOrder("1.0.0", "1.0-1");

    checkVersionsOrder("2.0-1", "2.0.1");
    checkVersionsOrder("2.0.1-klm", "2.0.1-lmn");
    // checkVersionsOrder("2.0.1", "2.0.1-xyz");

    // checkVersionsOrder("2.0.1", "2.0.1-123");
    // checkVersionsOrder("2.0.1-xyz", "2.0.1-123");
  }

  /** Wire's version maintains a total ordering but the ordering is different from Maven's! */
  @Test public void testMng5568() {
    String a = "6.1.0";
    String b = "6.1.0rc3";
    String c = "6.1H.5-beta";
    checkVersionsOrder(a, b);
    checkVersionsOrder(b, c);
    checkVersionsOrder(a, c);
  }

  /** Wire's version maintains a total ordering but the ordering is different from Maven's! */
  @Test public void testMng6572() {
    String a = "20190126.230843";
    String b = "1234567890.12345";
    String c = "123456789012345.1H.5-beta";
    String d = "12345678901234567890.1H.5-beta";
    checkVersionsOrder(a, b);
    checkVersionsOrder(b, c);
    checkVersionsOrder(a, c);
    checkVersionsOrder(c, d);
    checkVersionsOrder(b, d);
    checkVersionsOrder(a, d);
  }

  @Test public void testVersionEqualWithLeadingZeroes() {
    assertEquals(0, new SemVer("0000000000000000001").compareTo(new SemVer("1")));
  }

  @Test public void testVersionZeroEqualWithLeadingZeroes() {
    assertEquals(0, new SemVer("0000000000000000000").compareTo(new SemVer("0")));
  }

  private static void checkVersionsOrder(String aString, String bString) {
    SemVer a = new SemVer(aString.toLowerCase(Locale.ROOT));
    SemVer b = new SemVer(bString.toLowerCase(Locale.ROOT));
    assertEquals(0, a.compareTo(a), a + " == " + a);
    assertEquals(0, b.compareTo(b), b + " == " + b);
    assertTrue(a.compareTo(b) < 0, a + " > " + b);
    assertTrue(b.compareTo(a) > 0, b + " < " + a);
  }
}
