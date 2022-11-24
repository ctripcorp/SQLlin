package com.ctrip.sqllin.dsl.sql.clause

/**
 * Abstract clause element
 * @author yaqiao
 */

sealed class ClauseElement(
    internal val valueName: String
)