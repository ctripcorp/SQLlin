package com.ctrip.sqllin.driver

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android instrumentation test
 * @author yaqiao
 */

@SmallTest
@RunWith(AndroidJUnit4ClassRunner::class)
class AndroidTest {

    private val commonTest = CommonBasicTest(
        ApplicationProvider.getApplicationContext<Context>().toDatabasePath()
    )

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

    @After
    fun setDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(SQL.DATABASE_NAME)
    }
}