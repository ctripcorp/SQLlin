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

import cnames.structs.sqlite3
import com.ctrip.sqllin.driver.DatabaseConfiguration
import com.ctrip.sqllin.driver.cinterop.NativeDatabase
import com.ctrip.sqllin.driver.deleteDatabase
import com.ctrip.sqllin.driver.toDatabasePath
import com.ctrip.sqllin.sqlite3.*
import kotlinx.cinterop.*
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Native unit test
 * @author Yuang Qiao
 */

class NativeTest {

    private val path = getPlatformStringPath().toDatabasePath()
    private val commonTest = CommonBasicTest(path)

    @Test
    fun testCreateAndUpgrade() = commonTest.testCreateAndUpgrade()

    @Test
    fun testInsert() = commonTest.testInsert()

    @Test
    fun testUpdate() = commonTest.testUpdate()

    @Test
    fun testDelete() = commonTest.testDelete()

    @Test
    fun testTransaction() = commonTest.testTransaction()

    @Test
    fun testFailedCommit() = commonTest.testFailedCommit()

    @Test
    fun testImmediateTransaction() = commonTest.testImmediateTransaction()

    @Test
    fun testBusyTimeout() = commonTest.testBusyTimeout()

    @Test
    fun testConcurrency() = commonTest.testConcurrency()

    /**
     * A connection keeps SQLite's default lookaside configuration unless one is set, and 0 disables the allocator.
     */
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testLookaside() {
        fun lookasideUsed(db: CPointer<sqlite3>): Int = memScoped {
            sqlite3_exec(db, "CREATE TABLE t(x); INSERT INTO t VALUES (1); SELECT * FROM t;", null, null, null)
            val current = alloc<IntVar>()
            val highwater = alloc<IntVar>()
            sqlite3_db_status(db, SQLITE_DBSTATUS_LOOKASIDE_USED, current.ptr, highwater.ptr, 0)
            highwater.value
        }
        fun lookasideUsed(config: DatabaseConfiguration): Int {
            val database = NativeDatabase.openNativeDatabase(config, ":memory:")
            try {
                return lookasideUsed(database.dbPointer)
            } finally {
                database.close()
            }
        }
        val sqliteDefault = memScoped {
            val db = alloc<CPointerVar<sqlite3>>()
            sqlite3_open_v2(":memory:", db.ptr, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE, null)
            try {
                lookasideUsed(db.value!!)
            } finally {
                sqlite3_close_v2(db.value)
            }
        }
        val config = DatabaseConfiguration(name = SQL.DATABASE_NAME, path = path, version = 1)
        assertEquals(sqliteDefault, lookasideUsed(config))
        assertEquals(0, lookasideUsed(config.copy(lookasideSlotSize = 0, lookasideSlotCount = 0)))
        assertTrue(lookasideUsed(config.copy(lookasideSlotSize = 256, lookasideSlotCount = 32)) > 0)
    }

    @AfterTest
    fun setDown() {
        deleteDatabase(path, SQL.DATABASE_NAME)
    }
}
