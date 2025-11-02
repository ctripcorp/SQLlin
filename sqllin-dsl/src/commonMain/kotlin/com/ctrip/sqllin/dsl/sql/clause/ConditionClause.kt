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

import com.ctrip.sqllin.dsl.annotation.StatementDslMaker

/**
 * Base class for condition-based clauses (WHERE, HAVING).
 *
 * Wraps a [SelectCondition] and provides the clause name. Generates SQL in the format:
 * ` CLAUSE_NAME condition`
 *
 * This file also provides uppercase DSL operators for building conditions:
 * - Numeric: LT, LTE, EQ, NEQ, GT, GTE, IN, BETWEEN
 * - String: LT, LTE, EQ, NEQ, GT, GTE, IN, BETWEEN, LIKE, GLOB
 * - Blob: LT, LTE, EQ, NEQ, GT, GTE, IN, BETWEEN
 * - Enum: LT, LTE, EQ, NEQ, GT, GTE
 * - Boolean: IS
 * - Logic: AND, OR
 *
 * @param T The entity type this clause operates on
 *
 * @author Yuang Qiao
 */
public sealed class ConditionClause<T>(private val selectCondition: SelectCondition) : SelectClause<T> {

    internal abstract val clauseName: String

    final override val clauseStr: String
        get() = " $clauseName ${selectCondition.conditionSQL}"
}

// Less than, <
@StatementDslMaker
public infix fun ClauseNumber.LT(number: Number): SelectCondition = lt(number)

// Less than, append to ClauseNumber
@StatementDslMaker
public infix fun ClauseNumber.LT(clauseNumber: ClauseNumber): SelectCondition = lt(clauseNumber)

// Less than or equal to, <=
@StatementDslMaker
public infix fun ClauseNumber.LTE(number: Number): SelectCondition = lte(number)

// Less than or equal to, append to ClauseNumber
@StatementDslMaker
public infix fun ClauseNumber.LTE(clauseNumber: ClauseNumber): SelectCondition = lte(clauseNumber)

// Equals, ==
@StatementDslMaker
public infix fun ClauseNumber.EQ(number: Number?): SelectCondition = eq(number)

// Equals, append to ClauseNumber
@StatementDslMaker
public infix fun ClauseNumber.EQ(clauseNumber: ClauseNumber): SelectCondition = eq(clauseNumber)

// Not equal to, !=
@StatementDslMaker
public infix fun ClauseNumber.NEQ(number: Number?): SelectCondition = neq(number)

// Not equal to, append to ClauseNumber
@StatementDslMaker
public infix fun ClauseNumber.NEQ(clauseNumber: ClauseNumber): SelectCondition = neq(clauseNumber)

// Greater than, >
@StatementDslMaker
public infix fun ClauseNumber.GT(number: Number): SelectCondition = gt(number)

// Greater than, append to ClauseNumber
@StatementDslMaker
public infix fun ClauseNumber.GT(clauseNumber: ClauseNumber): SelectCondition = gt(clauseNumber)

// Greater than or equal to, >=
@StatementDslMaker
public infix fun ClauseNumber.GTE(number: Number): SelectCondition = gte(number)

// Greater than or equal to, append to ClauseNumber
@StatementDslMaker
public infix fun ClauseNumber.GTE(clauseNumber: ClauseNumber): SelectCondition = gte(clauseNumber)

// If the 'number' in the 'numbers'
@StatementDslMaker
public infix fun ClauseNumber.IN(numbers: Iterable<Number>): SelectCondition = inIterable(numbers)

// If the 'number' between the 'range'
@StatementDslMaker
public infix fun ClauseNumber.BETWEEN(range: LongRange): SelectCondition = between(range)

// Equals, ==
@StatementDslMaker
public infix fun ClauseString.EQ(str: String?): SelectCondition = eq(str)

// Equals, append another ClauseString
@StatementDslMaker
public infix fun ClauseString.EQ(clauseString: ClauseString): SelectCondition = eq(clauseString)

// Not equals to, !=
@StatementDslMaker
public infix fun ClauseString.NEQ(str: String?): SelectCondition = neq(str)

// Not equals to, append another ClauseString
@StatementDslMaker
public infix fun ClauseString.NEQ(clauseString: ClauseString): SelectCondition = neq(clauseString)

// SQL LIKE operator
@StatementDslMaker
public infix fun ClauseString.LIKE(regex: String): SelectCondition = like(regex)

// SQL GLOB operator
@StatementDslMaker
public infix fun ClauseString.GLOB(regex: String): SelectCondition = glob(regex)

// Less than, <
@StatementDslMaker
public infix fun ClauseString.LT(str: String): SelectCondition = lt(str)

// Less than, append to ClauseString
@StatementDslMaker
public infix fun ClauseString.LT(clauseString: ClauseString): SelectCondition = lt(clauseString)

// Less than or equal to, <=
@StatementDslMaker
public infix fun ClauseString.LTE(str: String): SelectCondition = lte(str)

// Less than or equal to, append to ClauseString
@StatementDslMaker
public infix fun ClauseString.LTE(clauseString: ClauseString): SelectCondition = lte(clauseString)

// Greater than, >
@StatementDslMaker
public infix fun ClauseString.GT(str: String): SelectCondition = gt(str)

// Greater than, append to ClauseString
@StatementDslMaker
public infix fun ClauseString.GT(clauseString: ClauseString): SelectCondition = gt(clauseString)

// Greater than or equal to, >=
@StatementDslMaker
public infix fun ClauseString.GTE(str: String): SelectCondition = gte(str)

// Greater than or equal to, append to ClauseString
@StatementDslMaker
public infix fun ClauseString.GTE(clauseString: ClauseString): SelectCondition = gte(clauseString)

