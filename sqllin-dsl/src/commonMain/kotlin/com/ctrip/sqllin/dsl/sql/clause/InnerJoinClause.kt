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

package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.sql.Table

/**
 * SQL "INNER JOIN" clause
 * @author yaqiao
 */

internal class InnerJoinClause<R>(
    vararg tables: Table<*>,
) : JoinClause<R>(*tables) {

    override val clauseName: String = " JOIN "
}

public fun <R> JOIN(vararg tables: Table<*>): JoinClause<R> = InnerJoinClause(*tables)

@Suppress("NOTHING_TO_INLINE")
public inline fun <R> INNER_JOIN(vararg tables: Table<*>): JoinClause<R> = JOIN(*tables)

internal class NaturalInnerJoinClause<R>(
    vararg tables: Table<*>,
) : NaturalJoinClause<R>(*tables) {

    override val clauseName: String = " NATURAL JOIN "
}

public fun <R> NATURAL_JOIN(vararg tables: Table<*>): NaturalJoinClause<R> = NaturalInnerJoinClause(*tables)

@Suppress("NOTHING_TO_INLINE")
public inline fun <R> NATURAL_INNER_JOIN(vararg tables: Table<*>): NaturalJoinClause<R> = NATURAL_JOIN(*tables)