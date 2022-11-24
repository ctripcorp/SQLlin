@file:OptIn(ExperimentalSerializationApi::class)

package com.ctrip.sqllin.dsl.sql.compiler

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor

/**
 * Some function that used for encode entities to SQL.
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

internal infix fun StringBuilder.appendDBColumnName(descriptor: SerialDescriptor) {
    // for (i in 0..<descriptor.elementsCount) {
    for (i in 0 until descriptor.elementsCount) {
        if (i != 0)
            append(',')
        append(descriptor.getElementName(i))
    }
}