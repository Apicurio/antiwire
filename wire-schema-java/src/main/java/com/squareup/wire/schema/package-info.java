/*
 * Copyright (C) 2026 the antiwire authors
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

/**
 * Pure-Java port of Wire's schema model at the pinned upstream tag (DEC-1 in
 * docs/decisions.md).
 *
 * <p>Package and type names intentionally match upstream so that upstream test sources run
 * with minimal adaptation against this artifact and consumers such as Apicurio Registry stay
 * source-compatible. Ported with TASK-10 through TASK-12; its tests landed with TASK-13.
 */
package com.squareup.wire.schema;
