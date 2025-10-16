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

internal class CompleteOrderByClause<T>(private val column2WayMap: Map<ClauseElement, OrderByWay>) : OrderByClause<T> {

    override val clauseStr: String
        get() {
            require(column2WayMap.isNotEmpty()) { "Please provider at least one 'BaseClauseElement' -> 'OrderByWay' entry for 'ORDER BY' clause!!!" }
            return buildString {
                append(" ORDER BY ")
                val iterator = column2WayMap.entries.iterator()
                do {
                    val (element, way) = iterator.next()
                    append(element.valueName)
                    append(' ')
                    append(way.str)
                    val hasNext = iterator.hasNext()
                    if (hasNext)
                        append(',')
                } while (hasNext)
            }
        }
}

public enum class OrderByWay(internal val str: String) {
    @KeyWordDslMaker
    ASC("ASC"),

    @KeyWordDslMaker
    DESC("DESC")
}

@StatementDslMaker
public fun <T> ORDER_BY(vararg column2Ways: Pair<ClauseElement, OrderByWay>): OrderByClause<T> =
    CompleteOrderByClause(mapOf(*column2Ways))

@StatementDslMaker
public inline infix fun <T> WhereSelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

@StatementDslMaker
public infix fun <T> WhereSelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(CompleteOrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public inline infix fun <T> HavingSelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

@StatementDslMaker
public infix fun <T> HavingSelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(CompleteOrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public inline infix fun <T> GroupBySelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

@StatementDslMaker
public infix fun <T> GroupBySelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(CompleteOrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public inline infix fun <T> JoinSelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

@StatementDslMaker
public infix fun <T> JoinSelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(CompleteOrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

internal class SimpleOrderByClause<T>(private val columns: Iterable<ClauseElement>) : OrderByClause<T> {

    override val clauseStr: String
        get() {
            val iterator = columns.iterator()
            require(iterator.hasNext()) { "Please provider at least one 'BaseClauseElement' for 'ORDER BY' clause!!!" }
            return buildString {
                append(" ORDER BY ")
                append(iterator.next().valueName)
                while (iterator.hasNext()) {
                    append(',')
                    append(iterator.next().valueName)
                }
            }
        }
}

@StatementDslMaker
public fun <T> ORDER_BY(vararg elements: ClauseElement): OrderByClause<T> =
    SimpleOrderByClause(elements.toList())

@StatementDslMaker
public inline infix fun <T> WhereSelectStatement<T>.ORDER_BY(column: ClauseElement): OrderBySelectStatement<T> =
    ORDER_BY(listOf(column))

@StatementDslMaker
public infix fun <T> WhereSelectStatement<T>.ORDER_BY(columns: Iterable<ClauseElement>): OrderBySelectStatement<T> =
    appendToOrderBy(SimpleOrderByClause(columns)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public inline infix fun <T> HavingSelectStatement<T>.ORDER_BY(column: ClauseElement): OrderBySelectStatement<T> =
    ORDER_BY(listOf(column))

@StatementDslMaker
public infix fun <T> HavingSelectStatement<T>.ORDER_BY(columns: Iterable<ClauseElement>): OrderBySelectStatement<T> =
    appendToOrderBy(SimpleOrderByClause(columns)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public inline infix fun <T> GroupBySelectStatement<T>.ORDER_BY(column: ClauseElement): OrderBySelectStatement<T> =
    ORDER_BY(listOf(column))

@StatementDslMaker
public infix fun <T> GroupBySelectStatement<T>.ORDER_BY(columns: Iterable<ClauseElement>): OrderBySelectStatement<T> =
    appendToOrderBy(SimpleOrderByClause(columns)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public inline infix fun <T> JoinSelectStatement<T>.ORDER_BY(column: ClauseElement): OrderBySelectStatement<T> =
    ORDER_BY(listOf(column))

@StatementDslMaker
public infix fun <T> JoinSelectStatement<T>.ORDER_BY(columns: Iterable<ClauseElement>): OrderBySelectStatement<T> =
    appendToOrderBy(SimpleOrderByClause(columns)).also {
        container changeLastStatement it
    }