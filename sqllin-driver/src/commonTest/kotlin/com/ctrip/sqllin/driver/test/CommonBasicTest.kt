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

package com.ctrip.sqllin.driver.test

import com.ctrip.sqllin.driver.DatabaseConfiguration
import com.ctrip.sqllin.driver.DatabasePath
import com.ctrip.sqllin.driver.openDatabase
import com.ctrip.sqllin.driver.withQuery
import com.ctrip.sqllin.driver.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * The sqllin-driver common basic test.
 * @author Yuang Qiao
 */

class CommonBasicTest(private val path: DatabasePath) {

    private class Book(
        val name: String,
        val author: String?,
        val pages: Int,
        val price: Double,
        val array: ByteArray?,
    )

    private val bookList = listOf(
        Book(name = "The Da Vinci Code", author = "Dan Brown", pages = 454, price = 16.96, byteArrayOf()),
        Book(name = "The Lost Symbol", author = "Dan Brown", pages = 510, price = 19.95, byteArrayOf(1, 2, 3)),
        Book(name = "", author = "Dan Brown", pages = 454, price = 16.96, byteArrayOf()),
        Book(name = "The Lost Symbol", author = null, pages = 510, price = 19.95, null),
    )

    fun testCreateAndUpgrade() {
        var isCreate0 = false
        var isUpgrade0 = false
        val config0 = DatabaseConfiguration(
            name = SQL.DATABASE_NAME,
            path = path,
            version = 1,
            isReadOnly = true,
            create = {
                isCreate0 = true
                it.execSQL(SQL.CREATE_BOOK)
            },
            upgrade = { _, _, _ ->
                isUpgrade0 = true
            }
        )
        openDatabase(config0) {
            assertEquals(true, isCreate0)
            assertEquals(false, isUpgrade0)
        }

        var isCreate1 = false
        var isUpgrade1 = false
        val config1 = DatabaseConfiguration(
            name = SQL.DATABASE_NAME,
            path = path,
            version = 2,
            isReadOnly = true,
            create = {
                isCreate1 = true
                it.execSQL(SQL.CREATE_BOOK)
            },
            upgrade = { _, _, _ ->
                isUpgrade1 = true
            }
        )
        openDatabase(config1) {
            assertEquals(false, isCreate1)
            assertEquals(true, isUpgrade1)
        }
    }

    fun testInsert() {
        val readWriteConfig = getDefaultDBConfig(false)
        openDatabase(readWriteConfig) {
            it.withTransaction { connection ->
                connection.executeInsert(SQL.INSERT_BOOK, arrayOf("The Da Vinci Code", "Dan Brown", 454, 16.96, byteArrayOf()))
                connection.executeInsert(SQL.INSERT_BOOK, arrayOf("The Lost Symbol", "Dan Brown", 510, 19.95, byteArrayOf(1, 2, 3)))
                connection.executeInsert(SQL.INSERT_BOOK, arrayOf("", "Dan Brown", 454, 16.96, byteArrayOf()))
                connection.executeInsert(SQL.INSERT_BOOK, arrayOf<Any?>("The Lost Symbol", null, 510, 19.95, null))
            }
        }
        val readOnlyConfig = getDefaultDBConfig(true)
        openDatabase(readOnlyConfig) {
            it.withQuery(SQL.QUERY_BOOK, null) { cursor ->
                cursor.forEachRow { rowIndex ->
                    val book = bookList[rowIndex]
                    var columnIndex = 0
                    assertEquals(book.name, cursor.getString(++columnIndex))
                    assertEquals(book.author, cursor.getString(++columnIndex))
                    assertEquals(book.pages, cursor.getInt(++columnIndex))
                    assertEquals(book.price, cursor.getDouble(++columnIndex))
                    assertEquals(book.array?.size, cursor.getByteArray(++columnIndex)?.size)
                }
            }
        }
    }

    fun testUpdate() {
        val readWriteConfig = getDefaultDBConfig(false)
        openDatabase(readWriteConfig) {
            it.withTransaction { connection ->
                connection.executeUpdateDelete(SQL.INSERT_BOOK, arrayOf<Any>("The Da Vinci Code", "Dan Brown", 454, 16.96))
                connection.executeUpdateDelete(SQL.INSERT_BOOK, arrayOf<Any>("The Lost Symbol", "Dan Brown", 510, 19.95))
            }

            it.withTransaction { connection ->
                connection.executeUpdateDelete(SQL.UPDATE_BOOK, arrayOf<Any>(18.99, "The Da Vinci Code"))
                connection.executeUpdateDelete(SQL.UPDATE_BOOK, arrayOf<Any>(25.88, "The Lost Symbol"))
            }
        }
        val readOnlyConfig = getDefaultDBConfig(true)
        openDatabase(readOnlyConfig) {
            it.withQuery(SQL.QUERY_BOOK, null) { cursor ->
                cursor.forEachRow { rowIndex ->
                    val (name, price) = when (rowIndex) {
                        0 -> "The Da Vinci Code" to 18.99
                        1 -> "The Lost Symbol" to 25.88
                        else -> throw IllegalArgumentException("This row don't exit.")
                    }
                    assertEquals(name, cursor.getString(1))
                    assertEquals(price, cursor.getDouble(4))
                }
            }
        }
    }

