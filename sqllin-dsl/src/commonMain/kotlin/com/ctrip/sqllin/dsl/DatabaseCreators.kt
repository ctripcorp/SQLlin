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
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI

/**
 * Factory functions for creating [Database] instances.
 *
 * @author Yuang Qiao
 */

/**
 * Creates a database from a driver-level configuration.
 *
 * @param configuration The database configuration
 * @param enableSimpleSQLLog Whether to enable simple SQL logging for debugging
 * @return A new database instance
 */
public fun Database(
    configuration: DatabaseConfiguration,
    enableSimpleSQLLog: Boolean = false,
): Database = Database(openDatabase(configuration), enableSimpleSQLLog)

/**
 * Creates a database with basic configuration parameters.
 *
 * Uses default settings for other configuration options.
 *
 * @param name The database filename
 * @param path The database directory path
 * @param version The database schema version
 * @param enableSimpleSQLLog Whether to enable simple SQL logging for debugging
 * @return A new database instance
 */
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

/**
 * Creates a database from a DSL-level configuration.
 *
 * Allows using [DatabaseScope] in create and upgrade callbacks instead of
 * raw [com.ctrip.sqllin.driver.DatabaseConnection].
 *
 * @param dsldbConfiguration The DSL database configuration
 * @param enableSimpleSQLLog Whether to enable simple SQL logging for debugging
 * @return A new database instance
 */
@ExperimentalDSLDatabaseAPI
public fun Database(
    dsldbConfiguration: DSLDBConfiguration,
    enableSimpleSQLLog: Boolean = false,
): Database = Database(
    configuration = dsldbConfiguration convertToDatabaseConfiguration enableSimpleSQLLog,
    enableSimpleSQLLog = enableSimpleSQLLog,
)
