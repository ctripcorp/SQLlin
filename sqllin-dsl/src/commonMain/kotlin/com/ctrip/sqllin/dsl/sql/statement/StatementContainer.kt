package com.ctrip.sqllin.dsl.sql.statement

/**
 * The container that used for store statement
 * @author yaqiao
 */

internal fun interface StatementContainer {

    infix fun changeLastStatement(statement: SingleStatement)
}