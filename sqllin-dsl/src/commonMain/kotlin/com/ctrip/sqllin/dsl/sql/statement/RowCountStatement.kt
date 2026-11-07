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
import kotlin.concurrent.Volatile

/**
 * The number of the rows of a SELECT, `SELECT count(*) FROM (select)`, which a PagingSource of sqllin-dsl-paging reads
 * with its first page, so that it tells how many rows are before and after each page. It is read when it runs, under
 * the lock its scope runs under.
 *
 * @author Yuang Qiao
 */
internal class RowCountStatement(
    sqlStr: String,
    private val connection: DatabaseConnection,
    override val parameters: MutableList<Any?>?,
) : SingleStatement(sqlStr) {

    @Volatile
    private var count: Long? = null

    override fun execute() {
        count = connection.query(sqlStr, params).use {
            it.next()
            it.getLong(0)
        }
    }

    /**
     * Returns the number of rows the SELECT returned.
     *
     * @throws IllegalStateException if the statement hasn't run yet
     */
    fun getResult(): Long = checkNotNull(count) { "You have to call 'execute' function before call 'getResult'!!!" }
}
