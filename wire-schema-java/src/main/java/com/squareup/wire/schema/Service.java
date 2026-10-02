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

import com.squareup.wire.schema.internal.parser.ServiceElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Service {
  final ProtoType type;
  final Location location;
  final String documentation;
  final String name;
  final List<Rpc> rpcs;
  final Options options;

  Service(ProtoType type, Location location, String documentation, String name, List<Rpc> rpcs,
      Options options) {
    this.type = type;
    this.location = location;
    this.documentation = documentation;
    this.name = name;
    this.rpcs = rpcs;
    this.options = options;
  }

  /** Returns a copy of this service with the given property values, mirroring the Kotlin
   * data-class {@code copy}. */
  public Service copy(ProtoType type, Location location, String documentation, String name,
      List<Rpc> rpcs, Options options) {
    return new Service(type, location, documentation, name, rpcs, options);
  }

  public ProtoType type() {
    return type;
  }

  public Location location() {
    return location;
  }

  public String documentation() {
    return documentation;
  }

  public String name() {
    return name;
  }

  public List<Rpc> rpcs() {
    return rpcs;
  }

  public Options options() {
    return options;
  }

  /** Returns the RPC named {@code name}, or null if this service has no such method. */
  public Rpc rpc(String name) {
    for (Rpc rpc : rpcs) {
      if (rpc.name().equals(name)) return rpc;
    }
    return null;
  }

  void link(Linker linker) {
    Linker scoped = linker.withContext(this);
    for (Rpc rpc : rpcs) {
      rpc.link(scoped);
    }
  }

  void linkOptions(Linker linker, boolean validate) {
    Linker scoped = linker.withContext(this);
    for (Rpc rpc : rpcs) {
      rpc.linkOptions(scoped, validate);
    }
    options.link(scoped, location, validate);
  }

  void validate(Linker linker) {
    Linker scoped = linker.withContext(this);
    validateRpcUniqueness(scoped, rpcs);
    for (Rpc rpc : rpcs) {
      rpc.validate(scoped);
    }
  }

  private void validateRpcUniqueness(Linker linker, List<Rpc> rpcs) {
    Map<String, List<Rpc>> nameToRpc = new LinkedHashMap<>();
    for (Rpc rpc : rpcs) {
      List<Rpc> list = nameToRpc.get(rpc.name());
      if (list == null) {
        list = new ArrayList<>();
        nameToRpc.put(rpc.name(), list);
      }
      list.add(rpc);
    }
    for (Map.Entry<String, List<Rpc>> entry : nameToRpc.entrySet()) {
      if (entry.getValue().size() > 1) {
        StringBuilder error = new StringBuilder();
        error.append("mutable rpcs share name ").append(entry.getKey()).append(":");
        List<Rpc> conflicting = entry.getValue();
        for (int i = 0; i < conflicting.size(); i++) {
          error.append("\n  ").append(i + 1).append(". ").append(conflicting.get(i).name())
              .append(" (").append(conflicting.get(i).location()).append(")");
        }
        linker.errors.add(error.toString());
      }
    }
  }

  Service retainAll(Schema schema, MarkSet markSet) {
    // If this service is not retained, prune it.
    if (!markSet.contains(type)) {
      return null;
    }

    List<Rpc> retainedRpcs = new ArrayList<>();
    for (Rpc rpc : rpcs) {
      Rpc retainedRpc = rpc.retainAll(schema, markSet);
      if (retainedRpc != null && markSet.contains(ProtoMember.get(type, rpc.name()))) {
        retainedRpcs.add(retainedRpc);
      }
    }

    return new Service(type, location, documentation, name, retainedRpcs,
        options.retainAll(schema, markSet));
  }

  static Service fromElement(ProtoType protoType, ServiceElement element) {
    List<Rpc> rpcs = Rpc.fromElements(element.rpcs);
    Options options = new Options(Options.SERVICE_OPTIONS, element.options);

    return new Service(protoType, element.location, element.documentation, element.name, rpcs,
        options);
  }

  static List<Service> fromElements(String packageName, List<ServiceElement> elements) {
    List<Service> result = new ArrayList<>();
    for (ServiceElement service : elements) {
      ProtoType protoType = ProtoType.get(packageName, service.name);
      result.add(fromElement(protoType, service));
    }
    return result;
  }

  static List<ServiceElement> toElements(List<Service> services) {
    List<ServiceElement> result = new ArrayList<>();
    for (Service service : services) {
      result.add(new ServiceElement(service.location, service.name, service.documentation,
          Rpc.toElements(service.rpcs), service.options.elements()));
    }
    return result;
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof Service)) return false;
    Service that = (Service) other;
    return type.equals(that.type)
        && location.equals(that.location)
        && documentation.equals(that.documentation)
        && name.equals(that.name)
        && rpcs.equals(that.rpcs)
        && Objects.equals(options, that.options);
  }

  @Override public int hashCode() {
    int result = type.hashCode();
    result = 31 * result + location.hashCode();
    result = 31 * result + documentation.hashCode();
    result = 31 * result + name.hashCode();
    result = 31 * result + rpcs.hashCode();
    result = 31 * result + Objects.hashCode(options);
    return result;
  }

  @Override public String toString() {
    return "Service(type=" + type + ", location=" + location + ", documentation=" + documentation
        + ", name=" + name + ", rpcs=" + rpcs + ", options=" + options + ")";
  }
}
