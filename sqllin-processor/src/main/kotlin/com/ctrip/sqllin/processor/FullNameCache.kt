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

package com.ctrip.sqllin.processor

/**
 * Cached qualified names for Kotlin types used in compile-time SQLite type mapping.
 *
 * This object provides:
 * 1. Pre-computed fully qualified names for all supported Kotlin types to avoid
 *    repeated reflection calls during annotation processing
 * 2. Centralized Kotlin-to-SQLite type mapping logic
 *
 * Used by [ClauseProcessor] during compile-time code generation to:
 * - Map Kotlin property types to appropriate SQLite column types
 * - Generate CREATE TABLE statements with correct type declarations
 * - Ensure type consistency across generated table objects
 *
 * ### Supported Type Mappings
 * - Integer types → TINYINT, SMALLINT, INT, BIGINT, INTEGER
 * - Unsigned integer types → TINYINT, SMALLINT, INT, BIGINT
 * - Floating-point types → FLOAT, DOUBLE
 * - Boolean → BOOLEAN
 * - Character/String → CHAR(1), TEXT
 * - ByteArray → BLOB
 *
 * @author Yuang Qiao
 */
internal object FullNameCache {

    /** Fully qualified name for [Byte] (`kotlin.Byte`) */
    val BYTE = Byte::class.qualifiedName!!

    /** Fully qualified name for [Short] (`kotlin.Short`) */
    val SHORT = Short::class.qualifiedName!!

    /** Fully qualified name for [Int] (`kotlin.Int`) */
    val INT = Int::class.qualifiedName!!

    /** Fully qualified name for [Long] (`kotlin.Long`) */
    val LONG = Long::class.qualifiedName!!

    /** Fully qualified name for [UByte] (`kotlin.UByte`) */
    val UBYTE = UByte::class.qualifiedName!!

    /** Fully qualified name for [UShort] (`kotlin.UShort`) */
    val USHORT = UShort::class.qualifiedName!!

    /** Fully qualified name for [UInt] (`kotlin.UInt`) */
    val UINT = UInt::class.qualifiedName!!

    /** Fully qualified name for [ULong] (`kotlin.ULong`) */
    val ULONG = ULong::class.qualifiedName!!

    /** Fully qualified name for [Float] (`kotlin.Float`) */
    val FLOAT = Float::class.qualifiedName!!

    /** Fully qualified name for [Double] (`kotlin.Double`) */
    val DOUBLE = Double::class.qualifiedName!!

    /** Fully qualified name for [Boolean] (`kotlin.Boolean`) */
    val BOOLEAN = Boolean::class.qualifiedName!!

    /** Fully qualified name for [Char] (`kotlin.Char`) */
    val CHAR = Char::class.qualifiedName!!

    /** Fully qualified name for [String] (`kotlin.String`) */
    val STRING = String::class.qualifiedName!!

    /** Fully qualified name for [ByteArray] (`kotlin.ByteArray`) */
    val BYTE_ARRAY = ByteArray::class.qualifiedName!!

    /**
     * Maps a Kotlin fully qualified type name to its corresponding SQLite type declaration.
     *
     * This function is used during compile-time code generation to produce the appropriate
     * SQLite type string for CREATE TABLE statements. Each returned string includes a
     * leading space for proper SQL formatting.
     *
     * ### Special Handling for Long Primary Keys
     * When a [Long] property is marked as a primary key, it's mapped to `INTEGER` instead
     * of `BIGINT`. This enables SQLite's special rowid aliasing behavior, where an
     * `INTEGER PRIMARY KEY` column becomes an alias for the internal rowid, providing
     * automatic unique ID generation and optimal performance.
     *
     * ### Type Mappings
     * | Kotlin Type | SQLite Type (non-PK) | SQLite Type (PK) |
     * |------------|---------------------|------------------|
     * | Byte, UByte | TINYINT | TINYINT |
     * | Short, UShort | SMALLINT | SMALLINT |
     * | Int, UInt | INT | INT |
     * | Long | BIGINT | INTEGER |
     * | ULong | BIGINT | BIGINT |
     * | Float | FLOAT | FLOAT |
     * | Double | DOUBLE | DOUBLE |
     * | Boolean | BOOLEAN | BOOLEAN |
     * | Char | CHAR(1) | CHAR(1) |
     * | String | TEXT | TEXT |
     * | ByteArray | BLOB | BLOB |
     *
     * @param typeName The fully qualified Kotlin type name (e.g., "kotlin.Int", "kotlin.String")
     * @param isPrimaryKey Whether this column is the primary key of the table
     * @return A SQLite type declaration string with leading space (e.g., " INT", " TEXT"),
     *         or `null` if the type is not supported
     *
     * @see ClauseProcessor.getSQLiteType
     */
    fun getSQLTypeName(typeName: String?, isPrimaryKey: Boolean): String? = when (typeName) {
        BYTE, UBYTE -> " TINYINT"
        SHORT, USHORT -> " SMALLINT"
        INT, UINT -> " INT"
        LONG -> if (isPrimaryKey) " INTEGER" else " BIGINT"
        ULONG -> " BIGINT"
        FLOAT -> " FLOAT"
        DOUBLE -> " DOUBLE"
        BOOLEAN -> " BOOLEAN"
        CHAR -> " CHAR(1)"
        STRING -> " TEXT"
        BYTE_ARRAY -> " BLOB"
        else -> null
    }
}