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
    for (ProtoFile protoFile : schema.getProtoFiles()) {
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
    for (ProtoFile protoFile : schema.getProtoFiles()) {
      markRoots(protoFile);
    }
  }

  private void markRoots(ProtoFile protoFile) {
    for (Type type : protoFile.getTypes()) {
      markRootsIncludingNested(type);
    }
    for (Service service : protoFile.getServices()) {
      markRoots(service.type());
    }
  }

  private void markRootsIncludingNested(Type type) {
    markRoots(type.getType());

    for (Type nested : type.getNestedTypes()) {
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
        marks.mark(((ProtoMember) reachable).getType()); // Consider this type as visited.
        queue.add(reachable);
      }
    }
  }

  /** Returns true if this member survives {@code since} and {@code until} pruning. */
  private boolean isRetainedVersion(ProtoMember protoMember) {
    String member = protoMember.getMember();
    Type type = schema.getType(protoMember.getType());
    if (type instanceof MessageType) {
      MessageType messageType = (MessageType) type;
      Field field = messageType.field(member);
      if (field == null) field = messageType.extensionField(member);
      if (field != null) {
        return pruningRules.isFieldRetainedVersion(field.getOptions());
      }
      return pruningRules.isFieldRetainedVersion(messageType.oneOf(member).getOptions());
    }
    if (type instanceof EnumType) {
      EnumConstant enumConstant = ((EnumType) type).constant(member);
      return pruningRules.isEnumConstantRetainedVersion(enumConstant.getOptions());
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

    String member = root.getMember();
    Type type = schema.getType(root.getType());
    Service service = schema.getService(root.getType());

    if (type instanceof MessageType) {
      MessageType messageType = (MessageType) type;
      Field field = messageType.field(member);
      if (field == null) field = messageType.extensionField(member);
      if (field != null) {
        result.add(field.getType());
        options = field.getOptions();
      } else {
        OneOf oneOf = messageType.oneOf(member);
        if (oneOf == null) {
          throw new IllegalStateException("unexpected member: " + member);
        }
        options = oneOf.getOptions();
      }
    } else if (type instanceof EnumType) {
      EnumConstant constant = ((EnumType) type).constant(member);
      if (constant == null) {
        throw new IllegalStateException("unexpected member: " + member);
      }
      options = constant.getOptions();
    } else if (service != null) {
      Rpc rpc = service.rpc(member);
      if (rpc == null) {
        throw new IllegalStateException("unexpected rpc: " + member);
      }
      result.add(rpc.getRequestType());
      result.add(rpc.getResponseType());
      options = rpc.getOptions();
    } else {
      throw new IllegalStateException("unexpected member: " + member);
    }

    addOptions(options.fields(pruningRules).values(), result);
    return result;
  }

  private List<Object> reachableFromType(ProtoType root) {
    List<Object> result = new ArrayList<>();

    if (root.isMap()) {
      result.add(root.getKeyType());
      result.add(root.getValueType());
      return result;
    }

    if (root.isScalar()) {
      return result; // Skip scalar types.
    }

    Type type = schema.getType(root);
    Service service = schema.getService(root);
    Options fileOptions = schema.protoFile(root).getOptions();
    Options options;

    if (type instanceof MessageType) {
      MessageType messageType = (MessageType) type;
      options = messageType.getOptions();
      for (Field field : messageType.getDeclaredFields()) {
        result.add(ProtoMember.get(root, field.getName()));
      }
      for (Field field : messageType.getExtensionFields()) {
        result.add(ProtoMember.get(root, field.getQualifiedName()));
      }
      for (OneOf oneOf : messageType.getOneOfs()) {
        result.add(ProtoMember.get(root, oneOf.getName()));
        for (Field field : oneOf.getFields()) {
          result.add(ProtoMember.get(root, field.getName()));
        }
      }
    } else if (type instanceof EnumType) {
      options = type.getOptions();
      for (EnumConstant constant : ((EnumType) type).getConstants()) {
        result.add(ProtoMember.get(type.getType(), constant.getName()));
      }
    } else if (type instanceof EnclosingType) {
      options = type.getOptions();
    } else if (service != null) {
      options = service.options();
      for (Rpc rpc : service.rpcs()) {
        result.add(ProtoMember.get(service.type(), rpc.getName()));
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
        result.add(member.getType());
      }
      result.add(member);
    }
  }
}
