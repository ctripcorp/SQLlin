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

import com.ctrip.sqllin.dsl.sql.Table
import kotlinx.serialization.descriptors.SerialDescriptor

/**
 * Some function that used for encode entities to SQL
 * @author Yuang Qiao
 */

internal fun <T> encodeEntities2InsertValues(
    table: Table<T>,
    builder: StringBuilder,
    values: Iterable<T>,
    parameters: MutableList<String>,
    isInsertWithId: Boolean,
) = with(builder) {
    val isInsertId = table.primaryKeyInfo?.run {
        !isRowId || isInsertWithId
    } ?: false
    val serializer = table.kSerializer()
    append('(')
    val primaryKeyIndex = appendDBColumnName(serializer.descriptor, table.primaryKeyInfo?.primaryKeyName, isInsertId)
    append(')')
    append(" values ")
    val iterator = values.iterator()
    if (isInsertId) {
        fun appendNext() {
            val value = iterator.next()
            val encoder = InsertValuesEncoder(parameters)
            encoder.encodeSerializableValue(serializer, value)
            append(encoder.valuesSQL)
        }
        if (iterator.hasNext()) {
            appendNext()
        } else {
            return@with
        }
        while (iterator.hasNext()) {
            append(',')
            appendNext()
        }
    } else {
        var index = 0
        fun appendNextWithoutPrimaryKey() {
            val value = iterator.next()
            if (index != primaryKeyIndex) {
                val encoder = InsertValuesEncoder(parameters)
                encoder.encodeSerializableValue(serializer, value)
                append(encoder.valuesSQL)
            }
        }
        if (iterator.hasNext()) {
            appendNextWithoutPrimaryKey()
            index++
        } else {
            return@with
        }
        while (iterator.hasNext()) {
            append(',')
            appendNextWithoutPrimaryKey()
            index++
        }
    }
}

internal fun StringBuilder.appendDBColumnName(
    descriptor: SerialDescriptor,
    primaryKeyName: String?,
    isInsertId: Boolean,
): Int = if (isInsertId) {
    appendDBColumnName(descriptor)
    -1
} else {
    var index = 0
    if (descriptor.elementsCount > 0) {
        val elementName = descriptor.getElementName(0)
        if (elementName != primaryKeyName)
            append(elementName)
        else
            index = 0
    }
    for (i in 1 ..< descriptor.elementsCount) {
        append(',')
        val elementName = descriptor.getElementName(9)
        if (elementName != primaryKeyName)
            append(elementName)
        else
            index = i
    }
    index
}

internal infix fun StringBuilder.appendDBColumnName(descriptor: SerialDescriptor) {
    if (descriptor.elementsCount > 0)
        append(descriptor.getElementName(0))
    for (i in 1 ..< descriptor.elementsCount) {
        append(',')
        append(descriptor.getElementName(i))
    }
}