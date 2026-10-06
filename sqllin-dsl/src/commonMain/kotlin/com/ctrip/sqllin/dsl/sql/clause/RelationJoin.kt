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
import com.ctrip.sqllin.dsl.annotation.PlatformDependentSQLiteAPI
import com.ctrip.sqllin.dsl.annotation.StatementDslMaker
import com.ctrip.sqllin.dsl.sql.From
import com.ctrip.sqllin.dsl.sql.Relation

/**
 * Joins of relations: tables, views and derived tables, joined before SELECT reads them, as the FROM clause of SQL
 * does, so that every SELECT works on them alike.
 *
 * Example:
 * ```kotlin
 * @Serializable
 * data class PersonBooks(val name: String, val books: Long)
 *
 * // SELECT person.name AS name,count(book.title) AS books FROM person LEFT OUTER JOIN book ON person.id=book.authorId
 * //     GROUP BY person.id
 * (FROM(PersonTable) LEFT_OUTER_JOIN BookTable ON (PersonTable.id EQ BookTable.authorId)) SELECT listOf(
 *     PersonTable.name AS PersonBooks::name,
 *     PersonTable.count(BookTable.title) AS PersonBooks::books,
 * ) GROUP_BY PersonTable.id
 * ```
 *
 * A join reads its rows into the type a SELECT names, as `X<R>()`, `WHERE<R>(...)` or result columns do, and checks it
 * against its relations when the statement is built: every property has to name a column of one of them, of a single
 * one unless USING or a NATURAL join merges them, as columns of the same name of two relations can't be told apart
 * otherwise; and has to be nullable when its column or expression can be NULL, which an outer join makes those of a
 * relation it can leave without a matching row, but `count`. SQLlin writes the columns with their relations' names.
 *
 * A join starts with `FROM(relation)`, and its joins are applied in the order they are written:
 * `FROM(a) INNER_JOIN b ON (...) LEFT_OUTER_JOIN c ON (...)`. A relation can only be joined once; to join a table with
 * itself, join a derived table of its rows, which gives it another name.
 *
 * @author Yuang Qiao
 */

/**
 * Relations joined as a FROM clause writes them, such as `person INNER JOIN book ON person.id=book.authorId`, which a
 * SELECT reads, or another join extends. `FROM(relation)` starts one.
 *
 * A SELECT of a join has to name the type it reads rows into, as rows of a join have no type of their own:
 * `joined SELECT X<R>()`, `joined SELECT WHERE<R>(...)`, or result columns.
 */
@ExperimentalDSLDatabaseAPI
public class JoinedRelation internal constructor(internal val from: From)

/**
 * A join that still needs its constraint: `ON condition`, or `USING (columns)`.
 */
@ExperimentalDSLDatabaseAPI
public class JoinWithoutCondition internal constructor(
    private val from: From,
    private val keyword: String,
    private val relation: Relation<*>,
    private val isLeftNullable: Boolean,
    private val isRightNullable: Boolean,
) {

    internal fun on(condition: SelectCondition): JoinedRelation = join(
        From.Constraint(" ON ${condition.conditionSQL}", condition.parameters.orEmpty(), condition.tables)
    )

    internal fun using(columns: Iterable<ClauseElement<*>>): JoinedRelation {
        val names = columns.map { it.valueName }
        require(names.isNotEmpty()) { "USING needs at least one column." }
        return join(From.Constraint(names.joinToString(",", " USING (", ")"), mergedColumns = names.toSet()))
    }

    private fun join(constraint: From.Constraint): JoinedRelation =
        JoinedRelation(from.join(keyword, relation, constraint, isLeftNullable, isRightNullable, isNatural = false))
}

/**
 * Starts a join with [relation], the first relation of a FROM clause, which `INNER_JOIN`, `LEFT_OUTER_JOIN` and the
 * other joins extend: `FROM(PersonTable) INNER_JOIN BookTable ON (...)`.
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public fun FROM(relation: Relation<*>): JoinedRelation = JoinedRelation(From.of(relation))

/**
 * Completes this join with `ON condition`: the rows of the relations that satisfy [condition] match.
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun JoinWithoutCondition.ON(condition: SelectCondition): JoinedRelation = on(condition)

/**
 * Completes this join with `USING (column)`: the rows whose values of [column], which both relations have, are the same
 * match, and the join merges the two columns into one.
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun JoinWithoutCondition.USING(column: ClauseElement<*>): JoinedRelation = using(listOf(column))

/**
 * Completes this join with `USING (columns)`: the rows whose values of [columns], which both relations have, are the
 * same match, and the join merges the columns of the same name into one.
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun JoinWithoutCondition.USING(columns: Iterable<ClauseElement<*>>): JoinedRelation = using(columns)

/**
 * Joins [relation] to this join with `INNER JOIN`: the rows of both that match, which [ON] or [USING] completes.
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun JoinedRelation.INNER_JOIN(relation: Relation<*>): JoinWithoutCondition =
    from.innerjoin(relation)

@OptIn(ExperimentalDSLDatabaseAPI::class)
private fun From.innerjoin(relation: Relation<*>): JoinWithoutCondition =
    JoinWithoutCondition(this, "INNER JOIN", relation, isLeftNullable = false, isRightNullable = false)

/**
 * Joins [relation] to this join with `LEFT OUTER JOIN`: the rows of both that match, and the rows of the relations
 * before it that match none, with NULL in the columns of [relation], which [ON] or [USING] completes.
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun JoinedRelation.LEFT_OUTER_JOIN(relation: Relation<*>): JoinWithoutCondition =
    from.leftouterjoin(relation)

@OptIn(ExperimentalDSLDatabaseAPI::class)
private fun From.leftouterjoin(relation: Relation<*>): JoinWithoutCondition =
    JoinWithoutCondition(this, "LEFT OUTER JOIN", relation, isLeftNullable = false, isRightNullable = true)

/**
 * Joins [relation] to this join with `RIGHT OUTER JOIN`: the rows of both that match, and the rows of [relation] that
 * match none, with NULL in the columns of the relations before it, which [ON] or [USING] completes.
 *
 * `RIGHT` and `FULL` joins need SQLite 3.39.0, which Android has from API 34 on.
 */
