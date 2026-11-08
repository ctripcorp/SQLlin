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

package com.ctrip.sqllin.dsl

import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI

/**
 * A token of the versions of the tables a read read, which [Database.versioned] returns with its results, so that a
 * reader learns when they change: [Database.awaitChange] suspends until one of them does, and [Database.onChange]
 * registers a callback for it.
 *
 * The versions are captured under the lock that the read runs under, after the tables are tracked, so that no change
 * made after the read is missed. A change made through the same [Database] makes the token stale, as a rolled back
 * transaction and an UPDATE or DELETE that matches no rows don't; changes made through another database instance,
 * connection or process aren't seen. A view stands for the tables it reads. A token can tell of a change that its
 * read already includes, but never misses one.
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public class TableVersions internal constructor(
    internal val tracker: InvalidationTracker,
    internal val versions: Map<String, Long>,
) {

    /**
     * Combines this token with [other], as of reads that depend on each other, so that the combined token is stale
     * when either is: it covers the tables of both, and takes the earlier version of a table that both cover, which
     * a change after either read makes stale.
     *
     * @throws IllegalArgumentException if [other] is a token of another [Database], whose versions count other changes
     */
    public operator fun plus(other: TableVersions): TableVersions {
        require(tracker === other.tracker) { "The tokens of different databases can't be combined, as each counts its own changes." }
        val combined = LinkedHashMap(versions)
        for ((table, version) in other.versions)
            combined[table] = combined[table]?.let { minOf(it, version) } ?: version
        return TableVersions(tracker, combined)
    }

    /**
     * Whether one of the tables of this token has changed, by [current], the versions the tracker has counted.
     */
    internal fun isStaleIn(current: Map<String, Long>): Boolean =
        versions.any { (table, version) -> (current[table] ?: 0L) > version }

    override fun toString(): String =
        versions.entries.joinToString(prefix = "TableVersions(", postfix = ")") { (table, version) -> "$table=$version" }
}
