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

package com.ctrip.sqllin.driver

/**
 * Platform-agnostic interface for iterating over query results.
 *
 * Provides methods to access column data by index and navigate through result rows.
 *
 * @author Yuang Qiao
 */
public interface CommonCursor : AutoCloseable {

    /**
     * Gets the value of the column as an Int.
     */
    public fun getInt(columnIndex: Int): Int

    /**
     * Gets the value of the column as a Long.
     */
    public fun getLong(columnIndex: Int): Long

    /**
     * Gets the value of the column as a Float.
     */
    public fun getFloat(columnIndex: Int): Float

    /**
     * Gets the value of the column as a Double.
     */
    public fun getDouble(columnIndex: Int): Double

    /**
     * Gets the value of the column as a String, or null if the column is NULL.
     */
    public fun getString(columnIndex: Int): String?

    /**
     * Gets the value of the column as a ByteArray, or null if the column is NULL.
     */
    public fun getByteArray(columnIndex: Int): ByteArray?

    /**
     * Gets the zero-based index for the given column name.
     *
     * @throws IllegalArgumentException if the column doesn't exist
     */
    public fun getColumnIndex(columnName: String): Int

    /**
     * Iterates over all rows, invoking the block with the current row index.
     */
    public fun forEachRow(block: (Int) -> Unit)

    /**
     * Moves to the next row.
     *
     * @return `true` if the move was successful, `false` if there are no more rows
     */
    public fun next(): Boolean

    /**
     * Checks if the column value is NULL.
     */
    public fun isNull(columnIndex: Int): Boolean

    /**
     * Closes the cursor and releases resources.
     */
    public override fun close()
}