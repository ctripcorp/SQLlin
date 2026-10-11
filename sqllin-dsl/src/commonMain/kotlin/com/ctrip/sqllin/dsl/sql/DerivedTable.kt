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

package com.ctrip.sqllin.dsl.sql

import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.sql.statement.SelectStatement
import kotlinx.serialization.KSerializer

/**
 * A derived table: a SELECT written in the FROM clause or a JOIN of another query, as `(SELECT ...) AS name`.
 *
 * Its rows are those of [view]'s type, and it is named after [view], whose column properties so name its columns,
 * though no such view has to exist in the database. Like a view, it can only be read.
 *
 * @author Yuang Qiao
 */
@OptIn(ExperimentalDSLDatabaseAPI::class)
internal class DerivedTable<T>(private val view: View<T>, select: SelectStatement<T>) : Relation<T>(view.tableName) {

    override fun kSerializer(): KSerializer<T> = view.kSerializer()

    override val fromSQL: String = "(${select.sqlStr}) AS $tableName"

    override val fromParameters: List<Any?> = select.parameters?.toList().orEmpty()

    override val readTables: Set<String> = select.tables
}
