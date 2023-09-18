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

package com.ctrip.sqllin.dsl

import com.ctrip.sqllin.driver.DatabaseConfiguration
import com.ctrip.sqllin.driver.DatabasePath
import com.ctrip.sqllin.dsl.sql.X
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.clause.OrderByWay.ASC
import com.ctrip.sqllin.dsl.sql.clause.OrderByWay.DESC
import com.ctrip.sqllin.dsl.sql.statement.SelectStatement
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The sqllin-dsl common test
 * @author yaqiao
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

    fun testInsert() = Database(getDefaultDBConfig()).databaseAutoClose { database ->
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

    fun testDelete() = Database(getDefaultDBConfig()).databaseAutoClose { database ->
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
        assertEquals(true, statement!!.getResults().any { it == book2 })

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

    fun testUpdate() = Database(getDefaultDBConfig()).databaseAutoClose { database ->
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
        assertEquals(true, statement!!.getResults().any { it == book2 })

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
        assertEquals(true, newResult!!.getResults().any { it == newBook2 })
    }

    fun testSelectWhereClause() = Database(getDefaultDBConfig()).databaseAutoClose { database ->
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

    fun testSelectOrderByClause() = Database(getDefaultDBConfig()).databaseAutoClose { database ->
        val book0 = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96)
        val book1 = Book(name = "Kotlin Cookbook", author = "Ken Kousen", pages = 251, price = 37.72)
        val book2 = Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95)
        var statementOfOrderBy: SelectStatement<Book>? = null
        var statementOfWhereAndOrderBy: SelectStatement<Book>? = null
        database {
            BookTable { table ->
                table INSERT listOf(book0, book1, book2)
                statementOfOrderBy = table SELECT ORDER_BY(price to DESC)
                statementOfWhereAndOrderBy = table SELECT WHERE(author EQ "Dan Brown") ORDER_BY mapOf(pages to ASC)
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

        assertEquals(2, statementOfWhereAndOrderBy?.getResults()?.size)
        statementOfWhereAndOrderBy!!.getResults().forEachIndexed { index, book ->
            val actualBook = when (index) {
                0 -> book0
                1 -> book2
                else -> throw IllegalStateException("Select got some wrong")
            }
            assertEquals(actualBook, book)
        }
    }

    fun testSelectLimitAndOffsetClause() = Database(getDefaultDBConfig()).databaseAutoClose { database ->
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

    fun testGroupByAndHavingClause() = Database(getDefaultDBConfig()).databaseAutoClose { database ->
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

    fun testUnionSelect() = Database(getDefaultDBConfig()).databaseAutoClose { database ->
        val book0 = Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96)
        val book1 = Book(name = "Kotlin Cookbook", author = "Ken Kousen", pages = 251, price = 37.72)
        val book2 = Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95)
        val book3 = Book(name = "Kotlin Guide Pratique", author = "Ken Kousen", pages = 398, price = 39.99)
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
        statement!!.getResults().forEach {
            println(it)
        }
        assertEquals(7, statement!!.getResults().size)
        assertEquals(2, statement!!.getResults().count { it == book0 })
        assertEquals(2, statement!!.getResults().count { it == book1 })
        assertEquals(1, statement!!.getResults().count { it == book2 })
        assertEquals(2, statement!!.getResults().count { it == book3 })
    }

    fun testFunction() = Database(getDefaultDBConfig()).databaseAutoClose { database ->
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
    }

    fun testJoinClause() = Database(getDefaultDBConfig()).databaseAutoClose { database ->
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
}