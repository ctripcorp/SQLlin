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

/**
 * Update statement without 'WHERE' clause, that could execute or link 'WHERE' clause
 * @author yaqiao
 */

public class UpdateStatementWithoutWhereClause<T> internal constructor(
    preSQLStr: String,
    internal val statementContainer: StatementContainer,
    internal val connection: DatabaseConnection,
    override val parameters: MutableList<String>?,
) : SingleStatement(preSQLStr) {
    public override fun execute(): Unit = connection.executeUpdateDelete(sqlStr, params)
}

public class UpdateDeleteStatement internal constructor(
    sqlStr: String,
    private val connection: DatabaseConnection,
    override val parameters: MutableList<String>?,
) : SingleStatement(sqlStr) {
    public override fun execute(): Unit = connection.executeUpdateDelete(sqlStr, params)
}

public class InsertStatement internal constructor(
    sqlStr: String,
    private val connection: DatabaseConnection,
    override val parameters: MutableList<String>,
) : SingleStatement(sqlStr) {
    public override fun execute(): Unit = connection.executeInsert(sqlStr, params)
}
