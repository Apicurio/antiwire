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

public final class RpcElement {
  public final Location location;
  public final String name;
  public final String documentation;
  public final String requestType;
  public final String responseType;
  public final boolean requestStreaming;
  public final boolean responseStreaming;
  public final List<OptionElement> options;

  public RpcElement(Location location, String name, String documentation, String requestType,
      String responseType, boolean requestStreaming, boolean responseStreaming,
      List<OptionElement> options) {
    this.location = location;
    this.name = name;
    this.documentation = documentation;
    this.requestType = requestType;
    this.responseType = responseType;
    this.requestStreaming = requestStreaming;
    this.responseStreaming = responseStreaming;
    this.options = options;
  }

  public String toSchema() {
    StringBuilder builder = new StringBuilder();
    SchemaUtil.appendDocumentation(builder, documentation);
    builder.append("rpc ").append(name).append(" (");

    if (requestStreaming) {
      builder.append("stream ");
    }
    builder.append(requestType).append(") returns (");

    if (responseStreaming) {
      builder.append("stream ");
    }
    builder.append(responseType).append(')');

    if (!options.isEmpty()) {
      builder.append(" {\n");
      for (OptionElement option : options) {
        SchemaUtil.appendIndented(builder, option.toSchemaDeclaration());
      }
      builder.append('}');
    }

    builder.append(";\n");
    return builder.toString();
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof RpcElement)) return false;
    RpcElement that = (RpcElement) other;
    return true
      && java.util.Objects.equals(this.location, that.location)
      && java.util.Objects.equals(this.name, that.name)
      && java.util.Objects.equals(this.documentation, that.documentation)
      && java.util.Objects.equals(this.requestType, that.requestType)
      && java.util.Objects.equals(this.responseType, that.responseType)
      && this.requestStreaming == that.requestStreaming
      && this.responseStreaming == that.responseStreaming
      && java.util.Objects.equals(this.options, that.options);
  }

  @Override public int hashCode() {
    int result = java.util.Objects.hashCode(location);
    result = 31 * result + java.util.Objects.hashCode(name);result = 31 * result + java.util.Objects.hashCode(documentation);result = 31 * result + java.util.Objects.hashCode(requestType);result = 31 * result + java.util.Objects.hashCode(responseType);result = 31 * result + (requestStreaming ? 1 : 0);result = 31 * result + (responseStreaming ? 1 : 0);result = 31 * result + java.util.Objects.hashCode(options);    return result;
  }
}
