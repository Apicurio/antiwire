/*
 * Copyright (C) 2019 Square, Inc.
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
package com.squareup.wire.internal;

import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.Syntax;
import com.squareup.wire.WireField;

/** Reads, writes, and describes a tag within a message (upstream commonMain). */
public abstract class FieldOrOneOfBinding<M, B> {
  public abstract int getTag();

  public abstract WireField.Label getLabel();

  public abstract boolean getRedacted();

  public abstract boolean isMap();

  public abstract boolean isMessage();

  /**
   * The name of the field in generated code. If the declared name is a keyword like {@code fun},
   * this will be a transformed name like {@code fun_}.
   */
  public abstract String getName();

  /** The name of the field as declared in the proto file. */
  public abstract String getDeclaredName();

  /**
   * The JSON name as determined at code-generation time. This is usually camelCase even if the
   * field is declared in snake_case.
   */
  public abstract String getWireFieldJsonName();

  public abstract ProtoAdapter<?> getKeyAdapter();

  public abstract ProtoAdapter<?> getSingleAdapter();

  /** If true, Wire will always write identity values. */
  public abstract boolean getWriteIdentityValues();

  private volatile ProtoAdapter<Object> adapterMemoized;

  /**
   * The full adapter for this binding: map entries when this is a map, the label-decorated
   * single adapter otherwise. Delegate adapters are created lazily; otherwise we could stack
   * overflow.
   */
  @SuppressWarnings("unchecked")
  public ProtoAdapter<Object> getAdapter() {
    ProtoAdapter<Object> result = adapterMemoized;
    if (result == null) {
      synchronized (this) {
        result = adapterMemoized;
        if (result == null) {
          if (isMap()) {
            result = (ProtoAdapter<Object>) (ProtoAdapter<?>) ProtoAdapter.newMapAdapter(
                (ProtoAdapter<Object>) (ProtoAdapter<?>) getKeyAdapter(),
                (ProtoAdapter<Object>) (ProtoAdapter<?>) getSingleAdapter());
          } else {
            result = (ProtoAdapter<Object>) (ProtoAdapter<?>) getSingleAdapter().withLabel(getLabel());
          }
          adapterMemoized = result;
        }
      }
    }
    return result;
  }

  /** Accept a single value, independent of whether this value is single or repeated. */
  public abstract void value(B builder, Object value);

  /** Assign a single value for required/optional fields, or a list for repeated/packed. */
  public abstract void set(B builder, Object value);

  public abstract Object get(M message);

  public abstract Object getFromBuilder(B builder);

  public final boolean omitFromJson(Syntax syntax, Object value) {
    if (value == null) return true;
    return omitIdentity(syntax) && value.equals(getAdapter().getIdentity());
  }

  private boolean omitIdentity(Syntax syntax) {
    if (getWriteIdentityValues()) return false;
    if (getLabel() == WireField.Label.OMIT_IDENTITY) return true;
    if (getLabel().isRepeated() && syntax == Syntax.PROTO_3) return true;
    if (isMap() && syntax == Syntax.PROTO_3) return true;
    return false;
  }
}
