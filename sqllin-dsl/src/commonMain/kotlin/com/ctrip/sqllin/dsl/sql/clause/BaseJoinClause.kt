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
import com.ctrip.sqllin.dsl.sql.statement.JoinSelectStatement
import com.ctrip.sqllin.dsl.sql.statement.JoinStatementWithoutCondition

/**
 * SQL abstract "JOIN" clause
 * @author yaqiao
 */

public sealed class BaseJoinClause<R>(private vararg val tables: Table<*>) : SelectClause<R> {

    internal abstract val clauseName: String

    final override val clauseStr: String
        get() = buildString {
            append(clauseName)
            tables.forEachIndexed { index, table ->
                append(table.tableName)
                if (index < tables.lastIndex)
                    append(',')
            }
        }
}

public sealed class NaturalJoinClause<R>(vararg tables: Table<*>) : BaseJoinClause<R>(*tables)

public sealed class JoinClause<R>(vararg tables: Table<*>) : BaseJoinClause<R>(*tables)

public infix fun <R> JoinStatementWithoutCondition<R>.ON(condition: SelectCondition): JoinSelectStatement<R> =
    convertToJoinSelectStatement(condition)

@Suppress("NOTHING_TO_INLINE")
public inline infix fun <R> JoinStatementWithoutCondition<R>.USING(clauseElement: ClauseElement): JoinSelectStatement<R> =
    USING(listOf(clauseElement))

public infix fun <R> JoinStatementWithoutCondition<R>.USING(clauseElements: Iterable<ClauseElement>): JoinSelectStatement<R> =
    convertToJoinSelectStatement(clauseElements)