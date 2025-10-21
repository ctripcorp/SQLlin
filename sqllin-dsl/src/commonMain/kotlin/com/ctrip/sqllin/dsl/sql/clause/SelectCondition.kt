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

/**
 * Represents a condition expression used in WHERE or HAVING clauses.
 *
 * Encapsulates a single condition (e.g., `id = ?`, `age > 18`) along with its parameterized
 * values. Supports combining conditions with AND/OR operators to build complex predicates.
 *
 * Conditions are built by comparison operations on [ClauseElement] instances:
 * ```kotlin
 * userTable.id EQ 42  // Creates: SelectCondition("id = ?", ["42"])
 * userTable.age GT 18 // Creates: SelectCondition("age > ?", ["18"])
 * userTable.image EQ byteArray // Creates: SelectCondition("image = ?", [byteArray])
 * ```
 *
 * @property conditionSQL The SQL condition expression (may contain ? placeholders)
 * @property parameters Parameterized query values (String, ByteArray, etc.), or null if none
 *
 * @author Yuang Qiao
 */
public class SelectCondition internal constructor(
    internal val conditionSQL: String,
    internal val parameters: MutableList<Any?>?,
) {

    /**
     * Combines this condition with another using OR.
     *
     * Creates: `(condition1) OR (condition2)`
     */
    internal infix fun or(next: SelectCondition): SelectCondition = append("OR", next)

    /**
     * Combines this condition with another using AND.
     *
     * Creates: `(condition1) AND (condition2)`
     */
    internal infix fun and(next: SelectCondition): SelectCondition = append("AND", next)

    private fun append(symbol: String, next: SelectCondition): SelectCondition {
        val sql = buildString {
            append(conditionSQL)
            append(" $symbol ")
            append(next.conditionSQL)
        }
        val combinedParameters = when {
            parameters == null && next.parameters != null -> next.parameters
            parameters != null && next.parameters == null -> parameters
            parameters == null && next.parameters == null -> null
            else -> {
                parameters!!.addAll(next.parameters!!)
                parameters
            }
        }
        return SelectCondition(sql, combinedParameters)
    }
}