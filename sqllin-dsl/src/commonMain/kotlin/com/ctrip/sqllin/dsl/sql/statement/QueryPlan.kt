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

package com.ctrip.sqllin.dsl.sql.statement

import com.ctrip.sqllin.driver.CommonCursor
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI

/**
 * How SQLite runs a query, as `EXPLAIN QUERY PLAN` tells, which `EXPLAIN_QUERY_PLAN(select)` returns: the steps of
 * its plan, such as the scan of a table, the search of an index, or a temporary B-tree that sorts the rows.
 *
 * Its `toString()` writes the plan as a tree, as the `sqlite3` command-line shell does:
 * ```
 * QUERY PLAN
 * |--SEARCH book USING INDEX idx_book_author (author=?)
 * `--USE TEMP B-TREE FOR ORDER BY
 * ```
 *
 * The details are the text SQLite writes, which is meant to be read, and which differs between SQLite versions, as
 * `SCAN TABLE book` and `SCAN book` do. A check of a plan should therefore look for the names of tables and indexes,
 * and for phrases such as `USING INDEX`, `USING COVERING INDEX` and `TEMP B-TREE`, rather than compare whole details.
 * The steps of subqueries and compound SELECTs are the children of the steps they belong to, except with a SQLite
 * older than 3.24.0, such as Android's before API 30, which lists all steps in a row, with no children.
 *
 * @property steps The steps of the plan, each with the steps it consists of
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public class QueryPlan internal constructor(
    public val steps: List<Step>,
) {

    /**
     * A step of a plan.
     *
     * @property detail What the step does, in SQLite's words, as `SCAN book`
     * @property children The steps it consists of, as those of a subquery
     */
    public class Step internal constructor(
        public val detail: String,
        public val children: List<Step>,
    ) {
        override fun toString(): String = detail
    }

    /**
     * The details of all steps, each before those of its children.
     */
    public val details: List<String> = buildList { addDetails(steps) }

    private fun MutableList<String>.addDetails(steps: List<Step>) {
        for (step in steps) {
            add(step.detail)
            addDetails(step.children)
        }
    }

    override fun toString(): String = buildString {
        append("QUERY PLAN")
        appendSteps(steps, "")
    }

    private fun StringBuilder.appendSteps(steps: List<Step>, indent: String) {
        steps.forEachIndexed { index, step ->
            val isLast = index == steps.lastIndex
            append('\n').append(indent).append(if (isLast) "`--" else "|--").append(step.detail)
            appendSteps(step.children, indent + if (isLast) "   " else "|  ")
        }
    }

    internal companion object {

        /**
         * Reads the plan from the rows of `EXPLAIN QUERY PLAN`, whose columns tell its format, rather than the version
         * of SQLite: since 3.24.0, they are `id`, `parent`, `notused` and `detail`, and a step's `parent` is the `id`
         * of the step it belongs to, or 0; before, they are `selectid`, `order`, `from` and `detail`, which tell no
         * such relation, so that the steps are listed as they are.
         */
        fun of(cursor: CommonCursor): QueryPlan {
            // Every driver throws for a column that the rows don't have
            val parentColumn = runCatching { cursor.getColumnIndex("parent") }.getOrNull()?.takeIf { it >= 0 }
            val detailColumn = cursor.getColumnIndex("detail")
            if (parentColumn == null)
                return QueryPlan(buildList {
                    cursor.forEachRow { add(Step(cursor.getString(detailColumn).orEmpty(), emptyList())) }
                })
            val idColumn = cursor.getColumnIndex("id")
            val rows = buildList {
                cursor.forEachRow {
                    add(Row(cursor.getLong(idColumn), cursor.getLong(parentColumn), cursor.getString(detailColumn).orEmpty()))
                }
            }
            // Grouped rather than read in a row, so that a step listed before the one it belongs to is still its child
            val rowsByParent = rows.groupBy { it.parent }
            val ids = rows.mapTo(HashSet()) { it.id }
            fun stepsOf(rows: List<Row>): List<Step> = rows.map { Step(it.detail, stepsOf(rowsByParent[it.id].orEmpty())) }
            return QueryPlan(stepsOf(rows.filter { it.parent !in ids }))
        }

        private class Row(val id: Long, val parent: Long, val detail: String)
    }
}
