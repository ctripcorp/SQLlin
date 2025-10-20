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
 * Utility functions for encoding entity objects to SQL statements.
 *
 * @author Yuang Qiao
 */

/**
 * Encodes a collection of entities into an INSERT statement's VALUES clause.
 *
 * Generates SQL in the format:
 * ```
 * (column1, column2, ...) VALUES (?, ?, ...), (?, ?, ...), ...
 * ```
 *
 * Handles primary key logic:
 * - For auto-increment `Long?` primary keys, omits the ID column unless [isInsertWithId] is true
 * - For user-provided primary keys or composite keys, includes all columns
 *
 * @param table The table definition containing serialization and primary key metadata
 * @param builder StringBuilder to append the SQL to
 * @param values The entities to insert
 * @param parameters Mutable list to collect parameterized query values
 * @param isInsertWithId Whether to include the primary key column for rowid-backed keys
 */
internal fun <T> encodeEntities2InsertValues(
    table: Table<T>,
    builder: StringBuilder,
    values: Iterable<T>,
    parameters: MutableList<Any?>,
    isInsertWithId: Boolean,
) = with(builder) {
    val isInsertId = table.primaryKeyInfo?.run {
        !isRowId || isInsertWithId
    } ?: true
    val serializer = table.kSerializer()
    append('(')
    val primaryKeyName = table.primaryKeyInfo?.primaryKeyName
    appendDBColumnName(serializer.descriptor, primaryKeyName, isInsertId)
    append(')')
    append(" values ")
    val iterator = values.iterator()
    fun appendNext() {
        val value = iterator.next()
        val encoder = InsertValuesEncoder(parameters, primaryKeyName)
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
}

/**
 * Appends database column names to the StringBuilder, optionally excluding a primary key.
 *
 * @param descriptor The serialization descriptor containing column/element names
 * @param primaryKeyName The name of the primary key column to potentially exclude
 * @param isInsertId Whether to include the primary key column
 * @return The index of the excluded primary key column, or -1 if all columns were included
 */
internal fun StringBuilder.appendDBColumnName(
    descriptor: SerialDescriptor,
    primaryKeyName: String?,
    isInsertId: Boolean,
) {
    if (isInsertId) {
        appendDBColumnName(descriptor)
    } else {
        if (descriptor.elementsCount > 0) {
            val elementName = descriptor.getElementName(0)
            if (elementName != primaryKeyName)
                append(elementName)
        }
        for (i in 1 ..< descriptor.elementsCount) {
            append(',')
            val elementName = descriptor.getElementName(i)
            if (elementName != primaryKeyName)
                append(elementName)
        }
    }
}

/**
 * Appends all database column names from the descriptor as a comma-separated list.
 */
internal infix fun StringBuilder.appendDBColumnName(descriptor: SerialDescriptor) {
    if (descriptor.elementsCount > 0)
        append(descriptor.getElementName(0))
    for (i in 1 ..< descriptor.elementsCount) {
        append(',')
        append(descriptor.getElementName(i))
    }
}