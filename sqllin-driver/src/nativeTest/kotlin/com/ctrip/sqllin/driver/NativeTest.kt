package com.ctrip.sqllin.driver

import platform.posix.remove
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Native unit test.
 * @author yaqiao
 */

class NativeTest {

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
            "$path/${SQL.DATABASE_NAME}",
            "$path/${SQL.DATABASE_NAME}-shm",
            "$path/${SQL.DATABASE_NAME}-wal",
        ).forEach {
            remove(it)
        }
    }

    private val path
        get() = "/Users/ccsa/Downloads"
}