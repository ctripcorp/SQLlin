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
 * BLOBs as byte sequences, this class enables type-safe operations on binary data:
 * - Equality comparisons (NULL-safe)
 * - Inequality comparisons (NULL-safe)
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
    internal infix fun eq(blob: ByteArray?): SelectCondition = appendBlob("=", " IS NULL", blob)

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
    internal infix fun neq(blob: ByteArray?): SelectCondition = appendBlob("!=", " IS NOT NULL", blob)

    /**
     * Creates an inequality comparison condition against another BLOB column/function.
     *
     * @param clauseBlob The BLOB column/function to compare against
     * @return Condition expression comparing two BLOB columns
     */
    internal infix fun neq(clauseBlob: ClauseBlob): SelectCondition = appendClauseBlob("!=", clauseBlob)

    private fun appendBlob(notNullSymbol: String, nullSymbol: String, blob: ByteArray?): SelectCondition {
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

    override fun hashCode(): Int = valueName.hashCode() + table.tableName.hashCode()
    override fun equals(other: Any?): Boolean = (other as? ClauseBlob)?.let {
        it.valueName == valueName && it.table.tableName == table.tableName
    } ?: false
}