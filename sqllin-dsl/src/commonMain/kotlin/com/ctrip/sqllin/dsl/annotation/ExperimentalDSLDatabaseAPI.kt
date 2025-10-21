/*
 * Copyright (C) 2025 Ctrip.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctrip.sqllin.dsl.annotation

/**
 * Marks declarations that are **experimental** in SQLlin DSL database API.
 *
 * This annotation indicates that the API is still being refined and may undergo changes
 * in future releases. These APIs include experimental features that provide additional
 * functionality but may not be as stable as the core APIs.
 *
 * Any usage of a declaration annotated with `@ExperimentalDSLDatabaseAPI` must be accepted either by
 * annotating that usage with the [OptIn] annotation, e.g. `@OptIn(ExperimentalDSLDatabaseAPI::class)`,
 * or by using the compiler argument `-opt-in=com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI`.
 *
 * @see OptIn
 * @see RequiresOptIn
 */
@RequiresOptIn(
    message = "This is an experimental API for SQLlin DSL database operations. " +
            "It may be changed or removed in future releases. " +
            "Use with caution and be prepared for potential breaking changes.",
    level = RequiresOptIn.Level.WARNING
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS
)
@Retention(AnnotationRetention.BINARY)
public annotation class ExperimentalDSLDatabaseAPI