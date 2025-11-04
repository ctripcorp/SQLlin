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

import com.ctrip.sqllin.dsl.sql.Table

/**
 * Wrapper for Boolean column/function references in SQL clauses.
 *
 * Provides comparison operators for Boolean values. Since SQLite stores booleans as integers
 * (0 for false, 1 for true), comparisons are translated to numeric expressions:
 * - `column IS true` → `column > 0`
 * - `column IS false` → `column <= 0`
 *
 * @author Yuang Qiao
 */
public class ClauseBoolean(
    valueName: String,
    table: Table<*>,
) : ClauseElement(valueName, table, false) {

    /**
     * Creates a condition comparing this Boolean column/function to a value.
     *
     * Since SQLite stores booleans as integers (0 = false, 1 = true), this generates
     * numeric comparison SQL:
     * - `true` → `column > 0`
     * - `false` → `column <= 0`
     * - `null` → `column IS NULL`
     *
     * @param bool The Boolean value to compare against
     * @return Condition expression for use in WHERE clauses
     */
    internal infix fun _is(bool: Boolean?): SelectCondition {
        val sql = buildString {
            append(table.tableName)
            append('.')
            append(valueName)
            append(
                when {
                    bool == null -> " IS NULL"
                    bool -> ">0"
                    else ->"<=0"
                }
            )
        }
        return SelectCondition(sql, null)
    }

    /**
     * Creates a negated condition comparing this Boolean column/function to a value.
     *
     * This is the inverse of [_is], generating negated numeric comparison SQL:
     * - `true` → `column <= 0` (NOT true = false)
     * - `false` → `column > 0` (NOT false = true)
     * - `null` → `column IS NOT NULL`
     *
     * ### Usage
     * Used internally by DSL operators to create "is not" conditions:
     * ```kotlin
     * WHERE(UserTable.isActive ISNOT true)  // finds inactive users
     * WHERE(UserTable.isDeleted ISNOT false) // finds deleted users
     * ```
     *
     * @param bool The Boolean value to negate and compare against
     * @return Negated condition expression for use in WHERE clauses
     * @see _is
     */
    internal infix fun _isNot(bool: Boolean?): SelectCondition {
        val sql = buildString {
            append(table.tableName)
            append('.')
            append(valueName)
            append(
                when {
                    bool == null -> " IS NOT NULL"
                    bool -> "<=0"
                    else -> ">0"
                }
            )
        }
        return SelectCondition(sql, null)
    }

    override fun hashCode(): Int = valueName.hashCode() + table.tableName.hashCode()
    override fun equals(other: Any?): Boolean = (other as? ClauseBoolean)?.let {
        it.valueName == valueName && it.table.tableName == table.tableName
    } ?: false
}