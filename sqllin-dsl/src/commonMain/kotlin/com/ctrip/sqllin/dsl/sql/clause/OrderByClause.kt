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

@file:Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")

package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.annotation.KeyWordDslMaker
import com.ctrip.sqllin.dsl.annotation.StatementDslMaker
import com.ctrip.sqllin.dsl.sql.statement.*

/**
 * ORDER BY clause for sorting SELECT query results.
 *
 * Generates SQL in the format: ` ORDER BY column1 ASC, column2 DESC, ...`
 *
 * Supports two modes:
 * - Explicit direction: `ORDER_BY(user.name to ASC, user.age to DESC)`
 * - Default ascending: `ORDER_BY(user.name, user.age)`
 *
 * @param T The entity type this clause operates on
 *
 * @author Yuang Qiao
 */
public sealed interface OrderByClause<T> : SelectClause<T>

internal class CompleteOrderByClause<T>(
    private val column2WayMap: Map<ClauseElement<*>, OrderByWay>,
    private val isQualified: Boolean = true,
) : OrderByClause<T> {

    override val clauseStr: String
        get() {
            require(column2WayMap.isNotEmpty()) { "Please provider at least one 'BaseClauseElement' -> 'OrderByWay' entry for 'ORDER BY' clause!!!" }
            return buildString {
                append(" ORDER BY ")
                val iterator = column2WayMap.entries.iterator()
                fun appendNext() {
                    val (element, way) = iterator.next()
                    appendColumn(element, isQualified)
                    append(' ')
                    append(way.str)
                }
                appendNext()
                while (iterator.hasNext()) {
                    append(',')
                    appendNext()
                }
            }
        }
}

/**
 * Appends [element] as a term of ORDER BY: qualified by its table's name, so that a join can tell it apart from a column
 * of the same name of another table, unless [isQualified] is false, as for a compound SELECT, whose ORDER BY can only
 * name the columns of its results.
 */
private fun StringBuilder.appendColumn(element: ClauseElement<*>, isQualified: Boolean) {
    if (isQualified)
        element.appendSQL(this)
    else
        append(element.valueName)
}

public enum class OrderByWay(internal val str: String) {
    @KeyWordDslMaker
    ASC("ASC"),

    @KeyWordDslMaker
    DESC("DESC")
}

@StatementDslMaker
public fun <T> ORDER_BY(vararg column2Ways: Pair<ClauseElement<*>, OrderByWay>): OrderByClause<T> =
    CompleteOrderByClause(mapOf(*column2Ways))

@StatementDslMaker
public inline infix fun <T> WhereSelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement<*>, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

@StatementDslMaker
public infix fun <T> WhereSelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement<*>, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(CompleteOrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public inline infix fun <T> HavingSelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement<*>, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

@StatementDslMaker
public infix fun <T> HavingSelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement<*>, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(CompleteOrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public inline infix fun <T> GroupBySelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement<*>, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

@StatementDslMaker
public infix fun <T> GroupBySelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement<*>, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(CompleteOrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public inline infix fun <T> JoinSelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement<*>, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

@StatementDslMaker
public infix fun <T> JoinSelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement<*>, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(CompleteOrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun <T> CompoundSelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement<*>, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun <T> CompoundSelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement<*>, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(CompleteOrderByClause(column2WayMap, isQualified = false)).also {
        container changeLastStatement it
    }

@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun <T> CompoundSelectStatement<T>.ORDER_BY(column: ClauseElement<*>): OrderBySelectStatement<T> =
    ORDER_BY(listOf(column))

@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun <T> CompoundSelectStatement<T>.ORDER_BY(columns: Iterable<ClauseElement<*>>): OrderBySelectStatement<T> =
    appendToOrderBy(SimpleOrderByClause(columns, isQualified = false)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public infix fun <T> ResultColumnSelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement<*>, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

@StatementDslMaker
public infix fun <T> ResultColumnSelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement<*>, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(CompleteOrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

internal class SimpleOrderByClause<T>(
    private val columns: Iterable<ClauseElement<*>>,
    private val isQualified: Boolean = true,
) : OrderByClause<T> {

    override val clauseStr: String
        get() {
            val iterator = columns.iterator()
            require(iterator.hasNext()) { "Please provider at least one 'BaseClauseElement' for 'ORDER BY' clause!!!" }
            return buildString {
                append(" ORDER BY ")
                appendColumn(iterator.next(), isQualified)
                while (iterator.hasNext()) {
                    append(',')
                    appendColumn(iterator.next(), isQualified)
                }
            }
        }
}

@StatementDslMaker
public fun <T> ORDER_BY(vararg elements: ClauseElement<*>): OrderByClause<T> =
    SimpleOrderByClause(elements.toList())

@StatementDslMaker
public inline infix fun <T> WhereSelectStatement<T>.ORDER_BY(column: ClauseElement<*>): OrderBySelectStatement<T> =
    ORDER_BY(listOf(column))

@StatementDslMaker
public infix fun <T> WhereSelectStatement<T>.ORDER_BY(columns: Iterable<ClauseElement<*>>): OrderBySelectStatement<T> =
    appendToOrderBy(SimpleOrderByClause(columns)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public inline infix fun <T> HavingSelectStatement<T>.ORDER_BY(column: ClauseElement<*>): OrderBySelectStatement<T> =
    ORDER_BY(listOf(column))

@StatementDslMaker
public infix fun <T> HavingSelectStatement<T>.ORDER_BY(columns: Iterable<ClauseElement<*>>): OrderBySelectStatement<T> =
    appendToOrderBy(SimpleOrderByClause(columns)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public inline infix fun <T> GroupBySelectStatement<T>.ORDER_BY(column: ClauseElement<*>): OrderBySelectStatement<T> =
    ORDER_BY(listOf(column))

@StatementDslMaker
public infix fun <T> GroupBySelectStatement<T>.ORDER_BY(columns: Iterable<ClauseElement<*>>): OrderBySelectStatement<T> =
    appendToOrderBy(SimpleOrderByClause(columns)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public inline infix fun <T> JoinSelectStatement<T>.ORDER_BY(column: ClauseElement<*>): OrderBySelectStatement<T> =
    ORDER_BY(listOf(column))

@StatementDslMaker
public infix fun <T> JoinSelectStatement<T>.ORDER_BY(columns: Iterable<ClauseElement<*>>): OrderBySelectStatement<T> =
    appendToOrderBy(SimpleOrderByClause(columns)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public infix fun <T> ResultColumnSelectStatement<T>.ORDER_BY(column: ClauseElement<*>): OrderBySelectStatement<T> =
    ORDER_BY(listOf(column))

@StatementDslMaker
public infix fun <T> ResultColumnSelectStatement<T>.ORDER_BY(columns: Iterable<ClauseElement<*>>): OrderBySelectStatement<T> =
    appendToOrderBy(SimpleOrderByClause(columns)).also {
        container changeLastStatement it
    }