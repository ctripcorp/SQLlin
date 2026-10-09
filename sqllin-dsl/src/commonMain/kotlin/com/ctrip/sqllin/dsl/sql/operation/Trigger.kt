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

package com.ctrip.sqllin.dsl.sql.operation

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.sql.TriggerDefinition
import com.ctrip.sqllin.dsl.sql.compiler.inlineParameters
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.statement.TableStructureStatement

/**
 * CREATE TRIGGER and DROP TRIGGER operations.
 *
 * SQLite doesn't let a trigger take parameters, so the values of its condition and of the statements of its body are
 * written into its SQL as literals.
 *
 * @author Yuang Qiao
 */
@OptIn(ExperimentalDSLDatabaseAPI::class)
internal object Trigger : Operation {

    override val sqlStr: String
        get() = "CREATE TRIGGER "

    fun create(definition: TriggerDefinition, body: List<SingleStatement>, connection: DatabaseConnection): SingleStatement {
        val timing = definition.timing
        val sql = buildString {
            append(sqlStr)
            if (timing.trigger.isIfNotExists)
                append("IF NOT EXISTS ")
            append(timing.trigger.name)
            append(' ')
            append(timing.timing)
            append(' ')
            append(timing.event.sql)
            append(" ON ")
            append(definition.table.tableName)
            definition.condition?.let {
                append(" WHEN ")
                append(inlineParameters(it.conditionSQL, it.parameters))
            }
            append(" BEGIN ")
            for (statement in body) {
                append(inlineParameters(statement.sqlStr, statement.parameters))
                append("; ")
            }
            append("END")
        }
        return TableStructureStatement(sql, connection)
    }

    fun drop(name: String, isIfExists: Boolean, connection: DatabaseConnection): SingleStatement =
        TableStructureStatement("DROP TRIGGER ${if (isIfExists) "IF EXISTS " else ""}$name", connection)
}
