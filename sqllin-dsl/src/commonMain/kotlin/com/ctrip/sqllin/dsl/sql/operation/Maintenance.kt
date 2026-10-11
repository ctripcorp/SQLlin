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
import com.ctrip.sqllin.dsl.sql.clause.Collation
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.statement.TableStructureStatement

/**
 * ANALYZE, which gathers the statistics of tables and indexes that the query planner chooses indexes with, and REINDEX,
 * which rebuilds indexes.
 *
 * @author Yuang Qiao
 */
internal object Maintenance {

    fun analyze(name: String?, connection: DatabaseConnection): SingleStatement =
        TableStructureStatement(if (name == null) "ANALYZE" else "ANALYZE $name", connection)

    fun reindex(name: String?, connection: DatabaseConnection): SingleStatement =
        TableStructureStatement(if (name == null) "REINDEX" else "REINDEX $name", connection)

    @OptIn(ExperimentalDSLDatabaseAPI::class)
    fun reindex(collation: Collation, connection: DatabaseConnection): SingleStatement =
        TableStructureStatement("REINDEX ${collation.name}", connection)
}
