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
 * SQLite extension function
 * @author yaqiao
 */

/**
 * Abstract database path, it is 'Context' in Android, and 'String' in native targets.
 * DO NOT implementation 'DatabasePath' by yourself!!!
 */
public interface DatabasePath

public expect fun openDatabase(config: DatabaseConfiguration): DatabaseConnection

public inline fun <T> openDatabase(config: DatabaseConfiguration, block: (DatabaseConnection) -> T): T {
    val connection = openDatabase(config)
    try {
        return block(connection)
    } finally {
        connection.close()
    }
}

public inline fun <T> DatabaseConnection.withTransaction(block: (DatabaseConnection) -> T): T {
    beginTransaction()
    try {
        val result = block(this)
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}

public inline fun <T> DatabaseConnection.withQuery(
    sql: String,
    bindParams: Array<String?>? = null,
    block: (CommonCursor) -> T,
): T {
    val commonCursor = query(sql, bindParams)
    try {
        return block(commonCursor)
    } finally {
        commonCursor.close()
    }
}