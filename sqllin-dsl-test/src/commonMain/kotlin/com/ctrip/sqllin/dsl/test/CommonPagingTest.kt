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

package com.ctrip.sqllin.dsl.test

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import androidx.paging.testing.TestPager
import androidx.paging.testing.asSnapshot
import com.ctrip.sqllin.driver.DatabasePath
import com.ctrip.sqllin.dsl.DSLDBConfiguration
import com.ctrip.sqllin.dsl.Database
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.paging.pagingSource
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.clause.OrderByWay.ASC
import com.ctrip.sqllin.dsl.sql.clause.OrderByWay.DESC
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The tests of sqllin-dsl-paging's PagingSource, which load it through androidx.paging's `TestPager` and `Pager`.
 *
 * @author Yuang Qiao
 */
@OptIn(ExperimentalDSLDatabaseAPI::class)
class CommonPagingTest(private val path: DatabasePath) {

    private fun config(placeholders: Boolean = true) =
        PagingConfig(pageSize = 10, initialLoadSize = 20, enablePlaceholders = placeholders)

    /**
     * Runs [block] with a database of 100 persons, whose ages are 1 to 100.
     */
    private inline fun withPersons(block: (Database) -> Unit) {
        val database = Database(
            DSLDBConfiguration(
                name = CommonBasicTest.DATABASE_NAME,
                path = path,
                version = 1,
                create = {
                    CREATE(PersonWithIdTable)
                    CREATE(BookTable)
                    CREATE(CategoryTable)
                },
            )
        )
        try {
            database { PersonWithIdTable INSERT (1..100).map { PersonWithId(id = null, name = "p$it", age = it) } }
            block(database)
        } finally {
            database.close()
        }
    }

    private fun Database.byAge(): PagingSource<Int, PersonWithId> =
        pagingSource { PersonWithIdTable SELECT ORDER_BY(PersonWithIdTable.age to ASC) }

    private fun LoadResult<Int, PersonWithId>?.ages(): List<Int> = assertIs<LoadResult.Page<Int, PersonWithId>>(this).data.map { it.age }

    /** The keys and the counts of rows around a page, in this order. */
    private fun LoadResult<Int, *>?.placement(): List<Int?> =
        assertIs<LoadResult.Page<Int, *>>(this).run { listOf(prevKey, nextKey, itemsBefore, itemsAfter) }

    /**
     * A first page of 20, from the first row, and pages of 10 after it: the first page counts the rows, which places
     * every page among them. A refresh in the middle of the rows has a key to prepend from, whose page ends where the
     * refresh begins; a prepend near the first row loads only the rows before its key, so that no row is in two pages;
     * and a refresh past the last row, as after rows are deleted, loads the last rows.
     */
    fun testPagingSourceLoads() = runTest {
        withPersons { database ->
            val fromStart = TestPager(config(), database.byAge())
            val first = fromStart.refresh()
            assertEquals((1..20).toList(), first.ages())
            assertEquals(listOf(null, 20, 0, 80), first.placement())
            val appended = fromStart.append()
            assertEquals((21..30).toList(), appended.ages())
            assertEquals(listOf(20, 30, 20, 70), appended.placement())

            val fromMiddle = TestPager(config(), database.byAge())
            val middle = fromMiddle.refresh(initialKey = 45)
            assertEquals((46..65).toList(), middle.ages())
            assertEquals(listOf(45, 65, 45, 35), middle.placement())
            val prepended = fromMiddle.prepend()
            assertEquals((36..45).toList(), prepended.ages())
            assertEquals(listOf(35, 45, 35, 55), prepended.placement())

            val nearStart = TestPager(config(), database.byAge())
            assertEquals((6..25).toList(), nearStart.refresh(initialKey = 5).ages())
            val start = nearStart.prepend()
            assertEquals((1..5).toList(), start.ages())
            assertEquals(null, start.placement().first())

            val atEnd = TestPager(config(), database.byAge())
            val end = atEnd.refresh(initialKey = 95)
            assertEquals((96..100).toList(), end.ages())
            assertEquals(listOf(95, null, 95, 0), end.placement())

            val pastEnd = TestPager(config(), database.byAge())
            val last = pastEnd.refresh(initialKey = 150)
            assertEquals((81..100).toList(), last.ages())
            assertEquals(listOf(80, null, 80, 0), last.placement())
        }
    }

