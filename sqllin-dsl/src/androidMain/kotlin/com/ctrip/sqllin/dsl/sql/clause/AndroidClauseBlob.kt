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
 * Android-specific implementation of BLOB clause handling.
 *
 * On Android, SQLite BLOB literals use hexadecimal notation (X'...' format).
 * This implementation converts ByteArray values to hex strings inline in the SQL,
 * avoiding the need for parameterized binding for BLOB values on Android.
 *
 * For example, a ByteArray of [0x01, 0x02, 0xFF] becomes: X'0102FF'
 *
 * @author Yuang Qiao
 */
internal class AndroidClauseBlob(
    valueName: String,
    table: Table<*>,
    isFunction: Boolean,
) : DefaultClauseBlob(valueName, table, isFunction) {

    /**
     * Appends a BLOB value to the SQL condition using Android's hex literal format.
     *
     * Instead of using parameterized binding, this converts ByteArray to hex notation.
     * Non-null values are encoded as X'hexstring' literals directly in the SQL.
     *
     * @param notNullSymbol The comparison operator (=, !=, etc.)
     * @param nullSymbol The SQL clause to use when blob is null (IS NULL, IS NOT NULL)
     * @param blob The ByteArray value to encode, or null
     * @return SelectCondition with hex-encoded SQL and no parameters
     */
    override fun appendBlob(
        notNullSymbol: String,
        nullSymbol: String,
        blob: ByteArray?
    ): SelectCondition {
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
                append("X'")
                blob toHexString this
                append('\'')
            }
        }
        return SelectCondition(sql, null)
    }

    /**
     * Converts ByteArray to uppercase hexadecimal string.
     *
     * Each byte is formatted as a two-digit hex value (00-FF).
     *
     * @param builder The StringBuilder to append hex characters to
     */
    private infix fun ByteArray.toHexString(builder: StringBuilder) = joinTo(
        buffer = builder,
        separator = "",
        transform = { "%02X".format(it) }
    )
}

/**
 * Platform-specific factory function for creating BLOB clause wrappers on Android.
 *
 * Returns an [AndroidClauseBlob] instance that uses hex literal encoding for BLOB values.
 *
 * @param valueName The column or function name
 * @param table The table this clause belongs to
 * @param isFunction True if this represents a SQL function result
 * @return AndroidClauseBlob instance
 */
public actual fun ClauseBlob(
    valueName: String,
    table: Table<*>,
    isFunction: Boolean,
): DefaultClauseBlob = AndroidClauseBlob(valueName, table, isFunction)