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

import com.ctrip.sqllin.dsl.sql.operation.parametersOf

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
 * @property tables The tables and views that the subqueries of the condition read, which an observed query watches
 * @property operator The operator, `AND` or `OR`, that this condition combines others with, or null if it doesn't
 *
 * @author Yuang Qiao
 */
public class SelectCondition internal constructor(
    internal val conditionSQL: String,
    internal val parameters: MutableList<Any?>?,
    internal val tables: Set<String> = emptySet(),
    private val operator: String? = null,
) {

    /**
     * Combines this condition with another using OR.
     *
     * Creates: `condition1 OR condition2`, with a condition that combines others with AND in parentheses.
     */
    internal infix fun or(next: SelectCondition): SelectCondition = append("OR", next)

    /**
     * Combines this condition with another using AND.
     *
     * Creates: `condition1 AND condition2`, with a condition that combines others with OR in parentheses.
     */
    internal infix fun and(next: SelectCondition): SelectCondition = append("AND", next)

    private fun append(symbol: String, next: SelectCondition): SelectCondition {
        val sql = buildString {
            appendOperand(this, symbol)
            append(" $symbol ")
            next.appendOperand(this, symbol)
        }
        // A new list, as this condition may be kept in a variable and used again, with its own parameters
        return SelectCondition(sql, parametersOf(parameters, next.parameters), tables + next.tables, symbol)
    }

    /**
     * Appends this condition to [builder] as an operand of [symbol]: in parentheses if it combines others with the
     * other operator. The infix AND and OR apply in the order they are written, as Kotlin's infix functions do, while
     * SQL gives AND precedence over OR, so `(a OR b) AND c` would otherwise become `a OR (b AND c)`.
     */
    private fun appendOperand(builder: StringBuilder, symbol: String) {
        if (operator != null && operator != symbol) {
            builder.append('(')
            builder.append(conditionSQL)
            builder.append(')')
        } else {
            builder.append(conditionSQL)
        }
    }
}