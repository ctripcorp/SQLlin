package com.ctrip.sqllin.dsl.sql.compiler

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor

/**
 * Encode the object to UPDATE SQL statement.
 * @author yaqiao
 */

@OptIn(ExperimentalSerializationApi::class)
internal class UpdateValuesEncoder : AbstractValuesEncoder() {

    override val sqlStrBuilder = StringBuilder()

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        super.encodeElement(descriptor, index)
        val elementName = descriptor.getElementName(index)
        sqlStrBuilder.append(elementName).append(" = ")
        return true
    }

    override fun StringBuilder.appendTail(): StringBuilder {
        if (elementsIndex < elementsCount - 1)
            append(',')
        return this
    }
}