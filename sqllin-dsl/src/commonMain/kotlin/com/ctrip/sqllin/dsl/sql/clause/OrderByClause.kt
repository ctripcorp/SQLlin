package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.statement.*

/**
 * SQL 'order by' clause by select statement
 * @author yaqiao
 */

class OrderByClause<T : DBEntity<T>> internal constructor(private val column2WayMap: Map<ClauseElement, OrderByWay>): SelectClause<T> {

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

enum class OrderByWay(internal val str: String) {
    ASC("ASC"),
    DESC("DESC")
}

fun <T : DBEntity<T>> ORDER_BY(vararg column2Ways: Pair<ClauseElement, OrderByWay>): OrderByClause<T> =
    OrderByClause(mapOf(*column2Ways))

inline infix fun <T : DBEntity<T>> WhereSelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

infix fun <T : DBEntity<T>> WhereSelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(OrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

inline infix fun <T : DBEntity<T>> HavingSelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

infix fun <T : DBEntity<T>> HavingSelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(OrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

inline infix fun <T : DBEntity<T>> GroupBySelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

infix fun <T : DBEntity<T>> GroupBySelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(OrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }

inline infix fun <T : DBEntity<T>> JoinSelectStatement<T>.ORDER_BY(column2Way: Pair<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    ORDER_BY(mapOf(column2Way))

infix fun <T : DBEntity<T>> JoinSelectStatement<T>.ORDER_BY(column2WayMap: Map<ClauseElement, OrderByWay>): OrderBySelectStatement<T> =
    appendToOrderBy(OrderByClause(column2WayMap)).also {
        container changeLastStatement it
    }