/*
 * Copyright (C) 2026 Square, Inc.
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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * TASK-15 port of upstream wire-protoc-compatibility-tests LargeFieldNumberInteropTest.kt
 * (pinned tag 7.1.0).
 *
 * <p>Both cases are disabled: the LargeFieldMessage fixtures live in the Kotlin proto package
 * com.squareup.wire.proto2.kotlin.largefield (java_package
 * com.squareup.wire.proto2.kotlin.largefield), which scripts/generate-protoc-compat-fixtures.sh
 * deliberately excludes (Kotlin-model fixture packages are DEC-6 exclusions), so neither the wire
 * model nor the protoc reference model exists in this module. The upstream bodies are preserved as
 * comments; porting these two cases requires extending the pinned fixture generation, which is a
 * maintainer decision recorded in the TASK-15 report.
 */
public class LargeFieldNumberInteropTest {

  @Test
  @Disabled("DEC-6: LargeFieldMessage fixtures are in the Kotlin proto package "
      + "com.squareup.wire.proto2.kotlin.largefield, excluded by the oracle module's pinned "
      + "fixture generation; no Java-model equivalent was generated.")
  public void fieldNumberBelowBoundary() {
    /*
     * val wireMessage = LargeFieldMessageK(
     *   below_boundary = "works",
     * )
     *
     * val protocMessage = LargeFieldMessageP.newBuilder()
     *   .setBelowBoundary("works")
     *   .build()
     *
     * val wireDecodedFromProtoc = LargeFieldMessageK.ADAPTER.decode(protocMessage.toByteArray())
     * assertThat(wireDecodedFromProtoc).isEqualTo(wireMessage)
     *
     * val protocDecodedFromWire = LargeFieldMessageP.parseFrom(wireMessage.encode())
     * assertThat(protocDecodedFromWire).isEqualTo(protocMessage)
     */
  }

  @Test
  @Disabled("DEC-6: LargeFieldMessage fixtures are in the Kotlin proto package "
      + "com.squareup.wire.proto2.kotlin.largefield, excluded by the oracle module's pinned "
      + "fixture generation; no Java-model equivalent was generated.")
  public void fieldNumberAtAndAboveBoundary() {
    /*
     * val wireMessage = LargeFieldMessageK(
     *   at_boundary = "fixed",
     *   max_field = "max",
     * )
     *
     * val protocMessage = LargeFieldMessageP.newBuilder()
     *   .setAtBoundary("fixed")
     *   .setMaxField("max")
     *   .build()
     *
     * val wireDecodedFromProtoc = LargeFieldMessageK.ADAPTER.decode(protocMessage.toByteArray())
     * assertThat(wireDecodedFromProtoc).isEqualTo(wireMessage)
     *
     * val protocDecodedFromWire = LargeFieldMessageP.parseFrom(wireMessage.encode())
     * assertThat(protocDecodedFromWire).isEqualTo(protocMessage)
     */
  }
}
