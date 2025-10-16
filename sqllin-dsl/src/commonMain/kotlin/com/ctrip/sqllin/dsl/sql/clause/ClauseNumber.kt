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
 * Supports comparisons against literal numbers or other numeric columns/functions.
 *
 * Available operators:
 * - `lt`: Less than (<)
 * - `lte`: Less than or equal (<=)
 * - `eq`: Equals (=) - handles null with IS NULL
 * - `neq`: Not equals (!=) - handles null with IS NOT NULL
 * - `gt`: Greater than (>)
 * - `gte`: Greater than or equal (>=)
 * - `inIterable`: IN (value1, value2, ...)
 * - `between`: BETWEEN start AND end
 *
 * @author Yuang Qiao
 */
public class ClauseNumber(
    valueName: String,
    table: Table<*>,
    isFunction: Boolean,
) : ClauseElement(valueName, table, isFunction) {

    /** Less than (<) */
    internal infix fun lt(number: Number): SelectCondition = appendNumber("<", number)

    /** Less than (<) - compare against another column/function */
    internal infix fun lt(clauseNumber: ClauseNumber): SelectCondition = appendClauseNumber("<", clauseNumber)

    /** Less than or equal (<=) */
    internal infix fun lte(number: Number): SelectCondition = appendNumber("<=", number)

    /** Less than or equal (<=) - compare against another column/function */
    internal infix fun lte(clauseNumber: ClauseNumber): SelectCondition = appendClauseNumber("<=", clauseNumber)

    /** Equals (=), or IS NULL if value is null */
    internal infix fun eq(number: Number?): SelectCondition = appendNullableNumber("=", " IS", number)

    /** Equals (=) - compare against another column/function */
    internal infix fun eq(clauseNumber: ClauseNumber): SelectCondition = appendClauseNumber("=", clauseNumber)

    /** Not equals (!=), or IS NOT NULL if value is null */
    internal infix fun neq(number: Number?): SelectCondition = appendNullableNumber("!=", " IS NOT", number)

    /** Not equals (!=) - compare against another column/function */
    internal infix fun neq(clauseNumber: ClauseNumber): SelectCondition = appendClauseNumber("!=", clauseNumber)

    /** Greater than (>) */
    internal infix fun gt(number: Number): SelectCondition = appendNumber(">", number)

    /** Greater than (>) - compare against another column/function */
    internal infix fun gt(clauseNumber: ClauseNumber): SelectCondition = appendClauseNumber(">", clauseNumber)

    /** Greater than or equal (>=) */
    internal infix fun gte(number: Number): SelectCondition = appendNumber(">=", number)

    /** Greater than or equal (>=) - compare against another column/function */
    internal infix fun gte(clauseNumber: ClauseNumber): SelectCondition = appendClauseNumber(">=", clauseNumber)

    /**
     * IN operator - checks if value is in the given set.
     *
     * Generates: `column IN (1, 2, 3, ...)`
     */
    internal infix fun inIterable(numbers: Iterable<Number>): SelectCondition {
        val iterator = numbers.iterator()
        require(iterator.hasNext()) { "Param 'numbers' must not be empty!!!" }
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(" IN (")
            do {
                append(iterator.next())
                val hasNext = iterator.hasNext()
                val symbol = if (hasNext) ',' else ')'
                append(symbol)
            } while (hasNext)
        }
        return SelectCondition(sql, null)
    }

    /**
     * BETWEEN operator - checks if value is within a range (inclusive).
     *
     * Generates: `column BETWEEN start AND end`
     */
    internal infix fun between(range: LongRange): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(" BETWEEN ")
            append(range.first)
            append(" AND ")
            append(range.last)
        }
        return SelectCondition(sql, null)
    }

    private fun appendNumber(symbol: String, number: Number): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(symbol)
            append(number)
        }
        return SelectCondition(sql, null)
    }

    private fun appendNullableNumber(notNullSymbol: String, nullSymbol: String, number: Number?): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            if (number == null){
                append(nullSymbol)
                append(" NULL")

            } else {
                append(notNullSymbol)
                append(number)
            }
        }
        return SelectCondition(sql, null)
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