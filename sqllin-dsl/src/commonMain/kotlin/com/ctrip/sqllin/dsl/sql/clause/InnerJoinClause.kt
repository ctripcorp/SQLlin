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

/**
 * INNER JOIN clause - returns rows where there's a match in both tables.
 *
 * Generates SQL: ` JOIN table`
 * Requires ON or USING condition.
 *
 * @param R The result entity type after JOIN
 *
 * @author Yuang Qiao
 */
internal class InnerJoinClause<R>(
    vararg tables: Table<*>,
) : JoinClause<R>(*tables) {

    override val clauseName: String = " JOIN "
}

/**
 * Creates an INNER JOIN clause (requires ON or USING).
 *
 * Usage:
 * ```kotlin
 * SELECT(user) JOIN (order) ON (user.id EQ order.userId)
 * SELECT(user) JOIN (order) USING (user.id)
 * ```
 */
@StatementDslMaker
public fun <R> JOIN(vararg tables: Table<*>): JoinClause<R> = InnerJoinClause(*tables)

/**
 * Alias for [JOIN] - creates an INNER JOIN clause.
 */
@StatementDslMaker
public inline fun <R> INNER_JOIN(vararg tables: Table<*>): JoinClause<R> = JOIN(*tables)

/**
 * NATURAL INNER JOIN - automatically joins on columns with matching names.
 *
 * Generates SQL: ` NATURAL JOIN table`
 * Does not require ON or USING.
 *
 * @param R The result entity type after JOIN
 *
 * @author Yuang Qiao
 */
internal class NaturalInnerJoinClause<R>(
    vararg tables: Table<*>,
) : NaturalJoinClause<R>(*tables) {

    override val clauseName: String = " NATURAL JOIN "
}

/**
 * Creates a NATURAL JOIN clause (no ON/USING needed).
 *
 * Usage:
 * ```kotlin
 * SELECT(user) NATURAL_JOIN (profile)  // Joins on matching column names
 * ```
 */
@StatementDslMaker
public fun <R> NATURAL_JOIN(vararg tables: Table<*>): NaturalJoinClause<R> = NaturalInnerJoinClause(*tables)

/**
 * Alias for [NATURAL_JOIN].
 */
@StatementDslMaker
public inline fun <R> NATURAL_INNER_JOIN(vararg tables: Table<*>): NaturalJoinClause<R> = NATURAL_JOIN(*tables)