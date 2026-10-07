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
import com.ctrip.sqllin.dsl.sql.compiler.sqlLiteral
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.statement.TableStructureStatement

/**
 * VACUUM operation, which rebuilds the database file, or writes a vacuumed copy of it to another file.
 *
 * @author Yuang Qiao
 */
internal object Vacuum : Operation {

    override val sqlStr: String
        get() = "VACUUM"

    fun vacuum(connection: DatabaseConnection): SingleStatement = TableStructureStatement(sqlStr, connection)

    fun vacuumInto(file: String, connection: DatabaseConnection): SingleStatement =
        TableStructureStatement("$sqlStr INTO ${sqlLiteral(file)}", connection)
}
