package com.ctrip.sqllin.dsl.sql.compiler

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Abstract Encode the object to UPDATE statement.
 * @author yaqiao
 */

@OptIn(ExperimentalSerializationApi::class)
internal abstract class AbstractValuesEncoder : AbstractEncoder() {

    final override val serializersModule: SerializersModule = EmptySerializersModule()

    protected abstract val sqlStrBuilder: StringBuilder

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

    override fun encodeBoolean(value: Boolean) {
        sqlStrBuilder.append(if (value) 1 else 0).appendTail()
    }

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

    override fun encodeChar(value: Char) {
        sqlStrBuilder
            .append('\'')
            .append(value)
            .append('\'')
            .appendTail()
    }

    override fun encodeString(value: String) {
        sqlStrBuilder
            .append('\'')
            .append(value)
            .append('\'')
            .appendTail()
    }

    override fun encodeFloat(value: Float) {
        sqlStrBuilder.append(value).appendTail()
    }

    override fun encodeDouble(value: Double) {
        sqlStrBuilder.append(value).appendTail()
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        sqlStrBuilder.append(index).appendTail()
    }
}