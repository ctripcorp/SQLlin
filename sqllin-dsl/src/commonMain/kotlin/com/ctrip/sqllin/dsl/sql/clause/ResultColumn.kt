/*
 * Copyright (C) 2026 Ctrip.com.
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

@file:Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")

package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.annotation.StatementDslMaker
import kotlin.reflect.KProperty1

/**
 * An expression selected into a property of the result type [R], as `count(X) AS AuthorStats::books` selects
 * `count(*) AS books` into `AuthorStats.books`.
 *
 * A SELECT reads its rows into [R] by the names of [R]'s properties: a property is read from the column of the same
 * name, unless a result column gives it an expression. So the result columns of a SELECT name only the properties that
 * hold expressions, such as aggregate functions, and every other property is read from its column:
 *
 * ```kotlin
 * @Serializable
 * data class AuthorStats(val author: String, val books: Long, val totalPages: Long?)
 *
 * BookTable { table ->
 *     table SELECT listOf(count(X) AS AuthorStats::books, sum(pages) AS AuthorStats::totalPages) GROUP_BY author
 * }
 * // SELECT author,count(*) AS books,sum(pages) AS totalPages FROM book GROUP BY author
 * ```
 *
 * @param R The result type the expression is selected into
 *
 * @author Yuang Qiao
 */
public class ResultColumn<R> internal constructor(
    internal val element: ClauseElement<*>,
    internal val propertyName: String,
)

/**
 * Selects this element into [property] of the result type [R], as the SQL `expression AS name` does.
 *
 * The property's type has to be the type of the element's values, which is checked at compile time: `count(X)` goes
 * into a `Long` property, and not into an `Int` or a `String` one. Whether the property has to be nullable depends on
 * the rest of the query, as an aggregate function such as `sum` is NULL when no rows match, but not in a group of
 * GROUP BY, so it is checked when the statement is built, before anything runs.
 *
 * The property is matched by its name, so it can't be renamed with `@SerialName`.
 *
 * @param property The property of the result type that receives the element's value
 * @return The result column, to give to `SELECT`, alone or in a list
 */
@StatementDslMaker
public infix fun <R, P : Any> ClauseElement<P>.AS(property: KProperty1<R, P?>): ResultColumn<R> =
    ResultColumn(this, property.name)
