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
 * LEFT OUTER JOIN clause - returns all rows from the left table and matching rows from the right.
 *
 * Generates SQL: ` LEFT OUTER JOIN table`
 * Requires ON or USING condition.
 * Returns NULL for right table columns when there's no match.
 *
 * @param R The result entity type after JOIN
 *
 * @author Yuang Qiao
 */
internal class LeftOuterJoinClause<R>(
    vararg tables: Table<*>
) : JoinClause<R>(*tables) {

    override val clauseName: String = " LEFT OUTER JOIN "
}

/**
 * Creates a LEFT OUTER JOIN clause (requires ON or USING).
 *
 * Usage:
 * ```kotlin
 * SELECT(user) LEFT_OUTER_JOIN (order) ON (user.id EQ order.userId)
 * // Returns all users, including those without orders
 * ```
 */
@StatementDslMaker
public fun <R> LEFT_OUTER_JOIN(vararg tables: Table<*>): JoinClause<R> = LeftOuterJoinClause(*tables)

/**
 * NATURAL LEFT OUTER JOIN - automatically joins on matching column names.
 *
 * Generates SQL: ` NATURAL LEFT OUTER JOIN table`
 * Does not require ON or USING.
 * Returns all left table rows, with NULLs for non-matching right table columns.
 *
 * @param R The result entity type after JOIN
 *
 * @author Yuang Qiao
 */
internal class NaturalLeftOuterJoinClause<R>(
    vararg tables: Table<*>
) : NaturalJoinClause<R>(*tables) {

    override val clauseName: String = " NATURAL LEFT OUTER JOIN "
}

/**
 * Creates a NATURAL LEFT OUTER JOIN clause (no ON/USING needed).
 *
 * Usage:
 * ```kotlin
 * SELECT(user) NATURAL_LEFT_OUTER_JOIN (profile)
 * ```
 */
@StatementDslMaker
public fun <R> NATURAL_LEFT_OUTER_JOIN(vararg tables: Table<*>): NaturalJoinClause<R> = NaturalLeftOuterJoinClause(*tables)