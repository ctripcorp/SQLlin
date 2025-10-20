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

package com.ctrip.sqllin.dsl.sql.statement

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.sql.clause.ClauseElement
import com.ctrip.sqllin.dsl.sql.clause.SelectCondition
import kotlinx.serialization.DeserializationStrategy

/**
 * Intermediate JOIN statement requiring an ON or USING condition.
 *
 * Represents a JOIN operation that has been initiated but not yet completed. In SQL, a JOIN
 * must specify how tables relate through either:
 * - USING clause: Lists common column names to join on
 * - ON clause: Specifies a join condition expression
 *
 * This class enforces the requirement at compile time by not extending [SelectStatement].
 * It converts to [JoinSelectStatement] only after a condition is added.
 *
 * @param R The result entity type after JOIN
 *
 * @author Yuang Qiao
 */
public class JoinStatementWithoutCondition<R> internal constructor(
    private val sqlStr: String,
    private val deserializer: DeserializationStrategy<R>,
    private val connection: DatabaseConnection,
    private val container: StatementContainer,
    private val addSelectStatement: (SelectStatement<R>) -> Unit
) {
    /**
     * Completes the JOIN by adding a USING clause.
     *
     * Generates SQL in the format: `JOIN table USING (column1, column2, ...)`.
     * The USING clause specifies columns that exist in both tables with the same name.
     *
     * @param clauseElements Column elements to join on (must not be empty)
     * @return Completed JOIN statement that can accept further clauses
     */
    internal infix fun convertToJoinSelectStatement(clauseElements: Iterable<ClauseElement>): JoinSelectStatement<R> {
        val iterator = clauseElements.iterator()
        require(iterator.hasNext()) { "Param 'clauseElements' must not be empty!!!" }
        val sql = buildString {
            append(sqlStr)
            clauseElements.joinTo(
                buffer = this,
                separator = ",",
                prefix = " USING (",
                postfix = ")",
            )
        }
        val joinStatement = JoinSelectStatement(sql, deserializer, connection, container, null)
        addSelectStatement(joinStatement)
        return joinStatement
    }

    /**
     * Completes the JOIN by adding an ON clause.
     *
     * Generates SQL in the format: `JOIN table ON condition`.
     * The ON clause specifies an arbitrary boolean expression for joining tables.
     *
     * @param condition Join condition (e.g., table1.id = table2.foreign_id)
     * @return Completed JOIN statement that can accept further clauses
     */
    internal infix fun convertToJoinSelectStatement(condition: SelectCondition): JoinSelectStatement<R> {
        val sql = buildString {
            append(sqlStr)
            append(" ON ")
            append(condition.conditionSQL)
        }
        val joinStatement = JoinSelectStatement(sql, deserializer, connection, container, condition.parameters)
        addSelectStatement(joinStatement)
        return joinStatement
    }
}