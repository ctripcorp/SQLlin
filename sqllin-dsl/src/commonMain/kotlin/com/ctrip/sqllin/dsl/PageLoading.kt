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

package com.ctrip.sqllin.dsl

import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.annotation.InternalSQLlinAPI
import com.ctrip.sqllin.dsl.sql.statement.SelectStatement

/*
 * What the PagingSource of sqllin-dsl-paging loads its pages through, which needs statements that DatabaseScope only
 * builds internally.
 */

/**
 * A page of the rows of a SELECT, which [loadPage] reads.
 *
 * @property rows The rows of the page
 * @property rowCount The number of all the rows of the SELECT, if they were counted
 * @property versions The token of the versions of the tables the rows were read from, as [Database.versioned] returns
 *
 * @author Yuang Qiao
 */
@InternalSQLlinAPI
@ExperimentalDSLDatabaseAPI
public class LoadedPage<T> internal constructor(
    public val rows: List<T>,
    public val rowCount: Int?,
    public val versions: TableVersions,
)

/**
 * Reads the rows of the SELECT that [query] builds from [offset] on, at most [limit] of them, and, if [countRows], the
 * number of all of them, as `SELECT * FROM (select) LIMIT limit OFFSET offset` and `SELECT count(*) FROM (select)`, in a
 * read-only scope that returns the token of the versions of the tables they read, as [Database.versioned] does.
 *
 * @throws IllegalArgumentException if [query] writes, as [Database.versioned] does
 */
@InternalSQLlinAPI
@ExperimentalDSLDatabaseAPI
public suspend fun <T> Database.loadPage(
    offset: Int,
    limit: Int,
    countRows: Boolean,
    query: DatabaseScope.() -> SelectStatement<T>,
): LoadedPage<T> {
    val (statements, versions) = versioned { page(query(), offset, limit, countRows) }
    val (page, count) = statements
    return LoadedPage(page.getResults(), count?.getResult()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(), versions)
}

/**
 * Whether a table of this token has changed since its read, through its [Database], as [Database.onChange] checks.
 */
@InternalSQLlinAPI
@ExperimentalDSLDatabaseAPI
public val TableVersions.isStale: Boolean
    get() = isStaleIn(tracker.versions.value)
