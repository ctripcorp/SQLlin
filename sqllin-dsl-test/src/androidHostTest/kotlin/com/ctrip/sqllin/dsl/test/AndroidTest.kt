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
    fun testCompoundSelect() = commonTest.testCompoundSelect()

    @Test
    fun testObserve() = commonTest.testObserve()

    @Test
    fun testObserveForeignKeyAction() = commonTest.testObserveForeignKeyAction()

    @Test
    fun testView() = commonTest.testView()

    @Test
    fun testUnsignedValues() = commonTest.testUnsignedValues()

    @Test
    fun testFts4() = commonTest.testFts4()

    @Test
    fun testFts3() = commonTest.testFts3()

    @Test
    fun testCheckConstraint() = commonTest.testCheckConstraint()

    @Test
    fun testSubquery() = commonTest.testSubquery()

    @Test
    fun testConditionPrecedence() = commonTest.testConditionPrecedence()

    @Test
    fun testConditionReuse() = commonTest.testConditionReuse()

    @Test
    fun testJoinedRelation() = commonTest.testJoinedRelation()

    @Test
    fun testExpressionIndex() = commonTest.testExpressionIndex()

    @Test
    fun testDropIndex() = commonTest.testDropIndex()

    @Test
    fun testIfNotExists() = commonTest.testIfNotExists()

    @Test
    fun testPartialIndex() = commonTest.testPartialIndex()

    @Test
    fun testDistinctAggregate() = commonTest.testDistinctAggregate()

    @Test
    fun testLikeEscape() = commonTest.testLikeEscape()

    @Test
    fun testVacuum() = commonTest.testVacuum()

    @Test
    fun testArithmetic() = commonTest.testArithmetic()

    @Test
    fun testCast() = commonTest.testCast()

    @Test
    fun testNullFunctions() = commonTest.testNullFunctions()

    @Test
    fun testMoreFunctions() = commonTest.testMoreFunctions()

    @Test
    fun testDateTimeFunctions() = commonTest.testDateTimeFunctions()

    @Test
    fun testPlatformDependentFunctions() = commonTest.testPlatformDependentFunctions()

    @Test
    fun testCaseExpression() = commonTest.testCaseExpression()

    @Test
    fun testScalarSubquery() = commonTest.testScalarSubquery()

    @Test
    fun testExpressionOperators() = commonTest.testExpressionOperators()

    @Test
    fun testSetExpressions() = commonTest.testSetExpressions()

    @Test
    fun testInsertExpressions() = commonTest.testInsertExpressions()

    @Test
    fun testTrigger() = commonTest.testTrigger()

    @Test
    fun testTriggerSelectingNewRow() = commonTest.testTriggerSelectingNewRow()

    @Test
    fun testTriggerChecks() = commonTest.testTriggerChecks()

    @Test
    fun testTriggerWritingFts() = commonTest.testTriggerWritingFts()

    @Test
    fun testInsertDefaultValues() = commonTest.testInsertDefaultValues()

    @Test
    fun testAnalyzeAndReindex() = commonTest.testAnalyzeAndReindex()

    @Test
    fun testIndexOrder() = commonTest.testIndexOrder()

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
    fun testInsertOrIgnore() = commonTest.testInsertOrIgnore()

    @Test
    fun testProjection() = commonTest.testProjection()

    @Test
    fun testResultColumns() = commonTest.testResultColumns()

    @Test
    fun testResultColumnChecks() = commonTest.testResultColumnChecks()

    @Test
    fun testInsertSelect() = commonTest.testInsertSelect()

    @Test
    fun testTableRebuild() = commonTest.testTableRebuild()

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
    fun testPrimaryKeyNullability() = commonTest.testPrimaryKeyNullability()

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
    fun testFunctionStringArguments() = commonTest.testFunctionStringArguments()

    @Test
    fun testFunctionComparisons() = commonTest.testFunctionComparisons()

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