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