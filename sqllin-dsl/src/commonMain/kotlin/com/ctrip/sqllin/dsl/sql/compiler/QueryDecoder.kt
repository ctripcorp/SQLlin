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

package com.ctrip.sqllin.dsl.sql.compiler

import com.ctrip.sqllin.driver.CommonCursor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Decoder for converting SQLite query results to Kotlin objects using kotlinx.serialization.
 *
 * This decoder reads data from a [CommonCursor] (representing a SQLite result set) and
 * deserializes it into strongly-typed entity objects. It maps cursor columns to object
 * properties by matching column names with serialization descriptor element names.
 *
 * The decoder handles:
 * - Type conversions from SQLite types to Kotlin types
 * - Null value handling for nullable properties
 * - Boolean mapping (SQLite integers to Kotlin booleans)
 * - Enum deserialization (ordinal values to enum instances)
 *
 * @author Yuang Qiao
 */
@OptIn(ExperimentalSerializationApi::class)
internal class QueryDecoder(
    private val cursor: CommonCursor
) : AbstractDecoder() {

    private var elementIndex = 0
    private var elementName = ""
    private var elementNullable = false

    override val serializersModule: SerializersModule = EmptySerializersModule()

    /**
     * Determines the next property to decode from the descriptor.
     *
     * Skips properties that don't have corresponding columns in the cursor.
     */
    override tailrec fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        if (elementIndex == descriptor.elementsCount)
            CompositeDecoder.DECODE_DONE
        else {
            elementName = descriptor.getElementName(elementIndex)
            elementNullable = descriptor.getElementDescriptor(elementIndex).isNullable
            val resultIndex = elementIndex++
            if (cursorColumnIndex >= 0)
                resultIndex
            else
                decodeElementIndex(descriptor)
        }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = QueryDecoder(cursor)

    /**
     * Resolves the cursor column index for the current element name.
     */
    private inline val cursorColumnIndex
        get() = cursor.getColumnIndex(elementName)

    /**
     * Helper to safely deserialize a value from the cursor with column validation.
     */
    private inline fun <T> deserialize(block: (Int) -> T): T = cursorColumnIndex.let {
        if (it >= 0) block(it) else throw SerializationException("The Cursor doesn't have this column")
    }

    /**
     * Decodes SQLite integer (1/0) to Boolean (true/false).
     */
    override fun decodeBoolean(): Boolean = deserialize { cursor.getInt(it) > 0 }
    override fun decodeByte(): Byte = deserialize { cursor.getInt(it).toByte() }
    override fun decodeShort(): Short = deserialize { cursor.getInt(it).toShort() }
    override fun decodeInt(): Int = deserialize { cursor.getInt(it) }
    override fun decodeLong(): Long = deserialize { cursor.getLong(it) }
    /**
     * Decodes first character of string, or null character if string is null.
     */
    override fun decodeChar(): Char = deserialize { cursor.getString(it)?.first() ?: '\u0000' }
    override fun decodeString(): String = deserialize { cursor.getString(it) ?: "" }
    override fun decodeFloat(): Float = deserialize { cursor.getFloat(it) }
    override fun decodeDouble(): Double = deserialize { cursor.getDouble(it) }
    /**
     * Decodes enum by its ordinal value stored as an integer.
     */
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = deserialize { cursor.getInt(it) }

    /**
     * Determines if the current column contains a non-null value.
     */
    override fun decodeNotNullMark(): Boolean = !cursor.isNull(cursorColumnIndex) || !elementNullable
}