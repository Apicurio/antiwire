/*
 * Copyright (C) 2015 Square, Inc.
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Creates a new schema that contains only the types selected by the pruning rules, including
 * their transitive dependencies.
 */
public final class Pruner {
  private final Schema schema;
  private final PruningRules pruningRules;

  private final MarkSet marks;

  /**
   * [types][ProtoType] and [members][ProtoMember] whose immediate dependencies have not yet
   * been visited.
   */
  private final ArrayDeque<Object> queue = new ArrayDeque<>();

  public Pruner(Schema schema, PruningRules pruningRules) {
    this.schema = schema;
    this.pruningRules = pruningRules;
    this.marks = new MarkSet(pruningRules);
  }

  public Schema prune() {
    markRoots();
    markReachable();

    List<ProtoFile> retained = retainImports(retainAll(schema, marks));

    return new Schema(retained);
  }

  private List<ProtoFile> retainAll(Schema schema, MarkSet marks) {
    List<ProtoFile> result = new ArrayList<>();
    for (ProtoFile protoFile : schema.protoFiles()) {
      result.add(protoFile.retainAll(schema, marks));
    }
    return result;
  }

  private List<ProtoFile> retainImports(List<ProtoFile> protoFiles) {
    Schema schema = new Schema(protoFiles);
    List<ProtoFile> result = new ArrayList<>();
    for (ProtoFile protoFile : protoFiles) {
      result.add(protoFile.retainImports(schema));
    }
    return result;
  }

  private void markRoots() {
    for (ProtoFile protoFile : schema.protoFiles()) {
      markRoots(protoFile);
    }
  }

  private void markRoots(ProtoFile protoFile) {
    for (Type type : protoFile.types()) {
      markRootsIncludingNested(type);
    }
    for (Service service : protoFile.services()) {
      markRoots(service.type());
    }
  }

  private void markRootsIncludingNested(Type type) {
    markRoots(type.type());

    for (Type nested : type.nestedTypes()) {
      markRootsIncludingNested(nested);
    }
  }

  private void markRoots(ProtoType protoType) {
    if (pruningRules.isRoot(protoType)) {
      marks.root(protoType);
      queue.add(protoType);
      return;
    }

    // The top-level type isn't a root, search for root members inside.
    for (Object reachable : reachableObjects(protoType)) {
      if (!(reachable instanceof ProtoMember)) continue;
      if (!isRetainedVersion((ProtoMember) reachable)) continue;
      if (pruningRules.isRoot((ProtoMember) reachable)) {
        marks.root((ProtoMember) reachable);
        marks.mark(((ProtoMember) reachable).type); // Consider this type as visited.
        queue.add(reachable);
      }
    }
  }

  /** Returns true if this member survives {@code since} and {@code until} pruning. */
  private boolean isRetainedVersion(ProtoMember protoMember) {
    String member = protoMember.member;
    Type type = schema.getType(protoMember.type);
    if (type instanceof MessageType) {
      MessageType messageType = (MessageType) type;
      Field field = messageType.field(member);
      if (field == null) field = messageType.extensionField(member);
      if (field != null) {
        return pruningRules.isFieldRetainedVersion(field.options());
      }
      return pruningRules.isFieldRetainedVersion(messageType.oneOf(member).options());
    }
    if (type instanceof EnumType) {
      EnumConstant enumConstant = ((EnumType) type).constant(member);
      return pruningRules.isEnumConstantRetainedVersion(enumConstant.options());
    }
    return true;
  }

  /**
   * Mark everything transitively reachable from the queue, adding to the queue whenever a
   * reachable object brings along more reachable objects.
   */
  private void markReachable() {
    while (true) {
      Object root = queue.poll();
      if (root == null) break;
      List<Object> reachableMembers = reachableObjects(root);

      for (Object reachable : reachableMembers) {
        if (reachable instanceof ProtoType) {
          if (root instanceof ProtoMember) {
            if (marks.mark((ProtoType) reachable, (ProtoMember) root)) {
              queue.add(reachable);
            }
          } else {
            if (marks.mark((ProtoType) reachable)) {
              queue.add(reachable);
            }
          }
        } else if (reachable instanceof ProtoMember) {
          if (isRetainedVersion((ProtoMember) reachable)
              && marks.mark((ProtoMember) reachable)) {
            queue.add(reachable);
          }
        } else if (reachable == null) {
          // Skip nulls.
          // TODO(jwilson): create a dedicated UNLINKED type as a placeholder.
        } else {
          throw new IllegalStateException("unexpected object: " + reachable);
        }
      }
    }
  }

