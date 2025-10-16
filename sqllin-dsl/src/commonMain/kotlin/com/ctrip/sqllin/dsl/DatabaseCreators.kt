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

package com.ctrip.sqllin.dsl

import com.ctrip.sqllin.driver.DatabaseConfiguration
import com.ctrip.sqllin.driver.DatabasePath
import com.ctrip.sqllin.driver.openDatabase

/**
 * Factory functions for Database
 * @author Yuang Qiao
 */

public fun Database(
    configuration: DatabaseConfiguration,
    enableSimpleSQLLog: Boolean = false,
): Database = Database(openDatabase(configuration), enableSimpleSQLLog)

public fun Database(
    name: String,
    path: DatabasePath,
    version: Int,
    enableSimpleSQLLog: Boolean = false,
): Database = Database(
    DatabaseConfiguration(
        name = name,
        path = path,
        version = version,
    ),
    enableSimpleSQLLog
)

public fun Database(
    dsldbConfiguration: DSLDBConfiguration,
    enableSimpleSQLLog: Boolean = false,
): Database = Database(
    configuration = dsldbConfiguration convertToDatabaseConfiguration enableSimpleSQLLog,
    enableSimpleSQLLog = enableSimpleSQLLog,
)
