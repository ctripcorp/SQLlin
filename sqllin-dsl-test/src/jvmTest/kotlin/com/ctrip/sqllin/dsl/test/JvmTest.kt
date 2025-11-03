package com.ctrip.sqllin.dsl.test

import com.ctrip.sqllin.driver.deleteDatabase
import com.ctrip.sqllin.driver.toDatabasePath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Native unit test
 * @author Yuang Qiao
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

    @Test
    fun testPrimaryKeyVariations() = commonTest.testPrimaryKeyVariations()

    @Test
    fun testInsertWithId() = commonTest.testInsertWithId()

    @Test
    fun testCreateInDatabaseScope() = commonTest.testCreateInDatabaseScope()

    @Test
    fun testUpdateAndDeleteWithPrimaryKey() = commonTest.testUpdateAndDeleteWithPrimaryKey()

    @Test
    fun testByteArrayAndBlobOperations() = commonTest.testByteArrayAndBlobOperations()

    @Test
    fun testDropAndCreateTable() = commonTest.testDropAndCreateTable()

    @Test
    fun testSchemaModification() = commonTest.testSchemaModification()

    @Test
    fun testStringOperators() = commonTest.testStringOperators()

    @Test
    fun testEnumOperations() = commonTest.testEnumOperations()

    @Test
    fun testCreateSQLGeneration() = commonTest.testCreateSQLGeneration()

    @Test
    fun testUniqueConstraint() = commonTest.testUniqueConstraint()

    @Test
    fun testCollateNoCaseConstraint() = commonTest.testCollateNoCaseConstraint()

    @Test
    fun testCompositeUniqueConstraint() = commonTest.testCompositeUniqueConstraint()

    @Test
    fun testMultiGroupCompositeUnique() = commonTest.testMultiGroupCompositeUnique()

    @Test
    fun testCombinedConstraints() = commonTest.testCombinedConstraints()

    @Test
    fun testNotNullConstraint() = commonTest.testNotNullConstraint()

    @BeforeTest
    fun setUp() {
        deleteDatabase(path, CommonBasicTest.DATABASE_NAME)
    }

    @AfterTest
    fun setDown() {
        deleteDatabase(path, CommonBasicTest.DATABASE_NAME)
    }
}