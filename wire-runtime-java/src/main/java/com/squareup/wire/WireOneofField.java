/*
 * Copyright (C) 2013 Square, Inc.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates generated data classes inside a sealed-class oneof with metadata for serialization
 * and deserialization. Each variant data class carries its own tag, adapter, and name metadata,
 * discovered at runtime via reflection on the sealed class's nested classes.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WireOneofField {
  /** The tag number used to store the field's value. */
  int tag();

  /**
   * Reference to the static field holding a {@link ProtoAdapter} that can encode and decode this
   * field's values, like {@code com.squareup.wire.protos.person.Person#ADAPTER}.
   */
  String adapter();

  /**
   * Name of this field as declared in the proto schema; non-empty only when it differs from the
   * generated one.
   */
  String declaredName() default "";

  /** Redacted fields are omitted from toString() to protect sensitive data. */
  boolean redacted() default false;

  /** Name representing this field as it should be used in JSON, when it differs. */
  String jsonName() default "";
}
