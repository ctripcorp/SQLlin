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

@file:OptIn(ExperimentalSerializationApi::class)

package com.ctrip.sqllin.dsl.sql.compiler

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor

/**
 * Some function that used for encode entities to SQL
 * @author yaqiao
 */

internal fun <T> encodeEntities2InsertValues(serializer: SerializationStrategy<T>, values: Iterable<T>): String = buildString {
    append('(')
    appendDBColumnName(serializer.descriptor)
    append(')')
    append(" values ")
    val iterator = values.iterator()
    do {
        val value = iterator.next()
        val encoder = InsertValuesEncoder()
        encoder.encodeSerializableValue(serializer, value)
        append(encoder.valuesSQL)
        val hasNext = iterator.hasNext()
        if (hasNext) append(',')
    } while (hasNext)
}

internal fun <T> encodeEntities2UpdateValues(serializer: SerializationStrategy<T>, values: Iterable<T>): List<String> =
    values.asSequence().map {
        UpdateValuesEncoder().apply {
            encodeSerializableValue(serializer, it)
        }.valuesSQL
    }.toList()

@OptIn(ExperimentalStdlibApi::class)
internal infix fun StringBuilder.appendDBColumnName(descriptor: SerialDescriptor) {
    for (i in 0 ..< descriptor.elementsCount) {
        if (i != 0)
            append(',')
        append(descriptor.getElementName(i))
    }
}