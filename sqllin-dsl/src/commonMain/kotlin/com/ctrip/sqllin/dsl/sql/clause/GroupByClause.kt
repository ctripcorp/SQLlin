package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.statement.GroupBySelectStatement
import com.ctrip.sqllin.dsl.sql.statement.JoinSelectStatement
import com.ctrip.sqllin.dsl.sql.statement.WhereSelectStatement

/**
 * SQL 'GROUP BY' clause by select statement
 * @author yaqiao
 */

public class GroupByClause<T : DBEntity<T>> internal constructor(private val columnNames: List<ClauseElement>): SelectClause<T> {

    override val clauseStr: String
        get() = buildString {
            append(" GROUP BY ")
            columnNames.forEachIndexed { index, clauseElement ->
                append(clauseElement.valueName)
                if (index < columnNames.lastIndex)
                    append(',')
            }
        }
}

public fun <T : DBEntity<T>> GROUP_BY(vararg elements: ClauseElement): GroupByClause<T> = GroupByClause(elements.toList())

public infix fun <T : DBEntity<T>> WhereSelectStatement<T>.GROUP_BY(element: ClauseElement): GroupBySelectStatement<T> =
    appendToGroupBy(GroupByClause(listOf(element))).also {
        container changeLastStatement it
    }

public infix fun <T : DBEntity<T>> WhereSelectStatement<T>.GROUP_BY(elements: Iterable<ClauseElement>): GroupBySelectStatement<T> {
    val elementList = if (elements is List<ClauseElement>) elements else elements.toList()
    val statement = appendToGroupBy(GroupByClause(elementList))
    container changeLastStatement statement
    return statement
}

public infix fun <T : DBEntity<T>> JoinSelectStatement<T>.GROUP_BY(element: ClauseElement): GroupBySelectStatement<T> =
    appendToGroupBy(GroupByClause(listOf(element))).also {
        container changeLastStatement it
    }

public infix fun <T : DBEntity<T>> JoinSelectStatement<T>.GROUP_BY(elements: Iterable<ClauseElement>): GroupBySelectStatement<T> {
    val elementList = if (elements is List<ClauseElement>) elements else elements.toList()
    val statement = appendToGroupBy(GroupByClause(elementList))
    container changeLastStatement statement
    return statement
}