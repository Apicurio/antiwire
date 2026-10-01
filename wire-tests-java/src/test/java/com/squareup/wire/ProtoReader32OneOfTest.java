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

import static org.junit.Assert.assertEquals;

import com.squareup.wire.protos.oneof.OneOfMessage;
import java.io.IOException;
import okio.ByteString;
import org.junit.Test;

/**
 * Upstream jvm-kotlin-proto-reader-32's OneOf merge semantics through the reader32, ported to
 * the flat Java-generated OneOfMessage fixture (the Kotlin boxed Inner fixture is a Kotlin
 * generator product, excluded per DEC-6; the merge semantics under test are the reader's and
 * are identical across boxed and flat forms). Mechanical adaptations ledgered in
 * UPSTREAM-TEST-ADAPTATIONS.md.
 */
public class ProtoReader32OneOfTest {
  private static OneOfMessage concat(OneOfMessage a, OneOfMessage b) throws IOException {
    byte[] both = new byte[a.encode().length + b.encode().length];
    System.arraycopy(a.encode(), 0, both, 0, a.encode().length);
    System.arraycopy(b.encode(), 0, both, a.encode().length, b.encode().length);
    return OneOfMessage.ADAPTER.decode(both);
  }

  @Test public void repeatedOccurrencesOfTheSameOneOfFieldMerge() throws IOException {
    OneOfMessage a = new OneOfMessage.Builder().foo(5).build();
    OneOfMessage b = new OneOfMessage.Builder().foo(6).build();
    // int32 scalars merge: the later value wins for the same field.
    OneOfMessage decoded = concat(a, b);
    assertEquals(6L, (long) decoded.foo);
  }

  @Test public void lastOneOfFieldWins() throws IOException {
    OneOfMessage a = new OneOfMessage.Builder().foo(5).build();
    OneOfMessage b = new OneOfMessage.Builder().bar("six").build();
    OneOfMessage decoded = concat(a, b);
    // Different oneof arms do not merge; the last arm set wins.
    assertEquals("six", decoded.bar);
    assertEquals(null, decoded.foo);
  }

  @Test public void lastOneOfScalarFieldWins() throws IOException {
    OneOfMessage a = new OneOfMessage.Builder().foo(5).build();
    OneOfMessage b = new OneOfMessage.Builder().baz("nine").build();
    OneOfMessage decoded = concat(a, b);
    assertEquals("nine", decoded.baz);
  }
}
