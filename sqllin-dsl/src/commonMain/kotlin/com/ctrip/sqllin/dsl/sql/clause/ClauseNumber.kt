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
 * Wrapper for numeric column/function references in SQL clauses.
 *
 * Provides comparison and set operators for numeric values (Byte, Short, Int, Long, Float, Double).
 * All value-based comparisons use parameterized binding (?) to prevent SQL injection and ensure
 * proper type handling across platforms.
 *
 * Available operators:
 * - `lt`: Less than (<) - parameterized
 * - `lte`: Less than or equal (<=) - parameterized
 * - `eq`: Equals (=) - parameterized or IS NULL
 * - `neq`: Not equals (!=) - parameterized or IS NOT NULL
 * - `gt`: Greater than (>) - parameterized
 * - `gte`: Greater than or equal (>=) - parameterized
 * - `inIterable`: IN (?, ?, ...) - all values parameterized
 * - `between`: BETWEEN ? AND ? - both boundaries parameterized
 *
 * @author Yuang Qiao
 */
public class ClauseNumber(
    valueName: String,
    table: Table<*>,
    isFunction: Boolean,
) : ClauseElement(valueName, table, isFunction) {

    /**
     * Less than (<) comparison using parameterized binding.
     *
     * Generates: `column < ?`
     *
     * @param number The value to compare against
     * @return SelectCondition with placeholder and bound parameter
     */
    internal infix fun lt(number: Number): SelectCondition = appendNumber("<?", number)

    /** Less than (<) - compare against another column/function */
    internal infix fun lt(clauseNumber: ClauseNumber): SelectCondition = appendClauseNumber("<", clauseNumber)

    /**
     * Less than or equal (<=) comparison using parameterized binding.
     *
     * Generates: `column <= ?`
     *
     * @param number The value to compare against
     * @return SelectCondition with placeholder and bound parameter
     */
    internal infix fun lte(number: Number): SelectCondition = appendNumber("<=?", number)

    /** Less than or equal (<=) - compare against another column/function */
    internal infix fun lte(clauseNumber: ClauseNumber): SelectCondition = appendClauseNumber("<=", clauseNumber)

    /**
     * Equals (=) comparison using parameterized binding, or IS NULL for null values.
     *
     * Generates: `column = ?` or `column IS NULL`
     *
     * @param number The value to compare against, or null
     * @return SelectCondition with placeholder (if non-null) and bound parameter
     */
    internal infix fun eq(number: Number?): SelectCondition = appendNullableNumber("=", " IS NULL", number)

    /** Equals (=) - compare against another column/function */
    internal infix fun eq(clauseNumber: ClauseNumber): SelectCondition = appendClauseNumber("=", clauseNumber)

    /**
     * Not equals (!=) comparison using parameterized binding, or IS NOT NULL for null values.
     *
     * Generates: `column != ?` or `column IS NOT NULL`
     *
     * @param number The value to compare against, or null
     * @return SelectCondition with placeholder (if non-null) and bound parameter
     */
    internal infix fun neq(number: Number?): SelectCondition = appendNullableNumber("!=", " IS NOT NULL", number)

    /** Not equals (!=) - compare against another column/function */
    internal infix fun neq(clauseNumber: ClauseNumber): SelectCondition = appendClauseNumber("!=", clauseNumber)

    /**
     * Greater than (>) comparison using parameterized binding.
     *
     * Generates: `column > ?`
     *
     * @param number The value to compare against
     * @return SelectCondition with placeholder and bound parameter
     */
    internal infix fun gt(number: Number): SelectCondition = appendNumber(">?", number)

    /** Greater than (>) - compare against another column/function */
    internal infix fun gt(clauseNumber: ClauseNumber): SelectCondition = appendClauseNumber(">", clauseNumber)

    /**
     * Greater than or equal (>=) comparison using parameterized binding.
     *
     * Generates: `column >= ?`
     *
     * @param number The value to compare against
     * @return SelectCondition with placeholder and bound parameter
     */
    internal infix fun gte(number: Number): SelectCondition = appendNumber(">=?", number)

    /** Greater than or equal (>=) - compare against another column/function */
    internal infix fun gte(clauseNumber: ClauseNumber): SelectCondition = appendClauseNumber(">=", clauseNumber)

    /**
     * IN operator - checks if value is in the given set.
     *
     * Uses parameterized binding for all values to prevent SQL injection.
     * Generates: `column IN (?, ?, ?, ...)`
     *
     * @param numbers Non-empty iterable of numbers to check against
     * @return SelectCondition with placeholders and bound parameters
     * @throws IllegalArgumentException if numbers is empty
     */
    internal infix fun inIterable(numbers: Iterable<Number>): SelectCondition {
        val parameters = numbers.toMutableList<Any?>()
        require(parameters.isNotEmpty()) { "Param 'numbers' must not be empty!!!" }
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
     * BETWEEN operator - checks if value is within a range (inclusive).
     *
     * Uses parameterized binding for both range boundaries.
     * Generates: `column BETWEEN ? AND ?`
     *
     * @param range The inclusive range to check (start..end)
     * @return SelectCondition with placeholders and bound parameters
     */
    internal infix fun between(range: LongRange): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(" BETWEEN ? AND ?")
        }
        return SelectCondition(sql, mutableListOf(range.first, range.last))
    }

    private fun appendNumber(symbol: String, number: Number): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(symbol)
        }
        return SelectCondition(sql, mutableListOf(number))
    }

    private fun appendNullableNumber(notNullSymbol: String, nullSymbol: String, number: Number?): SelectCondition {
        val builder = StringBuilder()
        if (!isFunction) {
            builder.append(table.tableName)
            builder.append('.')
        }
        builder.append(valueName)
        val parameters = if (number == null){
            builder.append(nullSymbol)
            null
        } else {
            builder.append(notNullSymbol)
            builder.append('?')
            mutableListOf<Any?>(number)
        }
        return SelectCondition(builder.toString(), parameters)
    }

    private fun appendClauseNumber(symbol: String, clauseNumber: ClauseNumber): SelectCondition {
        val sql = buildString {
            append(table.tableName)
            append('.')
            append(valueName)
            append(symbol)
            append(clauseNumber.table.tableName)
            append('.')
            append(clauseNumber.valueName)
        }
        return SelectCondition(sql, null)
    }

    override fun hashCode(): Int = valueName.hashCode() + table.tableName.hashCode()
    override fun equals(other: Any?): Boolean = (other as? ClauseNumber)?.let {
        it.valueName == valueName && it.table.tableName == table.tableName
    } ?: false
}