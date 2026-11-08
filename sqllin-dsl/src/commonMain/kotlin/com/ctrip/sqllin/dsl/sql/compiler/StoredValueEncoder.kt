/*
 * Copyright (C) 2026 Ctrip.com.
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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Returns [value] as [serializer] writes it to the database, the value a column of its type holds, as
 * [InsertValuesEncoder] writes the value of a property: a number, a string, a Boolean, a `ByteArray`, or null. An
 * unsigned number is the number it is, as [storedValue] gives it, and an enum entry its ordinal, as its default
 * serializer encodes it.
 *
 * A condition compares a column with the value a Kotlin value is stored as, so that `createdAt GT instant` compares
 * what the serializer of `createdAt` wrote with what it writes for `instant`.
 *
 * @throws IllegalArgumentException if [serializer] doesn't encode [value] as one value, as the plugin-generated
 * serializer of a class encodes each of its properties
 */
internal fun <V> storedValueOf(serializer: SerializationStrategy<V>, value: V): Any? {
    val encoder = StoredValueEncoder(serializer)
    encoder.encodeSerializableValue(serializer, value)
    require(encoder.isEncoded) {
        "The serializer '${serializer.descriptor.serialName}' encoded no value, while a column holds one."
    }
    return encoder.value
}

/**
 * The encoder of [storedValueOf], which keeps the one value a serializer encodes.
 *
 * @author Yuang Qiao
 */
@OptIn(ExperimentalSerializationApi::class)
private class StoredValueEncoder(private val serializer: SerializationStrategy<*>) : AbstractEncoder() {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    var value: Any? = null
        private set

    var isEncoded = false
        private set

    private fun keep(value: Any?) {
        require(!isEncoded) {
            "The serializer '${serializer.descriptor.serialName}' encoded more than one value, while a column holds one."
        }
        this.value = value
        isEncoded = true
    }

    override fun encodeValue(value: Any) = keep(value)

    override fun encodeNull() = keep(null)

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
        throw IllegalArgumentException(
            "The serializer '${serializer.descriptor.serialName}' encodes a structure, '${descriptor.serialName}', while a column holds one value. " +
                "Give the property a serializer that encodes it as a number, a string, a Boolean or a ByteArray, as JSON in a string, for example."
        )

    // As InsertValuesEncoder does: a ByteArray is a BLOB, rather than the list of bytes its serializer encodes
    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) =
        when (value) {
            is ByteArray -> keep(value)
            is UByte, is UShort, is UInt, is ULong -> keep(storedValue(value))
            else -> super.encodeSerializableValue(serializer, value)
        }
}
