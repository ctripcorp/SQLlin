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
     * @param bool The Boolean value to compare against
     * @return Condition expression (e.g., `column > 0` for true, `column <= 0` for false)
     */
    internal infix fun _is(bool: Boolean): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            if (bool)
                append('>')
            else
                append("<=")
            append('0')
        }
        return SelectCondition(sql, null)
    }

    override fun hashCode(): Int = valueName.hashCode() + table.tableName.hashCode()
    override fun equals(other: Any?): Boolean = (other as? ClauseBoolean)?.let {
        it.valueName == valueName && it.table.tableName == table.tableName
    } ?: false
}