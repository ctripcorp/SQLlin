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

/*
 * These DSL markers exist for IntelliJ IDEA, which gives a call to any function or property annotated with a
 * @DslMarker annotation one of its four DSL highlighting styles. They are applied to functions and properties
 * for that reason, and so don't provide the compiler's DSL scope control, which @DslMarker only gives when
 * applied to types. The compiler reports DSL_MARKER_APPLIED_TO_WRONG_TARGET for that use, so it's suppressed
 * wherever these annotations are applied.
 */

/**
 * DSL marker that highlights calls to SQL statement functions (SELECT, INSERT, UPDATE, DELETE, WHERE, ...) in
 * IntelliJ IDEA.
 *
 * @author Yuang Qiao
 */
@DslMarker
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
internal annotation class StatementDslMaker

/**
 * DSL marker that highlights SQL keywords, such as `X` and the `ASC` and `DESC` ordering, in IntelliJ IDEA.
 *
 * @author Yuang Qiao
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
internal annotation class KeyWordDslMaker

/**
 * DSL marker that highlights calls to SQL functions (aggregate, numeric and string functions) in IntelliJ IDEA.
 *
 * @author Yuang Qiao
 */
@DslMarker
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
internal annotation class FunctionDslMaker

/**
 * DSL marker that highlights the generated column properties in IntelliJ IDEA.
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