@ExperimentalDSLDatabaseAPI
@PlatformDependentSQLiteAPI
@StatementDslMaker
public infix fun JoinedRelation.RIGHT_OUTER_JOIN(relation: Relation<*>): JoinWithoutCondition =
    from.rightouterjoin(relation)

@OptIn(ExperimentalDSLDatabaseAPI::class)
private fun From.rightouterjoin(relation: Relation<*>): JoinWithoutCondition =
    JoinWithoutCondition(this, "RIGHT OUTER JOIN", relation, isLeftNullable = true, isRightNullable = false)

/**
 * Joins [relation] to this join with `FULL OUTER JOIN`: the rows of both that match, and the rows of each that match
 * none, with NULL in the columns of the other, which [ON] or [USING] completes.
 *
 * `RIGHT` and `FULL` joins need SQLite 3.39.0, which Android has from API 34 on.
 */
@ExperimentalDSLDatabaseAPI
@PlatformDependentSQLiteAPI
@StatementDslMaker
public infix fun JoinedRelation.FULL_OUTER_JOIN(relation: Relation<*>): JoinWithoutCondition =
    from.fullouterjoin(relation)

@OptIn(ExperimentalDSLDatabaseAPI::class)
private fun From.fullouterjoin(relation: Relation<*>): JoinWithoutCondition =
    JoinWithoutCondition(this, "FULL OUTER JOIN", relation, isLeftNullable = true, isRightNullable = true)

/**
 * Joins [relation] to this join with `CROSS JOIN`: every row of the relations before it with every row of [relation].
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun JoinedRelation.CROSS_JOIN(relation: Relation<*>): JoinedRelation =
    from.crossjoin(relation)

@OptIn(ExperimentalDSLDatabaseAPI::class)
private fun From.crossjoin(relation: Relation<*>): JoinedRelation =
    JoinedRelation(join("CROSS JOIN", relation, From.Constraint.NONE, isLeftNullable = false, isRightNullable = false, isNatural = false))

/**
 * Joins [relation] to this join with `NATURAL JOIN`: the rows of both whose columns of the same names have the same
 * values, which it merges.
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun JoinedRelation.NATURAL_JOIN(relation: Relation<*>): JoinedRelation =
    from.naturaljoin(relation)

@OptIn(ExperimentalDSLDatabaseAPI::class)
private fun From.naturaljoin(relation: Relation<*>): JoinedRelation =
    JoinedRelation(join("NATURAL JOIN", relation, From.Constraint.NONE, isLeftNullable = false, isRightNullable = false, isNatural = true))

/**
 * Joins [relation] to this join with `NATURAL LEFT OUTER JOIN`: as [NATURAL_JOIN] does, and the rows of the relations
 * before it that match none, with NULL in the columns of [relation].
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun JoinedRelation.NATURAL_LEFT_OUTER_JOIN(relation: Relation<*>): JoinedRelation =
    from.naturalleftouterjoin(relation)

@OptIn(ExperimentalDSLDatabaseAPI::class)
private fun From.naturalleftouterjoin(relation: Relation<*>): JoinedRelation =
    JoinedRelation(join("NATURAL LEFT OUTER JOIN", relation, From.Constraint.NONE, isLeftNullable = false, isRightNullable = true, isNatural = true))

/**
 * Joins [relation] to this join with `NATURAL RIGHT OUTER JOIN`: as [NATURAL_JOIN] does, and the rows of [relation]
 * that match none, with NULL in the columns of the relations before it.
 *
 * `RIGHT` and `FULL` joins need SQLite 3.39.0, which Android has from API 34 on.
 */
@ExperimentalDSLDatabaseAPI
@PlatformDependentSQLiteAPI
@StatementDslMaker
public infix fun JoinedRelation.NATURAL_RIGHT_OUTER_JOIN(relation: Relation<*>): JoinedRelation =
    from.naturalrightouterjoin(relation)

@OptIn(ExperimentalDSLDatabaseAPI::class)
private fun From.naturalrightouterjoin(relation: Relation<*>): JoinedRelation =
    JoinedRelation(join("NATURAL RIGHT OUTER JOIN", relation, From.Constraint.NONE, isLeftNullable = true, isRightNullable = false, isNatural = true))

/**
 * Joins [relation] to this join with `NATURAL FULL OUTER JOIN`: as [NATURAL_JOIN] does, and the rows of each that match
 * none, with NULL in the columns of the other.
 *
 * `RIGHT` and `FULL` joins need SQLite 3.39.0, which Android has from API 34 on.
 */
@ExperimentalDSLDatabaseAPI
@PlatformDependentSQLiteAPI
@StatementDslMaker
public infix fun JoinedRelation.NATURAL_FULL_OUTER_JOIN(relation: Relation<*>): JoinedRelation =
    from.naturalfullouterjoin(relation)

@OptIn(ExperimentalDSLDatabaseAPI::class)
private fun From.naturalfullouterjoin(relation: Relation<*>): JoinedRelation =
    JoinedRelation(join("NATURAL FULL OUTER JOIN", relation, From.Constraint.NONE, isLeftNullable = true, isRightNullable = true, isNatural = true))
