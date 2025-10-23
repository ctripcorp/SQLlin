package com.ctrip.sqllin.dsl.test

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import com.ctrip.sqllin.driver.toDatabasePath
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android instrumented test
 * @author Yuang Qiao
 */

@RunWith(AndroidJUnit4ClassRunner::class)
class AndroidTest {

    private val commonTest = CommonBasicTest(
        ApplicationProvider.getApplicationContext<Context>().toDatabasePath()
    )

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
    fun testCreateTableWithLongPrimaryKey() = commonTest.testCreateTableWithLongPrimaryKey()

    @Test
    fun testCreateTableWithStringPrimaryKey() = commonTest.testCreateTableWithStringPrimaryKey()

    @Test
    fun testCreateTableWithAutoincrement() = commonTest.testCreateTableWithAutoincrement()

    @Test
    fun testCreateTableWithCompositePrimaryKey() = commonTest.testCreateTableWithCompositePrimaryKey()

    @Test
    fun testInsertWithId() = commonTest.testInsertWithId()

    @Test
    fun testCreateInDatabaseScope() = commonTest.testCreateInDatabaseScope()

    @Test
    fun testUpdateAndDeleteWithPrimaryKey() = commonTest.testUpdateAndDeleteWithPrimaryKey()

    @Test
    fun testByteArrayInsert() = commonTest.testByteArrayInsert()

    @Test
    fun testByteArraySelect() = commonTest.testByteArraySelect()

    @Test
    fun testByteArrayUpdate() = commonTest.testByteArrayUpdate()

    @Test
    fun testByteArrayDelete() = commonTest.testByteArrayDelete()

    @Test
    fun testByteArrayMultipleOperations() = commonTest.testByteArrayMultipleOperations()

    @Test
    fun testDropTable() = commonTest.testDropTable()

    @Test
    fun testDropTableExtensionFunction() = commonTest.testDropTableExtensionFunction()

    @Test
    fun testAlertAddColumn() = commonTest.testAlertAddColumn()

    @Test
    fun testAlertRenameTableWithTableObject() = commonTest.testAlertRenameTableWithTableObject()

    @Test
    fun testAlertRenameTableWithString() = commonTest.testAlertRenameTableWithString()

    @Test
    fun testRenameColumnWithClauseElement() = commonTest.testRenameColumnWithClauseElement()

    @Test
    fun testRenameColumnWithString() = commonTest.testRenameColumnWithString()

    @Test
    fun testDropColumn() = commonTest.testDropColumn()

    @Test
    fun testDropAndRecreateTable() = commonTest.testDropAndRecreateTable()

    @Test
    fun testAlertOperationsInTransaction() = commonTest.testAlertOperationsInTransaction()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(CommonBasicTest.DATABASE_NAME)
    }

    @After
    fun setDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(CommonBasicTest.DATABASE_NAME)
    }
}