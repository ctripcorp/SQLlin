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

package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.sql.Table

/**
 * Base class for elements used in SQL clauses.
 *
 * Represents a reference to a database column or function that can be used in clause expressions.
 * Clause elements maintain their source table and whether they represent a function call.
 *
 * Subclasses provide type-specific wrappers:
 * - [ClauseBoolean]: Boolean column references with comparison operators
 * - [ClauseNumber]: Numeric column/function references with arithmetic and comparison operators
 * - [ClauseString]: String column/function references with text comparison operators
 * - [ClauseBlob]: BLOB (ByteArray) column references with comparison operators
 * - [ClauseEnum]: Enum column references with ordinal-based comparison operators
 *
 * Used in:
 * - WHERE/HAVING conditions
 * - ORDER BY expressions
 * - GROUP BY columns
 * - SET assignments
 * - JOIN USING clauses
 * - Result columns, as in `count(X) AS AuthorStats::books`
 *
 * An element knows what it reads into, so a [ResultColumn] can only put it into a property that can hold it: the type
 * of its values, and whether it can be NULL.
 *
 * @param V The type of the element's values, not counting NULL: `Int` for an `Int` or `Int?` column, `Long` for
 * `count(*)`, `Double` for `avg(...)`
 * @property valueName The column name or function expression
 * @property table The table this element belongs to
 * @property isFunction Whether this represents a function call (e.g., COUNT, SUM)
 * @property isNullable Whether the element can be NULL: for a column, whether the column is nullable, and for an
 * aggregate function, whether it can be NULL for a group of rows, as `sum` of a nullable column is when all its
 * values in the group are NULL
 * @property isAggregate Whether the element is or contains an aggregate function, which makes a query that selects it
 * an aggregate query
 * @property isNullOnNoRows Whether the element is NULL when an aggregate query without GROUP BY matches no rows. Such a
 * query still returns one row, in which a column, and every aggregate function except `count`, is NULL.
 *
 * @author Yuang Qiao
 */
public sealed class ClauseElement<V : Any>(
    internal val valueName: String,
    internal val table: Table<*>,
    internal val isFunction: Boolean,
    internal val isNullable: Boolean,
    internal val isAggregate: Boolean,
    internal val isNullOnNoRows: Boolean,
) {

    /**
     * Creates the element of an aggregate function of this element that has values of the same type, as `max` and
     * `min` do.
     */
    internal abstract fun toAggregate(valueName: String, table: Table<*>): ClauseElement<V>

    /**
     * Appends this element as SQL to [builder]: a column qualified by its table's name, so that it can be told apart
     * from a column of another table, and a function as it is, as a function call can't be qualified.
     */
    internal fun appendSQL(builder: StringBuilder) {
        if (!isFunction) {
            builder.append(table.tableName)
            builder.append('.')
        }
        builder.append(valueName)
    }
}
