package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity

/**
 * Present the single prediction in set clause
 * @author yaqiao
 */

class SetClause<T : DBEntity<T>> : Clause<T> {

    private val clauseBuilder = StringBuilder()

    fun append(propertyName: String, propertyValue: String?) {
        clauseBuilder
            .append(propertyName)
            .append('=')
            .append(propertyValue ?: "NULL")
            .append(',')
    }

    internal fun finalize(): String = clauseBuilder.apply {
        if (this[lastIndex] == ',')
            deleteAt(lastIndex)
    }.toString()
}

inline fun <T : DBEntity<T>> SET(block: SetClause<T>.() -> Unit): SetClause<T> = SetClause<T>().apply(block)