package com.ctrip.sqllin.dsl.sql.statement

/**
 * Abstract Single executable statement.
 * @author yaqiao
 */

public sealed class SingleStatement constructor(
    public val sqlStr: String,
) : ExecutableStatement
