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

package com.ctrip.sqllin.dsl.sql.operation

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.statement.InsertStatement
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.compiler.encodeEntities2InsertValues

/**
 * INSERT operation builder.
 *
 * Constructs INSERT statements by encoding entity objects into SQL VALUES clauses with
 * parameterized queries. Supports both auto-generated primary keys and user-provided keys.
 *
 * @author Yuang Qiao
 */
internal object Insert : Operation {

    override val sqlStr: String
        get() = "INSERT INTO "

    /**
     * Builds an INSERT statement for the given entities.
     *
     * Generates SQL in the format:
     * ```
     * INSERT INTO table_name (column1, column2, ...) VALUES (?, ?, ...), (?, ?, ...), ...
     * ```
     *
     * @param table Table definition containing serialization metadata
     * @param connection Database connection for execution
     * @param entities Entities to insert
     * @param isInsertWithId Whether to include the primary key column for auto-increment keys
     * @return INSERT statement ready for execution
     */
    fun <T> insert(table: Table<T>, connection: DatabaseConnection, entities: Iterable<T>, isInsertWithId: Boolean = false): SingleStatement {
        val parameters = ArrayList<Any?>()
        val sql = buildString {
            append(sqlStr)
            append(table.tableName)
            append(' ')
            encodeEntities2InsertValues(table, this,entities, parameters, isInsertWithId)
        }
        return InsertStatement(sql, connection, parameters)
    }
}