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
package com.squareup.wire.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.wire.SchemaBuilder;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * TASK-16 adoption of upstream ManifestPartitionTest:
 * {@code wire-schema/src/jvmTest/kotlin/com/squareup/wire/schema/ManifestPartitionTest.kt} at
 * square/wire tag 7.1.0. Case names, protos, module graphs, and expected warnings are verbatim;
 * assertk maps onto JUnit 5 and upstream's {@code schema.partition(modules)} extension becomes a
 * direct call to the port's package-private {@link PartitionedSchema#partition} (same package).
 */
public class ManifestPartitionTest {
  @Test public void upstreamPruneIsNotGeneratedDownstream() {
    Schema schema = new SchemaBuilder()
        .add("example.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message A {\n"
            + "  optional B b = 1;\n"
            + "}\n"
            + "message B {\n"
            + "  optional C c = 1;\n"
            + "}\n"
            + "message C {\n"
            + "}\n")
        .build();

    Map<String, WireRun.Module> modules = new LinkedHashMap<>();
    modules.put("common", new WireRun.Module(
        Collections.emptySet(),
        new PruningRules.Builder()
            .addRoot("B")
            .prune("C")
            .build()));
    modules.put("feature", new WireRun.Module(
        Collections.singleton("common"),
        new PruningRules.Builder()
            .addRoot("A")
            .build()));

    PartitionedSchema partitionedSchema = PartitionedSchema.partition(schema, modules);

    PartitionedSchema.Partition commonPartition = partitionedSchema.partitions.get("common");
    // B has no field of type C because of its inclusion in common's prune list.
    assertNull(getMessage(commonPartition.schema, "B").field("c"));
    // C is not generated in common because of its inclusion in the prune list.
    assertFalse(commonPartition.types.contains(ProtoType.get("C")));

    // C is not in feature because its only dependant is B which is from common.
    PartitionedSchema.Partition featurePartition = partitionedSchema.partitions.get("feature");
    assertFalse(featurePartition.types.contains(ProtoType.get("C")));
  }

  @Test public void upstreamPruneIsNotPrunedDownstream() {
    Schema schema = new SchemaBuilder()
        .add("example.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message A {\n"
            + "  optional B b = 1;\n"
            + "  optional C c = 2;\n"
            + "}\n"
            + "message B {\n"
            + "  optional C c = 1;\n"
            + "}\n"
            + "message C {\n"
            + "}\n")
        .build();

    Map<String, WireRun.Module> modules = new LinkedHashMap<>();
    modules.put("common", new WireRun.Module(
        Collections.emptySet(),
        new PruningRules.Builder()
            .addRoot("B")
            .prune("C")
            .build()));
    modules.put("feature", new WireRun.Module(
        Collections.singleton("common"),
        new PruningRules.Builder()
            .addRoot("A")
            .build()));

    PartitionedSchema partitionedSchema = PartitionedSchema.partition(schema, modules);

    PartitionedSchema.Partition commonPartition = partitionedSchema.partitions.get("common");
    // B has no field of type C because of its inclusion in common's prune list.
    assertNull(getMessage(commonPartition.schema, "B").field("c"));
    // C is not generated in common because of its inclusion in the prune list.
    assertFalse(commonPartition.types.contains(ProtoType.get("C")));

    PartitionedSchema.Partition featurePartition = partitionedSchema.partitions.get("feature");
    // A has a field of type C because common's prunes do not apply to feature.
    assertNotNull(getMessage(featurePartition.schema, "A").field("c"));
    // C is generated in feature because common's prunes do not apply and A depends on it.
    assertTrue(featurePartition.types.contains(ProtoType.get("C")));
  }

  @Test public void duplicatedTypesReportedOnce() {
    Schema schema = new SchemaBuilder()
        .add("example.proto", ""
            + "syntax = \"proto2\";\n"
            + "\n"
            + "message A {\n"
            + "  optional B b = 1;\n"
            + "  optional C c = 2;\n"
            + "}\n"
            + "message B {\n"
            + "  optional C c = 1;\n"
            + "}\n"
            + "message C {\n"
            + "}\n")
        .build();

    Map<String, WireRun.Module> modules = new LinkedHashMap<>();
    modules.put("common", new WireRun.Module(
        Collections.emptySet(),
        new PruningRules.Builder()
            .addRoot("B")
            .prune("C")
            .build()));
    modules.put("feature1", new WireRun.Module(
        Collections.singleton("common"),
        new PruningRules.Builder()
            .addRoot("A")
            .build()));
    modules.put("feature2", new WireRun.Module(
        Collections.singleton("common"),
        new PruningRules.Builder()
            .addRoot("A")
            .build()));

    PartitionedSchema partitionedSchema = PartitionedSchema.partition(schema, modules);

    assertEquals(Arrays.asList(""
        + "C is generated twice in peer modules feature1 and feature2.\n"
        + "  Consider moving this type into a common dependency of both modules.\n"
        + "  To suppress this warning, explicitly add the type to the roots of both modules."),
        partitionedSchema.warnings);
  }

  private static MessageType getMessage(Schema schema, String name) {
    Type type = schema.getType(name);
    if (!(type instanceof MessageType)) {
      throw new AssertionError("No type '" + name + "' in schema");
    }
    return (MessageType) type;
  }
}
