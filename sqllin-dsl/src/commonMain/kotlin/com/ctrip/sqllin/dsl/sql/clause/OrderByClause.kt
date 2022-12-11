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

import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.statement.*

/**
 * SQL 'order by' clause by select statement
 * @author yaqiao
 */

public class OrderByClause<T : DBEntity<T>> internal constructor(private val column2WayMap: Map<ClauseElement, OrderByWay>): SelectClause<T> {

    override val clauseStr: String
        get() {
            require(column2WayMap.isNotEmpty()) { "Please provider at least one 'BaseClauseElement' -> 'OrderByWay' entry when you use the 'ORDER BY' clause!!!" }
            return buildString {
                append(" ORDER BY ")
                val iterator = column2WayMap.entries.iterator()
                do {
                    val (number, way) = iterator.next()
                    append(number.valueName)
                    append(' ')
                    append(way.str)
                    val hasNext = iterator.hasNext()
                    val symbol = if (hasNext) ',' else ' '
                    append(symbol)
                } while (hasNext)
            }
        }
}

public enum class OrderByWay(internal val str: String) {
    ASC("ASC"),
    DESC("DESC")
}

public fun <T : DBEntity<T>> ORDER_BY(vararg column2Ways: Pair<ClauseElement, OrderByWay>): OrderByClause<T> =
    OrderByClause(mapOf(*column2Ways))

public inline infix fun <T : DBEntity<T>> WhereSelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

public infix fun <T : DBEntity<T>> WhereSelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(OrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

public inline infix fun <T : DBEntity<T>> HavingSelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

public infix fun <T : DBEntity<T>> HavingSelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(OrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

public inline infix fun <T : DBEntity<T>> GroupBySelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

public infix fun <T : DBEntity<T>> GroupBySelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(OrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

public inline infix fun <T : DBEntity<T>> JoinSelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

public infix fun <T : DBEntity<T>> JoinSelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(OrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }