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
import com.ctrip.sqllin.dsl.annotation.StatementDslMaker
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
public sealed class BaseJoinClause<R>(private vararg val joinedTables: Relation<*>) : SelectClause<R> {

    internal abstract val clauseName: String

    /**
     * The tables and views that the relations this clause joins read, which an observed query watches.
     */
    internal val tables: Set<String>
        get() = joinedTables.flatMapTo(LinkedHashSet()) { it.readTables }

    /**
     * The parameters of the derived tables this clause joins, in their order.
     */
    internal val parameters: List<Any?>
        get() = joinedTables.flatMap { it.fromParameters }

    final override val clauseStr: String
        get() = buildString {
            append(clauseName)
            joinedTables.forEachIndexed { index, table ->
                append(table.fromSQL)
                if (index < joinedTables.lastIndex)
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
public sealed class NaturalJoinClause<R>(vararg tables: Relation<*>) : BaseJoinClause<R>(*tables)

/**
 * JOIN clause that requires an ON or USING condition.
 *
 * Returns [JoinStatementWithoutCondition] which must be completed with ON or USING.
 *
 * @param R The result entity type after JOIN
 */
public sealed class JoinClause<R>(vararg tables: Relation<*>) : BaseJoinClause<R>(*tables)

/**
 * Completes a join of the old join API with `ON condition`.
 *
 * **To be removed in the next version after 2.5.0**: this join API is replaced by joins of relations, such as
 * `(FROM(PersonTable) INNER_JOIN BookTable ON (...)) SELECT X<R>()`, which check the type their rows are read into
 * against the joined relations, and can join more than two. See
 * [JoinedRelation][com.ctrip.sqllin.dsl.sql.clause.JoinedRelation].
 */
@Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")
@StatementDslMaker
public infix fun <R> JoinStatementWithoutCondition<R>.ON(condition: SelectCondition): JoinSelectStatement<R> =
    convertToJoinSelectStatement(condition)

/**
 * Completes a join of the old join API with `USING (column)`.
 *
 * **To be removed in the next version after 2.5.0**: this join API is replaced by joins of relations, such as
 * `(FROM(PersonTable) INNER_JOIN BookTable ON (...)) SELECT X<R>()`, which check the type their rows are read into
 * against the joined relations, and can join more than two. See
 * [JoinedRelation][com.ctrip.sqllin.dsl.sql.clause.JoinedRelation].
 */
@Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")
@StatementDslMaker
public inline infix fun <R> JoinStatementWithoutCondition<R>.USING(clauseElement: ClauseElement<*>): JoinSelectStatement<R> =
    USING(listOf(clauseElement))

/**
 * Completes a join of the old join API with `USING (columns)`.
 *
 * **To be removed in the next version after 2.5.0**: this join API is replaced by joins of relations, such as
 * `(FROM(PersonTable) INNER_JOIN BookTable ON (...)) SELECT X<R>()`, which check the type their rows are read into
 * against the joined relations, and can join more than two. See
 * [JoinedRelation][com.ctrip.sqllin.dsl.sql.clause.JoinedRelation].
 */
@Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")
@StatementDslMaker
public infix fun <R> JoinStatementWithoutCondition<R>.USING(clauseElements: Iterable<ClauseElement<*>>): JoinSelectStatement<R> =
    convertToJoinSelectStatement(clauseElements)