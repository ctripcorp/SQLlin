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

import android.database.sqlite.SQLiteDatabase

/**
 * Database connection Android actual
 * @author yaqiao
 */

internal class AndroidDatabaseConnection(private val database: SQLiteDatabase) : DatabaseConnection {

    override fun execSQL(sql: String, bindParams: Array<Any?>?) =
        if (bindParams == null)
            database.execSQL(sql)
        else
            database.execSQL(sql, bindParams)

    override fun executeInsert(sql: String, bindParams: Array<Any?>?) = execSQL(sql, bindParams)

    override fun executeUpdateDelete(sql: String, bindParams: Array<Any?>?) = execSQL(sql, bindParams)

    override fun query(sql: String, bindParams: Array<String?>?): CommonCursor = AndroidCursor(database.rawQuery(sql, bindParams))

    override fun beginTransaction() = database.beginTransaction()
    override fun endTransaction() = database.endTransaction()
    override fun setTransactionSuccessful() = database.setTransactionSuccessful()

    override fun close() = database.close()

    override val isClosed: Boolean
        get() = !database.isOpen
}
