/*
 * Copyright (C) 2023 Ctrip.com.
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

package com.ctrip.sqllin.driver

internal infix fun DatabaseConnection.updateSynchronousMode(mode: SynchronousMode): Unit =
    execSQL("PRAGMA synchronous=${mode.value}")

internal infix fun DatabaseConnection.updateJournalMode(mode: JournalMode): Unit =
    execSQL("PRAGMA journal_mode=${mode.name}")

internal fun DatabaseConnection.migrateIfNeeded(
    create: (DatabaseConnection) -> Unit,
    upgrade: (DatabaseConnection, Int, Int) -> Unit,
    version: Int,
) {
    beginTransaction()
    try {
        val versionQueryCursor = query("PRAGMA user_version;", null) as CursorImpl
        versionQueryCursor.next()
        val initialVersion = versionQueryCursor.getInt(0)
        if (initialVersion == 0) {
            create(this)
            execSQL("PRAGMA user_version = $version")
        } else if (initialVersion != version) {
            if (initialVersion > version) {
                throw IllegalStateException("Database version $initialVersion newer than config version $version")
            }
            upgrade(this, initialVersion, version)
            execSQL("PRAGMA user_version = $version")
        }
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
}
