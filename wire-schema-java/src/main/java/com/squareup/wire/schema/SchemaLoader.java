/*
 * Copyright (C) 2018 Square, Inc.
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

import com.squareup.wire.schema.internal.CommonSchemaLoader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import okio.FileSystem;

/**
 * Load proto files and their transitive dependencies and parse them. Keep track of which files
 * were loaded from where so that we can use that information later when deciding what to
 * generate.
 */
public final class SchemaLoader implements Loader, ProfileLoader, AutoCloseable {
  private final CommonSchemaLoader delegate;

  public SchemaLoader(java.nio.file.FileSystem fileSystem) {
    this(FileSystem.asOkioFileSystem(fileSystem));
  }

  public SchemaLoader(FileSystem fileSystem) {
    this.delegate = new CommonSchemaLoader(fileSystem);
  }

  private SchemaLoader(CommonSchemaLoader enclosing, ErrorCollector errors) {
    this.delegate = new CommonSchemaLoader(enclosing, errors);
  }

  @Override public Loader withErrors(ErrorCollector errors) {
    return new SchemaLoader(delegate, errors);
  }

  /** Strict by default. Note that golang cannot build protos with package cycles. */
  public boolean permitPackageCycles() {
    return delegate.permitPackageCycles();
  }

  public void setPermitPackageCycles(boolean permitPackageCycles) {
    delegate.setPermitPackageCycles(permitPackageCycles);
  }

  /**
   * All qualified named Protobuf types in {@code opaqueTypes} will be evaluated as being of type
   * {@code bytes}. On code generation, the fields of such types will be using the platform
   * equivalent of {@code bytes}, like {@link okio.ByteString} for the JVM. Note that scalar types
   * cannot be opaqued.
   */
  public List<ProtoType> opaqueTypes() {
    return delegate.opaqueTypes();
  }

  public void setOpaqueTypes(List<ProtoType> opaqueTypes) {
    delegate.setOpaqueTypes(opaqueTypes);
  }

  /**
   * If true, the schema loader will load the whole graph, including files and types not used by
   * anything in the source path.
   */
  public boolean loadExhaustively() {
    return delegate.loadExhaustively();
  }

  public void setLoadExhaustively(boolean loadExhaustively) {
    delegate.setLoadExhaustively(loadExhaustively);
  }

  /** Subset of the schema that was loaded from the source path. */
  public List<ProtoFile> sourcePathFiles() {
    return delegate.sourcePathFiles();
  }

  /** Initialize the source path and proto path from which files are loaded. */
  public void initRoots(List<Location> sourcePath, List<Location> protoPath) throws IOException {
    delegate.initRoots(sourcePath, protoPath);
  }

  public void initRoots(List<Location> sourcePath) throws IOException {
    delegate.initRoots(sourcePath, Collections.emptyList());
  }

  @Override public Profile loadProfile(String name, Schema schema) throws IOException {
    return delegate.loadProfile(name, schema);
  }

  @Override public ProtoFile load(String path) {
    return delegate.load(path);
  }

  public Schema loadSchema() throws IOException {
    return delegate.loadSchema();
  }

  @Override public void close() throws IOException {
    delegate.close();
  }
}
