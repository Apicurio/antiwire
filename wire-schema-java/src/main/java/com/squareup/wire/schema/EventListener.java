/*
 * Copyright (C) 2023 Square, Inc.
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

import com.squareup.wire.internal.Serializable;
import com.squareup.wire.schema.internal.TypeMover;
import java.util.List;

/**
 * Listener for metrics events. Extend this class to monitor WireRun and all schema handlers
 * ({@link SchemaHandler}) involved in the Protobuf schema manipulation.
 *
 * <p>The events' order is as follows:
 *
 * <ul>
 *   <li>runStart
 *   <li>loadSchemaStart
 *   <li>loadSchemaSuccess
 *   <li>treeShakeStart
 *   <li>treeShakeEnd
 *   <li>moveTypesStart
 *   <li>moveTypesEnd
 *   <li>schemaHandlersStart // Looping over all handlers.
 *       <ul>
 *         <li>schemaHandlerStart
 *         <li>schemaHandlerEnd
 *       </ul>
 *   <li>schemaHandlersEnd
 *   <li>runSuccess / runFailed
 * </ul>
 */
public abstract class EventListener {
  /** Invoked prior to Wire starting. */
  public void runStart(WireRun wireRun) {
  }

  /** Invoked after Wire has executed all operations. */
  public void runSuccess(WireRun wireRun) {
  }

  public void runFailed(List<String> errors) {
  }

  /**
   * Invoked prior to loading the Protobuf schema. this includes parsing {@code .proto} files,
   * and resolving all referenced types.
   */
  public void loadSchemaStart() {
  }

  /**
   * Invoked after having loaded the Protobuf {@code schema}. this includes parsing {@code .proto}
   * files, and resolving all referenced types.
   */
  public void loadSchemaSuccess(
      Schema schema) {
  }

  /** Invoked prior to refactoring the Protobuf {@code schema} by tree-shaking it using the
   * {@link PruningRules}. */
  public void treeShakeStart(
      Schema schema,
      PruningRules pruningRules) {
  }

  /** Invoked after having refactored the Protobuf schema by tree-shaking it using the
   * {@link PruningRules}. */
  public void treeShakeEnd(
      Schema refactoredSchema,
      PruningRules pruningRules) {
  }

  /** Invoked prior to refactoring the Protobuf {@code schema} by applying the {@code moves}. */
  public void moveTypesStart(
      Schema schema,
      List<TypeMover.Move> moves) {
  }

  /** Invoked after having refactored the Protobuf schema by applying the {@code moves}. */
  public void moveTypesEnd(
      Schema refactoredSchema,
      List<TypeMover.Move> moves) {
  }

  /** Invoked prior to executing all schema handlers ({@link SchemaHandler}). */
  public void schemaHandlersStart() {
  }

  /** Invoked after having executed all schema handlers ({@link SchemaHandler}). */
  public void schemaHandlersEnd() {
  }

  /** Invoked prior a schema handler ({@link SchemaHandler}) starting. */
  public void schemaHandlerStart(
      SchemaHandler schemaHandler,
      EmittingRules emittingRules) {
  }

  /** Invoked after a schema handler ({@link SchemaHandler}) has finished. */
  public void schemaHandlerEnd(
      SchemaHandler schemaHandler,
      EmittingRules emittingRules) {
  }

  /** Implementations of this interface must have a no-arguments public constructor. */
  public interface Factory extends Serializable {
    /**
     * Creates an instance of the {@link EventListener} for one Wire execution. The returned
     * {@link EventListener} instance will be used during the lifecycle of the Wire's task.
     */
    EventListener create();
  }
}
