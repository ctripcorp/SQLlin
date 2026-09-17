package com.ctrip.sqllin.dsl.test

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ctrip.sqllin.driver.toDatabasePath
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android unit test that runs on the JVM via Robolectric. The `sdk` levels cover both
 * the `SQLiteDatabase.OpenParams` code path (Android P and above) and the legacy one.
 * @author Yuang Qiao
 */

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 37])
class AndroidTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val commonTest = CommonBasicTest(context.toDatabasePath())

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
    fun testInsertOrReplace() = commonTest.testInsertOrReplace()

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

    @Test
    fun testStringAggregateFunctions() = commonTest.testStringAggregateFunctions()

    @Test
    fun testIndexOperations() = commonTest.testIndexOperations()

    @Test
    fun testBlobLengthFunction() = commonTest.testBlobLengthFunction()

    @Test
    fun testPragmaForeignKeys() = commonTest.testPragmaForeignKeys()

    @Test
    fun testForeignKeyCascadeDelete() = commonTest.testForeignKeyCascadeDelete()

    @Test
    fun testForeignKeySetNullDelete() = commonTest.testForeignKeySetNullDelete()

    @Test
    fun testForeignKeyRestrictDelete() = commonTest.testForeignKeyRestrictDelete()

    @Test
    fun testCompositeForeignKey() = commonTest.testCompositeForeignKey()

    @Test
    fun testMultipleForeignKeys() = commonTest.testMultipleForeignKeys()

    @Test
    fun testForeignKeyCreateSQL() = commonTest.testForeignKeyCreateSQL()

    @Test
    fun testForeignKeyWithoutPragma() = commonTest.testForeignKeyWithoutPragma()

    @Test
    fun testDefaultValuesCreateSQL() = commonTest.testDefaultValuesCreateSQL()

    @Test
    fun testDefaultValuesInsert() = commonTest.testDefaultValuesInsert()

    @Test
    fun testDefaultValuesWithForeignKey() = commonTest.testDefaultValuesWithForeignKey()

    @Before
    fun setUp() {
        context.deleteDatabase(CommonBasicTest.DATABASE_NAME)
    }

    @After
    fun setDown() {
        context.deleteDatabase(CommonBasicTest.DATABASE_NAME)
    }
}