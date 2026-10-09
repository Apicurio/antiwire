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

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CustomTarget extends Target {
  private final List<String> includes;
  private final List<String> excludes;
  private final boolean exclusive;
  private final String outDirectory;
  private final Map<String, String> options;
  private final SchemaHandler.Factory schemaHandlerFactory;

  public CustomTarget(String outDirectory, SchemaHandler.Factory schemaHandlerFactory) {
    this(
        Collections.singletonList("*"),
        Collections.emptyList(),
        true /* exclusive */,
        outDirectory,
        Collections.emptyMap(),
        schemaHandlerFactory);
  }

  public CustomTarget(
      List<String> includes,
      List<String> excludes,
      boolean exclusive,
      String outDirectory,
      Map<String, String> options,
      SchemaHandler.Factory schemaHandlerFactory) {
    this.includes = includes;
    this.excludes = excludes;
    this.exclusive = exclusive;
    this.outDirectory = outDirectory;
    this.options = options;
    this.schemaHandlerFactory = schemaHandlerFactory;
  }

  @Override public List<String> getIncludes() {
    return includes;
  }

  @Override public List<String> getExcludes() {
    return excludes;
  }

  @Override public boolean getExclusive() {
    return exclusive;
  }

  @Override public String getOutDirectory() {
    return outDirectory;
  }

  public Map<String, String> getOptions() {
    return options;
  }

  @Override public Target copyTarget(
      List<String> includes, List<String> excludes, boolean exclusive, String outDirectory) {
    return new CustomTarget(
        includes, excludes, exclusive, outDirectory, options, schemaHandlerFactory);
  }

  @Override public SchemaHandler newHandler() {
    return schemaHandlerFactory.create(
        includes, excludes, exclusive, outDirectory, options);
  }

  /**
   * Create and return an instance of {@link SchemaHandler.Factory}.
   *
   * @param schemaHandlerFactoryClass a fully qualified class name for a class that implements
   *     {@link SchemaHandler.Factory}. The class must have a no-arguments public constructor.
   */
  public static SchemaHandler.Factory newSchemaHandler(String schemaHandlerFactoryClass) {
    return new ClassNameSchemaHandlerFactory(schemaHandlerFactoryClass);
  }

  /**
   * This schema handler factory works even if the delegate handler class is itself not
   * serializable.
   */
  private static final class ClassNameSchemaHandlerFactory implements SchemaHandler.Factory {
    private final String schemaHandlerFactoryClass;

    private transient SchemaHandler.Factory cachedDelegate;

    ClassNameSchemaHandlerFactory(String schemaHandlerFactoryClass) {
      this.schemaHandlerFactoryClass = schemaHandlerFactoryClass;
    }

    private SchemaHandler.Factory delegate() {
      SchemaHandler.Factory cachedResult = cachedDelegate;
      if (cachedResult != null) return cachedResult;

      Class<?> schemaHandlerType;
      try {
        schemaHandlerType = Class.forName(schemaHandlerFactoryClass);
      } catch (ClassNotFoundException exception) {
        throw new IllegalArgumentException(
            "Couldn't find SchemaHandlerClass '" + schemaHandlerFactoryClass + "'");
      }

      java.lang.reflect.Constructor<?> constructor;
      try {
        constructor = schemaHandlerType.getConstructor();
      } catch (NoSuchMethodException exception) {
        throw new IllegalArgumentException(
            "No public constructor on " + schemaHandlerFactoryClass);
      }

      Object instance;
      try {
        instance = constructor.newInstance();
      } catch (ReflectiveOperationException exception) {
        throw new IllegalArgumentException(
            "Couldn't instantiate " + schemaHandlerFactoryClass, exception);
      }
      if (!(instance instanceof SchemaHandler.Factory)) {
        throw new IllegalArgumentException(
            schemaHandlerFactoryClass + " does not implement SchemaHandler.Factory");
      }
      SchemaHandler.Factory result = (SchemaHandler.Factory) instance;
      this.cachedDelegate = result;
      return result;
    }

    @Override public SchemaHandler create(
        List<String> includes,
        List<String> excludes,
        boolean exclusive,
        String outDirectory,
        Map<String, String> options) {
      return delegate().create(includes, excludes, exclusive, outDirectory, options);
    }
  }

  public SchemaHandler.Factory getSchemaHandlerFactory() {
    return schemaHandlerFactory;
  }
}
