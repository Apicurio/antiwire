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

import com.squareup.wire.schema.internal.parser.RpcElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Rpc {
  final Location location;
  final String name;
  final String documentation;
  final String requestTypeElement;
  final String responseTypeElement;
  final boolean requestStreaming;
  final boolean responseStreaming;
  final Options options;

  // Null until this RPC is linked.
  private ProtoType requestType;

  // Null until this RPC is linked.
  private ProtoType responseType;

  Rpc(Location location, String name, String documentation, String requestTypeElement,
      String responseTypeElement, boolean requestStreaming, boolean responseStreaming,
      Options options) {
    this.location = location;
    this.name = name;
    this.documentation = documentation;
    this.requestTypeElement = requestTypeElement;
    this.responseTypeElement = responseTypeElement;
    this.requestStreaming = requestStreaming;
    this.responseStreaming = responseStreaming;
    this.options = options;
  }

  public Location location() {
    return location;
  }

  public String name() {
    return name;
  }

  public String documentation() {
    return documentation;
  }

  public String requestTypeElement() {
    return requestTypeElement;
  }

  public String responseTypeElement() {
    return responseTypeElement;
  }

  public boolean requestStreaming() {
    return requestStreaming;
  }

  public boolean responseStreaming() {
    return responseStreaming;
  }

  public Options options() {
    return options;
  }

  /** Null until this RPC is linked. */
  public ProtoType requestType() {
    return requestType;
  }

  /** Null until this RPC is linked. */
  public ProtoType responseType() {
    return responseType;
  }

  void link(Linker linker) {
    Linker scoped = linker.withContext(this);
    requestType = scoped.resolveMessageType(requestTypeElement);
    responseType = scoped.resolveMessageType(responseTypeElement);
  }

  void linkOptions(Linker linker, boolean validate) {
    Linker scoped = linker.withContext(this);
    options.link(scoped, location, validate);
  }

  void validate(Linker linker) {
    Linker scoped = linker.withContext(this);
    scoped.validateImportForType(location, requestType);
    scoped.validateImportForType(location, responseType);
  }

  Rpc retainAll(Schema schema, MarkSet markSet) {
    if (!markSet.contains(requestType) || !markSet.contains(responseType)) return null;
    Rpc result = new Rpc(location, name, documentation, requestTypeElement, responseTypeElement,
        requestStreaming, responseStreaming, options.retainAll(schema, markSet));
    result.requestType = requestType;
    result.responseType = responseType;
    return result;
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof Rpc)) return false;
    Rpc that = (Rpc) other;
    return location.equals(that.location)
        && name.equals(that.name)
        && documentation.equals(that.documentation)
        && requestTypeElement.equals(that.requestTypeElement)
        && responseTypeElement.equals(that.responseTypeElement)
        && requestStreaming == that.requestStreaming
        && responseStreaming == that.responseStreaming
        && Objects.equals(options, that.options);
  }

  @Override public int hashCode() {
    int result = location.hashCode();
    result = 31 * result + name.hashCode();
    result = 31 * result + documentation.hashCode();
    result = 31 * result + requestTypeElement.hashCode();
    result = 31 * result + responseTypeElement.hashCode();
    result = 31 * result + (requestStreaming ? 1 : 0);
    result = 31 * result + (responseStreaming ? 1 : 0);
    result = 31 * result + Objects.hashCode(options);
    return result;
  }

  @Override public String toString() {
    return "Rpc(location=" + location + ", name=" + name + ", documentation=" + documentation
        + ", requestTypeElement=" + requestTypeElement
        + ", responseTypeElement=" + responseTypeElement
        + ", requestStreaming=" + requestStreaming
        + ", responseStreaming=" + responseStreaming
        + ", options=" + options + ")";
  }

  public static List<Rpc> fromElements(List<RpcElement> elements) {
    List<Rpc> result = new ArrayList<>();
    for (RpcElement element : elements) {
      result.add(new Rpc(element.location, element.name, element.documentation,
          element.requestType, element.responseType, element.requestStreaming,
          element.responseStreaming, new Options(Options.METHOD_OPTIONS, element.options)));
    }
    return result;
  }

  public static List<RpcElement> toElements(List<Rpc> rpcs) {
    List<RpcElement> result = new ArrayList<>();
    for (Rpc rpc : rpcs) {
      result.add(new RpcElement(rpc.location, rpc.name, rpc.documentation,
          rpc.requestTypeElement, rpc.responseTypeElement, rpc.requestStreaming,
          rpc.responseStreaming, rpc.options.elements()));
    }
    return result;
  }
}
