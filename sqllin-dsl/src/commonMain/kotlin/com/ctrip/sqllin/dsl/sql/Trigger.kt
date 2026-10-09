/*
 * Copyright (C) 2026 Ctrip.com.
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

package com.ctrip.sqllin.dsl.sql

import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.sql.clause.ClauseElement
import com.ctrip.sqllin.dsl.sql.clause.SelectCondition

/**
 * A trigger that `CREATE_TRIGGER` names, which `BEFORE` or `AFTER` gives the event it fires on:
 * ```kotlin
 * CREATE_TRIGGER("person_audit") AFTER DELETE ON PersonTable BEGIN {
 *     AuditTable INSERT listOf(old(PersonTable.id) AS Audit::personId, literal("delete") AS Audit::action)
 * }
 * ```
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public class TriggerName internal constructor(internal val name: String, internal val isIfNotExists: Boolean)

/**
 * The event a trigger fires on: `INSERT`, `DELETE`, `UPDATE`, or `UPDATE_OF` columns. A trigger on INSERT has a new
 * row, one on DELETE an old row, and one on UPDATE both.
 */
@ExperimentalDSLDatabaseAPI
public class TriggerEvent internal constructor(
    internal val sql: String,
    internal val hasNewRow: Boolean,
    internal val hasOldRow: Boolean,
    internal val columns: List<ClauseElement<*>> = emptyList(),
)

/**
 * A trigger and when it fires, which `ON` gives its table.
 */
@ExperimentalDSLDatabaseAPI
public class TriggerTiming internal constructor(internal val trigger: TriggerName, internal val timing: String, internal val event: TriggerEvent)

/**
 * A trigger on its table, which `WHEN` can give a condition, and `BEGIN` gives its body.
 */
@ExperimentalDSLDatabaseAPI
public class TriggerDefinition internal constructor(
    internal val timing: TriggerTiming,
    internal val table: Table<*>,
    internal val condition: SelectCondition? = null,
)

/**
 * What `RAISE` does with the statement that fired the trigger: `ABORT` stops it and undoes its changes, `FAIL` stops it
 * and keeps the changes it made before, and `ROLLBACK` stops it and rolls back the transaction.
 */
@ExperimentalDSLDatabaseAPI
public class RaiseResolution internal constructor(internal val sql: String)

/**
 * `RAISE(IGNORE)`, which skips the row that fired the trigger, and the rest of the trigger, while the statement goes on.
 */
@ExperimentalDSLDatabaseAPI
public class RaiseIgnore internal constructor()