// If the 'string' in the 'strings'
@StatementDslMaker
public infix fun ClauseString.IN(strings: Iterable<String>): SelectCondition = inIterable(strings)

// If the 'string' between the 'range'
@StatementDslMaker
public infix fun ClauseString.BETWEEN(range: Pair<String, String>): SelectCondition = between(range)

// Less than, <
@StatementDslMaker
public infix fun ClauseBlob.LT(byteArray: ByteArray): SelectCondition = lt(byteArray)

// Less than, append to ClauseBlob
@StatementDslMaker
public infix fun ClauseBlob.LT(blob: ClauseBlob): SelectCondition = lt(blob)

// Less than or equal to, <=
@StatementDslMaker
public infix fun ClauseBlob.LTE(byteArray: ByteArray): SelectCondition = lte(byteArray)

// Less than or equal to, append to ClauseBlob
@StatementDslMaker
public infix fun ClauseBlob.LTE(blob: ClauseBlob): SelectCondition = lte(blob)

// Equals, ==
@StatementDslMaker
public infix fun ClauseBlob.EQ(byteArray: ByteArray?): SelectCondition = eq(byteArray)

// Equals, append to ClauseBlob
@StatementDslMaker
public infix fun ClauseBlob.EQ(blob: ClauseBlob): SelectCondition = eq(blob)

// Not equal to, !=
@StatementDslMaker
public infix fun ClauseBlob.NEQ(byteArray: ByteArray?): SelectCondition = neq(byteArray)

// Not equal to, append to ClauseBlob
@StatementDslMaker
public infix fun ClauseBlob.NEQ(blob: ClauseBlob): SelectCondition = neq(blob)

// Greater than, >
@StatementDslMaker
public infix fun ClauseBlob.GT(byteArray: ByteArray): SelectCondition = gt(byteArray)

// Greater than, append to ClauseBlob
@StatementDslMaker
public infix fun ClauseBlob.GT(blob: ClauseBlob): SelectCondition = gt(blob)

// Greater than or equal to, >=
@StatementDslMaker
public infix fun ClauseBlob.GTE(byteArray: ByteArray): SelectCondition = gte(byteArray)

// Greater than or equal to, append to ClauseBlob
@StatementDslMaker
public infix fun ClauseBlob.GTE(blob: ClauseBlob): SelectCondition = gte(blob)

// If the 'blob' in the 'blobs'
@StatementDslMaker
public infix fun ClauseBlob.IN(blobs: Iterable<ByteArray>): SelectCondition = inIterable(blobs)

// If the 'blob' between the 'range'
@StatementDslMaker
public infix fun ClauseBlob.BETWEEN(range: Pair<ByteArray, ByteArray>): SelectCondition = between(range)

// Less than, <
@StatementDslMaker
public infix fun <T : Enum<T>> ClauseEnum<T>.LT(entry: T): SelectCondition = lt(entry)

// Less than, append to ClauseEnum
@StatementDslMaker
public infix fun <T : Enum<T>> ClauseEnum<T>.LT(clauseEnum: ClauseEnum<T>): SelectCondition = lt(clauseEnum)

// Less than or equal to, <=
@StatementDslMaker
public infix fun <T : Enum<T>> ClauseEnum<T>.LTE(entry: T): SelectCondition = lte(entry)

// Less than or equal to, append to ClauseEnum
@StatementDslMaker
public infix fun <T : Enum<T>> ClauseEnum<T>.LTE(clauseEnum: ClauseEnum<T>): SelectCondition = lte(clauseEnum)

// Equals, ==
@StatementDslMaker
public infix fun <T : Enum<T>> ClauseEnum<T>.EQ(entry: T?): SelectCondition = eq(entry)

// Equals, append to ClauseEnum
@StatementDslMaker
public infix fun <T : Enum<T>> ClauseEnum<T>.EQ(clauseEnum: ClauseEnum<T>): SelectCondition = eq(clauseEnum)

// Not equal to, !=
@StatementDslMaker
public infix fun <T : Enum<T>> ClauseEnum<T>.NEQ(entry: T?): SelectCondition = neq(entry)

// Not equal to, append to ClauseEnum
@StatementDslMaker
public infix fun <T : Enum<T>> ClauseEnum<T>.NEQ(clauseEnum: ClauseEnum<T>): SelectCondition = neq(clauseEnum)

// Greater than, >
@StatementDslMaker
public infix fun <T : Enum<T>> ClauseEnum<T>.GT(entry: T): SelectCondition = gt(entry)

// Greater than, append to ClauseEnum
@StatementDslMaker
public infix fun <T : Enum<T>> ClauseEnum<T>.GT(clauseEnum: ClauseEnum<T>): SelectCondition = gt(clauseEnum)

// Greater than or equal to, >=
@StatementDslMaker
public infix fun <T : Enum<T>> ClauseEnum<T>.GTE(entry: T): SelectCondition = gte(entry)

// Greater than or equal to, append to ClauseEnum
@StatementDslMaker
public infix fun <T : Enum<T>> ClauseEnum<T>.GTE(clauseEnum: ClauseEnum<T>): SelectCondition = gte(clauseEnum)

// Condition 'IS' operator
@StatementDslMaker
public infix fun ClauseBoolean.IS(bool: Boolean): SelectCondition = _is(bool)

// Condition 'OR' operator
@StatementDslMaker
public infix fun SelectCondition.OR(prediction: SelectCondition): SelectCondition = or(prediction)

// Condition 'AND' operator
@StatementDslMaker
public infix fun SelectCondition.AND(prediction: SelectCondition): SelectCondition = and(prediction)