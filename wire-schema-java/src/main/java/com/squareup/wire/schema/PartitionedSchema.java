/*
 * Copyright (C) 2020 Square, Inc.
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

import com.squareup.wire.schema.internal.SchemaUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Partitions a schema by module, mirroring upstream's internal PartitionedSchema.kt. */
final class PartitionedSchema {
  /** Module name to partition info. The iteration order of this map is the generation order. */
  final Map<String, Partition> partitions;
  final List<String> warnings;
  final List<String> errors;

  PartitionedSchema(Map<String, Partition> partitions, List<String> warnings,
      List<String> errors) {
    this.partitions = partitions;
    this.warnings = warnings;
    this.errors = errors;
  }

  static final class Partition {
    final Schema schema;
    /** The types that this partition will generate. */
    final Set<ProtoType> types;
    /** These are the types depended upon by {@link #types} associated with their module name. */
    final Map<ProtoType, String> transitiveUpstreamTypes;

    Partition(Schema schema, Set<ProtoType> types,
        Map<ProtoType, String> transitiveUpstreamTypes) {
      this.schema = schema;
      this.types = types;
      this.transitiveUpstreamTypes = transitiveUpstreamTypes;
    }

    Partition(Schema schema) {
      this(schema, schema.types(), Collections.emptyMap());
    }
  }

  static Map<String, Integer> computeDepths(Map<String, WireRun.Module> modules) {
    Map<String, Integer> memo = new LinkedHashMap<>();
    for (String name : modules.keySet()) {
      dfs(name, modules, memo);
    }
    return memo;
  }

  private static int dfs(String name, Map<String, WireRun.Module> modules,
      Map<String, Integer> memo) {
    Integer cached = memo.get(name);
    if (cached != null) return cached;
    int depth = 1;
    for (String dependency : modules.get(name).dependencies()) {
      depth = Math.max(depth, 1 + dfs(dependency, modules, memo));
    }
    memo.put(name, depth);
    return depth;
  }

  static PartitionedSchema partition(Schema schema, Map<String, WireRun.Module> modules) {
    DirectedAcyclicGraph<String> moduleGraph = new DirectedAcyclicGraph<>(
        modules.keySet(), moduleName -> modules.get(moduleName).dependencies());

    List<String> errors = new ArrayList<>();
    Map<String, Partition> partitions = new LinkedHashMap<>();
    List<String> topoGraph = moduleGraph.topologicalOrder();
    Map<String, Integer> depths = computeDepths(modules);
    List<String> ordered = new ArrayList<>(topoGraph);
    ordered.sort((a, b) -> Integer.compare(depths.get(a), depths.get(b)));

    for (String moduleName : ordered) {
      WireRun.Module module = modules.get(moduleName);

      Map<ProtoType, String> upstreamTypes = new LinkedHashMap<>();
      Map<ProtoType, Set<String>> duplicateTypes = new LinkedHashMap<>();
      for (String dependencyName : moduleGraph.transitiveNodes(moduleName)) {
        for (ProtoType type : partitions.get(dependencyName).types) {
          String replaced = upstreamTypes.put(type, dependencyName);
          if (replaced != null) {
            Set<String> sourceModules = duplicateTypes.get(type);
            if (sourceModules == null) {
              sourceModules = new LinkedHashSet<>();
              sourceModules.add(replaced);
              duplicateTypes.put(type, sourceModules);
            }
            sourceModules.add(dependencyName);
          }
        }
      }
      for (Map.Entry<ProtoType, Set<String>> entry : duplicateTypes.entrySet()) {
        ProtoType duplicate = entry.getKey();
        errors.add(
            moduleName + " sees " + duplicate + " in " + String.join(", ", entry.getValue())
                + ".\n"
                + "  In order to avoid confusion and incompatibility, either make one of these "
                + "modules\n"
                + "  depend on the other or move this type up into a common dependency.");
      }

      // Replace types which have already been generated with stub types that have no external
      // references. This ensures our types can still link. More critically, it ensures that
      // transitive types which were pruned upstream will only be generated in this module if they
      // are reachable from this module's types.
      Schema stubbedSchema = SchemaUtil.withStubs(schema, upstreamTypes.keySet());

      Schema prunedSchema = module.pruningRules() != null
          ? stubbedSchema.prune(module.pruningRules())
          : stubbedSchema;

      Set<ProtoType> ownedTypes = new LinkedHashSet<>();
      for (ProtoFile protoFile : prunedSchema.protoFiles()) {
        for (Type type : protoFile.typesAndNestedTypes()) {
          if (!upstreamTypes.containsKey(type.type())) ownedTypes.add(type.type());
        }
        for (Service service : protoFile.services()) {
          if (!upstreamTypes.containsKey(service.type())) ownedTypes.add(service.type());
        }
      }

      partitions.put(moduleName, new Partition(prunedSchema, ownedTypes, upstreamTypes));
    }

    List<String> warnings = new ArrayList<>();
    for (Set<String> subgraph : moduleGraph.disjointGraphs()) {
      List<String> subgraphList = new ArrayList<>(subgraph);
      for (int index = 0; index < subgraphList.size(); index++) {
        String currentName = subgraphList.get(index);
        for (int other = index + 1; other < subgraphList.size(); other++) {
          String otherName = subgraphList.get(other);
          Set<ProtoType> currentTypes = partitions.get(currentName).types;
          Set<ProtoType> otherTypes = partitions.get(otherName).types;
          Set<ProtoType> duplicates = new LinkedHashSet<>(currentTypes);
          duplicates.retainAll(otherTypes);
          if (!duplicates.isEmpty()) {
            WireRun.Module currentModule = modules.get(currentName);
            WireRun.Module otherModule = modules.get(otherName);
            for (ProtoType duplicate : duplicates) {
              String duplicateName = duplicate.toString();
              Set<String> currentModuleRoots =
                  currentModule.pruningRules() != null
                      ? currentModule.pruningRules().roots()
                      : Collections.emptySet();
              Set<String> otherModuleRoots =
                  otherModule.pruningRules() != null
                      ? otherModule.pruningRules().roots()
                      : Collections.emptySet();
              if (!currentModuleRoots.contains(duplicateName)
                  || !otherModuleRoots.contains(duplicateName)) {
                warnings.add(
                    duplicate + " is generated twice in peer modules " + currentName + " and "
                        + otherName + ".\n"
                        + "  Consider moving this type into a common dependency of both "
                        + "modules.\n"
                        + "  To suppress this warning, explicitly add the type to the roots of "
                        + "both modules.");
              }
            }
          }
        }
      }
    }

    return new PartitionedSchema(partitions, warnings, errors);
  }
}
