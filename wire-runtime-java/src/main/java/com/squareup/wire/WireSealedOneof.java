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
 * Annotates a sealed class generated for a oneof with its position in the enclosing message's
 * primary constructor. This is used by {@code KotlinConstructorBuilder} to reconstruct the
 * message when no explicit {@code Builder} class is present.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WireSealedOneof {
  /**
   * The order this oneof was declared in the proto schema, counting all constructor parameters
   * (regular fields and sealed oneofs), used to reconstruct the constructor call order.
   */
  int schemaIndex();
}
