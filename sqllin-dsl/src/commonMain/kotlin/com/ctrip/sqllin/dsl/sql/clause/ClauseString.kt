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
 * Wrapper for String column/function references in SQL clauses.
 *
 * Provides comparison and pattern matching operators for string values. Supports comparisons
 * against literal strings or other string columns/functions.
 *
 * Available operators:
 * - `lt`: Less than (<)
 * - `lte`: Less than or equal to (<=)
 * - `eq`: Equals (=) - handles null with IS NULL
 * - `neq`: Not equals (!=) - handles null with IS NOT NULL
 * - `gt`: Greater than (>)
 * - `gte`: Greater than or equal to (>=)
 * - `inIterable`: IN operator for checking membership in a collection
 * - `between`: BETWEEN operator for range checks
 * - `like`: LIKE pattern matching (case-insensitive, supports % and _ wildcards)
 * - `glob`: GLOB pattern matching (case-sensitive, supports * and ? wildcards)
 *
 * @author Yuang Qiao
 */
public class ClauseString(
    valueName: String,
    table: Table<*>,
    isFunction: Boolean = false,
) : ClauseElement(valueName, table, isFunction) {

    /** Equals (=), or IS NULL if value is null */
    internal infix fun eq(str: String?): SelectCondition = appendNullableString("=", " IS NULL", str)

    /** Equals (=) - compare against another column/function */
    internal infix fun eq(clauseString: ClauseString): SelectCondition = appendClauseString("=", clauseString)

    /** Not equals (!=), or IS NOT NULL if value is null */
    internal infix fun neq(str: String?): SelectCondition = appendNullableString("!=", " IS NOT NULL", str)

    /** Not equals (!=) - compare against another column/function */
    internal infix fun neq(clauseString: ClauseString): SelectCondition = appendClauseString("!=", clauseString)

    /**
     * Creates a less than comparison condition (<).
     *
     * @param str The String value to compare against
     * @return Condition expression for WHERE/HAVING clauses
     */
    internal infix fun lt(str: String): SelectCondition = appendString("<?", str)

    /**
     * Creates a less than comparison condition against another String column/function.
     *
     * @param clauseString The String column/function to compare against
     * @return Condition expression comparing two String columns
     */
    internal infix fun lt(clauseString: ClauseString): SelectCondition = appendClauseString("<", clauseString)

    /**
     * Creates a less than or equal to comparison condition (<=).
     *
     * @param str The String value to compare against
     * @return Condition expression for WHERE/HAVING clauses
     */
    internal infix fun lte(str: String): SelectCondition = appendString("<=?", str)

    /**
     * Creates a less than or equal to comparison condition against another String column/function.
     *
     * @param clauseString The String column/function to compare against
     * @return Condition expression comparing two String columns
     */
    internal infix fun lte(clauseString: ClauseString): SelectCondition = appendClauseString("<=", clauseString)

    /**
     * Creates a greater than comparison condition (>).
     *
     * @param str The String value to compare against
     * @return Condition expression for WHERE/HAVING clauses
     */
    internal infix fun gt(str: String): SelectCondition = appendString(">?", str)

    /**
     * Creates a greater than comparison condition against another String column/function.
     *
     * @param clauseString The String column/function to compare against
     * @return Condition expression comparing two String columns
     */
    internal infix fun gt(clauseString: ClauseString): SelectCondition = appendClauseString(">", clauseString)

    /**
     * Creates a greater than or equal to comparison condition (>=).
     *
     * @param str The String value to compare against
     * @return Condition expression for WHERE/HAVING clauses
     */
    internal infix fun gte(str: String): SelectCondition = appendString(">=?", str)

    /**
     * Creates a greater than or equal to comparison condition against another String column/function.
     *
     * @param clauseString The String column/function to compare against
     * @return Condition expression comparing two String columns
     */
    internal infix fun gte(clauseString: ClauseString): SelectCondition = appendClauseString(">=", clauseString)

    /**
     * LIKE operator - case-insensitive pattern matching.
     *
     * Wildcards: `%` (any characters), `_` (single character)
     * Example: `"John%"` matches "John", "Johnson", etc.
     */
    internal infix fun like(regex: String): SelectCondition = appendRegex(" LIKE ", regex)

    /**
     * GLOB operator - case-sensitive pattern matching.
     *
     * Wildcards: `*` (any characters), `?` (single character)
     * Example: `"John*"` matches "John", "Johnson", etc. (case-sensitive)
     */
    internal infix fun glob(regex: String): SelectCondition = appendRegex(" GLOB ", regex)

    private fun appendRegex(symbol: String, regex: String): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(symbol)
            append('?')
        }
        return SelectCondition(sql, mutableListOf(regex))
    }

    private fun appendNullableString(notNullSymbol: String, nullSymbol: String, str: String?): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            val isNull = str == null
            if (isNull) {
                append(nullSymbol)
            } else {
                append(notNullSymbol)
                append('?')
            }
        }
        return SelectCondition(sql, if (str == null) null else mutableListOf(str))
    }

    private fun appendString(symbol: String, str: String): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(symbol)
        }
        return SelectCondition(sql, mutableListOf(str))
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
        return SelectCondition(sql, null)
    }

    /**
     * Creates an IN condition to check if the String value is in a collection.
     *
     * Generates SQL like: `column IN (?, ?, ...)`
     *
     * @param strings The collection of String values to check against
     * @return Condition expression for WHERE/HAVING clauses
     * @throws IllegalArgumentException if the collection is empty
     */
    internal infix fun inIterable(strings: Iterable<String>): SelectCondition {
        val parameters = strings.toMutableList<Any?>()
        require(parameters.isNotEmpty()) { "Param 'strings' must not be empty!!!" }
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(" IN (")

            append('?')
            repeat(parameters.size - 1) {
                append(",?")
            }
            append(')')
        }
        return SelectCondition(sql, parameters)
    }

    /**
     * Creates a BETWEEN condition to check if the String value is within a range.
     *
     * Generates SQL like: `column BETWEEN ? AND ?`
     *
     * @param range A Pair containing the lower and upper bounds (inclusive)
     * @return Condition expression for WHERE/HAVING clauses
     */
    internal infix fun between(range: Pair<String, String>): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(" BETWEEN ? AND ?")
        }
        return SelectCondition(sql, mutableListOf(range.first, range.second))
    }

    override fun hashCode(): Int = valueName.hashCode() + table.tableName.hashCode()
    override fun equals(other: Any?): Boolean = (other as? ClauseString)?.let {
        it.valueName == valueName && it.table.tableName == table.tableName
    } ?: false
}