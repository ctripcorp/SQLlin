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

/**
 * Database manager common expect
 * @author yaqiao
 */

public interface DatabaseConnection {

    public fun execSQL(sql: String, bindParams: Array<out Any?>? = null)
    public fun executeInsert(sql: String, bindParams: Array<out Any?>? = null)
    public fun executeUpdateDelete(sql: String, bindParams: Array<out Any?>? = null)

    public fun query(sql: String, bindParams: Array<out String?>? = null): CommonCursor

    public fun beginTransaction()
    public fun setTransactionSuccessful()
    public fun endTransaction()

    public fun close()

    public val isClosed: Boolean
}
