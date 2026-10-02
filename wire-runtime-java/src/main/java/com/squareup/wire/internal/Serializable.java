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
package com.squareup.wire.internal;

/**
 * Marker for types the upstream Gradle plugin serializes between configuration and execution.
 *
 * <p>Upstream declares this name as a multiplatform {@code expect} that aliases to
 * {@link java.io.Serializable} on the JVM (wire-runtime jvmMain {@code -Platform.kt}); the Gradle
 * plugin that motivates it is DEC-6 non-ported functionality, so the port keeps the source-level
 * name as a marker instead of binding schema types to Java serialization. Public because
 * implementors such as {@code com.squareup.wire.schema.Target} live outside this package.
 */
public interface Serializable {
}
