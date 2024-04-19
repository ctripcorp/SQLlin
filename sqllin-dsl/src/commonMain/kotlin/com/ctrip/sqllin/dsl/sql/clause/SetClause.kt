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
 * Present the single prediction in set clause
 * @author yaqiao
 */

public class SetClause<T> : Clause<T> {

    private val clauseBuilder = StringBuilder()

    internal var parameters: MutableList<String>? = null
        private set

    public fun appendString(propertyName: String, propertyValue: String?) {
        clauseBuilder.append(propertyName)
        if (propertyValue == null)
            clauseBuilder.append("NULL,")
        else {
            clauseBuilder.append("?,")
            val params = parameters ?: ArrayList<String>().also {
                parameters = it
            }
            params.add(propertyValue)
        }
    }

    public fun appendAny(propertyName: String, propertyValue: Any?) {
        clauseBuilder
            .append(propertyName)
            .append('=')
            .append(propertyValue ?: "NULL")
            .append(',')
    }

    internal fun finalize(): String = clauseBuilder.apply {
        if (this[lastIndex] == ',')
            deleteAt(lastIndex)
    }.toString()
}

public inline fun <T> SET(block: SetClause<T>.() -> Unit): SetClause<T> = SetClause<T>().apply(block)