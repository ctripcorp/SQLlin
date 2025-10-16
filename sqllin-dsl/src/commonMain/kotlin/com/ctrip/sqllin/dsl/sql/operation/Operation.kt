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

package com.ctrip.sqllin.dsl.sql.operation

/**
 * Base interface for SQL operations in the DSL.
 *
 * Marker interface for operation builders (SELECT, UPDATE, DELETE, INSERT, CREATE) that construct
 * SQL strings. Each operation implementation accumulates clauses and generates the final SQL.
 *
 * Implementations:
 * - [SelectBuilder]: SELECT queries
 * - [UpdateBuilder]: UPDATE statements
 * - [DeleteBuilder]: DELETE statements
 * - [InsertBuilder]: INSERT statements
 * - [CreateBuilder]: CREATE TABLE statements
 *
 * @property sqlStr The accumulated SQL string built by this operation
 *
 * @author Yuang Qiao
 */
internal interface Operation {
    val sqlStr: String
}