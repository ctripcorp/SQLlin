package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity

/**
 * Abstract clause that could link conditions, include 'WHERE' and 'HAVING'
 * @author yaquai
 */

sealed class ConditionClause<T : DBEntity<T>>(private val selectCondition: SelectCondition) : SelectClause<T> {

    abstract val clauseName: String

    final override val clauseStr: String
        get() = selectCondition.conditionSQL.let { " $clauseName $it" }
}

// Less than, <.
infix fun ClauseNumber.LT(number: Number): SelectCondition = lt(number)

// Less or equal to, <=.
infix fun ClauseNumber.LTE(number: Number): SelectCondition = lte(number)

// Equals, ==.
infix fun ClauseNumber.EQ(number: Number?): SelectCondition = eq(number)

// Not equal to, !=.
infix fun ClauseNumber.NEQ(number: Number?): SelectCondition = neq(number)

// Greater than, >.
infix fun ClauseNumber.GT(number: Number): SelectCondition = gt(number)

// Greater than or equal to, >=.
infix fun ClauseNumber.GTE(number: Number): SelectCondition = gte(number)

// If the 'number' in the 'numbers'.
infix fun ClauseNumber.IN(numbers: Iterable<Number>): SelectCondition = inIterable(numbers)

// If the 'number' between the 'range'.
infix fun ClauseNumber.BETWEEN(range: LongRange): SelectCondition = between(range)

// Equals, ==.
infix fun ClauseString.EQ(str: String?): SelectCondition = eq(str)

// Not equal to, !=.
infix fun ClauseString.NEQ(str: String?): SelectCondition = neq(str)

// SQL LIKE operator.
infix fun ClauseString.LIKE(regex: String): SelectCondition = like(regex)

// SQL GLOB operator.
infix fun ClauseString.GLOB(regex: String): SelectCondition = glob(regex)

// Condition 'OR' operator.
infix fun SelectCondition.OR(prediction: SelectCondition): SelectCondition = or(prediction)

// Condition 'AND' operator.
infix fun SelectCondition.AND(prediction: SelectCondition): SelectCondition = and(prediction)

infix fun ClauseBoolean.IS(bool: Boolean): SelectCondition = _is(bool)