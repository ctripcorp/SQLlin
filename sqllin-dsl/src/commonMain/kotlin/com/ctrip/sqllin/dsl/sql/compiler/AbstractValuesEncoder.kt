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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Abstract Encode the object to UPDATE statement
 * @author yaqiao
 */

@OptIn(ExperimentalSerializationApi::class)
internal abstract class AbstractValuesEncoder : AbstractEncoder() {

    final override val serializersModule: SerializersModule = EmptySerializersModule()

    protected abstract val sqlStrBuilder: StringBuilder
    abstract val parameters: MutableList<String>

    protected abstract fun StringBuilder.appendTail(): StringBuilder

    protected var elementsIndex = 0
    protected var elementsCount = 0

    val valuesSQL
        get() = sqlStrBuilder.toString()

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        if (index == 0)
            elementsCount = descriptor.elementsCount
        elementsIndex = index
        return true
    }

    override fun encodeBoolean(value: Boolean) = encodeByte(if (value) 1 else 0)

    override fun encodeByte(value: Byte) {
        sqlStrBuilder.append(value).appendTail()
    }

    override fun encodeShort(value: Short) {
        sqlStrBuilder.append(value).appendTail()
    }

    override fun encodeInt(value: Int) {
        sqlStrBuilder.append(value).appendTail()
    }

    override fun encodeLong(value: Long) {
        sqlStrBuilder.append(value).appendTail()
    }

    override fun encodeChar(value: Char) = encodeString(value.toString())

    override fun encodeString(value: String) {
        sqlStrBuilder.append('?').appendTail()
        parameters.add(value)
    }

    override fun encodeFloat(value: Float) {
        sqlStrBuilder.append(value).appendTail()
    }

    override fun encodeDouble(value: Double) {
        sqlStrBuilder.append(value).appendTail()
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = encodeInt(index)
}