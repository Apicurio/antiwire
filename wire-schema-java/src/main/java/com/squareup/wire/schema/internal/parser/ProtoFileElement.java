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

import com.squareup.wire.Syntax;
import com.squareup.wire.schema.Location;
import java.util.List;

/** A single `.proto` file. */
public final class ProtoFileElement {
  public final Location location;
  public final String packageName;
  public final Syntax syntax;
  public final List<String> imports;
  public final List<String> publicImports;
  public final List<String> weakImports;
  public final List<TypeElement> types;
  public final List<ServiceElement> services;
  public final List<ExtendElement> extendDeclarations;
  public final List<OptionElement> options;

  public ProtoFileElement(Location location, String packageName, Syntax syntax,
      List<String> imports, List<String> publicImports, List<String> weakImports,
      List<TypeElement> types, List<ServiceElement> services,
      List<ExtendElement> extendDeclarations, List<OptionElement> options) {
    this.location = location;
    this.packageName = packageName;
    this.syntax = syntax;
    this.imports = imports;
    this.publicImports = publicImports;
    this.weakImports = weakImports;
    this.types = types;
    this.services = services;
    this.extendDeclarations = extendDeclarations;
    this.options = options;
  }

  public String toSchema() {
    StringBuilder builder = new StringBuilder();
    builder.append("// Proto schema formatted by Wire, do not edit.\n");
    builder.append("// Source: ").append(location.withPathOnly()).append('\n');

    if (syntax != null) {
      builder.append('\n');
      builder.append("syntax = \"").append(syntax).append("\";\n");
    }
    if (packageName != null) {
      builder.append('\n');
      builder.append("package ").append(packageName).append(";\n");
    }
    if (!imports.isEmpty() || !publicImports.isEmpty() || !weakImports.isEmpty()) {
      builder.append('\n');
      for (String file : imports) {
        builder.append("import \"").append(file).append("\";\n");
      }
      for (String file : publicImports) {
        builder.append("import public \"").append(file).append("\";\n");
      }
      for (String file : weakImports) {
        builder.append("import weak \"").append(file).append("\";\n");
      }
    }
    if (!options.isEmpty()) {
      builder.append('\n');
      for (OptionElement option : options) {
        builder.append(option.toSchemaDeclaration());
      }
    }
    if (!types.isEmpty()) {
      for (TypeElement typeElement : types) {
        builder.append('\n');
        builder.append(typeElement.toSchema());
      }
    }
    if (!extendDeclarations.isEmpty()) {
      for (ExtendElement extendDeclaration : extendDeclarations) {
        builder.append('\n');
        builder.append(extendDeclaration.toSchema());
      }
    }
    if (!services.isEmpty()) {
      for (ServiceElement service : services) {
        builder.append('\n');
        builder.append(service.toSchema());
      }
    }
    return builder.toString();
  }

  /** Returns an empty proto file to serve as a null object when a file cannot be found. */
  public static ProtoFileElement empty(String path) {
    return new ProtoFileElement(Location.get(path), null, null, java.util.Collections.emptyList(),
        java.util.Collections.emptyList(), java.util.Collections.emptyList(),
        java.util.Collections.emptyList(), java.util.Collections.emptyList(),
        java.util.Collections.emptyList(), java.util.Collections.emptyList());
  }
}
