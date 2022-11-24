package com.ctrip.sqllin.dsl.sql.operation

/**
 * SQL operation: SELECT, UPDATE, DELETE, INSERT
 * @author yaqiao
 */

internal interface Operation {
    val sqlStr: String
}