    fun testDelete() {
        val readWriteConfig = getDefaultDBConfig(false)
        openDatabase(readWriteConfig) {
            it.withTransaction { connection ->
                connection.executeUpdateDelete(SQL.INSERT_BOOK, arrayOf<Any>("The Da Vinci Code", "Dan Brown", 454, 16.96))
                connection.executeUpdateDelete(SQL.INSERT_BOOK, arrayOf<Any>("The Lost Symbol", "Dan Brown", 510, 19.95))
            }

            it.executeUpdateDelete(SQL.DELETE_BOOK, arrayOf(500))
        }
        val readOnlyConfig = getDefaultDBConfig(true)
        openDatabase(readOnlyConfig) {
            it.withQuery(SQL.QUERY_BOOK, null) { cursor ->
                cursor.forEachRow {
                    val book = bookList.first()
                    var columnIndex = 0
                    assertEquals(book.name, cursor.getString(++columnIndex))
                    assertEquals(book.author, cursor.getString(++columnIndex))
                    assertEquals(book.pages, cursor.getInt(++columnIndex))
                    assertEquals(book.price, cursor.getDouble(++columnIndex))
                }
            }
        }
    }

    fun testTransaction() {
        val readWriteConfig = getDefaultDBConfig(false)
        openDatabase(readWriteConfig) {
            it.withTransaction { connection ->
                connection.executeInsert(SQL.INSERT_BOOK, arrayOf("The Da Vinci Code", "Dan Brown", 454, 16.96, byteArrayOf()))
                connection.executeInsert(SQL.INSERT_BOOK, arrayOf("The Lost Symbol", "Dan Brown", 510, 19.95, byteArrayOf(1, 2, 3)))
            }

            try {
                it.withTransaction { connection ->
                    connection.executeUpdateDelete(SQL.UPDATE_BOOK, arrayOf<Any>(18.99, "The Da Vinci Code"))
                    connection.executeUpdateDelete(SQL.UPDATE_BOOK, arrayOf<Any>(25.88, "The Lost Symbol"))
                    throw IllegalStateException("Simulate transaction failed.")
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
        val readOnlyConfig = getDefaultDBConfig(true)
        openDatabase(readOnlyConfig) {
            it.withQuery(SQL.QUERY_BOOK, null) { cursor ->
                cursor.forEachRow { rowIndex ->
                    val (name, price) = bookList[rowIndex].run { name to price }
                    assertEquals(name, cursor.getString(1))
                    assertEquals(price, cursor.getDouble(4))
                }
            }
        }
    }

    /**
     * A COMMIT that fails, as one does while a deferred foreign key isn't satisfied, leaves SQLite's transaction open, so
     * it has to be rolled back: its rows are gone, and the next transaction can begin.
     */
    fun testFailedCommit() {
        openDatabase(getDefaultDBConfig(false)) {
            it.execSQL("PRAGMA foreign_keys = ON")
            it.execSQL("CREATE TABLE parent(id INTEGER PRIMARY KEY)")
            it.execSQL("CREATE TABLE child(parentId INT REFERENCES parent(id) DEFERRABLE INITIALLY DEFERRED)")
            assertFails {
                it.withTransaction { connection ->
                    connection.executeInsert("INSERT INTO child VALUES (?)", arrayOf(1L))
                }
            }
            fun children(): Long = it.withQuery("SELECT count(*) FROM child") { cursor -> cursor.next(); cursor.getLong(0) }
            assertEquals(0L, children())
            it.withTransaction { connection ->
                connection.executeInsert("INSERT INTO child VALUES (?)", arrayOf(1L))
                connection.executeInsert("INSERT INTO parent VALUES (?)", arrayOf(1L))
            }
            assertEquals(1L, children())
        }
    }

    /**
     * SQLite rolls a transaction back itself for a constraint with ON CONFLICT ROLLBACK, or a trigger's RAISE(ROLLBACK),
     * so that ending it fails too, which mustn't hide why it was rolled back; and the next transaction can begin.
     */
    fun testRolledBackBySQLite() {
        openDatabase(getDefaultDBConfig(false)) {
            it.execSQL("CREATE TABLE rolled_back(x INT UNIQUE ON CONFLICT ROLLBACK)")
            it.execSQL("CREATE TRIGGER no_negative BEFORE INSERT ON rolled_back WHEN new.x < 0 BEGIN SELECT RAISE(ROLLBACK, 'negative'); END")
            it.executeInsert("INSERT INTO rolled_back VALUES (?)", arrayOf(1L))
            fun failure(value: Long): String = assertFails {
                it.withTransaction { connection ->
                    connection.executeInsert("INSERT INTO rolled_back VALUES (?)", arrayOf(2L))
                    connection.executeInsert("INSERT INTO rolled_back VALUES (?)", arrayOf(value))
                }
            }.message ?: ""
            assertContains(failure(1L), "UNIQUE constraint failed")
            assertContains(failure(-1L), "negative")
            it.withTransaction { connection ->
                connection.executeInsert("INSERT INTO rolled_back VALUES (?)", arrayOf(3L))
            }
            val values = it.withQuery("SELECT x FROM rolled_back ORDER BY x") { cursor ->
                buildList { cursor.forEachRow { add(cursor.getLong(0)) } }
            }
            assertEquals(listOf(1L, 3L), values)
        }
    }

    /**
     * A transaction takes the write lock when it begins, as BEGIN IMMEDIATE does, on every platform: another connection
     * can't write until it ends.
     */
    fun testImmediateTransaction() {
        openDatabase(getDefaultDBConfig(false)) {
            openDatabase(getDefaultDBConfig(false).copy(busyTimeout = 0)) { other ->
                it.withTransaction {
                    assertFails {
                        other.executeInsert(SQL.INSERT_BOOK, arrayOf("The Da Vinci Code", "Dan Brown", 454, 16.96, byteArrayOf()))
                    }
                }
                other.executeInsert(SQL.INSERT_BOOK, arrayOf("The Da Vinci Code", "Dan Brown", 454, 16.96, byteArrayOf()))
            }
        }
    }

    /**
     * SQLite waits as long as `busyTimeout` for a lock that another connection holds.
     */
    fun testBusyTimeout() {
        openDatabase(getDefaultDBConfig(false).copy(busyTimeout = 1234)) {
            assertEquals(1234L, it.withQuery("PRAGMA busy_timeout") { cursor -> cursor.next(); cursor.getLong(0) })
        }
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun testConcurrency() = runTest {
        val readWriteConfig = getDefaultDBConfig(false)
        openDatabase(readWriteConfig) {
            launch(newSingleThreadContext("test0")) {
                val readOnlyConfig = getDefaultDBConfig(true)
                openDatabase(readOnlyConfig) { connection ->
                    connection.withQuery(SQL.QUERY_BOOK, null) { cursor ->
                        cursor.forEachRow { rowIndex ->
                            val book = bookList[rowIndex]
                            var columnIndex = 0
                            assertEquals(book.name, cursor.getString(++columnIndex))
                            assertEquals(book.author, cursor.getString(++columnIndex))
                            assertEquals(book.pages, cursor.getInt(++columnIndex))
                            assertEquals(book.price, cursor.getDouble(++columnIndex))
                            assertEquals(book.array?.size, cursor.getByteArray(++columnIndex)?.size)
                        }
                    }
                }
            }
            it.withTransaction { connection ->
                connection.executeInsert(SQL.INSERT_BOOK, arrayOf("The Da Vinci Code", "Dan Brown", 454, 16.96, byteArrayOf()))
                connection.executeInsert(SQL.INSERT_BOOK, arrayOf("The Lost Symbol", "Dan Brown", 510, 19.95, byteArrayOf(1, 2, 3)))
            }
        }
    }

    private fun getDefaultDBConfig(isReadOnly: Boolean): DatabaseConfiguration =
        DatabaseConfiguration(
            name = SQL.DATABASE_NAME,
            path = path,
            version = 1,
            isReadOnly = isReadOnly,
            create = {
                it.execSQL(SQL.CREATE_BOOK)
                it.execSQL(SQL.CREATE_CATEGORY)
            },
            upgrade = { connection, oldVersion, _ ->
                if (oldVersion == 1)
                    connection.execSQL(SQL.CREATE_CATEGORY)
                if (oldVersion == 2)
                    connection.execSQL(SQL.ASSOCIATE)
            }
        )
}