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

import com.ctrip.sqllin.dsl.sql.Relation

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
 * @property columnTables The names of the tables whose columns the element reads: a column's own table, and the tables
 * of a function's arguments, which an outer join can fill with NULL
 * @property tables The tables and views that the subqueries in the element read, which an observed query watches
 * @property isDeterministic Whether the element always gives the same result for the same row, which an index needs:
 * `random()` doesn't
 * @property isLiteral Whether the element is a literal on its own, such as `5`, which ORDER BY and GROUP BY would read
 * as the number of a result column
 *
 * @author Yuang Qiao
 */
public sealed class ClauseElement<V : Any>(
    internal val valueName: String,
    internal val table: Relation<*>,
    internal val isFunction: Boolean,
    internal val isNullable: Boolean,
    internal val isAggregate: Boolean,
    internal val isNullOnNoRows: Boolean,
    columnTables: Set<String>? = null,
    internal val tables: Set<String> = emptySet(),
    internal val isDeterministic: Boolean = true,
    internal val isLiteral: Boolean = false,
) {

    internal val columnTables: Set<String> = columnTables ?: if (isFunction) emptySet() else setOf(table.tableName)

    /**
     * Creates the element of an aggregate function of this element that has values of the same type, as `max` and
     * `min` do.
     */
    internal abstract fun toAggregate(valueName: String, table: Relation<*>): ClauseElement<V>

    /**
     * Creates the element of an expression of this element that has values of the same type, as an operator or a
     * function does, with [valueName] as its SQL and [traits] as what is known about it.
     */
    internal abstract fun derive(valueName: String, traits: Traits): ClauseElement<V>

    /**
     * Creates the literal of [value], of the same type as the values of this element, as an operator or a function
     * makes of a value given to it.
     */
    internal abstract fun literalOf(value: V): ClauseElement<V>

    /**
     * Creates the condition [sql] of this element, compared with [other] if given, with its [parameters]: it reads the
     * tables that the subqueries of the elements read.
     */
    internal fun condition(sql: String, parameters: MutableList<Any?>?, other: ClauseElement<*>? = null): SelectCondition =
        SelectCondition(
            conditionSQL = sql,
            parameters = parameters,
            tables = if (other == null) tables else tables + other.tables,
            isDeterministic = isDeterministic && other?.isDeterministic != false,
        )

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

    /**
     * This element as SQL, as [appendSQL] writes it, which a function writes for its argument, so that a column is told
     * apart from a column of the same name of another table in a join.
     */
    internal val sql: String
        get() = buildString { appendSQL(this) }
}
