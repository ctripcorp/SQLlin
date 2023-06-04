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

import com.ctrip.sqllin.driver.platform.separatorChar
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Native unit test
 * @author yaqiao
 */

class NativeTest {

    private val path = getPlatformStringPath()
    private val commonTest = CommonBasicTest(path.toDatabasePath())

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
    fun testConcurrency() = commonTest.testConcurrency()

    @AfterTest
    fun setDown() {
        listOf(
            "$path$separatorChar${SQL.DATABASE_NAME}",
            "$path$separatorChar${SQL.DATABASE_NAME}-shm",
            "$path$separatorChar${SQL.DATABASE_NAME}-wal",
        ).forEach {
            val result = deleteFile(it)
            println("Delete file: $it, result: $result")
        }
    }
}