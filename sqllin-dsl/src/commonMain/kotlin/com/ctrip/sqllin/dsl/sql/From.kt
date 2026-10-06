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

package com.ctrip.sqllin.dsl.sql

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder

/**
 * What a SELECT reads, as its FROM clause writes it: a relation, or the relations of a join.
 *
 * It finds the columns a SELECT names among its relations, so that the SELECT can check them: a column of one relation
 * is written with its relation's name, as other relations may have a column of that name, while a column that a USING
 * or a NATURAL join merges is written with its name alone. A relation that an outer join can leave without a matching
 * row is nullable, as its columns are NULL in such a row.
 *
 * @property members The relations, in the order they are joined
 * @property sql The FROM clause, without FROM
 * @property parameters The parameters of [sql], which a statement binds before those of its other clauses
 * @property tables The tables and views the relations read, which an observed query watches
 * @property mergedColumns The names of the columns that USING or NATURAL joins merge
 *
 * @author Yuang Qiao
 */
internal class From private constructor(
    val members: List<Member>,
    val sql: String,
    val parameters: List<Any?>,
    val tables: Set<String>,
    private val mergedColumns: Set<String>,
) {

    /**
     * A relation of a FROM clause, which is [isNullable] if an outer join can leave it without a matching row.
     */
    class Member(val relation: Relation<*>, val isNullable: Boolean) {
        val columns: SerialDescriptor
            get() = relation.kSerializer().descriptor
    }

    /**
     * A column that a SELECT names, as [find] finds it.
     *
     * @property descriptor The column's descriptor, of its relation's row type
     * @property isNullable Whether the column can be NULL: as it is nullable, or its relation is
     * @property relation The relation the column belongs to
     * @property sql The column as the SELECT writes it
     */
    class Column(val descriptor: SerialDescriptor, val isNullable: Boolean, val relation: Relation<*>, val sql: String)

    val isJoin: Boolean
        get() = members.size > 1

    /**
     * The names of the relations an outer join can leave without a matching row.
     */
    val nullableTables: Set<String> = members.filterTo(ArrayList()) { it.isNullable }.mapTo(HashSet()) { it.relation.tableName }

    /**
     * How a message names what the SELECT reads.
     */
    val description: String
        get() = if (isJoin)
            "the join of ${members.joinToString { "'${it.relation.tableName}'" }}"
        else
            "table '${members.single().relation.tableName}'"

    /**
     * Returns the column named [name] that a SELECT of [projectionName] reads into its property of that name.
     *
     * @throws IllegalArgumentException if no relation has such a column, or several have one that isn't merged
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun find(name: String, projectionName: String): Column {
        val candidates = members.filter { it.columns.getElementIndex(name) != CompositeDecoder.UNKNOWN_NAME }
        require(candidates.isNotEmpty()) {
            "Can't select '$projectionName' from $description: its property '$name' isn't a column of ${if (isJoin) "any of them" else "that table"}."
        }
        val isMerged = name in mergedColumns
        require(candidates.size == 1 || isMerged) {
            "Can't select '$projectionName' from $description: its property '$name' is a column of ${candidates.joinToString { "'${it.relation.tableName}'" }}, which can't be told apart. Select one of them with AS, as in `${candidates.first().relation.tableName}.$name AS $projectionName::$name`."
        }
        val member = candidates.first()
        val columns = member.columns
        val descriptor = columns.getElementDescriptor(columns.getElementIndex(name))
        val sql = if (!isJoin || isMerged) name else "${member.relation.tableName}.$name"
        return Column(descriptor, descriptor.isNullable || member.isNullable, member.relation, sql)
    }

    /**
     * Returns this FROM clause joined with [relation] by [keyword], such as `INNER JOIN`, under [constraint].
     *
     * @param isLeftNullable Whether the join can leave the relations before it without a matching row, as RIGHT and
     * FULL OUTER JOIN do
     * @param isRightNullable Whether the join can leave [relation] without a matching row, as LEFT and FULL OUTER JOIN do
     * @param isNatural Whether the join is NATURAL, which merges the columns of the same name
     * @throws IllegalArgumentException if [relation] is already joined, which only a derived table of it can be again
     */
    fun join(
        keyword: String,
        relation: Relation<*>,
        constraint: Constraint,
        isLeftNullable: Boolean,
        isRightNullable: Boolean,
        isNatural: Boolean,
    ): From {
        require(members.none { it.relation.tableName == relation.tableName }) {
            "Can't join '${relation.tableName}' with $description, which already has it. To join a table with itself, join a derived table of its rows, which gives it another name."
        }
        val newMembers = members.map { if (isLeftNullable) Member(it.relation, true) else it } + Member(relation, isRightNullable)
        val naturalColumns = if (isNatural) {
            val names = relation.columnNames()
            members.flatMapTo(HashSet()) { it.relation.columnNames() } intersect names
        } else {
            emptySet()
        }
        return From(
            members = newMembers,
            sql = "$sql $keyword ${relation.fromSQL}${constraint.sql}",
            parameters = parameters + relation.fromParameters + constraint.parameters,
            tables = tables + relation.readTables + constraint.tables,
            mergedColumns = mergedColumns + constraint.mergedColumns + naturalColumns,
        )
    }

    /**
     * The constraint of a join: ON, USING, or none.
     *
     * @property sql The constraint as the join writes it, after the relation
     * @property parameters The parameters of [sql]
     * @property tables The tables the subqueries of an ON condition read
     * @property mergedColumns The names of the columns USING merges
     */
    class Constraint(
        val sql: String,
        val parameters: List<Any?> = emptyList(),
        val tables: Set<String> = emptySet(),
        val mergedColumns: Set<String> = emptySet(),
    ) {
        companion object {
            val NONE = Constraint("")
        }
    }

    companion object {

        /**
         * Returns the FROM clause of [relation] alone.
         */
        fun of(relation: Relation<*>): From =
            From(listOf(Member(relation, false)), relation.fromSQL, relation.fromParameters, relation.readTables, emptySet())

        @OptIn(ExperimentalSerializationApi::class)
        private fun Relation<*>.columnNames(): Set<String> {
            val descriptor = kSerializer().descriptor
            return (0 ..< descriptor.elementsCount).mapTo(HashSet()) { descriptor.getElementName(it) }
        }
    }
}
