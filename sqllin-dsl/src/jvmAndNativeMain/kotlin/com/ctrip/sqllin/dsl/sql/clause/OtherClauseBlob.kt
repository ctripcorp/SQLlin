/*
 * Copyright (C) 2025 Ctrip.com.
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
 * Platform-specific factory function for creating BLOB clause wrappers on JVM and Native platforms.
 *
 * Returns the default [DefaultClauseBlob] implementation which uses parameterized binding
 * for BLOB values. This approach passes ByteArray values as parameters rather than embedding
 * them as literals in the SQL string.
 *
 * @param valueName The column or function name
 * @param table The table this clause belongs to
 * @param isFunction True if this represents a SQL function result
 * @return DefaultClauseBlob instance using parameterized binding
 */
public actual fun ClauseBlob(
    valueName: String,
    table: Table<*>,
    isFunction: Boolean,
): DefaultClauseBlob = DefaultClauseBlob(valueName, table, isFunction)