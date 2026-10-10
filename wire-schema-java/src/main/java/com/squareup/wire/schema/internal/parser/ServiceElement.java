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
package com.squareup.wire.schema.internal.parser;

import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.SchemaUtil;
import java.util.List;

public final class ServiceElement {
  private final Location location;

  public Location getLocation() {
    return location;
  }

  private final String name;

  public String getName() {
    return name;
  }

  private final String documentation;

  public String getDocumentation() {
    return documentation;
  }

  private final List<RpcElement> rpcs;

  public List<RpcElement> getRpcs() {
    return rpcs;
  }

  private final List<OptionElement> options;

  public List<OptionElement> getOptions() {
    return options;
  }

  public ServiceElement(Location location, String name, String documentation,
      List<RpcElement> rpcs, List<OptionElement> options) {
    this.location = location;
    this.name = name;
    this.documentation = documentation;
    this.rpcs = rpcs;
    this.options = options;
  }

  public String toSchema() {
    StringBuilder builder = new StringBuilder();
    SchemaUtil.appendDocumentation(builder, documentation);
    builder.append("service ").append(name).append(" {");
    if (!options.isEmpty()) {
      builder.append('\n');
      for (OptionElement option : options) {
        SchemaUtil.appendIndented(builder, option.toSchemaDeclaration());
      }
    }
    if (!rpcs.isEmpty()) {
      builder.append('\n');
      for (RpcElement rpc : rpcs) {
        SchemaUtil.appendIndented(builder, rpc.toSchema());
      }
    }
    builder.append("}\n");
    return builder.toString();
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof ServiceElement)) return false;
    ServiceElement that = (ServiceElement) other;
    return true
      && java.util.Objects.equals(this.location, that.location)
      && java.util.Objects.equals(this.name, that.name)
      && java.util.Objects.equals(this.documentation, that.documentation)
      && java.util.Objects.equals(this.rpcs, that.rpcs)
      && java.util.Objects.equals(this.options, that.options);
  }

  @Override public int hashCode() {
    int result = java.util.Objects.hashCode(location);
    result = 31 * result + java.util.Objects.hashCode(name);result = 31 * result + java.util.Objects.hashCode(documentation);result = 31 * result + java.util.Objects.hashCode(rpcs);result = 31 * result + java.util.Objects.hashCode(options);    return result;
  }

  @Override public String toString() {
    return "ServiceElement(location=" + location + ", name=" + name + ", documentation="
        + documentation + ", rpcs=" + rpcs + ", options=" + options + ")";
  }
}
