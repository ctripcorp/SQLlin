package com.ctrip.sqllin.dsl.sql.statement

/**
 * Abstract SQL statement that could execute.
 * @author yaqiao
 */

public sealed interface ExecutableStatement {
    public fun execute()
}