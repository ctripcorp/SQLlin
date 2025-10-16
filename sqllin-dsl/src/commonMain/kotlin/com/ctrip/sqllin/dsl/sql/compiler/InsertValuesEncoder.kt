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

package com.ctrip.sqllin.dsl.sql.compiler

/**
 * Encoder for generating VALUES clauses in INSERT statements.
 *
 * Produces SQL in the format: `(value1, value2, ..., valueN)`
 *
 * Each entity is encoded into a parenthesized tuple of values, with commas separating
 * elements within the tuple.
 *
 * Example output: `(123, 'Alice', 25)` or `(?, ?, ?)` with parameters `["Alice"]`
 *
 * @author Yuang Qiao
 */
internal class InsertValuesEncoder(
    override val parameters: MutableList<String>,
) : AbstractValuesEncoder() {

    override val sqlStrBuilder = StringBuilder("(")

    /**
     * Appends comma between values or closing parenthesis after the last value.
     */
    override fun StringBuilder.appendTail(): StringBuilder  {
        val symbol = if (elementsIndex < elementsCount - 1)
            ','
        else
            ')'
        return append(symbol)
    }
}