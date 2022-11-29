/*
 * Copyright (C) 2022 Ctrip.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity

/**
 * Abstract clause that could link conditions, include 'WHERE' and 'HAVING'
 * @author yaquai
 */

public sealed class ConditionClause<T : DBEntity<T>>(private val selectCondition: SelectCondition) : SelectClause<T> {

    internal abstract val clauseName: String

    final override val clauseStr: String
        get() = " $clauseName ${selectCondition.conditionSQL}"
}

// Less than, <.
public infix fun ClauseNumber.LT(number: Number): SelectCondition = lt(number)

// Less or equal to, <=.
public infix fun ClauseNumber.LTE(number: Number): SelectCondition = lte(number)

// Equals, ==.
public infix fun ClauseNumber.EQ(number: Number?): SelectCondition = eq(number)

// Not equal to, !=.
public infix fun ClauseNumber.NEQ(number: Number?): SelectCondition = neq(number)

// Greater than, >.
public infix fun ClauseNumber.GT(number: Number): SelectCondition = gt(number)

// Greater than or equal to, >=.
public infix fun ClauseNumber.GTE(number: Number): SelectCondition = gte(number)

// If the 'number' in the 'numbers'.
public infix fun ClauseNumber.IN(numbers: Iterable<Number>): SelectCondition = inIterable(numbers)

// If the 'number' between the 'range'.
public infix fun ClauseNumber.BETWEEN(range: LongRange): SelectCondition = between(range)

// Equals, ==.
public infix fun ClauseString.EQ(str: String?): SelectCondition = eq(str)

// Not equal to, !=.
public infix fun ClauseString.NEQ(str: String?): SelectCondition = neq(str)

// SQL LIKE operator.
public infix fun ClauseString.LIKE(regex: String): SelectCondition = like(regex)

// SQL GLOB operator.
public infix fun ClauseString.GLOB(regex: String): SelectCondition = glob(regex)

// Condition 'OR' operator.
public infix fun SelectCondition.OR(prediction: SelectCondition): SelectCondition = or(prediction)

// Condition 'AND' operator.
public infix fun SelectCondition.AND(prediction: SelectCondition): SelectCondition = and(prediction)

public infix fun ClauseBoolean.IS(bool: Boolean): SelectCondition = _is(bool)