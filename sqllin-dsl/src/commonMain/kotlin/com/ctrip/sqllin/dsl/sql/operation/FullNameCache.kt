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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind

/**
 * Cached qualified names for Kotlin types used in SQLite type mapping.
 *
 * Provides pre-computed fully qualified names for performance during CREATE TABLE generation.
 * These names are matched against kotlinx.serialization descriptor serial names to determine
 * the appropriate SQLite column type.
 *
 * Used by [columnTypeOf] to map Kotlin types to SQLite types:
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
     * Returns the SQLite type of the column of a property, which [descriptor], the descriptor of its serializer,
     * describes, without NULL: the type of SQLlin's default mapping of a Kotlin type, as `INT` for an `Int` or an enum,
     * stored as its ordinal; or, for a serializer that converts the values of another type, the type of the value it
     * encodes, as `TEXT` for the built-in serializer of `Uuid`, or the type of the value of a value class.
     *
     * Type mapping rules:
     * - **Byte/UByte** → TINYINT
     * - **Short/UShort** → SMALLINT
     * - **Int/UInt** → INT
     * - **Enum** → INT (stored as ordinal values)
     * - **Long** → INTEGER (if primary key) or BIGINT (if not)
     * - **ULong** → BIGINT
     * - **Float** → FLOAT
     * - **Double** → DOUBLE
     * - **Boolean** → BOOLEAN
     * - **Char** → CHAR(1)
     * - **String** → TEXT
     * - **ByteArray** → BLOB
     *
     * A serializer of another type stores the values of the kind of its descriptor, as a `PrimitiveKind.LONG`, which is
     * BIGINT, as it isn't a `Long` property that can be the rowid. A value class stores the value it holds.
     *
     * @param descriptor The descriptor of the serializer of the property, nullable or not
     * @param isPrimaryKey Whether the property is the table's primary key, which makes a `Long` the rowid
     * @return The SQLite type, as `TEXT`, or null if the serializer encodes a structure, which no column holds
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun columnTypeOf(descriptor: SerialDescriptor, isPrimaryKey: Boolean): String? = when (descriptor.serialName.removeSuffix("?")) {
        BYTE, UBYTE -> "TINYINT"
        SHORT, USHORT -> "SMALLINT"
        INT, UINT -> "INT"
        LONG -> if (isPrimaryKey) "INTEGER" else "BIGINT"
        ULONG -> "BIGINT"
        FLOAT -> "FLOAT"
        DOUBLE -> "DOUBLE"
        BOOLEAN -> "BOOLEAN"
        CHAR -> "CHAR(1)"
        STRING -> "TEXT"
        BYTE_ARRAY -> "BLOB"
        else -> when (descriptor.kind) {
            SerialKind.ENUM -> "INT"
            PrimitiveKind.BYTE -> "TINYINT"
            PrimitiveKind.SHORT -> "SMALLINT"
            PrimitiveKind.INT -> "INT"
            PrimitiveKind.LONG -> "BIGINT"
            PrimitiveKind.FLOAT -> "FLOAT"
            PrimitiveKind.DOUBLE -> "DOUBLE"
            PrimitiveKind.BOOLEAN -> "BOOLEAN"
            PrimitiveKind.CHAR -> "CHAR(1)"
            PrimitiveKind.STRING -> "TEXT"
            else -> if (descriptor.isInline) columnTypeOf(descriptor.getElementDescriptor(0), isPrimaryKey = false) else null
        }
    }
}