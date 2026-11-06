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

import com.ctrip.sqllin.driver.CommonCursor
import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import kotlin.concurrent.Volatile

/**
 * A PRAGMA that reads something of the database, as `PRAGMA_INTEGRITY_CHECK()` or `PRAGMA_TABLE_INFO(table)` builds,
 * whose result [getResults] returns once the scope has run, or one that sets how statements run, which returns none.
 *
 * Whether a PRAGMA returns rows is a property of the PRAGMA, which decides how it runs, as no driver runs both kinds
 * the same way: Android's framework doesn't run a statement that returns rows with `execSQL`, and sqlite-jdbc doesn't
 * run one that returns none as a query. A PRAGMA that returns rows is read when it runs, and its cursor closed, as its
 * result is small, rather than when [getResults] is called, as the results of a SELECT are.
 *
 * A PRAGMA changes no table, so a scope of PRAGMAs and SELECTs only reads, as a versioned read has to.
 *
 * @param read Reads the rows the PRAGMA returns, or null for a PRAGMA that returns none, which runs with `execSQL`
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public class PragmaStatement<R> internal constructor(
    sqlStr: String,
    private val connection: DatabaseConnection,
    private val read: ((CommonCursor) -> R)?,
) : SingleStatement(sqlStr) {

    override val parameters: MutableList<Any?>? = null

    @Volatile
    private var result: Any? = NOT_RUN

    override fun execute() {
        result = if (read == null) connection.execSQL(sqlStr) else connection.query(sqlStr).use(read)
    }

    /**
     * Returns what the PRAGMA read.
     *
     * @throws IllegalStateException if the statement hasn't run yet, as within the scope that builds it
     */
    @Suppress("UNCHECKED_CAST")
    public fun getResults(): R {
        val result = result
        check(result !== NOT_RUN) { "You have to call 'execute' function before call 'getResults'!!!" }
        return result as R
    }

    private companion object {
        val NOT_RUN = Any()
    }
}
