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
 * The column list and the VALUES tuples of an INSERT of entities: `(column1,column2,...)`, and `(?,?,...)` for each
 * entity, whose values [encodeInsertRows] adds to its parameters, as many as [InsertRows.parameterCounts] tells for each
 * row, in the order of the rows.
 *
 * @author Yuang Qiao
 */
internal class InsertRows(
    val columns: String,
    val rows: List<String>,
    val parameterCounts: List<Int>,
)

/**
 * Encodes [values] into the column list and the VALUES tuples of an INSERT, adding their values to [parameters]:
 * ```
 * (column1, column2, ...) and (?, ?, ...), (?, ?, ...), ...
 * ```
 *
 * Handles primary key logic:
 * - For a `Long?` primary key, whose value the database assigns, omits the ID column unless
 *   [isInsertWithId] is true
 * - For a primary key the caller supplies (a non-null `Long`, any other type, or a composite key),
 *   includes all columns
 *
 * @param table The table definition containing serialization and primary key metadata
 * @param values The entities to insert
 * @param parameters Mutable list to collect parameterized query values
 * @param isInsertWithId Whether to include the primary key column even when the database would assign it
 */
internal fun <T> encodeInsertRows(
    table: Table<T>,
    values: Iterable<T>,
    parameters: MutableList<Any?>,
    isInsertWithId: Boolean,
): InsertRows {
    val isInsertId = table.primaryKeyInfo?.run {
        !isGeneratedByDatabase || isInsertWithId
    } ?: true
    val serializer = table.kSerializer()
    val primaryKeyName = table.primaryKeyInfo?.primaryKeyName
    val columns = buildString {
        append('(')
        appendDBColumnName(serializer.descriptor, primaryKeyName, isInsertId)
        append(')')
    }
    val rows = ArrayList<String>()
    val parameterCounts = ArrayList<Int>()
    for (value in values) {
        val before = parameters.size
        val encoder = InsertValuesEncoder(parameters, primaryKeyName, isInsertId)
        encoder.encodeSerializableValue(serializer, value)
        rows.add(encoder.valuesSQL)
        parameterCounts.add(parameters.size - before)
    }
    return InsertRows(columns, rows, parameterCounts)
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
        val columnNames = (0 ..< descriptor.elementsCount)
            .map(descriptor::getElementName)
            .filter { it != primaryKeyName }
        append(columnNames.joinToString(","))
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