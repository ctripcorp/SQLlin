/*
 * Copyright (C) 2026 Ctrip.com.
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
import com.ctrip.sqllin.dsl.sql.compiler.sqlLiteral
import com.ctrip.sqllin.dsl.sql.compiler.storedValue
import kotlinx.serialization.KSerializer

/**
 * What is known about an expression, which an expression of it derives its own from: whether it can be NULL, in a row
 * and when an aggregate query without GROUP BY matches no rows, whether it is an aggregate, the tables of the columns it
 * reads and of its subqueries, and whether it always gives the same result for the same row.
 *
 * An expression carries no parameters: the values in it are written into its SQL as literals, so that it can be part
 * of any statement, a view, a trigger and an index included, which SQLite doesn't let take parameters.
 *
 * @author Yuang Qiao
 */
internal class Traits(
    val isNullable: Boolean,
    val isAggregate: Boolean,
    val isNullOnNoRows: Boolean,
    val columnTables: Set<String>,
    val tables: Set<String>,
    val isDeterministic: Boolean,
)

internal val ClauseElement<*>.traits: Traits
    get() = Traits(isNullable, isAggregate, isNullOnNoRows, columnTables, tables, isDeterministic)

/**
 * The traits of an expression of [operands] that is NULL when any of them is, as an arithmetic operator, or a function
 * such as `abs` or `upper`, which also makes it NULL where any of them is when no rows match. [isNullable] is given
 * where the expression can be NULL even when its operands aren't, as a division by zero is, and [isNullOnNoRows] where
 * it differs, as `hex` is never NULL. [isDeterministic] is false where the function isn't, as `random()` isn't.
 */
internal fun strictTraits(
    operands: List<ClauseElement<*>>,
    isNullable: Boolean = operands.any { it.isNullable },
    isNullOnNoRows: Boolean = operands.any { it.isNullOnNoRows },
    isDeterministic: Boolean = true,
): Traits = Traits(
    isNullable = isNullable,
    isAggregate = operands.any { it.isAggregate },
    isNullOnNoRows = isNullOnNoRows,
    columnTables = operands.flatMapTo(LinkedHashSet()) { it.columnTables },
    tables = operands.flatMapTo(LinkedHashSet()) { it.tables },
    isDeterministic = isDeterministic && operands.all { it.isDeterministic },
)

/**
 * The relation of the elements that belong to none, such as literals: as it isn't a column, an element writes no
 * relation's name, so this one is never written.
 */
internal object ExpressionRelation : Relation<Nothing>("") {
    override fun kSerializer(): KSerializer<Nothing> = error("An expression isn't a relation that can be read.")
}

/**
 * The row a trigger reads, `NEW` or `OLD`, of [table], whose columns `new(column)` and `old(column)` give.
 */
internal class RowRelation(name: String, val table: Relation<*>) : Relation<Nothing>(name) {
    override fun kSerializer(): KSerializer<Nothing> = error("The row of a trigger isn't a relation that can be read.")
}

/**
 * Writes [value] as the SQL literal of an expression, the number SQLite stores for an unsigned one included. A negative
 * number is put in parentheses, as `x - -1` written without spaces, `x--1`, would start a comment.
 */
internal fun expressionLiteral(value: Any): String {
    val literal = sqlLiteral(storedValue(value))
    return if (literal.startsWith('-')) "($literal)" else literal
}

/** The element of a function with `String` values, [sql], that has [traits]. */
internal fun Relation<*>.stringExpression(sql: String, traits: Traits): ClauseString<String> =
    ClauseString(sql, this, isFunction = true, isNullable = traits.isNullable, isAggregate = traits.isAggregate, isNullOnNoRows = traits.isNullOnNoRows, columnTables = traits.columnTables, tables = traits.tables, isDeterministic = traits.isDeterministic)

/** The element of a function with values of type [V], [sql], that has [traits]. */
internal fun <V : Any> Relation<*>.numberExpression(sql: String, traits: Traits): ClauseNumber<V> =
    ClauseNumber(sql, this, isFunction = true, isNullable = traits.isNullable, isAggregate = traits.isAggregate, isNullOnNoRows = traits.isNullOnNoRows, columnTables = traits.columnTables, tables = traits.tables, isDeterministic = traits.isDeterministic)

/** The element of a function with BLOB values, [sql], that has [traits]. */
internal fun Relation<*>.blobExpression(sql: String, traits: Traits): ClauseBlob =
    ClauseBlob(sql, this, isFunction = true, isNullable = traits.isNullable, isAggregate = traits.isAggregate, isNullOnNoRows = traits.isNullOnNoRows, columnTables = traits.columnTables, tables = traits.tables, isDeterministic = traits.isDeterministic)

/** The SQL of [elements], separated by commas. */
internal fun sqlOf(elements: List<ClauseElement<*>>): String = elements.joinToString(",") { it.sql }

