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

/**
 * Base interface for clauses used in SELECT statements.
 *
 * Represents SQL clauses that can be appended to SELECT queries to filter, order, group,
 * limit, or join data. Each implementation provides its SQL string representation.
 *
 * Implementations:
 * - [WhereClause]: WHERE condition filtering
 * - [OrderByClause]: ORDER BY sorting
 * - [LimitClause]: LIMIT row count restriction
 * - [OffsetClause]: OFFSET row skipping
 * - [GroupByClause]: GROUP BY aggregation grouping
 * - [HavingClause]: HAVING condition for grouped data
 * - [JoinClause], [InnerJoinClause], [LeftOuterJoinClause], [CrossJoinClause], [NaturalJoinClause]: JOIN operations
 *
 * @param T The entity type this clause operates on
 * @property clauseStr The SQL string representation (e.g., " WHERE id = ?", " ORDER BY name ASC")
 *
 * @author Yuang Qiao
 */
public sealed interface SelectClause<T> : Clause<T> {
    public val clauseStr: String
}