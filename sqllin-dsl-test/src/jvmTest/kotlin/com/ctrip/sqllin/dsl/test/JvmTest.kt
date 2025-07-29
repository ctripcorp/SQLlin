package com.ctrip.sqllin.dsl.test

import com.ctrip.sqllin.driver.deleteDatabase
import com.ctrip.sqllin.driver.toDatabasePath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Native unit test
 * @author yaqiao
 */

class JvmTest {

    private val path = System.getProperty("user.dir").toDatabasePath()
    private val commonTest = CommonBasicTest(path)

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
    fun testJoinClause() = commonTest.testJoinClause()

    @Test
    fun testConcurrency() = commonTest.testConcurrency()

    @Test
    fun testPrimitiveTypeForKSP() = commonTest.testPrimitiveTypeForKSP()

    @Test
    fun testNullValue() = commonTest.testNullValue()

    @BeforeTest
    fun setUp() {
        deleteDatabase(path, CommonBasicTest.DATABASE_NAME)
    }

    @AfterTest
    fun setDown() {
        deleteDatabase(path, CommonBasicTest.DATABASE_NAME)
    }
}