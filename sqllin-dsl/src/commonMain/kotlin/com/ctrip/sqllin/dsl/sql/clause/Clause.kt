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
 * Base interface for SQL clauses used in DSL operations.
 *
 * Marker interface for all clause types (WHERE, SET, ORDER BY, GROUP BY, HAVING, LIMIT, JOIN, etc.).
 * Clauses are type-parameterized to ensure they operate on the correct entity type.
 *
 * Implementations include:
 * - [SelectClause]: Clauses for SELECT statements (WHERE, ORDER BY, LIMIT, GROUP BY, HAVING, JOIN)
 * - [SetClause]: SET clause for UPDATE statements
 * - [ConditionClause]: Condition clauses for WHERE/HAVING
 *
 * @param T The entity type this clause operates on
 *
 * @author Yuang Qiao
 */
public sealed interface Clause<T>