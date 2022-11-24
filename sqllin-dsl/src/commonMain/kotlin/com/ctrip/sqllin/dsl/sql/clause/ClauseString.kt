package com.ctrip.sqllin.dsl.sql.clause

/**
 * Clause String
 * @author yaqiao
 */

class ClauseString(valueName: String) : ClauseElement(valueName) {

    // Equals, ==
    internal infix fun eq(str: String?): SelectCondition = appendString("=", "IS", str)

    // Not equal to, !=
    internal infix fun neq(str: String?): SelectCondition = appendString("!=", "IS NOT", str)

    internal infix fun like(regex: String): SelectCondition = appendRegex("LIKE", regex)

    internal infix fun glob(regex: String): SelectCondition = appendRegex("GLOB", regex)

    private fun appendRegex(symbol: String, regex: String): SelectCondition {
        val sql = buildString {
            append(valueName)
            append(' ')
            append(symbol)
            append(' ')
            append('\'')
            append(regex)
            append('\'')
        }
        return SelectCondition(sql)
    }

    private fun appendString(notNullSymbol: String, nullSymbol: String, str: String?): SelectCondition {
        val sql = buildString {
            append(valueName)
            append(' ')
            val isNull = str == null
            val symbol = if (isNull) nullSymbol else notNullSymbol
            append(symbol)
            append(' ')
            if (str == null)
                append(" NULL")
            else {
                append('\'')
                append(str)
                append('\'')
            }
        }
        return SelectCondition(sql)
    }

    override fun hashCode(): Int = valueName.hashCode()
    override fun equals(other: Any?): Boolean = (other as? ClauseString)?.valueName == valueName
}