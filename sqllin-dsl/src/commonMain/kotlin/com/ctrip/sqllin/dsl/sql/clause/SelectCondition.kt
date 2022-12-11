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
 * Present the single condition in where clause
 * @author yaqiao
 */

public class SelectCondition internal constructor(
    internal val conditionSQL: String,
) {

    // Where condition 'OR' operator.
    internal infix fun or(next: SelectCondition): SelectCondition = append("OR", next)

    // Where condition 'AND' operator.
    internal infix fun and(next: SelectCondition): SelectCondition = append("AND", next)

    private fun append(symbol: String, next: SelectCondition): SelectCondition {
        val sql = buildString {
            append(conditionSQL)
            append(" $symbol ")
            append(next.conditionSQL)
        }
        return SelectCondition(sql)
    }
}