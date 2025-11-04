/*
 * Copyright (C) 2025 Ctrip.com.
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
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.statement.TableStructureStatement

/**
 * CREATE TABLE operation builder.
 *
 * Constructs CREATE TABLE statements by inspecting entity serialization descriptors and
 * generating appropriate SQLite column definitions with type mappings, nullability constraints,
 * and primary key declarations.
 *
 * @author Yuang Qiao
 */
internal object Create : Operation {

    override val sqlStr: String
        get() = ""

    /**
     * Builds a CREATE TABLE statement for the given table definition.
     *
     * @param table Table definition containing entity metadata
     * @param connection Database connection for execution
     * @return CREATE statement ready for execution
     */
    fun <T> create(table: Table<T>, connection: DatabaseConnection): SingleStatement =
        TableStructureStatement(table.createSQL, connection)
}