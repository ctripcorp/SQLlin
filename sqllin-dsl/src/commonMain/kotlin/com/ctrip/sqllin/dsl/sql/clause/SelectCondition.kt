package com.ctrip.sqllin.dsl.sql.clause

/**
 * Present the single condition in where clause.
 * @author yaqiao
 */

public class SelectCondition internal constructor(
    internal val conditionSQL: String,
) {

    // Where condition 'OR' operator.
    internal infix fun or(next: SelectCondition): SelectCondition = append("OR", next)

    // Where condition 'AND' operator.
    internal infix fun and(next: SelectCondition): SelectCondition = append("AND", next)

    private fun append(symbol: String, next: SelectCondition): SelectCondition {
        val sql = buildString {
            append(conditionSQL)
            append(" $symbol ")
            append(next.conditionSQL)
        }
        return SelectCondition(sql)
    }
}