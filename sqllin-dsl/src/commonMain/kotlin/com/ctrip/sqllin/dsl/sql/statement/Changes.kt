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

/**
 * What the statements of a scope may change when they run, from least to most.
 *
 * @author Yuang Qiao
 */
internal enum class Changes {

    /** Nothing: the statements only read. */
    NONE,

    /** The rows of tables: INSERT, UPDATE or DELETE. */
    ROWS,

    /** The tables themselves: CREATE, DROP, ALTER or PRAGMA, which may also drop or replace their triggers. */
    SCHEMA;

    companion object {

        fun of(statement: SingleStatement): Changes = when (statement) {
            is SelectStatement<*> -> NONE
            is TableStructureStatement -> SCHEMA
            is InsertStatement, is UpdateDeleteStatement, is UpdateStatementWithoutWhereClause<*> -> ROWS
        }

        /**
         * The table [statement] writes rows to, or null if it writes none.
         */
        fun writtenTable(statement: SingleStatement): String? = when (statement) {
            is InsertStatement -> statement.table
            is UpdateDeleteStatement -> statement.table
            is UpdateStatementWithoutWhereClause<*> -> statement.table
            is SelectStatement<*>, is TableStructureStatement -> null
        }
    }
}
