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
 * Database manager Android actual
 * @author yaqiao
 */

public class DatabaseConnectionImpl internal constructor(private val database: SQLiteDatabase) : DatabaseConnection {

    override fun execSQL(sql: String, bindParams: Array<Any?>?): Unit =
        if (bindParams == null)
            database.execSQL(sql)
        else
            database.execSQL(sql, bindParams)

    override fun executeInsert(sql: String, bindParams: Array<Any?>?): Unit = execSQL(sql, bindParams)

    override fun executeUpdateDelete(sql: String, bindParams: Array<Any?>?): Unit = execSQL(sql, bindParams)

    override fun query(sql: String, bindParams: Array<String?>?): CommonCursor = CursorImpl(database.rawQuery(sql, bindParams))

    override fun beginTransaction(): Unit = database.beginTransaction()
    override fun endTransaction(): Unit = database.endTransaction()
    override fun setTransactionSuccessful(): Unit = database.setTransactionSuccessful()

    override fun close(): Unit = database.close()
    override val closed: Boolean
        get() = !database.isOpen
}
