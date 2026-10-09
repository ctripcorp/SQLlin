/*
 * Copyright (C) 2026 Ctrip.com.
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

@file:Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")

package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.annotation.StatementDslMaker
import com.ctrip.sqllin.dsl.sql.compiler.inlineParameters

/**
 * The condition of a branch of `CASE`, which `THEN` gives the branch's value: `WHEN(age LT 18) THEN literal("minor")`.
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public class When internal constructor(internal val condition: SelectCondition)

/**
 * A branch of `CASE`: the value it gives where its condition holds.
 */
@ExperimentalDSLDatabaseAPI
public class CaseBranch<out E : ClauseElement<*>> internal constructor(internal val condition: SelectCondition, internal val value: E)

/**
 * Starts a branch of `CASE` with its [condition].
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public fun WHEN(condition: SelectCondition): When = When(condition)

/**
 * Gives this branch of `CASE` its [value].
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun <E : ClauseElement<*>> When.THEN(value: E): CaseBranch<E> = CaseBranch(condition, value)

/**
 * SQL's searched `CASE`: the value of the first of [branches] whose condition holds, or [ELSE] where none does, which is
 * NULL if it is omitted.
 * ```kotlin
 * CASE(
 *     WHEN(age LT 18) THEN literal("minor"),
 *     WHEN(age LT 65) THEN literal("adult"),
 *     ELSE = literal("senior"),
 * )
 * // CASE WHEN person.age<18 THEN 'minor' WHEN person.age<65 THEN 'adult' ELSE 'senior' END
 * ```
 * The branches and `ELSE` have values of the same type, which `CASE` has. It can be NULL where a value can, or where
 * `ELSE` is omitted. The values of the conditions are written into the SQL, as the values of any expression are.
 *
 * @throws IllegalArgumentException if there are no branches
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
@Suppress("UNCHECKED_CAST")
public fun <E : ClauseElement<*>> CASE(vararg branches: CaseBranch<E>, ELSE: E? = null): E {
    require(branches.isNotEmpty()) { "CASE needs at least one branch: WHEN(condition) THEN value." }
    val values = branches.map { it.value } + listOfNotNull(ELSE)
    val conditions = branches.map { it.condition }
    val sql = buildString {
        append("CASE")
        for (branch in branches) {
            append(" WHEN ")
            append(inlineParameters(branch.condition.conditionSQL, branch.condition.parameters))
            append(" THEN ")
            append(branch.value.sql)
        }
        if (ELSE != null) {
            append(" ELSE ")
            append(ELSE.sql)
        }
        append(" END")
    }
    val traits = Traits(
        isNullable = ELSE == null || values.any { it.isNullable },
        isAggregate = values.any { it.isAggregate },
        isNullOnNoRows = ELSE == null || values.any { it.isNullOnNoRows },
        columnTables = values.flatMapTo(LinkedHashSet()) { it.columnTables },
        tables = values.flatMapTo(LinkedHashSet()) { it.tables } + conditions.flatMap { it.tables },
        isDeterministic = values.all { it.isDeterministic } && conditions.all { it.isDeterministic },
    )
    return branches.first().value.derive(sql, traits) as E
}