    /**
     * The key of a refresh is half the initial load before the row last read, whether the presented list has
     * placeholders, whose positions count the rows before the first page, or not, whose positions don't.
     */
    fun testPagingSourceRefreshKey() = runTest {
        withPersons { database ->
            for (placeholders in listOf(true, false)) {
                val source = database.byAge()
                val pager = TestPager(config(placeholders), source)
                pager.refresh(initialKey = 45)
                assertEquals(40, source.getRefreshKey(pager.getPagingState { it.age == 51 }), "placeholders: $placeholders")
                assertEquals(37, source.getRefreshKey(pager.getPagingState { it.age == 48 }), "placeholders: $placeholders")
            }
            val source = database.byAge()
            val pager = TestPager(config(), source)
            pager.refresh()
            assertEquals(0, source.getRefreshKey(pager.getPagingState { it.age == 4 }))
            assertEquals(null, source.getRefreshKey(PagingState(emptyList(), null, config(), 0)))
        }
    }

    /**
     * A change of the table the query reads invalidates the source, after its first page, and a change of another
     * table doesn't, nor one before the first page, which reads the rows as they are. An invalidated source loads no
     * more pages.
     */
    fun testPagingSourceInvalidation() = runTest {
        withPersons { database ->
            val source = database.byAge()
            TestPager(config(), source).refresh()
            database { CategoryTable INSERT Category(name = "Kotlin Cookbook", code = 1) }
            assertEquals(false, source.invalid)
            database suspendedScope { PersonWithIdTable DELETE WHERE(PersonWithIdTable.age GT 90) }
            assertEquals(true, source.invalid)
            assertIs<LoadResult.Invalid<Int, PersonWithId>>(source.load(PagingSource.LoadParams.Append(key = 20, loadSize = 10, placeholdersEnabled = true)))

            // Invalidated from elsewhere, as by a refresh, which closes its registration: a change after it is nothing
            val refreshed = database.byAge()
            TestPager(config(), refreshed).refresh()
            refreshed.invalidate()
            database { PersonWithIdTable INSERT PersonWithId(id = null, name = "p91", age = 91) }
            assertEquals(true, refreshed.invalid)

            val unloaded = database.byAge()
            database { PersonWithIdTable DELETE WHERE(PersonWithIdTable.age EQ 91) }
            assertEquals(false, unloaded.invalid)
            val first = TestPager(config(), unloaded).refresh()
            assertEquals(listOf(null, 20, 0, 70), first.placement())

            // Invalidated before it loads: it builds no SELECT, so it reads nothing
            var built = 0
            val preInvalidated = database.pagingSource {
                built++
                PersonWithIdTable SELECT ORDER_BY(PersonWithIdTable.age to ASC)
            }
            preInvalidated.invalidate()
            assertIs<LoadResult.Invalid<Int, PersonWithId>>(TestPager(config(), preInvalidated).refresh())
            assertEquals(0, built)

            // A change between the two reads of a refresh past the last row makes their token stale, so that the source
            // registers nothing, and invalidates itself as it returns LoadResult.Invalid, which a TestPager doesn't do
            var reads = 0
            val clamped = database.pagingSource {
                if (++reads == 2)
                    database { PersonWithIdTable INSERT PersonWithId(id = null, name = "p91", age = 91) }
                PersonWithIdTable SELECT ORDER_BY(PersonWithIdTable.age to ASC)
            }
            assertIs<LoadResult.Invalid<Int, PersonWithId>>(TestPager(config(), clamped).refresh(initialKey = 150))
            assertEquals(2, reads)
            assertEquals(true, clamped.invalid)

            // A change before a later page is read invalidates the source, which then doesn't return that page
            var loads = 0
            val changing = database.pagingSource {
                if (++loads == 2)
                    database { PersonWithIdTable DELETE WHERE(PersonWithIdTable.age EQ 91) }
                PersonWithIdTable SELECT ORDER_BY(PersonWithIdTable.age to ASC)
            }
            val changingPager = TestPager(config(), changing)
            changingPager.refresh()
            assertIs<LoadResult.Invalid<Int, PersonWithId>>(changingPager.append())
            assertEquals(true, changing.invalid)
        }
    }

