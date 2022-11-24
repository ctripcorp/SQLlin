package com.ctrip.sqllin.dsl.sql.statement

/**
 * Abstract Single executable statement.
 * @author yaqiao
 */

sealed class SingleStatement constructor(
    val sqlStr: String,
) : ExecutableStatement
