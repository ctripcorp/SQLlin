package com.ctrip.sqllin.driver

/**
 * SQLite extension function
 * @author yaqiao
 */

/**
 * Abstract database path, it is 'Context' in Android, and 'String' in native targets.
 * DO NOT implementation 'DatabasePath' by yourself!!!
 */
interface DatabasePath

expect fun openDatabase(config: DatabaseConfiguration): DatabaseConnection

inline fun <T> openDatabase(config: DatabaseConfiguration, block: (DatabaseConnection) -> T): T {
    val connection = openDatabase(config)
    try {
        return block(connection)
    } finally {
        connection.close()
    }
}

inline fun <T> DatabaseConnection.withTransaction(block: (DatabaseConnection) -> T): T {
    beginTransaction()
    try {
        val result = block(this)
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}

inline fun <T> DatabaseConnection.withQuery(
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