  /**
   * Returns everything reachable from {@code root} when traversing the graph. The returned list
   * contains instances of type [ProtoMember] and [ProtoType].
   *
   * @param root either a [ProtoMember] or [ProtoType].
   */
  private List<Object> reachableObjects(Object root) {
    if (root instanceof ProtoMember) {
      return reachableFromMember((ProtoMember) root);
    }
    if (root instanceof ProtoType) {
      return reachableFromType((ProtoType) root);
    }
    throw new IllegalStateException("unexpected root: " + root);
  }

  private List<Object> reachableFromMember(ProtoMember root) {
    List<Object> result = new ArrayList<>();
    Options options;

    String member = root.member;
    Type type = schema.getType(root.type);
    Service service = schema.getService(root.type);

    if (type instanceof MessageType) {
      MessageType messageType = (MessageType) type;
      Field field = messageType.field(member);
      if (field == null) field = messageType.extensionField(member);
      if (field != null) {
        result.add(field.type());
        options = field.options();
      } else {
        OneOf oneOf = messageType.oneOf(member);
        if (oneOf == null) {
          throw new IllegalStateException("unexpected member: " + member);
        }
        options = oneOf.options();
      }
    } else if (type instanceof EnumType) {
      EnumConstant constant = ((EnumType) type).constant(member);
      if (constant == null) {
        throw new IllegalStateException("unexpected member: " + member);
      }
      options = constant.options();
    } else if (service != null) {
      Rpc rpc = service.rpc(member);
      if (rpc == null) {
        throw new IllegalStateException("unexpected rpc: " + member);
      }
      result.add(rpc.requestType());
      result.add(rpc.responseType());
      options = rpc.options();
    } else {
      throw new IllegalStateException("unexpected member: " + member);
    }

    addOptions(options.fields(pruningRules).values(), result);
    return result;
  }

  private List<Object> reachableFromType(ProtoType root) {
    List<Object> result = new ArrayList<>();

    if (root.isMap) {
      result.add(root.keyType);
      result.add(root.valueType);
      return result;
    }

    if (root.isScalar) {
      return result; // Skip scalar types.
    }

    Type type = schema.getType(root);
    Service service = schema.getService(root);
    Options fileOptions = schema.protoFile(root).options();
    Options options;

    if (type instanceof MessageType) {
      MessageType messageType = (MessageType) type;
      options = messageType.options();
      for (Field field : messageType.declaredFields()) {
        result.add(ProtoMember.get(root, field.name()));
      }
      for (Field field : messageType.extensionFields()) {
        result.add(ProtoMember.get(root, field.qualifiedName()));
      }
      for (OneOf oneOf : messageType.oneOfs()) {
        result.add(ProtoMember.get(root, oneOf.name()));
        for (Field field : oneOf.fields()) {
          result.add(ProtoMember.get(root, field.name()));
        }
      }
    } else if (type instanceof EnumType) {
      options = type.options();
      for (EnumConstant constant : ((EnumType) type).constants()) {
        result.add(ProtoMember.get(type.type(), constant.name()));
      }
    } else if (type instanceof EnclosingType) {
      options = type.options();
    } else if (service != null) {
      options = service.options();
      for (Rpc rpc : service.rpcs()) {
        result.add(ProtoMember.get(service.type(), rpc.name()));
      }
    } else {
      throw new IllegalStateException("unexpected type: " + root);
    }

    addOptions(options.fields(pruningRules).values(), result);
    addOptions(fileOptions.fields(pruningRules).values(), result);
    return result;
  }

  private void addOptions(Collection<ProtoMember> options, List<Object> result) {
    for (ProtoMember member : options) {
      // If it's an extension, don't consider the entire enclosing type to be reachable.
      if (!schema.isExtensionField(member)) {
        result.add(member.type);
      }
      result.add(member);
    }
  }
}
