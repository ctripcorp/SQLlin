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