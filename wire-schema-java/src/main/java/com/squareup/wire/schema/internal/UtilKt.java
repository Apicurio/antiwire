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

package com.squareup.wire.schema.internal;

import com.squareup.wire.schema.ProtoType;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.internal.parser.OptionElement;
import java.util.List;
import java.util.Set;

/**
 * Java name of upstream's {@code Util.kt} file facade (TASK-33.2); delegates to
 * {@link SchemaUtil}. {@code withUnixSlashes(okio.Path)} is excluded by DEC-14.
 */
public final class UtilKt {
  public static final int MIN_TAG_VALUE = SchemaUtil.MIN_TAG_VALUE;
  public static final int MAX_TAG_VALUE = SchemaUtil.MAX_TAG_VALUE;

  private UtilKt() {
  }

  public static void appendDocumentation(StringBuilder builder, String documentation) {
    SchemaUtil.appendDocumentation(builder, documentation);
  }

  public static void appendOptions(StringBuilder builder, List<OptionElement> options) {
    SchemaUtil.appendOptions(builder, options);
  }

  public static void appendIndented(StringBuilder builder, String value) {
    SchemaUtil.appendIndented(builder, value);
  }

  public static boolean isValidTag(int value) {
    return SchemaUtil.isValidTag(value);
  }

  public static Schema withStubs(Schema schema, Set<ProtoType> typesToStub) {
    return SchemaUtil.withStubs(schema, typesToStub);
  }
}
