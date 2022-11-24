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

    @AfterTest
    fun setDown() {
        listOf(
            "$path/${CommonBasicTest.DATABASE_NAME}",
            "$path/${CommonBasicTest.DATABASE_NAME}-shm",
            "$path/${CommonBasicTest.DATABASE_NAME}-wal",
        ).forEach {
            remove(it)
        }
    }

    private val path
        get() = "/Users/ccsa/Downloads"
}