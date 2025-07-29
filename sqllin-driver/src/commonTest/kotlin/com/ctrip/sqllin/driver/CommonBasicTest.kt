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

package com.ctrip.sqllin.driver

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals

/**
 * The sqllin-driver common basic test.
 * @author yaqiao
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
                connection.executeInsert(SQL.INSERT_BOOK, arrayOf("The Lost Symbol", null, 510, 19.95, null))
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
                connection.executeUpdateDelete(SQL.INSERT_BOOK, arrayOf("The Da Vinci Code", "Dan Brown", 454, 16.96))
                connection.executeUpdateDelete(SQL.INSERT_BOOK, arrayOf("The Lost Symbol", "Dan Brown", 510, 19.95))
            }

            it.withTransaction { connection ->
                connection.executeUpdateDelete(SQL.UPDATE_BOOK, arrayOf(18.99, "The Da Vinci Code"))
                connection.executeUpdateDelete(SQL.UPDATE_BOOK, arrayOf(25.88, "The Lost Symbol"))
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
                connection.executeUpdateDelete(SQL.INSERT_BOOK, arrayOf("The Da Vinci Code", "Dan Brown", 454, 16.96))
                connection.executeUpdateDelete(SQL.INSERT_BOOK, arrayOf("The Lost Symbol", "Dan Brown", 510, 19.95))
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
                    connection.executeUpdateDelete(SQL.UPDATE_BOOK, arrayOf(18.99, "The Da Vinci Code"))
                    connection.executeUpdateDelete(SQL.UPDATE_BOOK, arrayOf(25.88, "The Lost Symbol"))
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