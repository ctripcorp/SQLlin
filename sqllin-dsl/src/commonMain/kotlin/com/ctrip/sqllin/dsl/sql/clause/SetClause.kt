/*
 * Copyright (C) 2022 Ctrip.com.
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

package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.annotation.StatementDslMaker
import com.ctrip.sqllin.dsl.sql.compiler.storedValue

/**
 * SET clause for UPDATE statements.
 *
 * Builds column assignments using parameterized binding for all values.
 * Format: `column1 = ?, column2 = ?, ...`
 *
 * All values (including null) are passed as parameters to ensure type safety and
 * prevent SQL injection across all platforms.
 *
 * Used in UPDATE operations to specify new values:
 * ```kotlin
 * UPDATE(user) SET {
 *     it.name = "John"      // Generates: name = ? with parameter "John"
 *     it.age = 30           // Generates: age = ? with parameter 30
 *     it.avatar = byteArray // Generates: avatar = ? with parameter byteArray
 * } WHERE (user.id EQ 42)
 * ```
 *
 * @param T The entity type being updated
 *
 * @author Yuang Qiao
 */
public class SetClause<T> : Clause<T> {

    private val clauseBuilder = StringBuilder()

    /**
     * List of parameter values to bind to the SQL statement.
     *
     * Null until first property assignment. Contains values in order of appearance.
     * Supports any type: String, Number, Boolean, ByteArray, null, etc.
     */
    internal var parameters: MutableList<Any?>? = null
        private set

    /**
     * The expressions given to columns by `SET(listOf(...))`, which UPDATE checks against its table, or null for the
     * values assigned in `SET {}`.
     */
    internal var assignments: List<ResultColumn<T>>? = null

    /**
     * Appends a column assignment to the SET clause using parameterized binding.
     *
     * Generates: `propertyName = ?` and adds the value to parameters list.
     *
     * @param propertyName The column name to update
     * @param propertyValue The new value (any type including null)
     */
    public fun appendAny(propertyName: String, propertyValue: Any?) {
        clauseBuilder.append(propertyName)
        clauseBuilder.append("=?,")
        val params = parameters ?: ArrayList<Any?>().also {
            parameters = it
        }
        params.add(storedValue(propertyValue))
    }

    /**
     * Finalizes the SET clause by removing trailing comma.
     *
     * @return The complete SET clause SQL string
     */
    internal fun finalize(): String = clauseBuilder.apply {
        if (this[lastIndex] == ',')
            deleteAt(lastIndex)
    }.toString()
}

@Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")
@StatementDslMaker
public inline fun <T> SET(block: SetClause<T>.() -> Unit): SetClause<T> = SetClause<T>().apply(block)

/**
 * Sets columns to expressions, as SQL's `SET column = expression` does, such as one computed from the row's own columns:
 * ```kotlin
 * // UPDATE person SET visits=(person.visits + 1),seen=datetime('now') WHERE person.id=?
 * table UPDATE SET(listOf((visits + 1) AS Person::visits, datetime("now") AS Person::seen)) WHERE (id EQ 7)
 * ```
 * Each expression is given to a property of the row type with `AS`, which checks its type at compile time. UPDATE checks
 * the rest when the statement is built: an expression that can be NULL can only set a nullable column, and it can only
 * read the columns of the table it updates, and not be an aggregate function.
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public fun <T> SET(assignments: Iterable<ResultColumn<T>>): SetClause<T> =
    SetClause<T>().apply { this.assignments = assignments.toList() }

/**
 * Sets a column to an expression, as SQL's `SET column = expression` does: `SET((visits + 1) AS Person::visits)`.
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public fun <T> SET(assignment: ResultColumn<T>): SetClause<T> = SET(listOf(assignment))

