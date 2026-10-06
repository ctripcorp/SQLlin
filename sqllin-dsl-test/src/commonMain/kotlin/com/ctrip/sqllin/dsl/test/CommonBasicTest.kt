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

package com.ctrip.sqllin.dsl.test

import com.ctrip.sqllin.driver.DatabaseConfiguration
import com.ctrip.sqllin.driver.DatabasePath
import com.ctrip.sqllin.dsl.DSLDBConfiguration
import com.ctrip.sqllin.dsl.Database
import com.ctrip.sqllin.dsl.DatabaseScope
import com.ctrip.sqllin.dsl.annotation.AdvancedInsertAPI
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.annotation.PlatformDependentSQLiteAPI
import com.ctrip.sqllin.dsl.sql.FtsTable
import com.ctrip.sqllin.dsl.sql.X
import com.ctrip.sqllin.dsl.sql.withName
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.clause.OrderByWay.ASC
import com.ctrip.sqllin.dsl.sql.clause.OrderByWay.DESC
import com.ctrip.sqllin.dsl.sql.statement.SelectStatement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * The sqllin-dsl common test
 * @author Yuang Qiao
 */

class CommonBasicTest(private val path: DatabasePath) {

    companion object {
        const val DATABASE_NAME = "BookStore.db"
        const val SQL_CREATE_BOOK = "create table book (id integer primary key autoincrement, name text, author text, pages integer, price real)"
        const val SQL_CREATE_CATEGORY = "create table category (id integer primary key autoincrement, name text, code integer)"
    }

    private inline fun Database.databaseAutoClose(block: (Database) -> Unit) = try {
        block(this)
    } finally {
        close()
    }

    fun testInsert() = Database(getDefaultDBConfig(), true).databaseAutoClose { database ->
        val book = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96)
        database {
            BookTable { bookTable ->
                bookTable INSERT book
            }
        }

