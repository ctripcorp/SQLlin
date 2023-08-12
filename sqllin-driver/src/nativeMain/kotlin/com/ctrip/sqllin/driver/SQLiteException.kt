/*
 * Copyright (C) 2023 Ctrip.com.
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

import com.ctrip.sqllin.driver.SQLiteResultCode.Companion.INVALID_CODE
import com.ctrip.sqllin.driver.cinterop.SQLiteErrorType

/**
 * The exceptions about SQLite, they include the native SQLite result codes and error message
 * @author yaqiao
 */

public open class SQLiteException(message: String) : Exception(message)

/**
 * The result codes in SQLite
 */
public class SQLiteResultCode(message: String, resultCode: Int) : SQLiteException(
    "$message | error code ${
        kotlin.run { 
            val code = resultCode and 0xff
            SQLiteErrorType.entries.find { it.code == code } 
        }
    }") {
    internal companion object {
        const val INVALID_CODE = -1
    }
}

internal fun sqliteException(message: String, errorCode: Int = INVALID_CODE): SQLiteException =
    if (errorCode == INVALID_CODE)
        SQLiteException(message)
    else
        SQLiteResultCode(message, errorCode)