/*
 * Copyright (C) 2025 Ctrip.com.
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
 * Wrapper for BLOB (Binary Large Object) column/function references in SQL clauses.
 *
 * Provides comparison operators for BLOB values stored as ByteArray. Since SQLite stores
 * BLOBs as byte sequences, this class enables type-safe operations on binary data.
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
 *
 * BLOB columns are commonly used for storing:
 * - Images, audio, video files
 * - Serialized objects
 * - Encrypted data
 * - Binary protocols or formats
 *
 * @author Yuang Qiao
 */
public class ClauseBlob(
    valueName: String,
    table: Table<*>,
    isFunction: Boolean,
) : ClauseElement(valueName, table, isFunction) {

    /**
     * Creates an equality comparison condition (=).
     *
     * Handles NULL values appropriately:
     * - If `blob` is null, generates `IS NULL` condition
     * - Otherwise, generates parameterized equality comparison
     *
     * @param blob The ByteArray value to compare against, or null
     * @return Condition expression for WHERE/HAVING clauses
     */
    internal infix fun eq(blob: ByteArray?): SelectCondition = appendNullableBlob("=", " IS NULL", blob)

    /**
     * Creates an equality comparison condition against another BLOB column/function.
     *
     * @param clauseBlob The BLOB column/function to compare against
     * @return Condition expression comparing two BLOB columns
     */
    internal infix fun eq(clauseBlob: ClauseBlob): SelectCondition = appendClauseBlob("=", clauseBlob)

    /**
     * Creates an inequality comparison condition (!=).
     *
     * Handles NULL values appropriately:
     * - If `blob` is null, generates `IS NOT NULL` condition
     * - Otherwise, generates parameterized inequality comparison
     *
     * @param blob The ByteArray value to compare against, or null
     * @return Condition expression for WHERE/HAVING clauses
     */
    internal infix fun neq(blob: ByteArray?): SelectCondition = appendNullableBlob("!=", " IS NOT NULL", blob)

    /**
     * Creates an inequality comparison condition against another BLOB column/function.
     *
     * @param clauseBlob The BLOB column/function to compare against
     * @return Condition expression comparing two BLOB columns
     */
    internal infix fun neq(clauseBlob: ClauseBlob): SelectCondition = appendClauseBlob("!=", clauseBlob)

    /**
     * Creates a less than comparison condition (<).
     *
     * @param byteArray The ByteArray value to compare against
     * @return Condition expression for WHERE/HAVING clauses
     */
    internal infix fun lt(byteArray: ByteArray): SelectCondition = appendBlob("<?", byteArray)

    /**
     * Creates a less than comparison condition against another BLOB column/function.
     *
     * @param clauseBlob The BLOB column/function to compare against
     * @return Condition expression comparing two BLOB columns
     */
    internal infix fun lt(clauseBlob: ClauseBlob): SelectCondition = appendClauseBlob("<", clauseBlob)

    /**
     * Creates a less than or equal to comparison condition (<=).
     *
     * @param byteArray The ByteArray value to compare against
     * @return Condition expression for WHERE/HAVING clauses
     */
    internal infix fun lte(byteArray: ByteArray): SelectCondition = appendBlob("<=?", byteArray)

    /**
     * Creates a less than or equal to comparison condition against another BLOB column/function.
     *
     * @param clauseBlob The BLOB column/function to compare against
     * @return Condition expression comparing two BLOB columns
     */
    internal infix fun lte(clauseBlob: ClauseBlob): SelectCondition = appendClauseBlob("<=", clauseBlob)

    /**
     * Creates a greater than comparison condition (>).
     *
     * @param byteArray The ByteArray value to compare against
     * @return Condition expression for WHERE/HAVING clauses
     */
    internal infix fun gt(byteArray: ByteArray): SelectCondition = appendBlob(">?", byteArray)

    /**
     * Creates a greater than comparison condition against another BLOB column/function.
     *
     * @param clauseBlob The BLOB column/function to compare against
     * @return Condition expression comparing two BLOB columns
     */
    internal infix fun gt(clauseBlob: ClauseBlob): SelectCondition = appendClauseBlob(">", clauseBlob)

    /**
     * Creates a greater than or equal to comparison condition (>=).
     *
     * @param byteArray The ByteArray value to compare against
     * @return Condition expression for WHERE/HAVING clauses
     */
    internal infix fun gte(byteArray: ByteArray): SelectCondition = appendBlob(">=?", byteArray)

    /**
     * Creates a greater than or equal to comparison condition against another BLOB column/function.
     *
     * @param clauseBlob The BLOB column/function to compare against
     * @return Condition expression comparing two BLOB columns
     */
    internal infix fun gte(clauseBlob: ClauseBlob): SelectCondition = appendClauseBlob(">=", clauseBlob)

    private fun appendNullableBlob(notNullSymbol: String, nullSymbol: String, blob: ByteArray?): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            if (blob == null) {
                append(nullSymbol)
            } else {
                append(notNullSymbol)
                append('?')
            }
        }
        return SelectCondition(sql, if (blob == null) null else mutableListOf(blob))
    }

    private fun appendBlob(symbol: String, blob: ByteArray): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(symbol)
        }
        return SelectCondition(sql, mutableListOf(blob))
    }

    private fun appendClauseBlob(symbol: String, clauseBlob: ClauseBlob): SelectCondition {
        val sql = buildString {
            append(table.tableName)
            append('.')
            append(valueName)
            append(' ')
            append(symbol)
            append(' ')
            append(clauseBlob.table.tableName)
            append('.')
            append(clauseBlob.valueName)
        }
        return SelectCondition(sql, null)
    }

    /**
     * Creates an IN condition to check if the BLOB value is in a collection.
     *
     * Generates SQL like: `column IN (?, ?, ...)`
     *
     * @param blobs The collection of ByteArray values to check against
     * @return Condition expression for WHERE/HAVING clauses
     * @throws IllegalArgumentException if the collection is empty
     */
    internal infix fun inIterable(blobs: Iterable<ByteArray>): SelectCondition {
        val parameters = blobs.toMutableList<Any?>()
        require(parameters.isNotEmpty()) { "Param 'blobs' must not be empty!!!" }
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
     * Creates a BETWEEN condition to check if the BLOB value is within a range.
     *
     * Generates SQL like: `column BETWEEN ? AND ?`
     *
     * @param range A Pair containing the lower and upper bounds (inclusive)
     * @return Condition expression for WHERE/HAVING clauses
     */
    internal infix fun between(range: Pair<ByteArray, ByteArray>): SelectCondition {
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
    override fun equals(other: Any?): Boolean = (other as? ClauseBlob)?.let {
        it.valueName == valueName && it.table.tableName == table.tableName
    } ?: false
}