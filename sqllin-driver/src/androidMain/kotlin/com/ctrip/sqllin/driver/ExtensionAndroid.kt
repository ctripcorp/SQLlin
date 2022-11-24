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

fun Context.toDatabasePath(): DatabasePath = AndroidDatabasePath(this)

@JvmInline
value class AndroidDatabasePath internal constructor(val context: Context) : DatabasePath

actual fun openDatabase(config: DatabaseConfiguration): DatabaseConnection {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && config.inMemory)
        return DatabaseConnectionImpl(createInMemory(config.toAndroidOpenParams()))
    val helper = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        AndroidDBHelper(config)
    else
        OldAndroidDBHelper(config)
    val database = if (config.isReadOnly)
        helper.readableDatabase
    else
        helper.writableDatabase
    return DatabaseConnectionImpl(database)
}

private class OldAndroidDBHelper(
    private val config: DatabaseConfiguration,
) : SQLiteOpenHelper((config.path as AndroidDatabasePath).context, config.name, null, config.version) {

    override fun onCreate(db: SQLiteDatabase) =
        config.create(DatabaseConnectionImpl(db))

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
        config.upgrade(DatabaseConnectionImpl(db), oldVersion, newVersion)
}

@RequiresApi(Build.VERSION_CODES.P)
private class AndroidDBHelper(
    private val config: DatabaseConfiguration,
) : SQLiteOpenHelper((config.path as AndroidDatabasePath).context, config.name, config.version, config.toAndroidOpenParams()) {

    override fun onCreate(db: SQLiteDatabase) =
        config.create(DatabaseConnectionImpl(db))

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
        config.upgrade(DatabaseConnectionImpl(db), oldVersion, newVersion)
}

@RequiresApi(Build.VERSION_CODES.P)
@Suppress("DEPRECATION")
private fun DatabaseConfiguration.toAndroidOpenParams(): OpenParams = OpenParams.Builder().apply {
    setJournalMode(journalMode.name)
    setSynchronousMode(synchronousMode.name)
    setIdleConnectionTimeout(busyTimeout.toLong())
    setLookasideConfig(lookasideSlotSize, lookasideSlotCount)
}.build()