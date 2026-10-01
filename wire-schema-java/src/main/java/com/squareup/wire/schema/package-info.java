/**
 * Pure-Java port of Wire's schema library (upstream: square/wire, tag 7.1.0): proto parser,
 * linker, and the Schema model. Classes in {@code internal.parser} stay public because Apicurio
 * Registry consumes them directly (Kotlin {@code internal} is public in JVM bytecode).
 */
package com.squareup.wire.schema;
