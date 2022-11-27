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

import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.X

/**
 * SQLite functions
 * @author yaqiao
 */

public fun <T : DBEntity<T>> Table<T>.count(element: ClauseElement): ClauseNumber =
    ClauseNumber("count(${element.valueName})")

public fun <T : DBEntity<T>> Table<T>.count(x: X): ClauseNumber =
    ClauseNumber("count(*)")

public fun <T : DBEntity<T>> Table<T>.max(element: ClauseElement): ClauseNumber =
    ClauseNumber("max(${element.valueName})")

public fun <T: DBEntity<T>> Table<T>.min(element: ClauseElement): ClauseNumber =
    ClauseNumber("min(${element.valueName})")

public fun <T: DBEntity<T>> Table<T>.avg(element: ClauseElement): ClauseNumber =
    ClauseNumber("avg(${element.valueName})")

public fun <T: DBEntity<T>> Table<T>.sum(element: ClauseElement): ClauseNumber =
    ClauseNumber("sum(${element.valueName})")

public fun <T: DBEntity<T>> Table<T>.abs(number: ClauseElement): ClauseNumber =
    ClauseNumber("abs(${number.valueName})")

public fun <T: DBEntity<T>> Table<T>.upper(element: ClauseElement): ClauseString =
    ClauseString("upper(${element.valueName})")

public fun <T: DBEntity<T>> Table<T>.lower(element: ClauseElement): ClauseString =
    ClauseString("lower(${element.valueName})")

public fun <T: DBEntity<T>> Table<T>.length(element: ClauseElement): ClauseNumber =
    ClauseNumber("length(${element.valueName})")
