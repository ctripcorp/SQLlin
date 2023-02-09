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

import com.ctrip.sqllin.driver.toDatabasePath
import platform.posix.remove
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
    fun testInsert() = commonTest.testInsert()

    @Test
    fun testDelete() = commonTest.testDelete()

    @Test
    fun testUpdate() = commonTest.testUpdate()

    @Test
    fun testSelectWhereClause() = commonTest.testSelectWhereClause()

    @Test
    fun testSelectOrderByClause() = commonTest.testSelectOrderByClause()

    @Test
    fun testSelectLimitAndOffsetClause() = commonTest.testSelectLimitAndOffsetClause()

    @Test
    fun testGroupByAndHavingClause() = commonTest.testGroupByAndHavingClause()

    @Test
    fun testUnionSelect() = commonTest.testUnionSelect()

    @Test
    fun testFunction() = commonTest.testFunction()

    @Test
    fun testJoiClause() = commonTest.testJoinClause()

    @AfterTest
    fun setDown() {
        listOf(
            "$path$pathSeparator${CommonBasicTest.DATABASE_NAME}",
            "$path$pathSeparator${CommonBasicTest.DATABASE_NAME}-shm",
            "$path$pathSeparator${CommonBasicTest.DATABASE_NAME}-wal",
        ).forEach {
            remove(it)
        }
    }
}