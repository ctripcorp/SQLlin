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

package com.ctrip.sqllin.driver

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.*
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Converts an Android Context to a [DatabasePath].
 */
public fun Context.toDatabasePath(): DatabasePath = AndroidDatabasePath(this)

/**
 * Android-specific [DatabasePath] implementation wrapping a Context.
 */
@JvmInline
internal value class AndroidDatabasePath(val context: Context) : DatabasePath

public actual fun openDatabase(config: DatabaseConfiguration): DatabaseConnection {
    val isEqualsOrHigherThanAndroidP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    if (isEqualsOrHigherThanAndroidP && config.inMemory)
        return AndroidDatabaseConnection(createInMemory(config.toAndroidOpenParams()).apply { setBusyTimeout(config.busyTimeout) })
    val helper = if (isEqualsOrHigherThanAndroidP)
        AndroidDBHelper(config)
    else
        OldAndroidDBHelper(config)
    val database = if (config.isReadOnly)
        helper.readableDatabase
    else
        helper.writableDatabase
    val connection = AndroidDatabaseConnection(database)
    if (!isEqualsOrHigherThanAndroidP) {
        connection.updateSynchronousMode(config.synchronousMode)
        connection.updateJournalMode(config.journalMode)
    }
    return connection
}

/**
 * SQLiteOpenHelper for Android versions below P (API level 28).
 */
private class OldAndroidDBHelper(
    private val config: DatabaseConfiguration,
) : SQLiteOpenHelper((config.path as AndroidDatabasePath).context, config.name, null, config.version) {

    override fun onConfigure(db: SQLiteDatabase) = db.setBusyTimeout(config.busyTimeout)

    override fun onCreate(db: SQLiteDatabase) =
        config.create(AndroidDatabaseConnection(db))

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
        config.upgrade(AndroidDatabaseConnection(db), oldVersion, newVersion)
}

/**
 * SQLiteOpenHelper for Android P (API level 28) and above with OpenParams support.
 */
@RequiresApi(Build.VERSION_CODES.P)
private class AndroidDBHelper(
    private val config: DatabaseConfiguration,
) : SQLiteOpenHelper((config.path as AndroidDatabasePath).context, config.name, config.version, config.toAndroidOpenParams()) {

    override fun onConfigure(db: SQLiteDatabase) = db.setBusyTimeout(config.busyTimeout)

    override fun onCreate(db: SQLiteDatabase) =
        config.create(AndroidDatabaseConnection(db))

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
        config.upgrade(AndroidDatabaseConnection(db), oldVersion, newVersion)
}

/**
 * Converts [DatabaseConfiguration] to Android's OpenParams for API level 28+.
 *
 * The lookaside configuration is only set when both of its values are 0 or more, as `setLookasideConfig(0, 0)`
 * disables the allocator, and leaving it unset keeps SQLite's default configuration.
 */
@RequiresApi(Build.VERSION_CODES.P)
internal fun DatabaseConfiguration.toAndroidOpenParams(): OpenParams = OpenParams
    .Builder()
    .setJournalMode(journalMode.name)
    .setSynchronousMode(synchronousMode.name)
    .apply {
        if (lookasideSlotSize >= 0 && lookasideSlotCount >= 0)
            setLookasideConfig(lookasideSlotSize, lookasideSlotCount)
    }
    .build()

/**
 * Sets how long SQLite waits for a lock that another connection holds, which the framework sets to 2500 ms.
 *
 * From Android 13 on, a database in WAL mode reads through several connections, so the PRAGMA is run on each of them,
 * and on those opened later; the framework only runs a PRAGMA that returns a row, as this one does, from Android 13 on.
 * Before, a database that SQLlin opens has a single connection.
 */
private fun SQLiteDatabase.setBusyTimeout(milliseconds: Int) {
    val sql = "PRAGMA busy_timeout = $milliseconds"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        execPerConnectionSQL(sql, null)
    else
        rawQuery(sql, null).use { it.moveToFirst() }
}

public actual fun deleteDatabase(path: DatabasePath, name: String): Boolean =
    (path as? AndroidDatabasePath)?.context?.deleteDatabase(name)
        ?: throw IllegalArgumentException("Please use the `Context.toDatabasePath()` to get the DatabasePath")