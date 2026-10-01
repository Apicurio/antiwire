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

/** Annotates generated {@link Message} fields with metadata for serialization. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WireField {
  /** The tag number used to store the field's value. */
  int tag();

  /**
   * Reference to the static field that holds a {@link ProtoAdapter} that can encode and decode
   * this field's keys. This only applies to maps. See {@link #adapter()} for the value format.
   */
  String keyAdapter() default "";

  /**
   * Reference to the static field that holds a {@link ProtoAdapter} that can encode and decode
   * this field's values. The reference is a string like
   * {@code com.squareup.wire.protos.person.Person#ADAPTER} and contains a fully-qualified class
   * name followed by a hash symbol and a field name.
   */
  String adapter();

  /**
   * The field's protocol buffer label, one of {@link Label#OPTIONAL}, {@link Label#REQUIRED},
   * {@link Label#REPEATED}, {@link Label#PACKED}, or {@link Label#OMIT_IDENTITY}. Defaults to
   * {@link Label#OPTIONAL}.
   */
  Label label() default Label.OPTIONAL;

  /** Redacted fields are omitted from toString() to protect sensitive data. */
  boolean redacted() default false;

  /**
   * Name of this field as declared in the proto schema; non-empty only when the declared name
   * differs from the generated one, for instance a proto field named {@code final} generated in
   * Java is renamed to {@code final_}.
   */
  String declaredName() default "";

  /**
   * Name representing this field as it should be used in JSON; non-empty only when the json name
   * differs from the name as declared in the proto schema.
   */
  String jsonName() default "";

  /** Name of the oneof this field belongs to; non-empty only for oneof members. */
  String oneofName() default "";

  /**
   * The order that this field was declared in the proto schema, or {@code -1} when the order
   * does not matter for JSON serialization.
   */
  int schemaIndex() default -1;

  /** A protocol buffer label. */
  enum Label {
    REQUIRED,
    OPTIONAL,
    REPEATED,
    ONE_OF,

    /** Implies {@link #REPEATED}. */
    PACKED,

    /**
     * Special label to define proto3 fields which should not be emitted if their value equals
     * their type's respective identity value, e.g. an {@code int32} field with value {@code 0}.
     */
    OMIT_IDENTITY;

    public boolean isRepeated() {
      return this == REPEATED || this == PACKED;
    }

    public boolean isPacked() {
      return this == PACKED;
    }

    public boolean isOneOf() {
      return this == ONE_OF;
    }
  }
}
