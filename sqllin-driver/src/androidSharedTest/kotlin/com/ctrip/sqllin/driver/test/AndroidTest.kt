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

package com.ctrip.sqllin.driver.test

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ctrip.sqllin.driver.DatabaseConfiguration
import com.ctrip.sqllin.driver.toAndroidOpenParams
import com.ctrip.sqllin.driver.toDatabasePath
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Android tests, which run on the JVM via Robolectric, as `testAndroidHostTest`, and on an emulator or a device,
 * as `connectedAndroidDeviceTest`: [AndroidJUnit4] runs them on Robolectric on the JVM, where the `sdk` levels of
 * [Config] cover both the `SQLiteDatabase.OpenParams` code path (Android P and above) and the legacy one, and on the
 * device otherwise, at its own level, where [Config] does nothing.
 * @author Yuang Qiao
 */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 37])
class AndroidTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val commonTest = CommonBasicTest(context.toDatabasePath())

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
    fun testFailedCommit() = commonTest.testFailedCommit()

    @Test
    fun testRolledBackBySQLite() = commonTest.testRolledBackBySQLite()

    @Test
    fun testImmediateTransaction() = commonTest.testImmediateTransaction()

    @Test
    fun testTransactionModes() = commonTest.testTransactionModes(isDeferredSupported = false)

    @Test
    fun testJournalMode() = commonTest.testJournalMode()

    @Test
    fun testBusyTimeout() = commonTest.testBusyTimeout()

    @Test
    fun testConnectionPragmas() = commonTest.testConnectionPragmas()

    @Test
    fun testConcurrency() = commonTest.testConcurrency()

    /**
     * The lookaside configuration is only given to the framework when one is set, as `setLookasideConfig(0, 0)`
     * disables the allocator.
     */
    @Test
    @Config(sdk = [37])
    fun testLookasideConfig() {
        // OpenParams, which a device below Android P doesn't have
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        val config = DatabaseConfiguration(name = SQL.DATABASE_NAME, path = context.toDatabasePath(), version = 1)
        fun lookaside(config: DatabaseConfiguration) = config.toAndroidOpenParams().run { lookasideSlotSize to lookasideSlotCount }
        assertEquals(-1 to -1, lookaside(config))
        assertEquals(0 to 0, lookaside(config.copy(lookasideSlotSize = 0, lookasideSlotCount = 0)))
        assertEquals(256 to 32, lookaside(config.copy(lookasideSlotSize = 256, lookasideSlotCount = 32)))
    }

    @AfterTest
    fun setDown() {
        context.deleteDatabase(SQL.DATABASE_NAME)
    }
}
