/*
 * Copyright (C) 2026 Ctrip.com.
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

package com.ctrip.sqllin.dsl.sql.statement

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import kotlin.concurrent.Volatile

/**
 * `EXPLAIN QUERY PLAN` of a SELECT, as `EXPLAIN_QUERY_PLAN(select)` builds: it doesn't run the SELECT, but tells how
 * SQLite would run it, which [getResults] returns once the scope has run.
 *
 * The plan is read when the statement runs, and its cursor closed, as the plan is small, rather than when
 * [getResults] is called, as the results of a SELECT are.
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public class QueryPlanStatement internal constructor(
    sqlStr: String,
    private val connection: DatabaseConnection,
    override val parameters: MutableList<Any?>?,
) : SingleStatement(sqlStr) {

    @Volatile
    private var plan: QueryPlan? = null

    override fun execute() {
        plan = connection.query(sqlStr, params).use { QueryPlan.of(it) }
    }

    /**
     * Returns the plan of the SELECT.
     *
     * @throws IllegalStateException if the statement hasn't run yet, as within the scope that builds it
     */
    public fun getResults(): QueryPlan =
        plan ?: throw IllegalStateException("You have to call 'execute' function before call 'getResults'!!!")
}
