package com.ctrip.sqllin.dsl.sql.clause

/**
 * Clause Boolean, will be converted to number in SQL statement.
 * @author yaqiao
 */

class ClauseBoolean(valueName: String) : ClauseElement(valueName) {

    internal infix fun _is(bool: Boolean): SelectCondition {
        val sql = buildString {
            append(valueName)
            append(' ')
            if (bool)
                append('>')
            else
                append("<=")
            append(' ')
            append(0)
        }
        return SelectCondition(sql)
    }

    override fun hashCode(): Int = valueName.hashCode()
    override fun equals(other: Any?): Boolean = (other as? ClauseBoolean)?.valueName == valueName
}