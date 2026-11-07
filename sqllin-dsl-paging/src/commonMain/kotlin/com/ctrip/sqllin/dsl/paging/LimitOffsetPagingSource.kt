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

package com.ctrip.sqllin.dsl.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.ctrip.sqllin.dsl.Database
import com.ctrip.sqllin.dsl.DatabaseScope
import com.ctrip.sqllin.dsl.TableVersions
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.annotation.InternalSQLlinAPI
import com.ctrip.sqllin.dsl.isStale
import com.ctrip.sqllin.dsl.loadPage
import com.ctrip.sqllin.dsl.sql.statement.SelectStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

/**
 * Creates a [PagingSource] of the rows of the SELECT that [query] builds, which loads them a page at a time, by their
 * offsets, as Room's `LimitOffsetPagingSource` does, and invalidates itself when a statement run through this database
 * changes a table the SELECT reads, so that its `Pager` loads them again:
 * ```kotlin
 * val persons: Flow<PagingData<Person>> = Pager(PagingConfig(pageSize = 20)) {
 *     database.pagingSource { PersonTable SELECT ORDER_BY(PersonTable.id to ASC) }
 * }.flow
 * ```
 *
 * A page is `SELECT * FROM (select) LIMIT ... OFFSET ...`, so [query] can build any SELECT, with WHERE, a join or a
 * compound SELECT, which should have an ORDER BY that orders all of its rows, as SQLite returns them in no order
 * otherwise, so that the rows of two pages could be the same. [query] runs again to build it for each page, and should
 * only build it. The first page also counts the rows, so that the pages tell how many rows are before and after them,
 * which placeholders show, and a refresh past the last row loads the last ones.
 *
 * Its changes are seen as [Database.versioned] sees them: those made through this database, but not those of another
 * database instance, connection or process. A change after the first page invalidates the source, and a page that
 * reads rows changed since the first one is `LoadResult.Invalid`, so that no list mixes rows from before and after a
 * change; the source is invalid once it returns one, whoever loads it. The source registers for the changes weakly, so that a source that its `Pager` drops without invalidating it
 * can still be garbage collected.
 *
 * @param context The context the pages are loaded in, as a `Pager` may load them on the main thread
 * @param query Builds the SELECT to page
 * @return A source of the rows of the SELECT, keyed by their offsets
 */
@ExperimentalDSLDatabaseAPI
public fun <T : Any> Database.pagingSource(
    context: CoroutineContext = Dispatchers.IO,
    query: DatabaseScope.() -> SelectStatement<T>,
): PagingSource<Int, T> = LimitOffsetPagingSource(this, context, query)

/**
 * The [PagingSource] that [pagingSource] creates.
 *
 * Its keys are offsets, the borders between pages, as Room's are: a refresh loads the rows from its key on, an append
 * those from its key on, and a prepend those just before its key, at most as many as there are, so that two pages
 * never share a row, wherever a refresh starts. A page after the first row has a key to prepend from, so that the rows
 * above a refresh in the middle of the list can be loaded, as after a `RemoteMediator` invalidates the source.
 *
 * @author Yuang Qiao
 */
