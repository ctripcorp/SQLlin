package com.ctrip.sqllin.driver

/**
 * Database manager common expect
 * @author yaqiao
 */

interface DatabaseConnection {

    fun execSQL(sql: String, bindParams: Array<Any?>? = null)
    fun executeInsert(sql: String, bindParams: Array<Any?>? = null)
    fun executeUpdateDelete(sql: String, bindParams: Array<Any?>? = null)

    fun query(sql: String, bindParams: Array<String?>? = null): CommonCursor

    fun beginTransaction()
    fun setTransactionSuccessful()
    fun endTransaction()

    fun close()
    val closed: Boolean
}
