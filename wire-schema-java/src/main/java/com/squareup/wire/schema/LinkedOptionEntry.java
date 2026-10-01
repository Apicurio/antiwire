/*
 * Copyright (C) 2023 Square, Inc.
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
package com.squareup.wire.schema;

import com.squareup.wire.schema.internal.parser.OptionElement;
import java.util.Objects;

final class LinkedOptionEntry {
  final OptionElement optionElement;
  final ProtoMember protoMember;
  final Object value;

  LinkedOptionEntry(OptionElement optionElement, ProtoMember protoMember, Object value) {
    this.optionElement = optionElement;
    this.protoMember = protoMember;
    this.value = value;
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof LinkedOptionEntry)) return false;
    LinkedOptionEntry that = (LinkedOptionEntry) other;
    return optionElement.equals(that.optionElement)
        && protoMember.equals(that.protoMember)
        && Objects.equals(value, that.value);
  }

  @Override public int hashCode() {
    int result = optionElement.hashCode();
    result = 31 * result + protoMember.hashCode();
    result = 31 * result + Objects.hashCode(value);
    return result;
  }

  @Override public String toString() {
    return "LinkedOptionEntry(optionElement=" + optionElement + ", protoMember=" + protoMember
        + ", value=" + value + ")";
  }
}
