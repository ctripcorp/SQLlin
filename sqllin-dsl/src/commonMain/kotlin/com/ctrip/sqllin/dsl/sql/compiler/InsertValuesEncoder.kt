package com.ctrip.sqllin.dsl.sql.compiler

/**
 * Encode the object to INSERT SQL statement.
 * @author yaqiao
 */

internal class InsertValuesEncoder : AbstractValuesEncoder() {

    override val sqlStrBuilder = StringBuilder("(")

    override fun StringBuilder.appendTail(): StringBuilder  {
        val symbol = if (elementsIndex < elementsCount - 1)
            ','
        else
            ')'
        return append(symbol)
    }
}