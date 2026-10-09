/*
 * Copyright (C) 2016 Square, Inc.
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
package com.squareup.wire.schema.internal;

import com.squareup.wire.schema.Location;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A single {@code .wire} file. This file is structured similarly to a {@code .proto} file, but
 * with different elements.
 *
 * <p>File Structure
 *
 * <p>A project may have 0 or more {@code .wire} files. These files should be in the same
 * directory as the {@code .proto} files so they may be automatically discovered by Wire.
 *
 * <p>Each file starts with a syntax declaration. The syntax must be "wire2". This is followed by
 * an optional package declaration, which should match to the package declarations of the
 * {@code .proto} files in the directory.
 *
 * <p>Profiles may import any number of proto files. Note that it is an error to import
 * {@code .wire} files. These imports are used to resolve types specified later in the file.
 *
 * <p>Profiles may specify any number of type configurations. These specify a fully qualified
 * type, its target Java type, and an adapter to do the encoding and decoding.
 *
 * <pre>{@code
 * syntax = "wire2";
 * package squareup.dinosaurs;
 *
 * import "squareup/geology/period.proto";
 *
 * // Roar!
 * type squareup.dinosaurs.Dinosaur {
 * target com.squareup.dino.Dinosaur using com.squareup.dino.Dinosaurs#DINO_ADAPTER;
 * }
 * }</pre>
 */
public final class ProfileFileElement {
  private final Location location;

  public Location getLocation() {
    return location;
  }

  private final String packageName;

  public String getPackageName() {
    return packageName;
  }

  private final List<String> imports;

  public List<String> getImports() {
    return imports;
  }

  private final List<TypeConfigElement> typeConfigs;

  public List<TypeConfigElement> getTypeConfigs() {
    return typeConfigs;
  }

  public ProfileFileElement(Location location, String packageName, List<String> imports,
      List<TypeConfigElement> typeConfigs) {
    this.location = location;
    this.packageName = packageName;
    this.imports = imports;
    this.typeConfigs = typeConfigs;
  }

  public ProfileFileElement(Location location) {
    this(location, null, Collections.emptyList(), Collections.emptyList());
  }

  public ProfileFileElement(Location location, String packageName) {
    this(location, packageName, Collections.emptyList(), Collections.emptyList());
  }

  public ProfileFileElement(Location location, String packageName, List<String> imports) {
    this(location, packageName, imports, Collections.emptyList());
  }

  public String toSchema() {
    StringBuilder builder = new StringBuilder();
    builder.append("// ").append(location).append('\n');
    builder.append("syntax = \"wire2\";\n");
    if (packageName != null) {
      builder.append("package ").append(packageName).append(";\n");
    }
    if (!imports.isEmpty()) {
      builder.append('\n');
      for (String file : imports) {
        builder.append("import \"").append(file).append("\";\n");
      }
    }
    if (!typeConfigs.isEmpty()) {
      builder.append('\n');
      for (TypeConfigElement typeConfigElement : typeConfigs) {
        builder.append(typeConfigElement.toSchema());
      }
    }
    return builder.toString();
  }

  @Override public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof ProfileFileElement)) return false;
    ProfileFileElement that = (ProfileFileElement) other;
    return Objects.equals(location, that.location)
        && Objects.equals(packageName, that.packageName)
        && Objects.equals(imports, that.imports)
        && Objects.equals(typeConfigs, that.typeConfigs);
  }

  @Override public int hashCode() {
    int result = Objects.hashCode(location);
    result = 31 * result + Objects.hashCode(packageName);
    result = 31 * result + Objects.hashCode(imports);
    result = 31 * result + Objects.hashCode(typeConfigs);
    return result;
  }

  @Override public String toString() {
    return "ProfileFileElement(location=" + location + ", packageName=" + packageName
        + ", imports=" + imports + ", typeConfigs=" + typeConfigs + ")";
  }
}
