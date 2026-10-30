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

@file:Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")

package com.ctrip.sqllin.dsl.sql

import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.annotation.KeyWordDslMaker
import com.ctrip.sqllin.dsl.annotation.StatementDslMaker
import kotlinx.serialization.KSerializer

/**
 * A table that a statement reads or writes with SQL's `INDEXED BY index` or `NOT INDEXED`, which make SQLite look its
 * rows up through that index, or through none, rather than through the one its query planner would choose:
 * `PersonTable INDEXED_BY "idx_person_age"` or `PersonTable.NOT_INDEXED`.
 *
 * A SELECT reads it as it reads the table, joins included, and UPDATE and DELETE write it; INSERT can't, as SQL doesn't
 * allow it there. Its columns are the table's. When SQLite can't run the statement through the index, as when the index
 * doesn't exist, or doesn't help the WHERE clause, the statement fails, with "no query solution".
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public class IndexedTable<T> internal constructor(
    internal val table: Table<T>,
    private val clause: String,
) : Relation<T>(table.tableName) {

    override fun kSerializer(): KSerializer<T> = table.kSerializer()

    override val fromSQL: String
        get() = "$tableName$clause"
}

/**
 * This table, which a statement looks its rows up in through [index], as SQL's `table INDEXED BY index` does.
 *
 * @throws IllegalArgumentException if [index] is blank
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun <T> Table<T>.INDEXED_BY(index: String): IndexedTable<T> {
    require(index.isNotBlank()) { "The name of an index can't be blank." }
    return IndexedTable(this, " INDEXED BY $index")
}

/**
 * This table, which a statement looks its rows up in without any index, as SQL's `table NOT INDEXED` does.
 */
@ExperimentalDSLDatabaseAPI
@KeyWordDslMaker
public val <T> Table<T>.NOT_INDEXED: IndexedTable<T>
    get() = IndexedTable(this, " NOT INDEXED")
