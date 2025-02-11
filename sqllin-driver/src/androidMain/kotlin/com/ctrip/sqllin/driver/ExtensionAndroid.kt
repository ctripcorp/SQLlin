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
 * SQLite extension Android
 * @author yaqiao
 */

public fun Context.toDatabasePath(): DatabasePath = AndroidDatabasePath(this)

@JvmInline
internal value class AndroidDatabasePath(val context: Context) : DatabasePath

public actual fun openDatabase(config: DatabaseConfiguration): DatabaseConnection {
    val isEqualsOrHigherThanAndroidP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    if (isEqualsOrHigherThanAndroidP && config.inMemory)
        return AndroidDatabaseConnection(createInMemory(config.toAndroidOpenParams()))
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

private class OldAndroidDBHelper(
    private val config: DatabaseConfiguration,
) : SQLiteOpenHelper((config.path as AndroidDatabasePath).context, config.name, null, config.version) {

    override fun onCreate(db: SQLiteDatabase) =
        config.create(AndroidDatabaseConnection(db))

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
        config.upgrade(AndroidDatabaseConnection(db), oldVersion, newVersion)
}

@RequiresApi(Build.VERSION_CODES.P)
private class AndroidDBHelper(
    private val config: DatabaseConfiguration,
) : SQLiteOpenHelper((config.path as AndroidDatabasePath).context, config.name, config.version, config.toAndroidOpenParams()) {

    override fun onCreate(db: SQLiteDatabase) =
        config.create(AndroidDatabaseConnection(db))

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
        config.upgrade(AndroidDatabaseConnection(db), oldVersion, newVersion)
}

@RequiresApi(Build.VERSION_CODES.P)
@Suppress("DEPRECATION")
private fun DatabaseConfiguration.toAndroidOpenParams(): OpenParams = OpenParams
    .Builder()
    .setJournalMode(journalMode.name)
    .setSynchronousMode(synchronousMode.name)
    .setIdleConnectionTimeout(busyTimeout.toLong())
    .setLookasideConfig(lookasideSlotSize, lookasideSlotCount)
    .build()

public actual fun deleteDatabase(path: DatabasePath, name: String): Boolean =
    (path as? AndroidDatabasePath)?.context?.deleteDatabase(name)
        ?: throw IllegalArgumentException("Please use the `Context.toDatabasePath()` to get the DatabasePath")