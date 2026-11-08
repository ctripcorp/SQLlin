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

package com.ctrip.sqllin.dsl.test

import com.ctrip.sqllin.driver.DatabasePath
import com.ctrip.sqllin.dsl.DSLDBConfiguration
import com.ctrip.sqllin.dsl.Database
import com.ctrip.sqllin.dsl.DatabaseScope
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.sql.X
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.clause.OrderByWay.ASC
import com.ctrip.sqllin.dsl.sql.clause.OrderByWay.DESC
import com.ctrip.sqllin.dsl.sql.pragma.ColumnInfo
import com.ctrip.sqllin.dsl.sql.statement.PragmaStatement
import com.ctrip.sqllin.dsl.sql.statement.SelectStatement
import kotlinx.serialization.serializer
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The tests of the columns whose serializers convert their values, `ClauseConverted`: their types, their conditions,
 * which compare what the serializers store, and SET, which stores what they do.
 *
 * @author Yuang Qiao
 */
@OptIn(ExperimentalDSLDatabaseAPI::class, ExperimentalUuidApi::class)
class CommonConvertedTest(private val path: DatabasePath) {

    private inline fun withDatabase(noinline create: DatabaseScope.() -> Unit, block: (Database) -> Unit) {
        val database = Database(DSLDBConfiguration(name = CommonBasicTest.DATABASE_NAME, path = path, version = 1, create = create))
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private val id1 = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val id2 = Uuid.parse("00000000-0000-0000-0000-000000000002")
    private val id3 = Uuid.parse("00000000-0000-0000-0000-000000000003")

    private val noon = Instant.parse("2024-01-01T12:00:00Z")
    // Half a second later, whose ISO-8601 string, "2024-01-01T12:00:00.500Z", comes before that of noon
    private val noonAndAHalf = Instant.parse("2024-01-01T12:00:00.500Z")
    private val evening = Instant.parse("2024-01-01T18:00:00Z")

    private fun event(id: Uuid, startsAt: Instant, endsAt: Instant?, score: Int, bonus: Int?, email: String, code: String) = ConvertedEvent(
        id = id,
        startsAt = startsAt,
        endsAt = endsAt,
        loggedAt = startsAt,
        createdAt = startsAt,
        duration = if (bonus == null) null else bonus.minutes,
        score = Score(score),
        bonus = bonus?.let { Score(it) },
        email = Email(email),
        digest = Digest(byteArrayOf(score.toByte(), 0x7F)),
        code = code,
    )

    private val events = listOf(
        event(id1, noon, evening, score = 1, bonus = 5, email = "a@x.com", code = "abc"),
        event(id2, noonAndAHalf, null, score = 2, bonus = null, email = "b@x.com", code = "def"),
        event(id3, evening, null, score = 3, bonus = 7, email = "c@x.com", code = "ghi"),
    )

    /** The ids of the events, in their order, which tells the rows apart, as a [Digest] doesn't equal another. */
    private fun SelectStatement<ConvertedEvent>.ids() = getResults().map { it.id }

    /**
     * The columns' types are those their serializers store, and a row is read back as it was written. Conditions
     * compare what the serializers store: of a Uuid primary key, instants stored as numbers, by the serializer of the
     * property, and of the type alias of its type, nullable or not; an instant and a duration stored by their built-in
     * serializers; value classes, one of them with NOCASE; a value class of bytes; and a String stored reversed, which
     * a condition only matches if it reverses the string it compares with.
     */
    fun testConvertedConditions() = withDatabase({ CREATE(ConvertedEventTable) }) { database ->
        assertEquals(
            "CREATE TABLE converted_event(id TEXT PRIMARY KEY NOT NULL,startsAt BIGINT NOT NULL,endsAt BIGINT,loggedAt BIGINT NOT NULL," +
                "createdAt TEXT NOT NULL,duration TEXT,score INT NOT NULL,bonus INT,email TEXT NOT NULL COLLATE NOCASE," +
                "digest BLOB NOT NULL,code TEXT NOT NULL)",
            ConvertedEventTable.createSQL,
        )
        lateinit var all: SelectStatement<ConvertedEvent>
        lateinit var byId: SelectStatement<ConvertedEvent>
        lateinit var afterNoon: SelectStatement<ConvertedEvent>
        lateinit var between: SelectStatement<ConvertedEvent>
        lateinit var byStart: SelectStatement<ConvertedEvent>
        lateinit var unended: SelectStatement<ConvertedEvent>
        lateinit var ended: SelectStatement<ConvertedEvent>
        lateinit var logged: SelectStatement<ConvertedEvent>
        lateinit var created: SelectStatement<ConvertedEvent>
        lateinit var lasting: SelectStatement<ConvertedEvent>
        lateinit var scored: SelectStatement<ConvertedEvent>
        lateinit var scores: SelectStatement<ConvertedEvent>
        lateinit var unscored: SelectStatement<ConvertedEvent>
        lateinit var byEmail: SelectStatement<ConvertedEvent>
        lateinit var byDigest: SelectStatement<ConvertedEvent>
        lateinit var byCode: SelectStatement<ConvertedEvent>
        lateinit var storedCodes: SelectStatement<StoredText>
        lateinit var endedAfterStart: SelectStatement<ConvertedEvent>
        lateinit var loggedAtStart: SelectStatement<ConvertedEvent>
        lateinit var endedAsStarted: SelectStatement<ConvertedEvent>
        database {
            ConvertedEventTable { table ->
                table INSERT events
                all = table SELECT X
                byId = table SELECT WHERE(id EQ id2)
                afterNoon = table SELECT WHERE(startsAt GT noon) ORDER_BY (startsAt to ASC)
                between = table SELECT WHERE(startsAt BETWEEN (noonAndAHalf to evening)) ORDER_BY (startsAt to ASC)
                byStart = table SELECT ORDER_BY(startsAt to DESC)
                unended = table SELECT WHERE(endsAt EQ null) ORDER_BY (startsAt to ASC)
                ended = table SELECT WHERE(endsAt NEQ null)
                logged = table SELECT WHERE(loggedAt LTE noonAndAHalf) ORDER_BY (loggedAt to ASC)
                created = table SELECT WHERE(createdAt EQ noonAndAHalf)
                lasting = table SELECT WHERE(duration EQ 7.minutes)
                scored = table SELECT WHERE(score GTE Score(2)) ORDER_BY (score to ASC)
                scores = table SELECT WHERE(score IN listOf(Score(1), Score(3))) ORDER_BY (score to ASC)
                unscored = table SELECT WHERE(bonus EQ null)
                byEmail = table SELECT WHERE(email EQ Email("C@X.COM"))
                byDigest = table SELECT WHERE(digest EQ Digest(byteArrayOf(2, 0x7F)))
                byCode = table SELECT WHERE(code EQ "def")
                storedCodes = table SELECT listOf(CAST(code AS TEXT) AS StoredText::value)
                endedAfterStart = table SELECT WHERE(endsAt GT startsAt)
                loggedAtStart = table SELECT WHERE((loggedAt EQ startsAt) AND (bonus NEQ null)) ORDER_BY (score to ASC)
                endedAsStarted = table SELECT WHERE(endsAt IS startsAt)
            }
        }
        // Read back as written, but for the bytes of a Digest, which equal without the Digests equaling
        val read = all.getResults()
        val noDigest = Digest(ByteArray(0))
        assertEquals(events.map { it.copy(digest = noDigest) }, read.map { it.copy(digest = noDigest) })
        read.zip(events).forEach { (row, event) -> assertContentEquals(event.digest.bytes, row.digest.bytes) }

        assertEquals(listOf(id2), byId.ids())
        // As numbers, half a second after noon is after noon, which as ISO-8601 strings it isn't
        assertEquals(listOf(id2, id3), afterNoon.ids())
        assertEquals(listOf(id2, id3), between.ids())
        assertEquals(listOf(id3, id2, id1), byStart.ids())
        assertEquals(listOf(id2, id3), unended.ids())
        assertEquals(listOf(id1), ended.ids())
        assertEquals(listOf(id1, id2), logged.ids())
        assertEquals(listOf(id2), created.ids())
        assertEquals(listOf(id3), lasting.ids())
        assertEquals(listOf(id2, id3), scored.ids())
        assertEquals(listOf(id1, id3), scores.ids())
        assertEquals(listOf(id2), unscored.ids())
        assertEquals(listOf(id3), byEmail.ids())
        assertEquals(listOf(id2), byDigest.ids())
        assertEquals(listOf(id2), byCode.ids())
        assertEquals(listOf("cba", "fed", "ihg"), storedCodes.getResults().map { it.value }.sortedBy { it })
        assertEquals(listOf(id1), endedAfterStart.ids())
        assertEquals(listOf(id1, id3), loggedAtStart.ids())
        assertEquals(emptyList(), endedAsStarted.ids())
    }

    /**
     * SET {} stores what the serializers of the columns store, as INSERT does, as does SET of an expression, with a
     * literal of a serializer; an UPDATE and a DELETE find their rows by converted columns; `max` and `coalesce` of a
     * converted column are of its type; IN compares with the column of a subquery of the same serializer; and a view and
     * an index can have converted columns.
     */
    fun testConvertedStatements() = withDatabase({
        CREATE(ConvertedEventTable)
        CREATE(ConvertedHolidayTable)
    }) { database ->
        lateinit var updated: SelectStatement<ConvertedEvent>
        lateinit var storedCode: SelectStatement<StoredText>
        lateinit var latest: SelectStatement<LatestStart>
        lateinit var latestEnd: SelectStatement<LatestStart>
        lateinit var onHolidays: SelectStatement<ConvertedEvent>
        lateinit var starts: SelectStatement<ConvertedEventStart>
        lateinit var remaining: SelectStatement<ConvertedEvent>
        database {
            ConvertedEventTable { table ->
                table INSERT events
                table UPDATE SET {
                    endsAt = noonAndAHalf
                    score = Score(9)
                    bonus = null
                    code = "xyz"
                } WHERE (id EQ id2)
                table UPDATE SET(literal(evening, EpochMillisSerializer) AS ConvertedEvent::endsAt) WHERE (id EQ id3)
                updated = table SELECT ORDER_BY(score to ASC)
                storedCode = table SELECT listOf(CAST(code AS TEXT) AS StoredText::value)
                latest = table SELECT listOf(max(startsAt) AS LatestStart::startsAt)
                latestEnd = table SELECT listOf(coalesce(endsAt, noon) AS LatestStart::startsAt) ORDER_BY (score to ASC)
                table.CREATE_INDEX("idx_converted_event_starts", startsAt)
                CREATE_VIEW(ConvertedEventStartView) AS (table SELECT listOf(startsAt AS ConvertedEventStart::startsAt, score AS ConvertedEventStart::score))
            }
            ConvertedHolidayTable INSERT listOf(ConvertedHoliday(null, evening), ConvertedHoliday(null, noon))
            ConvertedEventTable { table ->
                onHolidays = table SELECT WHERE(startsAt IN (ConvertedHolidayTable SELECT listOf(ConvertedHolidayTable.startsAt AS LatestStart::startsAt))) ORDER_BY (startsAt to ASC)
            }
            ConvertedEventStartView { view ->
                starts = view SELECT WHERE(startsAt GTE noonAndAHalf) ORDER_BY (startsAt to ASC)
            }
        }
        val rows = updated.getResults()
        assertEquals(listOf(id1, id3, id2), rows.map { it.id })
        assertEquals(listOf(evening, evening, noonAndAHalf), rows.map { it.endsAt })
        assertEquals(listOf(Score(1), Score(3), Score(9)), rows.map { it.score })
        assertEquals(listOf(Score(5), Score(7), null), rows.map { it.bonus })
        assertEquals("xyz", rows.last().code)
        assertEquals(listOf(StoredText("cba"), StoredText("zyx"), StoredText("ihg")).sortedBy { it.value }, storedCode.getResults().sortedBy { it.value })
        assertEquals(listOf(LatestStart(evening)), latest.getResults())
        assertEquals(listOf(evening, evening, noonAndAHalf).map { LatestStart(it) }, latestEnd.getResults())
        assertEquals(listOf(id1, id3), onHolidays.ids())
        assertEquals(listOf(ConvertedEventStart(noonAndAHalf, Score(9)), ConvertedEventStart(evening, Score(3))), starts.getResults())

        database {
            ConvertedEventTable { table ->
                table DELETE WHERE((startsAt LT evening) AND (score NEQ Score(9)))
                remaining = table SELECT ORDER_BY(startsAt to ASC)
            }
        }
        assertEquals(listOf(id2, id3), remaining.ids())
    }

    /**
     * Enums with serializers of their own, given on the enum class or on the property, are stored as those serializers
     * store them, by name here, which conditions compare with and SET stores: before 2.6.0, they were compared and set
     * as their ordinals, in a column of INT, so that a condition didn't match them, and a row that SET changed couldn't
     * be read. An enum with its default serializer, and one serialized contextually, are still stored as ordinals.
     */
    fun testConvertedEnums() = withDatabase({ CREATE(NamedEnumRowTable) }) { database ->
        lateinit var high: SelectStatement<NamedEnumRow>
        lateinit var severities: SelectStatement<NamedEnumRow>
        lateinit var banned: SelectStatement<NamedEnumRow>
        lateinit var noStatus: SelectStatement<NamedEnumRow>
        lateinit var ordinal: SelectStatement<NamedEnumRow>
        lateinit var contextual: SelectStatement<NamedEnumRow>
        lateinit var storedSeverities: SelectStatement<StoredText>
        lateinit var storedContextual: SelectStatement<StoredNumber>
        database {
            NamedEnumRowTable { table ->
                table INSERT listOf(
                    NamedEnumRow(null, Severity.LOW, UserStatus.ACTIVE, UserStatus.ACTIVE, UserStatus.SUSPENDED),
                    NamedEnumRow(null, Severity.HIGH, null, UserStatus.BANNED, UserStatus.ACTIVE),
                )
                high = table SELECT WHERE(severity EQ Severity.HIGH)
                table UPDATE SET {
                    severity = Severity.MEDIUM
                    status = UserStatus.BANNED
                } WHERE (severity EQ Severity.LOW)
                severities = table SELECT WHERE(severity IN listOf(Severity.MEDIUM, Severity.HIGH)) ORDER_BY (id to ASC)
                banned = table SELECT WHERE(status EQ UserStatus.BANNED)
                noStatus = table SELECT WHERE(status EQ null)
                ordinal = table SELECT WHERE(ordinalStatus GT UserStatus.SUSPENDED)
                contextual = table SELECT WHERE(contextualStatus EQ UserStatus.SUSPENDED)
                storedSeverities = table SELECT listOf(CAST(severity AS TEXT) AS StoredText::value) ORDER_BY (id to ASC)
                storedContextual = table SELECT listOf(CAST(contextualStatus AS BIGINT) AS StoredNumber::value) ORDER_BY (id to ASC)
            }
        }
        assertEquals(listOf(Severity.HIGH), high.getResults().map { it.severity })
        assertEquals(
            listOf(
                NamedEnumRow(1, Severity.MEDIUM, UserStatus.BANNED, UserStatus.ACTIVE, UserStatus.SUSPENDED),
                NamedEnumRow(2, Severity.HIGH, null, UserStatus.BANNED, UserStatus.ACTIVE),
            ),
            severities.getResults(),
        )
        assertEquals(listOf(1L), banned.getResults().map { it.id })
        assertEquals(listOf(2L), noStatus.getResults().map { it.id })
        assertEquals(listOf(2L), ordinal.getResults().map { it.id })
        assertEquals(listOf(1L), contextual.getResults().map { it.id })
        assertEquals(listOf("MEDIUM", "HIGH"), storedSeverities.getResults().map { it.value })
        assertEquals(listOf(UserStatus.SUSPENDED.ordinal.toLong(), UserStatus.ACTIVE.ordinal.toLong()), storedContextual.getResults().map { it.value })
        assertEquals(
            "CREATE TABLE named_enum_row(id INTEGER PRIMARY KEY,severity TEXT NOT NULL,status TEXT,ordinalStatus INT NOT NULL,contextualStatus INT NOT NULL)",
            NamedEnumRowTable.createSQL,
        )
    }

    /**
     * The serializer that `@file:UseSerializers` gives a type converts its values, rather than its built-in one; a
     * generic type, a `List`, is a column with the serializer of its property; and `ALTER_ADD_COLUMN` adds a converted
     * column of the type its serializer stores, where it used to fail for any type but a basic one or an enum.
     */
    fun testConvertedSchema() = withDatabase({
        CREATE(ConvertedTimerTable)
        CREATE(ConvertedNoteTable)
        CREATE(ConvertedEventV1Table)
    }) { database ->
        assertEquals("CREATE TABLE converted_timer(id INTEGER PRIMARY KEY,timeout BIGINT NOT NULL,delay BIGINT)", ConvertedTimerTable.createSQL)
        assertEquals("CREATE TABLE converted_note(id INTEGER PRIMARY KEY,tags TEXT NOT NULL)", ConvertedNoteTable.createSQL)
        lateinit var long: SelectStatement<ConvertedTimer>
        lateinit var storedTimeouts: SelectStatement<StoredNumber>
        lateinit var columns: PragmaStatement<List<ColumnInfo>>
        lateinit var reminded: SelectStatement<ConvertedEventV2>
        lateinit var tagged: SelectStatement<ConvertedNote>
        database {
            ConvertedNoteTable { table ->
                table INSERT listOf(ConvertedNote(null, listOf("a", "b")), ConvertedNote(null, emptyList()))
                tagged = table SELECT WHERE(tags EQ listOf("a", "b"))
            }
            ConvertedTimerTable { table ->
                table INSERT listOf(ConvertedTimer(null, 1.minutes, null), ConvertedTimer(null, 10.seconds, 5.seconds))
                long = table SELECT WHERE(timeout GT 30.seconds)
                storedTimeouts = table SELECT listOf(CAST(timeout AS BIGINT) AS StoredNumber::value) ORDER_BY (id to ASC)
            }
            ConvertedEventV1Table INSERT ConvertedEventV1(id1, Score(1))
            ConvertedEventV2Table ALTER_ADD_COLUMN ConvertedEventV2Table.reminder
            columns = PRAGMA_TABLE_INFO(ConvertedEventV2Table)
            ConvertedEventV2Table { table ->
                table INSERT ConvertedEventV2(id2, Score(2), evening)
                reminded = table SELECT WHERE(reminder GT noon)
            }
        }
        assertEquals(listOf(ConvertedTimer(1, 1.minutes, null)), long.getResults())
        assertEquals(listOf(ConvertedNote(1, listOf("a", "b"))), tagged.getResults())
        assertEquals(listOf(60L, 10L), storedTimeouts.getResults().map { it.value })
        assertEquals(listOf("id" to "TEXT", "score" to "INT", "reminder" to "BIGINT"), columns.getResults().map { it.name to it.type })
        assertEquals(listOf(ConvertedEventV2(id2, Score(2), evening)), reminded.getResults())
    }

    /**
     * A serializer that encodes a structure fails the CREATE statement of its column, and its conditions, as a column
     * holds one value; a collation of a column whose serializer doesn't store text fails its CREATE statement; and an
     * element whose serializer isn't the property's fails, as its conditions wouldn't compare what the column holds.
     */
    fun testConvertedErrors() {
        assertContains(assertFailsWith<IllegalArgumentException> { StructuredColumnTable.createSQL }.message.orEmpty(), "encodes a structure")
        assertContains(assertFailsWith<IllegalArgumentException> { StructuredColumnTable.point EQ Point(1, 2) }.message.orEmpty(), "encodes a structure")
        assertContains(assertFailsWith<IllegalArgumentException> { CollatedNumberTable.createSQL }.message.orEmpty(), "has the collation NOCASE")
        assertContains(
            assertFailsWith<IllegalArgumentException> {
                ClauseConverted("startsAt", ConvertedEventTable, false, serializer<Instant>())
            }.message.orEmpty(),
            "is written by the serializer of 'com.ctrip.sqllin.dsl.test.EpochMillis'",
        )
    }
}
