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
 * DSL marker for SQL statement functions to prevent implicit receiver nesting.
 *
 * Applied to top-level SQL statement functions (SELECT, INSERT, UPDATE, DELETE).
 *
 * @author Yuang Qiao
 */
@DslMarker
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
internal annotation class StatementDslMaker

/**
 * DSL marker for SQL keyword classes and properties to prevent implicit receiver nesting.
 *
 * Applied to SQL keyword constructs (WHERE, ORDER BY, etc.) and their properties.
 *
 * @author Yuang Qiao
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
internal annotation class KeyWordDslMaker

/**
 * DSL marker for SQL function builders to prevent implicit receiver nesting.
 *
 * Applied to SQL function builder functions (aggregate functions, etc.).
 *
 * @author Yuang Qiao
 */
@DslMarker
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
internal annotation class FunctionDslMaker

/**
 * DSL marker for generated column name properties.
 *
 * This annotation is applied by sqllin-processor to generated table column properties.
 * **Do not use this annotation manually** - it is intended for code generation only.
 *
 * @author Yuang Qiao
 */
@DslMarker
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
public annotation class ColumnNameDslMaker