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
 * Clause String
 * @author yaqiao
 */

public class ClauseString(
    valueName: String,
    table: Table<*>,
    isFunction: Boolean,
) : ClauseElement(valueName, table, isFunction) {

    // Equals, ==
    internal infix fun eq(str: String?): SelectCondition = appendString("=", "IS", str)

    // Equals, append another ClauseString
    internal infix fun eq(clauseString: ClauseString): SelectCondition = appendClauseString("=", clauseString)

    // Not equals to, !=
    internal infix fun neq(str: String?): SelectCondition = appendString("!=", "IS NOT", str)

    // Not equal to, append another ClauseString
    internal infix fun neq(clauseString: ClauseString): SelectCondition = appendClauseString("!=", clauseString)

    internal infix fun like(regex: String): SelectCondition = appendRegex("LIKE", regex)

    internal infix fun glob(regex: String): SelectCondition = appendRegex("GLOB", regex)

    private fun appendRegex(symbol: String, regex: String): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(' ')
            append(symbol)
            append(' ')
            append('\'')
            append(regex)
            append('\'')
        }
        return SelectCondition(sql)
    }

    private fun appendString(notNullSymbol: String, nullSymbol: String, str: String?): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(' ')
            val isNull = str == null
            val symbol = if (isNull) nullSymbol else notNullSymbol
            append(symbol)
            append(' ')
            if (str == null)
                append(" NULL")
            else {
                append('\'')
                append(str)
                append('\'')
            }
        }
        return SelectCondition(sql)
    }

    private fun appendClauseString(symbol: String, clauseString: ClauseString): SelectCondition {
        val sql = buildString {
            append(table.tableName)
            append('.')
            append(valueName)
            append(' ')
            append(symbol)
            append(' ')
            append(clauseString.table.tableName)
            append('.')
            append(clauseString.valueName)
        }
        return SelectCondition(sql)
    }

    override fun hashCode(): Int = valueName.hashCode() + table.tableName.hashCode()
    override fun equals(other: Any?): Boolean = (other as? ClauseString)?.let {
        it.valueName == valueName && it.table.tableName == table.tableName
    } ?: false
}