    /**
     * A page is a subquery of any SELECT: one with a WHERE, whose parameters both the page and the count of its rows
     * take, an outer join, whose rows have NULL columns, and a compound SELECT with its own ORDER BY.
     */
    fun testPagingSourceQueries() = runTest {
        withPersons { database ->
            val older = TestPager(config(), database.pagingSource {
                PersonWithIdTable SELECT WHERE(PersonWithIdTable.age GT 50) ORDER_BY (PersonWithIdTable.age to DESC)
            })
            val oldest = older.refresh()
            assertEquals((100 downTo 81).toList(), oldest.ages())
            assertEquals(listOf(null, 20, 0, 30), oldest.placement())
            assertEquals((80 downTo 71).toList(), older.append().ages())

            database {
                CategoryTable INSERT Category(name = "Kotlin Cookbook", code = 456)
                BookTable INSERT listOf(
                    Book(name = "Kotlin Cookbook", author = "Ken Kousen", pages = 251, price = 37.72),
                    Book(name = "Modern Java Recipes", author = "Ken Kousen", pages = 322, price = 25.78),
                    Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95),
                )
            }
            val joined = TestPager(PagingConfig(pageSize = 2, initialLoadSize = 2), database.pagingSource {
                (BookTable LEFT_OUTER_JOIN CategoryTable USING BookTable.name) SELECT WHERE<Joiner>(BookTable.pages GT 0) ORDER_BY (BookTable.name to ASC)
            })
            val books = assertIs<LoadResult.Page<Int, Joiner>>(joined.refresh())
            assertEquals(listOf("Kotlin Cookbook" to 456, "Modern Java Recipes" to null), books.data.map { it.name to it.code })
            assertEquals(listOf(null, 2, 0, 1), books.placement())
            val rest = assertIs<LoadResult.Page<Int, Joiner>>(joined.append())
            assertEquals(listOf("The Lost Symbol" to null), rest.data.map { it.name to it.code })

            val extremes = TestPager(config(), database.pagingSource {
                ((PersonWithIdTable SELECT WHERE(PersonWithIdTable.age LTE 5)) UNION_ALL (PersonWithIdTable SELECT WHERE(PersonWithIdTable.age GT 95))) ORDER_BY (PersonWithIdTable.age to DESC)
            })
            val both = extremes.refresh()
            assertEquals(listOf(100, 99, 98, 97, 96, 5, 4, 3, 2, 1), both.ages())
            assertEquals(listOf(null, null, 0, 0), both.placement())
        }
    }

    /**
     * Through a `Pager`, without placeholders: the pages scrolled through are the rows in order, and a change reloads
     * the pages around the position read, with no row twice, nor a deleted one.
     */
    fun testPagingSourceWithPager() = runTest {
        withPersons { database ->
            val pager = Pager(PagingConfig(pageSize = 10, enablePlaceholders = false)) { database.byAge() }
            val loaded = pager.flow.asSnapshot { scrollTo(index = 45) }
            assertTrue(loaded.size > 45)
            assertEquals((1..loaded.size).toList(), loaded.map { it.age })

            val reloaded = pager.flow.asSnapshot {
                scrollTo(index = 45)
                database suspendedScope { PersonWithIdTable DELETE WHERE(PersonWithIdTable.age LTE 10) }
            }
            assertTrue(reloaded.isNotEmpty())
            assertTrue(reloaded.all { it.age > 10 })
            assertEquals(reloaded.sortedBy { it.age }.distinct(), reloaded)
        }
    }
}
