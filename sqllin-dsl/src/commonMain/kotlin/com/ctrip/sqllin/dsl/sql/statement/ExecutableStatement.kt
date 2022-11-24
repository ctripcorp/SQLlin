package com.ctrip.sqllin.dsl.sql.statement

/**
 * Abstract SQL statement that could execute.
 * @author yaqiao
 */

sealed interface ExecutableStatement {
    fun execute()
}