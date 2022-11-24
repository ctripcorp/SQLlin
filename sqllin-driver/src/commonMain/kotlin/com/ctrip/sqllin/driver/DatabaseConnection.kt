package com.ctrip.sqllin.driver

/**
 * Database manager common expect
 * @author yaqiao
 */

public interface DatabaseConnection {

    public fun execSQL(sql: String, bindParams: Array<Any?>? = null)
    public fun executeInsert(sql: String, bindParams: Array<Any?>? = null)
    public fun executeUpdateDelete(sql: String, bindParams: Array<Any?>? = null)

    public fun query(sql: String, bindParams: Array<String?>? = null): CommonCursor

    public fun beginTransaction()
    public fun setTransactionSuccessful()
    public fun endTransaction()

    public fun close()
    public val closed: Boolean
}
