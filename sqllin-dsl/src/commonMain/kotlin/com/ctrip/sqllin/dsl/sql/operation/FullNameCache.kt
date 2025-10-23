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

package com.ctrip.sqllin.dsl.sql.operation

import com.ctrip.sqllin.dsl.sql.Table

/**
 * Cached qualified names for Kotlin types used in SQLite type mapping.
 *
 * Provides pre-computed fully qualified names for performance during CREATE TABLE generation.
 * These names are matched against kotlinx.serialization descriptor serial names to determine
 * the appropriate SQLite column type.
 *
 * Used by [Create.buildSQL] to map Kotlin types to SQLite types:
 * - Integer types → TINYINT, SMALLINT, INT, BIGINT, INTEGER
 * - Floating-point types → FLOAT, DOUBLE
 * - Boolean → BOOLEAN
 * - Character/String → CHAR(1), TEXT
 * - ByteArray → BLOB
 *
 * @author Yuang Qiao
 */
internal object FullNameCache {

    val BYTE = Byte::class.qualifiedName!!
    val SHORT = Short::class.qualifiedName!!
    val INT = Int::class.qualifiedName!!
    val LONG = Long::class.qualifiedName!!

    val UBYTE = UByte::class.qualifiedName!!
    val USHORT = UShort::class.qualifiedName!!
    val UINT = UInt::class.qualifiedName!!
    val ULONG = ULong::class.qualifiedName!!

    val FLOAT = Float::class.qualifiedName!!
    val DOUBLE = Double::class.qualifiedName!!

    val BOOLEAN = Boolean::class.qualifiedName!!

    val CHAR = Char::class.qualifiedName!!
    val STRING = String::class.qualifiedName!!

    val BYTE_ARRAY = ByteArray::class.qualifiedName!!

    /**
     * Maps a Kotlin type's serial name to its corresponding SQLite column type declaration.
     *
     * This function converts kotlinx.serialization descriptor serial names (fully qualified type names)
     * into appropriate SQLite column type strings for use in DDL statements like CREATE TABLE and
     * ALTER TABLE ADD COLUMN.
     *
     * Type mapping rules:
     * - **Byte/UByte** → TINYINT
     * - **Short/UShort** → SMALLINT
     * - **Int/UInt** → INT
     * - **Long** → INTEGER (if primary key) or BIGINT (if not)
     * - **ULong** → BIGINT
     * - **Float** → FLOAT
     * - **Double** → DOUBLE
     * - **Boolean** → BOOLEAN
     * - **Char** → CHAR(1)
     * - **String** → TEXT
     * - **ByteArray** → BLOB
     *
     * Special handling for Long type:
     * - Returns " INTEGER" when the column is the table's primary key (required by SQLite for auto-increment)
     * - Returns " BIGINT" for non-primary key Long columns
     *
     * Example usage:
     * ```kotlin
     * val sqlType = getSerialNameBySerialName("kotlin.String", "username", userTable)
     * // Returns: " TEXT"
     *
     * val pkType = getSerialNameBySerialName("kotlin.Long", "id", userTable)
     * // Returns: " INTEGER" (if "id" is the primary key) or " BIGINT" (if not)
     * ```
     *
     * @param serialName The kotlinx.serialization serial name (fully qualified type name)
     * @param elementName The property/column name being processed
     * @param table The table definition, used to check primary key information
     * @return A string starting with a space followed by the SQLite type name (e.g., " TEXT", " INTEGER")
     * @throws IllegalStateException if the type is not supported by SQLlin
     */
    fun getSerialNameBySerialName(serialName: String, elementName: String, table: Table<*>): String = with(serialName) {
        when {
            startsWith(BYTE) || startsWith(UBYTE) -> " TINYINT"
            startsWith(SHORT) || startsWith(USHORT) -> " SMALLINT"
            startsWith(INT) || startsWith(UINT) -> " INT"
            startsWith(LONG) -> if (elementName == table.primaryKeyInfo?.primaryKeyName) " INTEGER" else " BIGINT"
            startsWith(ULONG) -> " BIGINT"
            startsWith(FLOAT) -> " FLOAT"
            startsWith(DOUBLE) -> " DOUBLE"
            startsWith(BOOLEAN) -> " BOOLEAN"
            startsWith(CHAR) -> " CHAR(1)"
            startsWith(STRING) -> " TEXT"
            startsWith(BYTE_ARRAY) -> " BLOB"
            else -> throw IllegalStateException("Hasn't support the type '$this' yet")
        }
    }
}