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

import com.ctrip.sqllin.dsl.annotation.StatementDslMaker

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
        params.add(propertyValue)
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

@StatementDslMaker
public inline fun <T> SET(block: SetClause<T>.() -> Unit): SetClause<T> = SetClause<T>().apply(block)