@OptIn(ExperimentalDSLDatabaseAPI::class, InternalSQLlinAPI::class)
internal class LimitOffsetPagingSource<T : Any>(
    private val database: Database,
    private val context: CoroutineContext,
    private val query: DatabaseScope.() -> SelectStatement<T>,
) : PagingSource<Int, T>() {

    /**
     * The token of the first page, which a change invalidates the source by, or null before it is loaded.
     */
    @Volatile
    private var firstVersions: TableVersions? = null

    /**
     * The number of the rows of the SELECT, which the first page counts, and the others are placed in.
     */
    @Volatile
    private var rowCount = 0

    /**
     * The registration that invalidates the source once a table of the first page changes.
     */
    @Volatile
    private var registration: AutoCloseable? = null

    init {
        // So that an invalidation from elsewhere, as a refresh, doesn't leave the registration to the next change
        registerInvalidatedCallback { registration?.close() }
    }

    override val jumpingSupported: Boolean
        get() = true

    /**
     * Returns the key of a refresh around the last position read, as Room does: half the initial load before it, so
     * that the rows around it load at once. With placeholders, the position counts the rows before the first page, and
     * without them it doesn't, so that the first page's offset is added to it.
     */
    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        val anchor = state.anchorPosition ?: return null
        val position = if (state.config.enablePlaceholders) {
            anchor
        } else {
            val itemsBefore = state.pages.firstOrNull()?.itemsBefore ?: 0
            anchor + if (itemsBefore == LoadResult.Page.COUNT_UNDEFINED) 0 else itemsBefore
        }
        return maxOf(0, position - state.config.initialLoadSize / 2)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> = withContext(context) {
        // Invalidated before it loads, as by a refresh: there is nothing to read
        if (invalid)
            return@withContext LoadResult.Invalid()
        val key = params.key ?: 0
        val first = firstVersions
        val result = if (first == null) {
            loadFirst(key, params.loadSize)
        } else {
            val (offset, limit) = when (params) {
                // At most as many rows as there are before the key, so that the page ends where the next begins
                is LoadParams.Prepend -> maxOf(0, key - params.loadSize) to minOf(key, params.loadSize)
                else -> key to params.loadSize
            }
            val loaded = database.loadPage(offset, limit, countRows = false, query)
            // The rows changed since the first page, which has already invalidated the source, or will once the scope
            // that changed them ends: they don't belong with the pages already loaded
            if (first.isStale)
                invalidated()
            else
                page(loaded.rows, offset, limit)
        }
        if (invalid) LoadResult.Invalid() else result
    }

    /**
     * Loads the first page, from [key] on, with the count of the rows, and registers for the changes of its tables.
     * A key past the last row, as after rows are deleted, loads the last rows instead.
     */
    private suspend fun loadFirst(key: Int, loadSize: Int): LoadResult<Int, T> {
        var loaded = database.loadPage(key, loadSize, countRows = true, query)
        val count = loaded.rowCount ?: 0
        var versions = loaded.versions
        var offset = key
        if (key > 0 && key >= count) {
            offset = maxOf(0, count - loadSize)
            loaded = database.loadPage(offset, loadSize, countRows = false, query)
            // The token of both reads, which a change between them makes stale
            versions += loaded.versions
        }
        rowCount = count
        firstVersions = versions
        // A change since the reads registers nothing, and makes them stale
        val registration = database.onChange(versions, invalidator(this)) ?: return invalidated()
        this.registration = registration
        // An invalidation while the page loaded ran the callback that closes the registration before it was set
        if (invalid)
            registration.close()
        return page(loaded.rows, offset, loadSize)
    }

    /**
     * Invalidates the source, which it may already be, and returns the result that says so. The source returns
     * `LoadResult.Invalid` for a change that no callback has invalidated it for yet through here, so that it is invalid
     * once it returns one, whoever loads it: a `Pager` invalidates a source that returns one, but a `TestPager`, or a
     * caller of [load], doesn't.
     */
    private fun invalidated(): LoadResult<Int, T> {
        invalidate()
        return LoadResult.Invalid()
    }

    /**
     * The page of [rows] read from [offset], at most [limit] of them, placed among the [rowCount] rows of the SELECT.
     */
    private fun page(rows: List<T>, offset: Int, limit: Int): LoadResult.Page<Int, T> {
        val next = offset + rows.size
        return LoadResult.Page(
            data = rows,
            prevKey = if (offset <= 0 || rows.isEmpty()) null else offset,
            nextKey = if (rows.isEmpty() || rows.size < limit || next >= rowCount) null else next,
            itemsBefore = offset,
            itemsAfter = maxOf(0, rowCount - next),
        )
    }

    private companion object {

        /**
         * The callback that invalidates [source], which only holds it weakly, as the database's registration holds the
         * callback until a change runs it: a source that its `Pager` drops can be collected before then.
         */
        fun invalidator(source: PagingSource<*, *>): () -> Unit {
            val reference = weakReference(source)
            return { reference()?.invalidate() }
        }
    }
}