        var statement: SelectStatement<Book>? = null
        database {
            val table = BookTable
            statement = table SELECT X
        }
        assertEquals(book, statement?.getResults()?.firstOrNull())
    }

    fun testDelete() = Database(getDefaultDBConfig(), true).databaseAutoClose { database ->
        val book1 = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96)
        val book2 = Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95)
        var statement: SelectStatement<Book>? = null
        database {
            statement = BookTable { bookTable ->
                bookTable INSERT listOf(book1, book2)
                bookTable SELECT X
            }
        }
        assertEquals(true, statement!!.getResults().any { it == book1 })
        assertEquals(true, statement.getResults().any { it == book2 })

        var statement1: SelectStatement<Book>? = null
        var statement2: SelectStatement<Book>? = null
        database {
            BookTable { table ->
                table DELETE WHERE(name EQ "The Da Vinci Code" AND (author EQ "Dan Brown"))
                statement1 = table SELECT WHERE(name EQ "The Da Vinci Code" AND (author EQ "Dan Brown"))
                table DELETE X
                statement2 = table SELECT X
            }
        }
        assertEquals(true, statement1!!.getResults().isEmpty())
        assertEquals(true, statement2!!.getResults().isEmpty())
    }

    fun testUpdate() = Database(getDefaultDBConfig(), true).databaseAutoClose { database ->
        val book1 = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96)
        val book2 = Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95)
        var statement: SelectStatement<Book>? = null
        database {
            statement = BookTable { table ->
                table INSERT listOf(book1, book2)
                table SELECT X
            }
        }

        assertEquals(true, statement!!.getResults().any { it == book1 })
        assertEquals(true, statement.getResults().any { it == book2 })

        val book1NewPrice = 18.96
        val book2NewPrice = 21.95
        val newBook1 = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = book1NewPrice)
        val newBook2 = Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = book2NewPrice)

        var newResult: SelectStatement<Book>? = null
        database {
            newResult = transaction {
                BookTable { table ->
                    table UPDATE SET { price = book1NewPrice } WHERE (name EQ book1.name AND (price EQ book1.price))
                    table UPDATE SET { price = book2NewPrice } WHERE (name EQ book2.name AND (price EQ book2.price))
                    table SELECT X
                }
            }
        }

        assertEquals(true, newResult!!.getResults().any { it == newBook1 })
        assertEquals(true, newResult.getResults().any { it == newBook2 })
    }

    fun testSelectWhereClause() = Database(getDefaultDBConfig(), true).databaseAutoClose { database ->
        val book0 = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96)
        val book1 = Book(name = "Kotlin Cookbook", author = "Ken Kousen", pages = 251, price = 37.72)
        val book2 = Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95)
        var statementOfWhere0: SelectStatement<Book>? = null
        var statementOfWhere1: SelectStatement<Book>? = null
        var statementOfWhere2: SelectStatement<Book>? = null
        database {
            BookTable { table ->
                table INSERT listOf(book0, book1, book2)
                statementOfWhere0 = table SELECT WHERE (pages LT 300)
                statementOfWhere1 = table SELECT WHERE (price GTE 30)
                statementOfWhere2 = table SELECT WHERE (author NEQ "Dan Brown")
            }
        }
        assertEquals(1, statementOfWhere0?.getResults()?.size)
        assertEquals(book1, statementOfWhere0?.getResults()?.firstOrNull())
        assertEquals(1, statementOfWhere1?.getResults()?.size)
        assertEquals(book1, statementOfWhere1?.getResults()?.firstOrNull())
        assertEquals(1, statementOfWhere2?.getResults()?.size)
        assertEquals(book1, statementOfWhere2?.getResults()?.firstOrNull())
    }

    fun testSelectOrderByClause() = Database(getDefaultDBConfig(), true).databaseAutoClose { database ->
        val book0 = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96)
        val book1 = Book(name = "Kotlin Cookbook", author = "Ken Kousen", pages = 251, price = 37.72)
        val book2 = Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95)
        var statementOfOrderBy: SelectStatement<Book>? = null
        var statementOfOrderBy2: SelectStatement<Book>? = null
        var statementOfWhereAndOrderBy: SelectStatement<Book>? = null
        var statementOfWhereAndOrderBy2: SelectStatement<Book>? = null
        database {
            BookTable { table ->
                table INSERT listOf(book0, book1, book2)
                statementOfOrderBy = table SELECT ORDER_BY(price to DESC)
                statementOfOrderBy2 = table SELECT ORDER_BY(price)
                statementOfWhereAndOrderBy = table SELECT WHERE(author EQ "Dan Brown") ORDER_BY mapOf(pages to ASC)
                statementOfWhereAndOrderBy2 = table SELECT WHERE(author EQ "Dan Brown") ORDER_BY pages
            }
        }
        assertEquals(3, statementOfOrderBy?.getResults()?.size)
        statementOfOrderBy!!.getResults().forEachIndexed { index, book ->
            val actualBook = when (index) {
                0 -> book1
                1 -> book2
                2 -> book0
                else -> throw IllegalStateException("Select got some wrong")
            }
            assertEquals(actualBook, book)
        }

        assertEquals(3, statementOfOrderBy2?.getResults()?.size)
        statementOfOrderBy2!!.getResults().forEachIndexed { index, book ->
            val actualBook = when (index) {
                0 -> book0
                1 -> book2
                2 -> book1
                else -> throw IllegalStateException("Select got some wrong")
            }
            assertEquals(actualBook, book)
        }

        assertEquals(2, statementOfWhereAndOrderBy?.getResults()?.size)
        statementOfWhereAndOrderBy!!.getResults().forEachIndexed { index, book ->
            val actualBook = when (index) {
                0 -> book0
                1 -> book2
                else -> throw IllegalStateException("Select got some wrong")
            }
            assertEquals(actualBook, book)
        }

        assertEquals(2, statementOfWhereAndOrderBy2?.getResults()?.size)
        statementOfWhereAndOrderBy2!!.getResults().forEachIndexed { index, book ->
            val actualBook = when (index) {
                0 -> book0
                1 -> book2
                else -> throw IllegalStateException("Select got some wrong")
            }
            assertEquals(actualBook, book)
        }
    }

    fun testSelectLimitAndOffsetClause() = Database(getDefaultDBConfig(), true).databaseAutoClose { database ->
        val book0 = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96)
        val book1 = Book(name = "Kotlin Cookbook", author = "Ken Kousen", pages = 251, price = 37.72)
        val book2 = Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95)
        var statementOfLimit0: SelectStatement<Book>? = null
        var statementOfLimit1: SelectStatement<Book>? = null
        var statementOfLimitAndOffset: SelectStatement<Book>? = null
        database {
            BookTable { table ->
                table INSERT listOf(book0, book1, book2)
                statementOfLimit0 = table SELECT LIMIT(2)
                statementOfLimit1 = table SELECT WHERE (author EQ "Dan Brown") LIMIT 1
                statementOfLimitAndOffset = table SELECT LIMIT(2) OFFSET 2
            }
        }
        assertEquals(2, statementOfLimit0?.getResults()?.size)
        assertEquals(1, statementOfLimit1?.getResults()?.size)
        assertEquals(1, statementOfLimitAndOffset?.getResults()?.size)
    }

    fun testGroupByAndHavingClause() = Database(getDefaultDBConfig(), true).databaseAutoClose { database ->
        val book0 = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96)
        val book1 = Book(name = "Kotlin Cookbook", author = "Ken Kousen", pages = 251, price = 37.72)
        val book2 = Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95)
        val book3 = Book(name = "Kotlin Guide Pratique", author = "Ken Kousen", pages = 398, price = 39.99)
        var statementOfGroupBy0: SelectStatement<Book>? = null
        var statementOfGroupBy1: SelectStatement<Book>? = null
        var statementOfGroupByAndHaving: SelectStatement<Book>? = null
        database {
            BookTable { table ->
                table INSERT listOf(book0, book1, book2, book3)
                statementOfGroupBy0 = table SELECT GROUP_BY(author)
                statementOfGroupBy1 = table SELECT WHERE(pages GT 300) GROUP_BY author
                table DELETE WHERE (name EQ "The Da Vinci Code")
                statementOfGroupByAndHaving = table SELECT GROUP_BY(author) HAVING (count(author) GTE 2)
            }
        }
        val result0 = statementOfGroupBy0!!.getResults()
        assertEquals(2, result0.size)
        assertNotEquals(result0[0].author, result0[1].author)

        val result1 = statementOfGroupBy1!!.getResults()
        assertEquals(2, result1.size)
        assertEquals(true, result1[0].pages > 300)
        assertEquals(true, result1[1].pages > 300)
        assertNotEquals(result1[0].author, result1[1].author)

        val resultOfGroupByAndHaving = statementOfGroupByAndHaving!!.getResults()
        assertEquals(1, resultOfGroupByAndHaving.size)
        assertEquals("Ken Kousen", resultOfGroupByAndHaving.first().author)
    }

    /**
     * Covers compound SELECTs: UNION, UNION ALL, INTERSECT and EXCEPT combine two SELECTs of the same result type, as
     * in SQL, from any tables. A member that ends with ORDER BY or LIMIT keeps them to itself, a compound on the right
     * is combined as a whole, and the compound takes its own ORDER BY and LIMIT.
     */
    @OptIn(ExperimentalDSLDatabaseAPI::class)
    fun testCompoundSelect() = Database(getNewAPIDBConfig()).databaseAutoClose { database ->
        database {
            BookTable INSERT listOf(
                Book(name = "The Da Vinci Code", author = "Dan Brown", price = 16.96, pages = 454),
                Book(name = "The Lost Symbol", author = "Dan Brown", price = 19.95, pages = 510),
                Book(name = "Kotlin Cookbook", author = "Ken Kousen", price = 37.72, pages = 251),
            )
            PersonWithIdTable INSERT listOf(
                PersonWithId(id = null, name = "Ann", age = 30),
                PersonWithId(id = null, name = "Kotlin Cookbook", age = 40),
            )
        }

        // The four operators, on projections. The INTERSECT only gives Dan Brown if the WHERE parameters keep their
        // order; swapped, both SELECTs would match every book.
        lateinit var member: SelectStatement<BookAuthor>
        lateinit var union: SelectStatement<BookAuthor>
        lateinit var unionAll: SelectStatement<BookAuthor>
        lateinit var intersect: SelectStatement<BookAuthor>
        lateinit var except: SelectStatement<BookAuthor>
        database {
            BookTable { table ->
                member = table SELECT WHERE<BookAuthor>(price LT 20.0)
                union = member UNION (table SELECT WHERE<BookAuthor>(price GT 10.0))
                unionAll = (table SELECT WHERE<BookAuthor>(price LT 20.0)) UNION_ALL (table SELECT WHERE<BookAuthor>(price GT 30.0))
                intersect = (table SELECT WHERE<BookAuthor>(price LT 20.0)) INTERSECT (table SELECT WHERE<BookAuthor>(pages GT 500))
                except = (table SELECT WHERE<BookAuthor>(price GT 0.0)) EXCEPT (table SELECT WHERE<BookAuthor>(price LT 20.0))
            }
        }
        assertEquals(listOf("Dan Brown", "Ken Kousen"), union.getResults().map { it.author }.sorted())
        assertEquals(listOf("Dan Brown", "Dan Brown", "Ken Kousen"), unionAll.getResults().map { it.author }.sorted())
        assertEquals(listOf("Dan Brown"), intersect.getResults().map { it.author })
        assertEquals(listOf("Ken Kousen"), except.getResults().map { it.author })
        // A SELECT that became part of a compound no longer runs on its own
        assertFailsWith<IllegalStateException> { member.getResults() }

        // Rows of different tables, projected into the same type
        lateinit var names: SelectStatement<NameOnly>
        database {
            names = (BookTable SELECT X<NameOnly>()) INTERSECT (PersonWithIdTable SELECT X<NameOnly>())
        }
        assertEquals(listOf(NameOnly("Kotlin Cookbook")), names.getResults())

        // A member's ORDER BY and LIMIT stay its own, here giving the longest and the shortest book. Written as they
        // are, SQLite would reject the first ORDER BY, and apply the last one to the whole compound.
        lateinit var extremes: SelectStatement<NameOnly>
        database {
            BookTable { table ->
                extremes = (table SELECT ORDER_BY<NameOnly>(pages to DESC) LIMIT 1) UNION_ALL (table SELECT ORDER_BY<NameOnly>(pages to ASC) LIMIT 1)
            }
        }
        assertEquals(listOf("Kotlin Cookbook", "The Lost Symbol"), extremes.getResults().map { it.name }.sorted())

        // The compound's own ORDER BY, LIMIT and OFFSET apply to all of it
        lateinit var page: SelectStatement<NameOnly>
        database {
            page = (BookTable SELECT X<NameOnly>()) UNION (PersonWithIdTable SELECT X<NameOnly>()) ORDER_BY (BookTable.name to ASC) LIMIT 2 OFFSET 1
        }
        assertEquals(listOf("Kotlin Cookbook", "The Da Vinci Code"), page.getResults().map { it.name })

        // Chained operators are evaluated from left to right, as in SQL, and parentheses group them
        lateinit var leftToRight: SelectStatement<NameOnly>
        lateinit var grouped: SelectStatement<NameOnly>
        database {
            PersonWithIdTable { table ->
                val ann = { table SELECT WHERE<NameOnly>(name EQ "Ann") }
                leftToRight = ann() UNION ann() UNION_ALL ann() // (Ann UNION Ann) UNION ALL Ann
                grouped = ann() UNION (ann() UNION_ALL ann()) // Ann UNION (Ann UNION ALL Ann)
            }
        }
        assertEquals(2, leftToRight.getResults().size)
        assertEquals(1, grouped.getResults().size)

        // Result columns combine too, and an incomplete member is rejected when it is combined: without GROUP BY,
        // 'author' would be NULL when no rows match
        lateinit var counts: SelectStatement<BookCount>
        database {
            BookTable { table ->
                counts = (table SELECT (count(X) AS BookCount::books) WHERE (author EQ "Dan Brown")) UNION_ALL
                    (table SELECT (count(X) AS BookCount::books) WHERE (author EQ "Ken Kousen"))
            }
        }
        assertEquals(listOf(1L, 2L), counts.getResults().map { it.books }.sorted())
        val ungrouped = assertFailsWith<IllegalArgumentException> {
            database {
                BookTable { table ->
                    (table SELECT (count(X) AS AuthorBookCount::books)) UNION (table SELECT (count(X) AS AuthorBookCount::books) GROUP_BY author)
                }
            }
        }
        assertEquals(true, ungrouped.message!!.contains("without GROUP BY"))
    }

    /**
     * Covers the deprecated `UNION {}` block, which keeps working for the table's own rows until it is removed.
     */
    @Suppress("DEPRECATION")
    fun testUnionSelect() = Database(getDefaultDBConfig(), true).databaseAutoClose { database ->
        val book0 = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96)
        val book1 = Book(name = "Kotlin Cookbook", author = "Ken Kousen", pages = 251, price = 37.72)
        val book2 = Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95)
        val book3 = Book(name = "Kotlin Guide Pratique", author = "Ken Kousen", pages = 398, price = 40.08)
        var statement: SelectStatement<Book>? = null
        database {
            statement = BookTable { table ->
                table INSERT listOf(book0, book1, book2, book3)
                UNION_ALL {
                    UNION {
                        table SELECT WHERE (author EQ "Ken Kousen")
                        table SELECT WHERE (name EQ "Kotlin Cookbook" OR (name EQ "The Da Vinci Code"))
                    }
                    table SELECT X
                }
            }
        }
        assertEquals(7, statement!!.getResults().size)
        assertEquals(2, statement.getResults().count { it == book0 })
        assertEquals(2, statement.getResults().count { it == book1 })
        assertEquals(1, statement.getResults().count { it == book2 })
        assertEquals(2, statement.getResults().count { it == book3 })
    }

    fun testFunction() = Database(getDefaultDBConfig(), true).databaseAutoClose { database ->
        val book0 = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96)
        val book1 = Book(name = "Kotlin Cookbook", author = "Ken Kousen", pages = 251, price = 37.72)
        val book2 = Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95)
        val book3 = Book(name = "Kotlin Guide Pratique", author = "Ken Kousen", pages = 398, price = 39.99)
        val book4 = Book(name = "Modern Java Recipes", author ="Ken Kousen", pages = 322, price = 25.78)
        var selectStatement0: SelectStatement<Book>? = null
        var selectStatement1: SelectStatement<Book>? = null
        var selectStatement2: SelectStatement<Book>? = null
        var selectStatement3: SelectStatement<Book>? = null
        var selectStatement4: SelectStatement<Book>? = null
        var selectStatement5: SelectStatement<Book>? = null
        var selectStatement6: SelectStatement<Book>? = null
        var selectStatement7: SelectStatement<Book>? = null
        var selectStatement8: SelectStatement<Book>? = null
        var selectStatement9: SelectStatement<Book>? = null
        // var selectStatement10: SelectStatement<Book>? = null
        var selectStatement11: SelectStatement<Book>? = null
        var selectStatement12: SelectStatement<Book>? = null
        var selectStatement13: SelectStatement<Book>? = null
        var selectStatement14: SelectStatement<Book>? = null
        var selectStatement15: SelectStatement<Book>? = null
        var selectStatement16: SelectStatement<Book>? = null
        var selectStatement17: SelectStatement<Book>? = null
        database {
            BookTable { table ->
                table INSERT listOf(book0, book1, book2, book3, book4)
                selectStatement0 = table SELECT WHERE(upper(name) EQ "KOTLIN COOKBOOK")
                selectStatement1 = table SELECT WHERE(lower(name) EQ "kotlin cookbook")
                selectStatement2 = table SELECT WHERE(length(name) EQ 17)
                selectStatement3 = table SELECT WHERE(abs(price) EQ 16.96)
                selectStatement4 = table SELECT GROUP_BY (author) HAVING (count(X) LT 3)
                selectStatement5 = table SELECT GROUP_BY (author) HAVING (max(price) GTE 30)
                selectStatement6 = table SELECT GROUP_BY (author) HAVING (min(price) LT 17)
                selectStatement7 = table SELECT GROUP_BY (author) HAVING (avg(pages) LT 400)
                selectStatement8 = table SELECT GROUP_BY (author) HAVING (sum(pages) LTE 970)
                // New functions: round, sign
                selectStatement9 = table SELECT WHERE(round(price, 0) EQ 17.0)
                // selectStatement10 = table SELECT WHERE(sign(pages) EQ 1)
                // New string functions: substr, trim, ltrim, rtrim
                selectStatement11 = table SELECT WHERE(substr(name, 1, 6) EQ "Kotlin")
                selectStatement12 = table SELECT WHERE(trim(name) EQ "Kotlin Cookbook")
                selectStatement13 = table SELECT WHERE(ltrim(name) EQ "Kotlin Cookbook")
                selectStatement14 = table SELECT WHERE(rtrim(name) EQ "Kotlin Cookbook")
                // New string functions: replace, instr
                selectStatement15 = table SELECT WHERE(instr(name, "Kotlin") GT 0)
                selectStatement16 = table SELECT WHERE(replace(author, "Brown", "Smith") EQ "Dan Smith")
                // Test random function (just check it returns results)
                selectStatement17 = table SELECT ORDER_BY(random()) LIMIT 3
            }
        }
        assertEquals(book1, selectStatement0?.getResults()?.first())
        assertEquals(book1, selectStatement1?.getResults()?.first())
        assertEquals(book0, selectStatement2?.getResults()?.first())
        assertEquals(book0, selectStatement3?.getResults()?.first())
        assertEquals(1, selectStatement4?.getResults()?.size)
        assertEquals(book4.author, selectStatement5?.getResults()?.first()?.author)
        assertEquals(book0.author, selectStatement6?.getResults()?.first()?.author)
        assertEquals(book4.author, selectStatement7?.getResults()?.first()?.author)
        assertEquals(book0.author, selectStatement8?.getResults()?.first()?.author)
        // Verify new functions
        assertEquals(book0, selectStatement9?.getResults()?.first())
        // assertEquals(5, selectStatement10?.getResults()?.size) // All books have positive pages
        assertEquals(true, selectStatement11?.getResults()?.size == 2) // Kotlin Cookbook and Kotlin Guide Pratique
        assertEquals(book1, selectStatement12?.getResults()?.first())
        assertEquals(book1, selectStatement13?.getResults()?.first())
        assertEquals(book1, selectStatement14?.getResults()?.first())
        assertEquals(true, selectStatement15?.getResults()?.size == 2) // Books with "Kotlin" in name
        assertEquals(book0, selectStatement16?.getResults()?.first())
        assertEquals(3, selectStatement17?.getResults()?.size) // Random ordering, but should return 3 results
    }

    fun testJoinClause() = Database(getDefaultDBConfig(), true).databaseAutoClose { database ->
        var crossJoinStatement: SelectStatement<CrossJoiner>? = null
        var innerJoinStatement: SelectStatement<Joiner>? = null
        var naturalInnerJoinStatement: SelectStatement<Joiner>? = null
        var innerJoinStatementWithOn: SelectStatement<CrossJoiner>? = null
        var outerJoinStatement: SelectStatement<Joiner>? = null
        var naturalOuterJoinStatement: SelectStatement<Joiner>? = null
        var outerJoinStatementWithOn: SelectStatement<CrossJoiner>? = null
        val categories = listOf(
            Category(name = "The Da Vinci Code", code = 123),
            Category(name = "Kotlin Cookbook", code = 456),
        )
        val books = listOf(
            Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96),
            Book(name = "Kotlin Cookbook", author = "Ken Kousen", pages = 251, price = 37.72),
            Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95),
            Book(name = "Kotlin Guide Pratique", author = "Ken Kousen", pages = 398, price = 39.99),
            Book(name = "Modern Java Recipes", author ="Ken Kousen", pages = 322, price = 25.78),
        )
        database {
            CategoryTable { table ->
                table INSERT categories
            }
            BookTable { table ->
                table INSERT books
                crossJoinStatement = table SELECT_DISTINCT CROSS_JOIN(CategoryTable)
                innerJoinStatement = table SELECT INNER_JOIN<Joiner>(CategoryTable) USING name
                naturalInnerJoinStatement = table SELECT NATURAL_INNER_JOIN(CategoryTable)
                innerJoinStatementWithOn = table SELECT INNER_JOIN<CrossJoiner>(CategoryTable) ON (name EQ CategoryTable.name)
                outerJoinStatement = table SELECT LEFT_OUTER_JOIN<Joiner>(CategoryTable) USING name
                naturalOuterJoinStatement = table SELECT NATURAL_LEFT_OUTER_JOIN(CategoryTable)
                outerJoinStatementWithOn = table SELECT LEFT_OUTER_JOIN<CrossJoiner>(CategoryTable) ON (name EQ CategoryTable.name)
            }
        }
        assertEquals(crossJoinStatement?.getResults()?.size, categories.size * books.size)
        assertEquals(innerJoinStatement?.getResults()?.size, categories.size)
        assertEquals(naturalInnerJoinStatement?.getResults()?.size, categories.size)
        assertEquals(innerJoinStatementWithOn?.getResults()?.size, categories.size)
        assertEquals(outerJoinStatement?.getResults()?.size, books.size)
        assertEquals(naturalOuterJoinStatement?.getResults()?.size, books.size)
        assertEquals(outerJoinStatementWithOn?.getResults()?.size, books.size)
    }

    /**
     * Covers observed queries: the flow emits the SELECT's results when it is collected, and again when a statement run
     * through the database changes a table the SELECT reads and the results differ. A join watches all of its tables.
     * A change to another table, an UPDATE that matches no rows or leaves the results as they were, and a rolled back
     * transaction emit nothing, and an observed table that is dropped and created again is still watched.
     */
    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalDSLDatabaseAPI::class)
    fun testObserve() = Database(getNewAPIDBConfig()).databaseAutoClose { database ->
        runTest {
            val books = Channel<List<Book>>(Channel.UNLIMITED)
            val joined = Channel<List<Joiner>>(Channel.UNLIMITED)
            // The queries run in the test's own context, so that runCurrent() lets them catch up
            backgroundScope.launch {
                database.observe(EmptyCoroutineContext) { BookTable SELECT X }.collect { books.send(it) }
            }
            backgroundScope.launch {
                database.observe(EmptyCoroutineContext) {
                    BookTable SELECT INNER_JOIN<Joiner>(CategoryTable) USING BookTable.name
                }.collect { joined.send(it) }
            }
            fun assertNoNewBooks() {
                runCurrent()
                assertEquals(true, books.tryReceive().isFailure)
            }

            assertEquals(emptyList(), books.receive())
            assertEquals(emptyList(), joined.receive())

            val book = Book(name = "Kotlin Cookbook", author = "Ken Kousen", price = 37.72, pages = 251)
            database { BookTable INSERT book }
            assertEquals(listOf(book), books.receive())

            // Nothing new: a change to another table, an UPDATE that matches no rows, one that leaves the results as
            // they were, and a transaction that is rolled back
            database { PersonWithIdTable INSERT PersonWithId(id = null, name = "Ann", age = 30) }
            assertNoNewBooks()
            database { BookTable { table -> table UPDATE SET { pages = 1 } WHERE (name EQ "No Such Book") } }
            assertNoNewBooks()
            database { BookTable { table -> table UPDATE SET { pages = 251 } WHERE (name EQ "Kotlin Cookbook") } }
            assertNoNewBooks()
            assertFails {
                database {
                    transaction {
                        BookTable INSERT Book(name = "Rolled Back", author = "Nobody", price = 1.0, pages = 1)
                        UniqueEmailTestTable INSERT listOf(
                            UniqueEmailTest(id = null, email = "same@example.com", name = "One"),
                            UniqueEmailTest(id = null, email = "same@example.com", name = "Two"),
                        )
                    }
                }
            }
            assertNoNewBooks()

            database { BookTable { table -> table UPDATE SET { pages = 300 } WHERE (name EQ "Kotlin Cookbook") } }
            assertEquals(listOf(book.copy(pages = 300)), books.receive())

            // The join watches the category table too
            database { CategoryTable INSERT Category(name = "Kotlin Cookbook", code = 1) }
            assertEquals(listOf("Kotlin Cookbook"), joined.receive().map { it.name })

            // A dropped and created table loses its triggers, which come back
            database {
                DROP(BookTable)
                CREATE(BookTable)
            }
            assertEquals(emptyList(), books.receive())
            database { BookTable INSERT book }
            assertEquals(listOf(book), books.receive())
        }
    }

    /**
     * Covers an observed query of a table that only a foreign key action changes: SQLite reports the rows that
     * ON DELETE CASCADE deletes, though no statement names their table.
     */
    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalDSLDatabaseAPI::class)
    fun testObserveForeignKeyAction() = Database(getForeignKeyDBConfig()).databaseAutoClose { database ->
        runTest {
            database {
                PRAGMA_FOREIGN_KEYS(true)
            }
            database {
                FKUserTable INSERT FKUser(id = null, email = "alice@example.com", name = "Alice")
                FKOrderTable INSERT FKOrder(id = null, userId = 1L, amount = 99.99, orderDate = "2025-01-15")
            }
            val orders = Channel<List<FKOrder>>(Channel.UNLIMITED)
            backgroundScope.launch {
                database.observe(EmptyCoroutineContext) { FKOrderTable SELECT X }.collect { orders.send(it) }
            }
            assertEquals(1, orders.receive().size)
            database { FKUserTable DELETE WHERE(FKUserTable.id EQ 1L) }
            assertEquals(emptyList(), orders.receive())
        }
    }

    /**
     * Covers views: `CREATE_VIEW` creates one from a SELECT of its row type, with the SELECT's values written into its
     * SQL, as a view can't take parameters. A SELECT reads the view like a table, an observed query of it watches the
     * tables it reads, and `DROP` drops it.
     */
    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalDSLDatabaseAPI::class)
    fun testView() = Database(getNewAPIDBConfig()).databaseAutoClose { database ->
        runTest {
            database {
                PersonWithIdTable INSERT listOf(
                    PersonWithId(id = null, name = "Ann", age = 30),
                    PersonWithId(id = null, name = "Bob", age = 15),
                    PersonWithId(id = null, name = "Cat?", age = 40),
                    PersonWithId(id = null, name = "It's", age = 50),
                )
                // The literals of the SELECT go into the view: 18, 'Cat' and 'It''s'. The '?' in replace(name,'?','')
                // is a string, not a parameter, and stays as it is.
                PersonWithIdTable { table ->
                    CREATE_VIEW(AdultPersonView) AS (
                        table SELECT WHERE<AdultPerson>((age GTE 18) AND (replace(name, "?", "") NEQ "Cat") AND (name NEQ "It's"))
                    )
                }
            }

            lateinit var adults: SelectStatement<AdultPerson>
            lateinit var olderThan25: SelectStatement<AdultPerson>
            database {
                adults = AdultPersonView SELECT X
                AdultPersonView { view ->
                    olderThan25 = view SELECT WHERE(age GT 25)
                }
            }
            assertEquals(listOf(AdultPerson("Ann", 30)), adults.getResults())
            assertEquals(listOf(AdultPerson("Ann", 30)), olderThan25.getResults())

            // An observed query of the view watches the person table
            val observed = Channel<List<AdultPerson>>(Channel.UNLIMITED)
            backgroundScope.launch {
                database.observe(EmptyCoroutineContext) { AdultPersonView SELECT X }.collect { observed.send(it) }
            }
            assertEquals(listOf(AdultPerson("Ann", 30)), observed.receive())
            database { PersonWithIdTable INSERT PersonWithId(id = null, name = "Dan", age = 20) }
            assertEquals(listOf("Ann", "Dan"), observed.receive().map { it.name }.sorted())

            database { DROP(AdultPersonView) }
            assertEquals(true, database.selectFails { AdultPersonView SELECT X })
        }
    }

    /**
     * Covers how unsigned values are stored: as the numbers they are, which SQLite's 64-bit integers hold, so that SQL
     * compares, orders and sums them as Kotlin does, whether an INSERT or an UPDATE wrote them. Stored as the bits of
     * signed numbers, a UByte of 200 read back as 200, but compared as -56.
     */
    @OptIn(ExperimentalDSLDatabaseAPI::class)
    fun testUnsignedValues() {
        val config = DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = { CREATE(TestPrimitiveTypeForKSPTable) },
        )
        fun row(testInt: Int, uByte: UByte, uShort: UShort, uInt: UInt) = TestPrimitiveTypeForKSP(
            testInt = testInt, testLong = 1, testShort = 1, testByte = 1, testFloat = 1f, testDouble = 1.0,
            testUInt = uInt, testULong = 1u, testUShort = uShort, testUByte = uByte, testBoolean = true, testChar = 'c',
            testString = "s", testByteArray = byteArrayOf(1), testEnum = Priority.LOW, testTypeAlias = 1,
        )
        Database(config).databaseAutoClose { database ->
            database {
                TestPrimitiveTypeForKSPTable INSERT listOf(row(1, 200u, 40_000u, 3_000_000_000u), row(2, 100u, 100u, 100u))
            }
            lateinit var all: SelectStatement<TestPrimitiveTypeForKSP>
            lateinit var bigBytes: SelectStatement<TestPrimitiveTypeForKSP>
            lateinit var bigShorts: SelectStatement<TestPrimitiveTypeForKSP>
            lateinit var bigInts: SelectStatement<TestPrimitiveTypeForKSP>
            lateinit var byUInt: SelectStatement<TestPrimitiveTypeForKSP>
            lateinit var totals: SelectStatement<UIntTotals>
            database {
                TestPrimitiveTypeForKSPTable { table ->
                    all = table SELECT X
                    bigBytes = table SELECT WHERE(testUByte GT 150)
                    bigShorts = table SELECT WHERE(testUShort GT 30_000)
                    bigInts = table SELECT WHERE(testUInt GT 2_000_000_000L)
                    byUInt = table SELECT ORDER_BY(testUInt to DESC)
                    totals = table SELECT listOf(sum(testUInt) AS UIntTotals::total, max(testUInt) AS UIntTotals::highest)
                }
            }
            val first = all.getResults().single { it.testInt == 1 }
            assertEquals(200u.toUByte(), first.testUByte)
            assertEquals(40_000u.toUShort(), first.testUShort)
            assertEquals(3_000_000_000u, first.testUInt)
            assertEquals(listOf(1), bigBytes.getResults().map { it.testInt })
            assertEquals(listOf(1), bigShorts.getResults().map { it.testInt })
            assertEquals(listOf(1), bigInts.getResults().map { it.testInt })
            assertEquals(listOf(1, 2), byUInt.getResults().map { it.testInt })
            assertEquals(UIntTotals(total = 3_000_000_100L, highest = 3_000_000_000u), totals.getResults().single())

            // An UPDATE stores them the same way
            database {
                TestPrimitiveTypeForKSPTable { table ->
                    table UPDATE SET { testUByte = 250u; testUInt = 4_000_000_000u } WHERE (testInt EQ 2)
                }
            }
            lateinit var updatedBytes: SelectStatement<TestPrimitiveTypeForKSP>
            lateinit var updatedInts: SelectStatement<TestPrimitiveTypeForKSP>
            database {
                TestPrimitiveTypeForKSPTable { table ->
                    updatedBytes = table SELECT WHERE(testUByte GT 150)
                    updatedInts = table SELECT WHERE(testUInt GT 3_500_000_000L)
                }
            }
            assertEquals(listOf(1, 2), updatedBytes.getResults().map { it.testInt }.sorted())
            assertEquals(listOf(2), updatedInts.getResults().map { it.testInt })
        }
    }

    /**
     * Covers FTS4 tables: their CREATE VIRTUAL TABLE statement, rowids SQLite assigns, MATCH over the whole table and
     * over one column with terms, stems, prefixes, OR and phrases, a column that isn't indexed, snippet, offsets and
     * matchinfo, UPDATE and DELETE, an observed query, and rebuilding the table under another name.
     */
    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalDSLDatabaseAPI::class)
    fun testFts4() = Database(getFtsDBConfig()).databaseAutoClose { database ->
        runTest {
            assertEquals(
                "CREATE VIRTUAL TABLE articles USING fts4(title,body,note,tokenize=porter,prefix=\"2\",notindexed=note)",
                ArticleTable.createSQL,
            )
            database {
                ArticleTable INSERT listOf(
                    Article(rowid = null, title = "Kotlin Coroutines", body = "Running suspending functions concurrently", note = "draft"),
                    Article(rowid = null, title = "SQLite Full-Text Search", body = "Indexes documents for searching", note = "kotlin"),
                    Article(rowid = null, title = "Kotlin Multiplatform", body = "Sharing code between platforms", note = null),
                )
            }
            fun matches(block: ArticleTable.() -> SelectCondition): List<Long?> {
                lateinit var statement: SelectStatement<Article>
                database {
                    statement = ArticleTable SELECT WHERE(ArticleTable.block())
                }
                return statement.getResults().map { it.rowid }
            }
            // "kotlin" is only in a column that isn't indexed of article 2
            assertEquals(listOf(1L, 3L), matches { this MATCH "kotlin" })
            assertEquals(listOf(1L), matches { this MATCH "run" })
            assertEquals(listOf(1L), matches { this MATCH "corout*" })
            assertEquals(listOf(1L, 2L), matches { this MATCH "coroutines OR search" })
            assertEquals(listOf(3L), matches { this MATCH "\"kotlin multiplatform\"" })
            assertEquals(listOf(3L), matches { this MATCH "body:sharing" })
            assertEquals(emptyList(), matches { this MATCH "draft" })
            assertEquals(listOf(3L), matches { body MATCH "platform" })
            assertEquals(emptyList(), matches { title MATCH "code" })
            assertFailsWith<IllegalArgumentException> { BookTable.name MATCH "kotlin" }

            lateinit var all: SelectStatement<Article>
            lateinit var snippets: SelectStatement<ArticleMatch>
            lateinit var bodySnippets: SelectStatement<ArticleMatch>
            lateinit var offsets: SelectStatement<ArticleMatch>
            lateinit var matchInfo: SelectStatement<ArticleMatchInfo>
            database {
                ArticleTable { table ->
                    all = table SELECT X
                    snippets = table SELECT listOf(table.snippet() AS ArticleMatch::excerpt) WHERE (table MATCH "search")
                    bodySnippets = table SELECT listOf(table.snippet("[", "]", column = 1) AS ArticleMatch::excerpt) WHERE (table MATCH "run")
                    offsets = table SELECT listOf(table.offsets() AS ArticleMatch::excerpt) WHERE (table MATCH "coroutines")
                    matchInfo = table SELECT listOf(table.matchinfo() AS ArticleMatchInfo::info) WHERE (table MATCH "kotlin")
                }
            }
            assertEquals("draft", all.getResults().first().note)
            assertEquals(listOf(ArticleMatch("SQLite Full-Text Search", "SQLite Full-Text <b>Search</b>")), snippets.getResults())
            assertEquals(listOf(ArticleMatch("Kotlin Coroutines", "[Running] suspending functions concurrently")), bodySnippets.getResults())
            // Column 0, term 0 of the query, at byte 7, 10 bytes long
            assertEquals(listOf(ArticleMatch("Kotlin Coroutines", "0 0 7 10")), offsets.getResults())
            // "pcx": 1 phrase, 3 columns, and 3 numbers for each of them, of 4 bytes each
            assertEquals(listOf(44, 44), matchInfo.getResults().map { it.info.size })

            database {
                ArticleTable { table ->
                    table UPDATE SET { body = "Structured concurrency" } WHERE (rowid EQ 1L)
                    table DELETE WHERE (table MATCH "multiplatform")
                }
            }
            assertEquals(listOf(1L), matches { this MATCH "structured" })
            assertEquals(emptyList(), matches { this MATCH "running" })
            assertEquals(listOf(1L), matches { this MATCH "kotlin" })

            // A virtual table has no triggers, but its observed queries see SQLlin's statements that write to it
            val kotlinArticles = Channel<List<Article>>(Channel.UNLIMITED)
            backgroundScope.launch {
                database.observe(EmptyCoroutineContext) { ArticleTable SELECT WHERE(ArticleTable MATCH "kotlin") }
                    .collect { kotlinArticles.send(it) }
            }
            assertEquals(listOf(1L), kotlinArticles.receive().map { it.rowid })
            database { ArticleTable INSERT Article(rowid = null, title = "Kotlin Flows", body = "Cold streams", note = null) }
            assertEquals(listOf("Kotlin Coroutines", "Kotlin Flows"), kotlinArticles.receive().map { it.title })
            database { BookTable INSERT Book(name = "Kotlin in Action", author = "Dmitry Jemerov", price = 40.0, pages = 360) }
            runCurrent()
            assertEquals(true, kotlinArticles.tryReceive().isFailure)

            // Rebuilt under another name, with its rowids, and renamed back, it is still watched. The rebuild itself
            // leaves the results as they were, so it emits nothing.
            val newArticles = ArticleTable.withName("articles_new")
            assertEquals(true, newArticles is FtsTable<*>)
            assertEquals(ArticleTable.createSQL.replace(" articles ", " articles_new "), newArticles.createSQL)
            database {
                CREATE(newArticles)
                newArticles INSERT (ArticleTable SELECT X)
                DROP(ArticleTable)
                "articles_new" ALTER_RENAME_TABLE_TO ArticleTable
            }
            database { ArticleTable DELETE WHERE (ArticleTable MATCH "flows") }
            assertEquals(listOf(1L), kotlinArticles.receive().map { it.rowid })
        }
    }

    /**
     * Covers FTS3 tables: a tokenizer with an argument, which here removes diacritics, and a rowid named docid that the
     * caller supplies, through INSERT, INSERT_OR_REPLACE, UPDATE and DELETE.
     */
    @OptIn(ExperimentalDSLDatabaseAPI::class)
    fun testFts3() = Database(getFtsDBConfig()).databaseAutoClose { database ->
        assertEquals("CREATE VIRTUAL TABLE notes USING fts3(text,tokenize=unicode61 \"remove_diacritics=1\")", NoteTable.createSQL)
        fun matches(block: NoteTable.() -> SelectCondition): List<Note> {
            lateinit var statement: SelectStatement<Note>
            database {
                statement = NoteTable SELECT WHERE(NoteTable.block())
            }
            return statement.getResults()
        }
        database {
            NoteTable INSERT listOf(Note(docid = 5, text = "Crème brûlée"), Note(docid = 9, text = "Café au lait"))
        }
        assertEquals(listOf(Note(docid = 5, text = "Crème brûlée")), matches { this MATCH "creme" })
        assertEquals(listOf(9L), matches { text MATCH "cafe" }.map { it.docid })

        database {
            NoteTable { table ->
                table INSERT_OR_REPLACE Note(docid = 5, text = "Tarte Tatin")
                table UPDATE SET { text = "Crêpe" } WHERE (docid EQ 9L)
            }
        }
        assertEquals(listOf(5L), matches { this MATCH "tatin" }.map { it.docid })
        assertEquals(emptyList(), matches { this MATCH "creme" })
        assertEquals(listOf(9L), matches { this MATCH "crepe" }.map { it.docid })

        database { NoteTable DELETE WHERE (NoteTable MATCH "tatin") }
        lateinit var all: SelectStatement<Note>
        database {
            all = NoteTable SELECT X
        }
        assertEquals(listOf(Note(docid = 9, text = "Crêpe")), all.getResults())
    }

    /**
     * Covers CHECK constraints: their SQL, with a column's unnamed ones before its named ones, rows that pass them,
     * NULL included, rows that fail one, which SQLite reports by its name, an UPDATE that fails one and leaves the row
     * as it was, and an INSERT_OR_IGNORE that skips the rows failing one. SQLite reports an unnamed one by its
     * expression from 3.34 on, and by the table's name before, as on Android below API 34.
     */
    @OptIn(ExperimentalDSLDatabaseAPI::class)
    fun testCheckConstraint() {
        assertEquals(
            "CREATE TABLE CheckedMembership(id INTEGER PRIMARY KEY," +
                "name TEXT NOT NULL CHECK (length(trim(name)) > 0) CONSTRAINT lower_case_name CHECK (lower(name) = name)," +
                "age INT CHECK (age BETWEEN 0 AND 150),level INT NOT NULL CHECK (level IN (0, 1, 2)),startDate TEXT NOT NULL," +
                "endDate TEXT CHECK (endDate IS NULL OR date(endDate) IS endDate)," +
                "CONSTRAINT valid_period CHECK (endDate IS NULL OR endDate >= startDate))",
            CheckedMembershipTable.createSQL,
        )
        val config = DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = { CREATE(CheckedMembershipTable) },
        )
        fun member(name: String = "ann", age: Int? = 30, level: Int = 0, endDate: String? = null) =
            CheckedMembership(id = null, name = name, age = age, level = level, startDate = "2026-01-01", endDate = endDate)
        Database(config).databaseAutoClose { database ->
            fun failure(block: DatabaseScope.() -> Unit): String = assertFails { database(block) }.message.orEmpty()
            fun assertReports(message: String, name: String?, expression: String?) {
                val reported = if (name != null)
                    name in message
                else
                    expression!! in message || "CHECK constraint failed: CheckedMembership" in message
                assertEquals(true, reported, "'$message' doesn't report the constraint '${name ?: expression}'")
            }
            fun names(): List<String> {
                lateinit var statement: SelectStatement<CheckedMembership>
                database {
                    statement = CheckedMembershipTable SELECT X
                }
                return statement.getResults().map { it.name }
            }

            // NULL passes a constraint, as it isn't false
            database {
                CheckedMembershipTable INSERT listOf(member(), member(name = "bob", age = null, level = 2, endDate = "2026-12-31"))
            }
            assertEquals(listOf("ann", "bob"), names())

            // The rows failing a named constraint, and those failing an unnamed one, with its expression
            val namedFailures = mapOf(
                member(name = "Cat") to "lower_case_name",
                member(endDate = "2025-12-31") to "valid_period",
            )
            val unnamedFailures = mapOf(
                member(name = "   ") to "length(trim(name)) > 0",
                member(age = 200) to "age BETWEEN 0 AND 150",
                member(level = 3) to "level IN (0, 1, 2)",
                member(endDate = "2026-13-01") to "date(endDate) IS endDate",
            )
            for ((row, name) in namedFailures)
                assertReports(failure { CheckedMembershipTable INSERT row }, name = name, expression = null)
            for ((row, expression) in unnamedFailures)
                assertReports(failure { CheckedMembershipTable INSERT row }, name = null, expression = expression)
            assertEquals(listOf("ann", "bob"), names())

            val message = failure {
                CheckedMembershipTable { table -> table UPDATE SET { level = 5 } WHERE (name EQ "ann") }
            }
            assertReports(message, name = null, expression = "level IN (0, 1, 2)")
            lateinit var levels: SelectStatement<CheckedMembership>
            database {
                levels = CheckedMembershipTable SELECT ORDER_BY(CheckedMembershipTable.name)
            }
            assertEquals(listOf(0, 2), levels.getResults().map { it.level })

            database {
                CheckedMembershipTable INSERT_OR_IGNORE listOf(member(name = "dan"), member(name = "Eve"), member(name = "fay", age = -1))
            }
            assertEquals(listOf("ann", "bob", "dan"), names())
        }
    }

    /**
     * Covers subqueries: derived tables in FROM and in a join, one joining a table with itself, IN with a subquery
     * combined with other conditions, correlated EXISTS, NOT, subqueries in UPDATE and DELETE, IN subqueries that don't
     * fit, and observed queries that watch the tables their subqueries read. Parameters of the subqueries and of the
     * queries they are in are bound in the order the SQL has them.
     */
    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalDSLDatabaseAPI::class)
    fun testSubquery() = Database(getNewAPIDBConfig()).databaseAutoClose { database ->
        runTest {
            database {
                BookTable INSERT listOf(
                    Book(name = "The Da Vinci Code", author = "Dan Brown", price = 16.96, pages = 454),
                    Book(name = "The Lost Symbol", author = "Dan Brown", price = 19.95, pages = 510),
                    Book(name = "Kotlin Cookbook", author = "Ken Kousen", price = 37.72, pages = 251),
                    Book(name = "Cheap Book", author = "Dan Brown", price = 5.0, pages = 10),
                )
                PersonWithIdTable INSERT listOf(
                    PersonWithId(id = null, name = "Dan Brown", age = 61),
                    PersonWithId(id = null, name = "Ken Kousen", age = 60),
                    PersonWithId(id = null, name = "Ann", age = 30),
                )
            }

            lateinit var prolific: SelectStatement<AuthorBooks>
            lateinit var personBooks: SelectStatement<PersonBooks>
            lateinit var pairs: SelectStatement<YoungerAndElder>
            database {
                // The books over 10 of each author; a derived table can be read by several queries
                val authorBooks = BookTable { table ->
                    table SELECT listOf(count(X) AS AuthorBooks::books) WHERE (price GT 10.0) GROUP_BY author
                } AS AuthorBooksView
                prolific = authorBooks SELECT WHERE(AuthorBooksView.books GT 1)
                personBooks = PersonWithIdTable SELECT INNER_JOIN<PersonBooks>(authorBooks) ON (PersonWithIdTable.name EQ AuthorBooksView.author)
                // The persons under other names join their table with itself
                val elders = PersonWithIdTable { table ->
                    table SELECT listOf(name AS Elder::elderName, age AS Elder::elderAge)
                } AS ElderView
                pairs = PersonWithIdTable SELECT INNER_JOIN<YoungerAndElder>(elders) ON (ElderView.elderAge GT PersonWithIdTable.age)
            }
            assertEquals(listOf(AuthorBooks("Dan Brown", 2)), prolific.getResults())
            assertEquals(
                listOf(PersonBooks("Dan Brown", 61, 2), PersonBooks("Ken Kousen", 60, 1)),
                personBooks.getResults().sortedBy { it.name },
            )
            assertEquals(
                listOf(YoungerAndElder("Ann", "Dan Brown"), YoungerAndElder("Ann", "Ken Kousen"), YoungerAndElder("Ken Kousen", "Dan Brown")),
                pairs.getResults().sortedWith(compareBy({ it.name }, { it.elderName })),
            )

            lateinit var expensiveAuthors: SelectStatement<PersonWithId>
            lateinit var shortBookAuthors: SelectStatement<PersonWithId>
            lateinit var withoutBooks: SelectStatement<PersonWithId>
            lateinit var middleAged: SelectStatement<PersonWithId>
            database {
                PersonWithIdTable { table ->
                    expensiveAuthors = table SELECT WHERE((age GT 50) AND (name IN (BookTable SELECT WHERE<BookAuthor>(BookTable.price GT 30.0))))
                    shortBookAuthors = table SELECT WHERE(EXISTS(BookTable SELECT WHERE((BookTable.author EQ name) AND (BookTable.pages LT 300) AND (BookTable.price GT 6.0))))
                    withoutBooks = table SELECT WHERE(NOT(EXISTS(BookTable SELECT WHERE(BookTable.author EQ name))))
                    middleAged = table SELECT WHERE(NOT((age LT 40) OR (age GT 60)))
                }
            }
            assertEquals(listOf("Ken Kousen"), expensiveAuthors.getResults().map { it.name })
            assertEquals(listOf("Ken Kousen"), shortBookAuthors.getResults().map { it.name })
            assertEquals(listOf("Ann"), withoutBooks.getResults().map { it.name })
            assertEquals(listOf("Ken Kousen"), middleAged.getResults().map { it.name })

            // IN compares with a single column of values of the same kind
            assertFailsWith<IllegalArgumentException> {
                database { PersonWithIdTable SELECT WHERE(PersonWithIdTable.age IN (BookTable SELECT X<BookAuthor>())) }
            }
            assertFailsWith<IllegalArgumentException> {
                database { PersonWithIdTable SELECT WHERE(PersonWithIdTable.name IN (BookTable SELECT X<BookTitle>())) }
            }

            // Subqueries in UPDATE and DELETE, after the parameters of SET
            database {
                PersonWithIdTable { table ->
                    table UPDATE SET { age = 62 } WHERE (name IN (BookTable SELECT WHERE<BookAuthor>(BookTable.pages GT 500)))
                }
                PersonWithIdTable DELETE WHERE(NOT(PersonWithIdTable.name IN (BookTable SELECT X<BookAuthor>())))
            }
            lateinit var persons: SelectStatement<PersonWithId>
            database {
                persons = PersonWithIdTable SELECT X
            }
            assertEquals(listOf("Dan Brown" to 62, "Ken Kousen" to 60), persons.getResults().map { it.name to it.age }.sortedBy { it.first })

            // Observed queries watch the tables their derived tables and IN subqueries read
            val books = Channel<List<PersonBooks>>(Channel.UNLIMITED)
            val oldest = Channel<List<PersonWithId>>(Channel.UNLIMITED)
            backgroundScope.launch {
                database.observe(EmptyCoroutineContext) {
                    val authorBooks = BookTable { table -> table SELECT listOf(count(X) AS AuthorBooks::books) GROUP_BY author } AS AuthorBooksView
                    PersonWithIdTable SELECT INNER_JOIN<PersonBooks>(authorBooks) ON (PersonWithIdTable.name EQ AuthorBooksView.author)
                }.collect { books.send(it) }
            }
            backgroundScope.launch {
                database.observe(EmptyCoroutineContext) {
                    PersonWithIdTable { table -> table SELECT WHERE(age IN (table SELECT listOf(max(age) AS PersonAge::age))) }
                }.collect { oldest.send(it) }
            }
            assertEquals(listOf(3L, 1L), books.receive().sortedBy { it.name }.map { it.books })
            assertEquals(listOf("Dan Brown"), oldest.receive().map { it.name })
            database { BookTable INSERT Book(name = "Inferno", author = "Ken Kousen", price = 18.0, pages = 480) }
            assertEquals(listOf(3L, 2L), books.receive().sortedBy { it.name }.map { it.books })
            database { PersonWithIdTable INSERT PersonWithId(id = null, name = "Old Timer", age = 99) }
            assertEquals(listOf("Old Timer"), oldest.receive().map { it.name })
        }
    }

    /**
     * Covers conditions combined with AND and OR, which apply in the order they are written, as Kotlin's infix functions
     * do, though SQL gives AND precedence over OR: a condition that combines others with the other operator is put in
     * parentheses, while one that combines them with the same operator isn't.
     */
    @OptIn(ExperimentalDSLDatabaseAPI::class)
    fun testConditionPrecedence() = Database(getNewAPIDBConfig()).databaseAutoClose { database ->
        database {
            PersonWithIdTable INSERT listOf(
                PersonWithId(id = null, name = "a", age = 1),
                PersonWithId(id = null, name = "x", age = 2),
                PersonWithId(id = null, name = "y", age = 1),
                PersonWithId(id = null, name = "x", age = 3),
            )
        }
        fun select(condition: PersonWithIdTable.() -> SelectCondition): SelectStatement<PersonWithId> {
            lateinit var statement: SelectStatement<PersonWithId>
            database {
                statement = PersonWithIdTable SELECT WHERE(PersonWithIdTable.condition())
            }
            return statement
        }
        fun names(condition: PersonWithIdTable.() -> SelectCondition): List<String> =
            select(condition).getResults().map { it.name }.sorted()

        val statement = select { (age EQ 1) OR (age EQ 2) AND (name EQ "x") }
        assertEquals(
            "SELECT id,name,age FROM person_with_id WHERE (person_with_id.age=? OR person_with_id.age=?) AND person_with_id.name=?",
            statement.sqlStr,
        )
        assertEquals(listOf("x"), statement.getResults().map { it.name })
        assertEquals(listOf("a", "x", "y"), names { (age EQ 1) OR ((age EQ 2) AND (name EQ "x")) })
        assertEquals(listOf("x"), names { (name EQ "x") AND ((age EQ 2) OR (age EQ 1)) })
        assertEquals(listOf("a", "x", "y"), names { (name EQ "x") AND (age EQ 3) OR (age EQ 1) })
        assertEquals(listOf("a", "x", "x", "y"), names { (age EQ 1) OR (age EQ 2) OR (age EQ 3) })
        assertEquals(listOf("x"), names { NOT((age EQ 1) OR (age EQ 2)) AND (name EQ "x") })
    }

    /**
     * Covers a condition kept in a variable and used in several statements: combining it with others leaves its own
     * parameters as they were, so that each statement binds the parameters its SQL has.
     */
    fun testConditionReuse() = Database(getNewAPIDBConfig()).databaseAutoClose { database ->
        database {
            PersonWithIdTable INSERT listOf(
                PersonWithId(id = null, name = "a", age = 1),
                PersonWithId(id = null, name = "x", age = 2),
                PersonWithId(id = null, name = "y", age = 3),
            )
        }
        val older = PersonWithIdTable.age GTE 2
        lateinit var combined: SelectStatement<PersonWithId>
        lateinit var alone: SelectStatement<PersonWithId>
        lateinit var either: SelectStatement<PersonWithId>
        database {
            PersonWithIdTable { table ->
                combined = table SELECT WHERE(older AND (name EQ "x"))
                alone = table SELECT WHERE(older)
                either = table SELECT WHERE(older OR (name EQ "a"))
            }
        }
        assertEquals(listOf("x"), combined.getResults().map { it.name })
        assertEquals(listOf("x", "y"), alone.getResults().map { it.name }.sorted())
        assertEquals(listOf("a", "x", "y"), either.getResults().map { it.name }.sorted())
    }

    /**
     * Covers joins of relations: USING and NATURAL joins, which merge the columns of the same name, result columns with
     * GROUP BY on an outer join, three joined tables, CROSS JOIN, a derived table joined with parameters in the
     * subquery, the ON condition and WHERE, the checks of the type rows are read into, RIGHT and FULL OUTER JOIN where
     * SQLite has them, and an observed query of a join.
     */
    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalDSLDatabaseAPI::class, PlatformDependentSQLiteAPI::class)
    fun testJoinedRelation() = Database(getNewAPIDBConfig()).databaseAutoClose { database ->
        runTest {
            database {
                BookTable INSERT listOf(
                    Book(name = "The Da Vinci Code", author = "Dan Brown", price = 16.96, pages = 454),
                    Book(name = "The Lost Symbol", author = "Dan Brown", price = 19.95, pages = 510),
                    Book(name = "Kotlin Cookbook", author = "Ken Kousen", price = 37.72, pages = 251),
                )
                CategoryTable INSERT listOf(
                    Category(name = "The Da Vinci Code", code = 1),
                    Category(name = "Kotlin Cookbook", code = 2),
                    Category(name = "Unwritten", code = 3),
                )
                PersonWithIdTable INSERT listOf(
                    PersonWithId(id = null, name = "Dan Brown", age = 61),
                    PersonWithId(id = null, name = "Ken Kousen", age = 60),
                    PersonWithId(id = null, name = "Ann", age = 30),
                )
            }

            lateinit var using: SelectStatement<Joiner>
            lateinit var natural: SelectStatement<Joiner>
            lateinit var stats: SelectStatement<PersonBookStats>
            lateinit var titles: SelectStatement<PersonBookTitle>
            lateinit var codes: SelectStatement<AuthorCode>
            lateinit var crossCount: SelectStatement<BookCount>
            lateinit var prolific: SelectStatement<PersonBooks>
            database {
                // USING and NATURAL merge 'name', which book and category both have
                using = (FROM(BookTable) INNER_JOIN CategoryTable USING BookTable.name) SELECT WHERE<Joiner>(CategoryTable.code GT 0)
                natural = (FROM(BookTable) NATURAL_JOIN CategoryTable) SELECT X<Joiner>()
                // Ann has no books: count is 0, while max is NULL
                stats = (FROM(PersonWithIdTable) LEFT_OUTER_JOIN BookTable ON (PersonWithIdTable.name EQ BookTable.author)) SELECT listOf(
                    PersonWithIdTable.name AS PersonBookStats::name,
                    PersonWithIdTable.count(BookTable.name) AS PersonBookStats::books,
                    PersonWithIdTable.max(BookTable.price) AS PersonBookStats::highest,
                ) GROUP_BY PersonWithIdTable.name
                titles = (FROM(PersonWithIdTable) LEFT_OUTER_JOIN BookTable ON (PersonWithIdTable.name EQ BookTable.author)) SELECT listOf(
                    BookTable.name AS PersonBookTitle::title,
                ) WHERE (PersonWithIdTable.age LT 40)
                codes = (
                    FROM(PersonWithIdTable)
                        INNER_JOIN BookTable ON (PersonWithIdTable.name EQ BookTable.author)
                        INNER_JOIN CategoryTable ON (BookTable.name EQ CategoryTable.name)
                ) SELECT X<AuthorCode>()
                crossCount = (FROM(BookTable) CROSS_JOIN CategoryTable) SELECT listOf(BookTable.count(X) AS BookCount::books)
                // Parameters of the derived table, of ON and of WHERE, in this order
                val authorBooks = BookTable { table ->
                    table SELECT listOf(count(X) AS AuthorBooks::books) WHERE (price GT 10.0) GROUP_BY author
                } AS AuthorBooksView
                prolific = (FROM(PersonWithIdTable) INNER_JOIN authorBooks ON ((PersonWithIdTable.name EQ AuthorBooksView.author) AND (AuthorBooksView.books GT 1))) SELECT WHERE<PersonBooks>(PersonWithIdTable.age GT 50)
            }
            assertEquals(listOf("Kotlin Cookbook" to 2, "The Da Vinci Code" to 1), using.getResults().map { it.name to it.code }.sortedBy { it.first })
            assertEquals(using.getResults().sortedBy { it.name }, natural.getResults().sortedBy { it.name })
            assertEquals(
                listOf(PersonBookStats("Ann", 0, null), PersonBookStats("Dan Brown", 2, 19.95), PersonBookStats("Ken Kousen", 1, 37.72)),
                stats.getResults().sortedBy { it.name },
            )
            assertEquals(listOf(PersonBookTitle(30, null)), titles.getResults())
            assertEquals(listOf(AuthorCode(60, 2), AuthorCode(61, 1)), codes.getResults().sortedBy { it.age })
            assertEquals(listOf(BookCount(9)), crossCount.getResults())
            assertEquals(listOf(PersonBooks("Dan Brown", 61, 2)), prolific.getResults())

            // The type rows are read into is checked against the joined relations
            val ambiguous = assertFailsWith<IllegalArgumentException> {
                database { (FROM(BookTable) INNER_JOIN CategoryTable ON (BookTable.name EQ CategoryTable.name)) SELECT X<Joiner>() }
            }
            assertEquals(true, "can't be told apart" in ambiguous.message!!, ambiguous.message)
            val outerColumn = assertFailsWith<IllegalArgumentException> {
                database { (FROM(PersonWithIdTable) LEFT_OUTER_JOIN BookTable ON (PersonWithIdTable.name EQ BookTable.author)) SELECT X<BookAuthor>() }
            }
            assertEquals(true, "outer join" in outerColumn.message!!, outerColumn.message)
            val outerExpression = assertFailsWith<IllegalArgumentException> {
                database {
                    (FROM(PersonWithIdTable) LEFT_OUTER_JOIN BookTable ON (PersonWithIdTable.name EQ BookTable.author)) SELECT listOf(BookTable.name AS NameOnly::name)
                }
            }
            assertEquals(true, "outer join" in outerExpression.message!!, outerExpression.message)
            assertFailsWith<IllegalArgumentException> {
                database { (FROM(BookTable) INNER_JOIN BookTable ON (BookTable.name EQ BookTable.name)) SELECT X<BookAuthor>() }
            }
            val otherTable = assertFailsWith<IllegalArgumentException> {
                database { (FROM(BookTable) CROSS_JOIN CategoryTable) SELECT listOf(PersonWithIdTable.name AS NameOnly::name) }
            }
            assertEquals(true, "belongs to table 'person_with_id'" in otherTable.message!!, otherTable.message)

            // RIGHT and FULL OUTER JOIN need SQLite 3.39.0, which Android has from API 34 on
            val outerJoins = try {
                lateinit var right: SelectStatement<Joiner>
                lateinit var full: SelectStatement<Joiner>
                database {
                    right = (FROM(BookTable) RIGHT_OUTER_JOIN CategoryTable USING BookTable.name) SELECT X<Joiner>()
                    full = (FROM(BookTable) FULL_OUTER_JOIN CategoryTable USING BookTable.name) SELECT X<Joiner>()
                }
                right.getResults() to full.getResults()
            } catch (e: Exception) {
                assertEquals(true, "RIGHT and FULL OUTER JOINs are not currently supported" in e.message.orEmpty(), e.message)
                null
            }
            outerJoins?.let { (right, full) ->
                assertEquals(listOf(1, 2, 3), right.map { it.code }.sortedBy { it })
                assertEquals(listOf(null), right.filter { it.code == 3 }.map { it.author })
                assertEquals(4, full.size)
            }

            // An observed query of a join watches all its tables
            val observed = Channel<List<PersonBookStats>>(Channel.UNLIMITED)
            backgroundScope.launch {
                database.observe(EmptyCoroutineContext) {
                    (FROM(PersonWithIdTable) LEFT_OUTER_JOIN BookTable ON (PersonWithIdTable.name EQ BookTable.author)) SELECT listOf(
                        PersonWithIdTable.name AS PersonBookStats::name,
                        PersonWithIdTable.count(BookTable.name) AS PersonBookStats::books,
                        PersonWithIdTable.max(BookTable.price) AS PersonBookStats::highest,
                    ) GROUP_BY PersonWithIdTable.name
                }.collect { observed.send(it) }
            }
            assertEquals(0L, observed.receive().single { it.name == "Ann" }.books)
            database { BookTable INSERT Book(name = "Ann's Book", author = "Ann", price = 9.0, pages = 99) }
            assertEquals(1L, observed.receive().single { it.name == "Ann" }.books)
        }
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun testConcurrency() = Database(getDefaultDBConfig(), true).databaseAutoClose { database ->
        runTest {
            val book1 = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96)
            val book2 = Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95)
            launch(newSingleThreadContext("test0")) {
                lateinit var statement: SelectStatement<Book>
                database suspendedScope {
                    statement = BookTable { table ->
                        table INSERT listOf(book1, book2)
                        table SELECT X
                    }
                }
                assertEquals(true, statement.getResults().any { it == book1 })
                assertEquals(true, statement.getResults().any { it == book2 })
            }
            launch(newSingleThreadContext("test1")) {
                val book1NewPrice = 18.96
                val book2NewPrice = 21.95
                val newBook1 = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = book1NewPrice)
                val newBook2 = Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = book2NewPrice)
                lateinit var statement: SelectStatement<Book>
                database suspendedScope {
                    statement = transaction {
                        BookTable { table ->
                            table INSERT listOf(newBook1, newBook2)
                            table SELECT X
                        }
                    }
                }
                assertEquals(true, statement.getResults().any { it == newBook1 })
                assertEquals(true, statement.getResults().any { it == newBook2 })
            }
        }
    }

    fun testPrimitiveTypeForKSP() {
        TestPrimitiveTypeForKSPTable {
            SET {
                assertEquals(0, testInt)
                assertEquals(0L, testLong)
                assertEquals(0, testShort)
                assertEquals(0, testByte)
                assertEquals(0F, testFloat)
                assertEquals(0.0, testDouble)
                assertEquals(0U, testUInt)
                assertEquals(0UL, testULong)
                assertEquals(0U, testUShort)
                assertEquals(0U, testUByte)
                assertEquals(false, testBoolean)
                assertEquals('0', testChar)
                assertEquals("", testString)
            }
        }
    }

    fun testNullValue() {
        val config = DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = {
                CREATE(NullTesterTable)
            }
        )
        Database(config, true).databaseAutoClose { database ->
            lateinit var selectStatement: SelectStatement<NullTester>
            // INSERT & SELECT
            database {
                selectStatement = NullTesterTable { table ->
                    table INSERT listOf(
                        NullTester(null, null, null),
                        NullTester(8, "888", 8.8),
                    )
                    table SELECT X
                }
            }

            selectStatement.getResults().forEachIndexed { i, tester ->
                when (i) {
                    0 -> {
                        assertEquals(null, tester.paramInt)
                        assertEquals(null, tester.paramString)
                        assertEquals(null, tester.paramDouble)
                    }
                    1 -> {
                        assertEquals(8, tester.paramInt)
                        assertEquals("888", tester.paramString)
                        assertEquals(8.8, tester.paramDouble)
                    }
                }
            }

            // UPDATE & SELECT
            database {
                selectStatement = NullTesterTable { table ->
                    table UPDATE SET { paramString = null } WHERE (paramDouble EQ 8.8)
                    table SELECT WHERE (paramInt NEQ null)
                }
            }
            val result1 = selectStatement.getResults().first()
            assertEquals(1, selectStatement.getResults().size)
            assertEquals(8, result1.paramInt)
            assertEquals(null, result1.paramString)
            assertEquals(8.8, result1.paramDouble)

            // DELETE & SELECT
            database {
                selectStatement = NullTesterTable { table ->
                    table DELETE WHERE (paramInt EQ null OR (paramDouble EQ null))
                    table SELECT X
                }
            }
            val result2 = selectStatement.getResults().first()
            assertEquals(1, selectStatement.getResults().size)
            assertEquals(8, result2.paramInt)
            assertEquals(null, result2.paramString)
            assertEquals(8.8, result2.paramDouble)
        }
    }

    fun testPrimaryKeyVariations() {
        Database(getNewAPIDBConfig()).databaseAutoClose { database ->
            // Test 1: Long primary key
            val person1 = PersonWithId(id = null, name = "Alice", age = 25)
            val person2 = PersonWithId(id = null, name = "Bob", age = 30)

            lateinit var personStatement: SelectStatement<PersonWithId>
            database {
                PersonWithIdTable { table ->
                    table INSERT listOf(person1, person2)
                    personStatement = table SELECT X
                }
            }

            val personResults = personStatement.getResults()
            assertEquals(2, personResults.size)
            assertEquals("Alice", personResults[0].name)
            assertEquals(25, personResults[0].age)
            assertEquals("Bob", personResults[1].name)
            assertEquals(30, personResults[1].age)

            // Test 2: String primary key
            val product1 = Product(sku = "SKU-WIDGET", name = "Widget", price = 19.99)
            val product2 = Product(sku = "SKU-GADGET", name = "Gadget", price = 29.99)

            lateinit var productStatement: SelectStatement<Product>
            database {
                ProductTable { table ->
                    table INSERT listOf(product1, product2)
                    productStatement = table SELECT X
                }
            }

            val productResults = productStatement.getResults()
            assertEquals(2, productResults.size)
            assertEquals("SKU-WIDGET", productResults[0].sku)
            assertEquals("Widget", productResults[0].name)
            assertEquals(19.99, productResults[0].price)
            assertEquals("SKU-GADGET", productResults[1].sku)
            assertEquals("Gadget", productResults[1].name)
            assertEquals(29.99, productResults[1].price)

            // Test 3: Autoincrement primary key
            val student1 = StudentWithAutoincrement(id = null, studentName = "Charlie", grade = 85)
            val student2 = StudentWithAutoincrement(id = null, studentName = "Diana", grade = 92)

            lateinit var studentStatement: SelectStatement<StudentWithAutoincrement>
            database {
                StudentWithAutoincrementTable { table ->
                    table INSERT listOf(student1, student2)
                    studentStatement = table SELECT X
                }
            }

            val studentResults = studentStatement.getResults()
            assertEquals(2, studentResults.size)
            assertEquals("Charlie", studentResults[0].studentName)
            assertEquals(85, studentResults[0].grade)
            assertEquals("Diana", studentResults[1].studentName)
            assertEquals(92, studentResults[1].grade)

            // Test 4: Composite primary key
            val enrollment1 = Enrollment(studentId = 1, courseId = 101, semester = "Fall 2025")
            val enrollment2 = Enrollment(studentId = 1, courseId = 102, semester = "Fall 2025")
            val enrollment3 = Enrollment(studentId = 2, courseId = 101, semester = "Fall 2025")

            lateinit var enrollmentStatement: SelectStatement<Enrollment>
            database {
                EnrollmentTable { table ->
                    table INSERT listOf(enrollment1, enrollment2, enrollment3)
                    enrollmentStatement = table SELECT X
                }
            }

            val enrollmentResults = enrollmentStatement.getResults()
            assertEquals(3, enrollmentResults.size)
            assertEquals(true, enrollmentResults.any { it == enrollment1 })
            assertEquals(true, enrollmentResults.any { it == enrollment2 })
            assertEquals(true, enrollmentResults.any { it == enrollment3 })
        }
    }

    @OptIn(AdvancedInsertAPI::class)
    fun testInsertWithId() {
        Database(getNewAPIDBConfig()).databaseAutoClose { database ->
            val person1 = PersonWithId(id = 100, name = "Eve", age = 28)
            val person2 = PersonWithId(id = 200, name = "Frank", age = 35)

            lateinit var selectStatement: SelectStatement<PersonWithId>
            database {
                PersonWithIdTable { table ->
                    table INSERT_WITH_ID listOf(person1, person2)
                    selectStatement = table SELECT X
                }
            }

            val results = selectStatement.getResults()
            assertEquals(2, results.size)
            assertEquals(100L, results[0].id)
            assertEquals("Eve", results[0].name)
            assertEquals(28, results[0].age)
            assertEquals(200L, results[1].id)
            assertEquals("Frank", results[1].name)
            assertEquals(35, results[1].age)
        }
    }

    @OptIn(AdvancedInsertAPI::class)
    fun testInsertOrReplace() {
        Database(getNewAPIDBConfig()).databaseAutoClose { database ->
            // Insert an initial entity with a known ID
            val original = PersonWithId(id = 100L, name = "Eve", age = 28)
            database {
                PersonWithIdTable { table ->
                    table INSERT_WITH_ID original
                }
            }

            lateinit var selectStatement: SelectStatement<PersonWithId>
            database {
                selectStatement = PersonWithIdTable SELECT X
            }
            assertEquals(1, selectStatement.getResults().size)
            assertEquals(100L, selectStatement.getResults().first().id)
            assertEquals("Eve", selectStatement.getResults().first().name)

            // INSERT_OR_REPLACE with the same PK — should replace the existing row
            val replacement = PersonWithId(id = 100L, name = "Eve Updated", age = 29)
            database {
                PersonWithIdTable { table ->
                    table INSERT_OR_REPLACE replacement
                }
            }

            database {
                selectStatement = PersonWithIdTable SELECT X
            }
            val resultsAfterReplace = selectStatement.getResults()
            assertEquals(1, resultsAfterReplace.size)
            assertEquals(100L, resultsAfterReplace.first().id)
            assertEquals("Eve Updated", resultsAfterReplace.first().name)
            assertEquals(29, resultsAfterReplace.first().age)

            // INSERT_OR_REPLACE with a new entity (null ID) — should insert without conflict
            val newEntity = PersonWithId(id = null, name = "Frank", age = 35)
            database {
                PersonWithIdTable { table ->
                    table INSERT_OR_REPLACE newEntity
                }
            }

            database {
                selectStatement = PersonWithIdTable SELECT X
            }
            assertEquals(2, selectStatement.getResults().size)
            assertEquals(true, selectStatement.getResults().any { it.name == "Frank" })
        }
    }

    @OptIn(AdvancedInsertAPI::class)
    fun testInsertOrIgnore() {
        Database(getNewAPIDBConfig()).databaseAutoClose { database ->
            // A conflict on the primary key leaves the existing row exactly as it is, while the entities that
            // don't conflict are inserted. Seeing the conflict at all depends on the key being written.
            database {
                PersonWithIdTable INSERT_WITH_ID PersonWithId(id = 100L, name = "Eve", age = 28)
            }
            database {
                PersonWithIdTable INSERT_OR_IGNORE listOf(
                    PersonWithId(id = 100L, name = "Eve Updated", age = 29),
                    PersonWithId(id = 101L, name = "Grace", age = 30),
                )
            }
            lateinit var people: SelectStatement<PersonWithId>
            database {
                people = PersonWithIdTable SELECT X
            }
            assertEquals(2, people.getResults().size)
            val eve = people.getResults().first { it.id == 100L }
            assertEquals("Eve", eve.name)
            assertEquals(28, eve.age)
            assertEquals("Grace", people.getResults().first { it.id == 101L }.name)

            // A null ID is still assigned by the database, so it can't conflict on the primary key
            database {
                PersonWithIdTable INSERT_OR_IGNORE PersonWithId(id = null, name = "Frank", age = 35)
            }
            database {
                people = PersonWithIdTable SELECT X
            }
            assertEquals(3, people.getResults().size)
            assertNotEquals(null, people.getResults().first { it.name == "Frank" }.id)

            // A conflict on a UNIQUE column other than the key is ignored as well
            database {
                UniqueEmailTestTable INSERT UniqueEmailTest(id = null, email = "ivy@example.com", name = "Ivy")
                UniqueEmailTestTable INSERT_OR_IGNORE UniqueEmailTest(id = null, email = "ivy@example.com", name = "Ivy Again")
            }
            lateinit var accounts: SelectStatement<UniqueEmailTest>
            database {
                accounts = UniqueEmailTestTable SELECT X
            }
            assertEquals(1, accounts.getResults().size)
            assertEquals("Ivy", accounts.getResults().first().name)

            // With a composite key, inserting a pair again keeps its existing row, other columns included,
            // which is what tells INSERT_OR_IGNORE apart from INSERT_OR_REPLACE
            database {
                EnrollmentTable INSERT Enrollment(studentId = 1, courseId = 101, semester = "Spring")
            }
            database {
                EnrollmentTable INSERT_OR_IGNORE listOf(
                    Enrollment(studentId = 1, courseId = 101, semester = "Fall"),
                    Enrollment(studentId = 1, courseId = 102, semester = "Fall"),
                )
            }
            lateinit var enrollments: SelectStatement<Enrollment>
            database {
                enrollments = EnrollmentTable SELECT X
            }
            assertEquals(2, enrollments.getResults().size)
            assertEquals("Spring", enrollments.getResults().first { it.courseId == 101L }.semester)
            assertEquals("Fall", enrollments.getResults().first { it.courseId == 102L }.semester)
        }
    }

    /**
     * Covers projection: a SELECT that reads rows into a narrower @Serializable type than the table's own row type,
     * given to the clause function, as in `X<BookTitle>()` or `WHERE<BookTitle>(...)`. Only the columns that type's
     * properties name are selected, and a type that doesn't fit the table is rejected while the statement is built.
     */
    fun testProjection() = Database(getNewAPIDBConfig()).databaseAutoClose { database ->
        database {
            BookTable INSERT listOf(
                Book(name = "The Da Vinci Code", author = "Dan Brown", price = 16.96, pages = 454),
                Book(name = "The Lost Symbol", author = "Dan Brown", price = 19.95, pages = 510),
                Book(name = "Kotlin Cookbook", author = "Ken Kousen", price = 37.72, pages = 251),
            )
        }

        // No clause: SELECT name,author FROM book, and SELECT DISTINCT author FROM book. Three books but two authors,
        // which only holds if DISTINCT compares the projected column alone, so nothing else is selected.
        lateinit var titles: SelectStatement<BookTitle>
        lateinit var authors: SelectStatement<BookAuthor>
        database {
            titles = BookTable SELECT X<BookTitle>()
            authors = BookTable SELECT_DISTINCT X<BookAuthor>()
        }
        assertEquals(3, titles.getResults().size)
        assertEquals(true, BookTitle("Kotlin Cookbook", "Ken Kousen") in titles.getResults())
        assertEquals(listOf("Dan Brown", "Ken Kousen"), authors.getResults().map { it.author }.sorted())

        // Each clause can start a projection, and the projection carries through the rest of the chain
        lateinit var longestByBrown: SelectStatement<BookTitle>
        lateinit var byPages: SelectStatement<BookTitle>
        lateinit var firstTwo: SelectStatement<BookTitle>
        lateinit var grouped: SelectStatement<BookAuthor>
        database {
            BookTable { table ->
                longestByBrown = table SELECT WHERE<BookTitle>(author EQ "Dan Brown") ORDER_BY (pages to DESC) LIMIT 1
                byPages = table SELECT ORDER_BY<BookTitle>(pages to ASC)
                firstTwo = table SELECT LIMIT<BookTitle>(2)
                grouped = table SELECT GROUP_BY<BookAuthor>(author)
            }
        }
        assertEquals(listOf(BookTitle("The Lost Symbol", "Dan Brown")), longestByBrown.getResults())
        assertEquals(listOf("Kotlin Cookbook", "The Da Vinci Code", "The Lost Symbol"), byPages.getResults().map { it.name })
        assertEquals(2, firstTwo.getResults().size)
        assertEquals(listOf("Dan Brown", "Ken Kousen"), grouped.getResults().map { it.author }.sorted())

        // The DISTINCT variant of each clause
        lateinit var distinctWhere: SelectStatement<BookAuthor>
        lateinit var distinctOrderBy: SelectStatement<BookAuthor>
        lateinit var distinctLimit: SelectStatement<BookAuthor>
        lateinit var distinctGroupBy: SelectStatement<BookAuthor>
        database {
            BookTable { table ->
                distinctWhere = table SELECT_DISTINCT WHERE<BookAuthor>(price GT 10.0)
                distinctOrderBy = table SELECT_DISTINCT ORDER_BY<BookAuthor>(author to DESC)
                distinctLimit = table SELECT_DISTINCT LIMIT<BookAuthor>(1)
                distinctGroupBy = table SELECT_DISTINCT GROUP_BY<BookAuthor>(author)
            }
        }
        assertEquals(2, distinctWhere.getResults().size)
        assertEquals(listOf("Ken Kousen", "Dan Brown"), distinctOrderBy.getResults().map { it.author })
        assertEquals(1, distinctLimit.getResults().size)
        assertEquals(2, distinctGroupBy.getResults().size)

        // A nullable column is read into a nullable property
        database {
            UserAccountTable INSERT UserAccount(
                id = null,
                username = "ivy",
                email = "ivy@example.com",
                status = UserStatus.ACTIVE,
                priority = Priority.LOW,
                notes = null,
            )
        }
        lateinit var notes: SelectStatement<UserNotes>
        database {
            notes = UserAccountTable SELECT X<UserNotes>()
        }
        assertEquals(listOf(UserNotes("ivy", null)), notes.getResults())

        // A type that doesn't fit the table is rejected while the statement is built, before anything runs
        val notAColumn = assertFailsWith<IllegalArgumentException> {
            database { BookTable SELECT X<BookWithIsbn>() }
        }
        assertEquals(true, notAColumn.message!!.contains("'isbn' isn't a column"))
        val wrongType = assertFailsWith<IllegalArgumentException> {
            database { BookTable SELECT WHERE<BookPagesAsText>(BookTable.pages GT 0) }
        }
        assertEquals(true, wrongType.message!!.contains("'pages' is a kotlin.String, but the column holds a kotlin.Int"))
        val notNullable = assertFailsWith<IllegalArgumentException> {
            database { UserAccountTable SELECT X<UserNotesNonNull>() }
        }
        assertEquals(true, notNullable.message!!.contains("column 'notes' is nullable"))
    }

    /**
     * Covers result columns: expressions, such as aggregate functions, selected into properties of a result type with
     * AS, as in `table SELECT listOf(count(X) AS AuthorStats::books)`, while every other property is read from its
     * column. Each function reads into the type of the values SQLite returns for it, and NULL into a nullable property.
     */
    fun testResultColumns() = Database(getResultColumnDBConfig()).databaseAutoClose { database ->
        // Not grouped, an aggregate query returns one row even when no rows match: count is 0, and the others NULL
        lateinit var noTotals: SelectStatement<BookTotals>
        database {
            BookTable { table ->
                noTotals = table SELECT listOf(
                    count(X) AS BookTotals::books,
                    max(pages) AS BookTotals::maxPages,
                    avg(price) AS BookTotals::averagePrice,
                    sum(price) AS BookTotals::totalPrice,
                )
            }
        }
        assertEquals(listOf(BookTotals(books = 0, maxPages = null, averagePrice = null, totalPrice = null)), noTotals.getResults())

        database {
            BookTable INSERT listOf(
                Book(name = "The Da Vinci Code", author = "Dan Brown", price = 16.96, pages = 454),
                Book(name = "The Lost Symbol", author = "Dan Brown", price = 19.95, pages = 510),
                Book(name = "Kotlin Cookbook", author = "Ken Kousen", price = 37.72, pages = 251),
            )
        }

        // A group of GROUP BY always has rows, so aggregates of NOT NULL columns are non-null in it. 'author' has no
        // expression, so it is read from its column: SELECT author,count(*) AS books,... FROM book GROUP BY author
        lateinit var stats: SelectStatement<AuthorStats>
        lateinit var totals: SelectStatement<BookTotals>
        lateinit var bookCount: SelectStatement<BookCount>
        database {
            BookTable { table ->
                stats = table SELECT listOf(
                    count(X) AS AuthorStats::books,
                    sum(pages) AS AuthorStats::totalPages,
                    max(price) AS AuthorStats::maxPrice,
                    min(name) AS AuthorStats::firstTitle,
                ) GROUP_BY author ORDER_BY (author to ASC)
                totals = table SELECT listOf(
                    count(X) AS BookTotals::books,
                    max(pages) AS BookTotals::maxPages,
                    avg(price) AS BookTotals::averagePrice,
                    sum(price) AS BookTotals::totalPrice,
                )
                bookCount = table SELECT (count(X) AS BookCount::books)
            }
        }
        assertEquals(
            listOf(
                AuthorStats("Dan Brown", books = 2, totalPages = 964, maxPrice = 19.95, firstTitle = "The Da Vinci Code"),
                AuthorStats("Ken Kousen", books = 1, totalPages = 251, maxPrice = 37.72, firstTitle = "Kotlin Cookbook"),
            ),
            stats.getResults(),
        )
        val total = totals.getResults().single()
        assertEquals(3L, total.books)
        assertEquals(510, total.maxPages)
        assertEquals(74.63 / 3, total.averagePrice!!, 1e-9)
        assertEquals(74.63, total.totalPrice!!, 1e-9)
        assertEquals(3L, bookCount.getResults().single().books)

        // The clauses that can follow result columns, and those that follow them
        lateinit var prolific: SelectStatement<AuthorStats>
        lateinit var cheap: SelectStatement<BookCount>
        lateinit var secondMostBooks: SelectStatement<AuthorStats>
        lateinit var kotlinBook: SelectStatement<BookFunctions>
        lateinit var shortestBook: SelectStatement<BookFunctions>
        lateinit var upperAuthors: SelectStatement<BookAuthor>
        database {
            BookTable { table ->
                prolific = table SELECT listOf(
                    count(X) AS AuthorStats::books,
                    sum(pages) AS AuthorStats::totalPages,
                    max(price) AS AuthorStats::maxPrice,
                    min(name) AS AuthorStats::firstTitle,
                ) WHERE (price LT 30.0) GROUP_BY author HAVING (count(X) GT 1)
                cheap = table SELECT (count(X) AS BookCount::books) WHERE (price LT 20.0)
                secondMostBooks = table SELECT listOf(
                    count(X) AS AuthorStats::books,
                    sum(pages) AS AuthorStats::totalPages,
                    max(price) AS AuthorStats::maxPrice,
                    min(name) AS AuthorStats::firstTitle,
                ) GROUP_BY author ORDER_BY (count(X) to DESC) LIMIT 1 OFFSET 1
                kotlinBook = table SELECT listOf(
                    upper(name) AS BookFunctions::upperName,
                    length(name) AS BookFunctions::nameLength,
                    round(price, 0) AS BookFunctions::roundedPrice,
                    abs(pages) AS BookFunctions::absPages,
                ) WHERE (author EQ "Ken Kousen")
                shortestBook = table SELECT listOf(
                    upper(name) AS BookFunctions::upperName,
                    length(name) AS BookFunctions::nameLength,
                    round(price, 0) AS BookFunctions::roundedPrice,
                    abs(pages) AS BookFunctions::absPages,
                ) ORDER_BY (pages to ASC) LIMIT 1
                // An expression can take the place of the column of the same name
                upperAuthors = table SELECT_DISTINCT (upper(author) AS BookAuthor::author)
            }
        }
        assertEquals(
            listOf(AuthorStats("Dan Brown", books = 2, totalPages = 964, maxPrice = 19.95, firstTitle = "The Da Vinci Code")),
            prolific.getResults(),
        )
        assertEquals(2L, cheap.getResults().single().books)
        assertEquals(listOf("Ken Kousen"), secondMostBooks.getResults().map { it.author })
        val functions = BookFunctions("Kotlin Cookbook", upperName = "KOTLIN COOKBOOK", nameLength = 15, roundedPrice = 38.0, absPages = 251)
        assertEquals(listOf(functions), kotlinBook.getResults())
        assertEquals(listOf(functions), shortestBook.getResults())
        assertEquals(listOf("DAN BROWN", "KEN KOUSEN"), upperAuthors.getResults().map { it.author }.sorted())

        // An aggregate of a nullable column is NULL for a group whose values are all NULL, and max of an enum column
        // is an entry of that enum
        database {
            UserAccountTable INSERT listOf(
                UserAccount(id = null, username = "ann", email = "ann@example.com", status = UserStatus.ACTIVE, priority = Priority.LOW, notes = null),
                UserAccount(id = null, username = "bob", email = "bob@example.com", status = UserStatus.ACTIVE, priority = Priority.HIGH, notes = "vip"),
                UserAccount(id = null, username = "cat", email = "cat@example.com", status = UserStatus.INACTIVE, priority = Priority.MEDIUM, notes = null),
            )
        }
        lateinit var byStatus: SelectStatement<StatusStats>
        database {
            UserAccountTable { table ->
                byStatus = table SELECT listOf(
                    count(X) AS StatusStats::users,
                    group_concat(notes, ",") AS StatusStats::notes,
                    max(priority) AS StatusStats::highestPriority,
                ) GROUP_BY status ORDER_BY (status to ASC)
            }
        }
        assertEquals(
            listOf(
                StatusStats(UserStatus.ACTIVE, users = 2, notes = "vip", highestPriority = Priority.HIGH),
                StatusStats(UserStatus.INACTIVE, users = 1, notes = null, highestPriority = Priority.MEDIUM),
            ),
            byStatus.getResults(),
        )

        // sum of a Boolean column counts its true values
        database {
            DefaultValuesTestTable INSERT listOf(true, false, true).mapIndexed { index, isEnabled ->
                DefaultValuesTest(id = null, name = "row$index", status = "active", loginCount = 0, isEnabled = isEnabled, createdAt = "2026-10-02")
            }
        }
        lateinit var enabled: SelectStatement<EnabledCount>
        database {
            DefaultValuesTestTable { table ->
                enabled = table SELECT (sum(isEnabled) AS EnabledCount::enabled)
            }
        }
        assertEquals(2L, enabled.getResults().single().enabled)
    }

    /**
     * Covers how result columns are checked against their result type. Most of it is checked while the statement is
     * built: a property has to be serialized under its own name, get one expression at most, and be nullable when its
     * expression can be NULL in a row or a group. Whether a property can be NULL because an aggregate query isn't
     * grouped depends on whether GROUP BY follows, so that is checked when the scope ends, before any statement runs.
     */
    fun testResultColumnChecks() = Database(getResultColumnDBConfig()).databaseAutoClose { database ->
        // Checked while the statement is built
        val nullInGroup = assertFailsWith<IllegalArgumentException> {
            database {
                UserAccountTable { table ->
                    table SELECT (group_concat(notes, ",") AS UserNotesNonNull::notes) GROUP_BY status
                }
            }
        }
        assertEquals(true, nullInGroup.message!!.contains("'group_concat(user_account.notes,',')' can be NULL, so property 'notes' has to be nullable"))
        val twice = assertFailsWith<IllegalArgumentException> {
            database {
                BookTable { table ->
                    table SELECT listOf(count(X) AS BookCount::books, count(name) AS BookCount::books)
                }
            }
        }
        assertEquals(true, twice.message!!.contains("'books' is given more than one expression"))
        val renamed = assertFailsWith<IllegalArgumentException> {
            database { BookTable { table -> table SELECT (count(X) AS RenamedBookCount::books) } }
        }
        assertEquals(true, renamed.message!!.contains("doesn't serialize its property 'books' under that name"))
        val none = assertFailsWith<IllegalArgumentException> {
            database { BookTable SELECT emptyList<ResultColumn<BookCount>>() }
        }
        assertEquals(true, none.message!!.contains("no result columns"))
        val otherTable = assertFailsWith<IllegalArgumentException> {
            database { BookTable SELECT (UserAccountTable.username AS BookAuthor::author) }
        }
        assertEquals(true, otherTable.message!!.contains("belongs to table 'user_account'"))
        val notAColumn = assertFailsWith<IllegalArgumentException> {
            database { BookTable { table -> table SELECT (upper(name) AS BookWithIsbn::name) } }
        }
        assertEquals(true, notAColumn.message!!.contains("'isbn' isn't a column"))

        // Checked when the scope ends. Without GROUP BY, 'maxPages' would be NULL when no rows match, while 'books',
        // a count, would be 0. Nothing in the scope runs, not even the INSERT before it.
        val ungrouped = assertFailsWith<IllegalArgumentException> {
            database {
                BookTable INSERT Book(name = "Kotlin Cookbook", author = "Ken Kousen", price = 37.72, pages = 251)
                BookTable { table ->
                    table SELECT listOf(count(X) AS BookCountAndMaxPages::books, max(pages) AS BookCountAndMaxPages::maxPages)
                }
            }
        }
        assertEquals(true, ungrouped.message!!.contains("without GROUP BY"))
        assertEquals(true, ungrouped.message!!.contains("'maxPages'"))
        assertEquals(false, ungrouped.message!!.contains("'books'"))
        // A column is NULL in that row as well, and the check follows the statement through the clauses after it
        val ungroupedColumn = assertFailsWith<IllegalArgumentException> {
            database {
                BookTable { table ->
                    table SELECT listOf(
                        count(X) AS AuthorStats::books,
                        sum(pages) AS AuthorStats::totalPages,
                        max(price) AS AuthorStats::maxPrice,
                        min(name) AS AuthorStats::firstTitle,
                    ) WHERE (price GT 0.0) ORDER_BY (pages to ASC) LIMIT 1
                }
            }
        }
        assertEquals(true, ungroupedColumn.message!!.contains("'author'"))
        // In a transaction too
        val inTransaction = assertFailsWith<IllegalArgumentException> {
            database {
                transaction {
                    BookTable INSERT Book(name = "Kotlin Cookbook", author = "Ken Kousen", price = 37.72, pages = 251)
                    BookTable { table ->
                        table SELECT listOf(count(X) AS BookCountAndMaxPages::books, max(pages) AS BookCountAndMaxPages::maxPages)
                    }
                }
            }
        }
        assertEquals(true, inTransaction.message!!.contains("without GROUP BY"))
        lateinit var bookCount: SelectStatement<BookCount>
        database {
            BookTable { table -> bookCount = table SELECT (count(X) AS BookCount::books) }
        }
        assertEquals(0L, bookCount.getResults().single().books)

        // GROUP BY settles it, after WHERE as well
        lateinit var grouped: SelectStatement<BookCountAndMaxPages>
        database {
            BookTable INSERT Book(name = "Kotlin Cookbook", author = "Ken Kousen", price = 37.72, pages = 251)
            BookTable { table ->
                grouped = table SELECT listOf(
                    count(X) AS BookCountAndMaxPages::books,
                    max(pages) AS BookCountAndMaxPages::maxPages,
                ) WHERE (price GT 0.0) GROUP_BY author
            }
        }
        assertEquals(listOf(BookCountAndMaxPages(books = 1, maxPages = 251)), grouped.getResults())
    }

    /**
     * Compile-time check, never called: each function reads into the type of the values SQLite returns for it, which
     * is what lets AS select it only into a property of that type.
     */
    @Suppress("unused", "UNUSED_VARIABLE")
    private fun checkFunctionResultTypes(): Unit = BookTable { table ->
        val countAll: ClauseNumber<Long> = count(X)
        val countColumn: ClauseNumber<Long> = count(name)
        val sumOfInt: ClauseNumber<Long> = sum(pages)
        val sumOfDouble: ClauseNumber<Double> = sum(price)
        val sumOfBoolean: ClauseNumber<Long> = DefaultValuesTestTable.sum(DefaultValuesTestTable.isEnabled)
        val average: ClauseNumber<Double> = avg(pages)
        val maxOfInt: ClauseNumber<PageCount> = max(pages)
        val minOfString: ClauseString<String> = min(name)
        val maxOfEnum: ClauseEnum<UserStatus> = UserAccountTable.max(UserAccountTable.status)
        val absolute: ClauseNumber<PageCount> = abs(pages)
        val rounded: ClauseNumber<Double> = round(pages, 1)
        val randomNumber: ClauseNumber<Long> = random()
        val upperCase: ClauseString<String> = upper(name)
        val nameLength: ClauseNumber<Long> = length(name)
        val position: ClauseNumber<Long> = instr(name, "a")
        val concatenated: ClauseString<String> = group_concat(name, ",")
    }

    /**
     * Covers `INSERT INTO ... SELECT`: `INSERT`, `INSERT_OR_IGNORE` and `INSERT_OR_REPLACE` given a SELECT of the
     * table's row type insert the rows it returns, and the SELECT no longer runs on its own. The target is a copy of a
     * table made with `withName`.
     */
    fun testInsertSelect() = Database(getNewAPIDBConfig()).databaseAutoClose { database ->
        val bookCopy = BookTable.withName("book_copy")
        assertEquals(true, bookCopy.createSQL.startsWith("CREATE TABLE book_copy("))
        assertEquals(BookTable.createSQL.substringAfter('('), bookCopy.createSQL.substringAfter('('))

        // Copy the rows a WHERE selects, its parameter included. The SELECT is part of the INSERT now, so it doesn't
        // run by itself and has no results of its own.
        lateinit var source: SelectStatement<Book>
        database {
            CREATE(bookCopy)
            CREATE(AuthorBookCountTable)
            BookTable INSERT listOf(
                Book(name = "The Da Vinci Code", author = "Dan Brown", price = 16.96, pages = 454),
                Book(name = "The Lost Symbol", author = "Dan Brown", price = 19.95, pages = 510),
                Book(name = "Kotlin Cookbook", author = "Ken Kousen", price = 37.72, pages = 251),
            )
            source = BookTable SELECT WHERE(BookTable.price LT 30.0)
            bookCopy INSERT source
        }
        assertFailsWith<IllegalStateException> { source.getResults() }
        lateinit var copied: SelectStatement<Book>
        database {
            copied = bookCopy SELECT X
        }
        assertEquals(listOf("The Da Vinci Code", "The Lost Symbol"), copied.getResults().map { it.name }.sorted())

        // Result columns produce rows of another table's type: here a table of aggregates
        lateinit var counts: SelectStatement<AuthorBookCount>
        database {
            BookTable { table ->
                AuthorBookCountTable INSERT (table SELECT (count(X) AS AuthorBookCount::books) GROUP_BY author)
            }
            counts = AuthorBookCountTable SELECT X
        }
        assertEquals(
            listOf(AuthorBookCount("Dan Brown", 2), AuthorBookCount("Ken Kousen", 1)),
            counts.getResults().sortedBy { it.author },
        )
        // The SELECT is checked when it becomes part of the INSERT: without GROUP BY, 'author' would be NULL when no
        // rows match
        val ungrouped = assertFailsWith<IllegalArgumentException> {
            database {
                BookTable { table ->
                    AuthorBookCountTable INSERT (table SELECT (count(X) AS AuthorBookCount::books))
                }
            }
        }
        assertEquals(true, ungrouped.message!!.contains("without GROUP BY"))

        // On a conflict with the primary key, INSERT fails, INSERT_OR_IGNORE keeps the row, and INSERT_OR_REPLACE
        // replaces it. The key is copied as it is selected.
        val personCopy = PersonWithIdTable.withName("person_copy")
        database {
            CREATE(personCopy)
            PersonWithIdTable INSERT listOf(
                PersonWithId(id = null, name = "Ann", age = 30),
                PersonWithId(id = null, name = "Bob", age = 40),
            )
            personCopy INSERT (PersonWithIdTable SELECT WHERE(PersonWithIdTable.name EQ "Ann"))
        }
        assertFails {
            database { personCopy INSERT (PersonWithIdTable SELECT X) }
        }
        lateinit var afterIgnore: SelectStatement<PersonWithId>
        database {
            PersonWithIdTable { table ->
                table UPDATE SET { age = 31 } WHERE (name EQ "Ann")
            }
            personCopy INSERT_OR_IGNORE (PersonWithIdTable SELECT X)
            afterIgnore = personCopy SELECT X
        }
        assertEquals(listOf("Ann" to 30, "Bob" to 40), afterIgnore.getResults().map { it.name to it.age }.sortedBy { it.first })
        lateinit var afterReplace: SelectStatement<PersonWithId>
        lateinit var people: SelectStatement<PersonWithId>
        database {
            PersonWithIdTable { table ->
                personCopy INSERT_OR_REPLACE (table SELECT listOf(upper(name) AS PersonWithId::name))
            }
            afterReplace = personCopy SELECT X
            people = PersonWithIdTable SELECT X
        }
        assertEquals(listOf("ANN" to 31, "BOB" to 40), afterReplace.getResults().map { it.name to it.age }.sortedBy { it.first })
        assertEquals(people.getResults().map { it.id }.sortedBy { it }, afterReplace.getResults().map { it.id }.sortedBy { it })
    }

    /**
     * Covers rebuilding a table in a migration, for a change `ALTER TABLE` can't make: the new structure is created
     * under a temporary name with `withName`, filled with `INSERT INTO ... SELECT` from the old one, which is dropped,
     * and renamed to the table's name. Renaming the new table rather than the old one leaves the foreign keys of other
     * tables pointing at the rebuilt table.
     */
    fun testTableRebuild() {
        val version1 = DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = {
                CREATE(RebuildPersonV1Table)
                CREATE(RebuildPetTable)
            },
        )
        Database(version1).databaseAutoClose { database ->
            database {
                RebuildPersonV1Table INSERT listOf(
                    RebuildPersonV1(id = null, name = "Ann", legacy = 1),
                    RebuildPersonV1(id = null, name = "Bob", legacy = 2),
                )
                RebuildPetTable INSERT RebuildPet(id = 1, ownerId = 1)
            }
        }

        val version2 = DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 2,
            create = {
                CREATE(RebuildPersonTable)
                CREATE(RebuildPetTable)
            },
            upgrade = { oldVersion, _ ->
                if (oldVersion < 2) {
                    val newPerson = RebuildPersonTable.withName("rebuild_person_new")
                    CREATE(newPerson)
                    RebuildPersonV1Table { table ->
                        newPerson INSERT (table SELECT (name AS RebuildPerson::fullName))
                    }
                    DROP(RebuildPersonV1Table)
                    "rebuild_person_new" ALTER_RENAME_TABLE_TO RebuildPersonTable
                }
            },
        )
        Database(version2).databaseAutoClose { database ->
            // The rows and their keys are kept, under the new structure, and 'legacy' is gone
            lateinit var people: SelectStatement<RebuildPerson>
            database {
                people = RebuildPersonTable SELECT X
            }
            assertEquals(listOf(RebuildPerson(1, "Ann"), RebuildPerson(2, "Bob")), people.getResults().sortedBy { it.id })
            assertEquals(true, database.selectFails { RebuildPersonV1Table SELECT X })

            // The new constraint holds
            assertFails {
                database { RebuildPersonTable INSERT RebuildPerson(id = null, fullName = "Ann") }
            }

            // The pet's foreign key still points at 'rebuild_person': an existing owner is accepted and a missing one
            // rejected. Had the reference followed a renamed table, both would fail.
            database {
                PRAGMA_FOREIGN_KEYS(true)
                RebuildPetTable INSERT RebuildPet(id = 2, ownerId = 2)
            }
            assertFails {
                database { RebuildPetTable INSERT RebuildPet(id = 3, ownerId = 99) }
            }
            lateinit var pets: SelectStatement<RebuildPet>
            database {
                pets = RebuildPetTable SELECT X
            }
            assertEquals(listOf(1L, 2L), pets.getResults().map { it.id }.sorted())
        }
    }

    fun testCreateInDatabaseScope() {
        Database(getNewAPIDBConfig()).databaseAutoClose { database ->
            val person = PersonWithId(id = null, name = "Grace", age = 40)
            val product = Product(sku = "SKU-THING", name = "Thingamajig", price = 49.99)

            lateinit var personStatement: SelectStatement<PersonWithId>
            lateinit var productStatement: SelectStatement<Product>
            database {
                PersonWithIdTable { table ->
                    table INSERT person
                    personStatement = table SELECT X
                }
                ProductTable { table ->
                    table INSERT product
                    productStatement = table SELECT X
                }
            }

            assertEquals(1, personStatement.getResults().size)
            assertEquals("Grace", personStatement.getResults().first().name)
            assertEquals(1, productStatement.getResults().size)
            assertEquals("Thingamajig", productStatement.getResults().first().name)
            assertEquals(49.99, productStatement.getResults().first().price)
        }
    }

    fun testUpdateAndDeleteWithPrimaryKey() {
        Database(getNewAPIDBConfig()).databaseAutoClose { database ->
            val person1 = PersonWithId(id = null, name = "Henry", age = 45)
            val person2 = PersonWithId(id = null, name = "Iris", age = 50)

            database {
                PersonWithIdTable { table ->
                    table INSERT listOf(person1, person2)
                }
            }

            lateinit var selectStatement: SelectStatement<PersonWithId>
            database {
                PersonWithIdTable { table ->
                    table UPDATE SET { age = 46 } WHERE (name EQ "Henry")
                    selectStatement = table SELECT WHERE (name EQ "Henry")
                }
            }

            val updatedPerson = selectStatement.getResults().first()
            assertEquals("Henry", updatedPerson.name)
            assertEquals(46, updatedPerson.age)

            database {
                PersonWithIdTable { table ->
                    table DELETE WHERE (name EQ "Iris")
                    selectStatement = table SELECT X
                }
            }

            val remainingResults = selectStatement.getResults()
            assertEquals(1, remainingResults.size)
            assertEquals("Henry", remainingResults.first().name)
        }
    }

    fun testByteArrayAndBlobOperations() {
        Database(getNewAPIDBConfig()).databaseAutoClose { database ->
            // Test 1: INSERT - multiple files including empty and large
            val file1 = FileData(
                id = null,
                fileName = "test.bin",
                content = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte()),
                metadata = "Binary test file"
            )
            val file2 = FileData(
                id = null,
                fileName = "empty.dat",
                content = byteArrayOf(),
                metadata = "Empty file"
            )
            val file3 = FileData(
                id = null,
                fileName = "large.bin",
                content = ByteArray(256) { it.toByte() },
                metadata = "Large file with all byte values"
            )

            lateinit var selectStatement: SelectStatement<FileData>
            database {
                FileDataTable { table ->
                    table INSERT listOf(file1, file2, file3)
                    selectStatement = table SELECT X
                }
            }

            val results = selectStatement.getResults()
            assertEquals(3, results.size)
            assertEquals("test.bin", results[0].fileName)
            assertEquals(true, results[0].content.contentEquals(byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte())))
            assertEquals("Binary test file", results[0].metadata)
            assertEquals("empty.dat", results[1].fileName)
            assertEquals(true, results[1].content.contentEquals(byteArrayOf()))
            assertEquals("Empty file", results[1].metadata)
            assertEquals("large.bin", results[2].fileName)
            assertEquals(256, results[2].content.size)
            assertEquals(true, results[2].content.contentEquals(ByteArray(256) { it.toByte() }))

            // Test 2: SELECT with WHERE clause
            val selectFile = FileData(
                id = null,
                fileName = "select_test.bin",
                content = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()),
                metadata = "SELECT test"
            )
            database {
                FileDataTable { table ->
                    table INSERT selectFile
                }
            }
            database {
                FileDataTable { table ->
                    selectStatement = table SELECT WHERE (fileName EQ "select_test.bin")
                }
            }
            assertEquals(1, selectStatement.getResults().size)
            assertEquals("select_test.bin", selectStatement.getResults().first().fileName)
            assertEquals(true, selectStatement.getResults().first().content.contentEquals(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())))

            // Test 3: UPDATE
            val originalFile = FileData(
                id = null,
                fileName = "update_test.bin",
                content = byteArrayOf(0x00, 0x01, 0x02),
                metadata = "Original"
            )
            database {
                FileDataTable { table ->
                    table INSERT originalFile
                }
            }
            val newContent = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte())
            database {
                FileDataTable { table ->
                    table UPDATE SET {
                        content = newContent
                        metadata = "Updated"
                    } WHERE (fileName EQ "update_test.bin")
                    selectStatement = table SELECT WHERE (fileName EQ "update_test.bin")
                }
            }
            val updatedFile = selectStatement.getResults().first()
            assertEquals("update_test.bin", updatedFile.fileName)
            assertEquals(true, updatedFile.content.contentEquals(newContent))
            assertEquals("Updated", updatedFile.metadata)

            // Test 4: DELETE
            val deleteFile1 = FileData(
                id = null,
                fileName = "delete_test1.bin",
                content = byteArrayOf(0x01, 0x02),
                metadata = "To delete"
            )
            val deleteFile2 = FileData(
                id = null,
                fileName = "delete_test2.bin",
                content = byteArrayOf(0x03, 0x04),
                metadata = "To keep"
            )
            database {
                FileDataTable { table ->
                    table INSERT listOf(deleteFile1, deleteFile2)
                }
            }
            database {
                FileDataTable { table ->
                    table DELETE WHERE (fileName EQ "delete_test1.bin")
                    selectStatement = table SELECT WHERE (fileName LIKE "delete_test%")
                }
            }
            assertEquals(1, selectStatement.getResults().size)
            assertEquals("delete_test2.bin", selectStatement.getResults().first().fileName)

            // Test 5: Multiple operations (INSERT, UPDATE, SELECT)
            val multiFile1 = FileData(
                id = null,
                fileName = "multi1.bin",
                content = byteArrayOf(0xAA.toByte(), 0xBB.toByte()),
                metadata = "First"
            )
            val multiFile2 = FileData(
                id = null,
                fileName = "multi2.bin",
                content = byteArrayOf(0xCC.toByte(), 0xDD.toByte()),
                metadata = "Second"
            )
            database {
                FileDataTable { table ->
                    table INSERT listOf(multiFile1, multiFile2)
                }
            }
            val multiNewContent = byteArrayOf(0x11, 0x22, 0x33)
            database {
                FileDataTable { table ->
                    table UPDATE SET {
                        content = multiNewContent
                    } WHERE (fileName EQ "multi1.bin")
                    selectStatement = table SELECT WHERE (fileName EQ "multi1.bin")
                }
            }
            val multiUpdatedFile = selectStatement.getResults().first()
            assertEquals(true, multiUpdatedFile.content.contentEquals(multiNewContent))
            assertEquals("First", multiUpdatedFile.metadata)

            // Test 6: Blob comparison operators (LT, LTE, GT, GTE)
            // Clear the table first to avoid data from previous tests
            database {
                FileDataTable { table ->
                    table DELETE X
                }
            }

            val compareFile0 = FileData(id = null, fileName = "compare0.bin", content = byteArrayOf(0x01, 0x02), metadata = "File 0")
            val compareFile1 = FileData(id = null, fileName = "compare1.bin", content = byteArrayOf(0x03, 0x04), metadata = "File 1")
            val compareFile2 = FileData(id = null, fileName = "compare2.bin", content = byteArrayOf(0x05, 0x06), metadata = "File 2")
            val compareFile3 = FileData(id = null, fileName = "compare3.bin", content = byteArrayOf(0x07, 0x08), metadata = "File 3")

            var statementLT: SelectStatement<FileData>? = null
            var statementLTE: SelectStatement<FileData>? = null
            var statementGT: SelectStatement<FileData>? = null
            var statementGTE: SelectStatement<FileData>? = null

            database {
                FileDataTable { table ->
                    table INSERT listOf(compareFile0, compareFile1, compareFile2, compareFile3)
                    statementLT = table SELECT WHERE (content LT byteArrayOf(0x03, 0x04))
                    statementLTE = table SELECT WHERE (content LTE byteArrayOf(0x03, 0x04))
                    statementGT = table SELECT WHERE (content GT byteArrayOf(0x05, 0x06))
                    statementGTE = table SELECT WHERE (content GTE byteArrayOf(0x05, 0x06))
                }
            }

            val resultsLT = statementLT!!.getResults()
            assertEquals(1, resultsLT.size)
            assertEquals("compare0.bin", resultsLT[0].fileName)

            val resultsLTE = statementLTE!!.getResults()
            assertEquals(2, resultsLTE.size)
            assertEquals(true, resultsLTE.any { it.fileName == "compare0.bin" })
            assertEquals(true, resultsLTE.any { it.fileName == "compare1.bin" })

            val resultsGT = statementGT!!.getResults()
            assertEquals(1, resultsGT.size)
            assertEquals("compare3.bin", resultsGT[0].fileName)

            val resultsGTE = statementGTE!!.getResults()
            assertEquals(2, resultsGTE.size)
            assertEquals(true, resultsGTE.any { it.fileName == "compare2.bin" })
            assertEquals(true, resultsGTE.any { it.fileName == "compare3.bin" })

            // Test 7: Blob IN operator
            // Clear the table first
            database {
                FileDataTable { table ->
                    table DELETE X
                }
            }

            val inFile0 = FileData(id = null, fileName = "in0.bin", content = byteArrayOf(0x01, 0x02), metadata = "In 0")
            val inFile1 = FileData(id = null, fileName = "in1.bin", content = byteArrayOf(0x03, 0x04), metadata = "In 1")
            val inFile2 = FileData(id = null, fileName = "in2.bin", content = byteArrayOf(0x05, 0x06), metadata = "In 2")
            val inFile3 = FileData(id = null, fileName = "in3.bin", content = byteArrayOf(0x07, 0x08), metadata = "In 3")

            var statementIN: SelectStatement<FileData>? = null
            database {
                FileDataTable { table ->
                    table INSERT listOf(inFile0, inFile1, inFile2, inFile3)
                    statementIN = table SELECT WHERE (content IN listOf(
                        byteArrayOf(0x01, 0x02),
                        byteArrayOf(0x05, 0x06),
                        byteArrayOf(0x09, 0x0A)
                    ))
                }
            }

            val resultsIN = statementIN!!.getResults()
            assertEquals(2, resultsIN.size)
            assertEquals(true, resultsIN.any { it.fileName == "in0.bin" })
            assertEquals(true, resultsIN.any { it.fileName == "in2.bin" })

            // Test 8: Blob BETWEEN operator
            // Clear the table first
            database {
                FileDataTable { table ->
                    table DELETE X
                }
            }

            val betweenFile0 = FileData(id = null, fileName = "between0.bin", content = byteArrayOf(0x01, 0x02), metadata = "Between 0")
            val betweenFile1 = FileData(id = null, fileName = "between1.bin", content = byteArrayOf(0x03, 0x04), metadata = "Between 1")
            val betweenFile2 = FileData(id = null, fileName = "between2.bin", content = byteArrayOf(0x05, 0x06), metadata = "Between 2")
            val betweenFile3 = FileData(id = null, fileName = "between3.bin", content = byteArrayOf(0x07, 0x08), metadata = "Between 3")

            var statementBETWEEN: SelectStatement<FileData>? = null
            database {
                FileDataTable { table ->
                    table INSERT listOf(betweenFile0, betweenFile1, betweenFile2, betweenFile3)
                    statementBETWEEN = table SELECT WHERE (content BETWEEN (byteArrayOf(0x03, 0x04) to byteArrayOf(0x05, 0x06)))
                }
            }

            val resultsBETWEEN = statementBETWEEN!!.getResults()
            assertEquals(2, resultsBETWEEN.size)
            assertEquals(true, resultsBETWEEN.any { it.fileName == "between1.bin" })
            assertEquals(true, resultsBETWEEN.any { it.fileName == "between2.bin" })
        }
    }

    fun testDropAndCreateTable() {
        Database(getNewAPIDBConfig()).databaseAutoClose { database ->
            // Test 1: DROP using global function
            val person1 = PersonWithId(id = null, name = "Alice", age = 25)
            val person2 = PersonWithId(id = null, name = "Bob", age = 30)

            database {
                PersonWithIdTable { table ->
                    table INSERT listOf(person1, person2)
                }
            }

            lateinit var personStatement1: SelectStatement<PersonWithId>
            database {
                personStatement1 = PersonWithIdTable SELECT X
            }
            assertEquals(2, personStatement1.getResults().size)

            database {
                DROP(PersonWithIdTable)
            }

            database {
                CREATE(PersonWithIdTable)
            }

            lateinit var personStatement2: SelectStatement<PersonWithId>
            database {
                personStatement2 = PersonWithIdTable SELECT X
            }
            assertEquals(0, personStatement2.getResults().size)

            // Test 2: DROP using extension function
            val product = Product(sku = "SKU-001", name = "Widget", price = 19.99)

            database {
                ProductTable { table ->
                    table INSERT product
                }
            }

            lateinit var productStatement1: SelectStatement<Product>
            database {
                productStatement1 = ProductTable SELECT X
            }
            assertEquals(1, productStatement1.getResults().size)

            database {
                ProductTable.DROP()
            }

            database {
                CREATE(ProductTable)
            }

            lateinit var productStatement2: SelectStatement<Product>
            database {
                productStatement2 = ProductTable SELECT X
            }
            assertEquals(0, productStatement2.getResults().size)

            // Test 3: DROP and recreate FileDataTable with binary data
            val fileData = FileData(
                id = null,
                fileName = "test.txt",
                content = byteArrayOf(1, 2, 3, 4, 5),
                metadata = "Test metadata"
            )

            database {
                FileDataTable { table ->
                    table INSERT fileData
                }
            }

            lateinit var fileStatement1: SelectStatement<FileData>
            database {
                fileStatement1 = FileDataTable SELECT X
            }
            assertEquals(1, fileStatement1.getResults().size)
            assertEquals("test.txt", fileStatement1.getResults().first().fileName)

            database {
                FileDataTable.DROP()
                CREATE(FileDataTable)
            }

            lateinit var fileStatement2: SelectStatement<FileData>
            database {
                fileStatement2 = FileDataTable SELECT X
            }
            assertEquals(0, fileStatement2.getResults().size)
        }
    }

    /**
     * Exercises every ALTER operation as one realistic schema migration.
     *
     * 'alter_target' is created in the shape of [AlterBefore] (`id`, `name`, `legacy`) and migrated
     * to the shape of [AlterAfter] (`id`, `fullName`, `nickname`) by ADD COLUMN, RENAME COLUMN and
     * DROP COLUMN, then renamed to 'alter_renamed' and back. Every step is verified by reading the
     * table through the entity matching the shape it should have at that point, so a step that does
     * not actually run makes the test fail instead of passing quietly.
     */
    @OptIn(PlatformDependentSQLiteAPI::class)
    fun testSchemaModification() {
        Database(getNewAPIDBConfig()).databaseAutoClose { database ->
            database {
                CREATE(AlterBeforeTable)
                AlterBeforeTable { table ->
                    table INSERT AlterBefore(id = null, name = "Charlie", legacy = 7)
                }
            }

            // Reading the migrated shape must fail first: 'nickname' does not exist yet.
            assertEquals(
                true,
                database.selectFails { AlterAfterTable SELECT X },
                "'nickname' should not exist before ADD COLUMN",
            )

            // ADD COLUMN. The receiver must be the table whose serializer declares the new column,
            // because the column's SQL type is resolved from that descriptor.
            database {
                AlterAfterTable ALTER_ADD_COLUMN AlterAfterTable.nickname
            }

            // RENAME COLUMN, naming the old column by string. Both entities map to 'alter_target'.
            database {
                AlterAfterTable.RENAME_COLUMN("name", AlterAfterTable.fullName)
            }

            // Both steps landed: the table now has 'fullName' and 'nickname', and still 'legacy'.
            lateinit var withLegacy: SelectStatement<AlterWithLegacy>
            database {
                withLegacy = AlterWithLegacyTable SELECT X
            }
            assertEquals(1, withLegacy.getResults().size)
            assertEquals("Charlie", withLegacy.getResults().first().fullName)
            assertEquals(null, withLegacy.getResults().first().nickname)
            assertEquals(7, withLegacy.getResults().first().legacy)

            lateinit var migrated: SelectStatement<AlterAfter>
            database {
                migrated = AlterAfterTable SELECT X
            }
            assertEquals(1, migrated.getResults().size)
            assertEquals("Charlie", migrated.getResults().first().fullName)
            assertEquals(null, migrated.getResults().first().nickname)

            // RENAME TO, with a Table receiver, inside a transaction.
            database {
                transaction {
                    AlterAfterTable ALTER_RENAME_TABLE_TO AlterRenamedTable
                }
            }

            lateinit var renamed: SelectStatement<AlterRenamed>
            database {
                renamed = AlterRenamedTable SELECT X
            }
            assertEquals(1, renamed.getResults().size)
            assertEquals("Charlie", renamed.getResults().first().fullName)

            assertEquals(
                true,
                database.selectFails { AlterAfterTable SELECT X },
                "'alter_target' should not exist after RENAME TO",
            )

            // RENAME TO again, this time through the String receiver overload, renaming it back.
            database {
                "alter_renamed" ALTER_RENAME_TABLE_TO AlterAfterTable
            }

            lateinit var renamedBack: SelectStatement<AlterAfter>
            database {
                renamedBack = AlterAfterTable SELECT X
            }
            assertEquals(1, renamedBack.getResults().size)
            assertEquals("Charlie", renamedBack.getResults().first().fullName)

            // DROP COLUMN last, because it needs SQLite 3.35+ (2021) and the Android framework only
            // bundles that from API 34 on. Keeping it last means its failure on older SQLite cannot
            // disturb the steps above. Where it does run, its effect is asserted.
            var legacyDropped = true
            try {
                database {
                    AlterBeforeTable DROP_COLUMN AlterBeforeTable.legacy
                }
            } catch (e: Exception) {
                legacyDropped = false
            }
            if (legacyDropped) {
                assertEquals(
                    true,
                    database.selectFails { AlterWithLegacyTable SELECT X },
                    "'legacy' should be gone after DROP COLUMN",
                )
            }
        }
    }

    /**
     * Runs [block] in its own database scope and reports whether the query failed. The results are
     * read as well as executed, because the Android driver's `rawQuery` is lazy: a missing table or
     * column surfaces only once the cursor is actually read, not when the statement runs.
     */
    private fun Database.selectFails(block: DatabaseScope.() -> SelectStatement<*>): Boolean =
        try {
            var statement: SelectStatement<*>? = null
            this.invoke { statement = block() }
            statement!!.getResults()
            false
        } catch (e: Exception) {
            true
        }

    /**
     * Compile-time check, never called: the generated `SetClause` properties must carry the
     * nullability the entity declares. [PersonWithId] declares a `Long?` primary key *followed by*
     * non-null columns, which is the order that used to leak the key's nullability into every later
     * column. These assignments only compile while `name` and `age` are generated as non-null.
     */
    @Suppress("unused", "UNUSED_VARIABLE")
    private fun checkSetClauseNullability(clause: SetClause<PersonWithId>): Unit = with(PersonWithIdTable) {
        val id: Long? = clause.id
        val name: String = clause.name
        val age: Age = clause.age
    }

    /**
     * Covers how a single `@PrimaryKey`'s nullability decides who supplies its value: a `Long?` key is
     * assigned by the database; a non-null `Long` key is supplied by the caller yet stays a rowid alias;
     * and a key of any other type is supplied by the caller and declared `NOT NULL`, which SQLite would
     * otherwise not imply for it.
     */
    fun testPrimaryKeyNullability() {
        // Both Long keys are rowid aliases; only the non-Long key needs NOT NULL spelled out.
        assertEquals(true, PersonWithIdTable.createSQL.contains("id INTEGER PRIMARY KEY,"))
        assertEquals(true, RemoteMovieTable.createSQL.contains("id INTEGER PRIMARY KEY,"))
        assertEquals(true, ProductTable.createSQL.contains("sku TEXT PRIMARY KEY NOT NULL,"))

        Database(getNewAPIDBConfig()).databaseAutoClose { database ->
            database {
                CREATE(RemoteMovieTable)
            }

            // A caller-supplied Long key is written by a plain INSERT rather than left for the database.
            lateinit var movies: SelectStatement<RemoteMovie>
            database {
                RemoteMovieTable { table ->
                    table INSERT listOf(
                        RemoteMovie(id = 603, title = "The Matrix"),
                        RemoteMovie(id = 27205, title = "Inception"),
                    )
                    movies = table SELECT ORDER_BY(id to ASC)
                }
            }
            assertEquals(listOf(603L, 27205L), movies.getResults().map { it.id })

            // ...and it is a real primary key: inserting the same ID again is rejected.
            var duplicateFailed = false
            try {
                database {
                    RemoteMovieTable INSERT RemoteMovie(id = 603, title = "The Matrix Reloaded")
                }
            } catch (e: Exception) {
                duplicateFailed = true
            }
            assertEquals(true, duplicateFailed, "A duplicate caller-supplied key should be rejected")

            // A Long? key is still assigned by the database.
            lateinit var people: SelectStatement<PersonWithId>
            database {
                PersonWithIdTable { table ->
                    table INSERT PersonWithId(id = null, name = "Ivy", age = 21)
                    people = table SELECT X
                }
            }
            assertNotEquals(null, people.getResults().first().id)
        }
    }

    fun testStringOperators() = Database(getNewAPIDBConfig()).databaseAutoClose { database ->
        // Test 1: Comparison operators (LT, LTE, GT, GTE)
        val book0 = Book(name = "Alice in Wonderland", author = "Lewis Carroll", pages = 200, price = 15.99)
        val book1 = Book(name = "Bob's Adventures", author = "Bob Smith", pages = 300, price = 20.99)
        val book2 = Book(name = "Charlie and the Chocolate Factory", author = "Roald Dahl", pages = 250, price = 18.99)
        val book3 = Book(name = "David Copperfield", author = "Charles Dickens", pages = 400, price = 25.99)

        var statementLT: SelectStatement<Book>? = null
        var statementLTE: SelectStatement<Book>? = null
        var statementGT: SelectStatement<Book>? = null
        var statementGTE: SelectStatement<Book>? = null

        database {
            BookTable { table ->
                table INSERT listOf(book0, book1, book2, book3)
                statementLT = table SELECT WHERE (name LT "Bob's Adventures")
                statementLTE = table SELECT WHERE (name LTE "Bob's Adventures")
                statementGT = table SELECT WHERE (name GT "Charlie and the Chocolate Factory")
                statementGTE = table SELECT WHERE (name GTE "Charlie and the Chocolate Factory")
            }
        }

        val resultsLT = statementLT!!.getResults()
        assertEquals(1, resultsLT.size)
        assertEquals(book0, resultsLT[0])

        val resultsLTE = statementLTE!!.getResults()
        assertEquals(2, resultsLTE.size)
        assertEquals(true, resultsLTE.any { it == book0 })
        assertEquals(true, resultsLTE.any { it == book1 })

        val resultsGT = statementGT!!.getResults()
        assertEquals(1, resultsGT.size)
        assertEquals(book3, resultsGT[0])

        val resultsGTE = statementGTE!!.getResults()
        assertEquals(2, resultsGTE.size)
        assertEquals(true, resultsGTE.any { it == book2 })
        assertEquals(true, resultsGTE.any { it == book3 })

        // Test 2: IN operator
        // Clear the table first
        database {
            BookTable { table ->
                table DELETE X
            }
        }

        val inBook0 = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96)
        val inBook1 = Book(name = "Kotlin Cookbook", author = "Ken Kousen", pages = 251, price = 37.72)
        val inBook2 = Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95)
        val inBook3 = Book(name = "Modern Java Recipes", author ="Ken Kousen", pages = 322, price = 25.78)

        var statementIN: SelectStatement<Book>? = null
        database {
            BookTable { table ->
                table INSERT listOf(inBook0, inBook1, inBook2, inBook3)
                statementIN = table SELECT WHERE (author IN listOf("Dan Brown", "Unknown Author"))
            }
        }

        val resultsIN = statementIN!!.getResults()
        assertEquals(2, resultsIN.size)
        assertEquals(true, resultsIN.any { it == inBook0 })
        assertEquals(true, resultsIN.any { it == inBook2 })

        // Test 3: BETWEEN operator
        // Clear the table first
        database {
            BookTable { table ->
                table DELETE X
            }
        }

        val betweenBook0 = Book(name = "Alice in Wonderland", author = "Lewis Carroll", pages = 200, price = 15.99)
        val betweenBook1 = Book(name = "Bob's Adventures", author = "Bob Smith", pages = 300, price = 20.99)
        val betweenBook2 = Book(name = "Charlie and the Chocolate Factory", author = "Roald Dahl", pages = 250, price = 18.99)
        val betweenBook3 = Book(name = "David Copperfield", author = "Charles Dickens", pages = 400, price = 25.99)

        var statementBETWEEN: SelectStatement<Book>? = null
        database {
            BookTable { table ->
                table INSERT listOf(betweenBook0, betweenBook1, betweenBook2, betweenBook3)
                statementBETWEEN = table SELECT WHERE (name BETWEEN ("Bob's Adventures" to "Charlie and the Chocolate Factory"))
            }
        }

        val resultsBETWEEN = statementBETWEEN!!.getResults()
        assertEquals(2, resultsBETWEEN.size)
        assertEquals(true, resultsBETWEEN.any { it == betweenBook1 })
        assertEquals(true, resultsBETWEEN.any { it == betweenBook2 })

        // Test 4: Column comparison (EQ, NEQ)
        // Clear the table first
        database {
            BookTable { table ->
                table DELETE X
            }
        }

        val colBook0 = Book(name = "Same Name", author = "Same Name", pages = 200, price = 15.99)
        val colBook1 = Book(name = "Different", author = "Another", pages = 300, price = 20.99)

        var statementEQ: SelectStatement<Book>? = null
        var statementNEQ: SelectStatement<Book>? = null
        database {
            BookTable { table ->
                table INSERT listOf(colBook0, colBook1)
                statementEQ = table SELECT WHERE (name EQ BookTable.author)
                statementNEQ = table SELECT WHERE (name NEQ BookTable.author)
            }
        }

        val resultsEQ = statementEQ!!.getResults()
        assertEquals(1, resultsEQ.size)
        assertEquals(colBook0, resultsEQ[0])

        val resultsNEQ = statementNEQ!!.getResults()
        assertEquals(1, resultsNEQ.size)
        assertEquals(colBook1, resultsNEQ[0])
    }

    /**
     * Comprehensive test for enum type support covering all operations:
     * INSERT, SELECT, UPDATE, DELETE, equality/comparison operators,
     * nullable enums, complex conditions, and ORDER BY
     */
    fun testEnumOperations() = Database(getNewAPIDBConfig(), true).databaseAutoClose { database ->
        // Section 1: Basic INSERT and SELECT
        val user1 = UserAccount(null, "john_doe", "john@example.com", UserStatus.ACTIVE, Priority.HIGH, "VIP user")
        val user2 = UserAccount(null, "jane_smith", "jane@example.com", UserStatus.INACTIVE, Priority.LOW, null)
        database {
            UserAccountTable { table ->
                table INSERT listOf(user1, user2)
            }
        }

        var selectAll: SelectStatement<UserAccount>? = null
        database {
            selectAll = UserAccountTable SELECT X
        }
        val allUsers = selectAll!!.getResults()
        assertEquals(2, allUsers.size)
        assertEquals(UserStatus.ACTIVE, allUsers[0].status)
        assertEquals(Priority.HIGH, allUsers[0].priority)
        assertEquals(UserStatus.INACTIVE, allUsers[1].status)
        assertEquals(Priority.LOW, allUsers[1].priority)

        // Section 2: Equality operators (EQ, NEQ)
        val testUsers = listOf(
            UserAccount(null, "user1", "user1@test.com", UserStatus.ACTIVE, Priority.HIGH, null),
            UserAccount(null, "user2", "user2@test.com", UserStatus.INACTIVE, Priority.MEDIUM, null),
            UserAccount(null, "user3", "user3@test.com", UserStatus.ACTIVE, Priority.LOW, null),
            UserAccount(null, "user4", "user4@test.com", UserStatus.SUSPENDED, Priority.CRITICAL, null),
        )
        database {
            UserAccountTable { table ->
                table DELETE X
                table INSERT testUsers
            }
        }

        var selectEQ: SelectStatement<UserAccount>? = null
        database {
            UserAccountTable {
                selectEQ = it SELECT WHERE (it.status EQ UserStatus.ACTIVE)
            }
        }
        val activeUsers = selectEQ!!.getResults()
        assertEquals(2, activeUsers.size)
        assertEquals(true, activeUsers.all { it.status == UserStatus.ACTIVE })

        var selectNEQ: SelectStatement<UserAccount>? = null
        database {
            UserAccountTable {
                selectNEQ = it SELECT WHERE (it.status NEQ UserStatus.ACTIVE)
            }
        }
        val nonActiveUsers = selectNEQ!!.getResults()
        assertEquals(2, nonActiveUsers.size)
        assertEquals(false, nonActiveUsers.any { it.status == UserStatus.ACTIVE })

        // Section 3: Comparison operators (LT, LTE, GT, GTE)
        var selectLT: SelectStatement<UserAccount>? = null
        database {
            UserAccountTable {
                selectLT = it SELECT WHERE (it.priority LT Priority.HIGH)
            }
        }
        assertEquals(2, selectLT!!.getResults().size)

        var selectGTE: SelectStatement<UserAccount>? = null
        database {
            UserAccountTable {
                selectGTE = it SELECT WHERE (it.priority GTE Priority.HIGH)
            }
        }
        val highPriorityUsers = selectGTE!!.getResults()
        assertEquals(2, highPriorityUsers.size)
        assertEquals(true, highPriorityUsers.all { it.priority == Priority.HIGH || it.priority == Priority.CRITICAL })

        // Section 4: Nullable enum handling
        val tasks = listOf(
            Task(null, "High priority task", Priority.HIGH, "Important"),
            Task(null, "Unassigned task", null, "No priority set"),
            Task(null, "Low priority task", Priority.LOW, "Can wait"),
        )
        database {
            TaskTable { table ->
                table INSERT tasks
            }
        }

        var selectNull: SelectStatement<Task>? = null
        database {
            TaskTable {
                selectNull = it SELECT WHERE (it.priority EQ null)
            }
        }
        val nullTasks = selectNull!!.getResults()
        assertEquals(1, nullTasks.size)
        assertEquals("Unassigned task", nullTasks[0].title)

        var selectNotNull: SelectStatement<Task>? = null
        database {
            TaskTable {
                selectNotNull = it SELECT WHERE (it.priority NEQ null)
            }
        }
        assertEquals(2, selectNotNull!!.getResults().size)

        // Section 5: UPDATE with enum values
        database {
            UserAccountTable { table ->
                table UPDATE SET {
                    status = UserStatus.BANNED
                    priority = Priority.CRITICAL
                } WHERE (table.username EQ "user1")
            }
        }

        var selectUpdated: SelectStatement<UserAccount>? = null
        database {
            UserAccountTable {
                selectUpdated = it SELECT WHERE (it.username EQ "user1")
            }
        }
        val updatedUser = selectUpdated!!.getResults().first()
        assertEquals(UserStatus.BANNED, updatedUser.status)
        assertEquals(Priority.CRITICAL, updatedUser.priority)

        // Section 6: Complex conditions (AND/OR)
        var selectAND: SelectStatement<UserAccount>? = null
        database {
            UserAccountTable {
                selectAND = it SELECT WHERE (
                    (it.status EQ UserStatus.SUSPENDED) AND (it.priority EQ Priority.CRITICAL)
                )
            }
        }
        assertEquals(1, selectAND!!.getResults().size)

        var selectOR: SelectStatement<UserAccount>? = null
        database {
            UserAccountTable {
                selectOR = it SELECT WHERE (
                    (it.status EQ UserStatus.BANNED) OR (it.priority LTE Priority.LOW)
                )
            }
        }
        assertEquals(2, selectOR!!.getResults().size)

        // Section 7: ORDER BY enum columns
        var selectOrderByASC: SelectStatement<UserAccount>? = null
        database {
            UserAccountTable { table ->
                selectOrderByASC = table SELECT ORDER_BY (priority to ASC)
            }
        }
        val orderedASC = selectOrderByASC!!.getResults()
        assertEquals(Priority.LOW, orderedASC[0].priority)
        assertEquals(Priority.CRITICAL, orderedASC[orderedASC.size - 1].priority)

        var selectOrderByDESC: SelectStatement<UserAccount>? = null
        database {
            UserAccountTable { table ->
                selectOrderByDESC = table SELECT ORDER_BY (table.status to DESC)
            }
        }
        val orderedDESC = selectOrderByDESC!!.getResults()
        // After UPDATE in Section 5, user1 is BANNED (highest ordinal 3)
        assertEquals(UserStatus.BANNED, orderedDESC[0].status)
        assertEquals(UserStatus.ACTIVE, orderedDESC[orderedDESC.size - 1].status)

        // Section 8: DELETE with enum WHERE clause
        database {
            UserAccountTable { table ->
                table DELETE WHERE (table.status EQ UserStatus.BANNED)
            }
        }

        var selectAfterDelete: SelectStatement<UserAccount>? = null
        database {
            UserAccountTable {
                selectAfterDelete = it SELECT X
            }
        }
        val remainingUsers = selectAfterDelete!!.getResults()
        assertEquals(false, remainingUsers.any { it.status == UserStatus.BANNED })
    }

    /**
     * Test for new SQL string aggregate and formatting functions
     * Tests group_concat and printf functions
     */
    fun testStringAggregateFunctions() = Database(getNewAPIDBConfig(), true).databaseAutoClose { database ->
        // Clear and insert test data
        val book0 = Book(name = "Book A", author = "Author X", pages = 100, price = 10.50)
        val book1 = Book(name = "Book B", author = "Author X", pages = 200, price = 20.99)
        val book2 = Book(name = "Book C", author = "Author Y", pages = 300, price = 30.00)

        database {
            BookTable { table ->
                table DELETE X
                table INSERT listOf(book0, book1, book2)
            }
        }

        // Test group_concat - concatenate book names by author
        var groupConcatStatement: SelectStatement<Book>? = null
        database {
            BookTable { table ->
                groupConcatStatement = table SELECT GROUP_BY(author) HAVING (group_concat(name, ",") LIKE "%Book A%")
            }
        }
        assertEquals(1, groupConcatStatement?.getResults()?.size)
        assertEquals("Author X", groupConcatStatement?.getResults()?.first()?.author)

        // Test printf - format price as currency
        var printfStatement: SelectStatement<Book>? = null
        database {
            BookTable { table ->
                printfStatement = table SELECT WHERE (printf("%.2f", name) LIKE "%.2f")
            }
        }
        // Printf formats the value, we're just checking it can be used in queries
        assertNotEquals(null, printfStatement?.getResults())
    }

    /**
     * Covers the string arguments of `replace`, `instr`, `printf` and `group_concat`, which are written into the SQL
     * as literals: a `'` in one is part of the string, so it neither breaks the statement nor changes what it does.
     */
    fun testFunctionStringArguments() = Database(getNewAPIDBConfig()).databaseAutoClose { database ->
        database {
            BookTable INSERT listOf(
                Book(name = "It's Kotlin", author = "Pat O'Brien", price = 10.0, pages = 100),
                Book(name = "Plain Title", author = "Sam Lee", price = 20.0, pages = 200),
            )
        }
        lateinit var replaced: SelectStatement<Book>
        lateinit var withQuote: SelectStatement<Book>
        lateinit var injected: SelectStatement<Book>
        lateinit var names: SelectStatement<BookNames>
        lateinit var labels: SelectStatement<BookLabel>
        database {
            BookTable { table ->
                replaced = table SELECT WHERE(replace(name, "It's", "It is") EQ "It is Kotlin")
                withQuote = table SELECT WHERE(instr(author, "'") GT 0)
                // Before the fix, this ended the literal and made the condition true for every row
                injected = table SELECT WHERE(instr(name, "zzz') + 1 + ('") GT 0)
                names = table SELECT (group_concat(name, "' ") AS BookNames::names)
                labels = table SELECT (printf("it's %s", name) AS BookLabel::label) ORDER_BY (name to ASC)
            }
        }
        assertEquals(listOf("It's Kotlin"), replaced.getResults().map { it.name })
        assertEquals(listOf("Pat O'Brien"), withQuote.getResults().map { it.author })
        assertEquals(0, injected.getResults().size)
        assertEquals(listOf("It's Kotlin", "Plain Title"), names.getResults().single().names!!.split("' ").sorted())
        assertEquals(listOf("it's It's Kotlin", "it's Plain Title"), labels.getResults().map { it.label })
    }

    /**
     * Covers comparing two elements when one or both are functions: a column is qualified by its table's name, and a
     * function is written as it is, as `book.length(name)` isn't SQL.
     */
    fun testFunctionComparisons() = Database(getNewAPIDBConfig()).databaseAutoClose { database ->
        database {
            BookTable INSERT listOf(
                Book(name = "Short", author = "Ann", price = 10.0, pages = 3),
                Book(name = "A Longer Title", author = "Ann", price = 20.0, pages = 300),
                Book(name = "Equal", author = "Bob", price = 30.0, pages = 5),
            )
            UserAccountTable INSERT listOf(
                UserAccount(id = null, username = "ann", email = "ann@example.com", status = UserStatus.ACTIVE, priority = Priority.LOW, notes = null),
                UserAccount(id = null, username = "bob", email = "bob@example.com", status = UserStatus.ACTIVE, priority = Priority.HIGH, notes = null),
                UserAccount(id = null, username = "cat", email = "cat@example.com", status = UserStatus.INACTIVE, priority = Priority.MEDIUM, notes = null),
            )
        }
        lateinit var functionToColumn: SelectStatement<Book>
        lateinit var columnToFunction: SelectStatement<Book>
        lateinit var functionToFunction: SelectStatement<BookAuthor>
        lateinit var strings: SelectStatement<Book>
        lateinit var enums: SelectStatement<StatusStats>
        database {
            BookTable { table ->
                // Names longer than their page count
                functionToColumn = table SELECT WHERE(length(name) GT pages)
                columnToFunction = table SELECT WHERE(pages EQ length(name))
                // Authors whose books differ in length
                functionToFunction = table SELECT GROUP_BY<BookAuthor>(author) HAVING (max(pages) GT min(pages))
                strings = table SELECT WHERE(upper(author) NEQ author)
            }
            UserAccountTable { table ->
                // Statuses whose users differ in priority
                enums = table SELECT listOf(
                    count(X) AS StatusStats::users,
                    group_concat(notes, ",") AS StatusStats::notes,
                    max(priority) AS StatusStats::highestPriority,
                ) GROUP_BY status HAVING (max(priority) NEQ min(priority))
            }
        }
        assertEquals(listOf("Short"), functionToColumn.getResults().map { it.name })
        assertEquals(listOf("Equal"), columnToFunction.getResults().map { it.name })
        assertEquals(listOf("Ann"), functionToFunction.getResults().map { it.author })
        assertEquals(3, strings.getResults().size)
        assertEquals(listOf(UserStatus.ACTIVE), enums.getResults().map { it.status })
    }

    /**
     * Test for CREATE_INDEX and CREATE_UNIQUE_INDEX operations
     * Verifies index creation functionality
     */
    fun testIndexOperations() = Database(getNewAPIDBConfig(), true).databaseAutoClose { database ->
        // Test 1: CREATE_INDEX on single column
        database {
            BookTable.CREATE_INDEX("idx_book_name", BookTable.name)
        }

        // Verify index was created by inserting data and querying
        val book1 = Book(name = "Test Book 1", author = "Author 1", pages = 100, price = 10.99)
        val book2 = Book(name = "Test Book 2", author = "Author 2", pages = 200, price = 20.99)
        database {
            BookTable { table ->
                table INSERT listOf(book1, book2)
            }
        }

        lateinit var selectStatement: SelectStatement<Book>
        database {
            selectStatement = BookTable SELECT WHERE (BookTable.name EQ "Test Book 1")
        }
        assertEquals(1, selectStatement.getResults().size)
        assertEquals(book1.name, selectStatement.getResults().first().name)

        // Test 2: CREATE_INDEX on multiple columns
        database {
            PersonWithIdTable.CREATE_INDEX("idx_person_name_age", PersonWithIdTable.name, PersonWithIdTable.age)
        }

        val person1 = PersonWithId(id = null, name = "Alice", age = 25)
        val person2 = PersonWithId(id = null, name = "Bob", age = 30)
        database {
            PersonWithIdTable { table ->
                table INSERT listOf(person1, person2)
            }
        }

        lateinit var personStatement: SelectStatement<PersonWithId>
        database {
            personStatement = PersonWithIdTable SELECT WHERE (PersonWithIdTable.name EQ "Alice" AND (PersonWithIdTable.age EQ 25))
        }
        assertEquals(1, personStatement.getResults().size)
        assertEquals("Alice", personStatement.getResults().first().name)

        // Test 3: CREATE_UNIQUE_INDEX - should enforce uniqueness
        database {
            ProductTable.CREATE_UNIQUE_INDEX("idx_unique_product_name", ProductTable.name)
        }

        val product1 = Product(sku = "SKU-WIDGET-1", name = "Widget", price = 19.99)
        database {
            ProductTable { table ->
                table INSERT product1
            }
        }

        // Try to insert duplicate - should fail. The SKU differs on purpose, so the only constraint the
        // second product can violate is the unique index on 'name'.
        val product2 = Product(sku = "SKU-WIDGET-2", name = "Widget", price = 29.99)
        var duplicateFailed = false
        try {
            database {
                ProductTable { table ->
                    table INSERT product2
                }
            }
        } catch (e: Exception) {
            duplicateFailed = true
        }
        assertEquals(true, duplicateFailed, "Duplicate value should violate unique index")

        // Test 4: Verify empty columns parameter throws exception
        var emptyColumnsFailed = false
        try {
            database {
                BookTable.CREATE_INDEX("idx_empty")
            }
        } catch (e: IllegalArgumentException) {
            emptyColumnsFailed = true
        }
        assertEquals(true, emptyColumnsFailed, "CREATE_INDEX with no columns should throw IllegalArgumentException")

        // Test 5: CREATE_UNIQUE_INDEX with empty columns should also fail
        var emptyUniqueColumnsFailed = false
        try {
            database {
                BookTable.CREATE_UNIQUE_INDEX("idx_unique_empty")
            }
        } catch (e: IllegalArgumentException) {
            emptyUniqueColumnsFailed = true
        }
        assertEquals(true, emptyUniqueColumnsFailed, "CREATE_UNIQUE_INDEX with no columns should throw IllegalArgumentException")
    }

    /**
     * Test for length function with BLOB type
     * Verifies length() works with ClauseBlob parameter
     */
    fun testBlobLengthFunction() = Database(getNewAPIDBConfig(), true).databaseAutoClose { database ->
        val file1 = FileData(id = null, fileName = "small.bin", content = byteArrayOf(0x01, 0x02, 0x03), metadata = "Small file")
        val file2 = FileData(id = null, fileName = "large.bin", content = ByteArray(100) { it.toByte() }, metadata = "Large file")

        database {
            FileDataTable { table ->
                table DELETE X
                table INSERT listOf(file1, file2)
            }
        }

        // Test length function with BLOB
        var lengthStatement: SelectStatement<FileData>? = null
        database {
            FileDataTable { table ->
                lengthStatement = table SELECT WHERE (length(content) EQ 3)
            }
        }
        assertEquals(1, lengthStatement?.getResults()?.size)
        assertEquals("small.bin", lengthStatement?.getResults()?.first()?.fileName)

        // Test length with GT operator
        var lengthGTStatement: SelectStatement<FileData>? = null
        database {
            FileDataTable { table ->
                lengthGTStatement = table SELECT WHERE (length(content) GT 10)
            }
        }
        assertEquals(1, lengthGTStatement?.getResults()?.size)
        assertEquals("large.bin", lengthGTStatement?.getResults()?.first()?.fileName)
    }

    /**
     * Test for compile-time CREATE TABLE generation
     * Verifies that createSQL property contains the correct SQL statement
     */
    fun testCreateSQLGeneration() {
        // Test 1: Simple table with primary key and basic types
        val personSQL = PersonWithIdTable.createSQL
        assertEquals(true, personSQL.contains("CREATE TABLE person_with_id"))
        assertEquals(true, personSQL.contains("id INTEGER PRIMARY KEY"))
        assertEquals(true, personSQL.contains("name TEXT NOT NULL"))
        assertEquals(true, personSQL.contains("age INT NOT NULL"))

        // Test 2: Table with autoincrement
        val studentSQL = StudentWithAutoincrementTable.createSQL
        assertEquals(true, studentSQL.contains("CREATE TABLE student_with_autoincrement"))
        assertEquals(true, studentSQL.contains("id INTEGER PRIMARY KEY AUTOINCREMENT"))

        // A computed property isn't serialized, so it must not become a column: Book declares `title` that way
        assertEquals(false, BookTable.createSQL.contains("title"))

        // Test 3: Table with composite primary key
        val enrollmentSQL = EnrollmentTable.createSQL
        assertEquals(true, enrollmentSQL.contains("CREATE TABLE enrollment"))
        assertEquals(true, enrollmentSQL.contains("PRIMARY KEY(studentId,courseId)"))
        // SQLite doesn't let a table-level PRIMARY KEY imply NOT NULL, so each key column must declare it
        assertEquals(true, enrollmentSQL.contains("studentId BIGINT NOT NULL,"))
        assertEquals(true, enrollmentSQL.contains("courseId BIGINT NOT NULL,"))
        assertEquals(true, FKProductTable.createSQL.contains("categoryId INT NOT NULL,"))
        assertEquals(true, FKProductTable.createSQL.contains("productCode TEXT NOT NULL,"))

        // Test 4: Table with enum fields (stored as INT)
        val userSQL = UserAccountTable.createSQL
        assertEquals(true, userSQL.contains("CREATE TABLE user_account"))
        assertEquals(true, userSQL.contains("status INT NOT NULL"))
        assertEquals(true, userSQL.contains("priority INT NOT NULL"))

        // Test 5: Table with ByteArray (BLOB type)
        val fileSQL = FileDataTable.createSQL
        assertEquals(true, fileSQL.contains("CREATE TABLE file_data"))
        assertEquals(true, fileSQL.contains("content BLOB NOT NULL"))
    }

    /**
     * Test for @Unique annotation
     * Verifies that UNIQUE constraints prevent duplicate values
     */
    fun testUniqueConstraint() {
        val config = DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = {
                CREATE(UniqueEmailTestTable)
            }
        )
        Database(config, true).databaseAutoClose { database ->
            // Verify CREATE SQL contains UNIQUE keyword
            val createSQL = UniqueEmailTestTable.createSQL
            assertEquals(true, createSQL.contains("email TEXT NOT NULL UNIQUE"))

            // Test 1: Insert records with unique emails - should succeed
            val user1 = UniqueEmailTest(null, "alice@example.com", "Alice")
            val user2 = UniqueEmailTest(null, "bob@example.com", "Bob")

            database {
                UniqueEmailTestTable { table ->
                    table INSERT listOf(user1, user2)
                }
            }

            lateinit var selectStatement: SelectStatement<UniqueEmailTest>
            database {
                selectStatement = UniqueEmailTestTable SELECT X
            }
            assertEquals(2, selectStatement.getResults().size)

            // Test 2: Try to insert duplicate email - should fail
            val user3 = UniqueEmailTest(null, "alice@example.com", "Alice Clone")
            var duplicateInsertFailed = false
            try {
                database {
                    UniqueEmailTestTable { table ->
                        table INSERT user3
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                duplicateInsertFailed = true
            }
            assertEquals(true, duplicateInsertFailed, "Duplicate email should violate UNIQUE constraint")

            // Verify only 2 records exist
            database {
                selectStatement = UniqueEmailTestTable SELECT X
            }
            assertEquals(2, selectStatement.getResults().size)
        }
    }

    /**
     * Test for @CollateNoCase annotation
     * Verifies case-insensitive text comparison
     */
    fun testCollateNoCaseConstraint() {
        val config = DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = {
                CREATE(CollateNoCaseTestTable)
            }
        )
        Database(config, true).databaseAutoClose { database ->
            // Verify CREATE SQL contains COLLATE NOCASE
            val createSQL = CollateNoCaseTestTable.createSQL
            assertEquals(true, createSQL.contains("username TEXT NOT NULL COLLATE NOCASE"))
            assertEquals(true, createSQL.contains("email TEXT NOT NULL COLLATE NOCASE UNIQUE"))

            // Test 1: Insert users with different case usernames
            val user1 = CollateNoCaseTest(null, "JohnDoe", "john@example.com", "First user")
            val user2 = CollateNoCaseTest(null, "janedoe", "jane@example.com", "Second user")
            database {
                CollateNoCaseTestTable { table ->
                    table INSERT listOf(user1, user2)
                }
            }

            // Test 2: Case-insensitive search
            var selectStatement: SelectStatement<CollateNoCaseTest>? = null
            database {
                CollateNoCaseTestTable { table ->
                    selectStatement = table SELECT WHERE (username EQ "johndoe")  // lowercase query
                }
            }
            val results1 = selectStatement!!.getResults()
            assertEquals(1, results1.size)
            assertEquals("JohnDoe", results1[0].username)  // Original case preserved

            // Test 3: Another case variant
            database {
                CollateNoCaseTestTable { table ->
                    selectStatement = table SELECT WHERE (username EQ "JOHNDOE")  // uppercase query
                }
            }
            val results2 = selectStatement!!.getResults()
            assertEquals(1, results2.size)
            assertEquals("JohnDoe", results2[0].username)

            // Test 4: UNIQUE + NOCASE - duplicate email in different case should fail
            val user3 = CollateNoCaseTest(null, "alice", "JOHN@EXAMPLE.COM", "Duplicate email")
            var duplicateFailed = false
            try {
                database {
                    CollateNoCaseTestTable { table ->
                        table INSERT user3
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                duplicateFailed = true
            }
            assertEquals(true, duplicateFailed, "Duplicate email (different case) should violate UNIQUE constraint with NOCASE")
        }
    }

    /**
     * Test for @CompositeUnique annotation
     * Verifies multi-column uniqueness constraints with multiple groups
     */
    fun testCompositeUniqueConstraint() {
        val config = DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = {
                CREATE(CompositeUniqueTestTable)
            }
        )
        Database(config, true).databaseAutoClose { database ->
            // Verify CREATE SQL contains composite UNIQUE constraints
            val createSQL = CompositeUniqueTestTable.createSQL
            assertEquals(true, createSQL.contains("UNIQUE(groupA,groupB)"))
            assertEquals(true, createSQL.contains("UNIQUE(groupC,groupD)"))

            // Test 1: Insert records with unique combinations
            val record1 = CompositeUniqueTest(null, "A1", 1, "C1", "D1", "First record")
            val record2 = CompositeUniqueTest(null, "A1", 2, "C1", "D2", "Different groupB")
            val record3 = CompositeUniqueTest(null, "A2", 1, "C2", "D1", "Different groupA")
            database {
                CompositeUniqueTestTable { table ->
                    table INSERT listOf(record1, record2, record3)
                }
            }

            lateinit var selectStatement: SelectStatement<CompositeUniqueTest>
            database {
                selectStatement = CompositeUniqueTestTable SELECT X
            }
            assertEquals(3, selectStatement.getResults().size)

            // Test 2: Try to insert duplicate (groupA, groupB) - should fail
            val record4 = CompositeUniqueTest(null, "A1", 1, "C3", "D3", "Duplicate group 0")
            var duplicateGroup0Failed = false
            try {
                database {
                    CompositeUniqueTestTable { table ->
                        table INSERT record4
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                duplicateGroup0Failed = true
            }
            assertEquals(true, duplicateGroup0Failed, "Duplicate (groupA, groupB) should violate group 0 UNIQUE constraint")

            // Test 3: Try to insert duplicate (groupC, groupD) - should fail
            val record5 = CompositeUniqueTest(null, "A3", 3, "C1", "D1", "Duplicate group 1")
            var duplicateGroup1Failed = false
            try {
                database {
                    CompositeUniqueTestTable { table ->
                        table INSERT record5
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                duplicateGroup1Failed = true
            }
            assertEquals(true, duplicateGroup1Failed, "Duplicate (groupC, groupD) should violate group 1 UNIQUE constraint")

            // Verify still only 3 records
            database {
                selectStatement = CompositeUniqueTestTable SELECT X
            }
            assertEquals(3, selectStatement.getResults().size)
        }
    }

    /**
     * Test for multiple @CompositeUnique groups on same property
     * Verifies a property can participate in multiple composite constraints
     */
    fun testMultiGroupCompositeUnique() {
        val config = DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = {
                CREATE(MultiGroupUniqueTestTable)
            }
        )
        Database(config, true).databaseAutoClose { database ->
            // Verify CREATE SQL contains both composite UNIQUE constraints
            val createSQL = MultiGroupUniqueTestTable.createSQL
            assertEquals(true, createSQL.contains("UNIQUE(userId,eventType)"))
            assertEquals(true, createSQL.contains("UNIQUE(userId,timestamp)"))

            // Test 1: Insert valid records
            val event1 = MultiGroupUniqueTest(null, 1, "login", 1000L, "User 1 login")
            val event2 = MultiGroupUniqueTest(null, 1, "logout", 2000L, "User 1 logout")
            val event3 = MultiGroupUniqueTest(null, 2, "login", 1000L, "User 2 login")
            database {
                MultiGroupUniqueTestTable { table ->
                    table INSERT listOf(event1, event2, event3)
                }
            }

            lateinit var selectStatement: SelectStatement<MultiGroupUniqueTest>
            database {
                selectStatement = MultiGroupUniqueTestTable SELECT X
            }
            assertEquals(3, selectStatement.getResults().size)

            // Test 2: Try duplicate (userId, eventType) - should fail group 0 constraint
            val event4 = MultiGroupUniqueTest(null, 1, "login", 3000L, "Duplicate userId+eventType")
            var duplicateGroup0Failed = false
            try {
                database {
                    MultiGroupUniqueTestTable { table ->
                        table INSERT event4
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                duplicateGroup0Failed = true
            }
            assertEquals(true, duplicateGroup0Failed, "Duplicate (userId, eventType) should violate group 0 constraint")

            // Test 3: Try duplicate (userId, timestamp) - should fail group 1 constraint
            val event5 = MultiGroupUniqueTest(null, 2, "logout", 1000L, "Duplicate userId+timestamp")
            var duplicateGroup1Failed = false
            try {
                database {
                    MultiGroupUniqueTestTable { table ->
                        table INSERT event5
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                duplicateGroup1Failed = true
            }
            assertEquals(true, duplicateGroup1Failed, "Duplicate (userId, timestamp) should violate group 1 constraint")

            // Verify still only 3 records
            database {
                selectStatement = MultiGroupUniqueTestTable SELECT X
            }
            assertEquals(3, selectStatement.getResults().size)
        }
    }

    /**
     * Test for combined constraints
     * Verifies interaction of @Unique, @CollateNoCase, and NOT NULL
     */
    fun testCombinedConstraints() {
        val config = DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = {
                CREATE(CombinedConstraintsTestTable)
            }
        )
        Database(config, true).databaseAutoClose { database ->
            // Verify CREATE SQL
            val createSQL = CombinedConstraintsTestTable.createSQL
            // Check for key components (order may vary)
            assertEquals(true, createSQL.contains("code TEXT NOT NULL"))
            assertEquals(true, createSQL.contains("COLLATE NOCASE"))
            assertEquals(true, createSQL.contains("UNIQUE"))
            assertEquals(true, createSQL.contains("serial TEXT NOT NULL UNIQUE"))
            assertEquals(true, createSQL.contains("value INT NOT NULL"))

            // Test 1: Insert valid records
            val item1 = CombinedConstraintsTest(null, "CODE123", "SN-001", 100)
            val item2 = CombinedConstraintsTest(null, "CODE456", "SN-002", 200)
            database {
                CombinedConstraintsTestTable { table ->
                    table INSERT listOf(item1, item2)
                }
            }

            lateinit var selectStatement: SelectStatement<CombinedConstraintsTest>
            database {
                selectStatement = CombinedConstraintsTestTable SELECT X
            }
            assertEquals(2, selectStatement.getResults().size)

            // Test 2: Search with different case (NOCASE on code)
            database {
                CombinedConstraintsTestTable { table ->
                    selectStatement = table SELECT WHERE (code EQ "code123")  // lowercase
                }
            }
            assertEquals(1, selectStatement.getResults().size)
            assertEquals("CODE123", selectStatement.getResults()[0].code)

            // Test 3: Try duplicate code (different case) - should fail due to UNIQUE + NOCASE
            val item3 = CombinedConstraintsTest(null, "code123", "SN-003", 300)
            var duplicateCodeFailed = false
            try {
                database {
                    CombinedConstraintsTestTable { table ->
                        table INSERT item3
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                duplicateCodeFailed = true
            }
            assertEquals(true, duplicateCodeFailed, "Duplicate code (different case) should fail")

            // Test 4: Try duplicate serial (case-sensitive) - should fail
            val item4 = CombinedConstraintsTest(null, "CODE789", "SN-001", 400)
            var duplicateSerialFailed = false
            try {
                database {
                    CombinedConstraintsTestTable { table ->
                        table INSERT item4
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                duplicateSerialFailed = true
            }
            assertEquals(true, duplicateSerialFailed, "Duplicate serial should fail")

            // Test 5: Different case serial should succeed (no NOCASE on serial)
            val item5 = CombinedConstraintsTest(null, "CODE789", "sn-001", 400)
            database {
                CombinedConstraintsTestTable { table ->
                    table INSERT item5
                }
            }

            database {
                selectStatement = CombinedConstraintsTestTable SELECT X
            }
            assertEquals(3, selectStatement.getResults().size)
        }
    }

    /**
     * Test NOT NULL constraint enforcement
     * Verifies that non-nullable Kotlin types generate NOT NULL constraints
     */
    fun testNotNullConstraint() {
        Database(getNewAPIDBConfig(), true).databaseAutoClose { database ->
            // Test 1: Verify NOT NULL in CREATE SQL
            val bookSQL = BookTable.createSQL
            assertEquals(true, bookSQL.contains("name TEXT NOT NULL"))
            assertEquals(true, bookSQL.contains("author TEXT NOT NULL"))
            assertEquals(true, bookSQL.contains("pages INT NOT NULL"))
            assertEquals(true, bookSQL.contains("price DOUBLE NOT NULL"))

            // Test 2: Verify nullable columns don't have NOT NULL
            val nullTesterSQL = NullTesterTable.createSQL
            // Nullable columns should not have NOT NULL
            assertEquals(false, nullTesterSQL.contains("paramInt INT NOT NULL"))
            assertEquals(false, nullTesterSQL.contains("paramString TEXT NOT NULL"))
            assertEquals(false, nullTesterSQL.contains("paramDouble DOUBLE NOT NULL"))

            // Test 3: Verify data insertion and retrieval
            val book = Book(name = "Test Book", author = "Test Author", pages = 300, price = 25.99)
            database {
                BookTable { table ->
                    table INSERT book
                }
            }

            lateinit var selectStatement: SelectStatement<Book>
            database {
                selectStatement = BookTable SELECT WHERE (BookTable.name EQ "Test Book")
            }
            val result = selectStatement.getResults().first()
            assertEquals("Test Book", result.name)
            assertEquals("Test Author", result.author)
            assertEquals(300, result.pages)
            assertEquals(25.99, result.price)
        }
    }

    /**
     * Test PRAGMA_FOREIGN_KEYS function
     * Verifies foreign key enforcement can be enabled/disabled
     */
    fun testPragmaForeignKeys() {
        Database(getForeignKeyDBConfig(), true).databaseAutoClose { database ->
            // Test 1: Enable foreign keys
            database {
                PRAGMA_FOREIGN_KEYS(true)
            }

            // Test 2: Insert parent record
            val user = FKUser(id = null, email = "test@example.com", name = "Test User")
            database {
                FKUserTable { table ->
                    table INSERT user
                }
            }

            // Test 3: Insert child record with valid foreign key - should succeed
            val order = FKOrder(id = null, userId = 1L, amount = 99.99, orderDate = "2025-01-15")
            database {
                FKOrderTable { table ->
                    table INSERT order
                }
            }

            lateinit var selectStatement: SelectStatement<FKOrder>
            database {
                selectStatement = FKOrderTable SELECT X
            }
            assertEquals(1, selectStatement.getResults().size)

            // Test 4: Try to insert child with invalid foreign key - should fail
            val invalidOrder = FKOrder(id = null, userId = 999L, amount = 50.0, orderDate = "2025-01-15")
            var foreignKeyViolated = false
            try {
                database {
                    FKOrderTable { table ->
                        table INSERT invalidOrder
                    }
                }
            } catch (e: Exception) {
                foreignKeyViolated = true
            }
            assertEquals(true, foreignKeyViolated, "Insert with invalid foreign key should fail when enforcement is enabled")
        }
    }

    /**
     * Test CASCADE delete behavior with @References
     * Verifies that child rows are automatically deleted when parent is deleted
     */
    fun testForeignKeyCascadeDelete() {
        Database(getForeignKeyDBConfig(), true).databaseAutoClose { database ->
            // Enable foreign keys
            database {
                PRAGMA_FOREIGN_KEYS(true)
            }

            // Insert parent user
            val user1 = FKUser(id = null, email = "alice@example.com", name = "Alice")
            val user2 = FKUser(id = null, email = "bob@example.com", name = "Bob")
            database {
                FKUserTable { table ->
                    table INSERT listOf(user1, user2)
                }
            }

            // Insert orders for both users
            val order1 = FKOrder(id = null, userId = 1L, amount = 99.99, orderDate = "2025-01-15")
            val order2 = FKOrder(id = null, userId = 1L, amount = 49.99, orderDate = "2025-01-16")
            val order3 = FKOrder(id = null, userId = 2L, amount = 29.99, orderDate = "2025-01-17")
            database {
                FKOrderTable { table ->
                    table INSERT listOf(order1, order2, order3)
                }
            }

            // Verify orders exist
            lateinit var selectOrders: SelectStatement<FKOrder>
            database {
                selectOrders = FKOrderTable SELECT X
            }
            assertEquals(3, selectOrders.getResults().size)

            // Delete user 1 - should CASCADE delete their orders
            database {
                FKUserTable { table ->
                    table DELETE WHERE (table.id EQ 1L)
                }
            }

            // Verify user 1's orders are deleted
            database {
                selectOrders = FKOrderTable SELECT X
            }
            val remainingOrders = selectOrders.getResults()
            assertEquals(1, remainingOrders.size)
            assertEquals(2L, remainingOrders[0].userId)

            // Verify user 2 still exists
            lateinit var selectUsers: SelectStatement<FKUser>
            database {
                selectUsers = FKUserTable SELECT X
            }
            assertEquals(1, selectUsers.getResults().size)
            assertEquals("Bob", selectUsers.getResults()[0].name)
        }
    }

    /**
     * Test SET_NULL delete behavior with @References
     * Verifies that child foreign keys are set to NULL when parent is deleted
     */
    fun testForeignKeySetNullDelete() {
        Database(getForeignKeyDBConfig(), true).databaseAutoClose { database ->
            // Enable foreign keys
            database {
                PRAGMA_FOREIGN_KEYS(true)
            }

            // Insert parent users
            val user = FKUser(id = null, email = "author@example.com", name = "Author")
            database {
                FKUserTable { table ->
                    table INSERT user
                }
            }

            // Insert posts by the user
            val post1 = FKPost(id = null, authorId = 1L, title = "First Post", content = "Content 1")
            val post2 = FKPost(id = null, authorId = 1L, title = "Second Post", content = "Content 2")
            database {
                FKPostTable { table ->
                    table INSERT listOf(post1, post2)
                }
            }

            // Verify posts exist with author
            lateinit var selectPosts: SelectStatement<FKPost>
            database {
                selectPosts = FKPostTable SELECT X
            }
            val posts = selectPosts.getResults()
            assertEquals(2, posts.size)
            assertEquals(1L, posts[0].authorId)
            assertEquals(1L, posts[1].authorId)

            // Delete the user - should SET_NULL on authorId
            database {
                FKUserTable { table ->
                    table DELETE WHERE (table.id EQ 1L)
                }
            }

            // Verify posts still exist but authorId is NULL
            database {
                selectPosts = FKPostTable SELECT X
            }
            val remainingPosts = selectPosts.getResults()
            assertEquals(2, remainingPosts.size)
            assertEquals(null, remainingPosts[0].authorId)
            assertEquals(null, remainingPosts[1].authorId)
            assertEquals("First Post", remainingPosts[0].title)
            assertEquals("Second Post", remainingPosts[1].title)
        }
    }

    /**
     * Test RESTRICT delete behavior with @References
     * Verifies that parent deletion is prevented when child rows exist
     */
    fun testForeignKeyRestrictDelete() {
        Database(getForeignKeyDBConfig(), true).databaseAutoClose { database ->
            // Enable foreign keys
            database {
                PRAGMA_FOREIGN_KEYS(true)
            }

            // Insert parent user
            val user = FKUser(id = null, email = "user@example.com", name = "User")
            database {
                FKUserTable { table ->
                    table INSERT user
                }
            }

            // Insert profile for the user
            val profile = FKProfile(id = null, userId = 1L, bio = "User bio", website = "https://example.com")
            database {
                FKProfileTable { table ->
                    table INSERT profile
                }
            }

            // Try to delete user - should fail due to RESTRICT
            var deleteFailed = false
            try {
                database {
                    FKUserTable { table ->
                        table DELETE WHERE (table.id EQ 1L)
                    }
                }
            } catch (e: Exception) {
                deleteFailed = true
            }
            assertEquals(true, deleteFailed, "Delete should fail with RESTRICT when child rows exist")

            // Verify user still exists
            lateinit var selectUsers: SelectStatement<FKUser>
            database {
                selectUsers = FKUserTable SELECT X
            }
            assertEquals(1, selectUsers.getResults().size)

            // Delete the profile first
            database {
                FKProfileTable { table ->
                    table DELETE WHERE (table.userId EQ 1L)
                }
            }

            // Now deleting user should succeed
            database {
                FKUserTable { table ->
                    table DELETE WHERE (table.id EQ 1L)
                }
            }

            // Verify user is deleted
            database {
                selectUsers = FKUserTable SELECT X
            }
            assertEquals(0, selectUsers.getResults().size)
        }
    }

    /**
     * Test composite foreign keys with @ForeignKey annotation
     * Verifies multi-column foreign key constraints work correctly
     */
    fun testCompositeForeignKey() {
        Database(getForeignKeyDBConfig(), true).databaseAutoClose { database ->
            // Enable foreign keys
            database {
                PRAGMA_FOREIGN_KEYS(true)
            }

            // Insert parent products with composite primary key
            val product1 = FKProduct(categoryId = 1, productCode = "P001", name = "Widget", price = 19.99)
            val product2 = FKProduct(categoryId = 1, productCode = "P002", name = "Gadget", price = 29.99)
            val product3 = FKProduct(categoryId = 2, productCode = "P001", name = "Tool", price = 39.99)
            database {
                FKProductTable { table ->
                    table INSERT listOf(product1, product2, product3)
                }
            }

            // Insert order items with valid composite foreign keys - should succeed
            val item1 = FKOrderItem(id = null, productCategory = 1, productCode = "P001", quantity = 2, subtotal = 39.98)
            val item2 = FKOrderItem(id = null, productCategory = 2, productCode = "P001", quantity = 1, subtotal = 39.99)
            database {
                FKOrderItemTable { table ->
                    table INSERT listOf(item1, item2)
                }
            }

            lateinit var selectItems: SelectStatement<FKOrderItem>
            database {
                selectItems = FKOrderItemTable SELECT X
            }
            assertEquals(2, selectItems.getResults().size)

            // Try to insert with invalid composite foreign key - should fail
            val invalidItem = FKOrderItem(id = null, productCategory = 1, productCode = "P999", quantity = 1, subtotal = 10.0)
            var foreignKeyViolated = false
            try {
                database {
                    FKOrderItemTable { table ->
                        table INSERT invalidItem
                    }
                }
            } catch (e: Exception) {
                foreignKeyViolated = true
            }
            assertEquals(true, foreignKeyViolated, "Insert with invalid composite foreign key should fail")

            // Delete product (1, P001) - should CASCADE delete item1
            database {
                FKProductTable { table ->
                    table DELETE WHERE ((table.categoryId EQ 1) AND (table.productCode EQ "P001"))
                }
            }

            // Verify item1 is deleted
            database {
                selectItems = FKOrderItemTable SELECT X
            }
            val remainingItems = selectItems.getResults()
            assertEquals(1, remainingItems.size)
            assertEquals(2, remainingItems[0].productCategory)
        }
    }

    /**
     * Test multiple foreign keys to different tables
     * Verifies a table can have foreign keys to multiple parent tables
     */
    fun testMultipleForeignKeys() {
        Database(getForeignKeyDBConfig(), true).databaseAutoClose { database ->
            // Enable foreign keys
            database {
                PRAGMA_FOREIGN_KEYS(true)
            }

            // Insert parent user and post
            val user = FKUser(id = null, email = "commenter@example.com", name = "Commenter")
            database {
                FKUserTable { table ->
                    table INSERT user
                }
            }

            val post = FKPost(id = null, authorId = 1L, title = "Test Post", content = "Post content")
            database {
                FKPostTable { table ->
                    table INSERT post
                }
            }

            // Insert comment with both foreign keys - should succeed
            val comment = FKComment(id = null, authorId = 1L, postId = 1L, content = "Great post!", createdAt = "2025-01-15")
            database {
                FKCommentTable { table ->
                    table INSERT comment
                }
            }

            lateinit var selectComments: SelectStatement<FKComment>
            database {
                selectComments = FKCommentTable SELECT X
            }
            assertEquals(1, selectComments.getResults().size)

            // Try to insert with invalid user foreign key - should fail
            val invalidComment1 = FKComment(id = null, authorId = 999L, postId = 1L, content = "Comment", createdAt = "2025-01-15")
            var userFKViolated = false
            try {
                database {
                    FKCommentTable { table ->
                        table INSERT invalidComment1
                    }
                }
            } catch (e: Exception) {
                userFKViolated = true
            }
            assertEquals(true, userFKViolated, "Insert with invalid user foreign key should fail")

            // Try to insert with invalid post foreign key - should fail
            val invalidComment2 = FKComment(id = null, authorId = 1L, postId = 999L, content = "Comment", createdAt = "2025-01-15")
            var postFKViolated = false
            try {
                database {
                    FKCommentTable { table ->
                        table INSERT invalidComment2
                    }
                }
            } catch (e: Exception) {
                postFKViolated = true
            }
            assertEquals(true, postFKViolated, "Insert with invalid post foreign key should fail")

            // Delete user - should CASCADE delete comment
            database {
                FKUserTable { table ->
                    table DELETE WHERE (table.id EQ 1L)
                }
            }

            // Verify comment is deleted
            database {
                selectComments = FKCommentTable SELECT X
            }
            assertEquals(0, selectComments.getResults().size)
        }
    }

    /**
     * Test CREATE SQL generation for foreign keys
     * Verifies that foreign key constraints are correctly included in CREATE SQL
     */
    fun testForeignKeyCreateSQL() {
        // Test 1: Simple foreign key with @References
        val orderSQL = FKOrderTable.createSQL
        assertEquals(true, orderSQL.contains("REFERENCES fk_user(id)"))
        assertEquals(true, orderSQL.contains("ON DELETE CASCADE"))

        // Test 2: SET_NULL trigger
        val postSQL = FKPostTable.createSQL
        assertEquals(true, postSQL.contains("REFERENCES fk_user(id)"))
        assertEquals(true, postSQL.contains("ON DELETE SET NULL"))

        // Test 3: RESTRICT trigger
        val profileSQL = FKProfileTable.createSQL
        assertEquals(true, profileSQL.contains("REFERENCES fk_user(id)"))
        assertEquals(true, profileSQL.contains("ON DELETE RESTRICT"))

        // Test 4: Composite foreign key with @ForeignKey
        val orderItemSQL = FKOrderItemTable.createSQL
        assertEquals(true, orderItemSQL.contains("FOREIGN KEY"))
        assertEquals(true, orderItemSQL.contains("REFERENCES fk_product"))
        assertEquals(true, orderItemSQL.contains("ON DELETE CASCADE"))

        // Test 5: Multiple foreign keys
        val commentSQL = FKCommentTable.createSQL
        assertEquals(true, commentSQL.contains("REFERENCES fk_user(id)"))
        assertEquals(true, commentSQL.contains("REFERENCES fk_post(id)"))
    }

    /**
     * Test foreign key constraint without PRAGMA_FOREIGN_KEYS enabled
     * Verifies that constraints are not enforced when PRAGMA is not enabled
     */
    fun testForeignKeyWithoutPragma() {
        Database(getForeignKeyDBConfig(), true).databaseAutoClose { database ->
            // Note: NOT enabling PRAGMA_FOREIGN_KEYS

            // Insert parent user
            val user = FKUser(id = null, email = "test@example.com", name = "Test")
            database {
                FKUserTable { table ->
                    table INSERT user
                }
            }

            // Insert order with INVALID foreign key - should succeed without enforcement
            val invalidOrder = FKOrder(id = null, userId = 999L, amount = 99.99, orderDate = "2025-01-15")
            database {
                FKOrderTable { table ->
                    table INSERT invalidOrder
                }
            }

            // Verify order was inserted despite invalid foreign key
            lateinit var selectOrders: SelectStatement<FKOrder>
            database {
                selectOrders = FKOrderTable SELECT X
            }
            assertEquals(1, selectOrders.getResults().size)
            assertEquals(999L, selectOrders.getResults()[0].userId)
        }
    }

    /**
     * Test for @Default annotation - CREATE SQL generation
     * Verifies that createSQL property contains the DEFAULT clause
     */
    fun testDefaultValuesCreateSQL() {
        // Test 1: Basic default values
        val defaultValuesSQL = DefaultValuesTestTable.createSQL
        assertEquals(true, defaultValuesSQL.contains("CREATE TABLE default_values_test"))
        assertEquals(true, defaultValuesSQL.contains("status TEXT NOT NULL DEFAULT 'active'"))
        assertEquals(true, defaultValuesSQL.contains("loginCount INT NOT NULL DEFAULT 0"))
        assertEquals(true, defaultValuesSQL.contains("isEnabled BOOLEAN NOT NULL DEFAULT 1"))
        assertEquals(true, defaultValuesSQL.contains("createdAt TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"))

        // Test 2: Nullable columns with default values
        val defaultNullableSQL = DefaultNullableTestTable.createSQL
        assertEquals(true, defaultNullableSQL.contains("CREATE TABLE default_nullable_test"))
        assertEquals(true, defaultNullableSQL.contains("availability TEXT DEFAULT 'In Stock'"))
        assertEquals(true, defaultNullableSQL.contains("quantity INT DEFAULT 100"))
        assertEquals(true, defaultNullableSQL.contains("discount DOUBLE DEFAULT 0.0"))

        // Test 3: Default values with foreign key SET_DEFAULT trigger
        val defaultFKChildSQL = DefaultFKChildTable.createSQL
        assertEquals(true, defaultFKChildSQL.contains("CREATE TABLE default_fk_child"))
        assertEquals(true, defaultFKChildSQL.contains("parentId BIGINT NOT NULL DEFAULT 0"))
        assertEquals(true, defaultFKChildSQL.contains("FOREIGN KEY (parentId) REFERENCES default_fk_parent(id) ON DELETE SET DEFAULT"))
    }

    /**
     * Test for @Default annotation - INSERT behavior
     * Verifies that default values are used when columns are omitted in INSERT
     * Note: SQLlin's INSERT operation always provides all column values from data classes,
     * so this test verifies the schema is correctly generated with DEFAULT clauses
     */
    fun testDefaultValuesInsert() {
        val config = DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = {
                CREATE(DefaultValuesTestTable)
                CREATE(DefaultNullableTestTable)
            }
        )
        Database(config, true).databaseAutoClose { database ->
            // Test 1: Verify CREATE SQL contains DEFAULT clauses
            val createSQL = DefaultValuesTestTable.createSQL
            assertEquals(true, createSQL.contains("DEFAULT 'active'"))
            assertEquals(true, createSQL.contains("DEFAULT 0"))
            assertEquals(true, createSQL.contains("DEFAULT 1"))

            // Test 2: Insert record with all fields specified
            val record1 = DefaultValuesTest(
                id = null,
                name = "Test User",
                status = "active",
                loginCount = 0,
                isEnabled = true,
                createdAt = "2025-12-14 00:00:00"
            )
            database {
                DefaultValuesTestTable { table ->
                    table INSERT record1
                }
            }

            // Verify insertion
            lateinit var selectStatement: SelectStatement<DefaultValuesTest>
            database {
                selectStatement = DefaultValuesTestTable SELECT X
            }
            assertEquals(1, selectStatement.getResults().size)
            val result = selectStatement.getResults()[0]
            assertEquals("Test User", result.name)
            assertEquals("active", result.status)
            assertEquals(0, result.loginCount)

            // Test 3: Nullable columns with default values
            val nullableRecord = DefaultNullableTest(
                id = null,
                name = "Product A",
                availability = "In Stock",
                quantity = 100,
                discount = 0.0
            )
            database {
                DefaultNullableTestTable { table ->
                    table INSERT nullableRecord
                }
            }

            lateinit var selectNullable: SelectStatement<DefaultNullableTest>
            database {
                selectNullable = DefaultNullableTestTable SELECT X
            }
            assertEquals(1, selectNullable.getResults().size)
            assertEquals("Product A", selectNullable.getResults()[0].name)
            assertEquals("In Stock", selectNullable.getResults()[0].availability)
            assertEquals(100, selectNullable.getResults()[0].quantity)
        }
    }

    /**
     * Test for @Default annotation with foreign key ON_DELETE_SET_DEFAULT trigger
     * Verifies that default values are correctly included in CREATE TABLE statements with foreign keys
     */
    fun testDefaultValuesWithForeignKey() {
        val config = DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = {
                PRAGMA_FOREIGN_KEYS(true)
                CREATE(DefaultFKParentTable)
                CREATE(DefaultFKChildTable)
            }
        )
        Database(config, true).databaseAutoClose { database ->
            // Test 1: Verify CREATE SQL contains DEFAULT with foreign key
            val childSQL = DefaultFKChildTable.createSQL
            assertEquals(true, childSQL.contains("parentId BIGINT NOT NULL DEFAULT 0"))
            assertEquals(true, childSQL.contains("FOREIGN KEY"))
            assertEquals(true, childSQL.contains("REFERENCES default_fk_parent(id)"))
            assertEquals(true, childSQL.contains("ON DELETE SET DEFAULT"))

            // Test 2: Verify we can insert parent and child records
            val parent = DefaultFKParent(id = null, name = "Test Parent")
            database {
                DefaultFKParentTable { table ->
                    table INSERT parent
                }
            }

            // Get parent ID
            lateinit var parentSelect: SelectStatement<DefaultFKParent>
            database {
                parentSelect = DefaultFKParentTable SELECT X
            }
            val parents = parentSelect.getResults()
            assertEquals(1, parents.size)
            val parentId = parents[0].id!!

            // Test 3: Insert child record referencing parent
            val child = DefaultFKChild(id = null, parentId = parentId, description = "Test Child")
            database {
                DefaultFKChildTable { table ->
                    table INSERT child
                }
            }

            // Verify child was inserted with correct parentId
            lateinit var childSelect: SelectStatement<DefaultFKChild>
            database {
                childSelect = DefaultFKChildTable SELECT X
            }
            assertEquals(1, childSelect.getResults().size)
            assertEquals(parentId, childSelect.getResults()[0].parentId)
            assertEquals("Test Child", childSelect.getResults()[0].description)
        }
    }

    private fun getDefaultDBConfig(): DatabaseConfiguration =
        DatabaseConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = {
                it.execSQL(SQL_CREATE_BOOK)
                it.execSQL(SQL_CREATE_CATEGORY)
            }
        )

    private fun getNewAPIDBConfig(): DSLDBConfiguration =
        DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = {
                CREATE(BookTable)
                CREATE(CategoryTable)
                CREATE(PersonWithIdTable)
                CREATE(ProductTable)
                CREATE(StudentWithAutoincrementTable)
                CREATE(EnrollmentTable)
                CREATE(FileDataTable)
                CREATE(UserAccountTable)
                CREATE(TaskTable)
                CREATE(UniqueEmailTestTable)
                CREATE(CollateNoCaseTestTable)
                CREATE(CompositeUniqueTestTable)
                CREATE(MultiGroupUniqueTestTable)
                CREATE(CombinedConstraintsTestTable)
            }
        )

    private fun getResultColumnDBConfig(): DSLDBConfiguration =
        DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = {
                CREATE(BookTable)
                CREATE(UserAccountTable)
                CREATE(DefaultValuesTestTable)
            }
        )

    @OptIn(ExperimentalDSLDatabaseAPI::class)
    private fun getFtsDBConfig(): DSLDBConfiguration =
        DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = {
                CREATE(ArticleTable)
                CREATE(NoteTable)
                CREATE(BookTable)
            }
        )

    private fun getForeignKeyDBConfig(): DSLDBConfiguration =
        DSLDBConfiguration(
            name = DATABASE_NAME,
            path = path,
            version = 1,
            create = {
                CREATE(FKUserTable)
                CREATE(FKOrderTable)
                CREATE(FKPostTable)
                CREATE(FKProfileTable)
                CREATE(FKProductTable)
                CREATE(FKOrderItemTable)
                CREATE(FKCommentTable)
            }
        )
}