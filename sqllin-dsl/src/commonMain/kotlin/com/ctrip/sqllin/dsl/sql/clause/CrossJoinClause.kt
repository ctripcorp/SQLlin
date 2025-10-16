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
 * CROSS JOIN clause - returns the Cartesian product of two tables.
 *
 * Generates SQL: ` CROSS JOIN table`
 * Does not require ON or USING condition.
 * Returns every combination of rows from both tables.
 *
 * **Warning**: Result size = (left rows) × (right rows). Use with caution on large tables.
 *
 * @param R The result entity type after JOIN
 *
 * @author Yuang Qiao
 */
internal class CrossJoinClause<R>(vararg tables: Table<*>) : NaturalJoinClause<R>(*tables) {
    override val clauseName: String = " CROSS JOIN "
}

/**
 * Creates a CROSS JOIN clause (no ON/USING needed).
 *
 * Usage:
 * ```kotlin
 * SELECT(color) CROSS_JOIN (size)
 * // Returns all color-size combinations
 * ```
 *
 * **Warning**: Returns (left rows) × (right rows) results.
 */
@StatementDslMaker
public fun <R> CROSS_JOIN(vararg tables: Table<*>): NaturalJoinClause<R> = CrossJoinClause(*tables)