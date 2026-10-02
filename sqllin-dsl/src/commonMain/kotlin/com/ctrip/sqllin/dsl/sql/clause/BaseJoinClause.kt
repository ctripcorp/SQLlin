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

import com.ctrip.sqllin.dsl.annotation.StatementDslMaker
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.statement.JoinSelectStatement
import com.ctrip.sqllin.dsl.sql.statement.JoinStatementWithoutCondition

/**
 * Base class for JOIN clauses in SELECT statements.
 *
 * Generates SQL for joining multiple tables. Different JOIN types (INNER, LEFT OUTER, CROSS,
 * NATURAL) extend this class with their specific SQL keywords.
 *
 * @param R The result entity type after JOIN
 * @param tables Tables to join
 *
 * @author Yuang Qiao
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

/**
 * NATURAL JOIN clause - automatically matches columns with the same name.
 *
 * Does not require ON or USING condition.
 *
 * @param R The result entity type after JOIN
 */
public sealed class NaturalJoinClause<R>(vararg tables: Table<*>) : BaseJoinClause<R>(*tables)

/**
 * JOIN clause that requires an ON or USING condition.
 *
 * Returns [JoinStatementWithoutCondition] which must be completed with ON or USING.
 *
 * @param R The result entity type after JOIN
 */
public sealed class JoinClause<R>(vararg tables: Table<*>) : BaseJoinClause<R>(*tables)

@StatementDslMaker
public infix fun <R> JoinStatementWithoutCondition<R>.ON(condition: SelectCondition): JoinSelectStatement<R> =
    convertToJoinSelectStatement(condition)

@StatementDslMaker
public inline infix fun <R> JoinStatementWithoutCondition<R>.USING(clauseElement: ClauseElement): JoinSelectStatement<R> =
    USING(listOf(clauseElement))

@StatementDslMaker
public infix fun <R> JoinStatementWithoutCondition<R>.USING(clauseElements: Iterable<ClauseElement>): JoinSelectStatement<R> =
    convertToJoinSelectStatement(clauseElements)