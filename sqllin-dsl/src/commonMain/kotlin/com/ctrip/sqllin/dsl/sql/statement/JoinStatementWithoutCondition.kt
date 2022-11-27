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
import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.clause.ClauseElement
import com.ctrip.sqllin.dsl.sql.clause.SelectCondition
import kotlinx.serialization.DeserializationStrategy

/**
 * SQL 'JOIN' statement, but need add 'ON' or 'USING' statement
 * @author yaqiao
 */

public class JoinStatementWithoutCondition<R : DBEntity<R>> internal constructor(
    private val sqlStr: String,
    private val deserializer: DeserializationStrategy<R>,
    private val connection: DatabaseConnection,
    private val container: StatementContainer,
    private val addSelectStatement: (SelectStatement<R>) -> Unit
) {
    internal infix fun convertToJoinSelectStatement(clauseElements: Iterable<ClauseElement>): JoinSelectStatement<R> {
        val iterator = clauseElements.iterator()
        require(iterator.hasNext()) { "Param 'clauseElements' must not be empty!!!" }
        val sql = buildString {
            append(sqlStr)
            append(" USING ")
            do {
                append(iterator.next().valueName)
                val hasNext = iterator.hasNext()
                if (hasNext)
                    append(',')
            } while (hasNext)
        }
        val joinStatement = JoinSelectStatement(sql, deserializer, connection, container)
        addSelectStatement(joinStatement)
        return joinStatement
    }

    internal infix fun convertToJoinSelectStatement(condition: SelectCondition): JoinSelectStatement<R> {
        val sql = buildString {
            append(sqlStr)
            append(" ON ")
            append(condition.conditionSQL)
        }
        val joinStatement = JoinSelectStatement(sql, deserializer, connection, container)
        addSelectStatement(joinStatement)
        return joinStatement
    }
}