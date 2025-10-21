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

/**
 * Base class for individual SQL statements.
 *
 * Represents a single SQL operation (INSERT, UPDATE, DELETE, CREATE, or SELECT) that can be
 * executed against the database. Statements maintain their SQL string and optional parameters
 * for parameterized queries.
 *
 * Subclasses include specific statement types defined in the [com.ctrip.sqllin.dsl.sql.operation] package.
 *
 * @property sqlStr The complete SQL string for this statement
 *
 * @author Yuang Qiao
 */
public sealed class SingleStatement(
    public val sqlStr: String,
) : ExecutableStatement {

    /**
     * Parameters for parameterized query placeholders.
     *
     * Supports multiple types (String, ByteArray, numeric types, etc.).
     * `null` if the statement has no parameters.
     */
    internal abstract val parameters: MutableList<Any?>?

    /**
     * Parameters converted to array format for driver execution.
     */
    internal val params: Array<Any?>?
        get() = parameters?.toTypedArray()

    /**
     * Logs the SQL string and parameters for debugging.
     */
    internal fun printlnSQL() {
        print("SQL String: $sqlStr")
        parameters?.let {
            println("; Parameters: $it")
        } ?: println()
    }
}